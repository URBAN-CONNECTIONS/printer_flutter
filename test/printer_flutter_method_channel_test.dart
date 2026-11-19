import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:printer_flutter/printer_flutter.dart';
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

    final result = await platform
        .printPdf('path/to/file.pdf', const PdfPrintOptions(), copies: 3);
    expect(result, 'copies=3');
  });

  group('limits', () {
    late List<MethodCall> log;

    setUp(() {
      log = [];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
        log.add(methodCall);
        return 'Success';
      });
    });

    test('printPdf sends the default limits in milliseconds', () async {
      await platform.printPdf('file.pdf', const PdfPrintOptions(),
          closePort: true);
      expect(log.single.arguments['writeTimeoutMs'], 20000);
      expect(log.single.arguments['completionTimeoutMs'], 60000);
    });

    test('printPdf sends a custom completion limit', () async {
      await platform.printPdf('file.pdf', const PdfPrintOptions(),
          closePort: true, completionTimeout: const Duration(seconds: 90));
      expect(log.single.arguments['completionTimeoutMs'], 90000);
    });

    test('openPort sends the connect limit and the reset option', () async {
      await platform.openPort('MAC',
          connectTimeout: const Duration(seconds: 5),
          resetPrinterAfterFailure: true);
      expect(log.single.arguments['connectTimeoutMs'], 5000);
      expect(log.single.arguments['resetPrinterAfterFailure'], true);
    });

    test('openPort does not reset the printer by default', () async {
      await platform.openPort('MAC');
      expect(log.single.arguments['resetPrinterAfterFailure'], false);
    });

    test('cancel invokes the native cancel', () async {
      await platform.cancel();
      expect(log.single.method, 'cancel');
    });
  });

  group('errors', () {
    void failWith(String code) {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
        throw PlatformException(code: code, message: 'native $code');
      });
    }

    const cases = {
      'NOT_CONNECTED': PrinterErrorType.notConnected,
      'CONNECTION_LOST': PrinterErrorType.connectionLost,
      'TIMEOUT': PrinterErrorType.timeout,
      'CANCELLED': PrinterErrorType.cancelled,
      'PRINTER_ERROR': PrinterErrorType.unknown,
    };

    cases.forEach((code, type) {
      test('$code becomes PrinterException(${type.name})', () async {
        failWith(code);
        await expectLater(
          platform.printPdf('file.pdf', const PdfPrintOptions()),
          throwsA(isA<PrinterException>()
              .having((e) => e.type, 'type', type)
              .having((e) => e.message, 'message', 'native $code')),
        );
      });
    });

    test('openPort failure is an error, not a status string', () async {
      failWith('NOT_CONNECTED');
      await expectLater(
        platform.openPort('MAC'),
        throwsA(isA<PrinterException>()
            .having((e) => e.type, 'type', PrinterErrorType.notConnected)),
      );
    });
  });
}
