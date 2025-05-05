// This is the mobile implementation that uses the actual flutter_outline_vpn package

import 'package:flutter_outline_vpn/flutter_outline_vpn.dart' as outline_vpn;

// Re-export the classes from flutter_outline_vpn
export 'package:flutter_outline_vpn/flutter_outline_vpn.dart';

// For consistency with the stub implementation
class PlatformNotSupportedException implements Exception {
  final String message;
  
  PlatformNotSupportedException(this.message);
  
  @override
  String toString() => 'PlatformNotSupportedException: $message';
}
