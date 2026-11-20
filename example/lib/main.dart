import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:printer_flutter/printer_flutter.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      theme: ThemeData(useMaterial3: true, colorSchemeSeed: Colors.blue),
      home: const TestPage(),
    );
  }
}

enum _Status { pending, running, passed, failed }

class _TestResult {
  _Status status = _Status.pending;
  String detail = '';
}

/// What one printer call did: whether it worked, how long it took and the error type.
class _Outcome {
  _Outcome(this.ok, this.text, [this.type]);

  final bool ok;
  final String text;
  final PrinterErrorType? type;
}

/// One button per test. Each test connects and prints by itself, tells the person
/// when to do something with the printer, and writes its result.
class TestPage extends StatefulWidget {
  const TestPage({super.key});

  @override
  State<TestPage> createState() => _TestPageState();
}

class _TestPageState extends State<TestPage> {
  final _plugin = PrinterFlutter();

  final _macController = TextEditingController();
  final _modelController = TextEditingController();
  final _pinController = TextEditingController(text: '0000');
  final _fallbackPinController = TextEditingController(text: '1234');

  String _platformVersion = 'Unknown';
  final List<String> _logs = [];
  bool _busy = false;

  static const _tests = [
    '1. Imprimir normal (2 copias)',
    '2. Cancelar después de imprimir',
    '3. Reiniciar la impresora (<ESC>!R)',
    '4. Quitar el papel a mitad de impresión',
    '5. Cancelar con reinicio antes de cerrar',
    '6. Cancelar a mitad y reconectar con reinicio',
    '7. Cancelar a mitad de impresión',
    '8. Apagar la impresora a mitad de impresión',
  ];
  final List<_TestResult> _results =
      List.generate(_tests.length, (_) => _TestResult());

  @override
  void initState() {
    super.initState();
    _loadPlatformVersion();
  }

  @override
  void dispose() {
    _macController.dispose();
    _modelController.dispose();
    _pinController.dispose();
    _fallbackPinController.dispose();
    super.dispose();
  }

  Future<void> _loadPlatformVersion() async {
    String version;
    try {
      version =
          await _plugin.getPlatformVersion() ?? 'Unknown platform version';
    } on PlatformException {
      version = 'Failed to get platform version.';
    }
    if (!mounted) return;
    setState(() => _platformVersion = version);
  }

  void _log(String line) {
    setState(() {
      _logs
          .add('[${DateTime.now().toIso8601String().substring(11, 19)}] $line');
    });
  }

  String _secs(Stopwatch stopwatch) =>
      '${(stopwatch.elapsedMilliseconds / 1000).toStringAsFixed(1)} s';

  /// Runs one printer call and reports how it ended, never throwing.
  Future<_Outcome> _try(String name, Future<Object?> Function() action) async {
    final stopwatch = Stopwatch()..start();
    _log('$name...');
    try {
      await action();
      final text = 'OK en ${_secs(stopwatch)}';
      _log('$name -> $text');
      return _Outcome(true, text);
    } on PrinterException catch (e) {
      final text =
          'error ${e.type.name} a los ${_secs(stopwatch)}: ${e.message}';
      _log('$name -> $text');
      return _Outcome(false, text, e.type);
    } catch (e) {
      final text = 'error a los ${_secs(stopwatch)}: $e';
      _log('$name -> $text');
      return _Outcome(false, text);
    }
  }

  Future<_Outcome> _connect({bool reset = false}) => _try(
        reset ? 'Conectar con reinicio' : 'Conectar',
        () => _plugin.openPort(
          _macController.text.trim(),
          pin: _pinController.text.trim(),
          fallbackPin: _fallbackPinController.text.trim().isEmpty
              ? null
              : _fallbackPinController.text.trim(),
          resetPrinterAfterFailure: reset,
        ),
      );

  /// Prints the ticket with the strategy of the app, and waits for the printer to confirm.
  Future<_Outcome> _print(int copies) => _try('Imprimir x$copies', () async {
        final bytes = await rootBundle.load('assets/sample.pdf');
        final dir = await getTemporaryDirectory();
        final file = File('${dir.path}/sample.pdf');
        await file.writeAsBytes(bytes.buffer.asUint8List(), flush: true);
        return _plugin.printPdf(
          filePath: file.path,
          options: const PdfPrintOptions(
            paperWidthMm: 72.0,
            dpi: 203,
            strategy: PdfPrintStrategy.dramBatch,
            enableDithering: true,
            trimWhitespace: true,
          ),
          copies: copies,
          closePort: true,
        );
      });

  /// Connects and prints once: the check that the printer works again.
  Future<String> _recover({bool reset = false}) async {
    final connect = await _connect(reset: reset);
    if (!connect.ok) return 'no conectó (${connect.text})';
    final print = await _print(1);
    if (!print.ok) return 'conectó pero no imprimió (${print.text})';
    return 'conectó e imprimió';
  }

  Future<bool> _ask(String message, {String yes = 'Listo', String? no}) async {
    final answer = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        content: Text(message),
        actions: [
          if (no != null)
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: Text(no),
            ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(yes),
          ),
        ],
      ),
    );
    return answer ?? false;
  }

  /// Runs one test: [body] returns (passed, detail).
  Future<void> _runTest(
    int index,
    Future<(bool, String)> Function() body,
  ) async {
    if (_macController.text.trim().isEmpty) {
      _log('Escribe primero la MAC de la impresora');
      return;
    }
    setState(() {
      _busy = true;
      _results[index]
        ..status = _Status.running
        ..detail = '';
    });
    _log('=== ${_tests[index]} ===');
    try {
      final (passed, detail) = await body();
      if (!mounted) return;
      setState(() {
        _results[index]
          ..status = passed ? _Status.passed : _Status.failed
          ..detail = detail;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _results[index]
          ..status = _Status.failed
          ..detail = 'error inesperado: $e';
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _requestPermissions() async {
    setState(() => _busy = true);
    final outcome = await _try('Permisos', () => _plugin.requestPermissions());
    _log('Permisos: ${outcome.text}');
    if (mounted) setState(() => _busy = false);
  }

  // 1. A normal print.
  Future<void> _test1() => _runTest(0, () async {
        final connect = await _connect();
        if (!connect.ok) return (false, 'no conectó: ${connect.text}');
        final print = await _print(2);
        return (print.ok, 'conectó; imprimir x2: ${print.text}');
      });

  // 2. Cancel in the middle of a print, then see if the printer works again.
  Future<void> _test2() => _runTest(6, () async {
        final connect = await _connect();
        if (!connect.ok) return (false, 'no conectó: ${connect.text}');
        await _ask(
          'Se va a imprimir y a los 4 segundos se cancelará solo. '
          'Mira la pantalla de la impresora.',
          yes: 'Empezar',
        );
        final printing = _print(2);
        await Future<void>.delayed(const Duration(seconds: 4));
        _log('cancel()');
        await _plugin.cancel();
        final outcome = await printing;
        final cancelled = outcome.type == PrinterErrorType.cancelled;
        final ready = await _ask(
          '¿Qué dice la pantalla de la impresora?',
          yes: 'Ready (normal)',
          no: 'Downloading... (trabada)',
        );
        _log('Pantalla de la impresora: ${ready ? 'Ready' : 'Downloading'}');
        final recovery = await _recover();
        return (
          cancelled && recovery == 'conectó e imprimió',
          'cancelar: ${outcome.text}; pantalla: '
              '${ready ? 'Ready' : 'Downloading...'}; después: $recovery'
        );
      });

  // 3 and 4. Something goes wrong with the printer during a print.
  Future<(bool, String)> _printerProblem(String problem, String undo) async {
    final connect = await _connect();
    if (!connect.ok) return (false, 'no conectó: ${connect.text}');
    await _ask(
      'Se va a imprimir. Cuando empiece, $problem. Luego toca "Listo".',
      yes: 'Empezar',
    );
    final printing = _print(2);
    await _ask('Ahora: $problem. Cuando lo hayas hecho, toca "Listo".');
    final outcome = await printing;
    await _ask('Ahora: $undo. Espera a que diga Ready y toca "Listo".');
    final recovery = await _recover();
    return (
      recovery == 'conectó e imprimió',
      'durante la impresión: ${outcome.text}; después: $recovery'
    );
  }

  Future<void> _test3() => _runTest(
      7, () => _printerProblem('apaga la impresora', 'prende la impresora'));

  Future<void> _test4() => _runTest(
      3, () => _printerProblem('quita el papel', 'pon el papel otra vez'));

  // 5. Reset a healthy printer with <ESC>!R.
  Future<void> _test5() => _runTest(2, () async {
        final connect = await _connect();
        if (!connect.ok) return (false, 'no conectó: ${connect.text}');
        final sent = await _try(
          'Enviar <ESC>!R',
          () => _plugin.sendRawBytes(Uint8List.fromList([0x1B, 0x21, 0x52])),
        );
        final reacted = await _ask(
          'Se mandó el reinicio. ¿La impresora reaccionó (pitido, luces o '
          'la pantalla se reinició)?',
          yes: 'Sí reaccionó',
          no: 'No pasó nada',
        );
        await _ask('Espera a que la impresora diga Ready y toca "Listo".');
        final recovery = await _recover();
        return (
          sent.ok && recovery == 'conectó e imprimió',
          'envío: ${sent.text}; reaccionó: ${reacted ? 'sí' : 'no'}; '
              'después: $recovery'
        );
      });

  // 6. Cancel when nothing is running.
  Future<void> _test6() => _runTest(1, () async {
        final connect = await _connect();
        if (!connect.ok) return (false, 'no conectó: ${connect.text}');
        final print = await _print(1);
        if (!print.ok) return (false, 'imprimir x1: ${print.text}');
        _log('cancel() sin nada en curso');
        await _plugin.cancel();
        final recovery = await _recover();
        return (
          recovery == 'conectó e imprimió',
          'imprimió; cancelar sin nada en curso; después: $recovery'
        );
      });

  // 7. Like 2, but the reconnect asks the plugin to reset the printer first.
  Future<void> _test7() => _runTest(5, () async {
        final connect = await _connect();
        if (!connect.ok) return (false, 'no conectó: ${connect.text}');
        await _ask(
          'Se va a imprimir y a los 4 segundos se cancelará solo. '
          'Luego se reconectará pidiendo el reinicio de la impresora.',
          yes: 'Empezar',
        );
        final printing = _print(2);
        await Future<void>.delayed(const Duration(seconds: 4));
        _log('cancel()');
        await _plugin.cancel();
        final outcome = await printing;
        final cancelled = outcome.type == PrinterErrorType.cancelled;
        final recovery = await _recover(reset: true);
        final ready = await _ask(
          '¿Qué dice ahora la pantalla de la impresora?',
          yes: 'Ready (normal)',
          no: 'Downloading... (trabada)',
        );
        return (
          cancelled && recovery == 'conectó e imprimió',
          'cancelar: ${outcome.text}; reconectar con reinicio: $recovery; '
              'pantalla: ${ready ? 'Ready' : 'Downloading...'}'
        );
      });

  // 8. Cancel asking the plugin to reset the printer before closing the connection.
  Future<void> _test8() => _runTest(4, () async {
        final connect = await _connect();
        if (!connect.ok) return (false, 'no conectó: ${connect.text}');
        await _ask(
          'Se va a imprimir y a los 4 segundos se cancelará pidiendo el '
          'reinicio de la impresora antes de cerrar. Mira su pantalla.',
          yes: 'Empezar',
        );
        final printing = _print(2);
        await Future<void>.delayed(const Duration(seconds: 4));
        _log('cancel(resetPrinter: true)');
        await _plugin.cancel(resetPrinter: true);
        final outcome = await printing;
        final cancelled = outcome.type == PrinterErrorType.cancelled;
        await _ask(
          'Espera unos segundos a que la impresora termine de reiniciarse y '
          'toca "Listo".',
        );
        final recovery = await _recover();
        final ready = await _ask(
          '¿Qué dice ahora la pantalla de la impresora?',
          yes: 'Ready (normal)',
          no: 'Downloading... (trabada)',
        );
        return (
          cancelled && recovery == 'conectó e imprimió',
          'cancelar con reinicio: ${outcome.text}; después: $recovery; '
              'pantalla: ${ready ? 'Ready' : 'Downloading...'}'
        );
      });

  String _summary() {
    final buffer = StringBuffer()
      ..writeln('Impresora: ${_modelController.text.trim()}')
      ..writeln('Android: $_platformVersion')
      ..writeln();
    for (var i = 0; i < _tests.length; i++) {
      final result = _results[i];
      final label = switch (result.status) {
        _Status.pending => 'SIN HACER',
        _Status.running => 'EN CURSO',
        _Status.passed => 'BIEN',
        _Status.failed => 'FALLÓ',
      };
      buffer.writeln('${_tests[i]}: $label ${result.detail}');
    }
    buffer
      ..writeln()
      ..writeln('--- registro ---')
      ..writeAll(_logs, '\n');
    return buffer.toString();
  }

  Future<void> _copy() async {
    await Clipboard.setData(ClipboardData(text: _summary()));
    _log('Resultados copiados');
  }

  Widget _testCard(int index, Future<void> Function() onRun, String hint) {
    final result = _results[index];
    final (label, color) = switch (result.status) {
      _Status.pending => ('', Colors.grey),
      _Status.running => ('EN CURSO', Colors.orange),
      _Status.passed => ('BIEN', Colors.green),
      _Status.failed => ('FALLÓ', Colors.red),
    };
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              _tests[index],
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 4),
            Text(hint, style: Theme.of(context).textTheme.bodySmall),
            if (label.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text.rich(TextSpan(children: [
                TextSpan(
                  text: label,
                  style: TextStyle(color: color, fontWeight: FontWeight.bold),
                ),
                if (result.detail.isNotEmpty)
                  TextSpan(text: ' ${result.detail}'),
              ])),
            ],
            const SizedBox(height: 8),
            FilledButton(
              onPressed: _busy ? null : onRun,
              child: const Text('Hacer esta prueba'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar:
          AppBar(title: const Text('Pruebas de impresión'), centerTitle: true),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            'Running on: $_platformVersion',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _macController,
            decoration: const InputDecoration(
              labelText: 'MAC de la impresora',
              hintText: 'AA:BB:CC:DD:EE:FF',
              border: OutlineInputBorder(),
              prefixIcon: Icon(Icons.bluetooth),
            ),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _modelController,
            decoration: const InputDecoration(
              labelText: 'Marca y modelo de la impresora',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _pinController,
                  decoration: const InputDecoration(
                    labelText: 'PIN',
                    border: OutlineInputBorder(),
                    isDense: true,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: TextField(
                  controller: _fallbackPinController,
                  decoration: const InputDecoration(
                    labelText: 'PIN alterno',
                    border: OutlineInputBorder(),
                    isDense: true,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: _busy ? null : _requestPermissions,
            icon: const Icon(Icons.security),
            label: const Text('Dar permisos de Bluetooth (primero)'),
          ),
          if (_busy) ...[
            const SizedBox(height: 8),
            const LinearProgressIndicator(),
          ],
          const SizedBox(height: 8),
          Text(
            'Haz las pruebas en este orden. Si la impresora queda en '
            '"Downloading...", apágala y préndela antes de seguir.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 8),
          _testCard(0, _test1, 'Conecta e imprime el ticket con 2 copias.'),
          _testCard(1, _test6,
              'Imprime, cancela sin nada en curso y vuelve a imprimir.'),
          _testCard(2, _test5,
              'Con la impresora sana, le manda el reinicio y ve si reacciona.'),
          _testCard(3, _test4,
              'Imprime y te pide quitar el papel. Luego lo pones y vuelve a imprimir.'),
          _testCard(4, _test8,
              'Imprime y cancela a los 4 s mandando el reinicio ANTES de cerrar la conexión. Es el que debería evitar el Downloading.'),
          _testCard(5, _test7,
              'Imprime, cancela a los 4 s y reconecta pidiendo el reinicio. Si la impresora queda trabada, no lo logra.'),
          _testCard(6, _test2,
              'Imprime y cancela a los 4 s sin reinicio. Suele dejar la impresora en Downloading...: apágala y préndela después.'),
          _testCard(7, _test3,
              'Imprime y te pide apagar la impresora. Luego la prendes y vuelve a imprimir.'),
          const SizedBox(height: 8),
          FilledButton.icon(
            onPressed: _copy,
            icon: const Icon(Icons.copy),
            label: const Text('Copiar resultados'),
          ),
          const SizedBox(height: 8),
          ExpansionTile(
            tilePadding: EdgeInsets.zero,
            title: const Text('Registro',
                style: TextStyle(fontWeight: FontWeight.bold)),
            children: [
              Container(
                height: 260,
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: Colors.black87,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: ListView.builder(
                  reverse: true,
                  itemCount: _logs.length,
                  itemBuilder: (context, index) => Text(
                    _logs[_logs.length - 1 - index],
                    style: const TextStyle(
                      color: Colors.greenAccent,
                      fontFamily: 'monospace',
                      fontSize: 12,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
