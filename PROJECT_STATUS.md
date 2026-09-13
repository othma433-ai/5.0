# Project Status — v5.0.0-rc1 Durable Execution RC

## Implemented

- Independent Android/Kotlin/Compose application.
- Event-driven Accessibility automation; no arbitrary coordinates and no fixed multi-second navigation sleeps.
- Official WhatsApp / Business detection plus launcher-visible compatible variants within Android package-visibility limits.
- Groups-filter synchronization without opening every group.
- Semantic Group List / Search / Chat screen evidence and conversation/message container scoring.
- Adaptive UI latency timing and runtime health governor (FAST/BALANCED/SAFE).
- Stage circuit breaker, sync coverage safety stop, conservative duplicate-title identity, and ambiguous-result refusal.
- Persistent Room registry, queue, sessions, links, occurrences, and message checkpoints.
- Deep / Unread-only / New-only extraction with bounded overlap fingerprint cache.
- **v5 durable viewport commit barrier:** newly discovered links are committed before the corresponding viewport can be advanced.
- Fresh chat-screen verification after persistence and before scrolling.
- **v5 smart queue:** selected groups are mode-aware prioritized without losing selection.
- **v5 failure-aware retry:** ambiguous identity and missing/unsupported group failures are protected from blind Retry Failed loops; attempts are bounded.
- **v5 recovery progress:** process/service reconnect restores terminal queue progress accurately.
- **v5 truthful completion:** sessions with residual failed groups end as `COMPLETED_WITH_ERRORS` and report their count.
- URL text/content-description/URLSpan extraction without opening links.
- URL normalization, tracking cleanup, classification, canonical dedupe, and occurrence tracking.
- Pause / Resume / Stop / Skip / Retry Failed.
- Optional floating controller + foreground notification controls.
- Live health score, effective mode, groups/minute, and links/minute telemetry.
- Export Chat TXT/ZIP import and XLSX / CSV / TXT / JSON export.
- Message-text storage off by default; backup disabled; diagnostics sanitized.
- Root and Shizuku remain optional and are not required for the core engine.

## Verification available in this environment

Run:

```bash
bash tools/release-audit.sh
```

The audit compiles/runs pure-Kotlin policies including v5 smart queue, durability, retry, and recovery progress; checks runtime control/throughput logic; parses Android XML; enforces privacy/version/source invariants; scans for unfinished placeholders; and rejects unsafe fixed multi-second automation sleeps.

## Remaining production gates

This environment does not provide Android SDK/Gradle/ADB, so no claim is made that an APK was compiled or physically certified here.

Before changing the label from release candidate to production:

1. Run GitHub Actions or Android Studio with JDK 17 / Android SDK 35.
2. Require `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` to pass.
3. Install on the target Android device.
4. Enable Accessibility and notifications; overlay is optional.
5. Sync a controlled set of real groups and compare the registry count with WhatsApp.
6. Test duplicate-title groups and verify no ambiguous result is opened.
7. Test New-only, Unread-only, and Deep extraction against known links.
8. Force-stop/relaunch during a viewport containing known links and verify the durable commit/recovery behavior.
9. Verify pause/resume and safe Retry Failed behavior, including protected ambiguous groups.
10. Repeat certification for each desired WhatsApp/Business/Dual/Work/Secure Folder context accessible from that Android profile.
