## ADDED Requirements

### Requirement: Ensure Bluetooth is On (Dart API)
The plugin SHALL expose a method `ensureBluetoothIsOn()` that returns a `Future<bool>` indicating whether the Bluetooth adapter is enabled and ready to use.

#### Scenario: Method invocation
- **GIVEN** the `PrinterFlutter` plugin is initialized
- **WHEN** the developer calls `PrinterFlutter.instance.ensureBluetoothIsOn()`
- **THEN** it asynchronously returns `true` if Bluetooth is currently enabled, or `false` if it is disabled and could not be enabled.

### Requirement: Android Native Enablement Flow
The plugin SHALL natively check the Android `BluetoothAdapter` state and, if disabled, prompt the user to enable it using `BluetoothAdapter.ACTION_REQUEST_ENABLE`.

#### Scenario: Bluetooth is already on
- **GIVEN** an Android device with Bluetooth turned on
- **WHEN** `ensureBluetoothIsOn()` is invoked
- **THEN** the native code immediately returns `true` to Dart without prompting the user.

#### Scenario: Bluetooth is off and user accepts prompt
- **GIVEN** an Android device with Bluetooth turned off
- **WHEN** `ensureBluetoothIsOn()` is invoked
- **THEN** a system dialog appears asking to enable Bluetooth
- **WHEN** the user accepts the prompt (resulting in `RESULT_OK`)
- **THEN** the native code returns `true` to Dart.

#### Scenario: Bluetooth is off and user denies prompt
- **GIVEN** an Android device with Bluetooth turned off
- **WHEN** `ensureBluetoothIsOn()` is invoked
- **THEN** a system dialog appears asking to enable Bluetooth
- **WHEN** the user denies the prompt (resulting in `RESULT_CANCELED`)
- **THEN** the native code returns `false` to Dart.

### Requirement: iOS Native State Check
The plugin SHALL natively check the iOS `CBCentralManager` state and return whether it is powered on.

#### Scenario: Bluetooth is powered on
- **GIVEN** an iOS device with Bluetooth turned on
- **WHEN** `ensureBluetoothIsOn()` is invoked
- **THEN** the native code checks `CBCentralManager` state and returns `true`.

#### Scenario: Bluetooth is powered off
- **GIVEN** an iOS device with Bluetooth turned off
- **WHEN** `ensureBluetoothIsOn()` is invoked
- **THEN** the native code checks `CBCentralManager` state and returns `false` without programmatically prompting.
