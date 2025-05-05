import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:uuid/uuid.dart';

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
      debugPrint('Parsing URL: $url');

      // Handle URLs with special characters or formatting
      if (url.contains('\\n') || url.contains('\\r')) {
        url = url.replaceAll('\\n', '').replaceAll('\\r', '');
        debugPrint('Cleaned URL: $url');
      }

      // Check if it's a valid Outline URL
      if (!url.startsWith('ss://')) {
        debugPrint('Not a valid Outline URL: does not start with ss://');
        return null;
      }

      // Try different parsing approaches
      ServerModel? server = _parseSIP002Format(url) ??
                           _parseAlternativeFormat(url) ??
                           _createFallbackServer(url);

      return server;
    } catch (e) {
      debugPrint('Error parsing Outline URL: $e');
      return null;
    }
  }

  /// Parse a URL in SIP002 format: ss://base64(method:password)@host:port
  static ServerModel? _parseSIP002Format(String url) {
    try {
      debugPrint('Trying SIP002 format parsing');

      if (!url.contains('@')) {
        debugPrint('Not SIP002 format: missing @ symbol');
        return null;
      }

      // Parse the URI
      final uri = Uri.parse(url);
      final host = uri.host;
      final port = uri.port > 0 ? uri.port : 8388; // Default port is 8388

      debugPrint('Extracted host: $host, port: $port');

      // Extract the userinfo part (between ss:// and @)
      final atIndex = url.indexOf('@');
      final userInfo = url.substring(5, atIndex);

      // Check if userinfo is Base64 encoded
      String method = 'aes-256-gcm'; // Default method

      try {
        // Add padding if needed
        String paddedBase64 = userInfo;
        while (paddedBase64.length % 4 != 0) {
          paddedBase64 += '=';
        }

        // Try to decode as Base64
        final decoded = utf8.decode(base64Decode(paddedBase64));
        debugPrint('Decoded userinfo: $decoded');

        // The decoded part should be in the format "method:password"
        final parts = decoded.split(':');
        if (parts.length >= 2) {
          method = parts[0];
          String password = parts[1]; // Not used, but extracted for validation
          debugPrint('Extracted method: $method, password: ${password.substring(0, min(3, password.length))}***');
        } else {
          debugPrint('Invalid decoded format');
          return null;
        }
      } catch (e) {
        debugPrint('Not Base64 encoded, trying direct format: $e');

        // If not Base64 encoded, it should be in the format "method:password"
        final parts = Uri.decodeComponent(userInfo).split(':');
        if (parts.length >= 2) {
          method = parts[0];
          String password = parts[1]; // Not used, but extracted for validation
          debugPrint('Extracted method: $method from direct format, password: ${password.substring(0, min(3, password.length))}***');
        } else {
          debugPrint('Invalid direct format');
          return null;
        }
      }

      // Create a server name from the host
      String name = _createServerName(host);

      // Create a server model
      return ServerModel(
        id: const Uuid().v4(),
        name: name,
        country: 'Imported',
        city: host,
        pingTime: 100, // Default ping time
        serversAvailable: 1,
        isPremium: false,
        isSelected: false,
        outlineKey: url, // Store the original URL for connection
      );
    } catch (e) {
      debugPrint('Error parsing SIP002 format: $e');
      return null;
    }
  }

  /// Parse a URL in alternative format (not SIP002)
  static ServerModel? _parseAlternativeFormat(String url) {
    try {
      debugPrint('Trying alternative format parsing');

      // Remove the ss:// prefix
      final base64Part = url.substring(5);

      // Add padding if needed
      String paddedBase64 = base64Part;
      while (paddedBase64.length % 4 != 0) {
        paddedBase64 += '=';
      }

      // Try to decode the entire config
      final decoded = utf8.decode(base64Decode(paddedBase64));
      debugPrint('Decoded alternative format: $decoded');

      // Try to parse as JSON
      try {
        final jsonConfig = json.decode(decoded);
        debugPrint('Parsed as JSON: $jsonConfig');

        if (jsonConfig is Map<String, dynamic>) {
          final server = jsonConfig['server'] as String?;
          final port = jsonConfig['server_port'] as int?;
          final method = jsonConfig['method'] as String?;
          final password = jsonConfig['password'] as String?;

          if (server != null && port != null && method != null && password != null) {
            // Construct a SIP002 URL
            final userInfo = base64Encode(utf8.encode('$method:$password'));
            final sip002Url = 'ss://$userInfo@$server:$port';

            debugPrint('Constructed SIP002 URL: $sip002Url');

            // Create a server name from the host
            String name = _createServerName(server);

            // Create a server model
            return ServerModel(
              id: const Uuid().v4(),
              name: name,
              country: 'Imported',
              city: server,
              pingTime: 100, // Default ping time
              serversAvailable: 1,
              isPremium: false,
              isSelected: false,
              outlineKey: sip002Url, // Store the SIP002 URL for connection
            );
          }
        }
      } catch (e) {
        debugPrint('Not a JSON config: $e');
      }

      // If not JSON, try to parse as a simple string
      final parts = decoded.split(':');
      if (parts.length >= 4) {
        final method = parts[0];
        final password = parts[1];
        final server = parts[2].split('@').last;
        final port = int.tryParse(parts[3]) ?? 8388;

        // Construct a SIP002 URL
        final userInfo = base64Encode(utf8.encode('$method:$password'));
        final sip002Url = 'ss://$userInfo@$server:$port';

        debugPrint('Constructed SIP002 URL from string: $sip002Url');

        // Create a server name from the host
        String name = _createServerName(server);

        // Create a server model
        return ServerModel(
          id: const Uuid().v4(),
          name: name,
          country: 'Imported',
          city: server,
          pingTime: 100, // Default ping time
          serversAvailable: 1,
          isPremium: false,
          isSelected: false,
          outlineKey: sip002Url, // Store the SIP002 URL for connection
        );
      }

      return null;
    } catch (e) {
      debugPrint('Error parsing alternative format: $e');
      return null;
    }
  }

  /// Create a fallback server when all other parsing methods fail
  static ServerModel? _createFallbackServer(String url) {
    try {
      debugPrint('Creating fallback server');

      // Try to extract some information from the URL
      String host = 'unknown';

      if (url.contains('@')) {
        final atIndex = url.indexOf('@');
        final colonIndex = url.indexOf(':', atIndex);

        if (colonIndex > atIndex) {
          host = url.substring(atIndex + 1, colonIndex);
          // Extract port but don't use it
          final portStr = url.substring(colonIndex + 1).split('/')[0].split('?')[0];
          debugPrint('Extracted port: $portStr');
        } else {
          host = url.substring(atIndex + 1).split('/')[0].split('?')[0];
        }
      } else {
        // Just use a default configuration
        host = 'imported-server';
      }

      // Create a server name
      String name = _createServerName(host);

      // Create a server model
      return ServerModel(
        id: const Uuid().v4(),
        name: name,
        country: 'Imported',
        city: host,
        pingTime: 100, // Default ping time
        serversAvailable: 1,
        isPremium: false,
        isSelected: false,
        outlineKey: url, // Store the original URL for connection
      );
    } catch (e) {
      debugPrint('Error creating fallback server: $e');
      return null;
    }
  }

  /// Create a server name from a host
  static String _createServerName(String host) {
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

    return name;
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
