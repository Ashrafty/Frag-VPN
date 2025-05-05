<!--
This README describes the package. If you publish this package to pub.dev,
this README's contents appear on the landing page for your package.

For information about how to write a good package README, see the guide for
[writing package pages](https://dart.dev/tools/pub/writing-package-pages).

For general information about developing packages, see the Dart guide for
[creating packages](https://dart.dev/guides/libraries/create-packages)
and the Flutter guide for
[developing packages and plugins](https://flutter.dev/to/develop-packages).
-->

# Flutter Outline VPN

A Flutter package for integrating Outline SDK-based VPN functionality into Flutter applications. This package provides a simple API for adding VPN capabilities to your app, allowing you to protect user traffic and bypass network-level interference.

[![pub package](https://img.shields.io/pub/v/flutter_outline_vpn.svg)](https://pub.dev/packages/flutter_outline_vpn)

## Features

- 🔒 System-wide VPN using Outline SDK
- 🌍 Anti-censorship capabilities with multiple transport protocols
- 📊 Detailed traffic statistics (download/upload, packets, duration)
- 📱 Platform-specific implementations for both Android and iOS
- 🔔 Customizable notification for VPN status
- 🛠️ Simple API for easy integration

## Getting Started

### Installation

Add the package to your `pubspec.yaml`:

```yaml
dependencies:
  flutter_outline_vpn: ^0.0.1
```

### Platform Setup

#### Android

1. The VPN service requires the following permissions in your app's `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
```

2. Add the VPN service declaration to your `AndroidManifest.xml` inside the `<application>` tag:

```xml
<service
    android:name="com.example.flutter_outline_vpn.OutlineVpnService"
    android:permission="android.permission.BIND_VPN_SERVICE"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <intent-filter>
        <action android:name="android.net.VpnService"/>
    </intent-filter>
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="vpn"/>
</service>
```

3. Copy the `mobileproxy.aar` file from the Outline SDK to your app's `android/app/libs/` directory.

#### iOS

1. Enable the Network Extensions capability in your Xcode project:

   - Open your app in Xcode
   - Select your target, go to "Signing & Capabilities"
   - Click "+ Capability" and add "Network Extensions"
   - Check "Packet Tunnel"

2. Create a Network Extension target:

   - In Xcode, go to File > New > Target
   - Select "Network Extension" and click "Next"
   - Enter a name (e.g., "VpnExtension") and click "Finish"
   - Ensure both your main app and extension targets are properly signed

3. Copy the `mobileproxy.xcframework` to your iOS project and link it with both your main app and extension targets.

4. Configure App Groups to share data between your app and extension:
   - Add the App Groups capability to both your main app and extension targets
   - Create a new App Group identifier (e.g., "group.com.example.vpn")
   - Check the App Group in both targets

## Usage

### Initialize the VPN Service

Initialize the VPN service early in your app, typically in `main.dart` or during app startup:

```dart
import 'package:flutter_outline_vpn/flutter_outline_vpn.dart';

// Initialize the VPN service
await OutlineVPN().initialize(
  // iOS-specific parameters
  providerBundleIdentifier: 'com.example.app.VpnExtension',
  localizedDescription: 'My VPN Service',
  groupIdentifier: 'group.com.example.app',
);
```

### Connect to VPN

To connect to a VPN server:

```dart
try {
  await OutlineVPN().connect(
    config: 'ss://chacha20-ietf-poly1305:password@example.com:8388',
    name: 'My VPN Connection',
  );
} catch (e) {
  print('Connection error: $e');
}
```

The `config` parameter supports various transport configurations from the Outline SDK, such as:

- Shadowsocks: `ss://chacha20-ietf-poly1305:password@example.com:8388`
- Split connections: `split:3`
- TLS fragmentation: `tlsfrag:1`
- Host override: `override:host=cloudflare.net`
- Combinations: `override:host=cloudflare.net|tlsfrag:1`

### Custom Notification (Android)

Customize the Android notification:

```dart
await OutlineVPN().connect(
  config: 'ss://chacha20-ietf-poly1305:password@example.com:8388',
  name: 'My VPN Connection',
  notificationConfig: NotificationConfig(
    title: 'Protected Connection',
    showDownloadSpeed: true,
    showUploadSpeed: true,
    androidIconResourceName: 'ic_vpn_notification',
  ),
);
```

### Monitor VPN Status

Listen for VPN status updates:

```dart
// Monitor connection stage changes
OutlineVPN().onStageChanged.listen((stage) {
  print('VPN stage changed: ${stage.name}');

  if (stage == VpnStage.connected) {
    print('VPN connected successfully!');
  } else if (stage == VpnStage.error || stage == VpnStage.disconnected) {
    print('VPN disconnected or error occurred');
  }
});

// Monitor traffic statistics
OutlineVPN().onStatusChanged.listen((status) {
  print('Connection time: ${status.duration}');
  print('Downloaded: ${status.byteIn}');
  print('Uploaded: ${status.byteOut}');
});
```

### Disconnect from VPN

To disconnect from the VPN:

```dart
await OutlineVPN().disconnect();
```

### Check Connection Status

Check if the VPN is currently connected:

```dart
final bool isConnected = await OutlineVPN().isConnected();
print('VPN is connected: $isConnected');
```

### Exclude Apps from VPN (Android only)

On Android, you can exclude specific apps from the VPN tunnel:

```dart
await OutlineVPN().connect(
  config: 'ss://chacha20-ietf-poly1305:password@example.com:8388',
  name: 'My VPN Connection',
  bypassPackages: [
    'com.android.vending',  // Google Play Store
    'com.google.android.apps.maps'  // Google Maps
  ],
);
```

### Request VPN Permission (Android only)

On Android, you can explicitly request VPN permission before connecting:

```dart
final bool permissionGranted = await OutlineVPN().requestPermission();
if (permissionGranted) {
  // Permission granted, proceed with connection
  await OutlineVPN().connect(...);
} else {
  // Permission denied
  print('VPN permission denied by user');
}
```

## Complete Example

```dart
import 'package:flutter/material.dart';
import 'package:flutter_outline_vpn/flutter_outline_vpn.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize the VPN service
  await OutlineVPN().initialize(
    providerBundleIdentifier: 'com.example.app.VpnExtension',
    localizedDescription: 'My VPN Service',
    groupIdentifier: 'group.com.example.app',
  );

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Outline VPN Demo',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: const VpnScreen(),
    );
  }
}

class VpnScreen extends StatefulWidget {
  const VpnScreen({Key? key}) : super(key: key);

  @override
  State<VpnScreen> createState() => _VpnScreenState();
}

class _VpnScreenState extends State<VpnScreen> {
  VpnStage _stage = VpnStage.disconnected;
  VpnStatus _status = VpnStatus.empty();
  final String _serverConfig = 'split:3'; // Example config

  @override
  void initState() {
    super.initState();
    _setupVpnListeners();
  }

  void _setupVpnListeners() {
    OutlineVPN().onStageChanged.listen((stage) {
      setState(() {
        _stage = stage;
      });
    });

    OutlineVPN().onStatusChanged.listen((status) {
      setState(() {
        _status = status;
      });
    });
  }

  Future<void> _connectVpn() async {
    try {
      await OutlineVPN().connect(
        config: _serverConfig,
        name: 'Demo VPN',
        notificationConfig: const NotificationConfig(
          title: 'VPN Active',
          showDownloadSpeed: true,
          showUploadSpeed: true,
        ),
      );
    } catch (e) {
      print('Error connecting to VPN: $e');
    }
  }

  Future<void> _disconnectVpn() async {
    try {
      await OutlineVPN().disconnect();
    } catch (e) {
      print('Error disconnecting from VPN: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final bool isConnected = _stage == VpnStage.connected;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Outline VPN Demo'),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(
              'Status: ${_stage.name}',
              style: const TextStyle(fontSize: 18),
            ),
            const SizedBox(height: 20),
            if (isConnected) ...[
              Text('Duration: ${_status.duration ?? "00:00:00"}'),
              Text('Downloaded: ${_status.byteIn ?? "0"}'),
              Text('Uploaded: ${_status.byteOut ?? "0"}'),
              const SizedBox(height: 20),
            ],
            ElevatedButton(
              onPressed: isConnected ? _disconnectVpn : _connectVpn,
              child: Text(isConnected ? 'Disconnect' : 'Connect'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    OutlineVPN().dispose();
    super.dispose();
  }
}
```

## How It Works

### Architecture

This package uses a platform-specific approach to provide VPN functionality:

1. **Android**: Uses Android's VpnService API to create a system-wide VPN tunnel.
2. **iOS**: Uses NetworkExtension framework with a Packet Tunnel Provider.

Both platforms integrate with the Outline SDK's MobileProxy component, which provides:

- A local proxy server running on your device
- Multiple transport protocols for censorship circumvention
- Secure tunneling of network traffic

### Differences from Traditional VPNs

Unlike traditional VPNs that route all traffic through a remote server, the Flutter Outline VPN can use various transport mechanisms:

- **Split mode**: Splits TCP connections into multiple fragments to evade detection
- **TLS fragmentation**: Fragments TLS records to bypass deep packet inspection
- **Host override**: Uses alternative domain names to bypass SNI filtering
- **Shadowsocks**: Encrypts and tunnels traffic through a Shadowsocks proxy

## License

This package is available under the MIT License.

## Credits

This package is built with the [Outline SDK](https://github.com/Jigsaw-Code/outline-sdk), a project by Jigsaw (Google).
