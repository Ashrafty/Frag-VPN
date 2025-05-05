package com.example.flutter_outline_vpn.domain

/**
 * Represents the configuration for a VPN connection.
 */
data class ConnectionConfig(
    val outlineKey: String,
    val port: String = "0",
    val name: String,
    val bypassPackages: List<String>? = null,
    val notificationConfig: NotificationConfig? = null
) {
    /**
     * Validate that the configuration is valid.
     * @return a pair of Boolean and String, where the Boolean indicates if the config is valid
     * and the String contains an error message if it's not valid.
     */
    fun validate(): Pair<Boolean, String?> {
        if (outlineKey.isBlank()) {
            return Pair(false, "Outline key is required and cannot be empty")
        }
        
        if (!outlineKey.startsWith("ss://")) {
            return Pair(false, "Invalid Outline key format. Must start with 'ss://'")
        }
        
        if (name.isBlank()) {
            return Pair(false, "VPN connection name is required")
        }
        
        // Additional validation could go here
        
        return Pair(true, null)
    }
}

/**
 * Notification configuration for the VPN service.
 */
data class NotificationConfig(
    val title: String = "Outline VPN",
    val showDownloadSpeed: Boolean = true,
    val showUploadSpeed: Boolean = true,
    val androidIconResourceName: String? = null
) 