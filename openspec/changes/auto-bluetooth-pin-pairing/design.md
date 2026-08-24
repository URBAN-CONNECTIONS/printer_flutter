## Context

Thermal label and receipt printers communicate over Bluetooth Classic SPP (Serial Port Profile). When a device is paired for the first time, Android initiates an authentication sequence. For legacy devices, this results in an `ACTION_PAIRING_REQUEST` broadcast, prompting the user for a 4-digit PIN (defaulting to `0000` or `1234`). Without automated pairing, the user is interrupted by a system modal dialog, breaking automated and kiosk printing workflows.

## Goals / Non-Goals

**Goals:**
- Intercept Bluetooth pairing requests on Android using a high-priority `BroadcastReceiver`.
- Automatically provide PIN `"0000"` (with fallback to `"1234"`) and confirm pairing programmatically.
- Filter pairing requests strictly by target printer MAC address.
- Maintain zero battery/CPU overhead by binding the receiver to the Activity lifecycle.
- Provide clean Dart API parameters (`pin`, `fallbackPin`) while maintaining full backward compatibility.

**Non-Goals:**
- Support for BLE numeric comparison or passkey entry pairing (only legacy SPP PIN pairing is targeted).
- Bypassing standard Android runtime permission requirements (`BLUETOOTH_CONNECT`).

## Decisions

### Decision 1: Activity Lifecycle-Bound BroadcastReceiver
- **Rationale**: Registering the `BroadcastReceiver` during `onAttachedToActivity` and unregistering in `onDetachedFromActivity` ensures the receiver is ready for any pairing request without maintaining background services or polling threads. In Android, registered broadcast receivers are purely event-driven and consume 0% CPU/battery while idle.
- **Alternatives considered**:
  - *Dynamic registration only during `openPort`*: Risk of missing pairing intents if socket connection triggers pairing before or after the narrow window.
  - *Manifest static receiver*: Restricted in modern Android versions for dynamic device state handling.

### Decision 2: Target MAC Filtering
- **Rationale**: When `openPort` is called, store the normalized target MAC address. In the pairing receiver, ignore any pairing request where `device.address` does not match the target MAC, allowing standard OS handling for other peripherals (e.g. headphones).
- **Alternatives considered**:
  - *Global auto-pairing for all devices*: Could accidentally intercept pairing requests for unrelated Bluetooth devices.

### Decision 3: PIN Injection and Broadcast Abort
- **Rationale**:
  - Call `device.setPin(pinBytes)` with the configured PIN (default `"0000"`).
  - Call `device.setPairingConfirmation(true)` to accept pairing.
  - Call `abortBroadcast()` to suppress the system pairing dialog from popping up on the user's screen.
  - Listen for `ACTION_BOND_STATE_CHANGED` to detect if pairing failed due to authentication (`UNBOND_REASON_AUTH_FAILED`), triggering an automatic retry with `fallbackPin` (`"1234"`).

## Sequence Diagram

```
Flutter (Dart)            PrinterFlutterPlugin (Kotlin)        Android OS / Bluetooth Stack         Thermal Printer
      │                              │                                     │                               │
      │── openPort(mac, pin="0000") ─▶                                     │                               │
      │   (Set target MAC & PIN)     │── TSCActivity.openport(mac) ────────▶                               │
      │                              │                                     │── RFCOMM Connection Request ─▶│
      │                              │                                     │◀─ Pairing Challenge (PIN) ────│
      │                              │◀─ ACTION_PAIRING_REQUEST (Ordered) ─│                               │
      │                              │   [Filter MAC == targetMac]         │                               │
      │                              │── device.setPin("0000") ───────────▶│                               │
      │                              │── device.setPairingConfirmation ───▶│                               │
      │                              │── abortBroadcast() (Hide Dialog) ──▶│                               │
      │                              │                                     │── Auth Response (0000) ──────▶│
      │                              │                                     │◀─ Bonded / Connected ─────────│
      │◀─ Success ("0") ─────────────│◀─ Port Opened Successfully ─────────│                               │
```

## Risks / Trade-offs

- **[OEM Skin UI Flashing]** On some heavily customized OEM skins (MIUI, EMUI), the system dialog might flash for a millisecond before `abortBroadcast()` dismisses it.
  - *Mitigation*: This is cosmetic; pairing succeeds without user interaction.
- **[Android 12+ Permission Requirement]** `device.setPin()` requires `BLUETOOTH_CONNECT` permission on Android 12+ (API 31+).
  - *Mitigation*: The plugin already requests and verifies `BLUETOOTH_CONNECT` before initiating Bluetooth operations.
- **[Wrong PIN for non-standard printers]** If a printer uses a custom PIN other than `0000` or `1234`.
  - *Mitigation*: Allow developers to pass explicit `pin` and `fallbackPin` strings in `openPort`.
