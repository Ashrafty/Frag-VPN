import 'dart:async';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../constants/app_constants.dart';
import '../../shared/models/connection_status_model.dart';
import '../../shared/models/connection_stats_model.dart';
import '../../shared/models/server_model.dart';

/// A service that manages VPN connections.
/// 
/// This is a mock implementation based on the Outline VPN SDK.
/// In a real implementation, this would use the actual Outline VPN SDK.
class VpnService {
  // Singleton instance
  static final VpnService _instance = VpnService._internal();
  factory VpnService() => _instance;
  VpnService._internal();

  // Stream controllers
  final _connectionStatusController = StreamController<ConnectionStatusModel>.broadcast();
  final _connectionStatsController = StreamController<ConnectionStatsModel>.broadcast();
  final _vpnStageController = StreamController<VpnStage>.broadcast();

  // Current state
  ConnectionStatusModel _connectionStatus = ConnectionStatusModel(
    state: VpnConnectionState.disconnected,
    currentServer: null,
  );

  ConnectionStatsModel _connectionStats = ConnectionStatsModel(
    totalDataUsed: 47.8 * 1024 * 1024 * 1024, // 47.8 GB
    totalUpload: 18.3 * 1024 * 1024 * 1024, // 18.3 GB
    totalDownload: 29.5 * 1024 * 1024 * 1024, // 29.5 GB
    dailyUsage: {
      'Mon': 5.2 * 1024 * 1024 * 1024,
      'Tue': 8.7 * 1024 * 1024 * 1024,
      'Wed': 6.3 * 1024 * 1024 * 1024,
      'Thu': 9.1 * 1024 * 1024 * 1024,
      'Fri': 7.5 * 1024 * 1024 * 1024,
      'Sat': 4.8 * 1024 * 1024 * 1024,
      'Sun': 6.2 * 1024 * 1024 * 1024,
    },
  );

  ServerModel? _selectedServer;
  Timer? _connectionTimer;
  final Random _random = Random();
  bool _isInitialized = false;

  // Getters
  Stream<ConnectionStatusModel> get onConnectionStatusChanged => _connectionStatusController.stream;
  Stream<ConnectionStatsModel> get onConnectionStatsChanged => _connectionStatsController.stream;
  Stream<VpnStage> get onVpnStageChanged => _vpnStageController.stream;
  ConnectionStatusModel get connectionStatus => _connectionStatus;
  ConnectionStatsModel get connectionStats => _connectionStats;
  ServerModel? get selectedServer => _selectedServer;
  bool get isInitialized => _isInitialized;

  /// Initialize the VPN service.
  Future<void> initialize() async {
    if (_isInitialized) return;
    
    try {
      // In a real implementation, this would initialize the Outline VPN SDK
      _vpnStageController.add(VpnStage.initializing);
      
      // Simulate initialization delay
      await Future.delayed(const Duration(seconds: 1));
      
      _isInitialized = true;
      _vpnStageController.add(VpnStage.initialized);
      
      // Load saved data
      await _loadSavedData();
      
      return;
    } catch (e) {
      _vpnStageController.add(VpnStage.error);
      rethrow;
    }
  }

  /// Connect to the VPN using the specified server and configuration.
  Future<void> connect({
    required ServerModel server,
    String transportConfig = 'split:3',
  }) async {
    if (!_isInitialized) {
      throw Exception('VPN service not initialized. Call initialize() first.');
    }
    
    if (_connectionStatus.isConnected || _connectionStatus.isConnecting) {
      return;
    }
    
    try {
      // Set state to connecting
      _selectedServer = server;
      _connectionStatus = _connectionStatus.copyWith(
        state: VpnConnectionState.connecting,
        currentServer: server,
      );
      _connectionStatusController.add(_connectionStatus);
      _vpnStageController.add(VpnStage.connecting);
      
      // Simulate connection delay
      await Future.delayed(const Duration(seconds: 2));
      
      // Set state to connected
      final now = DateTime.now();
      _connectionStatus = _connectionStatus.copyWith(
        state: VpnConnectionState.connected,
        connectedSince: now,
      );
      _connectionStatusController.add(_connectionStatus);
      _vpnStageController.add(VpnStage.connected);
      
      _startConnectionTimer();
      _saveData();
      
      return;
    } catch (e) {
      _connectionStatus = _connectionStatus.copyWith(
        state: VpnConnectionState.error,
        errorMessage: e.toString(),
      );
      _connectionStatusController.add(_connectionStatus);
      _vpnStageController.add(VpnStage.error);
      rethrow;
    }
  }

  /// Disconnect from the VPN.
  Future<void> disconnect() async {
    if (!_isInitialized) {
      throw Exception('VPN service not initialized. Call initialize() first.');
    }
    
    if (_connectionStatus.isDisconnected || _connectionStatus.isDisconnecting) {
      return;
    }
    
    try {
      // Set state to disconnecting
      _connectionStatus = _connectionStatus.copyWith(
        state: VpnConnectionState.disconnecting,
      );
      _connectionStatusController.add(_connectionStatus);
      _vpnStageController.add(VpnStage.disconnecting);
      
      // Stop the connection timer
      _connectionTimer?.cancel();
      _connectionTimer = null;
      
      // Simulate disconnection delay
      await Future.delayed(const Duration(seconds: 1));
      
      // Set state to disconnected
      _connectionStatus = _connectionStatus.copyWith(
        state: VpnConnectionState.disconnected,
        connectedSince: null,
      );
      _connectionStatusController.add(_connectionStatus);
      _vpnStageController.add(VpnStage.disconnected);
      
      // Reset speeds
      _connectionStats = _connectionStats.copyWith(
        uploadSpeed: 0,
        downloadSpeed: 0,
      );
      _connectionStatsController.add(_connectionStats);
      
      _saveData();
      
      return;
    } catch (e) {
      _connectionStatus = _connectionStatus.copyWith(
        state: VpnConnectionState.error,
        errorMessage: e.toString(),
      );
      _connectionStatusController.add(_connectionStatus);
      _vpnStageController.add(VpnStage.error);
      rethrow;
    }
  }

  /// Get the current connection status.
  Future<ConnectionStatusModel> getStatus() async {
    return _connectionStatus;
  }

  /// Get the current connection statistics.
  Future<ConnectionStatsModel> getStats() async {
    return _connectionStats;
  }

  /// Load saved data from SharedPreferences.
  Future<void> _loadSavedData() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      
      // Load connection status
      final isConnected = prefs.getBool(AppConstants.prefIsConnected) ?? false;
      if (isConnected) {
        // If the app was connected before, reconnect
        final lastConnected = prefs.getString(AppConstants.prefLastConnected);
        if (lastConnected != null && _selectedServer != null) {
          _connectionStatus = _connectionStatus.copyWith(
            state: VpnConnectionState.connected,
            connectedSince: DateTime.parse(lastConnected),
            currentServer: _selectedServer,
          );
          _connectionStatusController.add(_connectionStatus);
          _startConnectionTimer();
        }
      }
      
      // Load data usage
      final totalDataUsed = prefs.getDouble(AppConstants.prefTotalDataUsed);
      final uploadData = prefs.getDouble(AppConstants.prefUploadData);
      final downloadData = prefs.getDouble(AppConstants.prefDownloadData);
      
      if (totalDataUsed != null && uploadData != null && downloadData != null) {
        _connectionStats = _connectionStats.copyWith(
          totalDataUsed: totalDataUsed,
          totalUpload: uploadData,
          totalDownload: downloadData,
        );
        _connectionStatsController.add(_connectionStats);
      }
    } catch (e) {
      debugPrint('Error loading saved data: $e');
    }
  }

  /// Save data to SharedPreferences.
  Future<void> _saveData() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      
      // Save connection status
      await prefs.setBool(AppConstants.prefIsConnected, _connectionStatus.isConnected);
      if (_connectionStatus.connectedSince != null) {
        await prefs.setString(AppConstants.prefLastConnected, _connectionStatus.connectedSince!.toIso8601String());
      }
      
      // Save data usage
      await prefs.setDouble(AppConstants.prefTotalDataUsed, _connectionStats.totalDataUsed);
      await prefs.setDouble(AppConstants.prefUploadData, _connectionStats.totalUpload);
      await prefs.setDouble(AppConstants.prefDownloadData, _connectionStats.totalDownload);
    } catch (e) {
      debugPrint('Error saving data: $e');
    }
  }

  /// Start the connection timer to update stats.
  void _startConnectionTimer() {
    _connectionTimer?.cancel();
    _connectionTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!_connectionStatus.isConnected) {
        timer.cancel();
        return;
      }
      
      // Generate random upload and download speeds
      final uploadSpeed = _random.nextDouble() * 3 * 1024 * 1024; // 0-3 MB/s
      final downloadSpeed = _random.nextDouble() * 6 * 1024 * 1024; // 0-6 MB/s
      
      // Update total data
      final newTotalUpload = _connectionStats.totalUpload + uploadSpeed;
      final newTotalDownload = _connectionStats.totalDownload + downloadSpeed;
      final newTotalDataUsed = _connectionStats.totalDataUsed + uploadSpeed + downloadSpeed;
      
      // Update daily usage
      final today = DateTime.now();
      final dayKey = today.weekday == 1 ? 'Mon' : 
                    today.weekday == 2 ? 'Tue' : 
                    today.weekday == 3 ? 'Wed' : 
                    today.weekday == 4 ? 'Thu' : 
                    today.weekday == 5 ? 'Fri' : 
                    today.weekday == 6 ? 'Sat' : 'Sun';
      
      final dailyUsage = Map<String, double>.from(_connectionStats.dailyUsage);
      dailyUsage[dayKey] = (dailyUsage[dayKey] ?? 0) + uploadSpeed + downloadSpeed;
      
      // Update connection stats
      _connectionStats = _connectionStats.copyWith(
        uploadSpeed: uploadSpeed,
        downloadSpeed: downloadSpeed,
        totalUpload: newTotalUpload,
        totalDownload: newTotalDownload,
        totalDataUsed: newTotalDataUsed,
        dailyUsage: dailyUsage,
      );
      
      _connectionStatsController.add(_connectionStats);
    });
  }

  /// Dispose of resources.
  void dispose() {
    _connectionTimer?.cancel();
    _connectionStatusController.close();
    _connectionStatsController.close();
    _vpnStageController.close();
  }
}

/// Enum representing the different stages of a VPN connection.
enum VpnStage {
  initializing,
  initialized,
  connecting,
  connected,
  disconnecting,
  disconnected,
  error,
}

/// Extension to add helper methods to the VpnStage enum.
extension VpnStageExtension on VpnStage {
  String get name {
    switch (this) {
      case VpnStage.initializing:
        return 'Initializing';
      case VpnStage.initialized:
        return 'Initialized';
      case VpnStage.connecting:
        return 'Connecting';
      case VpnStage.connected:
        return 'Connected';
      case VpnStage.disconnecting:
        return 'Disconnecting';
      case VpnStage.disconnected:
        return 'Disconnected';
      case VpnStage.error:
        return 'Error';
    }
  }
}
