# Tasks

## 1. Native session: atomic job, queue semantics and phase timing

- [x] 1.1 Add a plain Kotlin `PhaseTimer` (injectable clock) that records connect, render, send and confirm milliseconds and formats one log line with no MAC address or PIN; verify with a JVM unit test of the formatted line.
- [x] 1.2 Add `PrinterSession.runAtomic` (takeover of a manual connection, fresh connection, send, optional completion confirmation, close on every path, cancel scoped to the running call, phases recorded); verify with `PrinterSessionTest` cases using a fake transport: success, `confirmCompletion` true waits for the marker, false skips it, connect failure leaves nothing open, timeout after send, cancel, failure closes, and takeover closes a manual connection.
- [x] 1.3 Add `confirmCompletion` (default `true`) to `PrinterSession.runJob`; with `closePort` and `false`, sleep `CLOSE_GRACE_MS` then close; verify with a `PrinterSessionTest` case using a recording sleeper, and that the default path is unchanged.
- [x] 1.4 Verify FIFO order and that a cancel only ends the running call while queued calls still run, with a JVM test that runs atomic calls through a single-thread executor and a fake transport.

## 2. Plugin wiring

- [x] 2.1 In `PrinterFlutterPlugin.onMethodCall`, make `printPdf`, `sendTspl` and `sendRawBytes` take the atomic path when `macAddress` is present (reading `macAddress`, `pin`, `fallbackPin`, `connectTimeoutMs`, `confirmCompletion`) and the existing path otherwise; keep pairing PIN handling as in `openPort`, run on the job executor, and log the `PhaseTimer` line when the call ends; verify `./gradlew test` in `android/` (or the project's Kotlin test command) passes and describe the manual check of the log line.
- [x] 2.2 Measure the render phase inside the `printPdf` job (a `PhaseTimer` passed into the send lambda); verify the line shows a render value for `printPdf` and zero for TSPL/raw in the timer test.

## 3. Dart API

- [x] 3.1 Add optional `macAddress`, `pin`, `fallbackPin`, `connectTimeout` and `confirmCompletion` to `printPdf`, `sendTspl` and `sendRawBytes` in `lib/printer_flutter.dart`, `lib/printer_flutter_platform_interface.dart` and `lib/printer_flutter_method_channel.dart`, with `///` comments (including the "not confirmed" meaning of a timeout, D16), sending the new keys only when set; verify `flutter analyze` is clean.
- [x] 3.2 Add channel tests in `test/` for the new arguments (atomic map keys, defaults omitted when unset, `confirmCompletion` false, `closePort` unaffected) and that calls without `macAddress` send the same map as before; verify `flutter test` passes.

## 4. Documentation

- [x] 4.1 Document the atomic call, `confirmCompletion`, the queue/takeover/"do not mix the two styles" rule, the meaning of the completion confirmation and "not confirmed", and the phase log in `README.md`; add an entry under "Unreleased" in `CHANGELOG.md`; verify the README example compiles against the new signatures (`flutter analyze`).
- [x] 4.2 Write the documentation of the issue exactly as given: create `GLOSSARY.md` with a `# Glossary` heading and the four entries (Job, Atomic call, Completion confirmation, Not confirmed), and the ADR `docs/adr/0002-printing-is-done-with-an-atomic-native-call.md` with the given text; verify both files match the issue text.
- [x] 4.3 Run `dart format` on the changed Dart files, `flutter analyze` and `flutter test`; verify all pass with no formatting diff.

## 5. Review corrections [Iteration 1]

- [x] 5.1 Test: serialize the busy check and flag write of PrinterSession.cancel against the start and end of a call (lock or per-call token) so a cancel aimed at one call never cancels the next queued call; verify with a PrinterSessionTest that forces the boundary with a fake transport and a controlled thread handoff and fails without the fix
- [x] 5.2 In PrinterFlutterPlugin's pairing receiver read targetMacAddress, currentPin and currentFallbackPin once into local vals per event (no `!!` on a field another thread can change), keep the existing behaviour otherwise, and say how it was verified since it needs the Android SDK (read-through plus the manual pairing check on the RT-320 PB) (device only)
- [x] 5.3 Fix the D16 documentation: in lib/printer_flutter.dart printPdf `///` say the printer must confirm it processed everything it was sent (not that it printed it), and in README.md drop 'includes printing every copy' from the completionTimeout row; verify with grep that no README, `///` or PrinterLimits text says the confirmation means printing finished
- [x] 5.4 Test: add a Dart test that reads lib/printer_flutter.dart and README.md and fails when they say the confirmation arrives after printing (for example the phrases 'printed everything' or 'includes printing every copy'); verify flutter test passes
