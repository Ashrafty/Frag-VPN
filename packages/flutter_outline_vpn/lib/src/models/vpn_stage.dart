/// Represents different stages of a VPN connection
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

  /// Get a human-readable name for the stage
  String get name {
    return toString().split('.').last;
  }

  /// Convert from a string to enum value
  static VpnStage fromString(String? value) {
    if (value == null) return VpnStage.unknown;

    try {
      return VpnStage.values.firstWhere(
        (stage) => stage.name.toLowerCase() == value.toLowerCase(),
      );
    } catch (_) {
      return VpnStage.unknown;
    }
  }
}
