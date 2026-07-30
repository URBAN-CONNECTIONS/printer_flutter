import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:printer_flutter/printer_flutter_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelPrinterFlutter platform = MethodChannelPrinterFlutter();
  const MethodChannel channel = MethodChannel('printer_flutter');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        return '42';
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('getPlatformVersion', () async {
    expect(await platform.getPlatformVersion(), '42');
  });

  test('printPdf passes copies argument', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
      if (methodCall.method == 'printPdf') {
        final copies = methodCall.arguments['copies'];
        return 'copies=$copies';
      }
      return null;
    });

    final result = await platform.printPdf('path/to/file.pdf', const PdfPrintOptions(), copies: 3);
    expect(result, 'copies=3');
  });
}
