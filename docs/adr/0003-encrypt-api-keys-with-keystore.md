# ADR 0003: Encrypt API keys with an Android Keystore AES-GCM key

- Status: accepted

## Context
API keys were stored inside the profile JSON in plain `SharedPreferences`. On a rooted device, from a backup, or through an accidental log line, a key would be readable and would spend the user's money.

## Decision
- Keys are removed from the profile JSON and stored one by one as `base64(iv || ciphertext+tag)` produced by AES-256-GCM with a random 96-bit IV.
- The AES key is generated inside the Android Keystore and never leaves it.
- A profile saved by an older version is migrated the first time it is read.
- `UserProfile.toString()` prints how many keys are set, never their values.
- Cloud backup and device transfer exclude all app data; cleartext traffic is disabled.
- The deprecated `EncryptedSharedPreferences` is avoided on purpose: the cipher is a small class that can be tested on the JVM with an in-memory key.

## Consequences
- A tampered or foreign token fails authentication and reads as "no key"; the user re-enters it.
- If the Keystore is unavailable, saving a key fails with a clear message instead of storing it unprotected.
- The Keystore-backed provider itself is only verifiable on a device; the cipher logic is covered by unit tests.
