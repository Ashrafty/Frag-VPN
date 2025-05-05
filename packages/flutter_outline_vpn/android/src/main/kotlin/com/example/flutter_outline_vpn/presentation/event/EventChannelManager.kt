package com.example.flutter_outline_vpn.presentation.event

import android.os.Handler
import android.os.Looper
import com.example.flutter_outline_vpn.util.Logger
import io.flutter.plugin.common.EventChannel

/**
 * Manages Flutter event channels for sending events to Flutter.
 */
class EventChannelManager(
    private val stageChannel: EventChannel,
    private val statusChannel: EventChannel
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stageHandler = EventHandler()
    private val statusHandler = EventHandler()
    
    init {
        stageChannel.setStreamHandler(stageHandler)
        statusChannel.setStreamHandler(statusHandler)
    }
    
    /**
     * Send a VPN stage update to Flutter.
     *
     * @param stage The VPN stage as a string.
     */
    fun sendStage(stage: String) {
        Logger.d("Sending VPN stage update: $stage")
        mainHandler.post {
            stageHandler.send(stage)
        }
    }
    
    /**
     * Send a VPN status update to Flutter.
     *
     * @param status The VPN status as a JSON string.
     */
    fun sendStatus(status: String) {
        Logger.d("Sending VPN status update")
        mainHandler.post {
            statusHandler.send(status)
        }
    }
    
    /**
     * Clean up resources.
     */
    fun dispose() {
        stageChannel.setStreamHandler(null)
        statusChannel.setStreamHandler(null)
    }
    
    /**
     * A handler for Flutter event channels.
     */
    private class EventHandler : EventChannel.StreamHandler {
        private var eventSink: EventChannel.EventSink? = null
        
        override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
            Logger.d("EventHandler: onListen")
            eventSink = events
        }
        
        override fun onCancel(arguments: Any?) {
            Logger.d("EventHandler: onCancel")
            eventSink = null
        }
        
        /**
         * Send an event to Flutter.
         */
        fun send(event: Any) {
            eventSink?.success(event)
        }
    }
} 