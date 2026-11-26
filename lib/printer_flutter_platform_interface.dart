import 'dart:typed_data';

import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'printer_flutter_method_channel.dart';
import 'printer_flutter.dart';

abstract class PrinterFlutterPlatform extends PlatformInterface {
  /// Constructs a PrinterFlutterPlatform.
  PrinterFlutterPlatform() : super(token: _token);

  static final Object _token = Object();

  static PrinterFlutterPlatform _instance = MethodChannelPrinterFlutter();

  /// The default instance of [PrinterFlutterPlatform] to use.
  ///
  /// Defaults to [MethodChannelPrinterFlutter].
  static PrinterFlutterPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [PrinterFlutterPlatform] when
  /// they register themselves.
  static set instance(PrinterFlutterPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  /// Checks if Bluetooth is enabled and natively prompts to enable it on Android.
  Future<bool> ensureBluetoothIsOn() {
    throw UnimplementedError('ensureBluetoothIsOn() has not been implemented.');
  }

  /// Requests Bluetooth and location permissions on Android.
  Future<bool> requestPermissions() {
    throw UnimplementedError('requestPermissions() has not been implemented.');
  }

  /// Opens a Bluetooth connection to the printer using its MAC address.
  /// The [pin] and [fallbackPin] are used for automatic Bluetooth pairing on Android.
  ///
  /// Gives up after [connectTimeout] with a [PrinterException] of type
  /// [PrinterErrorType.notConnected]. With [resetPrinterAfterFailure], the printer
  /// is reset with `<ESC>!R` first when the previous job failed.
  Future<String?> openPort(
    String macAddress, {
    String pin = "0000",
    String? fallbackPin = "1234",
    Duration connectTimeout = PrinterLimits.connectTimeout,
    bool resetPrinterAfterFailure = false,
  }) {
    throw UnimplementedError('openPort() has not been implemented.');
  }

  /// Sends a TSPL command string to the connected printer.
  ///
  /// Each chunk must be accepted within [writeTimeout]. With [closePort], waits
  /// up to [completionTimeout] for the printer to confirm, then closes the port.
  ///
  /// With [macAddress] the call is atomic: it opens the connection (using [pin],
  /// [fallbackPin] and [connectTimeout] like `openPort`), sends, confirms and
  /// closes it, and [closePort] has no effect. Atomic calls queue first-in,
  /// first-out; do not mix them with `openPort`. With [confirmCompletion] false
  /// the call does not wait for the printer's confirmation. A timeout after
  /// everything was sent means "not confirmed", not "it did not print".
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
  }) {
    throw UnimplementedError('sendTspl() has not been implemented.');
  }

  /// Sends raw bytes to the connected printer.
  ///
  /// Each chunk must be accepted within [writeTimeout]. With [closePort], waits
  /// up to [completionTimeout] for the printer to confirm, then closes the port.
  ///
  /// With [macAddress] the call is atomic: it opens the connection (using [pin],
  /// [fallbackPin] and [connectTimeout] like `openPort`), sends, confirms and
  /// closes it, and [closePort] has no effect. Atomic calls queue first-in,
  /// first-out; do not mix them with `openPort`. With [confirmCompletion] false
  /// the call does not wait for the printer's confirmation. A timeout after
  /// everything was sent means "not confirmed", not "it did not print".
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
  }) {
    throw UnimplementedError('sendRawBytes() has not been implemented.');
  }

  /// Closes the active Bluetooth connection.
  Future<String?> closePort() {
    throw UnimplementedError('closePort() has not been implemented.');
  }

  /// Aborts the job in progress; its call ends with a [PrinterException] of type
  /// [PrinterErrorType.cancelled]. Does nothing when no job is running.
  Future<void> cancel() {
    throw UnimplementedError('cancel() has not been implemented.');
  }

  /// Renders and prints a PDF file using the specified configuration.
  ///
  /// Each chunk must be accepted within [writeTimeout]. With [closePort], the
  /// printer must confirm within [completionTimeout], counted from the last byte.
  ///
  /// With [macAddress] the call is atomic: it opens the connection (using [pin],
  /// [fallbackPin] and [connectTimeout] like `openPort`), sends, confirms and
  /// closes it, and [closePort] has no effect. Atomic calls queue first-in,
  /// first-out; do not mix them with `openPort`. With [confirmCompletion] false
  /// the call does not wait for the printer's confirmation. A timeout after
  /// everything was sent means "not confirmed", not "it did not print".
  Future<String?> printPdf(
    String filePath,
    PdfPrintOptions options, {
    int copies = 1,
    bool closePort = false,
    Duration writeTimeout = PrinterLimits.writeTimeout,
    Duration completionTimeout = PrinterLimits.completionTimeout,
    String? macAddress,
    String? pin,
    String? fallbackPin,
    Duration? connectTimeout,
    bool confirmCompletion = true,
  }) {
    throw UnimplementedError('printPdf() has not been implemented.');
  }
}
