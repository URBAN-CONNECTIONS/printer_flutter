## Why

On Android 12+ (API 31+), attempting Bluetooth socket connections without user-granted runtime permissions (`BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN`) results in security exceptions. Adding a native Kotlin runtime permission handler eliminates the need for external third-party packages while allowing Flutter applications to request and verify permissions seamlessly.

## What Changes

- Implement `ActivityAware` and `PluginRegistry.RequestPermissionsResultListener` in `PrinterFlutterPlugin.kt`.
- Expose `requestPermissions` endpoint over `MethodChannel` returning boolean status.
- Add `requestPermissions()` method to Dart `PrinterFlutter` API and platform interface.
- Add a "Request Bluetooth Permissions" button and log handler in `example/lib/main.dart`.

## Non-goals

- Third-party permission library integration (e.g. `permission_handler`).
- Non-Bluetooth permission management.

## Capabilities

### New Capabilities
- `runtime-permissions`: Native Android runtime permission handling for Bluetooth and Location permissions required for thermal printing.

### Modified Capabilities

*(None)*

## Impact

- **Android Module**: Updates `PrinterFlutterPlugin.kt` to bind to Activity lifecycle and listen for permission dialog results.
- **Dart API**: Adds `requestPermissions()` to `PrinterFlutterPlatform`, `MethodChannelPrinterFlutter`, and `PrinterFlutter`.
- **Example App**: Adds a permission request button to `example/lib/main.dart`.
