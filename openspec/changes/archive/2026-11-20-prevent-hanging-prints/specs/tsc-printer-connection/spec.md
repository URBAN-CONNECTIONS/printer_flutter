## REMOVED Requirements

### Requirement: Android Library Integration
**Reason**: The TSC SDK waited for the printer with no limit and could not be aborted, leaving phones unable to print.
**Migration**: None for callers; the plugin opens the Bluetooth socket itself.

## MODIFIED Requirements

### Requirement: Native TSC Connection and Command Execution
The Android native plugin SHALL expose MethodChannel endpoints to open a Bluetooth Classic SPP connection to a printer, transmit TSPL commands and close the port, without the TSC SDK.

#### Scenario: Opening Bluetooth printer port with PIN configuration
- **GIVEN** a valid Bluetooth MAC address and optional `pin` and `fallbackPin` arguments
- **WHEN** Flutter invokes `openPort` with the MAC address over `MethodChannel`
- **THEN** the native Android plugin SHALL update the target MAC address and active PIN configuration, open an SPP socket to the device and return success

#### Scenario: Opening fails
- **WHEN** the connection cannot be opened within the connect limit
- **THEN** `openPort` SHALL fail with a `notConnected` error instead of returning a status string

#### Scenario: Transmitting TSPL printing commands
- **GIVEN** an active connection to a printer
- **WHEN** Flutter invokes `sendTspl` with a string command over `MethodChannel`
- **THEN** the native Android plugin SHALL write the command to the printer

#### Scenario: Closing printer port
- **GIVEN** an open printer port
- **WHEN** Flutter invokes `closePort` over `MethodChannel`
- **THEN** the native Android plugin SHALL close the socket and release its resources
