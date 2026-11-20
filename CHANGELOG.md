## 0.1.0

* **BREAKING**: removed the TSC SDK (`tscsdk.jar`). The plugin now opens, writes, waits on and closes the Bluetooth SPP socket itself, with a limit on every step, so a printer that stops answering can no longer leave the phone unable to print.
* **BREAKING**: failures throw `PrinterException` with a `PrinterErrorType` (`notConnected`, `connectionLost`, `timeout`, `cancelled`, `unknown`). `openPort` throws when it cannot connect instead of returning `"-1"`.
* Added `connectTimeout`, `writeTimeout` and `completionTimeout` parameters, with defaults in `PrinterLimits` (15 s, 20 s per chunk, 60 s).
* Added `cancel()` to abort the job in progress.
* Added opt-in `resetPrinterAfterFailure` to `openPort`: resets the printer with `<ESC>!R` after a failed job.
* The wait for the completion marker now finds it when it arrives split across reads.
* Added `cancel(resetPrinter: true)`: stops sending, sends `<ESC>!R` on the open connection and then closes it, so the printer is not left waiting for the rest of an image. Off by default.

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
