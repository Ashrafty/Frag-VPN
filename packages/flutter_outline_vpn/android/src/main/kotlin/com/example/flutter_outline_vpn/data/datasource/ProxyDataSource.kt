package com.example.flutter_outline_vpn.data.datasource

import com.example.flutter_outline_vpn.data.VpnError
import com.example.flutter_outline_vpn.util.Logger
import com.example.flutter_outline_vpn.util.OutlineKeyParser
import com.example.flutter_outline_vpn.util.ProxyValidator
import org.json.JSONObject
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
                    
                    // Parse the Outline key
                    val uri = URI(outlineKey)
                    val userInfo = uri.userInfo
                    val decodedUserInfo = String(Base64.decode(userInfo, Base64.DEFAULT))
                    val parts = decodedUserInfo.split(":")
                    val method = parts[0]
                    val password = parts[1]
                    val host = uri.host
                    val port = uri.port
                    
                    // Build a JSON with alternative field names
                    val altJson = JSONObject().apply {
                        put("server", host)
                        put("server_port", port)
                        put("method", method)
                        put("password", password)
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