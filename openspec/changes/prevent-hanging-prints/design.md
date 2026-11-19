## Context

The TSC SDK's `sendcommand_getstring` loops until it reads `ENDLINE\r\n`. Its reader returns `false` on any socket error and the caller loops again, so a dead socket never ends the wait; `Thread.sleep` interruptions are swallowed too. It also builds the answer from the whole 1024-byte buffer instead of the bytes read, so an `ENDLINE` split across two reads is never found. Jobs run on a single-thread executor, so one stuck call blocks every later call until the process dies.

## Goals / Non-Goals

**Goals:** every call ends with success or a typed error within its limit; a hung, cancelled or failed job never blocks the next one; ticket rendering does not change.

**Non-Goals:** see the proposal.

## Decisions

### The plugin owns the Bluetooth socket
It uses the same Android calls the SDK used (`getRemoteDevice`, `createRfcommSocketToServiceRecord` with the SPP UUID, `cancelDiscovery`, `connect`). Discarded: reading the SDK's private stream by reflection (fragile, and its writes still swallow errors); abandoning the stuck thread and creating a new SDK instance (leaves live threads, and the SDK shares static buffers between instances).

### A watchdog closes the socket when a step runs past its limit
Android sockets have no read or write timeout, but closing a socket unblocks any thread blocked on it. Each connect and each 4 KB write chunk is guarded by a task on a scheduled executor that closes the transport at the limit. The wait for the marker polls `available()` every 50 ms and only reads available bytes, so it never blocks and checks its deadline each round.

### The completion request keeps the SDK's bytes
`OUT "COMPLETED\r\n"`, a blank line and `OUT "ENDLINE"` are sent exactly as before, and the plugin waits for `ENDLINE` in the bytes actually read.

### Cancel runs on the main thread
`cancel()` closes the current connection directly instead of queuing behind the job it must stop.

### Printer reset is opt-in
`<ESC>!R` drops the Bluetooth link and takes a few seconds, so it runs only after a failed job and only when the caller asks. It waits 4 s for the printer before reconnecting.

## Flow

```
Flutter            Plugin (main)        Job thread                 Watchdog        Printer
  | printPdf  -------->| queue ------------>|                           |              |
  |                    |                    | write chunk (guarded) ----|------------->|
  |                    |                    | OUT ENDLINE request ------|------------->|
  |                    |                    | poll available() <--------|--- ENDLINE --|
  |<---------- Success-|<-------------------| close socket              |              |
  |                    |                    |                           |              |
  | (printer stops answering)               | poll ... deadline passed  |              |
  |<-- PrinterException(timeout) ----------| close socket              |              |
  |                    |                    |                           |              |
  | cancel  ---------->| connection.cancel()-> socket closed           |              |
  |<-- PrinterException(cancelled) --------| (blocked call unblocks)   |              |
```

## Risks / Trade-offs

- [The 60 s default may be short for long tickets] → it is configurable per call; apps should set it to twice the worst case measured on their printer.
- [A late answer after a timeout] → the app sees an error and a retry may print a duplicate; accepted by the apps over a phone that cannot print.
- [`<ESC>!R` unsupported on some TSPL clones] → off by default.
