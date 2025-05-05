/// Configuration for initializing the VPN service
class VpnConfig {
  /// Maximum supported MTU size
  final int? mtu;

  /// Should route all traffic through VPN
  final bool routeAllTraffic;

  /// DNS servers to use
  final List<String>? dnsServers;

  /// Constructor with defaults
  const VpnConfig({
    this.mtu,
    this.routeAllTraffic = true,
    this.dnsServers,
  });

  /// Convert to a map for platform channel
  Map<String, dynamic> toMap() => {
        if (mtu != null) 'mtu': mtu,
        'routeAllTraffic': routeAllTraffic,
        if (dnsServers != null) 'dnsServers': dnsServers,
      };

  /// Create from map received from platform channel
  factory VpnConfig.fromMap(Map<String, dynamic> map) {
    return VpnConfig(
      mtu: map['mtu'] as int?,
      routeAllTraffic: map['routeAllTraffic'] as bool? ?? true,
      dnsServers: map['dnsServers'] != null ? List<String>.from(map['dnsServers'] as List) : null,
    );
  }

  @override
  String toString() {
    return 'VpnConfig(mtu: $mtu, routeAllTraffic: $routeAllTraffic, dnsServers: $dnsServers)';
  }
}
