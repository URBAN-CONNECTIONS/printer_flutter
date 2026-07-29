## Context

The library currently defines `PdfPrintStrategy.unifiedRoll` as the default strategy in `PdfPrintOptions`. `unifiedRoll` attempts to render the entire PDF into a single large continuous bitmap. This process is time-consuming and memory-intensive, leading to significant delays before printing starts. The alternative strategy, `pageByPage`, renders and prints sequentially, offering an instant start and reduced peak memory usage.

## Goals / Non-Goals

**Goals:**
- Make `pageByPage` the default out-of-the-box experience to guarantee instant printing.
- Simplify the example app to focus on the optimal path by defaulting to `pageByPage` and hiding the complex strategy selection.

**Non-Goals:**
- Removing or deprecating the `unifiedRoll` strategy entirely.
- Altering the underlying native implementation of either strategy.

## Decisions

1. **Update `PdfPrintOptions` Default**: Change `this.strategy = PdfPrintStrategy.unifiedRoll` to `this.strategy = PdfPrintStrategy.pageByPage` in `lib/printer_flutter.dart`.
2. **Update Example App (`main.dart`)**: 
   - Change `PdfPrintStrategy _strategy = PdfPrintStrategy.unifiedRoll;` to `PdfPrintStrategy _strategy = PdfPrintStrategy.pageByPage;`.
   - Remove the `DropdownButton` UI element that selects the strategy to simplify the example app interface.

## Risks / Trade-offs

- **Risk**: Existing users relying on the default `PdfPrintOptions()` configuration might experience a change in print behavior (gaps between pages instead of a single seamless roll).
- **Mitigation**: Users who need seamless roll printing can explicitly set `strategy: PdfPrintStrategy.unifiedRoll`. We can document this behavioral change in the changelog.
