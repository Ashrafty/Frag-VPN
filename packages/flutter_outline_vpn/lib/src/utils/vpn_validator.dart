import 'dart:convert';
import 'vpn_constants.dart';

/// Utility class for validating VPN-related inputs
class VpnValidator {
  /// Private constructor to prevent instantiation
  VpnValidator._();

  /// Validate an Outline key format
  ///
  /// An Outline key should follow the format: ss://<base64-encoded-data>[@hostname[:port]]
  /// The base64 encoded data contains method:password
  static bool isValidOutlineKey(String key) {
    if (key.isEmpty || !key.startsWith(VpnConstants.outlineKeyPrefix)) {
      return false;
    }

    try {
      // Extract the base64 part (between ss:// and optional @)
      final encodedPart = key.substring(VpnConstants.outlineKeyPrefix.length);
      final atIndex = encodedPart.indexOf('@');

      final base64Part = atIndex >= 0 ? encodedPart.substring(0, atIndex) : encodedPart;

      // Attempt to decode the base64 string
      // Some Outline keys use URL-safe base64 which requires replacing chars
      var cleanedBase64 = base64Part.replaceAll('-', '+').replaceAll('_', '/');

      // Add padding if needed
      final padding = cleanedBase64.length % 4;
      if (padding > 0) {
        cleanedBase64 = cleanedBase64.padRight(cleanedBase64.length + (4 - padding), '=');
      }

      // Try to decode and check if it contains method:password format
      final decoded = utf8.decode(base64.decode(cleanedBase64));
      return decoded.contains(':');
    } catch (e) {
      // If any exception occurs during validation, the key is invalid
      return false;
    }
  }
}
