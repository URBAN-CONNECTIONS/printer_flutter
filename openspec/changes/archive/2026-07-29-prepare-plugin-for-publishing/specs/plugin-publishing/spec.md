## ADDED Requirements

### Requirement: Complete Pubspec Metadata
The package `pubspec.yaml` MUST include all required pub.dev metadata fields including a repository URL, descriptive summary (60–180 characters), issue tracker URL, and search topics.

#### Scenario: Valid pubspec metadata check
- **WHEN** `flutter pub publish --dry-run` is executed
- **THEN** no missing repository, description length, or metadata warnings are raised by the pub tool

### Requirement: Open Source License File
The root of the repository MUST include a valid `LICENSE` file containing open-source license text (MIT License).

#### Scenario: License detection
- **WHEN** pub analysis scans the package root
- **THEN** it identifies a valid MIT open-source license without missing license errors

### Requirement: Initial Release Changelog
The `CHANGELOG.md` MUST detail all key capabilities of the initial `0.0.1` release instead of template placeholders.

#### Scenario: Changelog review
- **WHEN** reading `CHANGELOG.md` for version 0.0.1
- **THEN** it explicitly lists Bluetooth connection, TSPL command generation, PDF rendering, whitespace trimming, and permission features

### Requirement: Comprehensive README Documentation
The `README.md` MUST describe package features, required Android and iOS Bluetooth permissions, setup steps, and complete code usage examples.

#### Scenario: Reading package documentation
- **WHEN** a developer views `README.md`
- **THEN** it provides setup instructions for Bluetooth permissions and code examples for device scanning, connecting, and printing PDFs with TSPL options

### Requirement: Ignored Non-Package Files
The package MUST provide a `.pubignore` file excluding non-runtime artifacts (such as internal PDF manuals in `docs/`, sample test PDFs, agent configuration folders, and `openspec/`) to minimize the published tarball size.

#### Scenario: Dry run package size verification
- **WHEN** `flutter pub publish --dry-run` builds the compressed package archive
- **THEN** the archive size is under 100 KB and contains no `docs/` manual PDFs or agent files

### Requirement: Clean Static Analysis
The codebase MUST pass static analysis without lint errors or info warnings.

#### Scenario: Running linter
- **WHEN** `flutter analyze` is executed
- **THEN** 0 issues are found across the package and example codebase
