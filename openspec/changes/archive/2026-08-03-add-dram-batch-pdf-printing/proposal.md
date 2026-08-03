## Why

When printing multi-page PDFs using `PdfPrintStrategy.pageByPage`, Bluetooth transmission bottlenecks cause the printer motor to stop and resume between pages, creating noisy physical stuttering. Conversely, `PdfPrintStrategy.unifiedRoll` stitches all pages into a single giant bitmap, which frequently exceeds the 2MB–4MB RAM limit of mobile TSPL printers and causes hardware freezes or hard reboots.

We need a strategy that transmits all page bitmaps quietly into printer DRAM as separate files before initiating a single continuous batch print execution.

## What Changes

- Add a new `dramBatch` enum value to `PdfPrintStrategy` in Dart (`lib/printer_flutter.dart`).
- Update native Android `PdfPrintHelper.kt` to handle `dramBatch`:
  - Upload rendered 1-bit monochrome page bitmaps to printer DRAM using TSPL `DOWNLOAD "P<n>.BMP",<size>,<bytes>`.
  - Transmit a final execution script using `PUTBMP` and `PRINT 1,1` per page, followed by `KILL "P*.BMP"` cleanup.
- Add an example / test option in `example/lib/main.dart` to select and test the DRAM batch strategy.

## Capabilities

### New Capabilities

- `pdf-dram-batch-printing`: Enables buffering rendered PDF page bitmaps into printer DRAM memory via TSPL `DOWNLOAD` for continuous, crash-free multi-page thermal printing.

### Modified Capabilities

None.

## Impact

- **Public API**: New `PdfPrintStrategy.dramBatch` enum value available in `PdfPrintOptions`.
- **Android Native**: `PdfPrintHelper.kt` modified to add `printDramBatch` logic.
- **Hardware**: Zero motor stuttering between pages and eliminated out-of-memory printer crashes.

## Non-goals

- Supporting ESC/POS or CPCL DRAM download commands in this change (TSPL only).
- Dynamic RAM availability detection on hardware (relying on page-by-page DRAM file cleanup).
