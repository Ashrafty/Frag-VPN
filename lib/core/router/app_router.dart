import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../features/home/presentation/home_screen.dart';
import '../../features/locations/presentation/locations_screen.dart';
import '../../features/qr_scanner/presentation/qr_scanner_screen.dart';
import '../../features/settings/presentation/settings_screen.dart';
import '../../features/statistics/presentation/statistics_screen.dart';
import '../constants/app_constants.dart';

class AppRouter {
  // Private constructor to prevent instantiation
  AppRouter._();

  static final GoRouter router = GoRouter(
    initialLocation: AppConstants.homeRoute,
    routes: [
      GoRoute(
        path: AppConstants.homeRoute,
        builder: (BuildContext context, GoRouterState state) {
          return const HomeScreen();
        },
      ),
      GoRoute(
        path: AppConstants.locationsRoute,
        builder: (BuildContext context, GoRouterState state) {
          return const LocationsScreen();
        },
      ),
      GoRoute(
        path: AppConstants.statisticsRoute,
        builder: (BuildContext context, GoRouterState state) {
          return const StatisticsScreen();
        },
      ),
      GoRoute(
        path: AppConstants.settingsRoute,
        builder: (BuildContext context, GoRouterState state) {
          return const SettingsScreen();
        },
      ),
      GoRoute(
        path: AppConstants.qrScannerRoute,
        builder: (BuildContext context, GoRouterState state) {
          return const QrScannerScreen();
        },
      ),
    ],
    errorBuilder: (context, state) => Scaffold(
      body: Center(
        child: Text('Error: ${state.error}'),
      ),
    ),
  );
}
