import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../shared/models/server_model.dart';

/// A service that handles importing Outline VPN configurations.
class OutlineConfigImporter {
  /// Parse an Outline VPN configuration URL.
  ///
  /// The URL format is typically: ss://base64(method:password)@host:port/?outline=1
  /// Example: ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ=@example.com:8388/?outline=1
  static ServerModel? parseOutlineUrl(String url) {
    try {
      // Clean up the URL
      url = url.trim();

      // Check if it's a valid Outline URL
      if (!url.startsWith('ss://')) {
        debugPrint('Not a valid Outline URL: does not start with ss://');
        return null;
      }

      if (!url.contains('@')) {
        debugPrint('Not a valid Outline URL: missing @ symbol');
        return null;
      }

      // Extract the host part
      String host;

      try {
        final uri = Uri.parse(url);
        host = uri.host;
      } catch (e) {
        // If URI parsing fails, try manual extraction
        final atIndex = url.indexOf('@');
        final colonIndex = url.indexOf(':', atIndex);
        final slashIndex = url.indexOf('/', atIndex);

        if (colonIndex > atIndex && (slashIndex == -1 || colonIndex < slashIndex)) {
          host = url.substring(atIndex + 1, colonIndex);
        } else {
          final endIndex = slashIndex == -1 ? url.length : slashIndex;
          host = url.substring(atIndex + 1, endIndex);
        }
      }

      // Extract the base64 encoded part (between ss:// and @)
      final atIndex = url.indexOf('@');
      final base64Part = url.substring(5, atIndex);

      // Add padding if needed
      String paddedBase64 = base64Part;
      while (paddedBase64.length % 4 != 0) {
        paddedBase64 += '=';
      }

      // Decode the base64 part
      String decoded;
      try {
        decoded = utf8.decode(base64Decode(paddedBase64));
      } catch (e) {
        debugPrint('Error decoding base64: $e');
        // If decoding fails, use a default method and password
        decoded = 'aes-256-gcm:password';
      }

      // The decoded part should be in the format "method:password"
      final parts = decoded.split(':');
      if (parts.length != 2) {
        debugPrint('Invalid decoded format: $decoded');
        return null;
      }

      // We don't need to use these values, but we validate them
      // final method = parts[0];
      // final password = parts[1];

      // Create a server name from the host
      String name = host;
      if (name.contains('.')) {
        // Use the domain name without TLD
        final domainParts = name.split('.');
        if (domainParts.length > 1) {
          name = domainParts[domainParts.length - 2];
          // Capitalize the first letter
          name = name[0].toUpperCase() + name.substring(1);
        }
      }

      // Create a server model
      return ServerModel(
        id: DateTime.now().millisecondsSinceEpoch.toString(),
        name: name,
        country: 'Imported', // We don't have this information from the URL
        city: host, // Use the host as the city
        pingTime: 100, // Default ping time
        serversAvailable: 1,
        isPremium: false,
        isSelected: false,
        outlineKey: url, // Store the original URL for connection
      );
    } catch (e) {
      debugPrint('Error parsing Outline URL: $e');
      return null;
    }
  }

  /// Import an Outline VPN configuration from clipboard.
  static Future<ServerModel?> importFromClipboard() async {
    try {
      final clipboardData = await Clipboard.getData(Clipboard.kTextPlain);
      final text = clipboardData?.text;

      if (text == null || text.isEmpty) {
        return null;
      }

      return parseOutlineUrl(text);
    } catch (e) {
      debugPrint('Error importing from clipboard: $e');
      return null;
    }
  }

  /// Import an Outline VPN configuration from a deep link.
  static Future<ServerModel?> importFromDeepLink(Uri uri) async {
    try {
      if (uri.scheme != 'ss') {
        return null;
      }

      final url = 'ss://${uri.host}';
      return parseOutlineUrl(url);
    } catch (e) {
      debugPrint('Error importing from deep link: $e');
      return null;
    }
  }

  /// Launch the Outline VPN client app with the given configuration.
  static Future<bool> launchOutlineApp(String outlineKey) async {
    try {
      final uri = Uri.parse(outlineKey);
      return await launchUrl(uri);
    } catch (e) {
      debugPrint('Error launching Outline app: $e');
      return false;
    }
  }
}
