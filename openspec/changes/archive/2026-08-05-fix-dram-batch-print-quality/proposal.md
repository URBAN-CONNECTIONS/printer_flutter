## Why

When using the `dramBatch` PDF print strategy, printed tickets appear blurry, pixelated, or soft compared to `pageByPage`. This occurs because `dramBatch` ignores `enableDithering` and uses simple hard thresholding during 1-bit PCX image conversion, while `pageByPage` applies Floyd-Steinberg dithering. In addition, unaligned PCX row byte counts (`bytesPerLine`) add padding bytes that can cause minor pixel interpolation on thermal printer firmware.

## What Changes

- Update native Android `PdfPrintHelper.kt` PCX file conversion (`convertToPcxFile`) to accept the `enableDithering` flag and apply Floyd-Steinberg dithering when enabled.
- Align bitmap width calculations so PCX row scanlines (`bytesPerLine`) maintain strict 2-byte boundary alignment without injecting unexpected padding bits.
- Ensure `dramBatch` visual quality matches `pageByPage` quality bit-for-bit when dithering is enabled.

## Capabilities

### New Capabilities
- None

### Modified Capabilities
- `pdf-dram-batch-printing`: Support Floyd-Steinberg dithering and exact byte alignment in DRAM batch PCX page conversions so printed tickets match `pageByPage` visual quality.

## Non-goals

- Changing the underlying TSPL commands (`DOWNLOAD` / `PUTPCX`) used for DRAM storage unless dithering alone is insufficient.
- Modifying `unifiedRoll` or `pageByPage` printing strategies.
- Adding new public Dart API parameters or changing `PdfPrintOptions`.

## Impact

- **Android Native**: `PdfPrintHelper.kt` PCX conversion logic updated to support dithering and proper row alignment.
- **Dart API**: No breaking changes; `PdfPrintOptions(enableDithering: true)` will now be respected when `strategy: PdfPrintStrategy.dramBatch`.
