## Why

Flutter applications requiring direct communication with TSC thermal label printers currently lack native Android integration for the official `tscsdk.jar` SDK. Adding this integration enables reliable Bluetooth, USB, and Wi-Fi label printing using native TSPL commands provided by the vendor library.

## What Changes

- Move `tscsdk.jar` from project root to `android/libs/tscsdk.jar`.
- Update `android/build.gradle` to include local JAR dependencies from `android/libs`.
- Integrate `com.example.tscdll.TSCActivity` (Bluetooth/Serial), `TSCUSBActivity` (USB), and `TscWifiActivity` (Wi-Fi) inside `PrinterFlutterPlugin.kt`.
- Expose Flutter `MethodChannel` endpoints for connecting, sending TSPL commands/bitmaps, and closing printer connections via the native SDK.
- Update `android/src/main/AndroidManifest.xml` with required Android Bluetooth and network permissions.

## Non-goals

- iOS or desktop platform implementation for `tscsdk.jar`.
- Full rewrite of non-TSC printer protocols (ESC/POS or CPCL).

## Capabilities

### New Capabilities
- `tsc-printer-connection`: Connection management (Bluetooth MAC, USB, Wi-Fi IP/Port) and native TSPL command execution using `tscsdk.jar`.

### Modified Capabilities

*(None)*

## Impact

- **Android Module**: Updates `android/build.gradle`, `PrinterFlutterPlugin.kt`, and `AndroidManifest.xml`.
- **Dart API**: Expands `printer_flutter` plugin API to invoke TSC SDK methods over `MethodChannel`.
- **Dependencies**: Adds local `tscsdk.jar` binary dependency to the Android project.
