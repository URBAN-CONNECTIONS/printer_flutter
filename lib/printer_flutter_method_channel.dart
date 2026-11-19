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
    final result =
        await methodChannel.invokeMethod<bool>('ensureBluetoothIsOn');
    return result ?? false;
  }

  @override
  Future<bool> requestPermissions() async {
    final result = await methodChannel.invokeMethod<bool>('requestPermissions');
    return result ?? false;
  }

  @override
  Future<String?> openPort(
    String macAddress, {
    String pin = "0000",
    String? fallbackPin = "1234",
    Duration connectTimeout = PrinterLimits.connectTimeout,
    bool resetPrinterAfterFailure = false,
  }) {
    return _invoke<String>('openPort', {
      'macAddress': macAddress,
      'pin': pin,
      'fallbackPin': fallbackPin,
      'connectTimeoutMs': connectTimeout.inMilliseconds,
      'resetPrinterAfterFailure': resetPrinterAfterFailure,
    });
  }

  @override
  Future<String?> sendTspl(
    String command, {
    bool closePort = false,
    Duration writeTimeout = PrinterLimits.writeTimeout,
    Duration completionTimeout = PrinterLimits.completionTimeout,
  }) {
    return _invoke<String>('sendTspl', {
      'command': command,
      'closePort': closePort,
      'writeTimeoutMs': writeTimeout.inMilliseconds,
      'completionTimeoutMs': completionTimeout.inMilliseconds,
    });
  }

  @override
  Future<String?> sendRawBytes(
    Uint8List bytes, {
    bool closePort = false,
    Duration writeTimeout = PrinterLimits.writeTimeout,
    Duration completionTimeout = PrinterLimits.completionTimeout,
  }) {
    return _invoke<String>('sendRawBytes', {
      'bytes': bytes,
      'closePort': closePort,
      'writeTimeoutMs': writeTimeout.inMilliseconds,
      'completionTimeoutMs': completionTimeout.inMilliseconds,
    });
  }

  @override
  Future<String?> closePort() => _invoke<String>('closePort');

  @override
  Future<void> cancel() => _invoke<String>('cancel');

  @override
  Future<String?> printPdf(
    String filePath,
    PdfPrintOptions options, {
    int copies = 1,
    bool closePort = false,
    Duration writeTimeout = PrinterLimits.writeTimeout,
    Duration completionTimeout = PrinterLimits.completionTimeout,
  }) {
    return _invoke<String>('printPdf', {
      'filePath': filePath,
      'options': options.toMap(),
      'copies': copies,
      'closePort': closePort,
      'writeTimeoutMs': writeTimeout.inMilliseconds,
      'completionTimeoutMs': completionTimeout.inMilliseconds,
    });
  }

  /// Invokes [method] and turns native errors into [PrinterException].
  Future<T?> _invoke<T>(String method,
      [Map<String, dynamic>? arguments]) async {
    try {
      return await methodChannel.invokeMethod<T>(method, arguments);
    } on PlatformException catch (e) {
      throw PrinterException(PrinterException.typeFromCode(e.code), e.message);
    }
  }
}
