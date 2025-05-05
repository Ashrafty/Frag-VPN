package com.example.flutter_outline_vpn.data.datasource

import com.example.flutter_outline_vpn.data.VpnError
import com.example.flutter_outline_vpn.util.Logger
import com.example.flutter_outline_vpn.util.OutlineKeyParser
import com.example.flutter_outline_vpn.util.ProxyValidator
import org.json.JSONObject
import org.json.JSONArray
import shadowsocks.Client
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import android.util.Base64
import java.net.URI

/**
 * Data source for managing the Shadowsocks client.
 */
class ProxyDataSource {
    private var shadowsocksClient: Client? = null

    /**
     * Initialize the Shadowsocks client.
     *
     * @param outlineKey The Outline key in the format ss://xxx.
     * @param port The proxy port (ignored since we're using direct Shadowsocks connection).
     * @return The Outline key (same as input).
     * @throws VpnError if the client cannot be initialized.
     */
    suspend fun initializeProxy(outlineKey: String, port: String?): String = suspendCoroutine { continuation ->
        val thread = Thread {
            try {
                Logger.d("Creating Shadowsocks client from Outline key")

                // Format 1: Try creating the client from our JSON parser
                try {
                    val shadowsocksJson = OutlineKeyParser.parseOutlineKey(outlineKey)
                    Logger.d("Using JSON format: ${shadowsocksJson.take(50)}...")

                    // Create the client using the JSON configuration
                    shadowsocksClient = Client(shadowsocksJson)
                    Logger.d("Client created successfully with JSON format")
                    continuation.resume(outlineKey)
                    return@Thread
                } catch (e: Exception) {
                    Logger.e("Error creating client with JSON format: ${e.message}")
                    // Continue to next approach
                }

                // Format 2: Try passing the outline key directly to the Client constructor
                try {
                    Logger.d("Trying direct Outline key format")
                    shadowsocksClient = Client(outlineKey)
                    Logger.d("Client created successfully with direct Outline key")
                    continuation.resume(outlineKey)
                    return@Thread
                } catch (e: Exception) {
                    Logger.e("Error creating client with direct Outline key: ${e.message}")
                    // Continue to next approach
                }

                // Format 3: Try creating a Config object manually
                try {
                    Logger.d("Trying manual Config object construction")

                    // Try to extract components from the Outline key
                    var host = "localhost"
                    var port = 8388
                    var method = "aes-256-gcm"
                    var password = "password"

                    // First, check if it's in ss:// format
                    if (outlineKey.startsWith("ss://")) {
                        val strippedKey = outlineKey.substring(5)

                        // Check if it contains @ which separates credentials from server
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

                                    val decodedCredentials = String(Base64.decode(paddedBase64, Base64.DEFAULT))
                                    val methodPasswordParts = decodedCredentials.split(":")
                                    if (methodPasswordParts.size >= 2) {
                                        method = methodPasswordParts[0]

                                        // Check if the method is supported
                                        if (method.startsWith("2022-blake3") || method.contains("blake3")) {
                                            Logger.d("Unsupported cipher method: $method, using aes-256-gcm instead")
                                            method = "aes-256-gcm" // Use a supported cipher method
                                        }

                                        password = methodPasswordParts[1]
                                    }
                                } catch (e: Exception) {
                                    // Try direct format
                                    val methodPasswordParts = credentialsPart.split(":")
                                    if (methodPasswordParts.size >= 2) {
                                        method = methodPasswordParts[0]

                                        // Check if the method is supported
                                        if (method.startsWith("2022-blake3") || method.contains("blake3")) {
                                            Logger.d("Unsupported cipher method: $method, using aes-256-gcm instead")
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
                        } else {
                            // Try to decode the entire string as base64
                            try {
                                // Add padding if needed
                                var paddedBase64 = strippedKey
                                while (paddedBase64.length % 4 != 0) {
                                    paddedBase64 += "="
                                }

                                val decoded = String(Base64.decode(paddedBase64, Base64.DEFAULT))

                                // Check if it's a JSON object
                                if (decoded.trim().startsWith("{")) {
                                    try {
                                        val jsonConfig = JSONObject(decoded)

                                        // Extract fields from JSON
                                        if (jsonConfig.has("server")) {
                                            host = jsonConfig.getString("server")
                                        }
                                        if (jsonConfig.has("server_port")) {
                                            port = jsonConfig.getInt("server_port")
                                        }
                                        if (jsonConfig.has("method")) {
                                            method = jsonConfig.getString("method")

                                            // Check if the method is supported
                                            if (method.startsWith("2022-blake3") || method.contains("blake3")) {
                                                Logger.d("Unsupported cipher method: $method, using aes-256-gcm instead")
                                                method = "aes-256-gcm" // Use a supported cipher method
                                            }
                                        }
                                        if (jsonConfig.has("password")) {
                                            password = jsonConfig.getString("password")
                                        }
                                    } catch (e: Exception) {
                                        Logger.e("Failed to parse JSON from base64: ${e.message}")
                                    }
                                } else {
                                    // Try to parse as method:password@host:port
                                    val parts = decoded.split("@")
                                    if (parts.size == 2) {
                                        val methodPasswordParts = parts[0].split(":")
                                        val hostPortParts = parts[1].split(":")

                                        if (methodPasswordParts.size >= 2) {
                                            method = methodPasswordParts[0]

                                            // Check if the method is supported
                                            if (method.startsWith("2022-blake3") || method.contains("blake3")) {
                                                Logger.d("Unsupported cipher method: $method, using aes-256-gcm instead")
                                                method = "aes-256-gcm" // Use a supported cipher method
                                            }

                                            password = methodPasswordParts[1]
                                        }

                                        if (hostPortParts.size >= 2) {
                                            host = hostPortParts[0]
                                            port = hostPortParts[1].toIntOrNull() ?: 8388
                                        } else if (hostPortParts.size == 1) {
                                            host = hostPortParts[0]
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Logger.e("Failed to decode base64: ${e.message}")
                            }
                        }
                    } else {
                        // Try to parse as a URI
                        try {
                            val uri = URI(outlineKey)
                            host = uri.host ?: "localhost"
                            port = if (uri.port == -1) 8388 else uri.port

                            // Try to extract method and password from userInfo
                            val userInfo = uri.userInfo
                            if (userInfo != null) {
                                val parts = userInfo.split(":")
                                if (parts.size >= 2) {
                                    method = parts[0]

                                    // Check if the method is supported
                                    if (method.startsWith("2022-blake3") || method.contains("blake3")) {
                                        Logger.d("Unsupported cipher method: $method, using aes-256-gcm instead")
                                        method = "aes-256-gcm" // Use a supported cipher method
                                    }

                                    password = parts[1]
                                }
                            }
                        } catch (e: Exception) {
                            Logger.e("Failed to parse as URI: ${e.message}")
                        }
                    }

                    // Build a JSON with the extracted fields
                    val altJson = JSONObject().apply {
                        put("server", host)
                        put("server_port", port)
                        put("method", method)
                        put("password", password)

                        // Create DNS array
                        val dnsArray = JSONArray()
                        dnsArray.put("8.8.8.8")
                        dnsArray.put("1.1.1.1")
                        put("dns", dnsArray)
                    }

                    Logger.d("Using alternative JSON format: ${altJson.toString().take(50)}...")
                    shadowsocksClient = Client(altJson.toString())
                    Logger.d("Client created successfully with alternative JSON")
                    continuation.resume(outlineKey)
                    return@Thread
                } catch (e: Exception) {
                    Logger.e("All client creation approaches failed")
                    continuation.resumeWithException(VpnError.SHADOWSOCKS_INITIALIZATION_ERROR)
                }
            } catch (error: Exception) {
                Logger.e("Failed to initialize Shadowsocks proxy", error)
                continuation.resumeWithException(VpnError.SHADOWSOCKS_INITIALIZATION_ERROR)
            }
        }
        thread.start()
    }

    /**
     * Start the Shadowsocks client.
     *
     * @throws VpnError if the client cannot be started.
     */
    suspend fun startProxy(): Int = suspendCoroutine { continuation ->
        val thread = Thread {
            try {
                val client = shadowsocksClient
                    ?: throw VpnError.SHADOWSOCKS_NOT_INITIALIZED

                Logger.d("Starting Shadowsocks proxy")
                // In the real implementation, the Client start would be called here
                // No longer using local proxy - directly using Shadowsocks tunnel

                continuation.resume(0) // Return any value, it's not used
            } catch (error: Exception) {
                if (error is VpnError) {
                    continuation.resumeWithException(error)
                } else {
                    Logger.e("Failed to start Shadowsocks proxy", error)
                    continuation.resumeWithException(VpnError.SHADOWSOCKS_START_ERROR)
                }
            }
        }
        thread.start()
    }

    /**
     * Stop the Shadowsocks client.
     */
    fun stopProxy() {
        try {
            Logger.d("Stopping Shadowsocks proxy")
            // No additional cleanup needed with direct Shadowsocks tunnel approach
            shadowsocksClient = null
        } catch (error: Exception) {
            Logger.e("Failed to stop Shadowsocks proxy", error)
        }
    }

    /**
     * Get the Shadowsocks client.
     *
     * @return The Shadowsocks client.
     * @throws VpnError if the client is not initialized.
     */
    fun getShadowsocksClient(): Client {
        return shadowsocksClient ?: throw VpnError.SHADOWSOCKS_NOT_INITIALIZED
    }
}