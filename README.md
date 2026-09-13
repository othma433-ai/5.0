# WA Al-Othmany Link Bot v7.2.0-rc1

Standalone Android application for WhatsApp group synchronization and durable link extraction using evidence-driven Accessibility automation.

## What v7.2 hardens

- Generation-tokened atomic operation admission prevents Sync / Extract / Retry Failed / recovery from overlapping or reviving stale startup work after Stop/restart.
- Navigation fallback probes are performance-aware and bounded for FAST / BALANCED / SAFE instead of using long fixed polling loops.
- Existing screen verification, circuit breakers, durable viewport persistence, queue recovery, duplicate-title protection, adaptive timing, and sanitized diagnostics remain active.
- GitHub CI verifies the source, runs Android unit tests and lint, builds an installable APK, and publishes artifacts.
- Tag workflow `v7.2.0-rc1` can create a GitHub Release containing the APK and SHA-256 file after all verification gates pass.

## Preserved features

- Fast group synchronization with Groups-filter, Select-All, and conservative All-Chats fallback strategies.
- DEEP / NEW_ONLY / UNREAD_ONLY extraction.
- Select All / Unread / Read / Active / Never Scanned / Failed filters.
- Pause / Resume / Stop / Skip / Retry Failed.
- TXT/ZIP WhatsApp chat import.
- XLSX / CSV / TXT / JSON export.
- Durable Room registry, sessions, queue, links, occurrences, and checkpoints.
- Optional overlay and foreground notification controls.
- Official WhatsApp / WhatsApp Business plus launcher-visible compatible package detection.
- Core runtime does not require Root or Shizuku.

## Verification

Fast local verification that does not require Android SDK:

```bash
TERM=xterm bash tools/release-audit.sh
```

The v7.2 source currently contains 39 pure-Kotlin smoke checks plus XML, feature-preservation, manifest, gesture, v7.1 regression, and v7.2 runtime-integration audits.

Full Android build on a machine with JDK 17 and Android SDK 35:

```bash
bash tools/build-release-candidate.sh
```

The command performs release audit, Android unit tests, lint, APK build, and SHA-256 generation.

## GitHub build

Upload the repository to GitHub and keep GitHub Actions enabled. The workflow `.github/workflows/build-android-apk.yml` runs on `main`, pull requests, or manual dispatch and uploads the verified APK artifact.

To create a GitHub Release after `main` is green:

```bash
git tag v7.2.0-rc1
git push origin v7.2.0-rc1
```

The tag must exactly match `versionName`; otherwise the release workflow fails closed.

## Gradle wrapper bootstrap

The repository contains wrapper scripts and pinned Gradle 8.9 metadata. If `gradle/wrapper/gradle-wrapper.jar` is absent, `tools/bootstrap-gradle-wrapper.sh` downloads the official Gradle 8.9 wrapper JAR and verifies SHA-256 before use. The Gradle 8.9 distribution itself is also pinned with SHA-256 in `gradle-wrapper.properties`.

## Security and privacy

- Android backup disabled.
- No `QUERY_ALL_PACKAGES` permission.
- Full message-text storage is OFF by default.
- Diagnostics are sanitized and do not intentionally store complete message bodies/full URLs.
- Gesture fallback uses only bounds of a semantically resolved Accessibility node; screen/state evidence remains the proof of transition success.

## Physical-device release gate

Static/JVM verification and an Android APK build do not prove compatibility with every WhatsApp/OEM/profile combination. Complete `DEVICE_TEST_v7.2.0-rc1.md` on the target device before treating this RC as production-certified.
