## ADDED Requirements

### Requirement: Configurable PDF Printing Strategies
The system SHALL support printing PDF documents to TSPL thermal printers using configurable strategies (`unifiedRoll` and `pageByPage`).

#### Scenario: Unified continuous roll printing
- **GIVEN** a multi-page PDF file path and paper width of 72mm
- **WHEN** the developer invokes `printPdf` with `strategy: PdfPrintStrategy.unifiedRoll`
- **THEN** the system SHALL render all PDF pages onto a single vertically stitched monochrome bitmap and transmit a unified TSPL command packet containing `SIZE 72 mm, <totalHeightMm>`, `GAP 0,0`, `CLS`, `BITMAP`, and `PRINT 1,1`.

#### Scenario: Sequential page-by-page printing
- **GIVEN** a multi-page PDF file path and paper width of 72mm
- **WHEN** the developer invokes `printPdf` with `strategy: PdfPrintStrategy.pageByPage`
- **THEN** the system SHALL render each PDF page individually to a monochrome bitmap and sequentially transmit TSPL command packets with `GAP 0,0` and `PRINT 1,1` for each page without feeding extra paper gaps.

### Requirement: Paper Width and DPI Calculation
The system SHALL calculate dot dimensions and byte alignment dynamically based on paper width in mm and target printer DPI (defaulting to 203 DPI / 8 dots/mm).

#### Scenario: 72mm paper at 203 DPI
- **GIVEN** a target paper width of 72mm and resolution of 203 DPI
- **WHEN** the PDF rendering engine formats the bitmap for TSPL transmission
- **THEN** the system SHALL scale the horizontal resolution to 576 dots (72 bytes per row) and byte-align each raster line.

### Requirement: Image Dithering and Monochrome Binarization
The system SHALL convert colored or grayscale PDF pages to 1-bit monochrome bitmaps using configurable dithering (Floyd-Steinberg algorithm or thresholding).

#### Scenario: Printing PDF ticket with graphics and photographs
- **GIVEN** a PDF containing text, vector headers, and photographic evidence
- **WHEN** `printPdf` is executed with `enableDithering: true`
- **THEN** the system SHALL apply Floyd-Steinberg dithering to preserve photographic clarity in the 1-bit TSPL `BITMAP` output.
