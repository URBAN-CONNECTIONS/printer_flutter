## 1. Dart API Updates

- [x] 1.1 Add an optional `int copies = 1` parameter to print methods in `printer_flutter_platform_interface.dart`.
- [x] 1.2 Update the `printPdf` (and other print methods) in `printer_flutter.dart` to accept and forward the `copies` argument.
- [x] 1.3 Update `printer_flutter_method_channel.dart` to include `"copies"` in the argument map sent over the MethodChannel.

## 2. Android Native Updates

- [x] 2.1 Update the Android MethodCall handler (e.g., in `PrinterFlutterPlugin.kt` and `PdfPrintHelper.kt`) to extract the `"copies"` argument.
- [x] 2.2 In `PdfPrintHelper.printUnifiedRoll`, append `PRINT ${copies},1` to the TSPL command string instead of hardcoded `1,1`.
- [x] 2.3 Update `PdfPrintHelper.printPageByPage` to handle `copies <= 1` as a fast path (existing stream-and-print logic).
- [x] 2.4 Update `PdfPrintHelper.printPageByPage` to handle `copies > 1` by pre-rendering all pages into a `List<ByteArray>` (the full TSPL commands per page), and then looping `copies` times to transmit the cached arrays.

## 3. Testing and Verification

- [x] 3.1 Write unit tests in Dart to verify the `"copies"` parameter is correctly added to the MethodChannel arguments.
- [x] 3.2 Ensure the generated code complies with `dart format` and project linter rules.
