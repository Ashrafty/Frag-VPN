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
            Log.d(TAG, "Parsing Outline key: ${outlineKey.take(20)}...")

            // Validate basic format
            if (!outlineKey.startsWith("ss://")) {
                throw IllegalArgumentException("Not a valid Outline key. Must start with ss://")
            }

            // For Outline keys, we need to handle a few different formats
            // Format 1: ss://base64(method:password)@server:port
            // Format 2: ss://method:password@server:port (not base64 encoded)
            // Format 3: ss://base64(method:password@server:port)
            // Format 4: ss://base64({"server":"host","server_port":port,"method":"method","password":"password"})

            // First, try to parse as a direct JSON object (some clients provide this)
            try {
                // Check if the key might be a JSON object (after removing ss://)
                val jsonCandidate = outlineKey.substring(5)

                // Try to decode as base64 first
                try {
                    // Add padding if needed
                    var paddedBase64 = jsonCandidate
                    while (paddedBase64.length % 4 != 0) {
                        paddedBase64 += "="
                    }

                    val decodedJson = String(Base64.decode(paddedBase64, Base64.DEFAULT), StandardCharsets.UTF_8)

                    // Check if it starts with { which would indicate JSON
                    if (decodedJson.trim().startsWith("{")) {
                        try {
                            val jsonConfig = JSONObject(decodedJson)

                            // Verify it has the required fields
                            if (jsonConfig.has("server") && jsonConfig.has("server_port") &&
                                jsonConfig.has("method") && jsonConfig.has("password")) {

                                // Add DNS if not present
                                if (!jsonConfig.has("dns")) {
                                    val dnsArray = JSONArray()
                                    dnsArray.put("8.8.8.8")
                                    dnsArray.put("1.1.1.1")
                                    jsonConfig.put("dns", dnsArray)
                                }

                                // Check if the method is supported
                                if (jsonConfig.has("method")) {
                                    val method = jsonConfig.getString("method")
                                    if (method.startsWith("2022-blake3") || method.contains("blake3")) {
                                        Log.d(TAG, "Unsupported cipher method: $method, using aes-256-gcm instead")
                                        jsonConfig.put("method", "aes-256-gcm")
                                        // Also update cipher field if present
                                        if (jsonConfig.has("cipher")) {
                                            jsonConfig.put("cipher", "aes-256-gcm")
                                        }
                                    }
                                }

                                Log.d(TAG, "Successfully parsed as base64-encoded JSON")
                                return jsonConfig.toString()
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Not a valid JSON after base64 decoding: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Not base64 encoded JSON: ${e.message}")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Not a direct JSON object: ${e.message}")
            }

            // Extract the components after removing the ss:// prefix
            val strippedKey = outlineKey.substring(5)

            // Check if we have a '@' sign which separates credentials from server
            val components = strippedKey.split("@")

            var method: String
            var password: String
            var host: String
            var port: Int

            if (components.size == 2) {
                // Format 1 or 2: credentials are separated from server by @
                val credentialsPart = components[0]
                val serverPart = components[1]

                // First try to decode as base64
                try {
                    // Add padding if needed
                    var paddedBase64 = credentialsPart
                    while (paddedBase64.length % 4 != 0) {
                        paddedBase64 += "="
                    }

                    // Decode the base64-encoded credentials
                    val decodedCredentials = String(Base64.decode(paddedBase64, Base64.DEFAULT), StandardCharsets.UTF_8)
                    val methodPasswordParts = decodedCredentials.split(":")

                    if (methodPasswordParts.size >= 2) {
                        method = methodPasswordParts[0]

                        // Check if the method is supported
                        if (method.startsWith("2022-blake3") || method.contains("blake3")) {
                            Log.d(TAG, "Unsupported cipher method: $method, using aes-256-gcm instead")
                            method = "aes-256-gcm" // Use a supported cipher method
                        }

                        password = methodPasswordParts[1]
                        Log.d(TAG, "Decoded base64 credentials: method=$method (original may have been mapped)")
                    } else {
                        throw IllegalArgumentException("Invalid credentials format after base64 decoding")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Not base64 encoded, trying direct format: ${e.message}")

                    // Try direct format (not base64 encoded)
                    val methodPasswordParts = credentialsPart.split(":")

                    if (methodPasswordParts.size >= 2) {
                        method = methodPasswordParts[0]

                        // Check if the method is supported
                        if (method.startsWith("2022-blake3") || method.contains("blake3")) {
                            Log.d(TAG, "Unsupported cipher method: $method, using aes-256-gcm instead")
                            method = "aes-256-gcm" // Use a supported cipher method
                        }

                        password = methodPasswordParts[1]
                        Log.d(TAG, "Parsed direct credentials: method=$method (original may have been mapped)")
                    } else {
                        throw IllegalArgumentException("Invalid credentials format in direct format")
                    }
                }

                // Extract host and port from server part
                val hostPortParts = serverPart.split(":")
                if (hostPortParts.size >= 2) {
                    host = hostPortParts[0]
                    // Handle URL parameters if present
                    val portString = hostPortParts[1].split("/")[0].split("?")[0]
                    port = portString.toIntOrNull() ?: 8388 // Default to 8388 if port is invalid
                } else if (hostPortParts.size == 1) {
                    host = hostPortParts[0]
                    port = 8388 // Default port
                } else {
                    throw IllegalArgumentException("Invalid server format in Outline key")
                }
            } else {
                // Format 3: Try to decode the entire string as base64
                try {
                    // Add padding if needed
                    var paddedBase64 = strippedKey
                    while (paddedBase64.length % 4 != 0) {
                        paddedBase64 += "="
                    }

                    val decoded = String(Base64.decode(paddedBase64, Base64.DEFAULT), StandardCharsets.UTF_8)
                    Log.d(TAG, "Decoded entire key as base64: ${decoded.take(20)}...")

                    // Check if it's a JSON object
                    if (decoded.trim().startsWith("{")) {
                        try {
                            val jsonConfig = JSONObject(decoded)

                            // Verify it has the required fields
                            if (jsonConfig.has("server") && jsonConfig.has("server_port") &&
                                jsonConfig.has("method") && jsonConfig.has("password")) {

                                // Add DNS if not present
                                if (!jsonConfig.has("dns")) {
                                    val dnsArray = JSONArray()
                                    dnsArray.put("8.8.8.8")
                                    dnsArray.put("1.1.1.1")
                                    jsonConfig.put("dns", dnsArray)
                                }

                                // Check if the method is supported
                                if (jsonConfig.has("method")) {
                                    val method = jsonConfig.getString("method")
                                    if (method.startsWith("2022-blake3") || method.contains("blake3")) {
                                        Log.d(TAG, "Unsupported cipher method: $method, using aes-256-gcm instead")
                                        jsonConfig.put("method", "aes-256-gcm")
                                        // Also update cipher field if present
                                        if (jsonConfig.has("cipher")) {
                                            jsonConfig.put("cipher", "aes-256-gcm")
                                        }
                                    }
                                }

                                Log.d(TAG, "Successfully parsed as base64-encoded JSON")
                                return jsonConfig.toString()
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Not a valid JSON after base64 decoding entire string: ${e.message}")
                        }
                    }

                    // If not JSON, try to parse as method:password@host:port
                    val parts = decoded.split("@")
                    if (parts.size == 2) {
                        val methodPasswordParts = parts[0].split(":")
                        val hostPortParts = parts[1].split(":")

                        if (methodPasswordParts.size >= 2 && hostPortParts.size >= 1) {
                            method = methodPasswordParts[0]
                            password = methodPasswordParts[1]
                            host = hostPortParts[0]
                            port = if (hostPortParts.size >= 2) hostPortParts[1].toIntOrNull() ?: 8388 else 8388

                            Log.d(TAG, "Parsed from base64 decoded string: method=$method, host=$host, port=$port")
                        } else {
                            throw IllegalArgumentException("Invalid format in decoded base64")
                        }
                    } else {
                        // Last resort: try to parse as a URI
                        try {
                            val uri = URI(decoded)
                            host = uri.host ?: throw IllegalArgumentException("Missing host in decoded URI")
                            port = if (uri.port == -1) 8388 else uri.port

                            // Extract method and password from userInfo
                            val userInfo = uri.userInfo
                            if (userInfo != null) {
                                val methodPasswordParts = userInfo.split(":")
                                if (methodPasswordParts.size >= 2) {
                                    method = methodPasswordParts[0]
                                    password = methodPasswordParts[1]
                                } else {
                                    throw IllegalArgumentException("Invalid credentials in decoded URI")
                                }
                            } else {
                                throw IllegalArgumentException("Missing credentials in decoded URI")
                            }

                            Log.d(TAG, "Parsed from decoded URI: method=$method, host=$host, port=$port")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse decoded string as URI", e)
                            throw IllegalArgumentException("Invalid format after base64 decoding")
                        }
                    }
                } catch (e: Exception) {
                    // Format 4: Try to parse as a URI directly
                    try {
                        Log.d(TAG, "Trying to parse as URI directly")
                        val uri = URI(outlineKey)

                        // Extract host from URI
                        host = uri.host ?: throw IllegalArgumentException("Missing host in URI")

                        // Extract port from URI
                        port = if (uri.port == -1) 8388 else uri.port

                        // Extract the user info portion
                        val userInfo = uri.userInfo

                        if (userInfo != null) {
                            // Try to decode as base64 first
                            try {
                                // Add padding if needed
                                var paddedBase64 = userInfo
                                while (paddedBase64.length % 4 != 0) {
                                    paddedBase64 += "="
                                }

                                val decodedUserInfo = String(Base64.decode(paddedBase64, Base64.DEFAULT), StandardCharsets.UTF_8)
                                val methodPasswordParts = decodedUserInfo.split(":")

                                if (methodPasswordParts.size >= 2) {
                                    method = methodPasswordParts[0]
                                    password = methodPasswordParts[1]
                                    Log.d(TAG, "Parsed from URI with base64 userInfo: method=$method, host=$host, port=$port")
                                } else {
                                    throw IllegalArgumentException("Invalid credentials format in URI userInfo")
                                }
                            } catch (e: Exception) {
                                // Try direct format
                                val methodPasswordParts = userInfo.split(":")

                                if (methodPasswordParts.size >= 2) {
                                    method = methodPasswordParts[0]
                                    password = methodPasswordParts[1]
                                    Log.d(TAG, "Parsed from URI with direct userInfo: method=$method, host=$host, port=$port")
                                } else {
                                    throw IllegalArgumentException("Invalid credentials format in URI userInfo")
                                }
                            }
                        } else {
                            throw IllegalArgumentException("Missing credentials in URI")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse as URI", e)
                        throw IllegalArgumentException("Failed to parse Outline key in any format: ${e.message}")
                    }
                }
            }

            // Check if the method is supported
            if (method.startsWith("2022-blake3") || method.contains("blake3")) {
                Log.d(TAG, "Unsupported cipher method: $method, using aes-256-gcm instead")
                method = "aes-256-gcm" // Use a supported cipher method
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
                val dnsArray = JSONArray()
                dnsArray.put("8.8.8.8")
                dnsArray.put("1.1.1.1")
                put("dns", dnsArray)
            }

            Log.d(TAG, "Successfully parsed Outline key")
            Log.d(TAG, "Config: ${config.toString().take(50)}...")

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