import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_theme.dart';
import '../../../core/utils/format_utils.dart';
import '../../../shared/providers/vpn_provider.dart';
import '../../../shared/widgets/custom_bottom_navigation_bar.dart';
import '../../../shared/widgets/data_usage_card.dart';

class StatisticsScreen extends StatefulWidget {
  const StatisticsScreen({super.key});

  @override
  State<StatisticsScreen> createState() => _StatisticsScreenState();
}

class _StatisticsScreenState extends State<StatisticsScreen> {
  String _selectedTimeRange = 'Last 7 Days';
  final List<String> _timeRanges = ['Last 7 Days', 'Last 30 Days', 'Last 3 Months'];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.backgroundColor,
      appBar: AppBar(
        title: const Text('Statistics'),
        backgroundColor: AppTheme.backgroundColor,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.more_vert),
            onPressed: () {
              // TODO: Implement more options
            },
          ),
        ],
      ),
      body: Consumer<VpnProvider>(
        builder: (context, vpnProvider, _) {
          final connectionStats = vpnProvider.connectionStats;

          return Column(
            children: [
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // Total data usage card
                      Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: AppTheme.cardColor,
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              '${(connectionStats.totalDataUsed / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB',
                              style: AppTheme.headingLarge,
                            ),
                            const SizedBox(height: 4),
                            Text(
                              'Total Data Used',
                              style: AppTheme.bodyMedium.copyWith(
                                color: AppTheme.textSecondaryColor,
                              ),
                            ),
                            const SizedBox(height: 16),

                            // Progress bar
                            ClipRRect(
                              borderRadius: BorderRadius.circular(4),
                              child: LinearProgressIndicator(
                                value: connectionStats.totalDataUsed / (100 * 1024 * 1024 * 1024),
                                backgroundColor: AppTheme.dividerColor,
                                valueColor: const AlwaysStoppedAnimation<Color>(
                                  AppTheme.primaryColor,
                                ),
                                minHeight: 8,
                              ),
                            ),
                            const SizedBox(height: 8),

                            // Min and max labels
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Text(
                                  '0 GB',
                                  style: AppTheme.bodySmall,
                                ),
                                Text(
                                  '100 GB',
                                  style: AppTheme.bodySmall,
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 24),

                      // Daily usage section
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            'Daily Usage',
                            style: AppTheme.headingSmall,
                          ),
                          DropdownButton<String>(
                            value: _selectedTimeRange,
                            icon: const Icon(
                              Icons.keyboard_arrow_down,
                              color: AppTheme.primaryColor,
                            ),
                            underline: const SizedBox(),
                            style: AppTheme.labelMedium.copyWith(
                              color: AppTheme.primaryColor,
                            ),
                            dropdownColor: AppTheme.cardColor,
                            onChanged: (String? newValue) {
                              if (newValue != null) {
                                setState(() {
                                  _selectedTimeRange = newValue;
                                });
                              }
                            },
                            items: _timeRanges.map<DropdownMenuItem<String>>(
                              (String value) {
                                return DropdownMenuItem<String>(
                                  value: value,
                                  child: Text(value),
                                );
                              },
                            ).toList(),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),

                      // Chart
                      Container(
                        height: 200,
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: AppTheme.cardColor,
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: _buildBarChart(connectionStats.dailyUsage),
                      ),
                      const SizedBox(height: 24),

                      // Connection details section
                      Text(
                        'Connection Details',
                        style: AppTheme.headingSmall,
                      ),
                      const SizedBox(height: 16),

                      // Upload and download cards
                      Row(
                        children: [
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
                            ),
                          ),
                          const SizedBox(width: 16),
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
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),

              // Bottom navigation bar
              const CustomBottomNavigationBar(currentIndex: 2),
            ],
          );
        },
      ),
    );
  }

  Widget _buildBarChart(Map<String, double> dailyUsage) {
    if (dailyUsage.isEmpty) {
      return Center(
        child: Text(
          'No data available',
          style: AppTheme.bodyMedium.copyWith(
            color: AppTheme.textSecondaryColor,
          ),
        ),
      );
    }

    final days = dailyUsage.keys.toList();
    final values = dailyUsage.values.toList();
    final maxValue = values.isEmpty ? 1.0 : values.reduce((a, b) => a > b ? a : b);

    if (days.isEmpty) {
      return Center(
        child: Text(
          'No data available',
          style: AppTheme.bodyMedium.copyWith(
            color: AppTheme.textSecondaryColor,
          ),
        ),
      );
    }

    return BarChart(
      BarChartData(
        alignment: BarChartAlignment.spaceAround,
        maxY: maxValue * 1.2,
        barTouchData: BarTouchData(
          enabled: true,
          touchTooltipData: BarTouchTooltipData(
            tooltipBgColor: AppTheme.backgroundColor.withAlpha(204), // 0.8 * 255 = 204
            tooltipPadding: const EdgeInsets.all(8),
            tooltipMargin: 8,
            getTooltipItem: (group, groupIndex, rod, rodIndex) {
              return BarTooltipItem(
                FormatUtils.formatDataSize(values[groupIndex]),
                AppTheme.bodySmall.copyWith(color: AppTheme.textColor),
              );
            },
          ),
        ),
        titlesData: FlTitlesData(
          show: true,
          bottomTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              reservedSize: 30,
              getTitlesWidget: (value, meta) {
                return SideTitleWidget(
                  axisSide: meta.axisSide,
                  space: 4,
                  child: Text(
                    days[value.toInt()],
                    style: AppTheme.bodySmall,
                  ),
                );
              },
            ),
          ),
          leftTitles: const AxisTitles(
            sideTitles: SideTitles(
              showTitles: false,
            ),
          ),
          topTitles: const AxisTitles(
            sideTitles: SideTitles(
              showTitles: false,
            ),
          ),
          rightTitles: const AxisTitles(
            sideTitles: SideTitles(
              showTitles: false,
            ),
          ),
        ),
        borderData: FlBorderData(
          show: false,
        ),
        barGroups: List.generate(
          days.length,
          (index) => BarChartGroupData(
            x: index,
            barRods: [
              BarChartRodData(
                toY: values[index] / (1024 * 1024 * 1024),
                color: AppTheme.primaryColor,
                width: 16,
                borderRadius: const BorderRadius.only(
                  topLeft: Radius.circular(4),
                  topRight: Radius.circular(4),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
