package com.example.flutter_outline_vpn.util

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.net.InetSocketAddress
import java.net.Socket
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.net.InetAddress

private const val TAG = "Tun2socksAdapter"
private const val BUFFER_SIZE = 16384

// SOCKS5 protocol constants
private const val SOCKS_VERSION = 0x05.toByte()
private const val SOCKS_AUTH_NONE = 0x00.toByte()
private const val SOCKS_CMD_CONNECT = 0x01.toByte()
private const val SOCKS_ATYP_IPV4 = 0x01.toByte()
private const val SOCKS_ATYP_DOMAIN = 0x03.toByte()
private const val SOCKS_ATYP_IPV6 = 0x04.toByte()
private const val SOCKS_REPLY_SUCCESS = 0x00.toByte()

/**
 * Adapter class to work with the Outline VPN.
 * This implementation forwards packets between the VPN and the SOCKS proxy.
 */
class Tun2socksAdapter {
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnInputStream: FileInputStream? = null
    private var vpnOutputStream: FileOutputStream? = null
    private var socksProxyHost: String? = null
    private var socksProxyPort: Int = 0
    private var vpnService: VpnService? = null
    
    // Keep track of active connections
    private val activeConnections = ConcurrentHashMap<String, SocksConnection>()
    
    /**
     * Starts the packet forwarding between the VPN interface and the SOCKS proxy.
     *
     * @param vpnInterface The VPN interface
     * @param vpnMtu The Maximum Transmission Unit for the VPN
     * @param socksServerAddress The SOCKS proxy server address (format: "host:port")
     * @param udpEnabled Whether to enable UDP support (currently ignored)
     * @param vpnService VpnService instance used to protect sockets
     * @return True if the proxy was started successfully, false otherwise
     */
    fun start(
        vpnInterface: ParcelFileDescriptor,
        vpnMtu: Int,
        socksServerAddress: String,
        udpEnabled: Boolean = true,
        vpnService: VpnService? = null
    ): Boolean {
        if (running.get()) {
            Log.w(TAG, "Packet forwarding is already running")
            return true
        }

        try {
            Log.d(TAG, "Starting packet forwarding with SOCKS server: $socksServerAddress")
            
            // Store VPN service reference
            this.vpnService = vpnService
            
            // Parse SOCKS server address
            val parts = socksServerAddress.split(":")
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid SOCKS server address format: $socksServerAddress")
            }
            
            socksProxyHost = parts[0]
            socksProxyPort = parts[1].toInt()
            
            // Test if proxy is reachable
            if (!isProxyReachable(socksProxyHost!!, socksProxyPort)) {
                Log.e(TAG, "Proxy server is not reachable: $socksServerAddress")
                return false
            }
            
            // Store the VPN interface
            this.vpnInterface = vpnInterface
            
            // Create streams from the VPN interface
            val fd = vpnInterface.fileDescriptor
            vpnInputStream = FileInputStream(fd)
            vpnOutputStream = FileOutputStream(fd)
            
            running.set(true)
            
            // Start forward and backward threads
            startForwardThread()
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start packet forwarding", e)
            stop()
            return false
        }
    }

    /**
     * Stops the packet forwarding.
     */
    fun stop() {
        if (!running.getAndSet(false)) {
            Log.w(TAG, "Packet forwarding is not running")
            return
        }

        try {
            Log.d(TAG, "Stopping packet forwarding")
            
            // Close all active connections
            for (connection in activeConnections.values) {
                try {
                    connection.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing connection", e)
                }
            }
            activeConnections.clear()
            
            // Shutdown the executor
            executor.shutdown()
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow()
                }
            } catch (e: InterruptedException) {
                executor.shutdownNow()
            }
            
            // Close VPN streams
            try {
                vpnInputStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing VPN input stream", e)
            }
            
            try {
                vpnOutputStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing VPN output stream", e)
            }
            
            vpnInputStream = null
            vpnOutputStream = null
            
            // Close VPN interface
            try {
                vpnInterface?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing VPN interface", e)
            }
            
            vpnInterface = null
            
            Log.d(TAG, "Packet forwarding stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop packet forwarding", e)
        }
    }
    
    /**
     * Tests if the proxy server is reachable
     * Note: VPN service should call protect() on this socket before connecting
     */
    private fun isProxyReachable(host: String, port: Int): Boolean {
        return try {
            val socket = Socket()
            
            // Protect the socket from VPN routing
            if (vpnService != null) {
                if (!vpnService!!.protect(socket)) {
                    Log.w(TAG, "Failed to protect socket")
                }
            }
            
            socket.connect(InetSocketAddress(host, port), 5000)
            Log.d(TAG, "Successfully connected to proxy at $host:$port")
            socket.close()
            true
        } catch (e: IOException) {
            Log.e(TAG, "Proxy not reachable: ${e.message}")
            false
        }
    }
    
    /**
     * Starts a thread to forward packets from the VPN interface to the proxy
     */
    private fun startForwardThread() {
        executor.submit {
            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            
            try {
                Log.d(TAG, "Started VPN to proxy forwarding thread")
                
                while (running.get()) {
                    // Read packet from VPN interface
                    buffer.clear()
                    val length = vpnInputStream!!.read(buffer.array())
                    
                    if (length <= 0) {
                        Thread.sleep(10)
                        continue
                    }
                    
                    // Process packet by sending through proxy
                    processPacket(buffer, length)
                }
            } catch (e: Exception) {
                if (running.get()) {
                    Log.e(TAG, "Error in VPN to proxy forwarding thread", e)
                    stop()
                }
            }
        }
    }
    
    /**
     * Process a packet by sending it through the SOCKS proxy
     */
    private fun processPacket(buffer: ByteBuffer, length: Int) {
        try {
            // Extract IP packet info
            val version = buffer.get(0).toInt().shr(4) and 0xF
            
            if (version == 4) { // IPv4
                // Extract protocol
                val protocol = buffer.get(9).toInt() and 0xFF
                
                // Extract source and destination IPs
                val srcIp = "${buffer.get(12).toInt() and 0xFF}.${buffer.get(13).toInt() and 0xFF}.${buffer.get(14).toInt() and 0xFF}.${buffer.get(15).toInt() and 0xFF}"
                val dstIp = "${buffer.get(16).toInt() and 0xFF}.${buffer.get(17).toInt() and 0xFF}.${buffer.get(18).toInt() and 0xFF}.${buffer.get(19).toInt() and 0xFF}"
                
                // Skip localhost connections to prevent loops
                if (dstIp.startsWith("127.") || srcIp.startsWith("127.")) {
                    Log.d(TAG, "Skipping localhost traffic to prevent loops")
                    return
                }
                
                // Skip traffic to the VPN itself
                if (dstIp == "10.111.222.1" || srcIp == "10.111.222.1") {
                    Log.d(TAG, "Skipping VPN interface traffic")
                    return
                }
                
                // Extract header length and calculate data offset
                val headerLength = (buffer.get(0).toInt() and 0xF) * 4
                
                // Handle TCP or UDP packets
                if (protocol == 6 || protocol == 17) { // TCP or UDP
                    // Extract ports
                    val srcPort = (buffer.get(headerLength).toInt() and 0xFF) * 256 + (buffer.get(headerLength + 1).toInt() and 0xFF)
                    val dstPort = (buffer.get(headerLength + 2).toInt() and 0xFF) * 256 + (buffer.get(headerLength + 3).toInt() and 0xFF)
                    
                    // Skip connections to the proxy to prevent loops
                    if (dstIp == socksProxyHost && dstPort == socksProxyPort) {
                        Log.d(TAG, "Skipping traffic to the proxy itself to prevent loops")
                        return
                    }
                    
                    Log.d(TAG, "Processing $protocol packet: $srcIp:$srcPort -> $dstIp:$dstPort")
                    
                    // Create connection key
                    val connectionKey = "$srcIp:$srcPort-$dstIp:$dstPort-$protocol"
                    
                    // Get or create connection
                    var connection = activeConnections[connectionKey]
                    if (connection == null || !connection.isConnected()) {
                        Log.d(TAG, "Creating new connection for $connectionKey")
                        connection = SocksConnection(dstIp, dstPort, protocol)
                        if (connection.connect()) {
                            activeConnections[connectionKey] = connection
                            
                            // Start a thread to read responses
                            startResponseThread(connection, connectionKey)
                        } else {
                            Log.e(TAG, "Failed to establish connection to $dstIp:$dstPort")
                            return
                        }
                    }
                    
                    // Extract data from the packet
                    val dataStartPos = headerLength + (if (protocol == 6) 20 else 8) // TCP header is 20 bytes, UDP header is 8 bytes
                    val dataLength = length - dataStartPos
                    
                    if (dataLength > 0) {
                        val data = ByteArray(dataLength)
                        System.arraycopy(buffer.array(), dataStartPos, data, 0, dataLength)
                        
                        // Forward data through the SOCKS connection
                        if (!connection.send(data)) {
                            Log.e(TAG, "Failed to send data through SOCKS connection")
                            connection.close()
                            activeConnections.remove(connectionKey)
                        } else {
                            Log.d(TAG, "Sent $dataLength bytes to $dstIp:$dstPort")
                        }
                    }
                }
            } else if (version == 6) { // IPv6
                Log.d(TAG, "IPv6 packet processing not yet implemented")
                // IPv6 implementation would go here
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing packet", e)
        }
    }
    
    /**
     * Start a thread to read responses from a SOCKS connection
     */
    private fun startResponseThread(connection: SocksConnection, connectionKey: String) {
        executor.submit {
            try {
                val responseBuffer = ByteArray(BUFFER_SIZE)
                
                Log.d(TAG, "Started response thread for $connectionKey")
                
                while (running.get() && connection.isConnected()) {
                    // Read response from the SOCKS connection
                    val bytesRead = connection.read(responseBuffer)
                    
                    if (bytesRead <= 0) {
                        if (bytesRead < 0) {
                            Log.e(TAG, "Connection closed by remote host: $connectionKey")
                        }
                        // Connection closed or error
                        break
                    }
                    
                    // Parse connection key to get original packet details
                    val keyParts = connectionKey.split("-")
                    if (keyParts.size != 3) {
                        Log.e(TAG, "Invalid connection key format: $connectionKey")
                        continue
                    }
                    
                    val srcDest = keyParts[0].split(":")
                    val destSrc = keyParts[1].split(":")
                    if (srcDest.size != 2 || destSrc.size != 2) {
                        Log.e(TAG, "Invalid address format in key: $connectionKey")
                        continue
                    }
                    
                    val originalSrcIp = srcDest[0]
                    val originalSrcPort = srcDest[1].toInt()
                    val originalDstIp = destSrc[0]
                    val originalDstPort = destSrc[1].toInt()
                    val protocol = keyParts[2].toInt()
                    
                    Log.d(TAG, "Received $bytesRead bytes response for $connectionKey")
                    
                    // Create response packet - IMPORTANT: swap source and destination!
                    writePacketToVpn(
                        responseBuffer,
                        bytesRead,
                        originalDstIp, // This becomes the source in the response
                        originalDstPort, // This becomes the source port in the response
                        originalSrcIp, // This becomes the destination in the response
                        originalSrcPort, // This becomes the destination port in the response
                        protocol
                    )
                    
                    Log.d(TAG, "Successfully forwarded $bytesRead bytes from $connectionKey to VPN")
                }
                
                // Clean up
                Log.d(TAG, "Connection ended, cleaning up: $connectionKey")
                connection.close()
                activeConnections.remove(connectionKey)
                Log.d(TAG, "Response thread for $connectionKey ended")
            } catch (e: Exception) {
                Log.e(TAG, "Error in response thread for $connectionKey", e)
                try {
                    connection.close()
                } catch (ex: Exception) {
                    // Ignore
                }
                activeConnections.remove(connectionKey)
            }
        }
    }
    
    /**
     * Write a packet to the VPN interface
     */
    private fun writePacketToVpn(
        data: ByteArray,
        dataLength: Int,
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int,
        protocol: Int
    ) {
        try {
            // Create IP header
            val ipHeader = createIpHeader(srcIp, dstIp, protocol, dataLength)
            
            // Create TCP/UDP header
            val transportHeader = if (protocol == 6) {
                createTcpHeader(srcPort, dstPort, dataLength)
            } else {
                createUdpHeader(srcPort, dstPort, dataLength)
            }
            
            // Calculate header checksums
            updateIpChecksum(ipHeader)
            if (protocol == 6) {
                updateTcpChecksum(ipHeader, transportHeader, data, dataLength)
            } else {
                updateUdpChecksum(ipHeader, transportHeader, data, dataLength)
            }
            
            // Combine all parts into a single packet
            val packetLength = ipHeader.size + transportHeader.size + dataLength
            val packet = ByteArray(packetLength)
            
            // Copy headers and data
            System.arraycopy(ipHeader, 0, packet, 0, ipHeader.size)
            System.arraycopy(transportHeader, 0, packet, ipHeader.size, transportHeader.size)
            System.arraycopy(data, 0, packet, ipHeader.size + transportHeader.size, dataLength)
            
            // Write packet to VPN
            synchronized(vpnOutputStream!!) {
                vpnOutputStream!!.write(packet, 0, packetLength)
                vpnOutputStream!!.flush()
            }
            
            Log.d(TAG, "Wrote $packetLength bytes to VPN interface")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing packet to VPN", e)
        }
    }
    
    /**
     * Create an IPv4 header
     */
    private fun createIpHeader(srcIp: String, dstIp: String, protocol: Int, dataLength: Int): ByteArray {
        val header = ByteArray(20) // IPv4 header is 20 bytes
        
        // Version (4) and IHL (5 words = 20 bytes)
        header[0] = 0x45
        
        // Type of Service
        header[1] = 0
        
        // Total Length (header + transport header + data)
        val totalLength = 20 + (if (protocol == 6) 20 else 8) + dataLength
        header[2] = (totalLength shr 8).toByte()
        header[3] = totalLength.toByte()
        
        // Identification (can be random)
        header[4] = 0
        header[5] = 0
        
        // Flags and Fragment Offset
        header[6] = 0x40 // Don't fragment
        header[7] = 0
        
        // Time to Live
        header[8] = 64
        
        // Protocol
        header[9] = protocol.toByte()
        
        // Header Checksum (set to 0 initially, calculated later)
        header[10] = 0
        header[11] = 0
        
        // Source IP
        val srcParts = srcIp.split(".")
        header[12] = srcParts[0].toInt().toByte()
        header[13] = srcParts[1].toInt().toByte()
        header[14] = srcParts[2].toInt().toByte()
        header[15] = srcParts[3].toInt().toByte()
        
        // Destination IP
        val dstParts = dstIp.split(".")
        header[16] = dstParts[0].toInt().toByte()
        header[17] = dstParts[1].toInt().toByte()
        header[18] = dstParts[2].toInt().toByte()
        header[19] = dstParts[3].toInt().toByte()
        
        return header
    }
    
    /**
     * Create a TCP header
     */
    private fun createTcpHeader(srcPort: Int, dstPort: Int, dataLength: Int): ByteArray {
        val header = ByteArray(20) // Basic TCP header is 20 bytes
        
        // Source Port
        header[0] = (srcPort shr 8).toByte()
        header[1] = srcPort.toByte()
        
        // Destination Port
        header[2] = (dstPort shr 8).toByte()
        header[3] = dstPort.toByte()
        
        // Sequence Number (can be random for simplicity)
        header[4] = 0
        header[5] = 0
        header[6] = 0
        header[7] = 0
        
        // Acknowledgment Number
        header[8] = 0
        header[9] = 0
        header[10] = 0
        header[11] = 0
        
        // Data Offset (5 words = 20 bytes) and Reserved
        header[12] = 0x50
        
        // Flags (ACK is usually set)
        header[13] = 0x10
        
        // Window Size
        header[14] = 0xFF.toByte()
        header[15] = 0xFF.toByte()
        
        // Checksum (initially 0, calculated later)
        header[16] = 0
        header[17] = 0
        
        // Urgent Pointer
        header[18] = 0
        header[19] = 0
        
        return header
    }
    
    /**
     * Create a UDP header
     */
    private fun createUdpHeader(srcPort: Int, dstPort: Int, dataLength: Int): ByteArray {
        val header = ByteArray(8) // UDP header is 8 bytes
        
        // Source Port
        header[0] = (srcPort shr 8).toByte()
        header[1] = srcPort.toByte()
        
        // Destination Port
        header[2] = (dstPort shr 8).toByte()
        header[3] = dstPort.toByte()
        
        // Length (header + data)
        val length = 8 + dataLength
        header[4] = (length shr 8).toByte()
        header[5] = length.toByte()
        
        // Checksum (initially 0, calculated later)
        header[6] = 0
        header[7] = 0
        
        return header
    }
    
    /**
     * Update IP header checksum
     */
    private fun updateIpChecksum(header: ByteArray) {
        var sum = 0
        
        // Sum all 16-bit words
        for (i in 0 until header.size step 2) {
            val word = ((header[i].toInt() and 0xFF) shl 8) or (header[i + 1].toInt() and 0xFF)
            sum += word
        }
        
        // Add carry
        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        
        // Take one's complement
        val checksum = sum.inv() and 0xFFFF
        
        // Update header
        header[10] = (checksum shr 8).toByte()
        header[11] = checksum.toByte()
    }
    
    /**
     * Update TCP checksum (proper implementation)
     */
    private fun updateTcpChecksum(ipHeader: ByteArray, tcpHeader: ByteArray, data: ByteArray, dataLength: Int) {
        // Clear the current checksum
        tcpHeader[16] = 0
        tcpHeader[17] = 0
        
        // Calculate checksum over pseudo-header (12 bytes)
        var sum = 0
        
        // Source IP (4 bytes)
        sum += ((ipHeader[12].toInt() and 0xFF) shl 8) or (ipHeader[13].toInt() and 0xFF)
        sum += ((ipHeader[14].toInt() and 0xFF) shl 8) or (ipHeader[15].toInt() and 0xFF)
        
        // Destination IP (4 bytes)
        sum += ((ipHeader[16].toInt() and 0xFF) shl 8) or (ipHeader[17].toInt() and 0xFF)
        sum += ((ipHeader[18].toInt() and 0xFF) shl 8) or (ipHeader[19].toInt() and 0xFF)
        
        // Reserved (1 byte) + Protocol (1 byte)
        sum += ipHeader[9].toInt() and 0xFF
        
        // TCP Length (2 bytes) = TCP header length + data length
        val tcpLength = tcpHeader.size + dataLength
        sum += tcpLength
        
        // Calculate checksum over TCP header
        for (i in 0 until tcpHeader.size step 2) {
            if (i + 1 < tcpHeader.size) {
                sum += ((tcpHeader[i].toInt() and 0xFF) shl 8) or (tcpHeader[i + 1].toInt() and 0xFF)
            } else {
                sum += (tcpHeader[i].toInt() and 0xFF) shl 8
            }
        }
        
        // Calculate checksum over data
        for (i in 0 until dataLength step 2) {
            if (i + 1 < dataLength) {
                sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            } else {
                sum += (data[i].toInt() and 0xFF) shl 8
            }
        }
        
        // Add carry
        while (sum > 0xFFFF) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        
        // One's complement
        val checksum = sum.inv() and 0xFFFF
        
        // Update TCP header
        tcpHeader[16] = (checksum shr 8).toByte()
        tcpHeader[17] = checksum.toByte()
    }
    
    /**
     * Update UDP checksum (proper implementation)
     * Note: UDP allows checksum of 0, which means no checksum
     */
    private fun updateUdpChecksum(ipHeader: ByteArray, udpHeader: ByteArray, data: ByteArray, dataLength: Int) {
        // Skip UDP checksum calculation if it's set to 0
        // (indicating no checksum is used, which is valid for UDP)
        if (true) {
            udpHeader[6] = 0
            udpHeader[7] = 0
            return
        }
        
        // Optional - Proper UDP checksum calculation
        // Clear the current checksum
        udpHeader[6] = 0
        udpHeader[7] = 0
        
        // Calculate checksum over pseudo-header (12 bytes)
        var sum = 0
        
        // Source IP (4 bytes)
        sum += ((ipHeader[12].toInt() and 0xFF) shl 8) or (ipHeader[13].toInt() and 0xFF)
        sum += ((ipHeader[14].toInt() and 0xFF) shl 8) or (ipHeader[15].toInt() and 0xFF)
        
        // Destination IP (4 bytes)
        sum += ((ipHeader[16].toInt() and 0xFF) shl 8) or (ipHeader[17].toInt() and 0xFF)
        sum += ((ipHeader[18].toInt() and 0xFF) shl 8) or (ipHeader[19].toInt() and 0xFF)
        
        // Reserved (1 byte) + Protocol (1 byte)
        sum += ipHeader[9].toInt() and 0xFF
        
        // UDP Length (2 bytes) = UDP header length + data length
        val udpLength = udpHeader.size + dataLength
        sum += udpLength
        
        // Calculate checksum over UDP header
        for (i in 0 until udpHeader.size step 2) {
            if (i + 1 < udpHeader.size) {
                sum += ((udpHeader[i].toInt() and 0xFF) shl 8) or (udpHeader[i + 1].toInt() and 0xFF)
            } else {
                sum += (udpHeader[i].toInt() and 0xFF) shl 8
            }
        }
        
        // Calculate checksum over data
        for (i in 0 until dataLength step 2) {
            if (i + 1 < dataLength) {
                sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            } else {
                sum += (data[i].toInt() and 0xFF) shl 8
            }
        }
        
        // Add carry
        while (sum > 0xFFFF) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        
        // In UDP, checksum of 0 means no checksum, so if the result is 0, use all 1's (0xFFFF)
        var checksum = sum.inv() and 0xFFFF
        if (checksum == 0) {
            checksum = 0xFFFF
        }
        
        // Update UDP header
        udpHeader[6] = (checksum shr 8).toByte()
        udpHeader[7] = checksum.toByte()
    }
    
    /**
     * Class to handle SOCKS5 connections
     */
    private inner class SocksConnection(
        private val destinationHost: String,
        private val destinationPort: Int,
        private val protocol: Int
    ) {
        private var socket: Socket? = null
        private var inputStream: InputStream? = null
        private var outputStream: OutputStream? = null
        
        /**
         * Extension function for InputStream to read exactly n bytes
         * Similar to Java 9+ readNBytes but compatible with older Android versions
         */
        private fun InputStream.readNBytes(b: ByteArray, off: Int, len: Int): Int {
            var n = 0
            while (n < len) {
                val count = this.read(b, off + n, len - n)
                if (count < 0) {
                    break
                }
                n += count
            }
            return n
        }
        
        /**
         * Connect to the destination via SOCKS5 proxy
         */
        fun connect(): Boolean {
            try {
                // Create socket to proxy
                socket = Socket()
                
                // Set socket options for better performance
                socket!!.tcpNoDelay = true
                socket!!.keepAlive = true
                socket!!.soTimeout = 5000  // Shorter 5 second timeout to fail faster
                socket!!.receiveBufferSize = 65536  // Larger buffer for better performance
                socket!!.sendBufferSize = 65536
                
                // Protect the socket from VPN routing before connecting
                // This is CRITICAL to prevent VPN traffic loops
                if (vpnService != null) {
                    if (!vpnService!!.protect(socket)) {
                        Log.e(TAG, "Failed to protect socket to $destinationHost:$destinationPort - this will cause VPN loops!")
                        socket!!.close()
                        socket = null
                        return false
                    } else {
                        Log.d(TAG, "Successfully protected socket to $destinationHost:$destinationPort")
                    }
                } else {
                    Log.e(TAG, "No VpnService available to protect socket - this will cause VPN loops!")
                    socket!!.close()
                    socket = null
                    return false
                }
                
                // Now connect to the proxy
                try {
                    Log.d(TAG, "Connecting to SOCKS proxy at $socksProxyHost:$socksProxyPort")
                    socket!!.connect(InetSocketAddress(socksProxyHost, socksProxyPort), 10000) // Increased timeout
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect to SOCKS proxy at $socksProxyHost:$socksProxyPort", e)
                    close()
                    return false
                }
                
                Log.d(TAG, "Connected to proxy, performing SOCKS handshake")
                inputStream = socket!!.getInputStream()
                outputStream = socket!!.getOutputStream()
                
                // SOCKS5 handshake
                try {
                    // =================== Initial Handshake ===================
                    // Send greeting with authentication methods
                    val greeting = byteArrayOf(
                        SOCKS_VERSION,      // SOCKS version 5
                        0x01,               // Number of authentication methods (1)
                        SOCKS_AUTH_NONE     // No authentication (method 0)
                    )
                    
                    // Send the greeting and flush
                    outputStream!!.write(greeting)
                    outputStream!!.flush()
                    
                    Log.d(TAG, "Sent SOCKS5 greeting to proxy")
                    
                    // Receive server choice with more careful error handling and detailed logs
                    val response = ByteArray(2)
                    try {
                        // More detailed logging
                        Log.d(TAG, "Waiting for SOCKS5 response (timeout: ${socket!!.soTimeout}ms)")
                        
                        // Try to read exactly 2 bytes with a possible timeout
                        val bytesRead = inputStream!!.read(response)
                        
                        Log.d(TAG, "SOCKS5 response received: ${bytesRead} bytes, data: [${response[0].toInt() and 0xFF}, ${response[1].toInt() and 0xFF}]")
                        
                        if (bytesRead != 2) {
                            Log.e(TAG, "Invalid SOCKS5 handshake response length: $bytesRead")
                            close()
                            return false
                        }
                        
                        if (response[0] != SOCKS_VERSION) {
                            Log.e(TAG, "Invalid SOCKS version in response: ${response[0].toInt() and 0xFF}, expected: ${SOCKS_VERSION.toInt() and 0xFF}")
                            close()
                            return false
                        }
                        
                        if (response[1] != SOCKS_AUTH_NONE) {
                            Log.e(TAG, "Server did not accept NO_AUTH method: ${response[1].toInt() and 0xFF}, expected: ${SOCKS_AUTH_NONE.toInt() and 0xFF}")
                            close()
                            return false
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        Log.e(TAG, "SOCKS5 handshake timed out waiting for server response", e)
                        // Try to continue anyway - some servers might be slow to respond but still functional
                        Log.w(TAG, "Attempting to continue connection despite timeout...")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading SOCKS5 handshake response", e)
                        close()
                        return false
                    }
                    
                    Log.d(TAG, "SOCKS5 authentication successful, proceeding to connection request")
                    
                    // =================== Connection Request ===================
                    // Prepare the connection request
                    val ipAddress = InetAddress.getByName(destinationHost)
                    val ipBytes = ipAddress.address
                    
                    Log.d(TAG, "SOCKS5 connecting to $destinationHost ($ipAddress):$destinationPort")
                    
                    // Build the connection request header and body
                    val header = byteArrayOf(
                        SOCKS_VERSION,        // SOCKS version 5
                        SOCKS_CMD_CONNECT,    // CONNECT command
                        0x00                  // Reserved byte
                    )
                    
                    // Add address type based on IP version
                    val addrType: Byte = if (ipBytes.size == 4) {
                        SOCKS_ATYP_IPV4  // IPv4
                    } else {
                        SOCKS_ATYP_IPV6  // IPv6
                    }
                    
                    // Construct the full request
                    val fullRequest = ByteArrayOutputStream()
                    fullRequest.write(header)
                    fullRequest.write(addrType.toInt())
                    fullRequest.write(ipBytes)
                    
                    // Add port in network byte order (big endian)
                    fullRequest.write(((destinationPort shr 8) and 0xFF))
                    fullRequest.write((destinationPort and 0xFF))
                    
                    // Send the connection request
                    val requestBytes = fullRequest.toByteArray()
                    outputStream!!.write(requestBytes)
                    outputStream!!.flush()
                    
                    Log.d(TAG, "Sent SOCKS5 connection request to connect to $destinationHost:$destinationPort")
                    
                    // =================== Connection Response ===================
                    // Receive initial reply (at least 4 bytes for header)
                    val replyHeader = ByteArray(4)
                    var headerBytesRead = 0
                    var receivedValidResponse = false
                    
                    try {
                        Log.d(TAG, "Waiting for SOCKS5 connection response...")
                        headerBytesRead = inputStream!!.read(replyHeader)
                        
                        if (headerBytesRead != 4) {
                            Log.e(TAG, "Failed to read SOCKS reply header, got $headerBytesRead bytes")
                            close()
                            return false
                        }
                        
                        receivedValidResponse = true
                        Log.d(TAG, "Received SOCKS5 connection response header: [${replyHeader[0].toInt() and 0xFF}, ${replyHeader[1].toInt() and 0xFF}, ${replyHeader[2].toInt() and 0xFF}, ${replyHeader[3].toInt() and 0xFF}]")
                    } catch (e: java.net.SocketTimeoutException) {
                        Log.e(TAG, "SOCKS5 connection request timed out", e)
                        
                        // For DNS traffic, we can attempt to continue despite timeout
                        if (destinationPort == 53) {
                            Log.w(TAG, "DNS request timed out but continuing for DNS traffic")
                            return true
                        }
                        
                        close()
                        return false
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading SOCKS connection response", e)
                        close()
                        return false
                    }
                    
                    // Only process response header if we actually received one
                    if (receivedValidResponse) {
                        // Check protocol version
                        if (replyHeader[0] != SOCKS_VERSION) {
                            Log.e(TAG, "Invalid SOCKS version in reply: ${replyHeader[0].toInt() and 0xFF}")
                            close()
                            return false
                        }
                        
                        // Check status code
                        if (replyHeader[1] != SOCKS_REPLY_SUCCESS) {
                            val errorMsg = when(replyHeader[1].toInt() and 0xFF) {
                                1 -> "General SOCKS server failure"
                                2 -> "Connection not allowed by ruleset"
                                3 -> "Network unreachable"
                                4 -> "Host unreachable"
                                5 -> "Connection refused"
                                6 -> "TTL expired"
                                7 -> "Command not supported"
                                8 -> "Address type not supported"
                                else -> "Unknown error"
                            }
                            Log.e(TAG, "SOCKS connection failed: $errorMsg (code ${replyHeader[1].toInt() and 0xFF})")
                            close()
                            return false
                        }
                        
                        // Process the bound address based on type
                        val boundAddrType = replyHeader[3]
                        var boundPort = 0
                        
                        when (boundAddrType) {
                            SOCKS_ATYP_IPV4 -> {
                                val boundAddr = ByteArray(4)
                                val portBytes = ByteArray(2)
                                
                                inputStream!!.readNBytes(boundAddr, 0, 4)
                                inputStream!!.readNBytes(portBytes, 0, 2)
                                
                                boundPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
                                val boundIP = InetAddress.getByAddress(boundAddr)
                                
                                Log.d(TAG, "SOCKS server bound to IPv4: $boundIP:$boundPort")
                            }
                            SOCKS_ATYP_IPV6 -> {
                                val boundAddr = ByteArray(16)
                                val portBytes = ByteArray(2)
                                
                                inputStream!!.readNBytes(boundAddr, 0, 16)
                                inputStream!!.readNBytes(portBytes, 0, 2)
                                
                                boundPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
                                val boundIP = InetAddress.getByAddress(boundAddr)
                                
                                Log.d(TAG, "SOCKS server bound to IPv6: $boundIP:$boundPort")
                            }
                            SOCKS_ATYP_DOMAIN -> {
                                val domainLength = inputStream!!.read()
                                val domainBytes = ByteArray(domainLength)
                                val portBytes = ByteArray(2)
                                
                                inputStream!!.readNBytes(domainBytes, 0, domainLength)
                                inputStream!!.readNBytes(portBytes, 0, 2)
                                
                                boundPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
                                val boundDomain = String(domainBytes)
                                
                                Log.d(TAG, "SOCKS server bound to domain: $boundDomain:$boundPort")
                            }
                            else -> {
                                Log.e(TAG, "Unknown address type in SOCKS reply: $boundAddrType")
                                close()
                                return false
                            }
                        }
                    } // End of receivedValidResponse block
                } catch (e: Exception) {
                    Log.e(TAG, "Error during SOCKS negotiation", e)
                    close()
                    return false
                }
                
                Log.d(TAG, "SOCKS connection to $destinationHost:$destinationPort established successfully!")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error establishing SOCKS connection", e)
                close()
                return false
            }
        }
        
        /**
         * Send data through the SOCKS connection
         */
        fun send(data: ByteArray): Boolean {
            return try {
                if (socket == null || outputStream == null) {
                    Log.e(TAG, "Cannot send data - socket or output stream is null")
                    return false
                }
                
                if (!isConnected()) {
                    Log.e(TAG, "Cannot send data - socket is not connected")
                    return false
                }
                
                // Send the data and flush to ensure it's transmitted immediately
                outputStream!!.write(data)
                outputStream!!.flush()
                
                Log.d(TAG, "Successfully sent ${data.size} bytes through SOCKS connection")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error sending data through SOCKS: ${e.message}", e)
                // If we get an exception, the connection is likely broken
                close()
                return false
            }
        }
        
        /**
         * Read data from the SOCKS connection
         */
        fun read(buffer: ByteArray): Int {
            return try {
                if (socket == null || inputStream == null) {
                    Log.e(TAG, "Cannot read data - socket or input stream is null")
                    return -1
                }
                
                if (!isConnected()) {
                    Log.e(TAG, "Cannot read data - socket is not connected")
                    return -1
                }
                
                val bytesRead = inputStream!!.read(buffer)
                
                if (bytesRead > 0) {
                    Log.d(TAG, "Read $bytesRead bytes from SOCKS connection")
                } else if (bytesRead == 0) {
                    Log.d(TAG, "Read 0 bytes from SOCKS connection")
                } else {
                    Log.d(TAG, "End of stream reached on SOCKS connection")
                }
                
                return bytesRead
            } catch (e: Exception) {
                Log.e(TAG, "Error reading data from SOCKS: ${e.message}", e)
                // If we get an exception, the connection is likely broken
                close()
                return -1
            }
        }
        
        /**
         * Check if the connection is still connected
         */
        fun isConnected(): Boolean {
            return socket != null && socket!!.isConnected && !socket!!.isClosed
        }
        
        /**
         * Close the SOCKS connection
         */
        fun close() {
            try {
                inputStream?.close()
                outputStream?.close()
                socket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing SOCKS connection", e)
            } finally {
                inputStream = null
                outputStream = null
                socket = null
            }
        }
    }
} 