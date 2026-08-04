import 'dart:typed_data';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/services.dart';
import 'package:printer_flutter/printer_flutter.dart';
import 'package:printer_flutter/printer_flutter_platform_interface.dart';
import 'package:printer_flutter/printer_flutter_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockPrinterFlutterPlatform
    with MockPlatformInterfaceMixin
    implements PrinterFlutterPlatform {
  @override
  Future<String?> getPlatformVersion() => Future.value('42');

  @override
  Future<bool> ensureBluetoothIsOn() => Future.value(true);

  @override
  Future<bool> requestPermissions() => Future.value(true);

  @override
  Future<String?> openPort(String macAddress) => Future.value('Success');

  @override
  Future<String?> sendTspl(String command, {bool closePort = false}) =>
      Future.value('Success');

  @override
  Future<String?> sendRawBytes(Uint8List bytes, {bool closePort = false}) =>
      Future.value('Success');

  @override
  Future<String?> closePort() => Future.value('Success');

  @override
  Future<String?> printPdf(String filePath, PdfPrintOptions options,
          {int copies = 1, bool closePort = false}) =>
      Future.value('Success');
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final PrinterFlutterPlatform initialPlatform =
      PrinterFlutterPlatform.instance;

  test('$MethodChannelPrinterFlutter is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelPrinterFlutter>());
  });

  test('getPlatformVersion', () async {
    PrinterFlutter printerFlutterPlugin = PrinterFlutter();
    MockPrinterFlutterPlatform fakePlatform = MockPrinterFlutterPlatform();
    PrinterFlutterPlatform.instance = fakePlatform;

    expect(await printerFlutterPlugin.getPlatformVersion(), '42');
  });

  test('sendTspl serialization with closePort', () async {
    final platform = MethodChannelPrinterFlutter();
    final log = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(platform.methodChannel,
            (MethodCall methodCall) async {
      log.add(methodCall);
      return 'Success';
    });

    await platform.sendTspl('TEST', closePort: true);
    expect(log.length, 1);
    expect(log.first.method, 'sendTspl');
    expect(log.first.arguments['closePort'], true);
    expect(log.first.arguments['command'], 'TEST');
  });

  test('printPdf serialization with closePort', () async {
    final platform = MethodChannelPrinterFlutter();
    final log = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(platform.methodChannel,
            (MethodCall methodCall) async {
      log.add(methodCall);
      return 'Success';
    });

    await platform.printPdf('path', const PdfPrintOptions(),
        copies: 2, closePort: true);
    expect(log.length, 1);
    expect(log.first.method, 'printPdf');
    expect(log.first.arguments['closePort'], true);
    expect(log.first.arguments['copies'], 2);
  });
}
