## ADDED Requirements

### Requirement: DRAM Batch PDF Strategy Configuration
The system SHALL expose `dramBatch` as a valid `PdfPrintStrategy` option in `PdfPrintOptions` so developers can request buffering pages into printer DRAM before execution.

#### Scenario: Developer selects DRAM batch strategy
- **WHEN** developer configures `PdfPrintOptions(strategy: PdfPrintStrategy.dramBatch)`
- **THEN** system serializes `strategy` string as `"dramBatch"` in platform channel invocation

### Requirement: Pre-buffering rendered PDF pages in TSPL DRAM
When `strategy` is `"dramBatch"`, the native printer helper SHALL render each PDF page to monochrome bytes and upload it to printer DRAM via TSPL `DOWNLOAD "JOB.BAS"` as a complete script without triggering instant physical printing.

#### Scenario: Uploading page bitmaps to DRAM
- **WHEN** multi-page PDF is processed with `dramBatch` strategy
- **THEN** system transmits TSPL `KILL "JOB.BAS"` prior to uploading to purge any orphan script files from previous runs
- **THEN** system transmits TSPL `DOWNLOAD "JOB.BAS"` followed by `BITMAP` commands containing monochrome byte arrays for each page `0..N-1` into printer DRAM
- **THEN** system transmits `EOP` to finalize the script
- **THEN** print motor remains completely stationary during the upload phase

### Requirement: Continuous batch print execution and memory cleanup
Once the script is resident in printer DRAM, the system SHALL transmit a command to execute the script via `RUN "JOB.BAS"`.

#### Scenario: Executing continuous batch print
- **WHEN** all page bitmaps have completed uploading to DRAM inside `JOB.BAS`
- **THEN** system transmits `RUN "JOB.BAS"`
- **THEN** thermal printer prints all pages continuously without physical stopping or stuttering between pages
