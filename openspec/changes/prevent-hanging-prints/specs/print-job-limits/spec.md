## Purpose

Guarantee that no printer call can wait forever, so a hung, cancelled or failed job never leaves the phone unable to print.

## ADDED Requirements

### Requirement: Bounded printer calls
Every printer call SHALL end with success or a typed error within its limits: `connectTimeout` (default 15 s), `writeTimeout` per chunk (default 20 s) and `completionTimeout` from the last byte sent (default 60 s), each configurable per call.

#### Scenario: Printer turned off during a job
- **WHEN** the printer is turned off or goes out of range while a job is sent or awaited
- **THEN** the call SHALL fail with `connectionLost` or `timeout` within its limit
- **THEN** the next call SHALL run instead of waiting behind the failed one

#### Scenario: Printer stops accepting data
- **WHEN** a write does not complete within `writeTimeout`
- **THEN** the plugin SHALL close the connection and fail the call with `timeout`

### Requirement: Typed errors
Failures SHALL be reported as `PrinterException` with type `notConnected`, `connectionLost`, `timeout`, `cancelled` or `unknown`.

#### Scenario: No connection
- **WHEN** a print call is made with no open connection
- **THEN** it SHALL fail with `notConnected`

### Requirement: Cancel
The plugin SHALL expose `cancel()`, which aborts the job in progress.

#### Scenario: Cancel during a job
- **WHEN** `cancel()` is called while a job is running
- **THEN** that job SHALL fail with `cancelled` and the next job SHALL run on a new connection

### Requirement: Optional printer reset after a failure
When `openPort` is called with `resetPrinterAfterFailure: true` and the previous job hung, was cancelled or failed, the plugin SHALL send `<ESC>!R`, wait for the printer and then connect.

#### Scenario: Reset after a failed job
- **WHEN** the previous job failed and `resetPrinterAfterFailure` is true
- **THEN** the plugin SHALL reset the printer before connecting

#### Scenario: Normal job
- **WHEN** the previous job succeeded, or `resetPrinterAfterFailure` is false
- **THEN** the plugin SHALL NOT reset the printer
