## Context

The `printer_flutter` plugin is an open-source Flutter library providing Bluetooth thermal printer connectivity and TSPL PDF printing. Before releasing version `0.0.1` to pub.dev, all package health requirements enforced by `pana` (the pub.dev automated package analysis tool) must be met.

Running `flutter pub publish --dry-run` surfaced two main warnings (missing `repository` URL and plural `docs/` directory name) and revealed a 4 MB package size due to unignored manual PDF files and agent configuration folders.

## Goals / Non-Goals

**Goals:**
* Pass `flutter pub publish --dry-run` with 0 warnings.
* Minimize package tarball size (under 100 KB) by excluding non-runtime dev assets via `.pubignore`.
* Provide complete, professional documentation (`README.md`, `CHANGELOG.md`, `LICENSE`).
* Ensure zero linter issues (`flutter analyze`).

**Non-Goals:**
* Modifying public API signatures or changing core printing behavior.

## Decisions

### Decision 1: Metadata & Pubspec Enhancements
* **`repository`**: Set to `https://github.com/URBAN-CONNECTIONS/printer_flutter`.
* **`description`**: Update to `"Flutter plugin for Bluetooth thermal printer discovery, SPP connection, and PDF printing with TSPL/TSC command generation."` (132 chars, within 60–180 recommendation).
* **`issue_tracker`**: Set to `https://github.com/URBAN-CONNECTIONS/printer_flutter/issues`.
* **`topics`**: Include `bluetooth`, `printer`, `tspl`, `thermal-printer`, `pdf`.

### Decision 2: Package Archive Optimization (`.pubignore`)
Instead of renaming `docs/` to `doc/` (which would pack a 4.6 MB manual PDF into pub.dev downloads), create `.pubignore` to exclude:
- `docs/`
- `ticket ejemplo.pdf`
- `openspec/`
- `.agent/`, `.agents/`, `.dart_skills/`, `skills-lock.json`
- `.idea/`, `.vscode/`

This reduces the published package tarball from 4 MB to < 50 KB.

### Decision 3: Documentation Structure (`README.md`)
Structure the `README.md` to cover:
1. Overview & Capabilities (Bluetooth SPP, TSPL command generation, PDF rendering, whitespace trimming, page-by-page printing).
2. Android & iOS Setup & Required Permissions (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `FINE_LOCATION`).
3. Code Examples:
   - Requesting permissions & scanning devices.
   - Connecting to a printer MAC address.
   - Printing PDFs with custom TSPL settings (`LabelConfig`, `PdfPrintStrategy`, crop margins).
4. Platform Compatibility.

### Decision 4: Open Source License (`LICENSE`)
Use the MIT License with `URBAN CONNECTIONS` copyright.

### Decision 5: Linter Fixes
Mark `_strategy` field as `final` in `example/lib/main.dart` to satisfy `prefer_final_fields`.

## Risks / Trade-offs

* **[Risk] Excluding `docs/` from pub package**: Users viewing the package on pub.dev won't see the local PDF programming manual.
  * **Mitigation**: The README will contain full setup and API instructions, and the manual remains accessible in the GitHub repository.
