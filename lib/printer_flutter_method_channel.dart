import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'printer_flutter_platform_interface.dart';
import 'printer_flutter.dart';

/// An implementation of [PrinterFlutterPlatform] that uses method channels.
class MethodChannelPrinterFlutter extends PrinterFlutterPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('printer_flutter');

  @override
  Future<String?> getPlatformVersion() async {
    final version =
        await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }

  @override
  Future<bool> ensureBluetoothIsOn() async {
    final result = await methodChannel.invokeMethod<bool>('ensureBluetoothIsOn');
    return result ?? false;
  }

  @override
  Future<bool> requestPermissions() async {
    final result = await methodChannel.invokeMethod<bool>('requestPermissions');
    return result ?? false;
  }

  @override
  Future<String?> openPort(String macAddress) async {
    final status = await methodChannel
        .invokeMethod<String>('openPort', {'macAddress': macAddress});
    return status;
  }

  @override
  Future<String?> sendTspl(String command) async {
    final status = await methodChannel
        .invokeMethod<String>('sendTspl', {'command': command});
    return status;
  }

  @override
  Future<String?> sendRawBytes(Uint8List bytes) async {
    final status = await methodChannel
        .invokeMethod<String>('sendRawBytes', {'bytes': bytes});
    return status;
  }

  @override
  Future<String?> closePort() async {
    final status = await methodChannel.invokeMethod<String>('closePort');
    return status;
  }

  @override
  Future<String?> printPdf(String filePath, PdfPrintOptions options, {int copies = 1}) async {
    final status = await methodChannel.invokeMethod<String>('printPdf', {
      'filePath': filePath,
      'options': options.toMap(),
      'copies': copies,
    });
    return status;
  }
}
