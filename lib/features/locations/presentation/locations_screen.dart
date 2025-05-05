import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_theme.dart';
import '../../../shared/providers/vpn_provider.dart';
import '../../../shared/widgets/custom_bottom_navigation_bar.dart';
import '../../../shared/widgets/server_list_item.dart';
import 'import_server_dialog.dart';

class LocationsScreen extends StatelessWidget {
  const LocationsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.backgroundColor,
      appBar: AppBar(
        title: const Text('Locations'),
        backgroundColor: AppTheme.backgroundColor,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.search),
            onPressed: () {
              // TODO: Implement search functionality
            },
          ),
          IconButton(
            icon: const Icon(Icons.add),
            tooltip: 'Import Server',
            onPressed: () => _showImportDialog(context),
          ),
        ],
      ),
      body: Consumer<VpnProvider>(
        builder: (context, vpnProvider, _) {
          final servers = vpnProvider.servers;
          final premiumServers = servers.where((server) => server.isPremium).toList();
          final regularServers = servers.where((server) => !server.isPremium).toList();

          return Column(
            children: [
              Expanded(
                child: servers.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(
                              Icons.vpn_lock,
                              size: 64,
                              color: AppTheme.textSecondaryColor,
                            ),
                            const SizedBox(height: 24),
                            Text(
                              'No VPN Servers',
                              style: AppTheme.headingMedium,
                            ),
                            const SizedBox(height: 16),
                            Text(
                              'Import an Outline VPN configuration to get started',
                              textAlign: TextAlign.center,
                              style: AppTheme.bodyMedium.copyWith(
                                color: AppTheme.textSecondaryColor,
                              ),
                            ),
                            const SizedBox(height: 24),
                            ElevatedButton.icon(
                              onPressed: () => _showImportDialog(context),
                              style: ElevatedButton.styleFrom(
                                backgroundColor: AppTheme.primaryColor,
                                foregroundColor: Colors.white,
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 24,
                                  vertical: 12,
                                ),
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(8),
                                ),
                              ),
                              icon: const Icon(Icons.add),
                              label: const Text('Import Server'),
                            ),
                          ],
                        ),
                      )
                    : SingleChildScrollView(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            if (premiumServers.isNotEmpty) ...[
                              // Featured Locations
                              Text(
                                'Featured Locations',
                                style: AppTheme.labelLarge.copyWith(
                                  color: AppTheme.textSecondaryColor,
                                ),
                              ),
                              const SizedBox(height: 16),

                              // Premium servers grid
                              GridView.builder(
                                shrinkWrap: true,
                                physics: const NeverScrollableScrollPhysics(),
                                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                                  crossAxisCount: 2,
                                  crossAxisSpacing: 16,
                                  mainAxisSpacing: 16,
                                  childAspectRatio: 1.2,
                                ),
                                itemCount: premiumServers.length,
                                itemBuilder: (context, index) {
                                  final server = premiumServers[index];
                                  return _buildFeaturedServerCard(server);
                                },
                              ),
                              const SizedBox(height: 24),
                            ],

                            // All Locations
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Text(
                                  'All Locations',
                                  style: AppTheme.labelLarge.copyWith(
                                    color: AppTheme.textSecondaryColor,
                                  ),
                                ),
                                if (servers.length > 1)
                                  TextButton.icon(
                                    onPressed: () {
                                      // TODO: Implement filter functionality
                                    },
                                    icon: const Icon(
                                      Icons.filter_list,
                                      color: AppTheme.primaryColor,
                                      size: 18,
                                    ),
                                    label: Text(
                                      'Filter',
                                      style: AppTheme.labelMedium.copyWith(
                                        color: AppTheme.primaryColor,
                                      ),
                                    ),
                                    style: TextButton.styleFrom(
                                      padding: EdgeInsets.zero,
                                      minimumSize: Size.zero,
                                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                    ),
                                  ),
                              ],
                            ),
                            const SizedBox(height: 16),

                            // Regular servers list
                            ...regularServers.map((server) {
                              return Column(
                                children: [
                                  ServerListItem(server: server),
                                  const Divider(color: AppTheme.dividerColor),
                                ],
                              );
                            }),
                          ],
                        ),
                      ),
              ),

              // Bottom navigation bar
              const CustomBottomNavigationBar(currentIndex: 1),
            ],
          );
        },
      ),
    );
  }

  Widget _buildFeaturedServerCard(server) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppTheme.cardColor,
        borderRadius: BorderRadius.circular(16),
        border: server.isSelected
            ? Border.all(color: AppTheme.primaryColor, width: 1)
            : null,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(
                Icons.star,
                color: Colors.amber,
                size: 16,
              ),
              const SizedBox(width: 4),
              Text(
                'Premium',
                style: AppTheme.labelSmall.copyWith(
                  color: Colors.amber,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            server.name,
            style: AppTheme.labelLarge,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 4),
          Text(
            '${server.serversAvailable} Servers',
            style: AppTheme.bodySmall,
          ),
          const Spacer(),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: _getPingColor(server.pingTime),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  '${server.pingTime}ms',
                  style: AppTheme.labelSmall.copyWith(
                    color: Colors.white,
                  ),
                ),
              ),
              const Icon(
                Icons.chevron_right,
                color: AppTheme.textSecondaryColor,
                size: 20,
              ),
            ],
          ),
        ],
      ),
    );
  }

  Color _getPingColor(int pingTime) {
    if (pingTime < 100) {
      return AppTheme.successColor;
    } else if (pingTime < 150) {
      return AppTheme.secondaryColor;
    } else if (pingTime < 200) {
      return AppTheme.warningColor;
    } else {
      return AppTheme.errorColor;
    }
  }

  Future<void> _showImportDialog(BuildContext context) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => const ImportServerDialog(),
    );

    if (result == true) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Server imported successfully'),
            backgroundColor: AppTheme.successColor,
          ),
        );
      }
    }
  }
}
