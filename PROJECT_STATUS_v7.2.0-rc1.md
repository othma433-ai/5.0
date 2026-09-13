# Project Status — v7.2.0-rc1 Runtime & Release Hardening RC

## Implemented and verified in source

- v7.1 runtime reliability behavior retained.
- Generation-tokened atomic `OperationLease` added at the Accessibility service boundary (ABA-safe).
- Sync, Extract, Retry Failed, and recovered extraction now reserve operation ownership before asynchronous startup work.
- Stale startup coroutines are rejected after Stop/restart and cannot clear a newer operation lease.
- Extraction startup persists its session before queue items so Stop races cannot leave an orphan queue.
- Busy-start attempts are rejected without overwriting the active operation.
- Operation ownership is released on operation clear/service teardown and aborted startup paths.
- Fixed 20/24-iteration 500 ms navigation polling loops removed.
- `NavigationProbePolicy` adds bounded FAST / BALANCED / SAFE fallback probe schedules below each mode's stage watchdog budget.
- 39 pure-Kotlin smoke checks pass.
- XML parse, feature preservation, accessibility manifest, gesture regression, v7.1 regression, v7.2 integration, and static safety audits pass.
- Version metadata upgraded to `versionCode 72`, `versionName 7.2.0-rc1`.
- GitHub main/PR build workflow and tag-release workflow prepared.
- Gradle 8.9 distribution checksum and wrapper JAR checksum are pinned.

## Environment limitation during this build pass

The current execution environment does not provide Android SDK 35 and cannot reach Gradle distribution servers from its shell network. Therefore Android Gradle unit tests/lint/APK compilation cannot be truthfully claimed as locally completed here.

GitHub Actions is configured to perform those gates automatically after the source is uploaded to GitHub.

## Production gate

The release remains RC until all of the following are green:

1. GitHub `Release audit`.
2. `:app:testDebugUnitTest`.
3. `:app:lintDebug`.
4. `:app:assembleDebug` and APK SHA-256 generation.
5. Physical-device acceptance in `DEVICE_TEST_v7.2.0-rc1.md`.
