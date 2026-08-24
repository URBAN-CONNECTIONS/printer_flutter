## ADDED Requirements

### Requirement: Automatic Bluetooth PIN Pairing Interception
The Android native plugin SHALL register an ordered `BroadcastReceiver` for `BluetoothDevice.ACTION_PAIRING_REQUEST` with high priority to automatically supply the PIN code and confirm pairing when connecting to a target printer.

#### Scenario: Automatic pairing with default PIN 0000
- **GIVEN** an unbonded Bluetooth printer with MAC address matching the current target
- **WHEN** Android broadcasts an `ACTION_PAIRING_REQUEST` with variant `PAIRING_VARIANT_PIN`
- **THEN** the plugin SHALL invoke `device.setPin` with the configured PIN (`0000`), confirm pairing, and call `abortBroadcast` to prevent the system pairing dialog.

#### Scenario: Non-matching Bluetooth device request
- **GIVEN** a pairing request broadcast for a Bluetooth device whose MAC address does not match the target printer
- **WHEN** the plugin receives the `ACTION_PAIRING_REQUEST`
- **THEN** the plugin SHALL ignore the event and allow the OS to process it normally.

### Requirement: PIN Authentication Fallback
The Android native plugin SHALL support fallback PIN pairing if authentication fails with the primary PIN.

#### Scenario: Fallback to alternate PIN on authentication failure
- **GIVEN** a primary PIN attempt fails resulting in unbonding with auth failure
- **WHEN** a subsequent pairing request is received for the same target device
- **THEN** the plugin SHALL attempt pairing with the configured `fallbackPin` (`1234`).
