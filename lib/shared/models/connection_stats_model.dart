class ConnectionStatsModel {
  final double uploadSpeed; // in bytes per second
  final double downloadSpeed; // in bytes per second
  final double totalUpload; // in bytes
  final double totalDownload; // in bytes
  final double totalDataUsed; // in bytes
  final Map<String, double> dailyUsage; // day -> bytes

  ConnectionStatsModel({
    this.uploadSpeed = 0.0,
    this.downloadSpeed = 0.0,
    this.totalUpload = 0.0,
    this.totalDownload = 0.0,
    this.totalDataUsed = 0.0,
    Map<String, double>? dailyUsage,
  }) : dailyUsage = dailyUsage ?? _getDefaultDailyUsage();

  // Create default daily usage data with zero values
  static Map<String, double> _getDefaultDailyUsage() {
    return {
      'Mon': 0.0,
      'Tue': 0.0,
      'Wed': 0.0,
      'Thu': 0.0,
      'Fri': 0.0,
      'Sat': 0.0,
      'Sun': 0.0,
    };
  }

  ConnectionStatsModel copyWith({
    double? uploadSpeed,
    double? downloadSpeed,
    double? totalUpload,
    double? totalDownload,
    double? totalDataUsed,
    Map<String, double>? dailyUsage,
  }) {
    return ConnectionStatsModel(
      uploadSpeed: uploadSpeed ?? this.uploadSpeed,
      downloadSpeed: downloadSpeed ?? this.downloadSpeed,
      totalUpload: totalUpload ?? this.totalUpload,
      totalDownload: totalDownload ?? this.totalDownload,
      totalDataUsed: totalDataUsed ?? this.totalDataUsed,
      dailyUsage: dailyUsage ?? this.dailyUsage,
    );
  }

  @override
  String toString() {
    return 'ConnectionStatsModel(uploadSpeed: $uploadSpeed, downloadSpeed: $downloadSpeed, totalUpload: $totalUpload, totalDownload: $totalDownload, totalDataUsed: $totalDataUsed, dailyUsage: $dailyUsage)';
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;

    return other is ConnectionStatsModel &&
      other.uploadSpeed == uploadSpeed &&
      other.downloadSpeed == downloadSpeed &&
      other.totalUpload == totalUpload &&
      other.totalDownload == totalDownload &&
      other.totalDataUsed == totalDataUsed &&
      other.dailyUsage.toString() == dailyUsage.toString();
  }

  @override
  int get hashCode {
    return uploadSpeed.hashCode ^
      downloadSpeed.hashCode ^
      totalUpload.hashCode ^
      totalDownload.hashCode ^
      totalDataUsed.hashCode ^
      dailyUsage.hashCode;
  }
}
