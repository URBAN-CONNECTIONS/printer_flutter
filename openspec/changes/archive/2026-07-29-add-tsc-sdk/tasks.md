## 1. Project & Build System Setup

- [x] 1.1 Create `android/libs` directory and relocate `tscsdk.jar` from root into `android/libs/tscsdk.jar`.
- [x] 1.2 Update `android/build.gradle` to include `implementation fileTree(dir: 'libs', include: ['*.jar'])`.
- [x] 1.3 Update `android/src/main/AndroidManifest.xml` to include Bluetooth Classic (`BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`) and network permissions.

## 2. Android Native Integration (Kotlin)

- [x] 2.1 Import `com.example.tscdll.TSCActivity`, `TSCUSBActivity`, and `TscWifiActivity` into `PrinterFlutterPlugin.kt`.
- [x] 2.2 Implement `MethodChannel` handlers for `openPort`, `sendTspl`, `sendRawBytes`, and `closePort`.
- [x] 2.3 Ensure native SDK calls are safely dispatched to avoid blocking the main UI thread.

## 3. Flutter & Dart API Integration

- [x] 3.1 Update `PrinterFlutterPlatform` interface and `MethodChannelPrinterFlutter` implementation in Dart.
- [x] 3.2 Add high-level helper methods in `lib/printer_flutter.dart` for connecting and printing via TSC SDK.

## 4. Verification & Formatting

- [x] 4.1 Run `flutter pub get` and verify Android project builds cleanly (`flutter build aar` or `gradlew assembleDebug`).
- [x] 4.2 Run `dart format .` and `dart analyze` to ensure code quality standards.
