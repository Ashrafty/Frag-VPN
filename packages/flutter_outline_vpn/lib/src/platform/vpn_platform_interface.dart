import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import '../models/vpn_config.dart';
import '../models/vpn_stage.dart';
import '../models/vpn_status.dart';
import '../models/notification_config.dart';
import 'method_channel_vpn.dart';

/// The interface that implementations of flutter_outline_vpn must implement.
abstract class VpnPlatform extends PlatformInterface {
  /// Constructs a VpnPlatform.
  VpnPlatform() : super(token: _token);

  static final Object _token = Object();

  static VpnPlatform _instance = MethodChannelVpn();

  /// The default instance of [VpnPlatform] to use.
  static VpnPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [VpnPlatform] when they
  /// register themselves.
  static set instance(VpnPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Initialize the VPN service
  Future<void> initialize({
    String? providerBundleIdentifier,
    String? localizedDescription,
    String? groupIdentifier,
    VpnConfig? config,
  }) {
    throw UnimplementedError('initialize() has not been implemented.');
  }

  /// Connect to the VPN
  Future<void> connect({
    required String outlineKey,
    String? port,
    required String name,
    List<String>? bypassPackages,
    NotificationConfig? notificationConfig,
  }) {
    throw UnimplementedError('connect() has not been implemented.');
  }

  /// Disconnect from the VPN
  Future<void> disconnect() {
    throw UnimplementedError('disconnect() has not been implemented.');
  }

  /// Check if the VPN is connected
  Future<bool> isConnected() {
    throw UnimplementedError('isConnected() has not been implemented.');
  }

  /// Get the current VPN stage
  Future<VpnStage> getCurrentStage() {
    throw UnimplementedError('getCurrentStage() has not been implemented.');
  }

  /// Get the current VPN status
  Future<VpnStatus> getStatus() {
    throw UnimplementedError('getStatus() has not been implemented.');
  }

  /// Request VPN permission from the OS
  Future<bool> requestPermission() {
    throw UnimplementedError('requestPermission() has not been implemented.');
  }

  /// Stream of VPN stage changes
  Stream<VpnStage> onStageChanged() {
    throw UnimplementedError('onStageChanged() has not been implemented.');
  }

  /// Stream of VPN status updates
  Stream<VpnStatus> onStatusChanged() {
    throw UnimplementedError('onStatusChanged() has not been implemented.');
  }

  /// Clean up resources
  Future<void> dispose() {
    throw UnimplementedError('dispose() has not been implemented.');
  }

  /// Test an Outline key to verify if it can be parsed correctly
  /// Returns a descriptive string with the parsing results
  Future<String> testOutlineKey(String outlineKey) {
    throw UnimplementedError('testOutlineKey() has not been implemented.');
  }
}
