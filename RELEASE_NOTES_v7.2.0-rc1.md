# WA Al-Othmany Link Bot v7.2.0-rc1

## Runtime reliability

- Added a generation-tokened atomic operation lease so Sync, Extract, Retry Failed, and recovered extraction startup cannot overlap or resume from a stale coroutine after Stop/restart.
- Added explicit busy-start, stale-start, recovery-failure, and foreground-service startup diagnostics.
- Reordered extraction startup persistence so the session exists before its queue, preventing orphan queue creation across a Stop race.
- Replaced fixed navigation polling loops with bounded performance-aware fallback probes.
- FAST gets lower-latency probes, BALANCED remains the default, and SAFE permits slower UI settling while staying below its stage watchdog.
- Existing event-driven Accessibility handling and screen/state verification remain authoritative.

## Release engineering

- Raised app version to `72 / 7.2.0-rc1`.
- Added Gradle 8.9 wrapper metadata with pinned distribution SHA-256.
- Added verified wrapper-JAR bootstrap for environments where the binary wrapper JAR is not already present.
- Reworked GitHub Actions to run release audit, Android unit tests, lint, and APK build before artifact publication.
- Added tag-triggered GitHub Release workflow with APK + SHA-256 attachment and strict tag/version matching.
- Added a one-command `tools/build-release-candidate.sh` build path.

## Verification status

- 39 pure-Kotlin smoke checks: PASS.
- Source/XML/privacy/feature/runtime regression audits: PASS.
- Android SDK build/lint/unit tests: delegated to GitHub Actions in this environment.
- Physical-device certification: required before production declaration.
