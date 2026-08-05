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
