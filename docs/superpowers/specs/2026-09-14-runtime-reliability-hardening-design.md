# WA Al-Othmany Link Bot — Runtime Reliability Hardening Design

## Source of truth
The only code baseline is `WA-Al-Othmany-Link-Bot-v7.1.0-rc1-Runtime-Reliability-source.zip` as imported into this repository. Historical status/release documents are informational only and never evidence of current behavior.

## Goal
Harden the existing application without deleting working features so synchronization, navigation, extraction, persistence, recovery, diagnostics, and optional capabilities have deterministic ownership, bounded failure behavior, and testable contracts.

## Non-negotiable product constraints
- Standard Accessibility operation remains the core path and must not require Shizuku or root.
- Preserve Personal WhatsApp and WhatsApp Business support already present.
- Preserve Groups/Links/Occurrences, group filters, DEEP/NEW_ONLY/UNREAD_ONLY, Pause/Resume/Stop/Skip/Retry, TXT/ZIP import, XLSX/CSV/TXT/JSON export, overlay, notification controller, diagnostics, adaptive timing, checkpoints, and multi-strategy sync.
- Do not claim Dual Messenger, Work Profile, Secure Folder, cloned, or modified WhatsApp support unless executable discovery/navigation exists for that profile/installation.
- No production-ready/stable/build-success claim without the corresponding executed evidence.
- Do not bump version until compile + unit tests + lint + debug build + release audit all pass in an Android-capable environment.

## Architecture
### 1. Runtime ownership and terminal safety
Every Sync or Extraction receives an immutable operation generation/lease. Delayed probes, Accessibility callbacks, database completions, retry handlers, and terminal completions must present the current lease before mutating operation state. Old generation callbacks are ignored and logged. Terminal transitions use an idempotent terminal gate so group/session completion occurs once.

### 2. Accessibility readiness model
Separate OS setting from live service connection. Public state is `DISABLED`, `ENABLED_NOT_CONNECTED`, `CONNECTED`, `STALE`, or `DISCONNECTED`. `CONNECTED` requires a live binder callback and a non-stale heartbeat. A disconnect/stale transition suspends active automation safely instead of continuing blind actions. Starting Sync/Extraction requires operational Accessibility.

### 3. Verified navigation state machine
Keep the existing multi-strategy sync and selector policies but enforce Discover → Action → Verify for transitions. `groupsFound=false, chatsFound=true, screen=UNKNOWN` cannot loop indefinitely: bounded misses move from Chats anchoring to conservative all-chats fallback or explicit recovery. Extraction global-search entry must never treat an in-chat composer as a global-search editor; CHAT must return to Chats first.

### 4. Event pressure and tree processing
Window-state events remain immediate. High-frequency `TYPE_WINDOW_CONTENT_CHANGED` events are coalesced within a bounded interval per operation/screen while preserving the latest signal. Accessibility callbacks only capture minimal state and schedule work; no file I/O or heavy serialization is allowed on the callback path. Child traversal order remains deterministic and is regression-tested.

### 5. Persistence correctness
`LinkRepository.recordBatch` returns committed counts (`newLinks`, `insertedOccurrences`, `duplicateOccurrences`). `LINK_PERSISTED count=N` and runtime counters use committed rows, never raw detections. Link+occurrence writes remain one Room transaction. Queue completion + group checkpoint are committed in one transaction so a failed persistence transition never advances a durable checkpoint.

### 6. Diagnostics
Replace synchronous file logging with a bounded asynchronous queue serviced on an IO dispatcher/thread. Rotation is performed only by the writer. Queue overflow increments a dropped-record counter rather than blocking Accessibility. Export snapshots are synchronized safely and expose dropped count.

### 7. Retry/recovery
Retry is bounded by stage-specific budgets and adaptive/exponential backoff. Existing circuit breakers are preserved and extended where necessary. Accessibility loss suspends. Group filter absence falls back. Group-open failure falls back to global search only after Chats is verified. No infinite retry loops.

### 8. Shizuku and root capabilities
Use the official Shizuku API/provider integration. Shizuku state is `NOT_INSTALLED`, `BINDER_UNAVAILABLE`, `PERMISSION_REQUIRED`, `READY`, `DEAD_BINDER`, `FAILED`; `READY` requires binder + permission + lightweight capability check. It is optional and never gates Standard mode. Root is optional; file existence alone is not `READY`. Capability selection is honest: STANDARD unless an enhanced adapter is actually verified.

### 9. Instance identity
Keep existing database compatibility while extending instance identity toward `(packageName, profileIdentity, installationIdentity)`. Existing rows migrate with deterministic default identity values. Runtime must not infer unsupported profiles.

### 10. Build/release engineering
Add a complete Gradle wrapper, capability-based CI audit, Android CI, and tag-based release workflow. CI uses Java 17, executes release audit + unit tests + lint + assembleDebug, verifies the APK, and uploads artifacts. Release workflow builds release only when signing is configured, otherwise creates a clearly labeled unsigned/release artifact if Android tooling permits. SHA-256 files are generated.

## Verification strategy
1. Preserve current baseline smoke suite.
2. Add failing pure Kotlin regressions before policy/runtime fixes.
3. Add Android unit/instrumentation tests for Room transactions and migrations where platform APIs are required.
4. Run `tools/release-audit.sh` after each cluster.
5. In Android-capable environment run `./gradlew testDebugUnitTest`, `./gradlew lintDebug`, `./gradlew assembleDebug`, and release build when signing/config allows.
6. Device E2E remains explicitly unverified unless a real Android/WhatsApp device is available. Device acceptance logs must follow the requested contract.

## Release gate
Version bump is forbidden until the Android compile/test/lint/debug-build gate and release audit pass. If this execution environment lacks Android SDK/Gradle dependency resolution, source hardening may be delivered with that gate OPEN and no fabricated APK/version bump.
