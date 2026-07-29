# runtime-permissions

## Requirements

### Requirement: Native Runtime Permission Request
The Android native plugin SHALL implement runtime permission requests for Bluetooth and Location permissions required to connect to thermal printers.

#### Scenario: Requesting permissions on Android 12+
- **GIVEN** an Android 12+ device (API level >= 31)
- **WHEN** Flutter invokes `requestPermissions` over `MethodChannel`
- **THEN** the plugin SHALL request `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` permissions and return true if granted.

#### Scenario: Requesting permissions on Android < 12
- **GIVEN** an Android device with API level < 31
- **WHEN** Flutter invokes `requestPermissions` over `MethodChannel`
- **THEN** the plugin SHALL request `ACCESS_FINE_LOCATION` permission and return true if granted.

#### Scenario: Handling user denial
- **GIVEN** a permission request dialog
- **WHEN** the user denies any required permission
- **THEN** the plugin SHALL return false to Flutter over `MethodChannel`.
