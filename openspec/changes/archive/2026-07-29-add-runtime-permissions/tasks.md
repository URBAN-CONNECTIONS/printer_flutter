## 1. Native Android Implementation (Kotlin)

- [x] 1.1 Update `PrinterFlutterPlugin.kt` to implement `ActivityAware` and `PluginRegistry.RequestPermissionsResultListener`.
- [x] 1.2 Implement `requestPermissions` MethodChannel handler with SDK version checking (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` on API 31+, `ACCESS_FINE_LOCATION` on < 31).

## 2. Dart API & Interface

- [x] 2.1 Add `requestPermissions()` to `PrinterFlutterPlatform` interface and `MethodChannelPrinterFlutter`.
- [x] 2.2 Expose `requestPermissions()` in `lib/printer_flutter.dart`.
- [x] 2.3 Update `test/printer_flutter_test.dart` with mock stubs.

## 3. Example App UI Integration & Verification

- [x] 3.1 Add "Request Bluetooth Permissions" button to `example/lib/main.dart`.
- [x] 3.2 Run `dart format .` and `dart analyze` to verify code quality.
