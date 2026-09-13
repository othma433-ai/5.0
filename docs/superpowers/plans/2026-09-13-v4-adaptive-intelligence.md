# WA Al-Othmany Link Bot v4 Adaptive Intelligence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Raise the standalone Android bot from v3 stability hardening to a v4 adaptive-intelligence release candidate that is safer under WhatsApp UI drift, automatically balances speed vs. stability, reduces duplicate viewport work, and exposes runtime health to the user.

**Architecture:** Preserve the event-driven Accessibility state machine and Room persistence. Add pure-Kotlin semantic screen evidence, a session health governor, viewport overlap intelligence, and throughput telemetry. Accessibility actions remain gated by verifiable UI evidence; no arbitrary screen coordinates and no fixed multi-second navigation delays are introduced.

**Tech Stack:** Kotlin, Android AccessibilityService, Jetpack Compose, Room, Coroutines, Gradle/AGP, JVM smoke verification.

**Spec:** `docs/superpowers/specs/2026-09-13-wa-al-othmany-link-bot-design.md`

## Global Constraints

- Core operation remains possible without Root or Shizuku.
- Group synchronization must not open each group.
- Never choose an ambiguous duplicate-title search result.
- No arbitrary screen-coordinate tapping.
- No fixed multi-second navigation delays.
- Message content remains excluded from diagnostics.
- Existing v3 Room data remains compatible.
- Physical-device certification remains a final release gate.

---

### Task 1: Semantic screen evidence guard

**Files:**
- Create: `app/src/main/java/com/waalothmany/linkbot/automation/ScreenEvidencePolicy.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/AccessibilityTree.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt`
- Test: `tools/jvmtests/ScreenEvidencePolicySmoke.kt`

**Interfaces:**
- Produces: `ScreenKind`, `ScreenEvidence`, `ScreenEvidencePolicy.classify(...)`.
- AccessibilityTree converts Android nodes into pure evidence.
- WaAccessibilityService gates sync/chat/search transitions on semantic screen confidence.

- [x] Write the failing screen-evidence smoke test.
- [x] Run verification and confirm compile failure because the policy does not exist.
- [x] Implement the pure-Kotlin policy.
- [x] Add Android evidence extraction and action gating.
- [x] Run the full core suite.

### Task 2: Adaptive stability governor

**Files:**
- Create: `app/src/main/java/com/waalothmany/linkbot/automation/AutomationHealthPolicy.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/runtime/BotRuntime.kt`
- Test: `tools/jvmtests/AutomationHealthPolicySmoke.kt`

**Interfaces:**
- Produces: `AutomationHealthPolicy.recordSuccess()`, `recordTransientFailure()`, `recordAmbiguity()`, `snapshot()`.
- Snapshot includes health score and recommended `PerformanceMode`.
- Runtime exposes health and effective mode to UI/notification.

- [x] Write the failing health-governor smoke test.
- [x] Verify RED.
- [x] Implement health policy with bounded score and hysteresis.
- [x] Integrate health events with stage success/failure and dynamically rebuild adaptive timing policy only when mode changes.
- [x] Run full verification.

### Task 3: Intelligent message viewport processing

**Files:**
- Create: `app/src/main/java/com/waalothmany/linkbot/automation/MessageViewportPolicy.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/AccessibilityTree.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt`
- Test: `tools/jvmtests/MessageViewportPolicySmoke.kt`

**Interfaces:**
- Produces: overlap-aware unseen message selection and unread-boundary slicing.
- Service tracks a bounded fingerprint window per group to avoid reprocessing overlapping RecyclerView viewports.

- [x] Write failing overlap/unread-boundary tests.
- [x] Verify RED.
- [x] Implement pure viewport policy.
- [x] Add unread marker detection in Android tree extraction.
- [x] Integrate bounded seen-fingerprint cache per current group.
- [x] Run full verification.

### Task 4: Runtime telemetry and compact professional UI

**Files:**
- Create: `app/src/main/java/com/waalothmany/linkbot/runtime/ThroughputMeter.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/runtime/BotRuntime.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/ui/AppUi.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/runtime/BotForegroundService.kt`
- Test: `tools/jvmtests/ThroughputMeterSmoke.kt`

**Interfaces:**
- Produces groups/minute, links/minute, effective mode, health score.
- UI displays concise operational health without exposing message content.

- [x] Write failing throughput test.
- [x] Verify RED.
- [x] Implement pure throughput meter.
- [x] Integrate runtime counters and display.
- [x] Run verification.

### Task 5: v4 release engineering

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `PROJECT_STATUS.md`
- Create: `RELEASE_NOTES_v4.0.0-rc1.md`
- Modify: `tools/verify-core.sh`
- Modify: `tools/release-audit.sh`

- [x] Raise to `versionCode = 40`, `versionName = "4.0.0-rc1"`.
- [x] Add new policies/tests to core verification.
- [x] Strengthen static invariants for semantic screen gating and adaptive health.
- [x] Run `bash tools/release-audit.sh`.
- [x] Commit verified source and package source ZIP with SHA-256.
