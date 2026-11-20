## Why

Phones that print with this plugin can be left unable to print until the phone is restarted. With `closePort: true`, the plugin called the TSC SDK's `sendcommand_getstring`, which waits for the printer's `ENDLINE` answer with no limit, keeps waiting when the socket fails, and cannot be aborted. All jobs run on a single native thread, so every later job queued behind the stuck one forever. The SDK also turned connection and send failures into a `"-1"` string, so apps reported tickets as printed when nothing came out. This happened in the field with Ribetec printers.

## What Changes

- The plugin opens, writes, waits on and closes the Bluetooth SPP socket itself, with a limit on every step. When a limit expires the socket is closed, which unblocks any stuck read or write. **BREAKING**: the TSC SDK (`tscsdk.jar`) is removed.
- Failures end the call with a typed error (`notConnected`, `connectionLost`, `timeout`, `cancelled`) instead of a `"-1"` status. **BREAKING**: `openPort` throws when it cannot connect.
- New limits, configurable per call: `connectTimeout` (15 s), `writeTimeout` (20 s per chunk) and `completionTimeout` (60 s from the last byte, including physical printing).
- New `cancel()` that aborts the job in progress.
- Optional `resetPrinterAfterFailure` on `openPort`: after a hung, cancelled or failed job, the printer is reset with `<ESC>!R` before reconnecting. Off by default until it is confirmed on Ribetec printers.
- Version 0.1.0.

## Capabilities

### New Capabilities
- `print-job-limits`: limits, typed errors, cancel and the optional printer reset.

### Modified Capabilities
- `socket-auto-close-control`: the wait for the completion marker is bounded.
- `tsc-printer-connection`: the connection no longer uses the TSC SDK.

## Non-goals

- Changing how tickets are rendered (`PdfPrintHelper`, PCX, TSPL commands).
- Querying printer status with `<ESC>!?` while waiting: it must first be confirmed on a Ribetec printer.
- Using `INITIALPRINTER`, which restores factory settings.
- iOS, web and desktop.
- Updating the apps that use the plugin; each pins the new version on its own.

## Impact

- Android code: `PrinterFlutterPlugin.kt`, new `PrinterConnection.kt` and `BluetoothSocketTransport.kt`; `android/libs/tscsdk.jar` removed.
- Dart API: new parameters with defaults, `cancel()`, `PrinterException`, `PrinterErrorType`, `PrinterLimits`.
- Apps that relied on `openPort` returning `"-1"` must catch `PrinterException` instead.
