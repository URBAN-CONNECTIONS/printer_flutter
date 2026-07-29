## 1. Dart API and Models

- [x] 1.1 Add `PdfPrintStrategy` enum (`unifiedRoll`, `pageByPage`) and `PdfPrintOptions` data class to `lib/printer_flutter.dart`.
- [x] 1.2 Update platform interface and method channel implementations in `lib/printer_flutter_platform_interface.dart` and `lib/printer_flutter_method_channel.dart` to support `printPdf`.
- [x] 1.3 Format code using `dart format`.

## 2. Android Native PDF Rendering and TSPL Bitmapping

- [x] 2.1 Implement `renderPdf` handler in `android/src/main/kotlin/com/urbanconnections/printer_flutter/PrinterFlutterPlugin.kt` using `android.graphics.pdf.PdfRenderer`.
- [x] 2.2 Implement `unifiedRoll` strategy (stitching all rendered page Bitmaps into a single continuous Bitmap).
- [x] 2.3 Implement `pageByPage` strategy (sequential rendering and sending of page Bitmaps with `GAP 0,0`).
- [x] 2.4 Add 1-bit monochrome binarization and Floyd-Steinberg dithering for TSPL `BITMAP` byte packing.
- [x] 2.5 Send TSPL header commands (`SIZE`, `GAP 0,0`, `CLS`), `BITMAP` packets, and `PRINT 1,1` via `TSCActivity` or Bluetooth socket.

## 3. Example App Integration and Manual Verification

- [x] 3.1 Update `example/lib/main.dart` with a UI control to select PDF printing strategy (`unifiedRoll` vs `pageByPage`) and print sample ticket PDF (`sample.pdf`).
- [x] 3.2 Verify code quality and static analysis with `dart analyze`.
