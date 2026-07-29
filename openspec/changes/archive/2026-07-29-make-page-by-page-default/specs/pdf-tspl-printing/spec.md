## MODIFIED Requirements

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
