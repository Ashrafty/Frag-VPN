import 'package:flutter/material.dart';

import '../../core/theme/app_theme.dart';
import '../../core/utils/format_utils.dart';

class DataUsageCard extends StatelessWidget {
  final String title;
  final IconData icon;
  final Color iconColor;
  final double dataValue;
  final String? dataSpeed;
  final bool showTotal;

  const DataUsageCard({
    super.key,
    required this.title,
    required this.icon,
    required this.iconColor,
    required this.dataValue,
    this.dataSpeed,
    this.showTotal = true,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.cardColor,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                icon,
                color: iconColor,
                size: 20,
              ),
              const SizedBox(width: 8),
              Text(
                title,
                style: AppTheme.labelMedium,
              ),
            ],
          ),
          const SizedBox(height: 8),
          if (dataSpeed != null) ...[
            Center(
              child: Text(
                dataSpeed!,
                style: AppTheme.headingMedium,
              ),
            ),
          ],
          if (showTotal) ...[
            const SizedBox(height: 4),
            Center(
              child: Text(
                'Total: ${FormatUtils.formatDataSize(dataValue)}',
                style: AppTheme.bodySmall,
              ),
            ),
          ],
        ],
      ),
    );
  }
}
