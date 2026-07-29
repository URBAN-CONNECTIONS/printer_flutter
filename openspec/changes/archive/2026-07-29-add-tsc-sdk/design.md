## Context

The `printer_flutter` project is a Flutter plugin designed for thermal label printing on mobile devices. To interface with TSC thermal printers, the native vendor library `tscsdk.jar` must be included in the Android platform project and accessed via Flutter's `MethodChannel`.

Currently, `tscsdk.jar` exists in the repository root but is not linked to the Android build system or imported into the Android native Kotlin plugin codebase.

## Goals / Non-Goals

**Goals:**
- Relocate `tscsdk.jar` into `android/libs/`.
- Configure Gradle to include `android/libs/*.jar` as compile-time and runtime dependencies.
- Expose a unified Kotlin `MethodChannel` handler calling `com.example.tscdll.TSCActivity` (Bluetooth), `TSCUSBActivity` (USB), and `TscWifiActivity` (Wi-Fi).
- Define standard method signatures in Dart to trigger printer connections and TSPL command execution.

**Non-Goals:**
- iOS implementation for `tscsdk.jar`.
- Replacing existing standard Bluetooth SPP fallback functionality for non-TSC printers.

## Decisions

### Decision 1: Single Kotlin MethodChannel Wrapper for `tscsdk`
We will use a single `MethodChannel` named `printer_flutter` in `PrinterFlutterPlugin.kt` that delegates to `TSCActivity`, `TSCUSBActivity`, and `TscWifiActivity` based on connection mode (`bluetooth`, `usb`, `wifi`).

*Rationale*: Keeps the Dart client API clean and unified while abstracting away the multi-activity structure of the vendor JAR.

*Alternatives Considered*: Creating separate channels (`printer_flutter/bluetooth`, `printer_flutter/usb`) — rejected to avoid unnecessary channel management overhead in Flutter.

### Decision 2: Local FileTree Dependency in `build.gradle`
In `android/build.gradle`, add `implementation fileTree(dir: 'libs', include: ['*.jar'])`.

*Rationale*: Standard Gradle convention for bundling local third-party SDK JAR files into an Android plugin without requiring external repository publishing.

### Sequence Diagram: Flutter to TSC Printer Execution

```
Flutter (Dart)            PrinterFlutterPlugin (Kotlin)           TSC SDK (TSCActivity)           Hardware Printer
      │                                │                                │                                │
      │ ── openPort(macAddress) ──────▶│                                │                                │
      │                                │ ── tsc.openport(macAddress) ──▶│                                │
      │                                │                                │ ── BT SPP Socket Connect ─────▶│
      │ ◀── result (status) ───────────│◀── result status ──────────────│                                │
      │                                │                                │                                │
      │ ── sendTspl(command) ─────────▶│                                │                                │
      │                                │ ── tsc.sendcommand(command) ──▶│                                │
      │                                │                                │ ── Write TSPL Bytes ──────────▶│
      │ ◀── result (success) ──────────│◀── success ────────────────────│                                │
      │                                │                                │                                │
      │ ── closePort() ───────────────▶│                                │                                │
      │                                │ ── tsc.closeport() ───────────▶│                                │
      │                                │                                │ ── Close Socket ──────────────▶│
```

## Risks / Trade-offs

- [Vendor SDK Threading] → `TSCActivity.openport()` may perform blocking I/O on the main thread. *Mitigation*: Wrap native SDK invocations in background coroutines or executor threads within Kotlin before returning results over `MethodChannel`.
- [Android 12+ Bluetooth Permissions] → Calling Bluetooth connections without `BLUETOOTH_CONNECT` permission granted at runtime will throw SecurityExceptions. *Mitigation*: Ensure documentation and plugin setup guide developers to request runtime permissions prior to invoking `openPort`.
