# WA Al-Othmany Link Bot v6 Corrective Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a v6 rescue source release whose core Android automation can only start when Accessibility is truly connected, while preserving the v5.2 feature set.

**Architecture:** Introduce an observable Accessibility connection monitor and a pure operation-start gate. Rework capability/readiness to consume actual runtime connection state, reconcile WhatsApp instances, repair UI truthfulness/versioning, and add feature-preservation regression checks.

**Tech Stack:** Kotlin, Android AccessibilityService, Jetpack Compose, Room, Kotlin Coroutines/StateFlow, JUnit/pure Kotlin smoke tests.

**Spec:** `docs/superpowers/specs/2026-09-13-v6-corrective-rebuild-design.md`

## Global Constraints

- Android minSdk 26, targetSdk/compileSdk 35.
- Java/Kotlin JVM target 17.
- Overlay remains optional.
- No broad `QUERY_ALL_PACKAGES` permission.
- No message-body content in diagnostics by default.
- Do not remove existing automation/import/export/control features.
- Do not advertise Shizuku/Root as active execution modes without a verified runtime adapter.

---

### Task 1: Accessibility runtime connection state

**Files:**
- Create: `app/src/main/java/com/waalothmany/linkbot/capability/AccessibilityConnectionMonitor.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt`
- Test: `tools/jvmtests/AccessibilityConnectionPolicySmoke.kt`

**Interfaces:**
- Produces: `AccessibilityConnectionState`, `AccessibilityConnectionPolicy.isOperational(enabled, connected)`.
- Service marks monitor connected/disconnected and records diagnostics.

- [ ] Write failing pure test for enabled-but-disconnected being non-operational.
- [ ] Run test and confirm failure before implementation.
- [ ] Implement policy + Android monitor wrapper.
- [ ] Re-run test to PASS.

### Task 2: Honest capability/readiness model

**Files:**
- Modify: `app/src/main/java/com/waalothmany/linkbot/capability/CapabilityManager.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/capability/ReadinessEvaluator.kt`
- Test: `tools/jvmtests/ReadinessEvaluatorSmoke.kt`

**Interfaces:**
- Consumes: connection monitor state.
- Produces: readiness blockers that distinguish setting-enabled vs runtime-connected.

- [ ] Write failing readiness test for enabled/disconnected.
- [ ] Verify RED.
- [ ] Implement separated fields and honest execution-mode labels.
- [ ] Verify GREEN.

### Task 3: Central operation-start gate

**Files:**
- Create: `app/src/main/java/com/waalothmany/linkbot/capability/OperationStartGate.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/MainViewModel.kt`
- Test: `tools/jvmtests/OperationStartGateSmoke.kt`

**Interfaces:**
- Produces: `OperationStartDecision` with deterministic reason codes.

- [ ] Write failing gate tests for disconnected service, missing instance, and valid start.
- [ ] Verify RED.
- [ ] Implement gate and wire both sync/extraction through it.
- [ ] Verify GREEN.

### Task 4: Runtime service lifecycle recovery

**Files:**
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/runtime/BotRuntime.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/MainViewModel.kt`

**Interfaces:**
- Service connection transitions immediately affect UI readiness and runtime phase.

- [ ] Add lifecycle state-transition assertions to static smoke checks.
- [ ] Implement connected/disconnected/interrupted transitions.
- [ ] Verify pure/static checks.

### Task 5: WhatsApp instance reconciliation and truthfulness

**Files:**
- Modify: `app/src/main/java/com/waalothmany/linkbot/whatsapp/WhatsAppInstanceDetector.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/MainViewModel.kt`
- Create: `app/src/main/java/com/waalothmany/linkbot/whatsapp/InstanceInventoryPolicy.kt`
- Test: `tools/jvmtests/InstanceInventoryPolicySmoke.kt`

**Interfaces:**
- Produces deterministic reconciliation for detected current-profile instances while preserving known records safely.

- [ ] Write failing reconciliation tests.
- [ ] Verify RED.
- [ ] Implement policy and persistence reconciliation.
- [ ] Verify GREEN.

### Task 6: UI rescue and truthful status

**Files:**
- Modify: `app/src/main/java/com/waalothmany/linkbot/ui/AppUi.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/MainActivity.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- UI displays BuildConfig version, separate enabled/connected state, and a reconnect action.

- [ ] Add static regression test for no hard-coded `v4` and no invalid weight import.
- [ ] Implement UI changes.
- [ ] Verify static test PASS.

### Task 7: Feature-preservation regression gate

**Files:**
- Create: `tools/feature-preservation-smoke.py`
- Modify: `tools/release-audit.sh`
- Modify: `tools/verify-core.sh`

**Interfaces:**
- Release audit fails if promised v5.2 features disappear.

- [ ] Write smoke test that checks automation modes, selection actions, export formats, controls, multi-strategy sync, import path, overlay/notification services.
- [ ] Run against current source and capture gaps.
- [ ] Fix only real accidental omissions.
- [ ] Re-run to PASS.

### Task 8: Release v6 rescue package

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `RELEASE_NOTES_v6.0.0-rc1.md`
- Create: `PROJECT_STATUS_v6.0.0-rc1.md`

**Interfaces:**
- Version `6.0.0-rc1`, versionCode `60`.

- [ ] Run all pure Kotlin smoke tests.
- [ ] Run XML/static/release audit.
- [ ] Package source ZIP and verification report.
- [ ] Compute SHA-256.
