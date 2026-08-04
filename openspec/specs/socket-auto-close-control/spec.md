# socket-auto-close-control Specification

## Purpose
TBD - created by archiving change add-socket-auto-close-control. Update Purpose after archive.
## Requirements
### Requirement: Optional Automatic Port Closure
The Flutter plugin SHALL support an optional `closePort` boolean parameter across print API functions (`sendTspl`, `sendRawBytes`, `printPdf`).

#### Scenario: Transmitting print command with closePort set to true
- **WHEN** `printPdf`, `sendTspl`, or `sendRawBytes` is invoked with `closePort: true`
- **THEN** the native Android plugin SHALL transmit the print payload, append a TSPL `OUT` marker, await `OUT` completion on a background thread, close the port, and only then return success to Flutter.

#### Scenario: Transmitting print command with closePort set to false
- **WHEN** `printPdf`, `sendTspl`, or `sendRawBytes` is invoked with `closePort: false` (or default value)
- **THEN** the native Android plugin SHALL transmit the print payload and leave the Bluetooth socket connection open.

