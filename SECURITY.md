# Security policy

## Reporting a vulnerability
Please do not open a public issue. Use GitHub's private vulnerability reporting ("Security" tab, "Report a vulnerability") and include steps to reproduce. You can expect an acknowledgement within a few days.

## Supported versions
Only the latest release receives fixes.

## How secrets are handled
- AI provider API keys are encrypted at rest with AES-256-GCM. The key lives in the Android Keystore ([ADR 0003](docs/adr/0003-encrypt-api-keys-with-keystore.md)).
- Keys are sent only to the provider they belong to, in a request header (never in a URL), over HTTPS. Cleartext traffic is disabled.
- App data is excluded from cloud backup and device-to-device transfer.
- Keys are redacted from `UserProfile.toString()` so they cannot reach logs or crash reports through string interpolation.
- Exported CSV files neutralise cells that spreadsheets would execute as formulas.
- Release builds are minified and resource-shrunk (R8).

## Signing keys
Never commit a keystore or its passwords. `*.jks` and `*.keystore` are git-ignored; CI reads signing material from repository secrets only.

## Known limitations
- A rooted device or a debugger attached to a running app can still observe a key while it is in memory.
- Photos attached in chat and meal text are sent to the selected AI provider and are subject to that provider's retention policy.
