# pdf-tspl-printing Specification

## Purpose
Prints a PDF on a TSPL thermal printer with a choice of strategy, the dot width computed from the paper width and the DPI, and dithering to a 1-bit bitmap.

## Requirements

### Requirement: Configurable PDF Printing Strategies
The system SHALL support printing PDF documents to TSPL thermal printers using configurable strategies (`unifiedRoll` and `pageByPage`). The default strategy MUST be `pageByPage` to ensure instant start performance and lower peak memory usage.

#### Scenario: Unified continuous roll printing
- **GIVEN** a multi-page PDF file path and paper width of 72mm
- **WHEN** the developer invokes `printPdf` with `strategy: PdfPrintStrategy.unifiedRoll`
- **THEN** the system SHALL render all PDF pages onto a single vertically stitched monochrome bitmap and transmit a unified TSPL command packet containing `SIZE 72 mm, <totalHeightMm>`, `GAP 0,0`, `CLS`, `BITMAP`, and `PRINT 1,1`.

#### Scenario: Sequential page-by-page printing
- **GIVEN** a multi-page PDF file path and paper width of 72mm
- **WHEN** the developer invokes `printPdf` with `strategy: PdfPrintStrategy.pageByPage` (or with no strategy parameter, utilizing the default)
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

### Requirement: Images are sent in bands
`pageByPage` and `unifiedRoll` SHALL send each page image as consecutive `BITMAP` commands of at most 200 rows, at their `y` offsets, and the printed result SHALL be the same as one `BITMAP` for the whole page. `dramBatch` SHALL keep downloading one PCX file per page.

#### Scenario: Page image sent in bands
- **WHEN** `pageByPage` or `unifiedRoll` sends a page image
- **THEN** it is sent as consecutive `BITMAP` commands of at most 200 rows each, at their `y` offsets, and the printed result is the same as one `BITMAP` for the whole page

#### Scenario: Short page
- **WHEN** a page is shorter than 200 rows
- **THEN** it is sent as one `BITMAP`, and the last band of a longer page MAY have fewer rows

#### Scenario: Dithering before splitting
- **WHEN** dithering is enabled
- **THEN** it SHALL run on the whole page before it is split into bands, so no seam appears between bands

### Requirement: Rendering stops on cancel
Rendering and conversion of a PDF SHALL stop when the job is cancelled.

#### Scenario: Cancel while a PDF is rendered or converted
- **WHEN** `cancel()` is called while a PDF is being rendered or converted
- **THEN** the work stops at the next page boundary or after at most 128 rows, no more bytes are sent, and every bitmap created so far is recycled

#### Scenario: Cancel in dramBatch
- **WHEN** `cancel()` is called during `dramBatch`
- **THEN** the file in progress is finished before the call ends with `cancelled`
