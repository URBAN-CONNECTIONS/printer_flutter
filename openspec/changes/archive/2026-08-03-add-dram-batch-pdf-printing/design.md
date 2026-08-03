## Context

Thermal label printing for multi-page PDFs over Bluetooth Classic SPP suffers from two core issues:
1. `pageByPage` sends `PRINT 1,1` after every page, forcing the printer motor to stop and wait while the mobile app renders, dithers, and transmits subsequent pages.
2. `unifiedRoll` stitches all pages into one large continuous bitmap, causing buffer overflow and hardware reboots on mobile thermal printers with 2MB-4MB RAM.

The TSPL `DOWNLOAD` command allows raw files (monochrome bitmaps) to be written into the printer's volatile DRAM without starting the physical print engine. Combined with `PUTBMP` and `KILL`, we can upload all rendered pages first, then execute a single fast, continuous print job.

## Goals / Non-Goals

**Goals:**
- Add `dramBatch` strategy to `PdfPrintStrategy` enum.
- [x] Implement `printDramBatch` in Android `PdfPrintHelper.kt`.
- [x] Buffer all rendered pages and TSPL commands into a single `JOB.BAS` script downloaded to DRAM.
- Prevent physical motor stuttering and printer memory crashes.

**Non-Goals:**
- Flash memory persistence (files should only reside in DRAM).
- iOS native implementation (currently Android-focused).

## Decisions

### Decision 1: Use TSPL `DOWNLOAD "JOB.BAS"` + `BITMAP` over single `BITMAP` stream
- **Rationale**: Downloading a complete TSPL script (`JOB.BAS`) containing all individual `BITMAP` commands allows the printer to silently receive all page data into DRAM without activating the motor. When `RUN "JOB.BAS"` executes, the printer processes the script from internal RAM instantly, preventing Bluetooth bottlenecks from causing stuttering, while keeping memory usage safe.
- **Alternatives Considered**:
  - `DELAY 15000` before `pageByPage`: Printer blocks Bluetooth input buffer during delay, leading to exact same stuttering after delay expires.
  - `unifiedRoll`: Exceeds printer RAM, causes printer reboots.

### Decision 2: Sequence Diagram for `dramBatch` Flow

```text
Flutter App / Android Plugin                   TSPL Thermal Printer
───────────────────────────                   ────────────────────
            │                                           │
            │ 1. Pre-clear: KILL "JOB.BAS"           ──▶│ 🧹 Purge stale DRAM files
            │                                           │
            │ 2. Send: DOWNLOAD "JOB.BAS"            ──▶│ (Begin script download)
            │ 3. Render Page 0 -> monoBytes             │
            │ 4. Send: BITMAP ... <monoBytes>        ──▶│ (Buffered in DRAM script)
            │    Send: PRINT 1,1                     ──▶│
            │ 5. Render Page 1 -> monoBytes             │
            │ 6. Send: BITMAP ... <monoBytes>        ──▶│ (Buffered in DRAM script)
            │    Send: PRINT 1,1                     ──▶│
            │ 7. Send: EOP                           ──▶│ (End script download)
            │                                           │
            │ 8. Send: RUN "JOB.BAS"                 ──▶│
            │                                           │ ⚡ Prints P0 & P1
            │                                           │    continuously!
```

### Decision 3: DRAM Memory Cleanup
- **Rationale**: 
  1. **Pre-execution cleanup**: Issue `KILL "JOB.BAS"` *before* downloading to purge any orphaned scripts.

## Risks / Trade-offs

- **[Risk] Extremely large PDFs (e.g. 50+ pages) filling DRAM**:
  - *Mitigation*: Each page is ~100-150KB. A 10-page document uses ~1.5MB DRAM. For extremely long documents, users can still fallback to `pageByPage`.
- **[Risk] Firmware incompatibility with `DOWNLOAD`**:
  - *Mitigation*: `DOWNLOAD` and `PUTBMP` are standard core TSPL/TSPL2 commands supported across TSC, Xprinter, and clone models.
