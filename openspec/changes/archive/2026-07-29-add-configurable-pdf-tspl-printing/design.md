## Context

The `printer_flutter` plugin connects via Bluetooth Classic SPP to thermal printers using TSPL/TSPL2 commands. To support printing PDF documents like traffic citations (`ticket ejemplo.pdf`) onto continuous thermal paper (e.g. 72mm width), the plugin needs a native Android PDF renderer and TSPL byte builder capable of handling continuous roll dimensions.

## Goals / Non-Goals

**Goals:**
- Provide a configurable PDF printing API in Dart (`printPdf`) with `paperWidthMm`, `dpi`, `strategy`, and `enableDithering` options.
- Implement both `unifiedRoll` (stitching pages into a continuous roll) and `pageByPage` (sequential page output with `GAP 0,0`) strategies.
- Utilize native Android `PdfRenderer` and `Canvas` for memory-efficient rendering without third-party native binary dependencies.
- Ensure byte alignment for TSPL `BITMAP` packets (e.g., 72mm @ 203 DPI = 576 dots = 72 bytes per row).

**Non-Goals:**
- PDF creation or layout editing prior to rasterization.
- Support for iOS/Desktop PDF printing in this iteration (Android Bluetooth SPP focus).

## Decisions

### Decision 1: Native Android `PdfRenderer` + `Canvas` vs Dart PDF rendering package
- **Choice**: Native Android `PdfRenderer` inside `PrinterFlutterPlugin.kt`.
- **Rationale**: `PdfRenderer` is built into Android (API 21+) and avoids heavy Flutter package dependencies. It allows high-performance bitmap rendering directly to Android `Bitmap` objects.
- **Alternatives Considered**: Using a pure Dart PDF renderer and passing byte arrays over MethodChannel. This was rejected because transferring multi-megabyte uncompressed ARGB byte arrays over MethodChannel introduces IPC overhead and memory churn.

### Decision 2: Configurable Strategy Design (`unifiedRoll` vs `pageByPage`)
- **Choice**: Expose `PdfPrintStrategy.unifiedRoll` and `PdfPrintStrategy.pageByPage` as options.
- **Rationale**: 
  - `unifiedRoll` stitches all rendered PDF page bitmaps vertically onto a single continuous canvas, issuing a single `SIZE 72 mm, <totalHeight>` and `BITMAP` packet. This provides a completely seamless continuous ticket.
  - `pageByPage` renders each page individually and transmits TSPL commands sequentially with `GAP 0,0`. This reduces peak RAM usage.
- **Alternatives Considered**: Enforcing only one hardcoded strategy. Rejected because different thermal printer models handle large image buffers differently.

## Sequence Diagram

```
Flutter (Dart)            PrinterFlutterPlugin (Kotlin)        Android PdfRenderer         TSPL Printer (Bluetooth)
     │                                │                                │                            │
     │── printPdf(file, options) ────▶│                                │                            │
     │                                │── Open ParcelFileDescriptor ──▶│                            │
     │                                │── Render Pages to Bitmaps ────▶│                            │
     │                                │                                │                            │
     │                                │── [If unifiedRoll] ───────────┐                             │
     │                                │   Stitch Bitmaps vertically   │                             │
     │                                │── [If pageByPage] ────────────┘                             │
     │                                │                                                             │
     │                                │── Binarize / Dither to 1-bit ──────────────────────────────▶│
     │                                │── Send TSPL: SIZE 72mm, GAP 0,0, CLS ──────────────────────▶│
     │                                │── Send TSPL: BITMAP x,y,w,h,0,<data> ──────────────────────▶│
     │                                │── Send TSPL: PRINT 1,1 ────────────────────────────────────▶│
     │                                │                                                             │
     │◀── Result.success("Success") ──│                                                             │
```

## Risks / Trade-offs

- **[Risk] High Memory Usage on `unifiedRoll` for extremely long PDFs**  
  → *Mitigation*: Dynamically check available memory; if total height exceeds 8000 pixels (~1000mm), scale bitmap height or fall back to `pageByPage` mode.
- **[Risk] Printer Buffer Overflow on Bluetooth SPP**  
  → *Mitigation*: Execute printer socket writes on a background single-thread executor (`Executors.newSingleThreadExecutor()`) with chunking if necessary.
