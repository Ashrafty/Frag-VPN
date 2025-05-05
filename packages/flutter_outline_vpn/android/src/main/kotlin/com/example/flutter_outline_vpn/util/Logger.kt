package com.example.flutter_outline_vpn.util

import android.util.Log

/**
 * Logger utility for the Outline VPN plugin.
 * Uses emojis for better visibility and categorization of logs.
 */
object Logger {
    private const val TAG = "OutlineVPN"
    
    /**
     * Log levels with corresponding emoji indicators.
     */
    enum class Level(val emoji: String) {
        DEBUG("🔵"),
        INFO("⚪"),
        SUCCESS("🟢"),
        WARNING("🟡"),
        ERROR("🔴"),
        FATAL("⛔")
    }
    
    private var isDebugEnabled = true
    
    /**
     * Enable or disable debug logging.
     */
    fun setDebugEnabled(enabled: Boolean) {
        isDebugEnabled = enabled
    }
    
    /**
     * Log a debug message.
     */
    fun d(message: String, throwable: Throwable? = null) {
        if (isDebugEnabled) {
            log(Level.DEBUG, message, throwable)
        }
    }
    
    /**
     * Log an info message.
     */
    fun i(message: String, throwable: Throwable? = null) {
        log(Level.INFO, message, throwable)
    }
    
    /**
     * Log a success message.
     */
    fun s(message: String, throwable: Throwable? = null) {
        log(Level.SUCCESS, message, throwable)
    }
    
    /**
     * Log a warning message.
     */
    fun w(message: String, throwable: Throwable? = null) {
        log(Level.WARNING, message, throwable)
    }
    
    /**
     * Log an error message.
     */
    fun e(message: String, throwable: Throwable? = null) {
        log(Level.ERROR, message, throwable)
    }
    
    /**
     * Log a fatal error message.
     */
    fun f(message: String, throwable: Throwable? = null) {
        log(Level.FATAL, message, throwable)
    }
    
    /**
     * Internal log function that adds the emoji prefix and logs to Android's LogCat.
     */
    private fun log(level: Level, message: String, throwable: Throwable? = null) {
        val formattedMessage = "${level.emoji} $message"
        
        when (level) {
            Level.DEBUG -> Log.d(TAG, formattedMessage, throwable)
            Level.INFO -> Log.i(TAG, formattedMessage, throwable)
            Level.SUCCESS -> Log.i(TAG, formattedMessage, throwable)
            Level.WARNING -> Log.w(TAG, formattedMessage, throwable)
            Level.ERROR, Level.FATAL -> Log.e(TAG, formattedMessage, throwable)
        }
    }
} 