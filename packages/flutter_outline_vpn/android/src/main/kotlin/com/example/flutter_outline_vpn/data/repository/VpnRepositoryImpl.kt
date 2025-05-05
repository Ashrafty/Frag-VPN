package com.example.flutter_outline_vpn.data.repository

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import com.example.flutter_outline_vpn.data.VpnError
import com.example.flutter_outline_vpn.data.datasource.ProxyDataSource
import com.example.flutter_outline_vpn.data.datasource.VpnServiceDataSource
import com.example.flutter_outline_vpn.domain.ConnectionConfig
import com.example.flutter_outline_vpn.domain.VpnState
import com.example.flutter_outline_vpn.util.Logger
import com.example.flutter_outline_vpn.util.isValidOutlineKeyFormat
import com.example.flutter_outline_vpn.util.prepareVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing VPN connections.
 */
class VpnRepositoryImpl(
    private val proxyDataSource: ProxyDataSource,
    private val vpnServiceDataSource: VpnServiceDataSource
) {
    private var pendingConnectionConfig: ConnectionConfig? = null
    
    /**
     * Connect to the VPN with the given configuration.
     *
     * @param connectionConfig The VPN connection configuration.
     * @param activity The current activity (needed for permission requests).
     * @throws VpnError if connection fails.
     */
    suspend fun connect(connectionConfig: ConnectionConfig, activity: Activity?): Intent? {
        withContext(Dispatchers.IO) {
            // Validate inputs
            val (isValid, validationError) = connectionConfig.validate()
            if (!isValid) {
                Logger.e("Invalid connection config: $validationError")
                throw VpnError.InvalidArgs(validationError ?: "Invalid connection configuration")
            }
            
            if (!connectionConfig.outlineKey.isValidOutlineKeyFormat()) {
                Logger.e("Invalid Outline key format")
                throw VpnError.InvalidKey("Invalid Outline key format. The key appears to be malformed.")
            }
            
            Logger.d("Connecting to Outline VPN with name: ${connectionConfig.name}")
        }
        
        // Store the config for when permission is granted
        pendingConnectionConfig = connectionConfig
        
        // Check if we need to request VPN permission
        return prepareVpnService(activity)
    }
    
    /**
     * Handle the result of a VPN permission request.
     *
     * @return true if the connection was successful, false otherwise.
     * @throws VpnError if an error occurs.
     */
    suspend fun handlePermissionResult(resultCode: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            if (resultCode != Activity.RESULT_OK) {
                Logger.e("VPN permission denied")
                throw VpnError.PermissionDenied()
            }
            
            val config = pendingConnectionConfig
                ?: throw VpnError.MissingData("No pending connection configuration found")
            
            Logger.d("Permission granted, initializing Shadowsocks client")
            
            // Initialize the Shadowsocks client and get the outline key
            val outlineKey = proxyDataSource.initializeProxy(config.outlineKey, config.port)
            
            // Start the Shadowsocks proxy
            Logger.d("Starting Shadowsocks proxy")
            proxyDataSource.startProxy()
            
            // Start the VPN service
            Logger.d("Starting VPN service")
            vpnServiceDataSource.startVpnService(config, outlineKey)
            
            // Clear the pending config
            pendingConnectionConfig = null
            
            return@withContext true
        } catch (e: Exception) {
            // Clean up proxy if initialization fails
            proxyDataSource.stopProxy()
            
            when (e) {
                is VpnError -> throw e
                else -> throw VpnError.ConnectionError("Failed to connect: ${e.message}")
            }
        }
    }
    
    /**
     * Get the current VPN connection state.
     */
    fun getCurrentState(): VpnState {
        val stateString = vpnServiceDataSource.getCurrentState()
        return VpnState.fromString(stateString)
    }
    
    /**
     * Update the VPN stage and broadcast it immediately.
     * Used to provide immediate feedback to the UI.
     */
    fun updateStage(stage: String) {
        Logger.d("Manually updating VPN stage to: $stage")
        // We're using the OutlineVpnService.updateStage static method to broadcast stage
        // This ensures immediate stage updates even before the service is fully started
        try {
            val context = vpnServiceDataSource.getAppContext()
            com.example.flutter_outline_vpn.OutlineVpnService.updateStage(context, stage)
        } catch (e: Exception) {
            Logger.e("Error updating stage: ${e.message}")
        }
    }
    
    /**
     * Check if the VPN is connected.
     */
    fun isConnected(): Boolean {
        return vpnServiceDataSource.isVpnServiceRunning()
    }
    
    /**
     * Get the current VPN status as a JSON string.
     */
    fun getStatusJson(): String {
        return vpnServiceDataSource.getStatusJson()
    }
    
    /**
     * Disconnect from the VPN.
     */
    fun disconnect() {
        Logger.d("Disconnecting from VPN")
        proxyDataSource.stopProxy()
        vpnServiceDataSource.stopVpnService()
    }
    
    /**
     * Clean up resources.
     */
    fun dispose() {
        // No explicit dispose needed anymore
    }
} 