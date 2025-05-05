import '../models/vpn_config.dart';
import '../models/vpn_stage.dart';
import '../models/vpn_status.dart';
import '../models/notification_config.dart';
import '../platform/vpn_platform_interface.dart';
import '../platform/method_channel_vpn.dart' show VpnException;
import 'package:flutter/foundation.dart';

/// Main entry point for Outline VPN functionality
class OutlineVPN {
  /// Singleton instance
  static final OutlineVPN instance = OutlineVPN._internal();

  /// Factory constructor that returns the singleton instance
  factory OutlineVPN() => instance;

  /// Private constructor for singleton pattern
  OutlineVPN._internal();

  /// Initialize the VPN service
  ///
  /// [providerBundleIdentifier] - iOS Network Extension bundle identifier
  /// [localizedDescription] - Localized description for iOS VPN
  /// [groupIdentifier] - App Group identifier for sharing data with Network Extension on iOS
  /// [config] - Optional VPN configuration
  Future<void> initialize({
    // iOS-specific parameters
    String? providerBundleIdentifier,
    String? localizedDescription,
    String? groupIdentifier,

    // Optional configuration
    VpnConfig? config,
  }) async {
    return VpnPlatform.instance.initialize(
      providerBundleIdentifier: providerBundleIdentifier,
      localizedDescription: localizedDescription,
      groupIdentifier: groupIdentifier,
      config: config,
    );
  }

  /// Connect to VPN with the given Outline key
  ///
  /// [outlineKey] - Outline key in format ss://<YOUR_OUTLINE_KEY>
  /// [port] - Optional port to use for the proxy (default: "0")
  /// [name] - VPN connection name displayed to the user
  /// [bypassPackages] - Android-only: package names to exclude from VPN
  /// [notificationConfig] - Custom notification settings
  ///
  /// Throws [VpnException] if connection fails. Possible error codes include:
  /// * INVALID_ARGS - Invalid arguments provided
  /// * INVALID_KEY - Outline key format is invalid
  /// * PROXY_ERROR - Failed to establish connection with Outline server
  /// * PROXY_UNAVAILABLE - Outline proxy initialization failed
  /// * PERMISSION_DENIED - User denied VPN permission
  /// * MISSING_DATA - Required data missing for connection
  /// * ACTIVITY_NULL - Internal Android error (no activity context)
  /// * CONNECTION_ERROR - General connection failure
  Future<void> connect({
    required String outlineKey,
    String? port,
    required String name,
    List<String>? bypassPackages,
    NotificationConfig? notificationConfig,
  }) async {
    debugPrint('OutlineVPN.connect called with name: "$name", length: ${name.length}');

    // Extra validation to catch edge cases
    if (name.isEmpty) {
      debugPrint('WARNING: Empty name parameter detected in OutlineVPN.connect!');
    }

    return VpnPlatform.instance.connect(
      outlineKey: outlineKey,
      port: port,
      name: name,
      bypassPackages: bypassPackages,
      notificationConfig: notificationConfig,
    );
  }

  /// Disconnect from VPN
  Future<void> disconnect() async {
    return VpnPlatform.instance.disconnect();
  }

  /// Check if VPN is currently connected
  Future<bool> isConnected() async {
    return VpnPlatform.instance.isConnected();
  }

  /// Get current VPN connection stage
  Future<VpnStage> getCurrentStage() async {
    return VpnPlatform.instance.getCurrentStage();
  }

  /// Get current VPN status details
  Future<VpnStatus> getStatus() async {
    return VpnPlatform.instance.getStatus();
  }

  /// Request VPN permission (Android-only, automatically handled on iOS)
  ///
  /// Returns true if permission granted, false if denied
  Future<bool> requestPermission() async {
    return VpnPlatform.instance.requestPermission();
  }

  /// Stream of VPN connection stage changes
  Stream<VpnStage> get onStageChanged => VpnPlatform.instance.onStageChanged();

  /// Stream of VPN status updates (traffic, etc.)
  Stream<VpnStatus> get onStatusChanged => VpnPlatform.instance.onStatusChanged();

  /// Clean up resources
  Future<void> dispose() async {
    return VpnPlatform.instance.dispose();
  }

  /// Test an Outline key to verify if it can be parsed correctly
  /// This is useful for debugging connection issues
  ///
  /// [outlineKey] - The Outline key to test
  ///
  /// Returns a descriptive string with the parsing results
  Future<String> testOutlineKey(String outlineKey) async {
    return VpnPlatform.instance.testOutlineKey(outlineKey);
  }
}
