## Context

On Android 12+ (Build.VERSION.SDK_INT >= 31), Bluetooth Classic SPP connections require runtime permission grants (`BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN`). On older Android versions, location access (`ACCESS_FINE_LOCATION`) is needed for Bluetooth device operations.

Adding native runtime permission handling inside `PrinterFlutterPlugin.kt` avoids external Dart dependencies and allows Flutter apps to request permissions prior to attempting printer connection.

## Goals / Non-Goals

**Goals:**
- Implement `ActivityAware` interface in `PrinterFlutterPlugin` to access current `Activity`.
- Register a `PluginRegistry.RequestPermissionsResultListener` to process system permission dialog results.
- Dynamically select required permissions based on Android SDK version.
- Add a "Request Bluetooth Permissions" button to `example/lib/main.dart`.

**Non-Goals:**
- Adding third-party permission plugins like `permission_handler`.

## Decisions

### Decision 1: Implement `ActivityAware` in Plugin Class
`PrinterFlutterPlugin` will implement `ActivityAware` and store a reference to the active `Activity` binding.

*Rationale*: `ActivityCompat.requestPermissions()` requires an active `Activity` reference.

### Decision 2: Target Android SDK Version Branching
- **Android 12+ (SDK >= 31)**: Request `Manifest.permission.BLUETOOTH_CONNECT` and `Manifest.permission.BLUETOOTH_SCAN`.
- **Android < 12 (SDK < 31)**: Request `Manifest.permission.ACCESS_FINE_LOCATION`.

*Rationale*: Adheres to Android security guidelines while maintaining backwards compatibility across API levels.

### Sequence Diagram: Runtime Permission Request Flow

```
Flutter (Dart)            PrinterFlutterPlugin (Kotlin)             Android OS System            User
      │                                │                                    │                      │
      │ ── requestPermissions() ──────▶│                                    │                      │
      │                                │ ── checkSelfPermission() ────────▶│                      │
      │                                │ ── requestPermissions() ──────────▶│                      │
      │                                │                                    │ ── Show Dialog ─────▶│
      │                                │                                    │ ◀─ Allow/Deny ───────│
      │                                │ ◀─ onRequestPermissionsResult() ───│                      │
      │ ◀── result (true/false) ───────│                                    │                      │
```

## Risks / Trade-offs

- [Activity Detached] → `requestPermissions` invoked when no Activity is attached will fail. *Mitigation*: Check `activity != null` and return `false` or an error code if Activity is detached.
