package com.example.flutter_outline_vpn.util

/**
 * Constants used throughout the application.
 */
object Constants {
    // VPN Service constants
    const val OUTLINE_VPN_SERVICE_INTENT = "com.example.flutter_outline_vpn.VPN_SERVICE"
    
    // Broadcast action constants
    const val ACTION_VPN_STAGE = "com.example.flutter_outline_vpn.VPN_STAGE"
    const val ACTION_VPN_STATUS = "com.example.flutter_outline_vpn.VPN_STATUS"
    
    // Broadcast extras
    const val EXTRA_STAGE = "stage"
    const val EXTRA_STATUS = "status"
    const val EXTRA_ERROR = "error"
    
    // Notification constants
    const val NOTIFICATION_CHANNEL_ID = "outline_vpn_channel"
    const val NOTIFICATION_ID = 1
    
    // Permission request codes
    const val REQUEST_CODE_PREPARE_VPN = 100
    
    // Stats update interval
    const val STATS_UPDATE_INTERVAL_MS = 1000L // 1 second
} 