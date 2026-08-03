## 1. Dart API Update

- [x] 1.1 Add `dramBatch` enum value to `PdfPrintStrategy` in `lib/printer_flutter.dart`.
- [x] 1.2 Format Dart code using `dart format`.

## 2. Native Android Implementation

- [x] 2.1 Implement `printDramBatch` in `android/src/main/kotlin/com/urbanconnections/printer_flutter/PdfPrintHelper.kt`.
- [x] 2.2 Wire strategy selection in `PdfPrintHelper.printPdf` to route to `printDramBatch` when `strategy == "dramBatch"`.
- [x] 2.3 Transmit initial `KILL "P*.BMP"` command to clear DRAM of any stale bitmap files from previous runs.
- [x] 2.4 Construct TSPL `DOWNLOAD` payload per rendered page bitmap and transmit via `sendCommand` / `sendString`.
- [x] 2.5 Construct and transmit final TSPL batch execution script (`PUTBMP`, `PRINT 1,1`, `KILL "P*.BMP"`).

## 3. Example App & Testing

- [x] 3.1 Remove temporary `DELAY 15000` from `_printPdfLabel` in `example/lib/main.dart`.
- [x] 3.2 Update `_strategy` in `example/lib/main.dart` to use `PdfPrintStrategy.dramBatch`.
- [x] 3.3 Verify code compilation and formatting.
