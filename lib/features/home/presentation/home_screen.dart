import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/services/vpn_stage.dart' as app_vpn_stage;
import '../../../core/theme/app_theme.dart';
import '../../../core/utils/format_utils.dart';
import '../../../shared/providers/vpn_provider.dart';
import '../../../shared/widgets/connection_button.dart';
import '../../../shared/widgets/custom_bottom_navigation_bar.dart';
import '../../../shared/widgets/data_usage_card.dart';
import '../../../shared/widgets/server_list_item.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  // Helper method to get color based on VPN stage
  Color _getStageColor(app_vpn_stage.VpnStage stage) {
    return switch (stage) {
      app_vpn_stage.VpnStage.connected => AppTheme.successColor,
      app_vpn_stage.VpnStage.connecting || app_vpn_stage.VpnStage.initializing => AppTheme.warningColor,
      app_vpn_stage.VpnStage.disconnected || app_vpn_stage.VpnStage.disconnecting => AppTheme.textSecondaryColor,
      app_vpn_stage.VpnStage.error => AppTheme.errorColor,
      app_vpn_stage.VpnStage.initialized => AppTheme.primaryColor,
    };
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.backgroundColor,
      body: SafeArea(
        child: Consumer<VpnProvider>(
          builder: (context, vpnProvider, _) {
            final connectionStatus = vpnProvider.connectionStatus;
            final connectionStats = vpnProvider.connectionStats;
            final selectedServer = vpnProvider.selectedServer;
            final quickConnectServers = vpnProvider.quickConnectServers;

            return Column(
              children: [
                Expanded(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.center,
                      children: [
                        const SizedBox(height: 40),

                        // Connection button
                        ConnectionButton(
                          size: 120,
                          iconSize: 40,
                        ),
                        const SizedBox(height: 16),

                        // Connection status text
                        Text(
                          connectionStatus.isConnected
                              ? 'Connected'
                              : 'Not Connected',
                          style: AppTheme.headingMedium,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          connectionStatus.isConnected
                              ? 'Tap to disconnect'
                              : 'Tap to connect',
                          style: AppTheme.bodyMedium.copyWith(
                            color: AppTheme.textSecondaryColor,
                          ),
                        ),
                        const SizedBox(height: 4),
                        // VPN Stage
                        Text(
                          vpnProvider.vpnStage.name,
                          style: AppTheme.labelSmall.copyWith(
                            color: _getStageColor(vpnProvider.vpnStage),
                          ),
                        ),
                        const SizedBox(height: 24),

                        // Selected server
                        if (selectedServer != null) ...[
                          ServerListItem(
                            server: selectedServer,
                            showServersCount: false,
                          ),
                          const SizedBox(height: 24),
                        ],

                        // Data usage cards
                        Row(
                          children: [
                            Expanded(
                              child: DataUsageCard(
                                title: 'Download',
                                icon: Icons.arrow_downward,
                                iconColor: Colors.blue,
                                dataValue: connectionStats.totalDownload,
                                dataSpeed: FormatUtils.formatDataSize(
                                  connectionStats.downloadSpeed,
                                  includePerSecond: true,
                                ),
                                showTotal: false,
                              ),
                            ),
                            const SizedBox(width: 16),
                            Expanded(
                              child: DataUsageCard(
                                title: 'Upload',
                                icon: Icons.arrow_upward,
                                iconColor: Colors.green,
                                dataValue: connectionStats.totalUpload,
                                dataSpeed: FormatUtils.formatDataSize(
                                  connectionStats.uploadSpeed,
                                  includePerSecond: true,
                                ),
                                showTotal: false,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 24),

                        // Quick connect section
                        if (quickConnectServers.isEmpty) ...[
                          Center(
                            child: Column(
                              children: [
                                const Icon(
                                  Icons.vpn_lock,
                                  size: 48,
                                  color: AppTheme.textSecondaryColor,
                                ),
                                const SizedBox(height: 16),
                                Text(
                                  'No servers available',
                                  style: AppTheme.headingSmall,
                                ),
                                const SizedBox(height: 8),
                                Text(
                                  'Import a server from the Locations page',
                                  style: AppTheme.bodyMedium.copyWith(
                                    color: AppTheme.textSecondaryColor,
                                  ),
                                ),
                                const SizedBox(height: 16),
                                ElevatedButton(
                                  onPressed: () {
                                    context.go('/locations');
                                  },
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: AppTheme.primaryColor,
                                    foregroundColor: Colors.white,
                                    shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(8),
                                    ),
                                  ),
                                  child: const Text('Go to Locations'),
                                ),
                              ],
                            ),
                          ),
                        ] else ...[
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Quick Connect',
                                style: AppTheme.headingSmall,
                              ),
                              const SizedBox(height: 16),
                              ...quickConnectServers.map((server) => Padding(
                                padding: const EdgeInsets.only(bottom: 12),
                                child: ServerListItem(
                                  server: server,
                                  showChevron: false,
                                  onTap: () async {
                                    await vpnProvider.selectServer(server.id);
                                    if (!connectionStatus.isConnected) {
                                      vpnProvider.connect();
                                    }
                                  },
                                ),
                              )),
                            ],
                          ),
                        ],
                      ],
                    ),
                  ),
                ),

                // Bottom navigation bar
                const CustomBottomNavigationBar(currentIndex: 0),
              ],
            );
          },
        ),
      ),
    );
  }
}
