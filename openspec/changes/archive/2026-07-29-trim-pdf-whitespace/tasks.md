## 1. Dart API Updates

- [x] 1.1 Add `trimWhitespace` boolean property (default `false`) to `PdfPrintOptions` in `lib/printer_flutter.dart`
- [x] 1.2 Update `printPdf` method in `lib/printer_flutter_method_channel.dart` to pass `trimWhitespace` in the MethodChannel arguments
- [x] 1.3 Add documentation comments to reflect the new `trimWhitespace` property in `PdfPrintOptions`

## 2. Android Native Implementation

- [x] 2.1 Update `PrinterFlutterPlugin.kt` `printPdf` handler to extract the `trimWhitespace` argument
- [x] 2.2 Modify `PdfPrintHelper.kt` to accept the `trimWhitespace` parameter in `printPdf`
- [x] 2.3 Implement pixel scanning logic in `PdfPrintHelper.kt` to calculate the cropped height by checking for non-white pixels
- [x] 2.4 Apply the cropped height to the bitmap generation in `printUnifiedRoll` and `printPageByPage` when `trimWhitespace` is true
