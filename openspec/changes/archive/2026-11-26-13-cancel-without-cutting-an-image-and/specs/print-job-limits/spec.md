## MODIFIED Requirements

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
