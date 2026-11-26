import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:printer_flutter/printer_flutter.dart';
import 'package:printer_flutter/printer_flutter_method_channel.dart';
import 'package:printer_flutter/printer_flutter_platform_interface.dart';

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

  group('atomic call', () {
    const testMac = '00:00:00:00:00:00';
    late List<MethodCall> log;

    setUp(() {
      log = [];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
        log.add(methodCall);
        return 'Success';
      });
    });

    Map<Object?, Object?> sentArguments() =>
        log.single.arguments as Map<Object?, Object?>;

    test('printPdf sends the atomic keys that were set', () async {
      await platform.printPdf(
        'file.pdf',
        const PdfPrintOptions(),
        macAddress: testMac,
        pin: '1111',
        fallbackPin: '2222',
        connectTimeout: const Duration(seconds: 7),
      );
      final arguments = sentArguments();
      expect(arguments['macAddress'], testMac);
      expect(arguments['pin'], '1111');
      expect(arguments['fallbackPin'], '2222');
      expect(arguments['connectTimeoutMs'], 7000);
      expect(arguments.containsKey('confirmCompletion'), isFalse);
    });

    test('sendTspl sends the atomic keys that were set', () async {
      await platform.sendTspl(
        'TEXT',
        macAddress: testMac,
        pin: '1111',
        fallbackPin: '2222',
        connectTimeout: const Duration(seconds: 7),
      );
      final arguments = sentArguments();
      expect(log.single.method, 'sendTspl');
      expect(arguments['macAddress'], testMac);
      expect(arguments['pin'], '1111');
      expect(arguments['fallbackPin'], '2222');
      expect(arguments['connectTimeoutMs'], 7000);
    });

    test('sendRawBytes sends the atomic keys that were set', () async {
      await platform.sendRawBytes(
        Uint8List.fromList([1, 2, 3]),
        macAddress: testMac,
        pin: '1111',
        fallbackPin: '2222',
        connectTimeout: const Duration(seconds: 7),
      );
      final arguments = sentArguments();
      expect(log.single.method, 'sendRawBytes');
      expect(arguments['macAddress'], testMac);
      expect(arguments['pin'], '1111');
      expect(arguments['fallbackPin'], '2222');
      expect(arguments['connectTimeoutMs'], 7000);
    });

    test('only macAddress is sent when the other options are unset', () async {
      await platform.sendTspl('TEXT', macAddress: testMac);
      final arguments = sentArguments();
      expect(arguments['macAddress'], testMac);
      expect(arguments.containsKey('pin'), isFalse);
      expect(arguments.containsKey('fallbackPin'), isFalse);
      expect(arguments.containsKey('connectTimeoutMs'), isFalse);
      expect(arguments.containsKey('confirmCompletion'), isFalse);
    });

    test('confirmCompletion false is sent on every print call', () async {
      await platform.printPdf('file.pdf', const PdfPrintOptions(),
          macAddress: testMac, confirmCompletion: false);
      await platform.sendTspl('TEXT',
          macAddress: testMac, confirmCompletion: false);
      await platform.sendRawBytes(Uint8List(0),
          macAddress: testMac, confirmCompletion: false);
      expect(log, hasLength(3));
      for (final call in log) {
        expect((call.arguments as Map)['confirmCompletion'], false);
      }
    });

    test('closePort is still sent with the atomic call', () async {
      await platform.sendTspl('TEXT', macAddress: testMac, closePort: true);
      expect(sentArguments()['closePort'], true);
    });

    test('calls without macAddress send the map they always sent', () async {
      await platform.sendTspl('TEXT', closePort: true);
      await platform.sendRawBytes(Uint8List.fromList([9]));
      await platform.printPdf('file.pdf', const PdfPrintOptions(), copies: 2);
      expect(log[0].arguments, {
        'command': 'TEXT',
        'closePort': true,
        'writeTimeoutMs': 20000,
        'completionTimeoutMs': 60000,
      });
      expect(log[1].arguments, {
        'bytes': Uint8List.fromList([9]),
        'closePort': false,
        'writeTimeoutMs': 20000,
        'completionTimeoutMs': 60000,
      });
      expect(log[2].arguments, {
        'filePath': 'file.pdf',
        'options': const PdfPrintOptions().toMap(),
        'copies': 2,
        'closePort': false,
        'writeTimeoutMs': 20000,
        'completionTimeoutMs': 60000,
      });
    });

    test('PrinterFlutter forwards the atomic options to the platform',
        () async {
      final previous = PrinterFlutterPlatform.instance;
      PrinterFlutterPlatform.instance = platform;
      addTearDown(() => PrinterFlutterPlatform.instance = previous);

      await PrinterFlutter().sendTspl(
        'TEXT',
        macAddress: testMac,
        pin: '1111',
        connectTimeout: const Duration(seconds: 3),
        confirmCompletion: false,
      );
      final arguments = sentArguments();
      expect(arguments['macAddress'], testMac);
      expect(arguments['pin'], '1111');
      expect(arguments['connectTimeoutMs'], 3000);
      expect(arguments['confirmCompletion'], false);
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
