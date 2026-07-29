## Why

Printing PDF documents (such as traffic tickets, invoices, or receipts) to TSPL thermal printers requires flexible handling of paper layouts. Currently, thermal printing applications need a clean, configurable way to render PDF pages onto continuous thermal rolls (e.g. 72mm, 80mm, 58mm) using either a single continuous unified roll or page-by-page sequential rendering.

## What Changes

- Add native Android PDF rendering and TSPL bitmap generation support (`printPdf`).
- Introduce configurable PDF printing strategies: `unifiedRoll` (stitching all pages into a seamless continuous roll) and `pageByPage` (printing each page sequentially on continuous paper).
- Support custom paper dimensions (e.g., 72mm width), DPI configurations (e.g., 203 DPI = 576 dots / 72 bytes per row), and image dithering options.
- Expose a clean, structured Dart API (`PdfPrintOptions`) in the Flutter plugin.

## Capabilities

### New Capabilities
- `pdf-tspl-printing`: Render PDF documents into monochrome TSPL bitmap streams with configurable strategies (`unifiedRoll` and `pageByPage`) for continuous thermal roll paper (such as 72mm).

### Modified Capabilities

## Non-goals

- Support for ESC/POS or CPCL protocol conversion in this change (focus is strictly on TSPL/TSPL2).
- PDF editing or form-filling prior to printing (PDF is rendered as provided).

## Impact

- **Dart API**: New `printPdf` method and `PdfPrintOptions` data class added to `printer_flutter`.
- **Android Native**: Enhanced Kotlin plugin (`PrinterFlutterPlugin.kt`) integrating Android `PdfRenderer`, bitmap scaling/binarization, and TSPL `BITMAP` transmission.
- **Dependencies**: No external third-party PDF dependencies required; utilizes native Android `PdfRenderer` and Flutter `MethodChannel`.
