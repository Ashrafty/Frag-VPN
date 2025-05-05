import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/constants/app_constants.dart';
import '../../../core/localization/app_localizations.dart';
import '../../../core/theme/app_theme.dart';
import '../../../shared/providers/language_provider.dart';
import '../../../shared/widgets/custom_bottom_navigation_bar.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context);
    final languageProvider = Provider.of<LanguageProvider>(context);

    // Get the current language name
    String languageName;
    switch (languageProvider.locale.languageCode) {
      case 'es':
        languageName = 'Español';
        break;
      case 'fr':
        languageName = 'Français';
        break;
      case 'de':
        languageName = 'Deutsch';
        break;
      case 'ar':
        languageName = 'العربية';
        break;
      case 'zh':
        languageName = '中文';
        break;
      case 'ru':
        languageName = 'Русский';
        break;
      case 'fa':
        languageName = 'فارسی';
        break;
      default:
        languageName = 'English';
    }

    return Scaffold(
      backgroundColor: AppTheme.backgroundColor,
      appBar: AppBar(
        title: Text(localizations.translate('settings')),
        backgroundColor: AppTheme.backgroundColor,
        elevation: 0,
      ),
      body: Column(
        children: [
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Settings section
                  Text(
                    localizations.translate('settings'),
                    style: AppTheme.headingSmall,
                  ),
                  const SizedBox(height: 16),

                  // Settings items
                  _buildSettingsItem(
                    icon: Icons.language,
                    title: localizations.translate('language'),
                    subtitle: languageName,
                    onTap: () {
                      _showLanguageDialog(context);
                    },
                  ),
                  _buildSettingsItem(
                    icon: Icons.code,
                    title: localizations.translate('github_repository'),
                    subtitle: localizations.translate('view_source_code'),
                    onTap: () {
                      _launchGitHubRepo();
                    },
                  ),
                  _buildSettingsItem(
                    icon: Icons.info_outline,
                    title: localizations.translate('about'),
                    subtitle: '${localizations.translate('version')} ${AppConstants.appVersion}',
                    onTap: () {
                      _showAboutDialog(context);
                    },
                  ),
                ],
              ),
            ),
          ),

          // Bottom navigation bar
          const CustomBottomNavigationBar(currentIndex: 3),
        ],
      ),
    );
  }

  Widget _buildSettingsItem({
    required IconData icon,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: AppTheme.cardColor,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(
                icon,
                color: AppTheme.primaryColor,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: AppTheme.labelLarge,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    subtitle,
                    style: AppTheme.bodySmall,
                  ),
                ],
              ),
            ),
            const Icon(
              Icons.chevron_right,
              color: AppTheme.textSecondaryColor,
            ),
          ],
        ),
      ),
    );
  }

  void _showLanguageDialog(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppTheme.backgroundColor,
        title: Text(
          localizations.translate('select_language'),
          style: AppTheme.headingSmall,
        ),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              _buildLanguageOption(context, 'English', 'en'),
              _buildLanguageOption(context, 'Español', 'es'),
              _buildLanguageOption(context, 'Français', 'fr'),
              _buildLanguageOption(context, 'Deutsch', 'de'),
              _buildLanguageOption(context, 'العربية', 'ar'),
              _buildLanguageOption(context, '中文', 'zh'),
              _buildLanguageOption(context, 'Русский', 'ru'),
              _buildLanguageOption(context, 'فارسی', 'fa'),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(
              localizations.translate('cancel'),
              style: AppTheme.labelMedium.copyWith(
                color: AppTheme.primaryColor,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLanguageOption(BuildContext context, String language, String code) {
    final languageProvider = Provider.of<LanguageProvider>(context, listen: false);
    final isSelected = languageProvider.locale.languageCode == code;

    return InkWell(
      onTap: () async {
        await languageProvider.changeLanguage(code);
        if (context.mounted) {
          Navigator.pop(context);

          // We need to wait a moment for the locale to change before showing the snackbar
          Future.delayed(const Duration(milliseconds: 300), () {
            if (context.mounted) {
              final localizations = AppLocalizations.of(context);
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text('${localizations.translate("language")} ${localizations.translate("changed_to")} $language'),
                  backgroundColor: AppTheme.primaryColor,
                ),
              );
            }
          });
        }
      },
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Row(
          children: [
            Text(
              language,
              style: AppTheme.bodyMedium,
            ),
            const Spacer(),
            if (isSelected)
              const Icon(
                Icons.check,
                color: AppTheme.primaryColor,
              ),
          ],
        ),
      ),
    );
  }

  void _launchGitHubRepo() async {
    final Uri url = Uri.parse('https://github.com/yourusername/frag_vpn');
    if (!await launchUrl(url)) {
      throw Exception('Could not launch $url');
    }
  }

  void _showAboutDialog(BuildContext context) {
    final localizations = AppLocalizations.of(context);

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppTheme.backgroundColor,
        title: Text(
          '${localizations.translate('about')} ${AppConstants.appName}',
          style: AppTheme.headingSmall,
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircleAvatar(
              radius: 40,
              backgroundColor: AppTheme.primaryColor,
              child: Icon(
                Icons.shield,
                size: 40,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 16),
            Text(
              AppConstants.appName,
              style: AppTheme.headingMedium,
            ),
            Text(
              '${localizations.translate('version')} ${AppConstants.appVersion}',
              style: AppTheme.bodyMedium,
            ),
            const SizedBox(height: 16),
            Text(
              localizations.translate('app_description'),
              style: AppTheme.bodyMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            Text(
              localizations.translate('copyright'),
              style: AppTheme.bodySmall.copyWith(
                color: AppTheme.textSecondaryColor,
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(
              localizations.translate('close'),
              style: AppTheme.labelMedium.copyWith(
                color: AppTheme.primaryColor,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
