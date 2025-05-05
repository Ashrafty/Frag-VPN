import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../core/services/vpn_stage.dart' as app_vpn_stage;

import '../../core/services/outline_config_importer.dart';
import '../../core/services/outline_vpn_service.dart';
import '../models/connection_stats_model.dart';
import '../models/connection_status_model.dart';
import '../models/server_model.dart';

class VpnProvider with ChangeNotifier {
  // List of servers
  List<ServerModel> _servers = [];

  // VPN service
  final OutlineVpnService _vpnService = OutlineVpnService();

  // Subscriptions
  StreamSubscription<ConnectionStatusModel>? _connectionStatusSubscription;
  StreamSubscription<ConnectionStatsModel>? _connectionStatsSubscription;
  StreamSubscription<app_vpn_stage.VpnStage>? _vpnStageSubscription;

  // State
  ConnectionStatusModel _connectionStatus = ConnectionStatusModel(
    state: VpnConnectionState.disconnected,
    currentServer: null,
  );

  ConnectionStatsModel _connectionStats = ConnectionStatsModel();
  app_vpn_stage.VpnStage _vpnStage = app_vpn_stage.VpnStage.disconnected;
  bool _isInitialized = false;

  // Getters
  List<ServerModel> get servers => _servers;
  List<ServerModel> get quickConnectServers => _servers.take(_servers.length >= 2 ? 2 : _servers.length).toList();
  ConnectionStatusModel get connectionStatus => _connectionStatus;
  ConnectionStatsModel get connectionStats => _connectionStats;
  app_vpn_stage.VpnStage get vpnStage => _vpnStage;
  bool get isInitialized => _isInitialized;
  ServerModel? get selectedServer {
    if (_servers.isEmpty) return null;
    try {
      return _servers.firstWhere((server) => server.isSelected);
    } catch (e) {
      return _servers.first;
    }
  }

  // Constructor
  VpnProvider() {
    _initialize();
  }

  // Initialize the VPN service
  Future<void> _initialize() async {
    try {
      // Load saved servers
      await _loadServers();

      // Initialize VPN service
      await _vpnService.initialize();
      _isInitialized = true;

      // Subscribe to VPN service streams
      _connectionStatusSubscription = _vpnService.onConnectionStatusChanged.listen((status) {
        _connectionStatus = status;
        notifyListeners();
      });

      _connectionStatsSubscription = _vpnService.onConnectionStatsChanged.listen((stats) {
        _connectionStats = stats;
        notifyListeners();
      });

      _vpnStageSubscription = _vpnService.onVpnStageChanged.listen((stage) {
        _vpnStage = stage;
        notifyListeners();
      });

      // Get initial status and stats
      _connectionStatus = await _vpnService.getStatus();
      _connectionStats = await _vpnService.getStats();

      notifyListeners();
    } catch (e) {
      debugPrint('Error initializing VPN service: $e');
    }
  }

  // Load servers from SharedPreferences
  Future<void> _loadServers() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final serversJson = prefs.getStringList('servers');

      if (serversJson != null && serversJson.isNotEmpty) {
        _servers = serversJson.map((json) => _serverFromJson(json)).toList();

        // Check if there's a selected server
        final selectedServerId = prefs.getString('selectedServerId');
        if (selectedServerId != null) {
          selectServer(selectedServerId);
        } else if (_servers.isNotEmpty) {
          // Select the first server if none is selected
          selectServer(_servers.first.id);
        }
      }
    } catch (e) {
      debugPrint('Error loading servers: $e');
    }
  }

  // Save servers to SharedPreferences
  Future<void> _saveServers() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final serversJson = _servers.map((server) => _serverToJson(server)).toList();

      await prefs.setStringList('servers', serversJson);

      // Save selected server ID
      final selectedServerIndex = _servers.indexWhere((server) => server.isSelected);
      if (selectedServerIndex >= 0) {
        await prefs.setString('selectedServerId', _servers[selectedServerIndex].id);
      } else if (_servers.isNotEmpty) {
        await prefs.setString('selectedServerId', _servers.first.id);
      }
    } catch (e) {
      debugPrint('Error saving servers: $e');
    }
  }

  // Convert ServerModel to JSON string
  String _serverToJson(ServerModel server) {
    return json.encode({
      'id': server.id,
      'name': server.name,
      'country': server.country,
      'city': server.city,
      'pingTime': server.pingTime,
      'serversAvailable': server.serversAvailable,
      'isPremium': server.isPremium,
      'isSelected': server.isSelected,
      'outlineKey': server.outlineKey,
    });
  }

  // Convert JSON string to ServerModel
  ServerModel _serverFromJson(String jsonString) {
    final map = json.decode(jsonString) as Map<String, dynamic>;

    return ServerModel(
      id: map['id'] as String,
      name: map['name'] as String,
      country: map['country'] as String,
      city: map['city'] as String,
      pingTime: map['pingTime'] as int,
      serversAvailable: map['serversAvailable'] as int,
      isPremium: map['isPremium'] as bool,
      isSelected: map['isSelected'] as bool,
      outlineKey: map['outlineKey'] as String?,
    );
  }

  // Select a server
  Future<void> selectServer(String serverId) async {
    for (int i = 0; i < _servers.length; i++) {
      if (_servers[i].id == serverId) {
        _servers[i] = _servers[i].copyWith(isSelected: true);
      } else {
        _servers[i] = _servers[i].copyWith(isSelected: false);
      }
    }

    // Save the selection
    await _saveServers();

    notifyListeners();
  }

  // Add a new server
  Future<void> addServer(ServerModel server) async {
    // Check if server with the same ID already exists
    final existingIndex = _servers.indexWhere((s) => s.id == server.id);
    if (existingIndex >= 0) {
      // Update existing server
      _servers[existingIndex] = server;
    } else {
      // Add new server
      _servers.add(server);
    }

    // Save servers
    await _saveServers();

    notifyListeners();
  }

  // Import a server from an Outline VPN configuration
  Future<bool> importServer(String outlineKey) async {
    try {
      // Parse the Outline VPN configuration
      final server = OutlineConfigImporter.parseOutlineUrl(outlineKey);
      if (server == null) {
        return false;
      }

      // Add the server
      await addServer(server);
      return true;
    } catch (e) {
      debugPrint('Error importing server: $e');
      return false;
    }
  }

  // Connect to VPN
  Future<void> connect() async {
    if (!_isInitialized) {
      await _initialize();
    }

    if (_connectionStatus.isConnected || _connectionStatus.isConnecting) {
      return;
    }

    try {
      final server = selectedServer;
      if (server == null) {
        throw Exception('No server available. Please import a server first.');
      }

      await _vpnService.connect(server: server);
    } catch (e) {
      debugPrint('Error connecting to VPN: $e');
      rethrow;
    }
  }

  // Disconnect from VPN
  Future<void> disconnect() async {
    if (!_isInitialized) {
      return;
    }

    if (_connectionStatus.isDisconnected || _connectionStatus.isDisconnecting) {
      return;
    }

    try {
      await _vpnService.disconnect();
    } catch (e) {
      debugPrint('Error disconnecting from VPN: $e');
      rethrow;
    }
  }

  @override
  void dispose() {
    _connectionStatusSubscription?.cancel();
    _connectionStatsSubscription?.cancel();
    _vpnStageSubscription?.cancel();
    _vpnService.dispose();
    super.dispose();
  }
}
