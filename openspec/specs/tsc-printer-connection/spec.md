# tsc-printer-connection

## Requirements

### Requirement: Android Library Integration
The Android platform implementation SHALL compile and package `tscsdk.jar` from `android/libs/tscsdk.jar`.

#### Scenario: Gradle dependency resolution
- **GIVEN** `tscsdk.jar` is placed in `android/libs/`
- **WHEN** the Android library project is compiled via Gradle
- **THEN** classes under `com.example.tscdll` SHALL be available on the compilation classpath.

### Requirement: Native TSC Connection and Command Execution
The Android native plugin SHALL expose MethodChannel endpoints to open connections, transmit TSPL commands, and close ports using `TSCActivity`, `TSCUSBActivity`, and `TscWifiActivity`.

#### Scenario: Opening Bluetooth printer port
- **GIVEN** a valid Bluetooth MAC address
- **WHEN** Flutter invokes `openPort` with the MAC address over `MethodChannel`
- **THEN** the native Android plugin SHALL delegate to `TSCActivity.openport` and return the connection status result.

#### Scenario: Transmitting TSPL printing commands
- **GIVEN** an active connection to a TSC printer
- **WHEN** Flutter invokes `sendTspl` with a string command over `MethodChannel`
- **THEN** the native Android plugin SHALL execute `TSCActivity.sendcommand` to transmit data to the hardware printer.

#### Scenario: Closing printer port
- **GIVEN** an open printer port
- **WHEN** Flutter invokes `closePort` over `MethodChannel`
- **THEN** the native Android plugin SHALL invoke `TSCActivity.closeport` to release hardware socket resources.
