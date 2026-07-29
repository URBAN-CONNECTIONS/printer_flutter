import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'printer_flutter_platform_interface.dart';

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
}
