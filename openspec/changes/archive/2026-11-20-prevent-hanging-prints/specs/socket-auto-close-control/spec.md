## MODIFIED Requirements

### Requirement: Optional Automatic Port Closure
The Flutter plugin SHALL support an optional `closePort` boolean parameter across print API functions (`sendTspl`, `sendRawBytes`, `printPdf`). When it is true, the wait for the printer's completion answer SHALL be bounded by the call's completion limit.

#### Scenario: Transmitting print command with closePort set to true
- **WHEN** `printPdf`, `sendTspl`, or `sendRawBytes` is invoked with `closePort: true`
- **THEN** the native Android plugin SHALL transmit the print payload, append a TSPL `OUT` marker, await the marker on a background thread for at most the completion limit, close the port, and only then return success to Flutter

#### Scenario: Printer does not answer the marker
- **WHEN** the printer does not answer the `OUT` marker within the completion limit
- **THEN** the plugin SHALL close the port and fail the call with a `timeout` error

#### Scenario: Marker split across reads
- **WHEN** the printer's answer arrives in several reads
- **THEN** the plugin SHALL still recognize the marker

#### Scenario: Transmitting print command with closePort set to false
- **WHEN** `printPdf`, `sendTspl`, or `sendRawBytes` is invoked with `closePort: false` (or default value)
- **THEN** the native Android plugin SHALL transmit the print payload and leave the Bluetooth socket connection open
