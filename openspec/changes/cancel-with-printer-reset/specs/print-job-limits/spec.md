## MODIFIED Requirements

### Requirement: Cancel
The plugin SHALL expose `cancel()`, which aborts the job in progress, with an optional `resetPrinter` flag that resets the printer before the connection is closed.

#### Scenario: Cancel during a job
- **WHEN** `cancel()` is called while a job is running
- **THEN** that job SHALL fail with `cancelled` and the next job SHALL run on a new connection

#### Scenario: Cancel with nothing running
- **WHEN** `cancel()` is called while no call is running
- **THEN** it SHALL do nothing and the open connection SHALL stay usable

#### Scenario: Cancel while the printer resets
- **WHEN** `cancel()` is called during `openPort` while the plugin waits for the printer after the reset
- **THEN** `openPort` SHALL end with `cancelled` and SHALL NOT connect

#### Scenario: Cancel with printer reset
- **WHEN** `cancel(resetPrinter: true)` is called while a job is sending data
- **THEN** the plugin SHALL stop sending at the next chunk, write `<ESC>!R` on the open connection, close it and end the job with `cancelled`

#### Scenario: Cancel with printer reset and a blocked write
- **WHEN** `cancel(resetPrinter: true)` is called and the job does not reach a stop point within 5 s
- **THEN** the plugin SHALL close the connection and end the job with `cancelled` without sending the reset

#### Scenario: Next open after a reset sent by cancel
- **WHEN** `openPort` is called after a cancel that sent the reset
- **THEN** the plugin SHALL wait for the printer to come back before connecting and SHALL NOT send the reset again
