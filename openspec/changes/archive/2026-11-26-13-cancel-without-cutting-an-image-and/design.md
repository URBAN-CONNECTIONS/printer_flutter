## Context

See proposal.md. Today `PrinterConnection.cancel()` sets a flag and closes the transport at once, from the main thread (`PrinterFlutterPlugin.kt` `cancel` branch). `PrinterConnection.write` splits data in 4 KB chunks with no notion of blocks. `PdfPrintHelper` sends one `BITMAP` per image (`printPageByPage`, `printUnifiedRoll`) or one `DOWNLOAD` per page (`printDramBatch`), and its page loops never check for cancel.

## Goals / Non-Goals

- Goals: cancel at a block boundary, short blocks, rendering that stops, no main-thread blocking.
- Non-Goals: see "Out of scope" in the issue (blank-band skipping, splitting `dramBatch`, forced thread interruption, deleting `dramBatch` files).

## Decisions

- **Block-aware write.** `PrinterConnection` gets a way to write a block atomically with respect to cancel: while a block is being written, `cancel()` only sets the flag; the close happens when the block ends (or when `writeTimeout` closes it). Outside a block, `cancel()` closes at once as today. Alternative: always close at once (strands the printer).
- **Cancel checks between blocks.** A `PrinterConnection.checkCancelled()` (throws `CANCELLED`) is called by the senders before each next block, so nothing is sent after the one in progress and no `PRINT` follows.
- **Close off the main thread.** The plugin `cancel` branch hands the close to the watchdog executor and answers `Success` at once. `PrinterSession.cancel()` keeps its lock logic; only the transport close is scheduled on the watchdog.
- **Band splitter.** A plain Kotlin function splits a monochrome buffer (`widthBytes` per row, `height` rows) into `BITMAP 0,y,widthBytes,rows,0,` headers plus data, `rows <= 200`. Dithering/conversion runs on the whole page first (C1).
- **Render cancel.** `PdfPrintHelper` takes a `isCancelled` callback, checks it at each page and every 128 rows of the conversion/crop loops, and recycles bitmaps in `finally`.
- **Messages.** The timeout message while finishing a block says the printer may need to be turned off and on.

## Risks / Trade-offs

- Small bands add header bytes → negligible (about 20 bytes per 14 KB).
- A band must keep the printed result identical → covered by a JVM test that compares the reassembled bands with the single-bitmap data.

## Assumptions

- The 200-row band uses the `y` offset of the `BITMAP` command; the TSPL `BITMAP x,y,width,height,mode,data` form is supported by the target printers as stated in the issue.
- `PrinterErrorCode.CANCELLED` is the existing `cancelled` Dart error type.
- No Dart API or channel change is needed.
## From the issue: Decisions

### D7. Cancel at a safe boundary, off the main thread
Decided: cancel never cuts a `BITMAP` or `DOWNLOAD` block; it finishes the block in progress and stops before the next command. Rendering checks the cancel flag at every page and at least every 128 rows. The socket close runs on the watchdog thread.
Why: a cut block is what leaves the RT-320 PB in `Downloading...` (reproduced in #1, step 6), and the project rule says Bluetooth code never blocks the main thread.
Discarded: closing at once (strands the printer); interrupting threads by force.

### D20. Images go in bands of at most 200 rows
Decided: `pageByPage` and `unifiedRoll` send each image as `BITMAP` bands of at most 200 rows (about 14 KB at 72 mm).
Why: a page of the fine ticket is one `BITMAP` of about 125 KB, so finishing the block in progress would make a cancel wait 2 to 6 seconds, and with `unifiedRoll` the whole 389 KB ticket is one block; small bands keep the wait under about a second and shorten the moment in which a lost connection strands the printer.
Discarded: one `BITMAP` per page (slow cancel); leaving bands to the speed change.

### D18. No automatic printer reset
Decided: the plugin does not send `<ESC>!R` on its own, and the README says `resetPrinterAfterFailure` does not help on the RT-320 PB over Bluetooth.
Why: tested in #1, `<ESC>!R` does nothing over Bluetooth, with the printer healthy or stuck.
Discarded: resetting automatically after a cut job.

## From the issue: Constraints

- C1: The printed result does not change: same width, height, dithering and copies, for 58 mm, 72 mm, 80 mm and custom widths.
- C2: Sockets and streams are closed on every path, and nothing blocks the main thread.
- C3: The band splitting, the block-boundary logic and the cancel flag are plain Kotlin tested on the JVM with a fake transport; rendering bound to `Bitmap` is verified by hand.

## From the issue: Code pointers

- `android/src/main/kotlin/com/urbanconnections/printer_flutter/PrinterFlutterPlugin.kt`: the `cancel` branch runs on the main thread.
- `android/src/main/kotlin/com/urbanconnections/printer_flutter/PrinterConnection.kt`: `cancel` closes the transport at once; `write` splits data in 4 KB chunks with no notion of blocks.
- `android/src/main/kotlin/com/urbanconnections/printer_flutter/PdfPrintHelper.kt`: `printPageByPage` and `printUnifiedRoll` send one `BITMAP` per image; `printDramBatch` one `DOWNLOAD` per page; the page loops have no cancel check.

## From the issue: Verification

- Print the 4-page fine ticket (72 x 240 mm pages) with `pageByPage` on the RT-320 PB and compare it with a print from the current plugin: no lines or gaps between bands.
- With `dramBatch`, cancel 4 seconds into a 2-copy print; look at the screen before touching anything: it must not show `Downloading...`, and the next print must work without turning the printer off.
- Repeat with `pageByPage`, the strategy apps use on Ribetec printers; note how long the cancel takes to return.
- Cancel while a long PDF renders: no data reaches the printer and the next print works.

## Documentation from the issue

- Add this entry to `GLOSSARY.md`, under its existing heading, after the existing ones, exactly as written. It is vocabulary only; nothing else in that file changes.

**Stuck printer**:
A printer left waiting for the rest of a cut image or file, shown as `Downloading...`; only turning it off and on recovers it.
_Avoid_: hung, frozen
