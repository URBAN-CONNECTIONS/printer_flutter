## Why

Currently, the plugin does not expose a way to specify the number of copies to print in a single command. Users who need multiple copies of the same label or document must send the print command multiple times, which is inefficient and can cause delays or connectivity issues. Adding a `copies` argument allows the printer to handle duplication internally using the TSPL `PRINT` command (e.g., `PRINT m,1`).

## What Changes

- Add an optional `copies` parameter (defaulting to 1) to the Dart print methods (e.g., `printPdf`).
- Update the platform interface and method channel to pass the `copies` argument to the native Android implementation.
- Update the Android native code and TSPL command builder to append the `PRINT <copies>,1` command using the provided copies value instead of a hardcoded `1`.

## Capabilities

### New Capabilities
- `print-copies`: The ability to specify the number of copies to print when sending a print command, mapping to the TSPL `PRINT` command arguments.

### Modified Capabilities

## Impact

- **Dart API**: Adds an optional `int copies = 1` parameter to print methods. This is backwards compatible.
- **MethodChannel**: The argument map for print methods will now include `"copies"`.
- **Android Native**: The Kotlin code that handles printing and builds TSPL commands will read the `"copies"` argument and format the `PRINT` command accordingly.
