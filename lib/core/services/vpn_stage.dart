import 'package:flutter_outline_vpn/flutter_outline_vpn.dart' as flutter_outline_vpn;

/// Represents different stages of a VPN connection.
/// This is a wrapper around the VpnStage enum from flutter_outline_vpn.
enum VpnStage {
  initializing,
  initialized,
  connecting,
  connected,
  disconnecting,
  disconnected,
  error;

  /// Get a human-readable name for the stage.
  String get name {
    return toString().split('.').last;
  }
}

/// Extension to convert between our VpnStage and flutter_outline_vpn's VpnStage.
extension VpnStageExtension on VpnStage {
  /// Convert to flutter_outline_vpn's VpnStage.
  flutter_outline_vpn.VpnStage toOutlineVpnStage() {
    switch (this) {
      case VpnStage.initializing:
        return flutter_outline_vpn.VpnStage.prepare;
      case VpnStage.initialized:
        return flutter_outline_vpn.VpnStage.waitConnection;
      case VpnStage.connecting:
        return flutter_outline_vpn.VpnStage.connecting;
      case VpnStage.connected:
        return flutter_outline_vpn.VpnStage.connected;
      case VpnStage.disconnecting:
        return flutter_outline_vpn.VpnStage.disconnecting;
      case VpnStage.disconnected:
        return flutter_outline_vpn.VpnStage.disconnected;
      case VpnStage.error:
        return flutter_outline_vpn.VpnStage.error;
    }
  }
}

/// Extension to convert from flutter_outline_vpn's VpnStage to our VpnStage.
extension OutlineVpnStageExtension on flutter_outline_vpn.VpnStage {
  /// Convert to our VpnStage.
  VpnStage toAppVpnStage() {
    switch (this) {
      case flutter_outline_vpn.VpnStage.prepare:
      case flutter_outline_vpn.VpnStage.vpnGenerateConfig:
        return VpnStage.initializing;
      case flutter_outline_vpn.VpnStage.waitConnection:
        return VpnStage.initialized;
      case flutter_outline_vpn.VpnStage.connecting:
      case flutter_outline_vpn.VpnStage.authenticating:
      case flutter_outline_vpn.VpnStage.getConfig:
      case flutter_outline_vpn.VpnStage.assignIp:
      case flutter_outline_vpn.VpnStage.tcpConnect:
      case flutter_outline_vpn.VpnStage.udpConnect:
      case flutter_outline_vpn.VpnStage.resolve:
        return VpnStage.connecting;
      case flutter_outline_vpn.VpnStage.connected:
        return VpnStage.connected;
      case flutter_outline_vpn.VpnStage.disconnecting:
      case flutter_outline_vpn.VpnStage.exiting:
        return VpnStage.disconnecting;
      case flutter_outline_vpn.VpnStage.disconnected:
        return VpnStage.disconnected;
      case flutter_outline_vpn.VpnStage.error:
      case flutter_outline_vpn.VpnStage.denied:
      case flutter_outline_vpn.VpnStage.unknown:
        return VpnStage.error;
    }
  }
}
