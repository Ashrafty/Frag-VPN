// This is a stub implementation for platforms that don't support VPN
// (like web, desktop, etc.)

import 'dart:async';

// Mock classes to match the outline_vpn API
class OutlineVPN {
  static final OutlineVPN instance = OutlineVPN._internal();
  
  factory OutlineVPN() => instance;
  
  OutlineVPN._internal();
  
  Future<void> initialize({
    String? providerBundleIdentifier,
    String? localizedDescription,
    String? groupIdentifier,
    VpnConfig? config,
  }) async {
    // No-op for non-mobile platforms
    return;
  }
  
  Future<void> connect({
    required String outlineKey,
    String? port,
    required String name,
    List<String>? bypassPackages,
    NotificationConfig? notificationConfig,
  }) async {
    // No-op for non-mobile platforms
    throw PlatformNotSupportedException('VPN is not supported on this platform');
  }
  
  Future<void> disconnect() async {
    // No-op for non-mobile platforms
    return;
  }
  
  Future<bool> isConnected() async {
    // Always return false for non-mobile platforms
    return false;
  }
  
  Future<VpnStage> getCurrentStage() async {
    // Always return disconnected for non-mobile platforms
    return VpnStage.disconnected;
  }
  
  Future<VpnStatus> getStatus() async {
    // Return empty status for non-mobile platforms
    return VpnStatus();
  }
  
  Future<bool> requestPermission() async {
    // Always return false for non-mobile platforms
    return false;
  }
  
  Stream<VpnStage> get onStageChanged {
    // Return an empty stream for non-mobile platforms
    return Stream<VpnStage>.empty();
  }
  
  Stream<VpnStatus> get onStatusChanged {
    // Return an empty stream for non-mobile platforms
    return Stream<VpnStatus>.empty();
  }
  
  Future<void> dispose() async {
    // No-op for non-mobile platforms
    return;
  }
  
  Future<String> testOutlineKey(String outlineKey) async {
    // No-op for non-mobile platforms
    return 'VPN is not supported on this platform';
  }
}

class VpnConfig {
  final int? mtu;
  final bool routeAllTraffic;
  final List<String>? dnsServers;
  
  const VpnConfig({
    this.mtu,
    this.routeAllTraffic = true,
    this.dnsServers,
  });
}

class NotificationConfig {
  final String title;
  final bool showDownloadSpeed;
  final bool showUploadSpeed;
  final String? androidIconResourceName;
  
  const NotificationConfig({
    this.title = 'VPN Service',
    this.showDownloadSpeed = true,
    this.showUploadSpeed = true,
    this.androidIconResourceName,
  });
}

class VpnStatus {
  final DateTime? connectedOn;
  final String? duration;
  final int? byteIn;
  final int? byteOut;
  final int? packetsIn;
  final int? packetsOut;
  
  const VpnStatus({
    this.connectedOn,
    this.duration,
    this.byteIn,
    this.byteOut,
    this.packetsIn,
    this.packetsOut,
  });
}

enum VpnStage {
  prepare,
  authenticating,
  connecting,
  connected,
  disconnected,
  disconnecting,
  denied,
  error,
  waitConnection,
  vpnGenerateConfig,
  getConfig,
  tcpConnect,
  udpConnect,
  assignIp,
  resolve,
  exiting,
  unknown;
  
  String get name {
    return toString().split('.').last;
  }
}

class PlatformNotSupportedException implements Exception {
  final String message;
  
  PlatformNotSupportedException(this.message);
  
  @override
  String toString() => 'PlatformNotSupportedException: $message';
}
