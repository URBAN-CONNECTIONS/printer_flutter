## 1. Dart Interface Update

- [x] 1.1 Update `PrinterFlutterPlatform` interface methods (`sendTspl`, `sendRawBytes`, `printPdf`) to accept optional `bool closePort = false`.
- [x] 1.2 Update `MethodChannelPrinterFlutter` implementation in `printer_flutter_method_channel.dart` to forward `closePort` parameter over MethodChannel.
- [x] 1.3 Update `PrinterFlutter` facade class in `lib/printer_flutter.dart` to expose `closePort` optional parameter.

## 2. Native Android Implementation

- [x] 2.1 Update `PrinterFlutterPlugin.kt` method call handler to parse `closePort` boolean parameter for `sendTspl`, `sendRawBytes`, and `printPdf`.
- [x] 2.2 Implement background completion worker logic in `PrinterFlutterPlugin.kt`: send `OUT "COMPLETED\r\n"`, await completion marker indefinitely, call `tsc.closeport()`, and finally return MethodChannel success.

## 3. Verification & Formatting

- [x] 3.1 Run `dart format .` and verify static analysis with `dart analyze`.
- [x] 3.2 Add unit tests for Dart platform interface methods verifying parameter serialization.
