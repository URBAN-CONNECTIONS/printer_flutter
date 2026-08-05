# pdf-dram-batch-printing Specification

## Purpose
TBD - created by archiving change add-dram-batch-pdf-printing. Update Purpose after archive.
## Requirements
### Requirement: DRAM Batch PDF Strategy Configuration
The system SHALL expose `dramBatch` as a valid `PdfPrintStrategy` option in `PdfPrintOptions` so developers can request buffering pages into printer DRAM before execution.

#### Scenario: Developer selects DRAM batch strategy
- **WHEN** developer configures `PdfPrintOptions(strategy: PdfPrintStrategy.dramBatch)`
- **THEN** system serializes `strategy` string as `"dramBatch"` in platform channel invocation

### Requirement: Pre-buffering rendered PDF pages in TSPL DRAM
When `strategy` is `"dramBatch"`, the native printer helper SHALL render each PDF page to monochrome bytes using PCX encoding with Floyd-Steinberg dithering support (when `enableDithering: true`) and upload each page to printer DRAM before triggering print execution.

#### Scenario: Uploading page bitmaps to DRAM with dithering
- **WHEN** PDF is processed with `dramBatch` strategy and `enableDithering: true`
- **THEN** system applies Floyd-Steinberg error diffusion dithering when encoding each page into 1-bit PCX binary format
- **THEN** system transmits TSPL `KILL "P*.PCX"` prior to uploading
- **THEN** system downloads `P<i>.PCX` files into printer DRAM via `DOWNLOAD` commands
- **THEN** system queues `PUTPCX 0,0,"P<i>.PCX"` commands for continuous printing


### Requirement: Continuous batch print execution and memory cleanup
Once the script is resident in printer DRAM, the system SHALL transmit a command to execute the script via `RUN "JOB.BAS"`.

#### Scenario: Executing continuous batch print
- **WHEN** all page bitmaps have completed uploading to DRAM inside `JOB.BAS`
- **THEN** system transmits `RUN "JOB.BAS"`
- **THEN** thermal printer prints all pages continuously without physical stopping or stuttering between pages

