/// Configuration for VPN service notifications
class NotificationConfig {
  /// Notification title
  final String title;

  /// Show download speed in notification
  final bool showDownloadSpeed;

  /// Show upload speed in notification
  final bool showUploadSpeed;

  /// Custom icon resource name (Android only)
  final String? androidIconResourceName;

  /// Constructor with defaults
  const NotificationConfig({
    this.title = 'VPN Service',
    this.showDownloadSpeed = true,
    this.showUploadSpeed = true,
    this.androidIconResourceName,
  });

  /// Convert to a map for platform channel
  Map<String, dynamic> toMap() => {
        'title': title,
        'showDownloadSpeed': showDownloadSpeed,
        'showUploadSpeed': showUploadSpeed,
        'androidIconResourceName': androidIconResourceName,
      };

  /// Create from map received from platform channel
  factory NotificationConfig.fromMap(Map<String, dynamic> map) {
    return NotificationConfig(
      title: map['title'] as String? ?? 'VPN Service',
      showDownloadSpeed: map['showDownloadSpeed'] as bool? ?? true,
      showUploadSpeed: map['showUploadSpeed'] as bool? ?? true,
      androidIconResourceName: map['androidIconResourceName'] as String?,
    );
  }

  @override
  String toString() {
    return 'NotificationConfig(title: $title, showDownloadSpeed: $showDownloadSpeed, '
        'showUploadSpeed: $showUploadSpeed, androidIconResourceName: $androidIconResourceName)';
  }
}
