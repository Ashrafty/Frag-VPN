package com.example.flutter_outline_vpn.data.datasource

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.flutter_outline_vpn.domain.ConnectionConfig
import com.example.flutter_outline_vpn.OutlineVpnService
import com.example.flutter_outline_vpn.util.Logger

/**
 * Data source for managing the VPN service.
 */
class VpnServiceDataSource(private val context: Context) {
    
    /**
     * Start the VPN service with the given configuration.
     *
     * @param connectionConfig The VPN connection configuration.
     * @param proxyData The Outline key used to configure the VPN.
     */
    fun startVpnService(connectionConfig: ConnectionConfig, proxyData: String) {
        // Create intent to start VPN service
        val intent = Intent(context, OutlineVpnService::class.java).apply {
            action = "CONNECT"
            // No longer need proxy_address, since we'll use the Shadowsocks client directly
            putExtra("outline_key", connectionConfig.outlineKey)
            putExtra("connection_name", connectionConfig.name)
            
            // Add notification configuration
            connectionConfig.notificationConfig?.let { config ->
                putExtra("notification_title", config.title)
                putExtra("notification_show_download_speed", config.showDownloadSpeed)
                putExtra("notification_show_upload_speed", config.showUploadSpeed)
                putExtra("notification_icon", config.androidIconResourceName)
            }
            
            // Add bypass packages if provided
            if (!connectionConfig.bypassPackages.isNullOrEmpty()) {
                putStringArrayListExtra(
                    "bypass_packages", 
                    ArrayList(connectionConfig.bypassPackages)
                )
            }
        }
        
        // Start the service with proper foreground handling
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Logger.e("Failed to start VPN service: ${e.message}")
            throw e
        }
    }
    
    /**
     * Stop the VPN service.
     */
    fun stopVpnService() {
        Logger.d("Stopping VPN service")
        
        val intent = Intent(context, OutlineVpnService::class.java).apply {
            action = "DISCONNECT"
        }
        context.startService(intent)
    }
    
    /**
     * Check if the VPN service is running.
     *
     * @return true if the VPN service is running, false otherwise.
     */
    fun isVpnServiceRunning(): Boolean {
        return OutlineVpnService.getInstance() != null
    }
    
    /**
     * Get the current VPN connection state.
     *
     * @return The current VPN state as a string, or "disconnected" if the service is not running.
     */
    fun getCurrentState(): String {
        val service = OutlineVpnService.getInstance()
        return service?.getCurrentStage() ?: "disconnected"
    }
    
    /**
     * Get the current VPN status as a JSON string.
     *
     * @return A JSON string containing the VPN status information.
     */
    fun getStatusJson(): String {
        val service = OutlineVpnService.getInstance()
        return service?.getStatusJson() ?: "{}"
    }
    
    /**
     * Get the application context.
     * This is used for broadcasting events directly.
     */
    fun getAppContext(): Context {
        return context
    }
} 