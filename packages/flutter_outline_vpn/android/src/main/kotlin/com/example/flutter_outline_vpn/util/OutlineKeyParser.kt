package com.example.flutter_outline_vpn.util

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Utility class to parse Outline keys into the format expected by the Shadowsocks client.
 */
object OutlineKeyParser {
    private const val TAG = "OutlineKeyParser"

    /**
     * Parse an Outline key (ss:// URL format) into a JSON configuration string.
     *
     * @param outlineKey The Outline key in ss:// format
     * @return A JSON string with the Shadowsocks configuration
     * @throws IllegalArgumentException if the key is invalid
     */
    fun parseOutlineKey(outlineKey: String): String {
        try {
            // Validate basic format
            if (!outlineKey.startsWith("ss://")) {
                throw IllegalArgumentException("Not a valid Outline key. Must start with ss://")
            }

            // For Outline keys, we need to handle a few different formats
            // Format 1: ss://base64(method:password)@server:port
            // Format 2: ss://base64(method:password@server:port)
            
            // Extract the components after removing the ss:// prefix
            val strippedKey = outlineKey.substring(5)
            
            // Check if we have a '@' sign which separates credentials from server
            val components = strippedKey.split("@")
            
            var method: String
            var password: String
            var host: String
            var port: Int
            
            if (components.size == 2) {
                // Format 1: credentials are base64-encoded separately from server
                val credentialsPart = components[0]
                val serverPart = components[1]
                
                // Decode the base64-encoded credentials
                val decodedCredentials = String(Base64.decode(credentialsPart, Base64.DEFAULT), StandardCharsets.UTF_8)
                val methodPasswordParts = decodedCredentials.split(":")
                
                if (methodPasswordParts.size != 2) {
                    throw IllegalArgumentException("Invalid credentials format in Outline key")
                }
                
                method = methodPasswordParts[0]
                password = methodPasswordParts[1]
                
                // Extract host and port from server part
                val hostPortParts = serverPart.split(":")
                if (hostPortParts.size != 2) {
                    throw IllegalArgumentException("Invalid server format in Outline key")
                }
                
                host = hostPortParts[0]
                // Handle URL parameters if present
                val portString = hostPortParts[1].split("/")[0].split("?")[0]
                port = portString.toInt()
            } else {
                // Try alternate parsing approach for more complex formats
                try {
                    val uri = URI(outlineKey)
                    
                    // Extract path and query from URI
                    val pathAndQuery = uri.toString().substring(uri.toString().indexOf(uri.host) + uri.host.length)
                    
                    // Extract host from URI
                    host = uri.host ?: throw IllegalArgumentException("Missing host in Outline key")
                    
                    // Extract port from URI
                    port = if (uri.port == -1) 8388 else uri.port  // Default Shadowsocks port is 8388
                    
                    // Extract the user info portion (base64 encoded method:password)
                    val userInfo = uri.userInfo 
                    
                    if (userInfo != null) {
                        // Standard format
                        val decodedUserInfo = String(Base64.decode(userInfo, Base64.DEFAULT), StandardCharsets.UTF_8)
                        val methodPasswordParts = decodedUserInfo.split(":")
                        
                        if (methodPasswordParts.size != 2) {
                            throw IllegalArgumentException("Invalid credentials format in Outline key")
                        }
                        
                        method = methodPasswordParts[0]
                        password = methodPasswordParts[1]
                    } else {
                        // Handle the format where everything is base64 encoded
                        // Extract the base64 part between ss:// and @ or /
                        val base64End = if (strippedKey.contains("@")) strippedKey.indexOf("@") else strippedKey.length
                        val base64Part = strippedKey.substring(0, base64End)
                        
                        // Decode the entire base64 string
                        try {
                            val decoded = String(Base64.decode(base64Part, Base64.DEFAULT), StandardCharsets.UTF_8)
                            Log.d(TAG, "Decoded base64: $decoded")
                            
                            // Extract method, password, host, port
                            val parts = decoded.split("@")
                            if (parts.size == 2) {
                                val methodPassword = parts[0].split(":")
                                val hostPort = parts[1].split(":")
                                
                                if (methodPassword.size != 2 || hostPort.size != 2) {
                                    throw IllegalArgumentException("Invalid format in decoded base64")
                                }
                                
                                method = methodPassword[0]
                                password = methodPassword[1]
                                // Use host and port from the URI for consistency
                            } else {
                                throw IllegalArgumentException("Invalid format in decoded base64")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to decode base64 part", e)
                            throw IllegalArgumentException("Invalid base64 encoding in Outline key")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse URI", e)
                    throw IllegalArgumentException("Failed to parse Outline key URI: ${e.message}")
                }
            }
            
            // Create the JSON structure that matches what the Shadowsocks client expects
            val config = JSONObject().apply {
                put("host", host)
                put("port", port)
                put("password", password)
                put("cipher", method)
                // Adding method explicitly as some clients look for it
                put("method", method)
                // Add server field which might be used by some implementations
                put("server", host)
                put("server_port", port)
                // Use a proper JSON array for the 'dns' field
                put("dns", JSONArray(listOf("8.8.8.8", "1.1.1.1")))
            }
            
            Log.d(TAG, "Successfully parsed Outline key")
            Log.d(TAG, "Config: $config")
            
            return config.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Outline key", e)
            throw IllegalArgumentException("Failed to parse Outline key: ${e.message}", e)
        }
    }

    /**
     * Test function to verify parsing of an Outline key.
     * This function can be exposed through the Flutter method channel for testing.
     *
     * @param outlineKey The Outline key to test
     * @return A string with the parsing results and the generated JSON
     */
    fun testOutlineKeyParsing(outlineKey: String): String {
        return try {
            val jsonConfig = parseOutlineKey(outlineKey)
            val prettyJson = JSONObject(jsonConfig).toString(2) // Pretty-print with 2-space indentation
            """
            Successfully parsed Outline key.
            
            Host: ${JSONObject(jsonConfig).optString("host")}
            Port: ${JSONObject(jsonConfig).optInt("port")}
            Method: ${JSONObject(jsonConfig).optString("cipher")}
            Password: ${JSONObject(jsonConfig).optString("password")}
            
            JSON Configuration:
            $prettyJson
            """.trimIndent()
        } catch (e: Exception) {
            "Failed to parse Outline key: ${e.message}"
        }
    }
} 