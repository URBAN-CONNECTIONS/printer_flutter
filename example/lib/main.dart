import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
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
  String _platformVersion = 'Unknown';
  final _printerFlutterPlugin = PrinterFlutter();

  final TextEditingController _macController = TextEditingController();
  final TextEditingController _pinController =
      TextEditingController(text: '0000');
  final TextEditingController _fallbackPinController =
      TextEditingController(text: '1234');
  final TextEditingController _connectSecondsController =
      TextEditingController(text: '15');
  final TextEditingController _writeSecondsController =
      TextEditingController(text: '20');
  final TextEditingController _completionSecondsController =
      TextEditingController(text: '60');
  final TextEditingController _copiesController =
      TextEditingController(text: '2');
  final List<String> _logs = [];
  bool _isLoading = false;
  bool _resetAfterFailure = false;
  final PdfPrintStrategy _strategy = PdfPrintStrategy.dramBatch;

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  @override
  void dispose() {
    _macController.dispose();
    _pinController.dispose();
    _fallbackPinController.dispose();
    _connectSecondsController.dispose();
    _writeSecondsController.dispose();
    _completionSecondsController.dispose();
    _copiesController.dispose();
    super.dispose();
  }

  void _addLog(String log) {
    setState(() {
      _logs.add('[${DateTime.now().toIso8601String().substring(11, 19)}] $log');
    });
  }

  Duration _seconds(TextEditingController controller, int fallback) {
    final value = int.tryParse(controller.text.trim());
    return Duration(seconds: (value == null || value <= 0) ? fallback : value);
  }

  String _elapsed(Stopwatch stopwatch) =>
      '${(stopwatch.elapsedMilliseconds / 1000).toStringAsFixed(1)} s';

  /// Runs one printer call, and logs its result, its error type and how long it took.
  Future<void> _run(String name, Future<Object?> Function() action) async {
    setState(() => _isLoading = true);
    _addLog('$name...');
    final stopwatch = Stopwatch()..start();
    try {
      final result = await action();
      _addLog('$name -> OK (${_elapsed(stopwatch)}) $result');
    } on PrinterException catch (e) {
      _addLog(
          '$name -> ERROR ${e.type.name} (${_elapsed(stopwatch)}): ${e.message}');
    } catch (e) {
      _addLog('$name -> ERROR (${_elapsed(stopwatch)}): $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> initPlatformState() async {
    String platformVersion;
    try {
      platformVersion = await _printerFlutterPlugin.getPlatformVersion() ??
          'Unknown platform version';
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  Future<void> _requestPermissions() =>
      _run('Permissions', _printerFlutterPlugin.requestPermissions);

  Future<void> _connect() async {
    final mac = _macController.text.trim();
    if (mac.isEmpty) {
      _addLog('Error: MAC address is empty');
      return;
    }
    await _run(
      'Connect',
      () => _printerFlutterPlugin.openPort(
        mac,
        pin: _pinController.text.trim(),
        fallbackPin: _fallbackPinController.text.trim().isEmpty
            ? null
            : _fallbackPinController.text.trim(),
        connectTimeout: _seconds(_connectSecondsController, 15),
        resetPrinterAfterFailure: _resetAfterFailure,
      ),
    );
  }

  Future<void> _printTestLabel() {
    const String tsplCommand = '''
SIZE 40 mm, 30 mm
GAP 2 mm, 0 mm
CLS
TEXT 50,30,"3",0,1,1,"TSC FLUTTER TEST"
BARCODE 50,110,"128",60,1,0,2,2,"123456789"
PRINT 1,1
''';
    return _run(
      'Print TSPL label',
      () => _printerFlutterPlugin.sendTspl(
        tsplCommand,
        writeTimeout: _seconds(_writeSecondsController, 20),
      ),
    );
  }

  Future<void> _closePort() =>
      _run('Disconnect', _printerFlutterPlugin.closePort);

  /// Sends `<ESC>!R` (printer reset) on the open connection, to see if the printer reacts.
  Future<void> _sendReset() => _run(
        'Send <ESC>!R',
        () => _printerFlutterPlugin.sendRawBytes(
          Uint8List.fromList([0x1B, 0x21, 0x52]),
          writeTimeout: _seconds(_writeSecondsController, 20),
        ),
      );

  /// Prints the sample PDF on the open connection and waits for the printer's confirmation.
  Future<void> _printPdfLabel() async {
    final copies = int.tryParse(_copiesController.text.trim()) ?? 1;
    await _run('Print PDF x$copies', () async {
      final bytes = await rootBundle.load('assets/sample.pdf');
      final dir = await getTemporaryDirectory();
      final file = File('${dir.path}/sample.pdf');
      await file.writeAsBytes(bytes.buffer.asUint8List(), flush: true);
      return _printerFlutterPlugin.printPdf(
        filePath: file.path,
        options: PdfPrintOptions(
          paperWidthMm: 72.0,
          dpi: 203,
          strategy: _strategy,
          enableDithering: true,
          trimWhitespace: true,
        ),
        copies: copies < 1 ? 1 : copies,
        closePort: true,
        writeTimeout: _seconds(_writeSecondsController, 20),
        completionTimeout: _seconds(_completionSecondsController, 60),
      );
    });
  }

  /// Not routed through [_run]: it must work while a print is in progress.
  Future<void> _cancel() async {
    _addLog('cancel() sent');
    try {
      await _printerFlutterPlugin.cancel();
    } catch (e) {
      _addLog('cancel() -> ERROR $e');
    }
  }

  Future<void> _copyLogs() async {
    await Clipboard.setData(ClipboardData(
      text: 'Android: $_platformVersion\n${_logs.join('\n')}',
    ));
    _addLog('Log copied to the clipboard');
  }

  Widget _secondsField(String label, TextEditingController controller) {
    return Expanded(
      child: TextField(
        controller: controller,
        keyboardType: TextInputType.number,
        decoration: InputDecoration(
          labelText: label,
          border: const OutlineInputBorder(),
          isDense: true,
        ),
      ),
    );
  }

  static const String _guide = '''
Before: tap "Request Bluetooth Permissions", type the printer MAC and tap "Connect to Printer".

A. Normal print. Copies = 2. Tap "Print Sample PDF Ticket". Note the time of "OK (x s)". It must be well under the completion limit (60 s).

B. Printer off. Tap print and turn the printer off while it prints. Expected: ERROR connectionLost or timeout within the limit. Turn it on, tap "Connect to Printer" and print again: it must print without restarting the phone or the app.

C. No paper. Tap print and take the paper out while it prints. Expected: ERROR timeout. Put the paper back, connect and print again.

D. Cancel. Tap print and then "Cancel" while it prints. Expected: ERROR cancelled. Connect and print again: it must print.

E. Reset. Turn on "Reset the printer after a failure", cause a failure (B, C or D) and tap "Connect to Printer". Expected: the printer restarts (beeps or lights) and the connection succeeds. Also try "Send <ESC>!R" with a connection open and say whether the printer reacts.

When done, tap "Copy log" and send it with the printer brand and model.''';

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      theme: ThemeData(
        useMaterial3: true,
        colorSchemeSeed: Colors.blue,
      ),
      home: Scaffold(
        appBar: AppBar(
          title: const Text('TSC Printer Test'),
          centerTitle: true,
        ),
        body: ListView(
          padding: const EdgeInsets.all(16.0),
          children: [
            Text(
              'Running on: $_platformVersion',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _macController,
              decoration: const InputDecoration(
                labelText: 'Printer MAC Address',
                hintText: 'AA:BB:CC:DD:EE:FF',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.bluetooth),
              ),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _pinController,
                    decoration: const InputDecoration(
                      labelText: 'PIN (Default)',
                      border: OutlineInputBorder(),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: TextField(
                    controller: _fallbackPinController,
                    decoration: const InputDecoration(
                      labelText: 'Fallback PIN',
                      border: OutlineInputBorder(),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text('Limits (seconds)',
                style: Theme.of(context).textTheme.labelLarge),
            const SizedBox(height: 4),
            Row(
              children: [
                _secondsField('Connect', _connectSecondsController),
                const SizedBox(width: 8),
                _secondsField('Write', _writeSecondsController),
                const SizedBox(width: 8),
                _secondsField('Completion', _completionSecondsController),
                const SizedBox(width: 8),
                _secondsField('Copies', _copiesController),
              ],
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Reset the printer after a failure'),
              subtitle: const Text('Sends <ESC>!R on the next connect'),
              value: _resetAfterFailure,
              onChanged: (value) => setState(() => _resetAfterFailure = value),
            ),
            if (_isLoading)
              const LinearProgressIndicator()
            else
              const SizedBox(height: 4),
            const SizedBox(height: 12),
            ElevatedButton.icon(
              onPressed: _isLoading ? null : _requestPermissions,
              icon: const Icon(Icons.security),
              label: const Text('Request Bluetooth Permissions'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.all(12),
                backgroundColor: Colors.orange.shade100,
              ),
            ),
            const SizedBox(height: 8),
            ElevatedButton.icon(
              onPressed: _isLoading ? null : _connect,
              icon: const Icon(Icons.bluetooth_connected),
              label: const Text('Connect to Printer'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.all(12),
              ),
            ),
            const SizedBox(height: 8),
            ElevatedButton.icon(
              onPressed: _isLoading ? null : _printTestLabel,
              icon: const Icon(Icons.print),
              label: const Text('Print Test Label (TSPL)'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.green.shade100,
                padding: const EdgeInsets.all(12),
              ),
            ),
            const SizedBox(height: 8),
            ElevatedButton.icon(
              onPressed: _isLoading ? null : _printPdfLabel,
              icon: const Icon(Icons.picture_as_pdf),
              label: const Text('Print Sample PDF Ticket'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.purple.shade100,
                padding: const EdgeInsets.all(12),
              ),
            ),
            const SizedBox(height: 8),
            ElevatedButton.icon(
              onPressed: _cancel,
              icon: const Icon(Icons.cancel),
              label: const Text('Cancel'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.red.shade100,
                padding: const EdgeInsets.all(12),
              ),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: _isLoading ? null : _sendReset,
              icon: const Icon(Icons.restart_alt),
              label: const Text('Send <ESC>!R (printer reset)'),
              style:
                  OutlinedButton.styleFrom(padding: const EdgeInsets.all(12)),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: _isLoading ? null : _closePort,
              icon: const Icon(Icons.power_settings_new),
              label: const Text('Disconnect Printer'),
              style: OutlinedButton.styleFrom(
                foregroundColor: Colors.red,
                padding: const EdgeInsets.all(12),
              ),
            ),
            const SizedBox(height: 12),
            const ExpansionTile(
              tilePadding: EdgeInsets.zero,
              title: Text('Test guide',
                  style: TextStyle(fontWeight: FontWeight.bold)),
              children: [SelectableText(_guide)],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                const Expanded(
                  child: Text(
                    'Activity Logs:',
                    style: TextStyle(fontWeight: FontWeight.bold),
                  ),
                ),
                TextButton.icon(
                  onPressed: _logs.isEmpty ? null : _copyLogs,
                  icon: const Icon(Icons.copy),
                  label: const Text('Copy log'),
                ),
                TextButton.icon(
                  onPressed: _logs.isEmpty ? null : () => setState(_logs.clear),
                  icon: const Icon(Icons.delete_outline),
                  label: const Text('Clear'),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Container(
              height: 280,
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: Colors.black87,
                borderRadius: BorderRadius.circular(8),
              ),
              child: ListView.builder(
                reverse: true,
                itemCount: _logs.length,
                itemBuilder: (context, index) {
                  return Text(
                    _logs[_logs.length - 1 - index],
                    style: const TextStyle(
                      color: Colors.greenAccent,
                      fontFamily: 'monospace',
                      fontSize: 12,
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
