# print-job-limits Specification

## Purpose
Guarantee that no printer call can wait forever, so a hung, cancelled or failed job never leaves the phone unable to print.

## Requirements

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
The plugin SHALL expose `cancel()`, which aborts the job in progress without cutting a block (one `BITMAP` band or one `DOWNLOAD` file with its data) in the middle, and SHALL NOT block the main thread.

#### Scenario: Cancel during a job
- **WHEN** `cancel()` is called while a job is running
- **THEN** that job SHALL fail with `cancelled` and the next job SHALL run on a new connection

#### Scenario: Cancel with nothing running
- **WHEN** `cancel()` is called while no call is running
- **THEN** it SHALL do nothing and the open connection SHALL stay usable

#### Scenario: Cancel while the printer resets
- **WHEN** `cancel()` is called during `openPort` while the plugin waits for the printer after the reset
- **THEN** `openPort` SHALL end with `cancelled` and SHALL NOT connect

#### Scenario: Cancel while a block is being sent
- **WHEN** `cancel()` is called while a block is being sent
- **THEN** the plugin SHALL finish sending that block, send no `PRINT` and nothing after it, close the connection, and the call SHALL end with `PrinterErrorCode.cancelled`

#### Scenario: Cancel between blocks, while connecting or while waiting for confirmation
- **WHEN** `cancel()` is called between blocks, while connecting, or while waiting for the completion confirmation
- **THEN** the connection SHALL close at once and the call SHALL end with `PrinterErrorCode.cancelled`

#### Scenario: Cancel does not block the main thread
- **WHEN** `cancel()` is called
- **THEN** the main thread SHALL NOT be blocked: closing the socket runs on the watchdog thread

#### Scenario: The block in progress cannot finish
- **WHEN** the printer stops taking data while the block in progress is being finished
- **THEN** the connection SHALL close when `writeTimeout` runs out and the error message SHALL say the printer may need to be turned off and on

#### Scenario: Connection lost in the middle of a block
- **WHEN** the connection is lost in the middle of a block
- **THEN** the call SHALL fail as a lost connection, and the README SHALL say to turn the printer off and on if the next job fails

#### Scenario: Cancel while connecting
- **WHEN** `cancel()` is called while the connection to the printer is being opened and the connect is blocked
- **THEN** the connection SHALL close at once, the connect SHALL end before its limit runs out, and the call SHALL end with `PrinterErrorCode.cancelled` (not `notConnected` or `timeout`)

### Requirement: Optional printer reset after a failure
When `openPort` is called with `resetPrinterAfterFailure: true` and the previous job hung, was cancelled or failed, the plugin SHALL send `<ESC>!R`, wait for the printer and then connect.

#### Scenario: Reset after a failed job
- **WHEN** the previous job failed and `resetPrinterAfterFailure` is true
- **THEN** the plugin SHALL reset the printer before connecting

#### Scenario: Normal job
- **WHEN** the previous job succeeded, or `resetPrinterAfterFailure` is false
- **THEN** the plugin SHALL NOT reset the printer
