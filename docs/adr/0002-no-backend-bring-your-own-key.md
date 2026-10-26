# ADR 0002: No backend; the user brings their own API key

- Status: accepted

## Context
A nutrition log is personal data. Running a server would mean accounts, storage, a privacy policy, ongoing cost, and a place where everyone's API spend and meals are concentrated.

## Decision
The app talks directly from the phone to the AI provider the user chooses, with the user's own key. There is no server, no account and no analytics. All persistence is local.

## Consequences
- Strong privacy story and zero operating cost.
- The key must be protected on the device ([ADR 0003](0003-encrypt-api-keys-with-keystore.md)), and users carry their own provider costs and limits.
- Four providers need four request/response shapes, so provider differences are isolated behind `IAiProviderAdapter` and pinned by contract tests.
- No cross-device sync. Exporting the log (JSON or CSV) is the escape hatch.
