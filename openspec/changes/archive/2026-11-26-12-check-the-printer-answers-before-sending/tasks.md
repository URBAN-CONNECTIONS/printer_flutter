# Tasks

## 1. Android probe

- [x] 1.1 Add `PrinterErrorCode.PRINTER_NOT_READY` and the probe in `PrinterConnection.kt` (`OUT "READY"`, one limit for write and answer, timeout becomes not ready with the message; cancel and connection loss unchanged); verify with JVM tests using a fake transport: answer, no answer, blocked write, cancel
- [x] 1.2 Run the probe in `PrinterSession.kt` before the job of `runAtomic` and, when asked, of `runJob`, with the 2 s limit or `completionTimeoutMs` after an unconfirmed job on that printer; verify with JVM tests: no job byte without answer, connection closed, probe off, per-printer mark set and cleared
- [x] 1.3 Read the `probe` argument in `PrinterFlutterPlugin.kt` (default true for atomic calls, false otherwise) for `sendTspl`, `sendRawBytes` and `printPdf`; verify the existing JVM tests still pass

## 2. Dart API

- [x] 2.1 Add `PrinterErrorType.printerNotReady` and map `PRINTER_NOT_READY` in `typeFromCode`; verify a Dart test covers it and an unknown code still maps to `unknown`
- [x] 2.2 Add `bool? probe` with `///` comments to `sendTspl`, `sendRawBytes` and `printPdf` in `lib/printer_flutter.dart`, the platform interface and the method channel (sent only when set); verify channel and API tests for set and unset, and update fakes and the example so they compile
- [x] 2.3 Format the changed Dart files with `dart format`; verify `flutter analyze` and `flutter test` pass

## 3. Documentation

- [x] 3.1 Document the probe and `printerNotReady` in `README.md` and in `CHANGELOG.md` under "Unreleased"; verify the documented argument names match the code
- [x] 3.2 Write the documentation of "Documentation from the issue" in `design.md` into `GLOSSARY.md` exactly as given; verify only that entry is added

## 4. Review corrections [Iteration 1]

- [x] 4.1 Test: pin the default 2 s limit of the probe: build the session without passing probeTimeoutMs so the production default is used and assert the value is 2000 ms, so that a 3 s or 5 s default fails the test
