## Context

The Flutter Bluetooth Thermal Printer Plugin currently sends a hardcoded `PRINT 1` (or `PRINT 1,1`) command to the printer when finalizing a label or PDF print job. Users have requested the ability to specify the number of copies they want to print for a single document to avoid sending the document data multiple times over the Bluetooth connection manually from Dart. Furthermore, when printing PDFs page-by-page, users expect the output to be collated (intercalated).

## Goals / Non-Goals

**Goals:**
- Expose an optional `copies` parameter to the Dart plugin interface methods (e.g., `printPdf`).
- Ensure the `copies` value is passed via MethodChannel to the Android native side.
- For `unifiedRoll` printing strategy (single giant bitmap), pass the `copies` value to the TSPL `PRINT` command (`PRINT <copies>,1`).
- For `pageByPage` printing strategy, implement software-level collation (intercalated printing) by sending the pages in sequence repeatedly, while caching the processed TSPL byte arrays in memory to avoid re-rendering and re-dithering the PDF.
- Fast path: Do not cache if `copies == 1`.

**Non-Goals:**
- We are not adding complex print job management (e.g., pausing between copies). 

## Decisions

- **Dart API update**: We will add `int copies = 1` as an optional named parameter to `printPdf` and other relevant print methods in `printer_flutter` and `printer_flutter_platform_interface`. This ensures backward compatibility.
- **MethodChannel communication**: The `copies` value will be added to the arguments Map in `printer_flutter_method_channel.dart` and parsed in the Android Kotlin `MethodCallHandler`.
- **Kotlin PDF Print Logic Update (`PdfPrintHelper.kt`)**:
  - **`printUnifiedRoll`**: Append `PRINT ${copies},1` to the end of the TSPL byte buffer.
  - **`printPageByPage`**: 
    - *Fast Path (`copies <= 1`)*: Continue to loop through pages, render, and stream immediately to the printer (current behavior).
    - *Collation Path (`copies > 1`)*: First, loop through all pages, render, crop, convert to monochrome TSPL byte arrays, and save them in a memory list (`List<ByteArray>`). Then, loop `copies` times, iterating through the list and transmitting the cached byte arrays. This guarantees collated output without re-rendering overhead.

## Risks / Trade-offs

- **Risk (Transmission Time)**: For `pageByPage` with `copies > 1`, software-level collation requires transmitting the bitmaps over Bluetooth repeatedly. 
  **Mitigation**: CPU rendering overhead is mitigated by caching the TSPL byte arrays. The extra Bluetooth transmission time is an accepted trade-off to ensure collated output, and memory usage for a few cached pages is negligible.
- **Risk (Printer Buffer)**: Some older printers might have an upper limit on the number of copies they can hold if using `unifiedRoll`.
  **Mitigation**: The `PRINT` command in TSPL generally supports numbers from 1 to 65535.
