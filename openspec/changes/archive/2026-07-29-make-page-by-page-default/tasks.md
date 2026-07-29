## 1. Library Updates

- [x] 1.1 Update `PdfPrintOptions` in `lib/printer_flutter.dart` to default to `PdfPrintStrategy.pageByPage`.

## 2. Example App Updates

- [x] 2.1 Update `_MyAppState` in `example/lib/main.dart` to default `_strategy` to `PdfPrintStrategy.pageByPage`.
- [x] 2.2 Remove the `DropdownButton` UI element for selecting the PDF print strategy in `example/lib/main.dart` to simplify the app interface.

## 3. Formatting and Validation

- [x] 3.1 Run `dart format` on `lib` and `example/lib` to ensure formatting compliance.
