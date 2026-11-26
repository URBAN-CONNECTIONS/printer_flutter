## ADDED Requirements

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
