import 'dart:math';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_theme.dart';
import '../../../shared/providers/vpn_provider.dart';

class QrScannerScreen extends StatefulWidget {
  const QrScannerScreen({super.key});

  @override
  State<QrScannerScreen> createState() => _QrScannerScreenState();
}

class _QrScannerScreenState extends State<QrScannerScreen> {
  final MobileScannerController _controller = MobileScannerController();
  bool _isProcessing = false;
  String? _errorMessage;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Scan QR Code'),
        backgroundColor: AppTheme.backgroundColor,
        elevation: 0,
        actions: [
          IconButton(
            icon: ValueListenableBuilder(
              valueListenable: _controller.torchState,
              builder: (context, state, child) {
                switch (state) {
                  case TorchState.off:
                    return const Icon(Icons.flash_off, color: Colors.grey);
                  case TorchState.on:
                    return const Icon(Icons.flash_on, color: Colors.yellow);
                }
              },
            ),
            onPressed: () => _controller.toggleTorch(),
          ),
          IconButton(
            icon: ValueListenableBuilder(
              valueListenable: _controller.cameraFacingState,
              builder: (context, state, child) {
                switch (state) {
                  case CameraFacing.front:
                    return const Icon(Icons.camera_front);
                  case CameraFacing.back:
                    return const Icon(Icons.camera_rear);
                }
              },
            ),
            onPressed: () => _controller.switchCamera(),
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: Stack(
              children: [
                MobileScanner(
                  controller: _controller,
                  onDetect: _onDetect,
                ),
                // Overlay
                Container(
                  decoration: BoxDecoration(
                    color: Colors.black.withAlpha(128),
                  ),
                  child: Center(
                    child: Container(
                      width: 250,
                      height: 250,
                      decoration: BoxDecoration(
                        border: Border.all(color: AppTheme.primaryColor, width: 2),
                        borderRadius: BorderRadius.circular(12),
                        color: Colors.transparent,
                      ),
                    ),
                  ),
                ),
                // Processing indicator
                if (_isProcessing)
                  Container(
                    color: Colors.black.withAlpha(179),
                    child: const Center(
                      child: CircularProgressIndicator(
                        color: AppTheme.primaryColor,
                      ),
                    ),
                  ),
              ],
            ),
          ),
          // Instructions
          Container(
            padding: const EdgeInsets.all(16),
            color: AppTheme.backgroundColor,
            child: Column(
              children: [
                if (_errorMessage != null) ...[
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: AppTheme.errorColor.withAlpha(25),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Row(
                      children: [
                        const Icon(
                          Icons.error_outline,
                          color: AppTheme.errorColor,
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            _errorMessage!,
                            style: AppTheme.bodySmall.copyWith(
                              color: AppTheme.errorColor,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                ],
                Text(
                  'Scan a QR code containing an Outline VPN configuration',
                  style: AppTheme.bodyMedium,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                Text(
                  'Position the QR code within the frame',
                  style: AppTheme.bodySmall.copyWith(
                    color: AppTheme.textSecondaryColor,
                  ),
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _onDetect(BarcodeCapture capture) async {
    if (_isProcessing) return;

    final List<Barcode> barcodes = capture.barcodes;
    if (barcodes.isEmpty) return;

    final Barcode barcode = barcodes.first;
    final String? code = barcode.rawValue;

    if (code == null) return;

    debugPrint('QR code detected: ${code.substring(0, min(20, code.length))}...');

    // Process the scanned code
    String processedCode = code.trim();

    // Clean up the code - remove whitespace
    processedCode = processedCode.replaceAll(RegExp(r'\s+'), '');

    // Check if it contains an Outline VPN configuration
    if (!processedCode.contains('ss://')) {
      setState(() {
        _errorMessage = 'Invalid QR code. Not an Outline VPN configuration.';
      });
      return;
    }

    // Extract the ss:// part if it's embedded in a larger string
    final ssIndex = processedCode.indexOf('ss://');
    if (ssIndex > 0) {
      processedCode = processedCode.substring(ssIndex);
      debugPrint('Extracted ss:// part: ${processedCode.substring(0, min(20, processedCode.length))}...');
    }

    setState(() {
      _isProcessing = true;
      _errorMessage = null;
    });

    try {
      final vpnProvider = Provider.of<VpnProvider>(context, listen: false);
      debugPrint('Attempting to import server from QR code');
      final success = await vpnProvider.importServer(processedCode);

      if (success) {
        if (mounted) {
          // Show success message and navigate back
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Server imported successfully'),
              backgroundColor: AppTheme.successColor,
            ),
          );
          context.pop(true); // Return true to indicate success
        }
      } else {
        setState(() {
          _isProcessing = false;
          _errorMessage = 'Failed to import server. Invalid configuration.';
        });
      }
    } catch (e) {
      debugPrint('Error importing server from QR code: $e');
      setState(() {
        _isProcessing = false;
        _errorMessage = 'Error importing server: $e';
      });
    }
  }
}
