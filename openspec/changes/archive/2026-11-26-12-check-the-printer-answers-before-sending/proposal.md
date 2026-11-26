# Proposal

## Why

On the RT-320 PB a printer stuck in `Downloading...` or "No paper" still accepts the Bluetooth connection, then takes no data, and the job fails only after 20 to 60 s. A printer that does not answer should fail in about 2 seconds, with a message that tells the person to turn it off and on, and no part of the job should reach it.

## What Changes

- A probe (`OUT "READY"`) sent before the job; the plugin waits for the answer before sending any byte of the job.
- On by default in the atomic call and can be turned off; opt-in in the three-step API.
- New `PrinterErrorType.printerNotReady`, mapped from the native code `PRINTER_NOT_READY`.
- After a job with `confirmCompletion: false`, the probe on that printer waits up to `completionTimeout`.
- README, CHANGELOG and GLOSSARY entries.

## Capabilities

### New Capabilities

- `printer-probe`: checking that the printer answers before any byte of a job is sent.

### Modified Capabilities

None.

## Non-goals

- `<ESC>!?` and the other immediate commands.
- An error type per reason, or a `reason` field.

## Impact

- Android: `PrinterConnection.kt`, `PrinterSession.kt`, `PrinterFlutterPlugin.kt` and their JVM tests.
- Dart: the print calls, platform interface, method channel and `PrinterErrorType`.
- Public API: one new optional argument and one new enum value. An exhaustive `switch` over `PrinterErrorType` in an app needs the new case.
- Behaviour: atomic calls now probe by default.
