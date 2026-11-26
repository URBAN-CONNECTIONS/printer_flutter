## Unreleased

* Fixed: a `cancel()` that arrives after the last check of a job is no longer reported as success; the call ends with `PrinterErrorType.cancelled` and the closed connection is dropped. A `cancel()` during the close grace wait of `closePort()` also ends it with `cancelled`. Calls still queued when the plugin detaches no longer start.
* Fixed: `printPdf` with a missing file now fails before connecting. `sendRawBytes` without bytes now fails instead of returning a success string. A PDF that cannot be opened no longer leaks a file descriptor. `PdfPrintStrategy.dramBatch` notices a cancel during its pauses.
* Fixed: `cancel()` no longer leaves the printer stuck on `Downloading...` when it arrives in the middle of an image or file. The plugin finishes the `BITMAP` band or `DOWNLOAD` file in progress, sends nothing after it and ends the call with `PrinterErrorType.cancelled`. `cancel()` returns at once and does not block the main thread. With `PdfPrintStrategy.dramBatch` it waits for the file in progress.
* `PdfPrintStrategy.pageByPage` and `PdfPrintStrategy.unifiedRoll` now send each image in `BITMAP` bands of at most 200 rows. The printed result is the same.
* Rendering and conversion of a PDF stop at the next page, or every 128 rows, when the job is cancelled.
* Documented that a printer whose connection is lost in the middle of a block shows `Downloading...` and needs to be turned off and on, and that `resetPrinterAfterFailure` (`<ESC>!R`) does not recover it on the RT-320 PB over Bluetooth.
* Added a probe: when it is on, before any byte of a job is sent, the printer has to answer (TSPL `OUT "READY"`) within 2 s. If it does not, nothing of the job is sent, the connection is closed and the call fails with the new `PrinterErrorType.printerNotReady`.
* **Behaviour change**: atomic calls now probe by default; pass `probe: false` to turn it off. In the three-step API the probe is off unless `printPdf`, `sendTspl` or `sendRawBytes` get `probe: true`.
* After a job sent with `confirmCompletion: false`, the next probe on that printer waits up to `completionTimeout` instead of 2 s.
* An exhaustive `switch` over `PrinterErrorType` needs a case for `printerNotReady`.
* Added an atomic print call: pass the optional `macAddress` (and `pin`, `fallbackPin`, `connectTimeout`) to `printPdf`, `sendTspl` or `sendRawBytes` and the plugin connects, sends, confirms and closes in one native call. Atomic calls run first in, first out, take over a connection left open by `openPort`, and ignore `closePort`. The three-step API (`openPort`, send, `closePort`) is unchanged.
* Fixed: a queued atomic call could fail with `notConnected` when it connected right after a cancelled one, because the printer refused connections for a moment. For 5 s after a cancelled call, an atomic call now retries a failed connection about every 500 ms.
* Fixed: with `confirmCompletion: false`, a `cancel()` during the close-grace delay was ignored and the call returned `Success`. It now ends the delay at once, closes the connection and fails with `PrinterErrorType.cancelled`, both in an atomic call and in a send with `closePort: true`. The last bytes may not have reached the printer.
* Added `confirmCompletion` (default `true`) to `printPdf`, `sendTspl` and `sendRawBytes`. With `false` the call does not wait for the printer's `OUT` answer; it returns after sending the bytes and closing the connection.
* A timeout after everything was sent now documents as "not confirmed": the printer may still have printed.
* Android logs the milliseconds of the connect, render, send and confirm phases of each atomic call (`adb logcat -s PrinterFlutterPlugin:I`).

* **BREAKING**: removed the TSC SDK (`tscsdk.jar`). The plugin now opens, writes, waits on and closes the Bluetooth SPP socket itself, with a limit on every step, so a printer that stops answering can no longer leave the phone unable to print.
* **BREAKING**: failures throw `PrinterException` with a `PrinterErrorType` (`notConnected`, `connectionLost`, `timeout`, `cancelled`, `unknown`). `openPort` throws when it cannot connect instead of returning `"-1"`.
* Added `connectTimeout`, `writeTimeout` and `completionTimeout` parameters, with defaults in `PrinterLimits` (15 s, 20 s per chunk, 60 s).
* Added `cancel()` to abort the job in progress.
* Added opt-in `resetPrinterAfterFailure` to `openPort`: resets the printer with `<ESC>!R` after a failed job.
* The wait for the completion marker now finds it when it arrives split across reads.

## 0.0.11

* Added automatic Bluetooth PIN pairing for Android legacy SPP connections.
* Added `pin` (default: "0000") and `fallbackPin` (default: "1234") optional parameters to `openPort` API.
* MAC address filtering added to Android pairing receiver to prevent interference with other Bluetooth devices.
* Activity-bound receiver lifecycle implemented for zero-battery overhead auto-pairing.

## 0.0.10

* Fixed `dramBatch` printing quality by adding Floyd-Steinberg error diffusion dithering to PCX encoding.
* Fixed PCX scanline byte calculation alignment to 16-bit boundaries to prevent horizontal pixel shifting and blurriness.

## 0.0.9

* Added `dramBatch` printing strategy with PCX compression.
* Added socket auto-close control with TSPL OUT marker.
* Fixed openport re-entry crash.
* Added TSPL programming manual to documentation.

## 0.0.8

* Fix `IllegalArgumentException` on Android when encoding `kotlin.Unit` return values in MethodChannel `executeInBackground`.

## 0.0.3

* Added `ensureBluetoothIsOn()` to natively check and prompt the user to enable Bluetooth on Android.
* Added native iOS support for checking `CBCentralManager` power state in `ensureBluetoothIsOn()`.
* Removes the need for external Bluetooth LE packages just to manage adapter state.

## 0.0.2

* Added optional `copies` argument to `printPdf` and other print methods.
* Optimized `pageByPage` printing strategy to cache TSPL commands in memory for faster collation when `copies > 1`.

## 0.0.1

* Initial release of `printer_flutter`.
* Bluetooth Classic (SPP) thermal printer scanning and connection.
* TSPL/TSC command generation (text, 1D barcodes, 2D QR codes, raster images).
* PDF thermal printing with configurable DPI, paper dimensions, and margins.
* Support for monolithic and page-by-page PDF TSPL printing strategies.
* Automatic PDF page whitespace trimming.
* Integrated runtime Bluetooth permissions helper (`checkAndRequestPermissions`).
