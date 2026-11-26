## 1. Native connection

- [x] 1.1 Add `PrinterConnection` with guarded connect and chunked writes, bounded marker wait and cancel
- [x] 1.2 Add `BluetoothSocketTransport` over the Android SPP socket
- [x] 1.3 Rewrite `openPort`, `sendTspl`, `sendRawBytes`, `printPdf` and `closePort` on the new connection; add `cancel`
- [x] 1.4 Return typed error codes and answer Flutter for any `Throwable`
- [x] 1.5 Add the opt-in `<ESC>!R` reset after a failed job
- [x] 1.6 Remove `tscsdk.jar`

## 2. Dart API

- [x] 2.1 Add `PrinterLimits`, `PrinterErrorType` and `PrinterException`
- [x] 2.2 Add limit parameters, `resetPrinterAfterFailure` and `cancel()`; map `PlatformException` codes

## 3. Tests and docs

- [x] 3.1 JVM tests for `PrinterConnection` (split marker, timeout, lost connection, cancel, blocked write, stuck connect)
- [x] 3.2 Dart tests for arguments and error mapping
- [x] 3.3 ADR, README, CHANGELOG and version 0.1.0

## 4. QA follow-up

- [x] 4.1 Move the open, job, close, cancel and reset sequence into `PrinterSession`, testable with fake transports
- [x] 4.2 `cancel()` acts only while a call is running; a cancel during the reset wait ends `openPort` with `cancelled`
- [x] 4.3 JVM tests for the session: not connected, completion request and marker, reset after failure, cancel idle, cancel during a job, cancel during the reset wait
- [x] 4.4 Restore the `///` docs of the platform interface and document `cancel()` and `PrinterException`

## 5. Device verification (manual)

- [ ] 5.1 On a Ribetec printer, measure the longest ticket with 2 copies and confirm the 60 s default (first measure: a 72 x 240 mm ticket with 2 copies confirmed in 18.1 s; the longest ticket is still to measure)
- [ ] 5.2 Turn the printer off mid-print: error within the limit, next print works without restarting the phone
- [x] 5.3 Cancel mid-print: ends with `cancelled`. Cancelling after the paper started to come out lets the printer finish and a new `openPort` works; cancelling before, the printer stays on `Downloading...` and refuses connections until it is turned off and on. Removing the paper mid-print is still to try (a jam gave no error: the printer still answered)
- [ ] 5.4 Check whether the Ribetec answers `<ESC>!R` and `<ESC>!?`
