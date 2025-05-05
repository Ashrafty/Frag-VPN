import 'package:flutter/foundation.dart';

/// VPN Logger for debug and error messages
class VpnLogger {
  /// Whether to enable logging
  bool enabled = false;

  /// Private constructor for singleton
  VpnLogger._();

  /// Singleton instance
  static final VpnLogger _instance = VpnLogger._();

  /// Factory constructor that returns the singleton instance
  factory VpnLogger() => _instance;

  /// Log a message
  void log(String message, {bool isError = false}) {
    if (!enabled) return;

    if (isError) {
      debugPrint('🔴 [OutlineVPN] $message');
    } else {
      debugPrint('🔵 [OutlineVPN] $message');
    }
  }
}
