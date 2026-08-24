## 1. Native Android Pairing Receiver

- [x] 1.1 Implement `BroadcastReceiver` in `PrinterFlutterPlugin.kt` listening to `BluetoothDevice.ACTION_PAIRING_REQUEST` and `ACTION_BOND_STATE_CHANGED` with `IntentFilter.SYSTEM_HIGH_PRIORITY`.
- [x] 1.2 Implement MAC address filtering and automatic PIN injection (`device.setPin`, `device.setPairingConfirmation`, `abortBroadcast`) in `PrinterFlutterPlugin.kt`.
- [x] 1.3 Implement fallback PIN handling on authentication failure (`UNBOND_REASON_AUTH_FAILED`).
- [x] 1.4 Register and unregister receiver in `onAttachedToActivity` / `onDetachedFromActivity`.

## 2. Dart API & MethodChannel

- [x] 2.1 Update `PrinterFlutterPlatform` interface and `MethodChannelPrinterFlutter` to support optional `pin` and `fallbackPin` parameters in `openPort`.
- [x] 2.2 Update `PrinterFlutter` facade in `lib/printer_flutter.dart` with default values `pin = "0000"` and `fallbackPin = "1234"`.

## 3. Testing & Verification

- [x] 3.1 Update unit tests in `test/` to verify `openPort` parameters and method calls.
- [x] 3.2 Update `example/lib/main.dart` UI to support testing with auto PIN configurations.
- [x] 3.3 Run `dart format .` and `dart analyze` to ensure clean code quality.
