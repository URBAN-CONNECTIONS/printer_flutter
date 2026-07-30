## 1. Dart API Implementation

- [x] 1.1 Add `ensureBluetoothIsOn()` to `printer_flutter_platform_interface.dart`.
- [x] 1.2 Implement `ensureBluetoothIsOn()` in `printer_flutter_method_channel.dart` to invoke the native channel.
- [x] 1.3 Expose `ensureBluetoothIsOn()` on the main `PrinterFlutter` class in `printer_flutter.dart`.

## 2. Android Native Implementation

- [x] 2.1 Update `PrinterFlutterPlugin.kt` to handle the `ensureBluetoothIsOn` method call.
- [x] 2.2 Add logic to check `BluetoothAdapter.getDefaultAdapter().isEnabled` and return `true` if already enabled.
- [x] 2.3 Implement starting the `BluetoothAdapter.ACTION_REQUEST_ENABLE` intent for result when Bluetooth is off.
- [x] 2.4 Implement `PluginRegistry.ActivityResultListener` to capture the intent result and return the boolean to Dart.

## 3. iOS Native Implementation

- [x] 3.1 Update `PrinterFlutterPlugin.swift` to import `CoreBluetooth`.
- [x] 3.2 Add a `CBCentralManager` instance and implement `CBCentralManagerDelegate` to monitor state.
- [x] 3.3 Add handling for the `ensureBluetoothIsOn` method call to check `centralManager.state == .poweredOn` and return the result.
