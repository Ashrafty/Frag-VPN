import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../models/vpn_config.dart';
import '../models/vpn_stage.dart';
import '../models/vpn_status.dart';
import '../models/notification_config.dart';
import 'vpn_platform_interface.dart';

/// An implementation of [VpnPlatform] that uses method channels.
class MethodChannelVpn extends VpnPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final MethodChannel methodChannel = const MethodChannel('flutter_outline_vpn');

  /// The event channel for stage changes
  @visibleForTesting
  final EventChannel stageChannel = const EventChannel('flutter_outline_vpn/stage');

  /// The event channel for status updates
  @visibleForTesting
  final EventChannel statusChannel = const EventChannel('flutter_outline_vpn/status');

  /// Stream controller for stage changes
  final StreamController<VpnStage> _stageController = StreamController<VpnStage>.broadcast();

  /// Stream controller for status updates
  final StreamController<VpnStatus> _statusController = StreamController<VpnStatus>.broadcast();

  /// Constructor
  MethodChannelVpn() {
    stageChannel.receiveBroadcastStream().listen((dynamic event) {
      try {
        final stage = VpnStage.fromString(event as String?);
        _stageController.add(stage);
      } catch (e) {
        debugPrint('Error parsing VPN stage: $e');
      }
    });

    statusChannel.receiveBroadcastStream().listen((dynamic event) {
      try {
        if (event is String) {
          final statusMap = jsonDecode(event) as Map<String, dynamic>;
          final status = VpnStatus.fromJson(statusMap);
          _statusController.add(status);
        }
      } catch (e) {
        debugPrint('Error parsing VPN status: $e');
      }
    });
  }

  @override
  Future<void> initialize({
    String? providerBundleIdentifier,
    String? localizedDescription,
    String? groupIdentifier,
    VpnConfig? config,
  }) async {
    final Map<String, dynamic> args = {
      if (providerBundleIdentifier != null) 'providerBundleIdentifier': providerBundleIdentifier,
      if (localizedDescription != null) 'localizedDescription': localizedDescription,
      if (groupIdentifier != null) 'groupIdentifier': groupIdentifier,
      if (config != null) 'config': config.toMap(),
    };

    await methodChannel.invokeMethod<void>('initialize', args);
  }

  @override
  Future<void> connect({
    required String outlineKey,
    String? port,
    required String name,
    List<String>? bypassPackages,
    NotificationConfig? notificationConfig,
  }) async {
    // Validate inputs on Dart side before sending to platform
    if (outlineKey.isEmpty) {
      throw VpnException('INVALID_ARGS', 'Outline key is required and cannot be empty');
    }

    if (!outlineKey.startsWith('ss://')) {
      throw VpnException('INVALID_KEY', 'Invalid Outline key format. Must start with "ss://"');
    }

    if (name.isEmpty) {
      throw VpnException('MISSING_NAME', 'VPN connection name is required');
    }

    final Map<String, dynamic> args = {
      'outline_key': outlineKey,
      if (port != null) 'port': port,
      'name': name,
      if (bypassPackages != null) 'bypassPackages': bypassPackages,
      if (notificationConfig != null) 'notificationConfig': notificationConfig.toMap(),
    };

    try {
      await methodChannel.invokeMethod<void>('connect', args);
    } on PlatformException catch (e) {
      String errorMessage;

      switch (e.code) {
        case 'INVALID_ARGS':
          errorMessage = 'Invalid connection parameters: ${e.message}';
          break;
        case 'INVALID_KEY':
          errorMessage = 'Invalid Outline key format: ${e.message}';
          break;
        case 'PROXY_ERROR':
          errorMessage = 'Failed to establish proxy connection: ${e.message}';
          break;
        case 'ACTIVITY_NULL':
          errorMessage = 'Internal error: ${e.message}';
          break;
        case 'MISSING_NAME':
          errorMessage = 'Connection name is required';
          break;
        case 'PROXY_UNAVAILABLE':
          errorMessage = 'Outline proxy initialization failed';
          break;
        case 'PROXY_ADDRESS_MISSING':
          errorMessage = 'Failed to obtain proxy address';
          break;
        case 'MISSING_DATA':
          errorMessage = e.message ?? 'Missing required data for VPN connection';
          break;
        case 'PERMISSION_DENIED':
          errorMessage = 'VPN permission was denied';
          break;
        default:
          errorMessage = e.message ?? 'Unknown error during VPN connection';
      }

      throw VpnException(e.code, errorMessage);
    }
  }

  @override
  Future<void> disconnect() async {
    await methodChannel.invokeMethod<void>('disconnect');
  }

  @override
  Future<bool> isConnected() async {
    return await methodChannel.invokeMethod<bool>('isConnected') ?? false;
  }

  @override
  Future<VpnStage> getCurrentStage() async {
    final String? stage = await methodChannel.invokeMethod<String>('getCurrentStage');
    return VpnStage.fromString(stage);
  }

  @override
  Future<VpnStatus> getStatus() async {
    final String? statusJson = await methodChannel.invokeMethod<String>('getStatus');
    if (statusJson == null) {
      return VpnStatus.empty();
    }

    try {
      final statusMap = jsonDecode(statusJson) as Map<String, dynamic>;
      return VpnStatus.fromJson(statusMap);
    } catch (e) {
      debugPrint('Error parsing VPN status: $e');
      return VpnStatus.empty();
    }
  }

  @override
  Future<bool> requestPermission() async {
    return await methodChannel.invokeMethod<bool>('requestPermission') ?? false;
  }

  @override
  Stream<VpnStage> onStageChanged() {
    return _stageController.stream;
  }

  @override
  Stream<VpnStatus> onStatusChanged() {
    return _statusController.stream;
  }

  @override
  Future<void> dispose() async {
    await methodChannel.invokeMethod<void>('dispose');
    await _stageController.close();
    await _statusController.close();
  }

  /// Test parsing an Outline key to see if it's valid and can be correctly parsed
  /// Returns a descriptive string with the parsing result
  @override
  Future<String> testOutlineKey(String outlineKey) async {
    try {
      final result = await methodChannel.invokeMethod<String>(
        'testOutlineKey',
        {'outline_key': outlineKey},
      );
      return result ?? 'No result returned from native code';
    } on PlatformException catch (e) {
      return 'Error: [${e.code}] ${e.message}';
    } catch (e) {
      return 'Unexpected error: $e';
    }
  }
}

/// Custom exception for VPN-related errors with detailed messages
class VpnException implements Exception {
  /// Error code from platform
  final String code;

  /// Human-readable error message
  final String message;

  /// Constructor
  VpnException(this.code, this.message);

  @override
  String toString() => 'VpnException: [$code] $message';
}
