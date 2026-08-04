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

  final TextEditingController _macController =
      TextEditingController(text: '00:19:0E:A3:03:E8');
  final List<String> _logs = [];
  bool _isLoading = false;
  final PdfPrintStrategy _strategy = PdfPrintStrategy.dramBatch;

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  void _addLog(String log) {
    setState(() {
      _logs.insert(
          0, '[${DateTime.now().toIso8601String().substring(11, 19)}] $log');
    });
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

  Future<void> _requestPermissions() async {
    setState(() => _isLoading = true);
    _addLog('Requesting permissions...');

    try {
      final res = await _printerFlutterPlugin.requestPermissions();
      _addLog('Permissions Granted: $res');
    } catch (e) {
      _addLog('Permissions Error: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _connect() async {
    final mac = _macController.text.trim();
    if (mac.isEmpty) {
      _addLog('Error: MAC address is empty');
      return;
    }

    setState(() => _isLoading = true);
    _addLog('Connecting to $mac...');

    try {
      final res = await _printerFlutterPlugin.openPort(mac);
      _addLog('Connect Result: $res');
    } catch (e) {
      _addLog('Connect Error: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _printTestLabel() async {
    setState(() => _isLoading = true);
    _addLog('Sending TSPL print command...');

    const String tsplCommand = '''
SIZE 40 mm, 30 mm
GAP 2 mm, 0 mm
CLS
TEXT 50,30,"3",0,1,1,"TSC FLUTTER TEST"
TEXT 50,70,"2",0,1,1,"MAC: DC:0D:30:FD:B5:B9"
BARCODE 50,110,"128",60,1,0,2,2,"123456789"
PRINT 1,1
''';

    try {
      final res = await _printerFlutterPlugin.sendTspl(tsplCommand);
      _addLog('Print Result: $res');
    } catch (e) {
      _addLog('Print Error: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _closePort() async {
    setState(() => _isLoading = true);
    _addLog('Closing port...');

    try {
      final res = await _printerFlutterPlugin.closePort();
      _addLog('Close Result: $res');
    } catch (e) {
      _addLog('Close Error: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _printPdfLabel() async {
    final mac = _macController.text.trim();
    if (mac.isEmpty) {
      _addLog('Error: MAC address is empty');
      return;
    }

    setState(() => _isLoading = true);
    _addLog('Preparing PDF from assets...');

    try {
      final bytes = await rootBundle.load('assets/sample.pdf');
      final dir = await getTemporaryDirectory();
      final file = File('${dir.path}/sample.pdf');
      await file.writeAsBytes(bytes.buffer.asUint8List(), flush: true);

      _addLog('Connecting to $mac...');
      final connectRes = await _printerFlutterPlugin.openPort(mac);
      _addLog('Connect Result: $connectRes');

      _addLog('Sending PDF Print Command (${_strategy.name})...');

      final res = await _printerFlutterPlugin.printPdf(
        filePath: file.path,
        options: PdfPrintOptions(
          paperWidthMm: 72.0,
          dpi: 203,
          strategy: _strategy,
          enableDithering: true,
          trimWhitespace: true,
        ),
        closePort: true,
      );
      _addLog('PDF Print Result: $res');

      _addLog('Closing port...');
      //final closeRes = await _printerFlutterPlugin.closePort();
      //_addLog('Close Result: $closeRes');
    } catch (e) {
      _addLog('PDF Print Error: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

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
        body: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
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
                  border: OutlineInputBorder(),
                  prefixIcon: Icon(Icons.bluetooth),
                ),
              ),
              const SizedBox(height: 16),
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
              OutlinedButton.icon(
                onPressed: _isLoading ? null : _closePort,
                icon: const Icon(Icons.power_settings_new),
                label: const Text('Disconnect Printer'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: Colors.red,
                  padding: const EdgeInsets.all(12),
                ),
              ),
              const SizedBox(height: 20),
              const Text(
                'Activity Logs:',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              Expanded(
                child: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: Colors.black87,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: ListView.builder(
                    itemCount: _logs.length,
                    itemBuilder: (context, index) {
                      return Text(
                        _logs[index],
                        style: const TextStyle(
                          color: Colors.greenAccent,
                          fontFamily: 'monospace',
                          fontSize: 12,
                        ),
                      );
                    },
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
