# printer_flutter

A Flutter plugin for Bluetooth thermal printer discovery, SPP connection, and PDF printing with TSPL/TSC command generation.

[![pub package](https://img.shields.io/pub/v/printer_flutter.svg)](https://pub.dev/packages/printer_flutter)
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/URBAN-CONNECTIONS/printer_flutter/blob/main/LICENSE)

## Features

- **Bluetooth Discovery & Connection**: Scan for nearby Bluetooth Classic devices and connect via MAC address using SPP (Serial Port Profile).
- **TSPL / TSC Command Builder**: Mandatory `LabelConfig(widthMm, heightMm, gapMm)` initialization for formatted text, barcodes, QR codes, and raster graphics.
- **PDF Thermal Printing**: Render PDF document pages into monochrome bitmaps scaled for thermal paper (58mm, 80mm, or label sizes).
- **Printing Strategies**: Support for both `pageByPage` (renders and transmits each page individually) and `monolithic` (combines pages into a single print job) modes.
- **Automatic Whitespace Trimming**: Option to automatically trim blank margins from PDF pages before printing.
- **Runtime Permissions**: Built-in permission check helper (`checkAndRequestPermissions`) for Android 12+ Bluetooth requirements.

---

## Getting Started

### 1. Platform Permissions

#### Android Setup (`android/app/src/main/AndroidManifest.xml`)

Add the following permissions to your `AndroidManifest.xml`:

```xml
<!-- Bluetooth permissions for Android 11 and below -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />

<!-- Bluetooth permissions for Android 12 (API 31) and above -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

#### iOS Setup (`ios/Runner/Info.plist`)

Add the Bluetooth usage description to `Info.plist`:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app requires Bluetooth access to connect to thermal label printers.</string>
```

---

## Usage Example

```dart
import 'package:flutter/material.dart';
import 'package:printer_flutter/printer_flutter.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final PrinterFlutter _printer = PrinterFlutter();
  List<BluetoothPrinterDevice> _devices = [];
  bool _isConnected = false;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('Printer Flutter Example')),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            ElevatedButton(
              onPressed: _requestPermissionsAndScan,
              child: const Text('Scan Bluetooth Printers'),
            ),
            ..._devices.map((device) => ListTile(
                  title: Text(device.name ?? 'Unknown Device'),
                  subtitle: Text(device.address),
                  onTap: () => _connectToDevice(device.address),
                )),
            if (_isConnected)
              ElevatedButton(
                onPressed: _printSamplePdf,
                child: const Text('Print PDF'),
              ),
          ],
        ),
      ),
    );
  }

  Future<void> _requestPermissionsAndScan() async {
    final granted = await _printer.checkAndRequestPermissions();
    if (granted) {
      final devices = await _printer.getBondedDevices();
      setState(() {
        _devices = devices;
      });
    }
  }

  Future<void> _connectToDevice(String address) async {
    final success = await _printer.connect(address);
    setState(() {
      _isConnected = success;
    });
  }

  Future<void> _printSamplePdf() async {
    // Example: Print PDF with TSPL configuration
    // await _printer.printPdfBytes(
    //   pdfBytes,
    //   config: const LabelConfig(widthMm: 76, heightMm: 130, gapMm: 3),
    //   strategy: PdfPrintStrategy.pageByPage,
    //   trimWhitespace: true,
    // );
  }
}
```

---

## Print in one call (atomic call)

Pass `macAddress` to `printPdf`, `sendTspl` or `sendRawBytes` and the plugin does the whole job in one native call: it connects, sends, confirms and closes the connection, also when something fails. There is no `openPort` or `closePort` step to forget or to run twice.

```dart
final printer = PrinterFlutter();

try {
  await printer.printPdf(
    filePath: path,
    macAddress: '00:00:00:00:00:00',
    // Optional: pin, fallbackPin, connectTimeout, completionTimeout, confirmCompletion.
  );
} on PrinterException catch (e) {
  if (e.type == PrinterErrorType.timeout) {
    // "Not confirmed": the printer may still have printed. Check it before printing again.
  }
}
```

`pin`, `fallbackPin` and `connectTimeout` work as in `openPort`.

- **Queue.** Atomic calls run one at a time, first in, first out, so two quick taps never close each other's connection. A call that is waiting starts clean after the one before it ends.
- **Takeover.** A connection left open by hand with `openPort` is closed when an atomic call starts, and the atomic call opens its own.
- **Do not mix the two styles** (`openPort`, send, `closePort`) on the same printer.
- `closePort: true` has no effect when `macAddress` is set; the call always closes the connection.
- `cancel()` ends only the call that is running. Calls still waiting in the queue run afterwards.
- Without `macAddress` the three-step API works exactly as before.

### Completion confirmation

By default (`confirmCompletion: true`) the call asks the printer for an `OUT` answer after everything was sent and waits for it up to `completionTimeout`. That answer means the printer processed everything it was sent. It does not mean it finished printing, and the plugin adds no `EOJ` command.

A `PrinterErrorType.timeout` after everything was sent means "not confirmed": the printer did not confirm that it processed the job, and it may still have printed. It does not mean "it did not print", so check the printer before printing again.

With `confirmCompletion: false` the call does not wait for the printer's answer: it returns after the bytes are sent and the connection is closed. Before closing, the plugin still waits a short close-grace delay (1.5 s) so the printer receives the last bytes. Use it when speed matters more than knowing the printer processed the job.

### Where the time goes

On Android each atomic call writes one line to the log with the milliseconds of each phase (it holds no MAC address and no PIN):

```
Atomic print phases: connect=210ms render=40ms send=900ms confirm=120ms
```

`render` is only for `printPdf`; it is 0 for `sendTspl` and `sendRawBytes`. With `confirmCompletion: false`, `confirm` is the close-grace delay. Read it with:

```
adb logcat -s PrinterFlutterPlugin:I
```

---

## Limits and errors

Every call ends within its limits; no call can wait forever.

| Limit | Default | Applies to |
|---|---|---|
| `connectTimeout` | 15 s | `openPort`, and the connect step of an atomic call |
| `writeTimeout` | 20 s per 4 KB chunk | `printPdf`, `sendTspl`, `sendRawBytes` |
| `completionTimeout` | 60 s from the last byte | the printer's confirmation that it processed everything it was sent, when `closePort: true`, or in an atomic call with `confirmCompletion: true`; not a confirmation that printing finished |

Failures throw `PrinterException`:

```dart
try {
  await printer.openPort(mac);
  await printer.printPdf(
    filePath: path,
    closePort: true,
    completionTimeout: const Duration(seconds: 90),
  );
} on PrinterException catch (e) {
  switch (e.type) {
    case PrinterErrorType.notConnected:
    case PrinterErrorType.connectionLost:
    case PrinterErrorType.timeout:
    case PrinterErrorType.cancelled:
    case PrinterErrorType.unknown:
      // Show the error; the next print starts on a new connection.
  }
}
```

`cancel()` aborts the job in progress. `openPort(mac, resetPrinterAfterFailure: true)` resets the printer with `<ESC>!R` before connecting when the previous job failed; enable it only for printers that support that command.

### A printer left halfway through an image

If the connection is cut (or `cancel()` is called) while the plugin is still sending an image, some printers keep waiting for the bytes that never arrive. On a Ribetec this shows as `Downloading...` on its screen, and it stops accepting Bluetooth connections: every `openPort` fails with `notConnected` until the printer is turned off and on. Restarting the phone, turning its Bluetooth off and on, or closing the app does not help, and `resetPrinterAfterFailure` cannot help either, because it needs a connection to send `<ESC>!R`.

The plugin cannot prevent this, only report it. When `openPort` keeps failing after a cut or cancelled print, ask the user to turn the printer off and on. If `cancel()` arrives after the printer already received the whole image, the printer finishes the print and `openPort` works again at once.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
