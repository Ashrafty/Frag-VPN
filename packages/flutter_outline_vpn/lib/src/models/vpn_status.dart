/// Represents the current status of a VPN connection
class VpnStatus {
  /// When the connection was established
  final DateTime? connectedOn;

  /// Human-readable connection duration (HH:MM:SS)
  final String? duration;

  /// Download bytes
  final int? byteIn;

  /// Upload bytes
  final int? byteOut;

  /// Incoming packet count
  final int? packetsIn;

  /// Outgoing packet count
  final int? packetsOut;

  /// Constructor
  const VpnStatus({
    this.connectedOn,
    this.duration,
    this.byteIn,
    this.byteOut,
    this.packetsIn,
    this.packetsOut,
  });

  /// Empty status factory
  factory VpnStatus.empty() => const VpnStatus();

  /// JSON conversion methods
  Map<String, dynamic> toJson() => {
        'connectedOn': connectedOn?.toIso8601String(),
        'duration': duration,
        'byteIn': byteIn,
        'byteOut': byteOut,
        'packetsIn': packetsIn,
        'packetsOut': packetsOut,
      };

  /// From JSON factory
  factory VpnStatus.fromJson(Map<String, dynamic> json) {
    return VpnStatus(
      connectedOn: json['connectedOn'] != null
          ? DateTime.fromMillisecondsSinceEpoch(json['connectedOn'] as int)
          : null,
      duration: json['duration'] as String?,
      byteIn: json['byteIn'] is String ? int.tryParse(json['byteIn'] as String) : json['byteIn'] as int?,
      byteOut: json['byteOut'] is String ? int.tryParse(json['byteOut'] as String) : json['byteOut'] as int?,
      packetsIn:
          json['packetsIn'] is String ? int.tryParse(json['packetsIn'] as String) : json['packetsIn'] as int?,
      packetsOut: json['packetsOut'] is String
          ? int.tryParse(json['packetsOut'] as String)
          : json['packetsOut'] as int?,
    );
  }

  @override
  String toString() {
    return 'VpnStatus(connectedOn: $connectedOn, duration: $duration, '
        'byteIn: $byteIn, byteOut: $byteOut, '
        'packetsIn: $packetsIn, packetsOut: $packetsOut)';
  }
}
