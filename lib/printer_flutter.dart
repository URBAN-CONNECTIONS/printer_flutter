import 'dart:typed_data';

import 'printer_flutter_platform_interface.dart';

/// Strategy for printing multi-page PDFs to continuous thermal rolls.
enum PdfPrintStrategy {
  /// Unifies all PDF pages into a single continuous vertical bitmap.
  /// Seamless roll printing without gaps between pages.
  unifiedRoll,

  /// Renders and prints each PDF page sequentially.
  /// Reduces peak memory usage for very long documents.
  pageByPage,
}

/// Options for printing PDF documents to a thermal printer.
class PdfPrintOptions {
  /// The target width of the thermal paper in millimeters. (e.g., 72.0, 58.0, 80.0)
  final double paperWidthMm;

  /// The printer DPI (Dots Per Inch). Usually 203 (8 dots/mm) or 300 (12 dots/mm).
  final int dpi;

  /// The strategy to use when printing multi-page documents.
  final PdfPrintStrategy strategy;

  /// Whether to apply dithering (e.g., Floyd-Steinberg) to preserve photographs/gradients in 1-bit monochrome.
  final bool enableDithering;

  /// Whether to trim empty white space from the bottom of the rendered PDF pages.
  final bool trimWhitespace;

  const PdfPrintOptions({
    this.paperWidthMm = 72.0,
    this.dpi = 203,
    this.strategy = PdfPrintStrategy.pageByPage,
    this.enableDithering = true,
    this.trimWhitespace = false,
  });

  Map<String, dynamic> toMap() {
    return {
      'paperWidthMm': paperWidthMm,
      'dpi': dpi,
      'strategy': strategy.name,
      'enableDithering': enableDithering,
      'trimWhitespace': trimWhitespace,
    };
  }
}

class PrinterFlutter {
  Future<String?> getPlatformVersion() {
    return PrinterFlutterPlatform.instance.getPlatformVersion();
  }

  /// Requests Bluetooth and location permissions on Android.
  Future<bool> requestPermissions() {
    return PrinterFlutterPlatform.instance.requestPermissions();
  }

  Future<String?> openPort(String macAddress) {
    return PrinterFlutterPlatform.instance.openPort(macAddress);
  }

  Future<String?> sendTspl(String command) {
    return PrinterFlutterPlatform.instance.sendTspl(command);
  }

  Future<String?> sendRawBytes(Uint8List bytes) {
    return PrinterFlutterPlatform.instance.sendRawBytes(bytes);
  }

  Future<String?> closePort() {
    return PrinterFlutterPlatform.instance.closePort();
  }

  /// Renders and prints a PDF file using the specified configuration.
  Future<String?> printPdf({
    required String filePath,
    PdfPrintOptions options = const PdfPrintOptions(),
    int copies = 1,
  }) {
    return PrinterFlutterPlatform.instance.printPdf(filePath, options, copies: copies);
  }
}
