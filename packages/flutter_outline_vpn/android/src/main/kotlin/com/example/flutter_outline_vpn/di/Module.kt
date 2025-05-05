package com.example.flutter_outline_vpn.di

import android.content.Context
import com.example.flutter_outline_vpn.data.datasource.ProxyDataSource
import com.example.flutter_outline_vpn.data.datasource.VpnServiceDataSource
import com.example.flutter_outline_vpn.data.repository.VpnRepositoryImpl
import com.example.flutter_outline_vpn.presentation.event.EventChannelManager
import com.example.flutter_outline_vpn.presentation.event.VpnEventManager
import com.example.flutter_outline_vpn.util.Logger
import io.flutter.plugin.common.EventChannel

/**
 * Dependency injection module for the Outline VPN plugin.
 * Since we're not using a DI framework, this class serves as a manual DI container.
 */
class Module(
    private val context: Context,
    stageChannel: EventChannel,
    statusChannel: EventChannel
) {
    // Initialize all components directly
    private val proxyDataSource = ProxyDataSource()
    private val vpnServiceDataSource = VpnServiceDataSource(context)
    private val vpnRepository = VpnRepositoryImpl(proxyDataSource, vpnServiceDataSource)
    private val eventChannelManager = EventChannelManager(stageChannel, statusChannel)
    private val vpnEventManager = VpnEventManager(context, eventChannelManager)
    
    /**
     * Initialize the module and register for VPN events.
     */
    fun initialize() {
        Logger.d("Initializing Outline VPN module")
        vpnEventManager.registerReceivers()
    }
    
    /**
     * Access the VPN repository.
     */
    fun getVpnRepository(): VpnRepositoryImpl {
        return vpnRepository
    }
    
    /**
     * Clean up resources.
     */
    fun dispose() {
        Logger.d("Disposing Outline VPN module")
        vpnEventManager.unregisterReceivers()
        eventChannelManager.dispose()
        vpnRepository.dispose()
    }
} 