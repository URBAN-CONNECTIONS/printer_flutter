# Spec Delta

## Purpose

Lets an app print a whole job with a single call that opens the connection, sends the job, optionally confirms completion and closes, so that quick successive prints never close each other's connection.

## ADDED Requirements

### Requirement: Atomic print call
`printPdf`, `sendTspl` and `sendRawBytes` SHALL accept an optional `macAddress` (and optionally `pin`, `fallbackPin`, `connectTimeout`). With `macAddress`, the plugin SHALL open the connection, send the job, wait for the completion confirmation and close the connection inside that one call, and return `Success`.

#### Scenario: Atomic call completes a job
- **GIVEN** a printer reachable at the given `macAddress`
- **WHEN** `printPdf`, `sendTspl` or `sendRawBytes` is called with `macAddress`
- **THEN** the connection is opened, the job is sent, the completion confirmation is awaited and the connection is closed within that call
- **AND** the call returns `Success` as before

#### Scenario: Calls without macAddress are unchanged
- **WHEN** the same calls are made without `macAddress`
- **THEN** they behave exactly as today
- **AND** the three-step API (`openPort`, send, `closePort`) is unchanged

### Requirement: First-in-first-out queue and takeover
Atomic calls SHALL run one at a time in arrival order, each on its own connection. An atomic call SHALL close a connection left open by hand with `openPort` and take over.

#### Scenario: Atomic call waits for a running call
- **WHEN** an atomic call arrives while another call is running
- **THEN** it waits in a first-in-first-out queue
- **AND** it runs afterwards on its own connection

#### Scenario: Atomic call takes over a manual connection
- **GIVEN** a connection was left open by hand with `openPort`
- **WHEN** an atomic call arrives
- **THEN** the plugin closes that connection and the atomic call takes over

### Requirement: Cancel affects only the running call
`cancel()` SHALL end only the atomic call that is running with `PrinterErrorType.cancelled`; calls waiting in the queue SHALL still run. A printer may refuse connections for a moment after a cancelled call closed its link, so an atomic call whose connection fails within 5 s of a cancelled call ending SHALL retry the connection about every 500 ms until that window ends, then fail with `PrinterErrorType.notConnected`. A `cancel()` during the wait SHALL end that call with `PrinterErrorType.cancelled`. Without a recently cancelled call the connection SHALL be attempted once.

#### Scenario: Cancel during an atomic call
- **GIVEN** an atomic call is running and others wait in the queue
- **WHEN** `cancel()` is called
- **THEN** only the running call ends with `PrinterErrorType.cancelled`
- **AND** the calls waiting in the queue still run

#### Scenario: Queued call connects right after a cancelled one
- **GIVEN** an atomic call is cancelled while another waits in the queue, and the printer refuses connections for a moment after the cancel
- **WHEN** the queued call starts
- **THEN** it retries the connection and completes with `Success`
- **AND** it does not fail with `PrinterErrorType.notConnected` because of the cancelled call

### Requirement: Cancel aimed at one call never hits the next
`cancel()` SHALL act on the call that was running when it was called; if that call ended and the next queued call started in the meantime, the next call SHALL NOT be cancelled.

#### Scenario: Cancel arrives as a call ends
- **GIVEN** an atomic call is running and another waits in the queue
- **WHEN** `cancel()` is called at the moment the running call ends and the queued call starts
- **THEN** the queued call is not cancelled and completes with `Success`

### Requirement: Optional completion confirmation
`confirmCompletion` SHALL default to `true`. With `true` the call waits for the completion confirmation within `completionTimeout`. With `false` it SHALL return `Success` once every byte was sent and the connection is closed, without waiting for the confirmation.

#### Scenario: confirmCompletion is false
- **WHEN** `confirmCompletion` is `false`
- **THEN** the call returns `Success` once every byte was sent and the connection is closed
- **AND** it does not wait for the completion confirmation

#### Scenario: confirmCompletion is true
- **WHEN** `confirmCompletion` is `true` (the default)
- **THEN** the call waits for the completion confirmation within `completionTimeout`, as `closePort: true` does today

#### Scenario: Documentation states what the confirmation means
- **WHEN** a developer reads the README, the `///` comments or `PrinterLimits.completionTimeout`
- **THEN** they say the confirmation means the printer processed everything it was sent, not that it finished printing
- **AND** none of them says the confirmation arrives after the print is physically finished

#### Scenario: Three-step API without confirmation keeps the grace delay
- **GIVEN** the three-step API with `closePort: true` and `confirmCompletion: false`
- **WHEN** the job has been sent
- **THEN** the existing grace delay runs before the connection is closed

### Requirement: Failure behaviour of atomic calls
An atomic call SHALL leave nothing open on any path and SHALL report typed errors.

#### Scenario: Connection cannot be opened
- **WHEN** the connection cannot be opened
- **THEN** the call fails with `PrinterErrorType.notConnected`
- **AND** nothing is left open

#### Scenario: No confirmation in time
- **GIVEN** every byte was sent
- **WHEN** no confirmation arrives within `completionTimeout`
- **THEN** the call fails with `PrinterErrorType.timeout`
- **AND** the documentation states this means "not confirmed", not "it did not print"

#### Scenario: Failure or cancel closes the connection
- **WHEN** an atomic call fails or is cancelled
- **THEN** the connection is closed on that path

#### Scenario: macAddress with closePort
- **WHEN** an atomic call also receives `closePort`
- **THEN** the call still closes at the end and `closePort` has no effect

### Requirement: Pairing PINs are read consistently
The pairing receiver SHALL use one consistent set of target address, PIN and fallback PIN for a pairing attempt, even when a queued atomic call starts and replaces them at the same moment.

#### Scenario: Next queued call starts during a pairing failure
- **GIVEN** a pairing attempt of one atomic call fails with an authentication error
- **WHEN** the next queued atomic call starts and replaces the stored PIN and fallback PIN at that moment
- **THEN** the receiver does not crash and does not mix the PIN of one call with the fallback of another

### Requirement: Phase timing log
When an atomic call ends, in success or failure, the Android log SHALL have one line with the milliseconds of each phase: connect, render, send and confirm. The API SHALL NOT return the times to Dart, and the line SHALL NOT contain device MAC addresses.

#### Scenario: Phase log line
- **WHEN** an atomic call ends, in success or failure
- **THEN** the Android log has one line with the milliseconds of connect, render, send and confirm

### Requirement: Existing paper widths keep working
Atomic calls and `confirmCompletion` SHALL NOT change defaults of the existing calls; 58 mm, 72 mm, 80 mm and custom paper widths SHALL keep working.

#### Scenario: Defaults unchanged
- **WHEN** an existing call is made without the new arguments
- **THEN** its behaviour and defaults are the same as before this change
