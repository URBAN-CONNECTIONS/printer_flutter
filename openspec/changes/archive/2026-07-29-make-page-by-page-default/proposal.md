## Why

The `unifiedRoll` strategy for printing PDFs takes a long time because it allocates and composes a massive vertical bitmap before printing. Conversely, the `pageByPage` strategy is instant as it processes and prints each page sequentially. Since users prefer the instant start time, we want to make `pageByPage` the default behavior while retaining `unifiedRoll` for edge cases.

## What Changes

- Change the default value of `strategy` in `PdfPrintOptions` from `PdfPrintStrategy.unifiedRoll` to `PdfPrintStrategy.pageByPage`.
- Update the example app (`main.dart`) to default to `pageByPage`.
- Remove the strategy selection dropdown from the example app UI to simplify the interface, while keeping the `PdfPrintStrategy` enum available for developers who specifically need `unifiedRoll`.

## Capabilities

### New Capabilities

- None

### Modified Capabilities

- `pdf-tspl-printing`: The default print strategy is changing from `unifiedRoll` to `pageByPage`.

## Impact

- **Code**: `lib/printer_flutter.dart` and `example/lib/main.dart`
- **APIs**: The default `PdfPrintOptions()` behavior will change to `pageByPage`. This is technically a behavioral change but is considered a fix for long printing delays.

## Non-goals

- We are not removing or deleting the `unifiedRoll` strategy. It will still be available programmatically.
