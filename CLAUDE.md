# printer_flutter: project rules

Flutter plugin for Bluetooth thermal printers: discovery, SPP connection, PDF printing and TSPL/TSC command generation. Android is the supported target (minSdk 21+, Android 12+ Bluetooth permissions); web has a stub. QA reads this file at every run (`qa.rules_from`).

## Layout

- `lib/`: public Dart API. `printer_flutter.dart` is the entry point, `printer_flutter_platform_interface.dart` the contract, `printer_flutter_method_channel.dart` the channel implementation, `printer_flutter_web.dart` the web stub.
- `android/src/main/kotlin/`: native Bluetooth SPP, PDF rendering and printing.
- `test/`: plugin tests. `example/`: demo app, with its own tests.
- `openspec/`: specs and changes. Behaviour changes go through an OpenSpec change; do not edit `openspec/` by hand outside that flow.

## Rules

- Every change to the Dart API goes through the platform interface and the method channel; keep the channel method names and argument maps in sync between Dart and Kotlin.
- Public API additions are documented with `///` comments and covered in `README.md` and `CHANGELOG.md`.
- Printing paths (TSPL builder, PDF to bitmap, dithering, 16-bit alignment) must keep working for existing label sizes (58mm, 80mm, custom `LabelConfig`). Do not change defaults silently.
- Bluetooth code must close sockets and streams on every path (success, error, disconnect) and never block the main thread.
- No secrets, keys or device MAC addresses in code, tests or examples.
- Conventional commits (`feat:`, `fix:`, `chore:`, `ci:`...). Version bumps only in release commits, with a changelog entry.

## Checks (CI runs the first two)

- `flutter analyze`: no errors or warnings.
- `flutter test`: all tests pass.
- `dart format .`: no formatting diff.
- Every behaviour change or bug fix adds or updates a test in `test/`; a Kotlin change that cannot be unit tested says how it was verified.
