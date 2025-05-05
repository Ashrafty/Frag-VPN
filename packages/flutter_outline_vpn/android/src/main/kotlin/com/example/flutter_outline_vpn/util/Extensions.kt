package com.example.flutter_outline_vpn.util

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import com.example.flutter_outline_vpn.data.VpnError
import com.example.flutter_outline_vpn.domain.ConnectionConfig
import com.example.flutter_outline_vpn.domain.NotificationConfig
import io.flutter.plugin.common.MethodCall
import org.json.JSONObject

/**
 * Parse a ConnectionConfig from a MethodCall.
 */
fun MethodCall.toConnectionConfig(): ConnectionConfig? {
    val outlineKey = argument<String>("outline_key") ?: return null
    val port = argument<String>("port") ?: "0"
    val name = argument<String>("name") ?: return null
    val bypassPackages = argument<List<String>>("bypassPackages")
    
    val notificationConfig = argument<Map<String, Any>>("notificationConfig")?.let { map ->
        NotificationConfig(
            title = map["title"] as? String ?: "Outline VPN",
            showDownloadSpeed = map["showDownloadSpeed"] as? Boolean ?: true,
            showUploadSpeed = map["showUploadSpeed"] as? Boolean ?: true,
            androidIconResourceName = map["androidIconResourceName"] as? String
        )
    }
    
    return ConnectionConfig(
        outlineKey = outlineKey,
        port = port,
        name = name,
        bypassPackages = bypassPackages,
        notificationConfig = notificationConfig
    )
}

/**
 * Validate an Outline key format using the ProxyValidator.
 * @return true if the format is valid, false otherwise.
 */
fun String.isValidOutlineKeyFormat(): Boolean {
    return ProxyValidator.isValidOutlineKeyFormat(this)
}

/**
 * Prepare the VPN service for the given activity.
 * @return null if permission is already granted, or the Intent to request permission.
 */
fun prepareVpnService(activity: Activity?): Intent? {
    return if (activity == null) {
        throw VpnError.ActivityNull()
    } else {
        VpnService.prepare(activity)
    }
}

/**
 * Create a JSONObject with VPN status information.
 */
fun createStatusJson(
    connectedOn: java.util.Date? = null,
    bytesIn: Long = 0,
    bytesOut: Long = 0,
    packetsIn: Long = 0,
    packetsOut: Long = 0
): String {
    val duration = if (connectedOn != null) {
        System.currentTimeMillis() - connectedOn.time
    } else {
        0L
    }
    
    return JSONObject().apply {
        put("connectedOn", connectedOn?.let { FormatUtils.formatDateIso8601(it) })
        put("duration", FormatUtils.formatDuration(duration))
        put("byteIn", bytesIn)
        put("byteOut", bytesOut)
        put("packetsIn", packetsIn)
        put("packetsOut", packetsOut)
    }.toString()
} 