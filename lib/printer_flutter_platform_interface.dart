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
  Future<String?> openPort(String macAddress) {
    throw UnimplementedError('openPort() has not been implemented.');
  }

  /// Sends a TSPL command string to the connected printer.
  Future<String?> sendTspl(String command, {bool closePort = false}) {
    throw UnimplementedError('sendTspl() has not been implemented.');
  }

  /// Sends raw bytes to the connected printer.
  Future<String?> sendRawBytes(Uint8List bytes, {bool closePort = false}) {
    throw UnimplementedError('sendRawBytes() has not been implemented.');
  }

  /// Closes the active Bluetooth connection.
  Future<String?> closePort() {
    throw UnimplementedError('closePort() has not been implemented.');
  }

  /// Renders and prints a PDF file using the specified configuration.
  Future<String?> printPdf(String filePath, PdfPrintOptions options,
      {int copies = 1, bool closePort = false}) {
    throw UnimplementedError('printPdf() has not been implemented.');
  }
}
