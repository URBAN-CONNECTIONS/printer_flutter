## Why

When printing a PDF document generated outside of the plugin, the bottom of the page often contains empty white space. Since thermal printers use continuous rolls and rely on the height provided, this causes the printer to output unnecessary blank paper at the end of each print job. This change introduces the ability to detect and remove this trailing whitespace dynamically, saving paper and providing a cleaner printout.

## What Changes

- Add a new optional configuration parameter `trimWhitespace` (boolean) to `PdfPrintOptions` in the Dart API.
- Update the Android native `PdfPrintHelper.kt` to optionally scan the rendered PDF bitmap from the bottom up.
- When `trimWhitespace` is enabled, the helper will find the last row containing non-white pixels and crop the height of the image sent to the printer accordingly.

## Capabilities

### New Capabilities
- `trim-pdf-whitespace`: Trim trailing white space from rendered PDF pages before printing to save thermal paper.

### Modified Capabilities

## Impact

- **Dart API:** Adds `trimWhitespace` parameter to `PdfPrintOptions`. This is backward compatible (defaults to false).
- **Android Native:** Modifies `PdfPrintHelper.kt` pixel processing logic. Increases CPU overhead slightly when scanning pixels, but negligible on typical thermal label sizes.
- **Testing:** Requires verifying that cropped prints accurately align with the actual content without cutting off the bottom margin aggressively.

## Non-goals

- Trimming horizontal (left/right) white space.
- Trimming whitespace from the top of the PDF.
- Modifying the original PDF file itself; the trimming only occurs in the rasterized bitmap sent to the printer.
