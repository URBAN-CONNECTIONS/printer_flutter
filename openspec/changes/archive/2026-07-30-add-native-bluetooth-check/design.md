## Context

When working with Bluetooth printers, confirming that the device's Bluetooth radio is actually turned on is a fundamental first step before attempting to discover or connect to a device. In previous iterations, we considered relying on third-party dependencies like `flutter_blue_plus` to handle this. However, bringing in a large Bluetooth LE package just to check the basic adapter state and trigger the system prompt is overkill and introduces potential version conflicts for app developers.

## Goals / Non-Goals

**Goals:**
- Provide a single, easy-to-use method `ensureBluetoothIsOn()` on the `PrinterFlutter` class.
- Natively check the Bluetooth adapter state on both Android and iOS.
- On Android, if Bluetooth is off, natively prompt the user to turn it on via the system dialog (`BluetoothAdapter.ACTION_REQUEST_ENABLE`).
- Wait for the user to interact with the Android dialog (Allow/Deny) before returning the final boolean result to Dart.

**Non-Goals:**
- iOS cannot programmatically prompt the user to turn on Bluetooth via an intent/Settings deep link in the same way Android does; the iOS implementation will simply return the current state, leaving any UI prompting to the app developer (or relying on the automatic OS prompt when CoreBluetooth starts).
- We are not handling Bluetooth permissions in this specific method (we already have a `requestPermissions` method for that).

## Decisions

1.  **Wait for Android Intent Result:**
    Instead of just firing the intent and returning `false` (requiring the developer to poll or listen to state changes), we will use `startActivityForResult` in `PrinterFlutterPlugin.kt`. By implementing `PluginRegistry.ActivityResultListener`, we can intercept the result of the `ACTION_REQUEST_ENABLE` intent. If the result is `RESULT_OK`, we return `true` to Dart. If it is `RESULT_CANCELED`, we return `false`. This creates a clean, predictable `Future<bool>` for the Flutter developer.

2.  **iOS `CBCentralManager` instantiation:**
    Instantiating a `CBCentralManager` without options can sometimes trigger an immediate iOS system prompt if Bluetooth is off. However, since we just want to check the state, we will instantiate it and check `manager.state == .poweredOn`. Since state initialization is asynchronous on iOS, we may need to implement `CBCentralManagerDelegate` and wait for `centralManagerDidUpdateState` to fire before returning the result, or just check the state if it's already known. For simplicity, we will check the state synchronously; if it is `.unknown`, we'll return false (or we can wait for the delegate).

## Risks / Trade-offs

-   [Risk] **Android Lifecycle / Activity nullability:** `startActivityForResult` requires an active `Activity`. If the plugin is detached from the activity, the intent cannot be fired.
    -   *Mitigation:* We will check if `activity != null` before starting the intent. If it is null, we return the current state immediately without prompting.
-   [Risk] **iOS asynchronous state:** `CBCentralManager` state might not be immediately available right upon instantiation.
    -   *Mitigation:* To be safe, the native iOS code should implement the delegate and resolve the Flutter result only after `centralManagerDidUpdateState` is called with a definitive state (like `.poweredOn` or `.poweredOff`).
