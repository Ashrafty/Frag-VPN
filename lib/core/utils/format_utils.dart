import 'package:intl/intl.dart';

class FormatUtils {
  // Private constructor to prevent instantiation
  FormatUtils._();

  /// Format data size to readable format (e.g., 1.5 MB/s, 2.3 GB)
  static String formatDataSize(double bytes, {bool includePerSecond = false}) {
    if (bytes < 1024) {
      return '${bytes.toStringAsFixed(2)} B${includePerSecond ? '/s' : ''}';
    } else if (bytes < 1024 * 1024) {
      return '${(bytes / 1024).toStringAsFixed(2)} KB${includePerSecond ? '/s' : ''}';
    } else if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(2)} MB${includePerSecond ? '/s' : ''}';
    } else {
      return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB${includePerSecond ? '/s' : ''}';
    }
  }

  /// Format ping time to readable format (e.g., 85ms)
  static String formatPingTime(int milliseconds) {
    return '${milliseconds}ms';
  }

  /// Format date to readable format (e.g., Jan 1, 2023)
  static String formatDate(DateTime date) {
    return DateFormat('MMM d, yyyy').format(date);
  }

  /// Format day of week (e.g., Mon, Tue)
  static String formatDayOfWeek(DateTime date) {
    return DateFormat('E').format(date);
  }
}
