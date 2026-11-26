# Proposal

## Why

Printing is three separate calls (`openPort`, a print call, `closePort`) over one global connection, so two quick taps can close each other's connection. Printers that never answer the completion marker print but report a timeout, and nobody can see which phase of a job takes the time.

## What Changes

- `printPdf`, `sendTspl` and `sendRawBytes` gain an optional `macAddress` (plus `pin`, `fallbackPin`, `connectTimeout`). With it, one call opens the connection, sends the job, confirms completion and closes (an atomic call).
- Atomic calls run first-in-first-out, each on its own connection; an atomic call closes a connection left open by hand; `cancel()` ends only the running call.
- New opt-in `confirmCompletion` (default `true`); `false` returns `Success` after every byte was sent and the connection is closed.
- Every atomic call writes one Android log line with the milliseconds of connect, render, send and confirm.
- Calls without `macAddress` and the three-step API behave as today.
- Documentation: README, `///` comments, `CHANGELOG.md` (Unreleased), `GLOSSARY.md` entries and one ADR.

## Capabilities

### New Capabilities
- `atomic-print-call`: one-call print jobs with an optional `macAddress`, queueing, takeover, scoped cancel, optional completion confirmation and per-phase timing log.

### Modified Capabilities

## Impact

- Dart: `lib/printer_flutter.dart`, `lib/printer_flutter_platform_interface.dart`, `lib/printer_flutter_method_channel.dart`, README, CHANGELOG, tests in `test/`.
- Kotlin: `PrinterSession.kt`, `PrinterFlutterPlugin.kt` and a new plain Kotlin queue/timing class, tests in `android/src/test/`.
- Docs: `GLOSSARY.md`, `docs/adr/`.
- No new dependencies, no version bump (0.2.0 candidate; the release bumps it).

## Non-goals

- Speed work (a later change that uses the phase log).
- The printer check before sending, safe cancel and `dramBatch` hardening (separate issues).
- Returning phase times to Dart, new method names, or adding `EOJ`.
- Changing defaults of the existing calls or label sizes.
