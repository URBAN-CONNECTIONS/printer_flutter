import 'dart:typed_data';

import 'printer_flutter_platform_interface.dart';

class PrinterFlutter {
  Future<String?> getPlatformVersion() {
    return PrinterFlutterPlatform.instance.getPlatformVersion();
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
}
