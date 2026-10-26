# Contributing

## Setup
JDK 17 and the Android SDK are required. Put the SDK path in `local.properties` (Android Studio does this automatically), then:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Before opening a pull request
Run what CI runs:

```bash
./gradlew spotlessApply          # format (ktfmt, Google style)
./gradlew spotlessCheck detekt lintDebug testDebugUnitTest koverVerify
```

## Conventions
- Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `build:`).
- Keep `domain/` free of Android and framework imports; talk to storage and the network through the interfaces there.
- New screens get a ViewModel that exposes one immutable UI state plus `UiEffect`s; no business logic in composables.
- Anything that decides what is saved or shown needs a test. Use the fakes in `src/test/.../support`, inject `Clock`, and never call `Date()` or `System.currentTimeMillis()` in code that decides a date.
- A new AI provider means a new `IAiProviderAdapter` plus an entry in `AdapterContractTest`.
- Database changes need a Room migration and a case in `RoomMigrationTest`; commit the exported schema in `app/schemas`.
- Never log or print API keys, and do not add dependencies that send data off the device.
