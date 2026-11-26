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

  /// Buffers all rendered PDF page bitmaps into printer DRAM before printing continuously.
  /// Eliminates stuttering between pages and prevents out-of-memory errors on small RAM printers.
  dramBatch,
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

  /// Checks if Bluetooth is enabled and natively prompts to enable it on Android.
  Future<bool> ensureBluetoothIsOn() {
    return PrinterFlutterPlatform.instance.ensureBluetoothIsOn();
  }

  /// Requests Bluetooth and location permissions on Android.
  Future<bool> requestPermissions() {
    return PrinterFlutterPlatform.instance.requestPermissions();
  }

  /// Opens the Bluetooth connection to the printer at [macAddress].
  ///
  /// Gives up after [connectTimeout] and throws a [PrinterException] of type
  /// [PrinterErrorType.notConnected]. With [resetPrinterAfterFailure], when the
  /// previous job hung, was cancelled or failed, the printer is first reset with
  /// `<ESC>!R` (only for printers that support it).
  Future<String?> openPort(
    String macAddress, {
    String pin = "0000",
    String? fallbackPin = "1234",
    Duration connectTimeout = PrinterLimits.connectTimeout,
    bool resetPrinterAfterFailure = false,
  }) {
    return PrinterFlutterPlatform.instance.openPort(
      macAddress,
      pin: pin,
      fallbackPin: fallbackPin,
      connectTimeout: connectTimeout,
      resetPrinterAfterFailure: resetPrinterAfterFailure,
    );
  }

  /// Sends a TSPL [command]. With [closePort], waits up to [completionTimeout]
  /// for the printer to confirm it processed everything, then closes the port.
  ///
  /// With [macAddress] the call is atomic: it opens the connection to that
  /// printer (using [pin], [fallbackPin] and [connectTimeout] as `openPort`
  /// does; [pin] defaults to "0000" and, unset, [fallbackPin] means no second
  /// attempt), sends, confirms and closes it, all in one native call.
  /// [closePort] has no effect then, a connection left open by `openPort` is
  /// closed first, and atomic calls run first-in, first-out. Do not mix the
  /// atomic style with the three-step style (`openPort`, send, `closePort`) on
  /// the same printer.
  ///
  /// With [confirmCompletion] (default true) the call waits up to
  /// [completionTimeout] for the printer to confirm it processed everything it
  /// was sent; that is not a confirmation that it finished printing. Pass false
  /// to skip the wait. A [PrinterErrorType.timeout] after everything was sent
  /// means "not confirmed", not "it did not print": check the printer before
  /// printing again.
  ///
  /// With [probe], the printer is first asked to answer (TSPL `OUT`) and no
  /// byte of the job is sent before it does. It has 2 seconds, or
  /// [completionTimeout] when the last job sent to that printer used
  /// [confirmCompletion] false, since it may still be busy with it. A printer
  /// that does not answer in time fails the call with
  /// [PrinterErrorType.printerNotReady] and the connection is closed. Unset,
  /// the probe is on in an atomic call and off otherwise.
  Future<String?> sendTspl(
    String command, {
    bool closePort = false,
    Duration writeTimeout = PrinterLimits.writeTimeout,
    Duration completionTimeout = PrinterLimits.completionTimeout,
    String? macAddress,
    String? pin,
    String? fallbackPin,
    Duration? connectTimeout,
    bool confirmCompletion = true,
    bool? probe,
  }) {
    return PrinterFlutterPlatform.instance.sendTspl(
      command,
      closePort: closePort,
      writeTimeout: writeTimeout,
      completionTimeout: completionTimeout,
      macAddress: macAddress,
      pin: pin,
      fallbackPin: fallbackPin,
      connectTimeout: connectTimeout,
      confirmCompletion: confirmCompletion,
      probe: probe,
    );
  }

  /// Sends raw [bytes]. With [closePort], waits up to [completionTimeout] for
  /// the printer to confirm it processed everything, then closes the port.
  ///
  /// With [macAddress] the call is atomic: it opens the connection to that
  /// printer (using [pin], [fallbackPin] and [connectTimeout] as `openPort`
  /// does; [pin] defaults to "0000" and, unset, [fallbackPin] means no second
  /// attempt), sends, confirms and closes it, all in one native call.
  /// [closePort] has no effect then, a connection left open by `openPort` is
  /// closed first, and atomic calls run first-in, first-out. Do not mix the
  /// atomic style with the three-step style (`openPort`, send, `closePort`) on
  /// the same printer.
  ///
  /// With [confirmCompletion] (default true) the call waits up to
  /// [completionTimeout] for the printer to confirm it processed everything it
  /// was sent; that is not a confirmation that it finished printing. Pass false
  /// to skip the wait. A [PrinterErrorType.timeout] after everything was sent
  /// means "not confirmed", not "it did not print": check the printer before
  /// printing again.
  ///
  /// With [probe], the printer is first asked to answer (TSPL `OUT`) and no
  /// byte of the job is sent before it does. It has 2 seconds, or
  /// [completionTimeout] when the last job sent to that printer used
  /// [confirmCompletion] false, since it may still be busy with it. A printer
  /// that does not answer in time fails the call with
  /// [PrinterErrorType.printerNotReady] and the connection is closed. Unset,
  /// the probe is on in an atomic call and off otherwise.
  Future<String?> sendRawBytes(
    Uint8List bytes, {
    bool closePort = false,
    Duration writeTimeout = PrinterLimits.writeTimeout,
    Duration completionTimeout = PrinterLimits.completionTimeout,
    String? macAddress,
    String? pin,
    String? fallbackPin,
    Duration? connectTimeout,
    bool confirmCompletion = true,
    bool? probe,
  }) {
    return PrinterFlutterPlatform.instance.sendRawBytes(
      bytes,
      closePort: closePort,
      writeTimeout: writeTimeout,
      completionTimeout: completionTimeout,
      macAddress: macAddress,
      pin: pin,
      fallbackPin: fallbackPin,
      connectTimeout: connectTimeout,
      confirmCompletion: confirmCompletion,
      probe: probe,
    );
  }

  /// Closes the active Bluetooth connection.
  Future<String?> closePort() {
    return PrinterFlutterPlatform.instance.closePort();
  }

  /// Aborts the job in progress: its call ends with a [PrinterException] of type
  /// [PrinterErrorType.cancelled] and the next job runs on a new connection.
  /// Does nothing when no job is running.
  Future<void> cancel() {
    return PrinterFlutterPlatform.instance.cancel();
  }

  /// Prints the PDF at [filePath].
  ///
  /// Each chunk sent must be accepted within [writeTimeout]. With [closePort],
  /// the printer must confirm it processed everything it was sent within
  /// [completionTimeout], counted from the last byte sent. The confirmation does
  /// not mean the printer finished printing.
  /// Failures throw a [PrinterException].
  ///
  /// With [macAddress] the call is atomic: it opens the connection to that
  /// printer (using [pin], [fallbackPin] and [connectTimeout] as `openPort`
  /// does; [pin] defaults to "0000" and, unset, [fallbackPin] means no second
  /// attempt), sends, confirms and closes it, all in one native call.
  /// [closePort] has no effect then, a connection left open by `openPort` is
  /// closed first, and atomic calls run first-in, first-out. Do not mix the
  /// atomic style with the three-step style (`openPort`, send, `closePort`) on
  /// the same printer.
  ///
  /// With [confirmCompletion] (default true) the call waits up to
  /// [completionTimeout] for the printer to confirm it processed everything it
  /// was sent; that is not a confirmation that it finished printing. Pass false
  /// to skip the wait. A [PrinterErrorType.timeout] after everything was sent
  /// means "not confirmed", not "it did not print": check the printer before
  /// printing again.
  ///
  /// With [probe], the printer is first asked to answer (TSPL `OUT`) and no
  /// byte of the job is sent before it does. It has 2 seconds, or
  /// [completionTimeout] when the last job sent to that printer used
  /// [confirmCompletion] false, since it may still be busy with it. A printer
  /// that does not answer in time fails the call with
  /// [PrinterErrorType.printerNotReady] and the connection is closed. Unset,
  /// the probe is on in an atomic call and off otherwise.
  Future<String?> printPdf({
    required String filePath,
    PdfPrintOptions options = const PdfPrintOptions(),
    int copies = 1,
    bool closePort = false,
    Duration writeTimeout = PrinterLimits.writeTimeout,
    Duration completionTimeout = PrinterLimits.completionTimeout,
    String? macAddress,
    String? pin,
    String? fallbackPin,
    Duration? connectTimeout,
    bool confirmCompletion = true,
    bool? probe,
  }) {
    return PrinterFlutterPlatform.instance.printPdf(
      filePath,
      options,
      copies: copies,
      closePort: closePort,
      writeTimeout: writeTimeout,
      completionTimeout: completionTimeout,
      macAddress: macAddress,
      pin: pin,
      fallbackPin: fallbackPin,
      connectTimeout: connectTimeout,
      confirmCompletion: confirmCompletion,
      probe: probe,
    );
  }
}

/// Default limits of the printer calls.
abstract final class PrinterLimits {
  /// How long opening the Bluetooth connection may take.
  static const Duration connectTimeout = Duration(seconds: 15);

  /// How long the printer may take to accept each chunk of data.
  static const Duration writeTimeout = Duration(seconds: 20);

  /// How long the printer may take to confirm a job, counted from the last
  /// byte sent. The confirmation means the printer processed everything it was
  /// sent, not that it finished printing; a timeout after everything was sent
  /// means "not confirmed", not "it did not print".
  static const Duration completionTimeout = Duration(seconds: 60);
}

/// Why a printer call failed.
enum PrinterErrorType {
  /// The connection to the printer could not be opened, or none was open.
  notConnected,

  /// The connection was lost while sending or waiting.
  connectionLost,

  /// The printer did not answer within the limit.
  timeout,

  /// The job was cancelled with [PrinterFlutter.cancel].
  cancelled,

  /// The printer did not answer the probe sent before the job, so nothing of
  /// the job was sent. Turn the printer off and on.
  printerNotReady,

  /// Any other native error.
  unknown,
}

/// Error thrown by the printer calls.
class PrinterException implements Exception {
  /// Creates an error of the given [type], with an optional [message].
  const PrinterException(this.type, [this.message]);

  /// Why the call failed.
  final PrinterErrorType type;

  /// What the native side reported, if anything.
  final String? message;

  /// Native error codes, as sent by the Android plugin.
  static PrinterErrorType typeFromCode(String code) => switch (code) {
        'NOT_CONNECTED' => PrinterErrorType.notConnected,
        'CONNECTION_LOST' => PrinterErrorType.connectionLost,
        'TIMEOUT' => PrinterErrorType.timeout,
        'CANCELLED' => PrinterErrorType.cancelled,
        'PRINTER_NOT_READY' => PrinterErrorType.printerNotReady,
        _ => PrinterErrorType.unknown,
      };

  @override
  String toString() => 'PrinterException(${type.name}): ${message ?? ''}';
}
