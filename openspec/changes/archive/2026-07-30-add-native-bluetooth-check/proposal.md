## Why

Checking if Bluetooth is enabled and prompting the user to turn it on is a core requirement for a Bluetooth printer plugin. Currently, this relies heavily on large external dependencies like `flutter_blue_plus`, which can cause version conflicts and bloat the app. A native check and enablement flow inside the plugin provides a lighter, more reliable developer experience.

## What Changes

- Add a new method `ensureBluetoothIsOn()` to the Dart `PrinterFlutter` facade and `PrinterFlutterPlatform` interface.
- Implement `ensureBluetoothIsOn` natively for Android (Kotlin) by checking `BluetoothAdapter.getDefaultAdapter().isEnabled` and starting an intent (`BluetoothAdapter.ACTION_REQUEST_ENABLE`) if disabled, waiting for the user's response before returning `true`/`false`.
- Implement `ensureBluetoothIsOn` natively for iOS (Swift) by checking `CBCentralManager.state == .poweredOn` and returning `true`/`false`.

## Capabilities

### New Capabilities
- `bluetooth-state-management`: Checking and enforcing Bluetooth enabled state natively on Android and iOS.

### Modified Capabilities
- (None)

## Impact

- **API:** Adds a new asynchronous method `ensureBluetoothIsOn()` to the public Dart API.
- **Android:** Adds new activity result listeners and intent flows to `PrinterFlutterPlugin.kt`.
- **iOS:** Adds `CoreBluetooth` imports and state checking to `PrinterFlutterPlugin.swift`.
- **Dependencies:** Removes the need for app developers to manually include or configure `flutter_blue_plus` just to check/enable Bluetooth.

## Non-goals

- We will not handle complex Bluetooth pairing or scanning, only checking the adapter state and prompting to turn the radio on.
