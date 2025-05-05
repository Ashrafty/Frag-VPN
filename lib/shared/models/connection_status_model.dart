import 'server_model.dart';

enum VpnConnectionState {
  disconnected,
  connecting,
  connected,
  disconnecting,
  error
}

class ConnectionStatusModel {
  final VpnConnectionState state;
  final String? errorMessage;
  final DateTime? connectedSince;
  final ServerModel? currentServer;

  ConnectionStatusModel({
    this.state = VpnConnectionState.disconnected,
    this.errorMessage,
    this.connectedSince,
    this.currentServer,
  });

  bool get isConnected => state == VpnConnectionState.connected;
  bool get isConnecting => state == VpnConnectionState.connecting;
  bool get isDisconnected => state == VpnConnectionState.disconnected;
  bool get isDisconnecting => state == VpnConnectionState.disconnecting;
  bool get hasError => state == VpnConnectionState.error;

  ConnectionStatusModel copyWith({
    VpnConnectionState? state,
    String? errorMessage,
    DateTime? connectedSince,
    ServerModel? currentServer,
  }) {
    return ConnectionStatusModel(
      state: state ?? this.state,
      errorMessage: errorMessage ?? this.errorMessage,
      connectedSince: connectedSince ?? this.connectedSince,
      currentServer: currentServer ?? this.currentServer,
    );
  }

  @override
  String toString() {
    return 'ConnectionStatusModel(state: $state, errorMessage: $errorMessage, connectedSince: $connectedSince, currentServer: $currentServer)';
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;

    return other is ConnectionStatusModel &&
      other.state == state &&
      other.errorMessage == errorMessage &&
      other.connectedSince == connectedSince &&
      other.currentServer == currentServer;
  }

  @override
  int get hashCode {
    return state.hashCode ^
      errorMessage.hashCode ^
      connectedSince.hashCode ^
      currentServer.hashCode;
  }
}
