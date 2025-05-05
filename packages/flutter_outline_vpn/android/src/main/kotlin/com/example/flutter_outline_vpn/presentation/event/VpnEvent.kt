package com.example.flutter_outline_vpn.presentation.event

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.example.flutter_outline_vpn.OutlineVpnService
import com.example.flutter_outline_vpn.util.Logger

/**
 * Manages VPN events from the service.
 */
class VpnEventManager(
    private val context: Context,
    private val eventChannelManager: EventChannelManager
) {
    private val stageReceiver = createStageReceiver()
    private val statusReceiver = createStatusReceiver()
    
    /**
     * Register to receive VPN events.
     */
    fun registerReceivers() {
        Logger.d("Registering VPN event receivers")
        
        val stageFilter = IntentFilter(OutlineVpnService.ACTION_VPN_STAGE)
        context.registerReceiver(stageReceiver, stageFilter)
        
        val statusFilter = IntentFilter(OutlineVpnService.ACTION_VPN_STATUS)
        context.registerReceiver(statusReceiver, statusFilter)
    }
    
    /**
     * Unregister from VPN events.
     */
    fun unregisterReceivers() {
        Logger.d("Unregistering VPN event receivers")
        
        try {
            context.unregisterReceiver(stageReceiver)
            context.unregisterReceiver(statusReceiver)
        } catch (e: Exception) {
            Logger.e("Error unregistering receivers", e)
        }
    }
    
    /**
     * Create a receiver for VPN stage changes.
     */
    private fun createStageReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == OutlineVpnService.ACTION_VPN_STAGE) {
                    val stage = intent.getStringExtra(OutlineVpnService.EXTRA_STAGE)
                    Logger.d("Received VPN stage broadcast: $stage")
                    eventChannelManager.sendStage(stage ?: "unknown")
                }
            }
        }
    }
    
    /**
     * Create a receiver for VPN status updates.
     */
    private fun createStatusReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == OutlineVpnService.ACTION_VPN_STATUS) {
                    val status = intent.getStringExtra(OutlineVpnService.EXTRA_STATUS)
                    Logger.d("Received VPN status broadcast")
                    eventChannelManager.sendStatus(status ?: "{}")
                }
            }
        }
    }
} 