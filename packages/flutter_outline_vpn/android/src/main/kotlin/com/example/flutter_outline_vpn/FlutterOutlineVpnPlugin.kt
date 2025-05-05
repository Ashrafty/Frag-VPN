package com.example.flutter_outline_vpn

import android.app.Activity
import android.content.Intent
import androidx.annotation.NonNull
import com.example.flutter_outline_vpn.data.VpnError
import com.example.flutter_outline_vpn.di.Module
import com.example.flutter_outline_vpn.domain.ConnectionConfig
import com.example.flutter_outline_vpn.util.Logger
import com.example.flutter_outline_vpn.util.toConnectionConfig
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.flutter_outline_vpn.domain.NotificationConfig
import com.example.flutter_outline_vpn.util.OutlineKeyParser

private const val REQUEST_CODE_PREPARE_VPN = 100

/** FlutterOutlineVpnPlugin */
class FlutterOutlineVpnPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener {
  private lateinit var methodChannel: MethodChannel
  private lateinit var stageChannel: EventChannel
  private lateinit var statusChannel: EventChannel
  private lateinit var module: Module
  
  private var activity: Activity? = null
  private var pendingResult: Result? = null
  
  // Coroutine scope for background operations
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    Logger.d("Plugin attached to engine")
    
    methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_outline_vpn")
    stageChannel = EventChannel(flutterPluginBinding.binaryMessenger, "flutter_outline_vpn/stage")
    statusChannel = EventChannel(flutterPluginBinding.binaryMessenger, "flutter_outline_vpn/status")
    
    methodChannel.setMethodCallHandler(this)
    
    // Create the dependency injection module
    module = Module(
        flutterPluginBinding.applicationContext,
        stageChannel,
        statusChannel
    )
    
    // Initialize the module
    module.initialize()
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when (call.method) {
      "initialize" -> {
        // No special initialization needed for Android
        Logger.d("Initializing plugin")
        result.success(null)
      }
      "connect" -> handleConnect(call, result)
      "disconnect" -> handleDisconnect(result)
      "isConnected" -> handleIsConnected(result)
      "getCurrentStage" -> handleGetCurrentStage(result)
      "getStatus" -> handleGetStatus(result)
      "requestPermission" -> handleRequestPermission(result)
      "dispose" -> handleDispose(result)
      "testOutlineKey" -> testOutlineKey(call, result)
      else -> {
        Logger.e("Method not implemented: ${call.method}")
        result.notImplemented()
      }
    }
  }
  
  /**
   * Handle the connect method call.
   */
  private fun handleConnect(call: MethodCall, result: Result) {
    Logger.d("Connect method called")
    
    // Send connecting stage immediately to update UI
    module.getVpnRepository().updateStage("connecting")
    
    // Extract connection config from the call
    val config = call.toConnectionConfig()
    
    if (config == null) {
      Logger.e("Failed to create connection config from method call")
      result.error(
        "INVALID_ARGS",
        "Invalid connection configuration",
        null
      )
      return
    }
    
    // Use coroutines for the connection flow
    scope.launch {
      try {
        // Request VPN permission if needed
        val intent = module.getVpnRepository().connect(config, activity)
        
        if (intent == null) {
          // No permission needed, proceed with connection
          val success = module.getVpnRepository().handlePermissionResult(Activity.RESULT_OK)
          result.success(success)
        } else {
          // Need to request permission
          pendingResult = result
          activity?.startActivityForResult(intent, REQUEST_CODE_PREPARE_VPN)
        }
      } catch (e: VpnError) {
        Logger.e("Error connecting to VPN: ${e.message}")
        val (errorCode, errorMessage, errorDetails) = e.toFlutterError()
        result.error(errorCode, errorMessage, errorDetails)
      } catch (e: Exception) {
        Logger.e("Unexpected error connecting to VPN", e)
        result.error(
          "UNEXPECTED_ERROR",
          "An unexpected error occurred: ${e.message}",
          null
        )
      }
    }
  }
  
  /**
   * Handle the disconnect method call.
   */
  private fun handleDisconnect(result: Result) {
    Logger.d("Disconnect method called")
    
    try {
      module.getVpnRepository().disconnect()
      result.success(null)
    } catch (e: Exception) {
      Logger.e("Error disconnecting from VPN", e)
      result.error(
        "DISCONNECT_ERROR",
        "Failed to disconnect: ${e.message}",
        null
      )
    }
  }
  
  /**
   * Handle the isConnected method call.
   */
  private fun handleIsConnected(result: Result) {
    Logger.d("isConnected method called")
    
    try {
      val isConnected = module.getVpnRepository().isConnected()
      result.success(isConnected)
    } catch (e: Exception) {
      Logger.e("Error checking connection status", e)
      result.error(
        "CHECK_ERROR",
        "Failed to check connection status: ${e.message}",
        null
      )
    }
  }
  
  /**
   * Handle the getCurrentStage method call.
   */
  private fun handleGetCurrentStage(result: Result) {
    Logger.d("getCurrentStage method called")
    
    try {
      val state = module.getVpnRepository().getCurrentState()
      result.success(state.stateName)
    } catch (e: Exception) {
      Logger.e("Error getting current stage", e)
      result.error(
        "STAGE_ERROR",
        "Failed to get current stage: ${e.message}",
        null
      )
    }
  }
  
  /**
   * Handle the getStatus method call.
   */
  private fun handleGetStatus(result: Result) {
    Logger.d("getStatus method called")
    
    try {
      val status = module.getVpnRepository().getStatusJson()
      result.success(status)
    } catch (e: Exception) {
      Logger.e("Error getting status", e)
      result.error(
        "STATUS_ERROR",
        "Failed to get status: ${e.message}",
        null
      )
    }
  }
  
  /**
   * Handle the requestPermission method call.
   */
  private fun handleRequestPermission(result: Result) {
    Logger.d("requestPermission method called")
    
    scope.launch {
      try {
        val intent = module.getVpnRepository().connect(
          ConnectionConfig(
            outlineKey = "ss://dummy", // Dummy key just for permission
            name = "Outline VPN",
            port = "0"
          ),
          activity
        )
        
        if (intent == null) {
          // Permission already granted
          result.success(true)
        } else {
          // Need to request permission
          pendingResult = result
          activity?.startActivityForResult(intent, REQUEST_CODE_PREPARE_VPN)
        }
      } catch (e: Exception) {
        Logger.e("Error requesting permission", e)
        result.error(
          "PERMISSION_ERROR",
          "Failed to request permission: ${e.message}",
          null
        )
      }
    }
  }
  
  /**
   * Handle the dispose method call.
   */
  private fun handleDispose(result: Result) {
    Logger.d("dispose method called")
    
    try {
      module.getVpnRepository().disconnect()
      result.success(null)
    } catch (e: Exception) {
      Logger.e("Error disposing plugin", e)
      result.error(
        "DISPOSE_ERROR",
        "Failed to dispose plugin: ${e.message}",
        null
      )
    }
  }

  private fun testOutlineKey(call: MethodCall, result: Result) {
    val outlineKey = call.argument<String>("outline_key")
    if (outlineKey == null) {
      result.error("MISSING_PARAMS", "Missing outline_key parameter", null)
      return
    }

    try {
      val parsingResult = OutlineKeyParser.testOutlineKeyParsing(outlineKey)
      result.success(parsingResult)
    } catch (e: Exception) {
      result.error("PARSING_ERROR", "Error testing Outline key: ${e.message}", null)
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    Logger.d("Plugin detached from engine")
    methodChannel.setMethodCallHandler(null)
    module.dispose()
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    Logger.d("Plugin attached to activity")
    activity = binding.activity
    binding.addActivityResultListener(this)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    Logger.d("Plugin detached from activity for config changes")
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    Logger.d("Plugin reattached to activity for config changes")
    activity = binding.activity
    binding.addActivityResultListener(this)
  }

  override fun onDetachedFromActivity() {
    Logger.d("Plugin detached from activity")
    activity = null
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    if (requestCode == REQUEST_CODE_PREPARE_VPN) {
      Logger.d("Received activity result for VPN permission: $resultCode")
      
      // Handle permission result
      scope.launch {
        try {
          if (resultCode == Activity.RESULT_OK) {
            val success = module.getVpnRepository().handlePermissionResult(resultCode)
            pendingResult?.success(success)
          } else {
            // Permission denied
            pendingResult?.error(
              "PERMISSION_DENIED",
              "VPN permission was denied by the user",
              null
            )
          }
        } catch (e: VpnError) {
          Logger.e("Error handling permission result: ${e.message}")
          val (errorCode, errorMessage, errorDetails) = e.toFlutterError()
          pendingResult?.error(errorCode, errorMessage, errorDetails)
        } catch (e: Exception) {
          Logger.e("Unexpected error handling permission result", e)
          pendingResult?.error(
            "UNEXPECTED_ERROR",
            "An unexpected error occurred: ${e.message}",
            null
          )
        } finally {
          pendingResult = null
        }
      }
      
      return true
    }
    return false
  }
}