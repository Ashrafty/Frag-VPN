package com.example.flutter_outline_vpn.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.flutter_outline_vpn.domain.NotificationConfig
import com.example.flutter_outline_vpn.OutlineVpnService
import com.example.flutter_outline_vpn.util.Constants.NOTIFICATION_CHANNEL_ID
import com.example.flutter_outline_vpn.util.Constants.NOTIFICATION_ID

/**
 * Helper class for creating and managing VPN notifications.
 */
class NotificationHelper(private val context: Context) {
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    /**
     * Create the notification channel (required for Android O and above).
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Outline VPN",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows the status of the VPN connection"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create a notification for the VPN service.
     *
     * @param config Notification configuration.
     * @param traffic Optional traffic statistics to display.
     * @return A notification that can be used with a foreground service.
     */
    fun createNotification(
        config: NotificationConfig,
        traffic: Pair<Long, Long>? = null
    ): Notification {
        val pendingIntent = createPendingIntent()
        
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(config.title)
            .setSmallIcon(getNotificationIcon(config.androidIconResourceName))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        
        if (traffic != null) {
            val (bytesIn, bytesOut) = traffic
            val contentText = StringBuilder()
            
            if (config.showDownloadSpeed) {
                contentText.append("↓ ${FormatUtils.formatBytes(bytesIn)}")
            }
            
            if (config.showUploadSpeed && config.showDownloadSpeed) {
                contentText.append(" • ")
            }
            
            if (config.showUploadSpeed) {
                contentText.append("↑ ${FormatUtils.formatBytes(bytesOut)}")
            }
            
            if (contentText.isNotEmpty()) {
                builder.setContentText(contentText.toString())
            }
        }
        
        return builder.build()
    }
    
    /**
     * Update the notification with new traffic statistics.
     *
     * @param config Notification configuration.
     * @param traffic A pair of download and upload bytes.
     */
    fun updateNotification(config: NotificationConfig, traffic: Pair<Long, Long>) {
        val notification = createNotification(config, traffic)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Cancel the notification.
     */
    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
    
    /**
     * Create a pending intent that will open the app when the notification is clicked.
     */
    private fun createPendingIntent(): PendingIntent {
        // Try to get the launcher activity of the app
        val packageManager = context.packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(context.packageName)
        
        val intent = launchIntent ?: Intent().apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        return PendingIntent.getActivity(context, 0, intent, flags)
    }
    
    /**
     * Get the resource ID for the notification icon.
     */
    private fun getNotificationIcon(androidIconResourceName: String?): Int {
        // Try specified icon first
        if (androidIconResourceName != null) {
            // Check drawable folder
            context.resources.getIdentifier(
                androidIconResourceName, "drawable", context.packageName
            ).takeIf { it != 0 }?.let { return it }
            
            // Check mipmap folder
            context.resources.getIdentifier(
                androidIconResourceName, "mipmap", context.packageName
            ).takeIf { it != 0 }?.let { return it }
        }
        
        // Try default notification icon
        context.resources.getIdentifier(
            "ic_notification", "drawable", context.packageName
        ).takeIf { it != 0 }?.let { return it }
        
        // Try launcher icon
        context.resources.getIdentifier(
            "ic_launcher", "mipmap", context.packageName
        ).takeIf { it != 0 }?.let { return it }
        
        // Last resort fallback
        return android.R.drawable.ic_dialog_info
    }
} 