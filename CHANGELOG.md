# Changelog

## 1.1.0

### Added
- Water tracking on the Today screen with +250 ml / +500 ml shortcuts, and a persistent creatine toggle. Both are stored per day and appear in analytics.
- Carb and fat targets derived from your calorie and protein targets.
- Hilt dependency injection, ViewModels for every screen, and a use case that owns the "create vs update" rule for AI-logged meals.
- API keys are encrypted at rest with an Android Keystore AES-GCM key; older plain-text profiles migrate automatically.
- 149 unit tests (was 79): ViewModels, Room DAOs and migrations, provider wire contracts, encryption, and a boot test of the real Hilt graph. Kover coverage gate.
- ktfmt, detekt, Android Lint and R8 minification in CI; tag-triggered release workflow; LICENSE, ARCHITECTURE, ADRs, SECURITY, PRIVACY.

### Changed
- Gradle 8.13, AGP 8.13, Kotlin 2.0.21, compile SDK 36, target SDK 35, AndroidX updated.
- `MainActivity` shrank from ~500 lines to a thin host; navigation and effect handling moved to `AppRoot`.
- The four provider adapters share their HTTP, error and schema code (891 to 741 lines including the new shared schema file; same behaviour, pinned by contract tests).
- One `Meal` model instead of three near-identical types.
- The profile is an observable `StateFlow`, so chat weight updates and profile edits reach every screen without a reload counter.
- The rule that logging the same food twice keeps both entries (only an explicit AI update replaces a meal) now lives in a use case with regression tests.
- Releases are no longer published on every push to `main`.

### Fixed
- Analytics and the history day dialog no longer report creatine as always taken; they read the stored value.
- CSV export can no longer inject spreadsheet formulas from a food name.
