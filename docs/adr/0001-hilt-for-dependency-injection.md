# ADR 0001: Hilt for dependency injection

- Status: accepted

## Context
`MainActivity` constructed repositories, DAOs and ViewModels by hand, `DietitianChatViewModel` built its own database access, and the WorkManager worker repeated the same wiring. Nothing could be tested without a real `Context`, and the same objects existed several times.

## Decision
Use Hilt. `@HiltAndroidApp` application, `@AndroidEntryPoint` activity, `@HiltViewModel` ViewModels and an `@HiltWorker` for the coach notifications. Modules provide the database, DAOs, HTTP client, provider adapters and an injectable `java.time.Clock`; a second module binds repository interfaces to implementations.

## Consequences
- ViewModels and use cases take interfaces, so tests use small fakes and a fixed clock.
- The graph is validated at compile time, and `AppSmokeTest` boots the real graph under Robolectric.
- WorkManager must be initialised by the app (`Configuration.Provider`) so the worker factory is Hilt-aware; the default initializer is removed in the manifest.
- Cost: kapt-free (KSP) but a longer build and some annotation boilerplate. Accepted for the testability gain.
