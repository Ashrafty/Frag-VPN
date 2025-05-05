class AppConstants {
  // Private constructor to prevent instantiation
  AppConstants._();

  // App Info
  static const String appName = 'Frag VPN';
  static const String appVersion = '1.0.0';

  // Routes
  static const String homeRoute = '/';
  static const String locationsRoute = '/locations';
  static const String statisticsRoute = '/statistics';
  static const String settingsRoute = '/settings';
  static const String qrScannerRoute = '/qr-scanner';

  // Shared Preferences Keys
  static const String prefSelectedServer = 'selected_server';
  static const String prefIsConnected = 'is_connected';
  static const String prefLastConnected = 'last_connected';
  static const String prefTotalDataUsed = 'total_data_used';
  static const String prefUploadData = 'upload_data';
  static const String prefDownloadData = 'download_data';
  static const String prefDailyUsage = 'daily_usage';

  // Default Values
  static const int defaultPingTime = 100; // ms
  static const double defaultDataLimit = 100.0; // GB
}
