# Design

## Context

`PrinterSession.runAtomic` opens a connection, sends and optionally confirms; `runJob` sends on the connection of `openPort`. Both run one at a time on the job thread. `PrinterConnection.awaitMarker` waits for any marker within a limit, closes the connection when it expires and honours `cancel`. `PrinterException.typeFromCode` maps native codes in Dart.

## Goals / Non-Goals

**Goals:** the probe flow in plain Kotlin, tested on the JVM with a fake transport; one optional argument in Dart.

**Non-Goals:** immediate commands; error types per reason.

## Decisions

- The probe writes `OUT "READY"\r\n` and calls `awaitMarker("READY", ...)`. One limit covers write and answer: the write is given the limit, the wait what is left of it.
- A `TIMEOUT` from either step becomes `PRINTER_NOT_READY` (new `PrinterErrorCode`). `CANCELLED` and `CONNECTION_LOST` are reported as they are. The expired limit already closes the connection.
- The Dart argument is `bool? probe` on `sendTspl`, `sendRawBytes` and `printPdf`, sent over the channel only when set. Kotlin resolves an absent value: `true` for an atomic call, `false` otherwise.
- In the three-step API the probe runs inside the print call (`runJob`), before its job, not inside `openPort`.
- `PrinterSession` keeps the set of printers (upper-case MAC) whose last job was sent with `confirmCompletion` false. For those the limit is `completionTimeoutMs`.

```
call -> session: connect
session -> printer: OUT "READY"
alt answer in time
  session -> printer: job, completion request
else
  session -> printer: close
  session -> call: PRINTER_NOT_READY
end
```

## Risks / Trade-offs

- A healthy printer slower than 2 s would be rejected → the caller can turn the probe off; verified on the device in review.
- The set of unconfirmed printers lives in memory, so after an app restart the first probe waits 2 s → the printer may still be busy then and the call can be repeated.

## Assumptions

- "A job ended with `confirmCompletion: false`" means every byte was sent with that value, even if the close grace wait was then cancelled. The mark of a printer is cleared when it answers a probe or confirms a job; a call that fails at the probe keeps it.
- The probe time counts in the `connect` phase of the timing log, which keeps its four phases.
- The error message is in Spanish, like the other native messages.
- With the probe on, each three-step print call probes, including several sends on one open connection.
## From the issue: Decisions

### D6. The probe uses `OUT`, on by default only in the atomic call
Decided: the probe is `OUT "READY"` with a 2 second limit; on by default in the atomic call (new API) and opt-in in the three-step API.
Why: `OUT` is verified on the RT-320 PB, while immediate commands are not; connecting does not prove the printer is healthy (it connects in 2 to 5 s even when stuck).
Discarded: `<ESC>!?` (immediate command, likely not answered over Bluetooth); on by default in the three-step API (changes existing behaviour).

### D10. What the RT-320 PB supports over Bluetooth
Decided: `OUT` works; `<ESC>!R` was tested and does nothing over Bluetooth; `<ESC>!?` is not tested and is not relied upon.
Why: the tests in #1, and the TSC manual lists immediate commands only for RS-232, USB and Ethernet.
Discarded: relying on the TSC manual for a Ribetec printer.

### D12. One new error type
Decided: add only `PrinterErrorType.printerNotReady`, with the reason in `message`.
Why: public API change; one type is enough for now.
Discarded: one type per state.

## From the issue: Constraints

- C2: Sockets and streams are closed on every path, and the main thread is never blocked.
- C3: The probe flow is plain Kotlin tested on the JVM with a fake transport; the new type and option are tested in Dart.
- C5: No immediate command (`<ESC>!R`, `<ESC>!?`) is relied upon over Bluetooth.
- C6: No byte of the job is sent before the printer answered the probe.

## From the issue: Code pointers

- `android/src/main/kotlin/com/urbanconnections/printer_flutter/PrinterConnection.kt`: `PrinterErrorCode` and `awaitMarker`, which can wait for any marker.
- `lib/printer_flutter.dart`: `PrinterErrorType` and `PrinterException.typeFromCode`.
- `README.md` and `CHANGELOG.md`: document the probe and the new type under "Unreleased".

## From the issue: Verification

- On a healthy RT-320 PB, note how long the probe answer takes; it must be well under 2 seconds.
- Leave the RT-320 PB in `Downloading...` (cancel a `dramBatch` job with the current plugin) and check the next atomic call fails in about 2 seconds with `printerNotReady`.
- Remove the paper mid-print, put it back, and check the next call fails fast instead of after 22 s.

## From the issue: Assumptions

- A1: a healthy RT-320 PB answers the probe in well under 2 seconds.
- A2: a printer stuck in `Downloading...` or "No paper" does not answer the probe.

## Documentation from the issue

- Add this entry to `GLOSSARY.md`, under its existing heading, after the existing ones, exactly as written. It is vocabulary only; nothing else in that file changes.

**Probe**:
The `OUT` question sent right after connecting, to check that the printer answers before any byte of the job is sent.
_Avoid_: ping, status check
