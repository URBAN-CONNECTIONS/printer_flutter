import 'dart:typed_data';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'printer_flutter_method_channel.dart';

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

  Future<String?> openPort(String macAddress) {
    throw UnimplementedError('openPort() has not been implemented.');
  }

  Future<String?> sendTspl(String command) {
    throw UnimplementedError('sendTspl() has not been implemented.');
  }

  Future<String?> sendRawBytes(Uint8List bytes) {
    throw UnimplementedError('sendRawBytes() has not been implemented.');
  }

  Future<String?> closePort() {
    throw UnimplementedError('closePort() has not been implemented.');
  }
}
