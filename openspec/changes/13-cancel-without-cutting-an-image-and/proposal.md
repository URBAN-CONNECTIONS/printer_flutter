## Why

Cancelling a job on the RT-320 PB while an image or file is being sent cuts the block in the middle, and the printer stays on `Downloading...` until it is turned off and on. `cancel()` also closes the socket on the main thread and does not stop a PDF still being rendered.

## What Changes

- Cancel finishes the `BITMAP` band or `DOWNLOAD` file in progress, sends nothing after it and ends with `PrinterErrorCode.CANCELLED`.
- `pageByPage` and `unifiedRoll` send each page image as consecutive `BITMAP` bands of at most 200 rows, so the block in progress is short.
- PDF rendering and conversion stop at the next page boundary or after at most 128 rows when cancelled, and recycle every bitmap created.
- `cancel()` no longer blocks the main thread: the socket close runs on the watchdog thread.
- README explains the stuck printer and that `resetPrinterAfterFailure` does not help on the RT-320 PB over Bluetooth; `GLOSSARY.md` gets the term **Stuck printer**.

## Capabilities

### New Capabilities

### Modified Capabilities

- `print-job-limits`: the Cancel requirement now defines the safe boundary, the wait, and the main thread.
- `pdf-tspl-printing`: images are sent as `BITMAP` bands; rendering stops on cancel.

## Impact

- `PrinterFlutterPlugin.kt`, `PrinterConnection.kt`, `PrinterSession.kt`, `PdfPrintHelper.kt`.
- Kotlin unit tests with a fake transport; `README.md`, `CHANGELOG.md`, `GLOSSARY.md`.
- No Dart API or channel change.
