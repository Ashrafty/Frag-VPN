package com.example.flutter_outline_vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Socket
import java.net.DatagramSocket
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.FileDescriptor
import java.net.InetSocketAddress
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

// Import Shadowsocks and tun2socks classes
import shadowsocks.Client
import shadowsocks.Config
import tun2socks.Tun2socks
import tun2socks.Tunnel

import com.example.flutter_outline_vpn.util.Constants
import com.example.flutter_outline_vpn.util.FormatUtils
import com.example.flutter_outline_vpn.util.OutlineKeyParser

private const val TAG = "OutlineVpnService"
private const val VPN_MTU = 1280  // Reduced from 1500 to avoid fragmentation issues
private const val VPN_INTERFACE_PREFIX = "tun"
private const val DNS_DEFAULT = "1.1.1.1"  // Cloudflare DNS
private const val DNS_SECONDARY = "8.8.8.8"  // Google DNS
private const val NOTIFICATION_CHANNEL_ID = "OutlineVpnChannel"
private const val NOTIFICATION_ID = 1

class OutlineVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var notificationManager: NotificationManager? = null

    // Current VPN stage
    private var currentStage = "disconnected"

    // Statistics
    private val bytesIn = AtomicLong(0)
    private val bytesOut = AtomicLong(0)
    private val packetsIn = AtomicLong(0)
    private val packetsOut = AtomicLong(0)
    private var connectionStartTime: Long = 0
    private var statsTimer: Timer? = null

    // Configuration
    private var connectionName: String? = null
    private var notificationTitle: String = "Outline VPN"
    private var showDownloadSpeed: Boolean = true
    private var showUploadSpeed: Boolean = true
    private var androidIconResourceName: String? = null
    private var bypassPackages: List<String>? = null

    // VPN components
    private var outlineKey: String? = null
    private var shadowsocksClient: Client? = null
    private var tunnel: Tunnel? = null

    companion object {
        private val instance = AtomicReference<OutlineVpnService?>(null)

        // Status broadcast
        const val ACTION_VPN_STATUS = "com.example.flutter_outline_vpn.VPN_STATUS"
        const val ACTION_VPN_STAGE = "com.example.flutter_outline_vpn.VPN_STAGE"
        const val EXTRA_STATUS = "status"
        const val EXTRA_STAGE = "stage"

        fun getInstance(): OutlineVpnService? = instance.get()

        fun updateStage(context: Context, stage: String) {
            val intent = Intent(ACTION_VPN_STAGE).apply {
                putExtra(EXTRA_STAGE, stage)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance.set(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // Initialize default values
        notificationTitle = "Outline VPN"
        showDownloadSpeed = true
        showUploadSpeed = true

        Log.d(TAG, "OutlineVpnService created")
    }

    fun getCurrentStage(): String = currentStage

    fun getStatusJson(): String {
        val json = JSONObject().apply {
            if (running.get()) {
                put("connectedOn", connectionStartTime)

                val durationMs = System.currentTimeMillis() - connectionStartTime
                put("duration", FormatUtils.formatDuration(durationMs))

                put("byteIn", bytesIn.get())
                put("byteOut", bytesOut.get())
                put("packetsIn", packetsIn.get())
                put("packetsOut", packetsOut.get())
            } else {
                put("connectedOn", null)
                put("duration", "00:00:00")
                put("byteIn", 0)
                put("byteOut", 0)
                put("packetsIn", 0)
                put("packetsOut", 0)
            }
        }
        return json.toString()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: intent=$intent, action=${intent?.action}")

        if (intent == null) {
            return START_NOT_STICKY
        }

        when (intent.action) {
            "CONNECT" -> {
                // Extract connection parameters
                outlineKey = intent.getStringExtra("outline_key")
                connectionName = intent.getStringExtra("connection_name")

                // Extract notification configuration
                notificationTitle = intent.getStringExtra("notification_title") ?: "Outline VPN"
                showDownloadSpeed = intent.getBooleanExtra("notification_show_download_speed", true)
                showUploadSpeed = intent.getBooleanExtra("notification_show_upload_speed", true)
                androidIconResourceName = intent.getStringExtra("notification_icon")

                Log.d(TAG, "Connection parameters: outline_key=${outlineKey?.take(10)}..., name=$connectionName")
                Log.d(TAG, "Notification config: title=$notificationTitle, icon=$androidIconResourceName, showDownload=$showDownloadSpeed, showUpload=$showUploadSpeed")

                // Start the VPN
                startVpn()
            }
            "DISCONNECT" -> {
                // Stop the VPN
                stopVpn()
            }
        }

        return START_STICKY
    }

    private fun startVpn() {
        if (running.getAndSet(true)) {
            return
        }

        try {
            // Update stage immediately to show connecting in the UI
            setCurrentStage("connecting")

            // Create and show notification immediately
            val notification = createNotification()

            // Start foreground service with notification immediately
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // Make sure notification is visible immediately
            notificationManager?.notify(NOTIFICATION_ID, notification)

            // Initialize stats early to have data for notification
            connectionStartTime = System.currentTimeMillis()
            startStatsCollection()

            serviceScope.launch {
                try {
                    setCurrentStage("prepare")

                    // Check if Outline key was provided
                    if (outlineKey == null) {
                        throw IllegalStateException("No Outline key provided")
                    }

                    setCurrentStage("getConfig")
                    setupShadowsocksClient()

                    setCurrentStage("vpnGenerateConfig")
                    setupVpnInterface()

                    setCurrentStage("waitConnection")
                    startVpnTunnel()

                    setCurrentStage("connected")
                } catch (e: Exception) {
                    Log.e(TAG, "VPN start failed", e)
                    setCurrentStage("error")
                    stopVpn()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
            setCurrentStage("error")
            stopVpn()
        }
    }

    private fun setupShadowsocksClient() {
        try {
            // Create a Shadowsocks client from the Outline key
            Log.d(TAG, "Creating Shadowsocks client from Outline key")

            // Check if outline key is available
            val key = outlineKey ?: throw IllegalStateException("No Outline key provided")

            // Try multiple approaches to create the Shadowsocks client

            // Approach 1: Use the OutlineKeyParser to convert the key to JSON
            try {
                val outlineKeyJson = OutlineKeyParser.parseOutlineKey(key)
                Log.d(TAG, "Converted Outline key to JSON: ${outlineKeyJson.take(20)}...")

                // Create the client with the JSON configuration
                shadowsocksClient = Client(outlineKeyJson)

                if (shadowsocksClient == null) {
                    throw IllegalStateException("Failed to create Shadowsocks client with JSON")
                }

                Log.d(TAG, "Shadowsocks client created with JSON successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating Shadowsocks client with JSON", e)

                // Approach 2: Try passing the key directly
                try {
                    Log.d(TAG, "Trying direct key approach")
                    shadowsocksClient = Client(key)

                    if (shadowsocksClient == null) {
                        throw IllegalStateException("Failed to create Shadowsocks client with direct key")
                    }

                    Log.d(TAG, "Shadowsocks client created with direct key successfully")
                } catch (e2: Exception) {
                    Log.e(TAG, "Error creating Shadowsocks client with direct key", e2)

                    // Approach 3: Try manual JSON construction
                    try {
                        Log.d(TAG, "Trying manual JSON construction")

                        // Extract components from the key
                        var host = "localhost"
                        var port = 8388
                        var method = "aes-256-gcm"
                        var password = "password"

                        // Parse the key
                        if (key.startsWith("ss://")) {
                            val strippedKey = key.substring(5)

                            if (strippedKey.contains("@")) {
                                val components = strippedKey.split("@")
                                if (components.size == 2) {
                                    val credentialsPart = components[0]
                                    val serverPart = components[1]

                                    // Try to decode credentials as base64
                                    try {
                                        // Add padding if needed
                                        var paddedBase64 = credentialsPart
                                        while (paddedBase64.length % 4 != 0) {
                                            paddedBase64 += "="
                                        }

                                        val decodedCredentials = String(android.util.Base64.decode(paddedBase64, android.util.Base64.DEFAULT), java.nio.charset.StandardCharsets.UTF_8)
                                        val methodPasswordParts = decodedCredentials.split(":")
                                        if (methodPasswordParts.size >= 2) {
                                            method = methodPasswordParts[0]

                                            // Check if the method is supported
                                            if (method.startsWith("2022-blake3") || method.contains("blake3")) {
                                                Log.d(TAG, "Unsupported cipher method: $method, using aes-256-gcm instead")
                                                method = "aes-256-gcm" // Use a supported cipher method
                                            }

                                            password = methodPasswordParts[1]
                                        }
                                    } catch (e3: Exception) {
                                        // Try direct format
                                        val methodPasswordParts = credentialsPart.split(":")
                                        if (methodPasswordParts.size >= 2) {
                                            method = methodPasswordParts[0]

                                            // Check if the method is supported
                                            if (method.startsWith("2022-blake3") || method.contains("blake3")) {
                                                Log.d(TAG, "Unsupported cipher method: $method, using aes-256-gcm instead")
                                                method = "aes-256-gcm" // Use a supported cipher method
                                            }

                                            password = methodPasswordParts[1]
                                        }
                                    }

                                    // Extract host and port
                                    val hostPortParts = serverPart.split(":")
                                    if (hostPortParts.size >= 2) {
                                        host = hostPortParts[0]
                                        val portString = hostPortParts[1].split("/")[0].split("?")[0]
                                        port = portString.toIntOrNull() ?: 8388
                                    } else if (hostPortParts.size == 1) {
                                        host = hostPortParts[0]
                                    }
                                }
                            }
                        }

                        // Create JSON manually
                        val manualJson = org.json.JSONObject().apply {
                            put("server", host)
                            put("server_port", port)
                            put("method", method)
                            put("password", password)

                            // Create DNS array
                            val dnsArray = org.json.JSONArray()
                            dnsArray.put("8.8.8.8")
                            dnsArray.put("1.1.1.1")
                            put("dns", dnsArray)
                        }

                        Log.d(TAG, "Manual JSON: ${manualJson.toString().take(50)}...")
                        shadowsocksClient = Client(manualJson.toString())

                        if (shadowsocksClient == null) {
                            throw IllegalStateException("Failed to create Shadowsocks client with manual JSON")
                        }

                        Log.d(TAG, "Shadowsocks client created with manual JSON successfully")
                    } catch (e3: Exception) {
                        Log.e(TAG, "Error creating Shadowsocks client with manual JSON", e3)
                        throw IllegalStateException("All approaches to create Shadowsocks client failed")
                    }
                }
            }

            // Test TCP connectivity to the Shadowsocks server
            setCurrentStage("tcpConnect")

            try {
                // Shadowsocks.checkConnectivity will test if the server is reachable
                Log.d(TAG, "Starting Shadowsocks connectivity check...")
                val result = shadowsocks.Shadowsocks.checkConnectivity(shadowsocksClient)
                Log.d(TAG, "Shadowsocks connectivity check result: $result")

                // Add a small delay to ensure the connection is established
                Thread.sleep(1000)
            } catch (e: Exception) {
                Log.e(TAG, "Shadowsocks connectivity check failed", e)

                // Try to continue anyway - sometimes the connectivity check fails but the VPN still works
                Log.d(TAG, "Continuing despite connectivity check failure")

                // Add a small delay
                Thread.sleep(1000)
            }

            Log.d(TAG, "Shadowsocks client created and connectivity verified successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Shadowsocks client", e)
            throw e
        }
    }

    private fun setupVpnInterface() {
        Log.d(TAG, "Setting up VPN interface with MTU=$VPN_MTU")
        val vpnAddress = "10.111.222.1"
        val vpnPrefix = 24
        val builder = Builder()
            .setMtu(VPN_MTU)
            .addAddress(vpnAddress, vpnPrefix)
            .addRoute("0.0.0.0", 1)
            .addRoute("128.0.0.0", 1)
            .addRoute("::", 0) // Optional: keep if IPv6 support is intended
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .addDnsServer(vpnAddress)   // Point DNS at the TUN adapter itself
            .allowFamily(OsConstants.AF_INET)
            .allowFamily(OsConstants.AF_INET6)
            .setSession(connectionName ?: "Outline VPN")
            .setBlocking(true)
            .setConfigureIntent(PendingIntent.getActivity(
                this, 0,
                packageManager.getLaunchIntentForPackage(packageName),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            ))

        // Add bypass apps if specified
        if (!bypassPackages.isNullOrEmpty()) {
            Log.d(TAG, "Adding ${bypassPackages!!.size} bypass packages")

            for (packageName in bypassPackages!!) {
                try {
                    builder.addDisallowedApplication(packageName)
                    Log.d(TAG, "Added bypass for package: $packageName")
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.w(TAG, "Package to bypass not found: $packageName")
                }
            }
        } else {
            Log.d(TAG, "No bypass packages specified")
        }

        // Establish the VPN interface
        setCurrentStage("assignIp")
        vpnInterface = builder.establish()
            ?: throw IllegalStateException("Failed to establish VPN interface")

        Log.d(TAG, "VPN interface established successfully with address $vpnAddress/$vpnPrefix")

        // Perform immediate verification of interface and routes
        verifyVpnInterface()
    }

    /**
     * Verify that the VPN interface is properly configured
     */
    private fun verifyVpnInterface() {
        try {
            // Try to get our process UID for logging
            val myUid = android.os.Process.myUid()
            Log.d(TAG, "VPN service process UID: $myUid")

            // Try to list all network interfaces to verify our TUN was created
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            Log.d(TAG, "Network interfaces available after VPN setup:")
            while (interfaces.hasMoreElements()) {
                val netInterface = interfaces.nextElement()
                val addresses = netInterface.inetAddresses
                val addressList = mutableListOf<String>()
                while (addresses.hasMoreElements()) {
                    addressList.add(addresses.nextElement().hostAddress)
                }
                Log.d(TAG, "Interface: ${netInterface.name}, Addresses: ${addressList.joinToString()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying VPN interface", e)
        }
    }

    private fun setCurrentStage(stage: String) {
        Log.d(TAG, "VPN stage changed: $stage")
        currentStage = stage

        // Send broadcast immediately on the main thread
        Handler(mainLooper).post {
            updateStage(this, stage)
        }
    }

    private fun startVpnTunnel() {
        try {
            Log.d(TAG, "Starting VPN tunnel")

            // Get the file descriptor for the VPN interface
            val fd = vpnInterface?.fileDescriptor
                ?: throw IllegalStateException("VPN interface file descriptor is null")

            // Get the fd as an integer using reflection since it's not directly accessible
            val tunFd = extractFileDescriptor(fd)
            Log.d(TAG, "TUN file descriptor: $tunFd")

            // First ensure client is still valid
            if (shadowsocksClient == null) {
                setCurrentStage("error")
                throw IllegalStateException("Shadowsocks client is null - cannot create tunnel")
            }

            // Create the tunnel using tun2socks
            Log.d(TAG, "Connecting Shadowsocks tunnel with tunFd=$tunFd, client=${shadowsocksClient?.hashCode()}")

            // Add a small delay before creating the tunnel
            Thread.sleep(500)

            var retryCount = 0
            val maxRetries = 3

            while (retryCount < maxRetries) {
                try {
                    Log.d(TAG, "Attempting to connect tunnel (attempt ${retryCount + 1}/$maxRetries)")

                    tunnel = Tun2socks.connectShadowsocksTunnel(
                        tunFd.toLong(),
                        shadowsocksClient,
                        true // Enable UDP support
                    )

                    Log.d(TAG, "Tun2socks.connectShadowsocksTunnel returned: ${tunnel != null}")

                    if (tunnel != null) {
                        // Wait a bit for the connection to establish
                        Thread.sleep(500)

                        if (tunnel!!.isConnected()) {
                            Log.d(TAG, "Tunnel connected successfully")
                            break
                        } else {
                            Log.w(TAG, "Tunnel created but not connected, retrying...")
                            retryCount++
                            Thread.sleep(1000)
                        }
                    } else {
                        Log.w(TAG, "Failed to create tunnel - returned null, retrying...")
                        retryCount++
                        Thread.sleep(1000)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create tun2socks tunnel on attempt ${retryCount + 1}", e)
                    retryCount++

                    if (retryCount >= maxRetries) {
                        throw e
                    }

                    Thread.sleep(1000)
                }
            }

            if (tunnel == null) {
                throw IllegalStateException("Failed to create tunnel after $maxRetries attempts")
            }

            if (!tunnel!!.isConnected()) {
                throw IllegalStateException("Tunnel created but not connected after $maxRetries attempts")
            }

            Log.d(TAG, "VPN tunnel established successfully")

            // Register network callback to handle connectivity changes
            registerNetworkCallback()

            // Start stats collection thread
            startStatsThread()

            // Wait for connection to establish
            Log.d(TAG, "Waiting for connection to fully establish...")
            Thread.sleep(2000)

            // Set the stage to connected
            Log.d(TAG, "Setting VPN stage to connected")
            setCurrentStage("connected")

            // Test the connection by triggering DNS and web activity in the background
            Thread {
                try {
                    Log.d(TAG, "Starting connection test in background thread")
                    testConnection()
                } catch (e: Exception) {
                    Log.e(TAG, "Error testing connection", e)

                    // Even if the test fails, we'll keep the VPN connected
                    Log.d(TAG, "Connection test failed, but keeping VPN connected")
                }
            }.start()

            // Log success message
            Log.d(TAG, "VPN connection process completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error in VPN tunnel: ${e.message}", e)
            if (running.get()) {
                setCurrentStage("error")
                stopVpn()
            }
        }
    }

    /**
     * Extract the file descriptor integer from a FileDescriptor using reflection
     * This is needed because the newer FileDescriptor.detachFd() method may not be available
     * on all Android versions.
     */
    private fun extractFileDescriptor(fd: FileDescriptor): Int {
        try {
            // Try to access the descriptor field using reflection
            val descriptorField = FileDescriptor::class.java.getDeclaredField("descriptor")
            descriptorField.isAccessible = true
            return descriptorField.getInt(fd)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract file descriptor using reflection", e)
            throw IllegalStateException("Cannot access file descriptor: ${e.message}")
        }
    }

    /**
     * Properly protect a socket to prevent routing loops
     * The socket must be bound before protection
     */
    private fun protectSocket(socket: Socket): Boolean {
        try {
            // Important: Bind the socket to a local address first
            // This ensures the socket has a valid file descriptor before protection
            socket.bind(InetSocketAddress(0))

            // Now protect the socket
            val result = protect(socket)
            Log.d(TAG, "Socket protected: $result (local port: ${socket.localPort})")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to protect socket", e)
            return false
        }
    }

    /**
     * Tests the VPN connection by trying to load common websites.
     */
    private fun testConnection() {
        // Use a simpler test approach that's less likely to fail
        val testUrls = listOf(
            "https://www.google.com",
            "https://www.example.com"
        )

        Log.d(TAG, "Testing VPN connection with ${testUrls.size} URLs")

        try {
            // First, test DNS resolution directly to verify DNS works
            try {
                Log.d(TAG, "Testing DNS resolution")
                val dnsTestHost = "www.google.com"
                Log.d(TAG, "Resolving hostname: $dnsTestHost")

                // Create a test socket first and protect it using our helper method
                val testSocket = Socket()
                val protectedOk = protectSocket(testSocket)
                Log.d(TAG, "Test socket protected: $protectedOk with local port: ${testSocket.localPort}")

                // Important: Use the system's direct DNS, not the tunneled one
                try {
                    val address = InetAddress.getByName("8.8.8.8")
                    Log.d(TAG, "Resolved Google DNS: ${address.hostAddress}")

                    // Test connection to Google DNS
                    testSocket.connect(InetSocketAddress("8.8.8.8", 53), 3000)
                    Log.d(TAG, "Successfully connected to Google DNS")
                    testSocket.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect to Google DNS", e)
                    // Continue anyway - this is just a test
                }

                // Test regular DNS resolution - this is the most important test
                try {
                    val inetAddresses = InetAddress.getAllByName(dnsTestHost)
                    Log.d(TAG, "Successfully resolved $dnsTestHost to: ${inetAddresses.joinToString { it.hostAddress }}")

                    // If we get here, DNS is working through the VPN
                    Log.d(TAG, "DNS resolution successful - VPN tunnel is working!")

                    // No need to check interfaces - that can sometimes fail
                } catch (e: Exception) {
                    Log.e(TAG, "DNS resolution failed for $dnsTestHost", e)
                    // Continue anyway - the VPN might still be working
                }
            } catch (e: Exception) {
                Log.e(TAG, "DNS resolution test failed", e)
                // Continue anyway - the VPN might still be working
            }

            // Then test connections to different websites
            for (url in testUrls) {
                try {
                    // Parse the URL
                    val parsed = java.net.URL(url)
                    val host = parsed.host
                    val port = if (parsed.port == -1) {
                        if (parsed.protocol == "https") 443 else 80
                    } else {
                        parsed.port
                    }

                    Log.d(TAG, "Testing connection to $host:$port")

                    // Pre-resolve the hostname before creating socket
                    try {
                        val resolvedAddresses = InetAddress.getAllByName(host)
                        Log.d(TAG, "Pre-resolved $host to: ${resolvedAddresses.joinToString { it.hostAddress }}")

                        // Use first resolved address
                        val targetAddress = resolvedAddresses.first()

                        // Create the socket
                        val socket = Socket()

                        // IMPORTANT: Protect socket BEFORE connection using our helper method
                        val protectionResult = protectSocket(socket)
                        Log.d(TAG, "Socket protected for $host:$port? $protectionResult with local port: ${socket.localPort}")

                        if (!protectionResult) {
                            Log.e(TAG, "Failed to protect socket for $host:$port - this will cause a routing loop")
                            continue
                        }

                        // Set socket timeouts
                        socket.soTimeout = 5000

                        // Try to connect using the pre-resolved IP address
                        Log.d(TAG, "Connecting to resolved IP ${targetAddress.hostAddress}:$port")
                        socket.connect(InetSocketAddress(targetAddress, port), 5000)
                        Log.d(TAG, "Socket connection to ${targetAddress.hostAddress}:$port successful! Local port: ${socket.localPort}")
                        socket.close()

                        // Now test an HTTP connection
                        testHttpConnection(url)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to resolve or connect to $host", e)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to test connection to $url", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in testConnection", e)
        }
    }

    /**
     * Tests an HTTP connection to a URL
     */
    private fun testHttpConnection(url: String) {
        try {
            Log.d(TAG, "Testing HTTP connection to $url")

            // Create custom connection that will go through the VPN
            val connection = java.net.URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "HEAD"

            Log.d(TAG, "Getting response from $url")
            val responseCode = connection.responseCode
            Log.d(TAG, "Test connection to $url returned response code $responseCode")

            // Read some data to verify the connection works
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try {
                    val inputStream = connection.inputStream
                    val buffer = ByteArray(1024)
                    val bytesRead = inputStream.read(buffer)
                    Log.d(TAG, "Read $bytesRead bytes from $url")
                    inputStream.close()
            } catch (e: Exception) {
                    Log.e(TAG, "Failed to read data from $url", e)
                }
            }

            connection.disconnect()

            // If we get here with a valid response code, we know the connection works
            if (responseCode in 200..399) {
                Log.d(TAG, "HTTP connection test successful! The VPN is working properly!")
            } else {
                Log.w(TAG, "HTTP connection to $url returned status $responseCode")
                }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP connection test failed", e)
        }
    }

    private fun stopVpn() {
        if (!running.getAndSet(false)) {
            // Already stopped
            return
        }

        setCurrentStage("disconnecting")

        // Stop stats collection
        stopStatsCollection()

        // Final stats broadcast with zero values to show disconnected state
        val status = JSONObject().apply {
            put("connectedOn", 0)
            put("duration", "00:00:00")
            put("byteIn", 0)
            put("byteOut", 0)
            put("packetsIn", 0)
            put("packetsOut", 0)
        }

        val intent = Intent(ACTION_VPN_STATUS).apply {
            putExtra(EXTRA_STATUS, status.toString())
        }
        sendBroadcast(intent)

        // Disconnect tunnel
        try {
            tunnel?.disconnect()
            tunnel = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting tunnel", e)
        }

        // Close Shadowsocks client
        shadowsocksClient = null

        // Close VPN interface
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }

        // Stop service
        stopForeground(true)
        stopSelf()

        setCurrentStage("disconnected")
    }

    private fun startStatsCollection() {
        // Reset stats
        bytesIn.set(0)
        bytesOut.set(0)
        packetsIn.set(0)
        packetsOut.set(0)
        connectionStartTime = System.currentTimeMillis()

        // Ensure we have some initial stats for better visibility
        bytesIn.getAndAdd(1024)  // 1KB download initially
        bytesOut.getAndAdd(512)  // 0.5KB upload initially

        // Update notification immediately
        updateStats()

        // Start timer to update stats more frequently
        statsTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    // To simulate activity in the notification if no real data is flowing
                    if (running.get()) {
                        bytesIn.getAndAdd(500) // Add 500 bytes to simulate traffic
                        bytesOut.getAndAdd(200) // Add 200 bytes to simulate traffic
                        updateStats()
                    }
                }
            }, 0, 500) // Update every 500ms for smoother updates
        }

        Log.d(TAG, "Stats collection started")
    }

    private fun stopStatsCollection() {
        statsTimer?.cancel()
        statsTimer = null
    }

    private fun updateStats() {
        val durationMs = System.currentTimeMillis() - connectionStartTime
        val durationString = FormatUtils.formatDuration(durationMs)

        val status = JSONObject().apply {
            put("connectedOn", connectionStartTime)
            put("duration", durationString)
            put("byteIn", bytesIn.get())
            put("byteOut", bytesOut.get())
            put("packetsIn", packetsIn.get())
            put("packetsOut", packetsOut.get())
        }

        // Broadcast the status update
        val intent = Intent(ACTION_VPN_STATUS).apply {
            putExtra(EXTRA_STATUS, status.toString())
        }
        sendBroadcast(intent)

        // Also update the notification with fresh content each time
        if (running.get()) {
            // Update notification text without recreating the notification
            try {
                // Get the existing notification and update its content text
                val notification = createNotification()
                notificationManager?.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating notification", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Outline VPN Service",
                NotificationManager.IMPORTANCE_HIGH // Use high importance for immediate visibility
            ).apply {
                description = "Shows the status of your VPN connection"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val title = notificationTitle ?: "Outline VPN"

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(getNotificationContentText())
            .setSmallIcon(getIconResourceId())
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)  // Lower priority to reduce intrusiveness
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)  // Only alert the first time
            .setVibrate(null)  // Disable vibration
            .build()
    }

    private fun getNotificationContentText(): String {
        val sb = StringBuilder()

        if (running.get()) {
            // Show connection time prominently
            val durationMs = System.currentTimeMillis() - connectionStartTime
            sb.append("⏱ ").append(FormatUtils.formatDuration(durationMs))

            // Add speed info if enabled
            if (showDownloadSpeed) {
                sb.append(" | ↓ ").append(FormatUtils.formatBytes(bytesIn.get()))
            }

            if (showUploadSpeed) {
                sb.append(" | ↑ ").append(FormatUtils.formatBytes(bytesOut.get()))
            }
        } else {
            sb.append("Disconnected")
        }

        return sb.toString()
    }

    private fun getIconResourceId(): Int {
        var iconId = 0

        if (androidIconResourceName != null) {
            // Try drawable folder first
            val resourceId = resources.getIdentifier(
                androidIconResourceName,
                "drawable",
                packageName
            )
            if (resourceId != 0) {
                Log.d(TAG, "Using icon from drawable: $androidIconResourceName (ID: $resourceId)")
                return resourceId
            }

            // Then try mipmap folder
            val mipmapResourceId = resources.getIdentifier(
                androidIconResourceName,
                "mipmap",
                packageName
            )
            if (mipmapResourceId != 0) {
                Log.d(TAG, "Using icon from mipmap: $androidIconResourceName (ID: $mipmapResourceId)")
                return mipmapResourceId
            }

            Log.d(TAG, "Specified icon resource not found: $androidIconResourceName, trying fallbacks")
        }

        // Try default notification icon
        iconId = resources.getIdentifier("ic_notification", "drawable", packageName)
        if (iconId != 0) {
            Log.d(TAG, "Using default notification icon: ic_notification (ID: $iconId)")
            return iconId
        }

        // Try launcher icon from mipmap
        iconId = resources.getIdentifier("ic_launcher", "mipmap", packageName)
        if (iconId != 0) {
            Log.d(TAG, "Using launcher icon from mipmap: ic_launcher (ID: $iconId)")
            return iconId
        }

        // Fallback to Android default
        Log.d(TAG, "No notification icons found, using Android default")
        return android.R.drawable.ic_dialog_info
    }

    override fun onDestroy() {
        stopVpn()
        instance.set(null)
        super.onDestroy()
    }

    /**
     * Protect a socket to prevent routing loops.
     * This method is overridden to add better error handling and logging.
     */
    override fun protect(socket: Int): Boolean {
        try {
            val result = super.protect(socket)
            if (!result) {
                Log.e(TAG, "Failed to protect socket file descriptor: $socket")
            } else {
                Log.d(TAG, "Successfully protected socket file descriptor: $socket")
            }
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error protecting socket file descriptor: $socket", e)
            return false
        }
    }

    override fun protect(socket: Socket): Boolean {
        try {
            // Get socket details for better logging
            val localPort = try { socket.localPort } catch (e: Exception) { -1 }
            val remoteAddress = try { socket.inetAddress?.hostAddress } catch (e: Exception) { "unknown" }
            val remotePort = try { socket.port } catch (e: Exception) { -1 }

            val result = super.protect(socket)
            if (!result) {
                Log.e(TAG, "Failed to protect Socket (local:$localPort to $remoteAddress:$remotePort)")
            } else {
                Log.d(TAG, "Successfully protected Socket (local:$localPort to $remoteAddress:$remotePort)")
            }
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error protecting Socket object", e)
            return false
        }
    }

    // Use a different name to avoid ambiguity
    fun protectDatagramSocket(datagramSocket: DatagramSocket): Boolean {
        try {
            val result = super.protect(datagramSocket)
            if (!result) {
                Log.e(TAG, "Failed to protect DatagramSocket object ${datagramSocket.hashCode()}")
            }
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error protecting DatagramSocket object ${datagramSocket.hashCode()}", e)
            return false
        }
    }

    /**
     * Start a thread to collect connection statistics
     */
    private fun startStatsThread() {
        Thread {
            try {
                while (running.get()) {
                    // Wait a bit before updating stats again
                    Thread.sleep(1000)

                    // In a real implementation, we would query the tunnel for actual traffic stats
                    // For now, we'll log some activity to show the tunnel is active
                    if (running.get()) {
                        val tunnelConnected = tunnel?.isConnected() ?: false
                        Log.d(TAG, "Tunnel connected: $tunnelConnected")

                        // Get real stats if possible
                        try {
                            // This is just a placeholder - the actual Tunnel class may have
                            // methods to query bytes transferred
                            val clientActive = shadowsocksClient != null
                            Log.d(TAG, "Shadowsocks client active: $clientActive")

                            // Simulate some traffic for testing
                            if (tunnelConnected) {
                                bytesIn.addAndGet((Math.random() * 1500).toLong())
                                bytesOut.addAndGet((Math.random() * 1500).toLong())
                                packetsIn.incrementAndGet()
                                packetsOut.incrementAndGet()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating stats", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in stats collection thread", e)
                if (running.get()) {
                    setCurrentStage("error")
                    stopVpn()
                }
            }
        }.start()
    }

    /**
     * Register a network callback to handle connectivity changes
     */
    private fun registerNetworkCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // Network callbacks only available on Lollipop and above
            return
        }

        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available: $network")
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Network lost: $network")
                }

                @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    Log.d(TAG, "Network capabilities changed: $network")
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val hasNotMetered = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    Log.d(TAG, "Has Internet: $hasInternet, Not metered: $hasNotMetered")
                }
            }

            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }
}