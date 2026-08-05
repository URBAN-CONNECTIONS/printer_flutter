## 1. Native Android Implementation

- [x] 1.1 Update `convertToPcxFile` in `android/src/main/kotlin/com/urbanconnections/printer_flutter/PdfPrintHelper.kt` to accept `dither: Boolean` parameter and implement Floyd-Steinberg error diffusion algorithm.
- [x] 1.2 Update `printDramBatch` in `PdfPrintHelper.kt` to pass `enableDithering` flag to `convertToPcxFile`.
- [x] 1.3 Verify PCX scanline byte calculation (`bytesPerLine`) alignment to ensure clean byte boundaries without extra pixel padding.

## 2. Verification

- [x] 2.1 Run static analysis (`dart analyze`) to verify Dart project cleanliness.
- [x] 2.2 Verify Android Kotlin compilation cleanly builds.
