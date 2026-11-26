import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// The completion confirmation means the printer processed everything it was
/// sent, never that it finished printing. These tests keep the public
/// documentation from saying otherwise.
void main() {
  /// The text of [path] with comment markers and line breaks collapsed, so a
  /// phrase wrapped over two lines is still found.
  String readNormalized(String path) {
    return File(path)
        .readAsStringSync()
        .replaceAll(RegExp(r'^\s*///?', multiLine: true), ' ')
        .replaceAll(RegExp(r'\s+'), ' ')
        .toLowerCase();
  }

  const forbiddenPhrases = [
    'printed everything',
    'includes printing every copy',
    'confirm it printed',
    'confirms it printed',
    'confirmation arrives after printing',
    'after printing finished',
  ];

  for (final path in ['lib/printer_flutter.dart', 'README.md']) {
    test('$path does not say the confirmation arrives after printing', () {
      final text = readNormalized(path);

      for (final phrase in forbiddenPhrases) {
        expect(
          text.contains(phrase),
          isFalse,
          reason: '$path says "$phrase", but the confirmation only means the '
              'printer processed everything it was sent',
        );
      }
    });
  }

  test('lib/printer_flutter.dart says the confirmation is of processing', () {
    final text = readNormalized('lib/printer_flutter.dart');

    expect(text, contains('confirm it processed everything it was sent'));
  });

  test('README.md says the confirmation is of processing', () {
    final text = readNormalized('README.md');

    expect(text, contains('processed everything it was sent'));
  });
}
