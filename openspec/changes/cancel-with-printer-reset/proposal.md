## Why

On a Ribetec printer, cancelling (or losing the connection) while the plugin is still sending an image leaves the printer waiting for the missing bytes. Its screen shows `Downloading...`, it refuses every Bluetooth connection, and only turning it off and on recovers it (see the tests on issue #1). The reset command `<ESC>!R` cannot be sent afterwards, because there is no way to connect. It can only be sent before the connection is closed.

## What Changes

- `cancel()` takes an optional `resetPrinter` flag (default `false`). With it, the plugin stops sending at the next chunk, sends `<ESC>!R` on the connection that is still open, and only then closes it. The job ends with `cancelled`.
- If the job thread cannot reach that point in a few seconds (a blocked write), the plugin closes the connection anyway, as `cancel()` does today.
- After a reset sent by cancel, the next `openPort` waits for the printer to come back and does not reset it a second time.
- The example app gets a test that cancels with the reset and checks that the printer prints again.

## Non-goals

- Recovering a printer after the connection was lost by itself (range, battery): there is no open connection to send the reset through.
- Making the reset the default: `<ESC>!R` deletes what was downloaded to the printer.
- Reading the printer status (`<ESC>!?`).

## Impact

- `PrinterConnection`, `PrinterSession` and the plugin's `cancel` handler (Kotlin).
- `cancel({bool resetPrinter = false})` in the Dart API and the method channel. Compatible: the default keeps today's behaviour.
- Spec `print-job-limits`, README and CHANGELOG.
