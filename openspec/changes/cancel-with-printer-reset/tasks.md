## 1. Native

- [x] 1.1 `PrinterConnection`: pending reset, stop at the next chunk or poll, reset write with its own limit, hard stop on the watchdog
- [x] 1.2 `PrinterSession`: `cancel(resetPrinter)`, wait before the next open and no second reset
- [x] 1.3 Plugin `cancel` handler reads `resetPrinter`

## 2. Dart API

- [x] 2.1 `cancel({bool resetPrinter = false})` in the facade, the platform interface and the method channel, with docs

## 3. Tests and docs

- [x] 3.1 JVM tests: reset sent before closing, hard stop on a blocked write, next open waits and does not reset again
- [x] 3.2 Dart tests for the argument
- [x] 3.3 README, CHANGELOG and the guided test of the example app

## 4. Device verification (manual)

- [ ] 4.1 On a Ribetec, cancel mid-print with `resetPrinter: true`: the printer must not stay on `Downloading...` and the next print must work
