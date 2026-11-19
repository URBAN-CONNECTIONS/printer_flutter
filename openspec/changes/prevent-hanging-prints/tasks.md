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

## 4. Device verification (manual)

- [ ] 4.1 On a Ribetec printer, measure the longest ticket with 2 copies and confirm the 60 s default
- [ ] 4.2 Turn the printer off mid-print: error within the limit, next print works without restarting the phone
- [ ] 4.3 Remove the paper mid-print and cancel mid-print
- [ ] 4.4 Check whether the Ribetec answers `<ESC>!R` and `<ESC>!?`
