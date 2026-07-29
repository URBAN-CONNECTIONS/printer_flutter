## Context

When generating PDFs externally, they are often created with fixed page sizes (e.g., A4 or a custom receipt length) that exceed the actual printed content. When printed on a continuous thermal printer roll, this results in significant wasted paper at the end of each printout because the plugin currently renders the full height of the PDF page and sends it to the printer.

## Goals / Non-Goals

**Goals:**
- Dynamically calculate the "true" height of the content by removing bottom whitespace.
- Reduce thermal paper waste.
- Provide a simple toggle for developers to opt-in or opt-out of this behavior.

**Non-Goals:**
- Do not modify the original PDF file.
- Do not trim horizontal whitespace or top whitespace.
- Do not apply this to commands other than `printPdf`.

## Decisions

**Decision 1: Trim in Android Native Code**
- **Rationale**: The PDF is rasterized using `PdfRenderer` on the Android side. By analyzing the `Bitmap` immediately after rendering, we avoid round-trips to the Dart side or using external Dart packages.
- **Alternatives Considered**: Using a Dart-side library to manipulate the PDF before sending it to the platform. This was rejected because it introduces a heavy dependency and increases memory overhead.

**Decision 2: Pixel Scanning Algorithm**
- **Rationale**: We will scan the generated `Bitmap` pixel array starting from the bottom row and moving upwards. The first row containing any pixel that is not `Color.WHITE` will become the new bottom boundary of the image.
- **Alternatives Considered**: Using OpenCV or similar computer vision libraries. Rejected because it's overkill for a simple color check on a small bitmap.

## Risks / Trade-offs

- **Risk: Performance overhead**
  → **Mitigation**: Thermal printer resolutions are generally low (e.g., 203 DPI, 384-576 pixels wide). Iterating over a 1D IntArray of pixels takes negligible time in Kotlin. The overhead is minimal.
- **Risk: Cutting too close to the text**
  → **Mitigation**: We will add a small padding (e.g., 20px) to the cropped boundary so it doesn't look cut off.
