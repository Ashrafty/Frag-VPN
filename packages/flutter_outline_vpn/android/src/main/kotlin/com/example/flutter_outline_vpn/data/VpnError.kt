package com.example.flutter_outline_vpn.data

/**
 * Represents errors that can occur during VPN operations.
 */
sealed class VpnError(
    open val code: String,
    override val message: String
) : Exception(message) {
    class InvalidArgs(override val message: String) : VpnError("INVALID_ARGS", message)
    
    class InvalidKey(override val message: String) : VpnError("INVALID_KEY", message)
    
    class ProxyError(override val message: String) : VpnError("PROXY_ERROR", message)
    
    class ProxyUnavailable(override val message: String = "Outline proxy was not initialized successfully") : 
        VpnError("PROXY_UNAVAILABLE", message)
    
    class PermissionDenied(override val message: String = "VPN permission was denied by the user") : 
        VpnError("PERMISSION_DENIED", message)
    
    class MissingData(override val message: String) : VpnError("MISSING_DATA", message)
    
    class ActivityNull(override val message: String = "Activity is null - VPN permission cannot be requested without an activity") : 
        VpnError("ACTIVITY_NULL", message)
    
    class ConnectionError(override val message: String) : VpnError("CONNECTION_ERROR", message)
    
    class Unknown(override val message: String = "Unknown error occurred") : 
        VpnError("UNKNOWN_ERROR", message)
    
    // Shadowsocks specific error types
    companion object {
        val SHADOWSOCKS_INITIALIZATION_ERROR = ProxyError("Failed to initialize Shadowsocks client")
        val SHADOWSOCKS_NOT_INITIALIZED = ProxyError("Shadowsocks client is not initialized")
        val SHADOWSOCKS_START_ERROR = ProxyError("Failed to start Shadowsocks client")
    }
    
    /**
     * Convert the error to a format that can be returned to Flutter.
     */
    fun toFlutterError(): Triple<String, String, Any?> {
        return Triple(code, message, null)
    }
} 