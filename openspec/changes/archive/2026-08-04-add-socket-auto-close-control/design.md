## Context

Currently, the native Android plugin executes print operations over Bluetooth SPP using `TSCActivity.sendcommand()`. When a caller wants to close the port, doing so immediately after transmitting bytes risks closing the socket while the printer's motor is mid-print. Keeping the connection open works well for consecutive print jobs but requires callers to specify when and how the port should automatically close without blocking Flutter UI threads.

## Goals / Non-Goals

**Goals:**
- Provide an optional `bool closePort = false` parameter across `sendTspl`, `sendRawBytes`, and `printPdf`.
- Return MethodChannel success to Flutter only after the print operation completes (if closePort is true).
- If `closePort == true`, delegate port closure to a background executor thread that appends `OUT "COMPLETED\r\n"`, waits for the printer hardware marker response, calls `tsc.closeport()`, and then returns success to the Flutter MethodChannel.

- Interactive status error dialogs inside the plugin.

## Flow & Sequence Diagram

```
Flutter (Dart)                          PrinterFlutterPlugin (Kotlin)             TSC Hardware Printer
     │                                                │                                          │
     │ ─── printPdf(closePort: true) ───────────────▶ │                                          │
     │                                                │ ─── tsc.sendcommand(BITMAP...) ────────▶ │ (Buffering & Printing)
     │                                                │ ─── tsc.sendcommand("OUT END\r\n") ────▶ │
     │                                                │ ◄── "END" string received ───────────────│ (Label printed)
     │                                                │ ─── tsc.closeport()                      │ (Socket closed)
     │ ◄── Returns "Success" after wait ──────────────│                                          │
     │     (Flutter UI can show cancel button)        │                                          │
```

## Decisions

### Decision 1: Await Completion Before Flutter Return
- **Choice**: Return `result.success("Success")` after the background wait task completes.
- **Rationale**: The Flutter app wants to know when printing is actually finished. Awaiting the `OUT` marker allows the Flutter UI to show a loading/cancel state for the duration of the print job, rather than instantly returning while the printer is still running.

### Decision 2: TSPL `OUT` Marker with No Hardcoded Timeout
- **Choice**: Send `OUT "COMPLETED\r\n"` command and listen via background worker indefinitely until the marker is received (or the socket is manually closed/cancelled by the user).
- **Rationale**: Thermal printing time varies greatly depending on label length. A hardcoded timeout (e.g., 5 seconds) would incorrectly abort long print jobs. Since the Flutter UI provides a manual "Cancel" button, we delegate the timeout/cancellation responsibility to the user/UI level rather than enforcing it in the native socket layer.

## Risks / Trade-offs

- [Unresponsive Printer on `closePort: true`] → *Mitigation*: The Flutter UI provides a "Cancel" button to allow the user to manually abort if the printer runs out of paper or hangs, preventing an inescapable infinite loading state.
- [Race Condition with Rapid Subsequent `openPort`] → *Mitigation*: Existing `openPort` implementation safely calls `closeport()` prior to establishing a new socket.
