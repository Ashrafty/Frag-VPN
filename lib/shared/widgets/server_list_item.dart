import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/theme/app_theme.dart';
import '../../core/utils/format_utils.dart';
import '../models/server_model.dart';
import '../providers/vpn_provider.dart';

class ServerListItem extends StatelessWidget {
  final ServerModel server;
  final bool showChevron;
  final bool showServersCount;
  final Function()? onTap;

  const ServerListItem({
    super.key,
    required this.server,
    this.showChevron = true,
    this.showServersCount = true,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: () async {
        if (onTap != null) {
          await onTap!();
        } else {
          final vpnProvider = Provider.of<VpnProvider>(context, listen: false);
          await vpnProvider.selectServer(server.id);
        }
      },
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: AppTheme.cardColor,
          borderRadius: BorderRadius.circular(16),
          border: server.isSelected
              ? Border.all(color: AppTheme.primaryColor, width: 1)
              : null,
        ),
        child: Row(
          children: [
            // Location icon
            Container(
              width: 40,
              height: 40,
              decoration: const BoxDecoration(
                color: AppTheme.backgroundColor,
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.location_on,
                color: AppTheme.primaryColor,
                size: 20,
              ),
            ),
            const SizedBox(width: 16),

            // Server info
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      if (server.isPremium) ...[
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
                        const SizedBox(width: 8),
                      ],
                      Expanded(
                        child: Text(
                          server.name,
                          style: AppTheme.labelLarge,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                  if (showServersCount) ...[
                    const SizedBox(height: 4),
                    Text(
                      '${server.serversAvailable} Servers Available',
                      style: AppTheme.bodySmall,
                    ),
                  ],
                ],
              ),
            ),

            // Ping time
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: _getPingColor(server.pingTime),
                borderRadius: BorderRadius.circular(4),
              ),
              child: Text(
                FormatUtils.formatPingTime(server.pingTime),
                style: AppTheme.labelSmall.copyWith(
                  color: Colors.white,
                ),
              ),
            ),

            // Chevron icon
            if (showChevron) ...[
              const SizedBox(width: 8),
              const Icon(
                Icons.chevron_right,
                color: AppTheme.textSecondaryColor,
                size: 20,
              ),
            ],
          ],
        ),
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
}
