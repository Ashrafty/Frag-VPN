package com.example.flutter_outline_vpn.util

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Utility class for formatting various data types into human-readable strings.
 */
object FormatUtils {
    /**
     * Format bytes into a human-readable string (e.g., "1.5 MB").
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        
        return String.format("%.1f %s", 
            bytes / Math.pow(1024.0, digitGroups.toDouble()),
            units[minOf(digitGroups, units.size - 1)]
        )
    }
    
    /**
     * Format milliseconds as a human-readable duration string (HH:MM:SS).
     */
    fun formatDuration(milliseconds: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    /**
     * Format a date as an ISO 8601 string.
     */
    fun formatDateIso8601(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }
} 