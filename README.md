# Frag VPN

A secure and reliable VPN client for Outline VPN servers, built with Flutter.

![Frag VPN Logo](assets/images/logo.png)

## About the Project

Frag VPN is a modern, user-friendly VPN client specifically designed to work with Outline VPN servers. It provides a secure connection to help protect your privacy and bypass internet restrictions.

### Key Features

- **Secure Connection**: Connect to Outline VPN servers with strong encryption
- **Server Management**: Import and manage multiple VPN server configurations
- **QR Code Scanning**: Easily import server configurations via QR code
- **Usage Statistics**: Monitor your data usage with detailed statistics
- **Multi-language Support**: Available in 8 languages:
  - English
  - Spanish
  - French
  - German
  - Arabic
  - Chinese
  - Russian
  - Persian
- **Dark Theme**: Sleek, modern dark interface for comfortable viewing
- **Open Source**: Transparent, community-driven development

## Getting Started

### Prerequisites

- Flutter SDK (version 3.7.2 or higher)
- Dart SDK (version 3.0.0 or higher)
- Android Studio / VS Code with Flutter extensions
- An Android device or emulator (API level 21+) or iOS device (iOS 11+)

### Installation

1. **Clone the repository**

```bash
git clone https://github.com/yourusername/frag_vpn.git
cd frag_vpn
```

2. **Install dependencies**

```bash
flutter pub get
```

3. **Run the app**

```bash
flutter run
```

### Importing a VPN Server

1. Open the app and navigate to the "Locations" tab
2. Tap the "+" icon in the top right corner
3. Enter your Outline VPN server configuration (ss:// URL) or scan a QR code
4. Tap "Import" to add the server

### Connecting to a VPN

1. Select a server from the "Locations" tab
2. Go to the "Home" tab
3. Tap the "Connect" button to establish a VPN connection

## Architecture

Frag VPN follows a clean architecture approach with:

- **Feature-based organization**: Each major feature has its own directory
- **Provider pattern**: State management using the Provider package
- **Repository pattern**: Data access through repositories
- **Service layer**: Business logic encapsulated in services

## Technologies Used

- **Flutter**: UI framework
- **Provider**: State management
- **Go Router**: Navigation
- **flutter_outline_vpn**: VPN connectivity
- **FL Chart**: Data visualization
- **Shared Preferences**: Local storage
- **Mobile Scanner**: QR code scanning

## Localization

The app supports multiple languages through a custom localization system. Language files are stored in the `assets/lang/` directory as JSON files.

To add a new language:
1. Create a new JSON file in the `assets/lang/` directory (e.g., `de.json`)
2. Copy the structure from an existing language file
3. Translate all the strings
4. Add the new locale to the supported locales list in `main.dart`

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- [Outline VPN](https://getoutline.org/) for their VPN technology
- The Flutter team for their amazing framework
- All contributors who have helped improve this project
