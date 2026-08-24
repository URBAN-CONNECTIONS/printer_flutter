# Proposal: Automatic Bluetooth PIN Pairing

## Why

Connecting to unbonded Bluetooth thermal printers prompts an Android system dialog requiring manual entry of legacy PIN codes (typically `0000` or `1234`). This interrupts the printing workflow and creates friction for users. Automating this PIN challenge via a broadcast receiver enables seamless, one-touch connection and printing.

## What Changes

- Introduce native Android `BroadcastReceiver` listening to `BluetoothDevice.ACTION_PAIRING_REQUEST` with high priority.
- Automatically respond to PIN pairing requests with default PIN `"0000"` (and fallback `"1234"` if authentication fails).
- Filter pairing requests by the target printer MAC address to avoid interfering with other Bluetooth devices.
- Bind the receiver lifecycle cleanly to Flutter's Android Activity lifecycle (`onAttachedToActivity` / `onDetachedFromActivity`) without background battery overhead.
- Expose optional `pin` and `fallbackPin` parameters in the Dart API (`openPort`).

## Capabilities

### New Capabilities
- `auto-pin-pairing`: Programmatic interception and auto-completion of Bluetooth legacy PIN pairing requests for thermal printers.

### Modified Capabilities
- `tsc-printer-connection`: Update `openPort` to track target MAC address and accept optional custom PIN parameters for pairing.

## Non-goals

- Supporting BLE passkey confirmation or numeric comparison pairing (only legacy SPP PIN pairing is handled).
- Removing or bypassing Android OS-level runtime permission requirements (`BLUETOOTH_CONNECT`).
- Supporting iOS Bluetooth auto-pairing (iOS handles MFi/BLE pairing at the OS level).

## Impact

- **Android Native**: `PrinterFlutterPlugin.kt` registers/unregisters `BroadcastReceiver` and injects PIN via `device.setPin()`.
- **Dart API**: `PrinterFlutter.openPort(macAddress, {String pin = "0000", String? fallbackPin = "1234"})`.
- **Backward Compatibility**: Fully backward compatible; existing `openPort(macAddress)` calls default to PIN `"0000"`.
