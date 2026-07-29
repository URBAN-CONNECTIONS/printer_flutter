## ADDED Requirements

### Requirement: Trim Whitespace Configuration
The system MUST allow users to specify whether trailing whitespace should be trimmed via a `trimWhitespace` flag in `PdfPrintOptions`.

#### Scenario: User enables trimming
- **GIVEN** a PDF document with blank space at the bottom
- **WHEN** the user calls `printPdf` with `trimWhitespace = true`
- **THEN** the system crops the empty space at the bottom of the rendered image before printing

#### Scenario: User disables trimming (Default)
- **GIVEN** a PDF document with blank space at the bottom
- **WHEN** the user calls `printPdf` with `trimWhitespace = false`
- **THEN** the system prints the full PDF page including the blank space at the bottom

### Requirement: Bitmap Pixel Analysis
When trimming is enabled, the system MUST calculate the bounding box of non-white pixels starting from the bottom of the rendered PDF image.

#### Scenario: Cropping the final image
- **GIVEN** a rendered PDF Bitmap filled with `Color.WHITE` at the bottom
- **WHEN** trimming is processed
- **THEN** the height of the Bitmap sent to the printer is reduced to include only rows with at least one non-white pixel (plus optional padding)
