## Why

Currently, calling printing methods (`printPdf`, `sendTspl`, `sendRawBytes`) closes the Bluetooth SPP socket right after command transmission or leaves socket lifecycle ambiguous. Closing the socket immediately severs connection before the hardware thermal engine finishes printing physical paper, while keeping it open unconditionally makes manual socket management cumbersome. 

Providing a configurable `closePort` parameter with an asynchronous `OUT` marker background completion check ensures that the hardware thermal engine finishes printing the physical paper before closing the socket. The Flutter call awaits this completion, allowing the UI to show a loading state or cancel button while the print finishes, and safely closes the socket only after physical label execution completes.

## What Changes

- Add optional `bool closePort = false` parameter to Dart public API methods (`sendTspl`, `sendRawBytes`, `printPdf`).
- Modify native Kotlin plugin (`PrinterFlutterPlugin.kt`) to support background socket termination:
  - If `closePort == false`: socket remains open for subsequent print operations.
  - If `closePort == true`: native layer appends TSPL `OUT "COMPLETED\r\n"` marker, uses a native background executor thread to wait for `"COMPLETED"` from the printer, and only then returns success to Flutter and calls `tsc.closeport()`.

## Capabilities

### New Capabilities
- `socket-auto-close-control`: Configurable option for automatic background socket termination after TSPL completion marker detection.

### Modified Capabilities
- `tsc-printer-connection`: Modified connection handling to support asynchronous background port closing without severing ongoing hardware print jobs.

## Impact

- **Flutter API**: Non-breaking addition of `closePort` optional named parameter.
- **Android Native**: `PrinterFlutterPlugin.kt` background executor logic for `OUT` marker parsing and deferred `closeport()`.
- **Dependencies**: No external dependencies added.

## Non-goals

- Real-time status reporting UI widgets or error recovery UI dialogs.
- Automatic continuous polling loops (`<ESC>!?`) during active printing (deferred to a future status query change).
