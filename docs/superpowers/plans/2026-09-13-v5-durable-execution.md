# WA Al-Othmany Link Bot v5 Durable Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Raise v4 adaptive intelligence to a v5 release candidate with durable viewport persistence, intelligent queue priority, accurate process-death recovery progress, and failure-aware retry behavior.

**Architecture:** Preserve the Accessibility state machine, Room persistence, semantic screen guards, and adaptive timing. Add pure-Kotlin queue/retry/durability policies, then integrate them into the existing service so a viewport is never advanced before its discovered links are committed, interrupted sessions resume with correct progress, and unsafe failures are not retried blindly.

**Tech Stack:** Kotlin, Android AccessibilityService, Room, Coroutines, Jetpack Compose, JVM smoke verification.

**Spec:** `docs/superpowers/specs/2026-09-13-wa-al-othmany-link-bot-design.md`

## Global Constraints

- Core operation remains possible without Root or Shizuku.
- Group synchronization never opens every group.
- Never choose an ambiguous duplicate-title result automatically.
- Never advance away from a viewport containing newly discovered links before persistence succeeds.
- No arbitrary screen-coordinate tapping.
- No fixed multi-second navigation delays.
- Physical-device certification remains the final production gate.

---

### Task 1: Smart extraction queue priority

**Files:**
- Create: `app/src/main/java/com/waalothmany/linkbot/automation/SmartQueuePolicy.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt`
- Test: `tools/jvmtests/SmartQueuePolicySmoke.kt`

- [x] Write failing queue-priority smoke test.
- [x] Verify RED.
- [x] Implement deterministic mode-aware queue ordering.
- [x] Integrate ordering before queue persistence.
- [x] Run core verification.

### Task 2: Durable viewport commit barrier

**Files:**
- Create: `app/src/main/java/com/waalothmany/linkbot/automation/DurableViewportPolicy.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt`
- Test: `tools/jvmtests/DurableViewportPolicySmoke.kt`

- [x] Write failing durability policy test.
- [x] Verify RED.
- [x] Implement persistence-before-advance policy.
- [x] Serialize viewport persistence and perform scroll only after successful commit.
- [x] Fail safely on persistence error without advancing/checkpointing past unsaved content.
- [x] Run full verification.

### Task 3: Failure-aware retry policy

**Files:**
- Create: `app/src/main/java/com/waalothmany/linkbot/automation/FailureRecoveryPolicy.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt`
- Test: `tools/jvmtests/FailureRecoveryPolicySmoke.kt`

- [x] Write failing retry-disposition test.
- [x] Verify RED.
- [x] Implement retry/quarantine/exhaustion decisions.
- [x] Make Retry Failed reset only safe retry candidates.
- [x] Run verification.

### Task 4: Accurate recovered-session progress

**Files:**
- Create: `app/src/main/java/com/waalothmany/linkbot/automation/QueueProgressPolicy.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt`
- Test: `tools/jvmtests/QueueProgressPolicySmoke.kt`

- [x] Write failing terminal-progress test.
- [x] Verify RED.
- [x] Implement progress summary policy.
- [x] Restore completed count and link counter semantics on service reconnect.
- [x] Run verification.

### Task 5: v5 release engineering

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `tools/verify-core.sh`
- Modify: `tools/release-audit.sh`
- Modify: `README.md`
- Modify: `PROJECT_STATUS.md`
- Create: `RELEASE_NOTES_v5.0.0-rc1.md`

- [x] Raise to `versionCode = 50`, `versionName = "5.0.0-rc1"`.
- [x] Add v5 policies/tests to release verification.
- [x] Add static invariant requiring durable viewport commit barrier.
- [x] Run release audit.
- [x] Package source ZIP and SHA-256.
