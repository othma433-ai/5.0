# Runtime Reliability Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the existing v7.1.0-rc1 runtime while preserving features, adding regression evidence, and producing GitHub-ready build/release assets.

**Architecture:** Introduce operation leases/idempotent terminal gates around the existing automation state machine; separate Accessibility setting/connection/heartbeat state; coalesce event pressure; make link/checkpoint persistence transactional and count committed rows; move diagnostics off the Accessibility callback thread; integrate optional Shizuku honestly. Preserve existing adapters and sync/extraction modes rather than replacing the app.

**Tech Stack:** Kotlin 2.0.21, Android/AccessibilityService, Coroutines, Room 2.6.1, Jetpack Compose/Material 3, AGP 8.7.3, Java 17, Gradle 8.9 target wrapper.

**Spec:** `docs/superpowers/specs/2026-09-14-runtime-reliability-hardening-design.md`

## Global Constraints
- `WA-Al-Othmany-Link-Bot-v7.1.0-rc1-Runtime-Reliability-source.zip` is the sole baseline authority.
- No feature removal to make tests/build pass.
- No versionCode/versionName bump until compile + tests + lint + debug build + release audit pass.
- Standard Accessibility operation must work without Shizuku/root.
- Device E2E success cannot be claimed without a real Android/WhatsApp device run.

---

### Task 1: Establish regression harness and start-gate correctness
**Files:**
- Modify: `tools/jvmtests/OperationStartGateSmoke.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/capability/OperationStartGate.kt`
- Modify: `tools/verify-core.sh` only if a new test source is introduced.

- [x] Add a failing assertion that notifications-not-ready blocks an operation.
- [x] Run the focused pure Kotlin smoke and observe RED.
- [x] Add the minimal gate condition.
- [x] Re-run focused smoke and release audit; observe GREEN.

### Task 2: Operation generation/lease and idempotent terminal transitions
**Files:**
- Create: `app/src/main/java/com/waalothmany/linkbot/runtime/OperationLease.kt`
- Create: `tools/jvmtests/OperationLeaseSmoke.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt`
- Modify: `tools/verify-core.sh`

- [x] Write RED tests: old generation rejected, same-generation callback accepted, terminal completion accepted once only.
- [x] Add `OperationLeaseController` with monotonic generation and terminal gate.
- [x] Thread lease checks through delayed sync/extraction probes and terminal completion paths.
- [x] Reject start of any second operation while another operation is active.
- [x] Run smoke/release audit GREEN.

### Task 3: Accessibility five-state readiness and stale heartbeat
**Files:**
- Modify: `app/src/main/java/com/waalothmany/linkbot/capability/AccessibilityConnectionPolicy.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/capability/AccessibilityConnectionMonitor.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/capability/CapabilityManager.kt`
- Modify: `tools/jvmtests/AccessibilityConnectionPolicySmoke.kt`
- Modify consumers in ViewModel/service as required.

- [x] Write RED state-derivation tests for DISABLED, ENABLED_NOT_CONNECTED, CONNECTED, STALE, DISCONNECTED.
- [x] Implement pure state derivation with stale timeout.
- [x] Make READY/coreReady require CONNECTED.
- [x] Suspend active automation on lost/stale live connection rather than advancing stages.
- [x] Verify GREEN and release audit.

### Task 4: Verified navigation and global-search safety
**Files:**
- Modify: `NavigationFallbackPolicy.kt`, `ExtractionNavigationPolicy.kt`, `SearchResolutionPolicy.kt` or create a focused search-editor policy.
- Modify: corresponding `tools/jvmtests/*Smoke.kt`.
- Modify: `WaAccessibilityService.kt` navigation methods.

- [x] Add RED cases for `groups=false,chats=true,screen=UNKNOWN` bounded transition.
- [x] Add RED case proving a chat composer cannot be used as global search.
- [x] Implement explicit screen-evidence requirements and bounded fallback/recovery.
- [x] Keep Groups-filter path primary, all-chats and global-search fallbacks secondary.
- [x] Verify GREEN.

### Task 5: Event coalescing and callback pressure
**Files:**
- Create: `automation/AccessibilityEventCoalescer.kt`
- Create: `tools/jvmtests/AccessibilityEventCoalescerSmoke.kt`
- Modify: `WaAccessibilityService.kt`.

- [x] RED tests: window-state never coalesced; repeated content-changed within window coalesced; latest event admitted after window/generation change.
- [x] Implement bounded pure coalescer.
- [x] Integrate without retaining `AccessibilityNodeInfo` objects.
- [x] Verify GREEN.

### Task 6: Async bounded diagnostics
**Files:**
- Create pure queue policy/model if needed.
- Modify: `runtime/DiagnosticLog.kt`.
- Add `tools/jvmtests/DiagnosticQueuePolicySmoke.kt`.

- [x] RED tests for bounded capacity/drop accounting/rotation thresholds at policy level.
- [x] Replace caller-thread file writes with bounded channel/queue + IO writer.
- [x] Keep export deterministic and include dropped-event count.
- [x] Verify no Accessibility callback performs log file reads/writes directly.
- [x] Verify smoke/release audit GREEN.

### Task 7: Persistence counts and durable completion transaction
**Files:**
- Modify: `data/Repositories.kt`, `data/Daos.kt`, `automation/WaAccessibilityService.kt`.
- Add pure persistence-result tests plus Android Room tests under `app/src/androidTest/`.
- Modify `app/build.gradle.kts` for room-testing/android-test dependencies.

- [x] RED pure test for committed-vs-detected counter semantics.
- [x] Make `recordBatch` return `LinkPersistResult(newLinks, insertedOccurrences, duplicateOccurrences)`.
- [x] Log `LINK_PERSISTED count=insertedOccurrences` and increment runtime only from committed inserts.
- [x] Add one Room transaction for queue final state + group checkpoint.
- [x] Add database tests verifying duplicate canonical URL and duplicate occurrence behavior and no checkpoint advance on transaction failure.
- [x] Run all available tests.

### Task 8: Database identity/migration safety
**Files:**
- Modify: `Entities.kt`, `AppDatabase.kt`, detector/repository code.
- Add migration test under `app/src/androidTest/`.

- [ ] RED migration/schema test for preserving existing instance/group/link data.
- [ ] Add profile/installation identity fields with deterministic legacy defaults and composite identity semantics.
- [ ] Update instance ID construction without claiming discovery of inaccessible profiles.
- [ ] Verify migration test when Android tooling is available; keep static schema checks now.

### Task 9: Real optional Shizuku + honest root capability
**Files:**
- Modify: `app/build.gradle.kts`, Manifest if provider setup requires it.
- Create capability classes/state models.
- Modify capability UI/readiness consumers.
- Add pure capability-state tests; Android-facing adapter tests where practical.

- [ ] RED tests for all requested Shizuku states and execution-mode selection.
- [ ] Add official `dev.rikka.shizuku:api:13.1.5` and `provider:13.1.5` integration.
- [ ] Register binder received/dead and permission result listeners; verify lightweight capability before READY.
- [ ] Keep Standard mode independent.
- [ ] Replace root-file-existence READY semantics with bounded capability probe state.
- [ ] Verify source/static tests; Gradle verify when environment allows.

### Task 10: Runtime acceptance logging and feature-preservation expansion
**Files:**
- Modify: `tools/feature-preservation-smoke.py`, `tools/v71-runtime-integration-smoke.py` or successor.
- Modify runtime logs.
- Create/update device acceptance guide.

- [ ] Add preservation checks for every user-listed feature and both WhatsApp packages.
- [ ] Enforce deterministic acceptance milestone log codes.
- [ ] Add failure-reason log requirement at every terminal failure stage.
- [ ] Run preservation/integration smoke GREEN.

### Task 11: Gradle wrapper, CI, release workflow, docs
**Files:**
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.
- Create: `.github/workflows/android-ci.yml`, `.github/workflows/release.yml`.
- Modify/create: `.gitignore`, `README.md`, `CHANGELOG.md`, `LICENSE` if appropriate, release/device docs.

- [ ] Add wrapper pinned to compatible Gradle with checksum validation.
- [ ] CI: Java 17, Android SDK, wrapper validation/cache, release audit, unit tests, lint, assembleDebug, APK verification/upload.
- [ ] Release: tag `vX.Y.Z`, tests/lint/release build, SHA-256, artifacts/release notes; signing behavior explicit.
- [ ] Audit workflows by capabilities, not hard-coded legacy workflow filename.
- [ ] Update README with permissions, Accessibility, optional Shizuku, supported instances, modes, import/export, diagnostics, privacy, local/CI build.

### Task 12: Final verification and conditional version/release packaging
**Files:** release metadata only after gate passes.

- [ ] Run `tools/release-audit.sh` and all source smokes.
- [ ] Run `./gradlew testDebugUnitTest`, `./gradlew lintDebug`, `./gradlew assembleDebug` in Android-capable environment.
- [ ] Run release build only if signing/config permits; otherwise report exact constraint.
- [ ] If and only if all required non-device release gates pass, bump version and rerun entire gate from clean state.
- [ ] Produce ZIP, APK(s) actually built, SHA-256, verification report, changelog, release notes, device guide, fixed-root-causes list, and unverified-items list.
