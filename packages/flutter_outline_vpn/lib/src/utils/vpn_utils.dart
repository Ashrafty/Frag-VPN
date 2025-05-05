import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;

/// Utility functions for VPN operations
class VpnUtils {
  /// Format bytes to human-readable format (KB, MB, GB)
  static String formatBytes(int bytes, {int decimals = 2}) {
    if (bytes <= 0) return '0 B';

    const suffixes = ['B', 'KB', 'MB', 'GB', 'TB'];
    var i = (math.log(bytes) / math.log(1024)).floor();

    return '${(bytes / math.pow(1024, i)).toStringAsFixed(decimals)} ${suffixes[i]}';
  }

  /// Format duration in seconds to human-readable format (HH:MM:SS)
  static String formatDuration(int seconds) {
    final hours = (seconds / 3600).floor();
    final minutes = ((seconds % 3600) / 60).floor();
    final secs = seconds % 60;

    return '${hours.toString().padLeft(2, '0')}:'
        '${minutes.toString().padLeft(2, '0')}:'
        '${secs.toString().padLeft(2, '0')}';
  }

  /// Determine if running on an iOS platform
  static bool get isIOS => Platform.isIOS;

  /// Determine if running on an Android platform
  static bool get isAndroid => Platform.isAndroid;

  /// Encode a VPN configuration to a string
  static String encodeConfig(Map<String, dynamic> config) {
    return base64Encode(utf8.encode(jsonEncode(config)));
  }

  /// Decode a VPN configuration from a string
  static Map<String, dynamic> decodeConfig(String encodedConfig) {
    final decoded = utf8.decode(base64Decode(encodedConfig));
    return jsonDecode(decoded) as Map<String, dynamic>;
  }

  /// Parse an Outline transport config URL
  static Map<String, dynamic> parseOutlineUrl(String url) {
    // Example URL: ss://chacha20-ietf-poly1305:password@server:port/?outline=1
    final uri = Uri.parse(url);

    if (!uri.scheme.startsWith('ss')) {
      throw FormatException('Invalid Outline URL scheme: ${uri.scheme}');
    }

    final userInfo = uri.userInfo.split(':');
    if (userInfo.length != 2) {
      throw const FormatException('Invalid user info in Outline URL');
    }

    return {
      'method': userInfo[0],
      'password': userInfo[1],
      'server': uri.host,
      'port': uri.port,
      'outline': uri.queryParameters['outline'] == '1',
    };
  }
}
