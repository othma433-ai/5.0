# WA Al-Othmany Link Bot v5.0.0-rc1

Standalone Android application for fast WhatsApp group synchronization and durable intelligent link extraction.

## Core workflow

1. Select a detected WhatsApp instance.
2. Run **Sync Groups**. The bot activates WhatsApp's Groups filter and scans conversation RecyclerViews in batches without opening every group.
3. Select All / Unread / Read / Active / Never Scanned / Failed groups.
4. Run **New**, **Unread**, or **Deep** extraction.
5. The v5 smart queue prioritizes high-value groups without dropping any user selection.
6. Every viewport containing new links is committed to Room before the bot is allowed to scroll away.
7. Pause/resume/skip/stop at any time; safe transient failures can be retried while identity/safety failures remain protected for review.
8. Export XLSX/CSV/TXT/JSON or import a WhatsApp Export Chat TXT/ZIP.

## v5 durable-execution architecture

v5 keeps all v4 adaptive-intelligence controls and adds:

- `SmartQueuePolicy`: deterministic mode-aware ordering for large extraction queues.
- `DurableViewportPolicy`: persistence-before-advance safety invariant.
- `FailureRecoveryPolicy`: distinguishes transient retries from ambiguous/unsafe failures.
- `QueueProgressPolicy`: accurate recovered-session and completed-with-errors reporting.
- Bounded `viewportCommitInFlight` barrier so repeated Accessibility events cannot scroll ahead of pending persistence.
- Fresh screen verification immediately before each post-commit scroll.
- Process-death resume restores already-completed queue progress instead of restarting the UI counter at zero.
- Extraction completion records `COMPLETED_WITH_ERRORS` when failed groups remain instead of falsely reporting an all-clear session.

Inherited v4 controls include semantic screen evidence, conversation/message container role scoring, adaptive timing, runtime health governor, stage circuit breaker, sync coverage safety stop, duplicate-title protection, persistent checkpoints, end-of-list proof, and throughput telemetry.

## Link engine

The engine reads text, content descriptions, and Android `URLSpan` targets exposed through Accessibility without opening links. It normalizes URLs, removes common tracking parameters while preserving functional parameters, classifies link types, collapses canonical duplicates, and records distinct occurrences.

Categories include WhatsApp groups/channels, Telegram, Google Drive, OneDrive, Dropbox, MEGA, YouTube, TikTok, Instagram, Facebook, X/Twitter, meetings, documents, websites, and Other.

## Privacy

- Full message-text storage is OFF by default.
- Android backup is disabled.
- `QUERY_ALL_PACKAGES` is not requested.
- Diagnostic export is sanitized and excludes complete message text/full URLs.
- Root and Shizuku are optional; core automation does not require them.

## Build and verification

Use Android Studio/JDK 17 with Android SDK 35, or the included GitHub Actions workflow.

CI runs:

```text
release audit
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
```

Local non-Android verification:

```bash
bash tools/release-audit.sh
```

## Physical-device certification

WhatsApp does not expose a public automation API for this workflow, and Accessibility layouts can change across WhatsApp/OEM/profile versions. `v5.0.0-rc1` therefore remains a release candidate until the exact target device and WhatsApp build pass the real-device gates in `PROJECT_STATUS.md`.
