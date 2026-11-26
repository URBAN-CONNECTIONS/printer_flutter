# Tasks

## 1. Block-safe cancel in the connection

- [x] 1.1 Add a block write to `PrinterConnection` that defers the close of `cancel()` until the block ends, and a `checkCancelled()` between blocks; verify with JVM tests using a fake transport (cancel mid-block finishes the block and then closes; cancel between blocks closes at once; `writeTimeout` still closes a stuck block with a message that says the printer may need to be turned off and on)
- [x] 1.2 Make `cancel` not block the main thread: schedule the socket close on the watchdog thread from `PrinterSession`/`PrinterFlutterPlugin`; verify with a JVM test that `cancel()` returns before a slow `close()` ends, and that the connection closes on every path

## 2. Bands and rendering

- [x] 2.1 Add a plain-Kotlin band splitter (at most 200 rows per `BITMAP`, `y` offsets, last band may be shorter, page under 200 rows is one `BITMAP`) and use it in `printPageByPage` and `printUnifiedRoll`, converting/dithering the whole page first; verify with JVM tests that the bands reassemble to the same bytes as one `BITMAP` for 58, 72, 80 mm and a custom width
- [x] 2.2 Check cancel before every block sent by `PdfPrintHelper` (bands, `DOWNLOAD` files, `PRINT`), so nothing follows the block in progress; verify with a JVM test over a fake sender that no `PRINT` and no later block is sent after a cancel
- [x] 2.3 Stop rendering and conversion at each page and every 128 rows when cancelled, and recycle every bitmap in `finally`; verify the cancel check logic with a JVM test and describe the by-hand check for `Bitmap` rendering in the final report

## 3. Documentation

- [x] 3.1 Add the **Stuck printer** entry to `GLOSSARY.md` exactly as in design.md, document the cancel behaviour, the 200-row bands and that `resetPrinterAfterFailure` does not help on the RT-320 PB over Bluetooth in `README.md`, and add a `CHANGELOG.md` entry under unreleased; verify by reading the three files
- [x] 3.2 Run the checks and verify they pass: `flutter analyze`, `flutter test`, `dart format .` with no diff, and the Kotlin unit tests
