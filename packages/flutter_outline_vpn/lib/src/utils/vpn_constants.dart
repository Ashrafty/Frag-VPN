/// Constants for OutlineVPN package
class VpnConstants {
  /// Validation timeout in seconds for proxy connection validation
  static const int proxyValidationTimeoutSeconds = 10;

  /// Default proxy port
  static const String defaultProxyPort = "0";

  /// Default notification title
  static const String defaultNotificationTitle = "VPN Service";

  /// Default log tag
  static const String logTag = "OutlineVPN";

  /// Outline key prefix
  static const String outlineKeyPrefix = "ss://";

  /// Private constructor to prevent instantiation
  VpnConstants._();
}
