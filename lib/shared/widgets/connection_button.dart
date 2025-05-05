import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/theme/app_theme.dart';
import '../models/connection_status_model.dart';
import '../providers/vpn_provider.dart';

class ConnectionButton extends StatelessWidget {
  final double size;
  final double iconSize;
  final VoidCallback? onPressed;

  const ConnectionButton({
    super.key,
    this.size = 120,
    this.iconSize = 40,
    this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return Consumer<VpnProvider>(
      builder: (context, vpnProvider, _) {
        final connectionStatus = vpnProvider.connectionStatus;
        final isConnected = connectionStatus.isConnected;
        final isConnecting = connectionStatus.isConnecting;
        final isDisconnecting = connectionStatus.isDisconnecting;

        // Determine button color based on connection state
        Color buttonColor = AppTheme.primaryColor;
        if (isConnected) {
          buttonColor = AppTheme.primaryColor;
        } else if (isConnecting || isDisconnecting) {
          buttonColor = AppTheme.warningColor;
        } else {
          buttonColor = AppTheme.primaryColor.withAlpha(128); // 0.5 * 255 = 128
        }

        return GestureDetector(
          onTap: () {
            if (isConnecting || isDisconnecting) {
              return; // Don't allow interaction during transition states
            }

            if (onPressed != null) {
              onPressed!();
            } else {
              if (isConnected) {
                vpnProvider.disconnect();
              } else {
                vpnProvider.connect();
              }
            }
          },
          child: Container(
            width: size,
            height: size,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(
                color: buttonColor,
                width: 2,
              ),
            ),
            child: Center(
              child: AnimatedSwitcher(
                duration: const Duration(milliseconds: 300),
                child: _buildButtonContent(
                  connectionStatus.state,
                  buttonColor,
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildButtonContent(VpnConnectionState state, Color color) {
    switch (state) {
      case VpnConnectionState.connected:
        return Icon(
          Icons.power_settings_new,
          size: iconSize,
          color: color,
          key: const ValueKey('connected'),
        );
      case VpnConnectionState.connecting:
        return SizedBox(
          width: iconSize,
          height: iconSize,
          key: const ValueKey('connecting'),
          child: CircularProgressIndicator(
            color: color,
            strokeWidth: 3,
          ),
        );
      case VpnConnectionState.disconnecting:
        return SizedBox(
          width: iconSize,
          height: iconSize,
          key: const ValueKey('disconnecting'),
          child: CircularProgressIndicator(
            color: color,
            strokeWidth: 3,
          ),
        );
      case VpnConnectionState.error:
        return Icon(
          Icons.error_outline,
          size: iconSize,
          color: AppTheme.errorColor,
          key: const ValueKey('error'),
        );
      case VpnConnectionState.disconnected:
        return Icon(
          Icons.power_settings_new,
          size: iconSize,
          color: color,
          key: const ValueKey('disconnected'),
        );
    }
  }
}
