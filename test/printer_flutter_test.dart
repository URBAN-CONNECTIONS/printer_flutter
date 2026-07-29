import 'package:flutter_test/flutter_test.dart';
import 'package:printer_flutter/printer_flutter.dart';
import 'package:printer_flutter/printer_flutter_platform_interface.dart';
import 'package:printer_flutter/printer_flutter_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockPrinterFlutterPlatform
    with MockPlatformInterfaceMixin
    implements PrinterFlutterPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final PrinterFlutterPlatform initialPlatform = PrinterFlutterPlatform.instance;

  test('$MethodChannelPrinterFlutter is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelPrinterFlutter>());
  });

  test('getPlatformVersion', () async {
    PrinterFlutter printerFlutterPlugin = PrinterFlutter();
    MockPrinterFlutterPlatform fakePlatform = MockPrinterFlutterPlatform();
    PrinterFlutterPlatform.instance = fakePlatform;

    expect(await printerFlutterPlugin.getPlatformVersion(), '42');
  });
}
