# PROGRESS: calorie-tracker overhaul

## Baseline (audited 2026-10-27)
- Builds: `./gradlew testDebugUnitTest assembleDebug` OK on JDK 17 / Android SDK (set `sdk.dir` in the untracked `local.properties`).
- 79 JVM unit tests pass. No UI tests, no coverage report, no static analysis (ktlint/detekt), 37 Android lint warnings (mostly outdated deps).
- 89 tracked files, ~6,000 lines of Kotlin. Stack: Kotlin 2.0, Compose (BOM 2024.06), Room 2.6.1 + KSP, WorkManager, OkHttp + Gson, four LLM adapters (Gemini, OpenAI, Claude, DeepSeek).

## Findings (what a Google/Amazon/Microsoft interviewer would notice)
1. **Architecture is advertised but not wired.** README claims Clean Architecture; `MainActivity.kt` (495 lines) builds repositories, holds profile state and does the logging flow inline. `HomeViewModel` + use cases exist but are unused; `IUserProfileRepository` has no production implementation. No DI.
2. **Secrets at rest.** AI provider API keys sit in plain `SharedPreferences` (`PreferencesRepository`). Should be encrypted with a Keystore-backed key.
3. **Release pipeline is unsafe.** CI publishes a GitHub release tagged `v1.0.0` on every push to main; uses system `gradle` instead of the wrapper; no lint/static analysis step; `isMinifyEnabled = false` and `proguard-rules.pro` is referenced but not in the repo; signing secrets only partially documented.
4. **Stale/incorrect docs.** `BUGS_AND_MISSING_FEATURES.md` references a local `d:\...` path and mixes fixed and outdated items; README says "Android SDK 35 target" but `targetSdk = 34`, mentions a LICENSE file that does not exist, and the CI release text names a person.
5. **Known functional gaps** (still to verify against current code): `weeklySummaries` memo keyed only on `allMeals.size`; hardcoded 2,300 kcal / 180 g strings in Analytics/History/Profile/Home; creatine toggle not persisted; no water tracking UI.
6. **Testing gaps:** no tests for ViewModels, repositories against Room (in-memory), or UI; network adapters tested but no contract tests with a fake server (MockWebServer).
7. **Android hygiene:** `Theme.Material.NoActionBar` (framework theme) rather than a Compose/Material3 theme in the manifest; missing `dataExtractionRules`; launcher icon lint issues; outdated dependency versions.

## Plan
- [x] A. Tooling: LICENSE, proguard rules, R8 minify on release, targetSdk 35, ktlint/spotless + detekt, Android lint baseline, Kover coverage, CI using the wrapper with lint/test/coverage, safe tag-based release workflow
- [ ] B. Architecture: Hilt (or manual DI container) + `HomeViewModel` bound to `HomeScreen`; `PreferencesRepository` implements `IUserProfileRepository`; shrink `MainActivity` to a thin host; navigation graph
- [ ] C. Security: Keystore-backed encrypted storage for API keys (with migration from existing plain prefs), network security config, redact keys in logs
- [ ] D. Functional gaps: fix cache invalidation, remove hardcoded targets, persist creatine, add water tracking
- [ ] E. Tests: ViewModel tests (turbine), Room in-memory tests, MockWebServer adapter contract tests, Compose UI tests for key screens; coverage gate
- [ ] F. Docs: README rewrite (screenshots, architecture diagram, setup, privacy), ARCHITECTURE.md, ADRs, SECURITY/PRIVACY, CHANGELOG, remove stale bug doc into issues/CHANGELOG
- [ ] G. Resume bullets into `../RESUME_BULLETS.md`

## Decisions log
- Branch `overhaul/v1`. Nothing pushed without approval.
- Keep user-visible behaviour; any behaviour change is logged here.

## Log
- A: Gradle 8.13, AGP 8.13, Kotlin 2.0.21, compile SDK 36 / target 35, AndroidX bumped to AGP-8.13-compatible versions (newest AndroidX needs AGP 9 + compileSdk 37, deliberately skipped). R8 minify + shrink resources on release (6.5 MB unsigned APK) with keep rules for Gson-reflected models. Spotless (ktfmt) applied repo-wide, detekt with a baseline of 158 existing findings to burn down in B/D, Kover (line coverage gate 25%, UI/generated excluded), lint is clean with documented ignores. CI uses the wrapper and runs format, detekt, lint, tests + coverage, builds; release workflow only runs on `v*` tags and signs only if secrets exist. Fixed real lint findings (obsolete SDK check, boxed float state, KTX prefs, missing data-extraction rules).
- Not verified on a device: the minified release APK has been built but not run. Do a smoke test before distributing it.
