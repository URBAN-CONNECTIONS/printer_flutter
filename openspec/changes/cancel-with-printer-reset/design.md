## Context

`cancel()` runs on the main thread and closes the socket, which unblocks a job stuck in a write or a read. Closing the socket in the middle of an image is what leaves some printers waiting for bytes. `<ESC>!R` is an immediate command: the printer should process it even while it waits for data. It is not confirmed that the Ribetec does so over Bluetooth (the TSC manual lists immediate commands for RS-232, USB and Ethernet), so the feature is opt-in and the manual test decides.

## Goals / Non-Goals

**Goals:** let an app cancel without leaving the printer waiting for an image; keep the guarantee that `cancel()` always ends the job quickly.
**Non-Goals:** see the proposal.

## Decisions

### Cooperative stop, then reset, then close
`cancel(resetPrinter: true)` does not close the socket itself. It leaves a pending reset on the `PrinterConnection` and the job thread, at its next chunk or its next poll of the marker, sends `<ESC>!R`, closes the connection and ends the job with `cancelled`. All writes stay on the job thread, so the reset never interleaves with a chunk being written.

### Hard stop
When `cancel(resetPrinter: true)` is called, the watchdog closes the connection after 5 s if the job has not reached a stop point (a write blocked on a printer that does not read). The job then ends with `cancelled` without the reset, as a plain `cancel()` does.

### The reset write has its own limit
The reset is written with a limit of 2 s; if it does not complete, the connection is closed.

### The next open waits, and does not reset twice
`<ESC>!R` restarts the printer and drops the Bluetooth link. When a reset was sent by cancel, the session waits the same settle time as the reset after a failure before the next `openPort` connects, and does not send the reset again even with `resetPrinterAfterFailure`.

## Flow

```
Flutter        Plugin (main)            Job thread                    Printer
  | cancel(reset) ->| pendingReset=true, start 5 s hard stop |
  |                 |                   | next chunk / poll:             |
  |                 |                   | sees pendingReset              |
  |                 |                   | write <ESC>!R (2 s limit) ---->| resets
  |                 |                   | close socket                   |
  |<-- PrinterException(cancelled) -----|                                |
  | openPort ------>| waits settle time, connects                        |
```

## Risks / Trade-offs

- [The Ribetec may not process `<ESC>!R` while it waits for image data over Bluetooth] → opt-in; to be checked with the guided test of the example app before apps enable it.
- [`<ESC>!R` deletes files downloaded to the printer] → opt-in, documented.
- [A long render between writes can pass the 5 s hard stop] → the connection is closed as with a plain `cancel()`.
