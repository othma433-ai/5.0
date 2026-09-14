# Universal WhatsApp Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make WhatsApp selector resolution package-neutral and instance-adaptive while retaining the existing conservative sync/extraction engine.

**Architecture:** Static adapter knowledge remains the deterministic baseline. A small pure Kotlin selector policy generates package-neutral suffix hints, while an Android SharedPreferences store persists only verified selector metadata per instance. WaAccessibilityService resolves controls through learned hints, suffix hints, full IDs, labels, then structural fallback and records selectors only after verification.

**Tech Stack:** Kotlin, Android AccessibilityService, SharedPreferences, Room (existing data only), Coroutines, Jetpack Compose.

**Spec:** `docs/superpowers/specs/2026-09-14-universal-whatsapp-adapter-design.md`

## Global Constraints
- Core mode must remain non-root.
- Do not store conversation text in adaptive selector metadata.
- Do not retain AccessibilityNodeInfo objects beyond the current action/snapshot.
- Keep existing Deep/Unread/New extraction behavior and persistent queue semantics intact.
- No fixed multi-second navigation delays.

---

### Task 1: Package-neutral selector policy
**Files:**
- Create: `app/src/main/java/com/waalothmany/linkbot/whatsapp/AdaptiveSelectorPolicy.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/whatsapp/WhatsAppAdapter.kt`
- Test: `tools/jvmtests/AdaptiveSelectorPolicySmoke.kt`

**Interfaces:**
- Produces: `SelectorRole`, `SelectorSignature`, `AdaptiveSelectorPolicy.resourceIdSuffix`, `AdaptiveSelectorPolicy.buildHints`.

- [ ] Write failing tests for suffix extraction and dynamic hint generation.
- [ ] Run verification and confirm failure because the policy does not exist.
- [ ] Implement minimal policy and adapter suffix contracts.
- [ ] Run tests and confirm pass.

### Task 2: Instance-scoped learned selector store
**Files:**
- Create: `app/src/main/java/com/waalothmany/linkbot/whatsapp/AdaptiveSelectorStore.kt`
- Create: `app/src/main/java/com/waalothmany/linkbot/whatsapp/AdaptiveSelectorMemory.kt`
- Test: `tools/jvmtests/AdaptiveSelectorMemorySmoke.kt`

**Interfaces:**
- Produces: pure `AdaptiveSelectorMemory` scoring/pruning behavior plus Android persistence wrapper `AdaptiveSelectorStore`.

- [ ] Write failing tests for per-instance isolation and confidence/pruning.
- [ ] Verify failure.
- [ ] Implement pure memory model and SharedPreferences wrapper.
- [ ] Verify pass.

### Task 3: Accessibility suffix search and verified learning
**Files:**
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/AccessibilityTree.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt`
- Create: `tools/adaptive-selector-integration-smoke.py`

**Interfaces:**
- Consumes: `SelectorRole`, adapter suffixes, `AdaptiveSelectorStore`.
- Produces: lookup order learned -> suffix -> full ID -> labels -> structure; records verified Groups/All/Chats/Select-All selectors.

- [ ] Add failing integration smoke assertions.
- [ ] Verify failure.
- [ ] Implement suffix matching and service integration.
- [ ] Run core + integration smoke.

### Task 4: Reapply group-sync hotfix to advanced v7.1 base
**Files:**
- Modify: `app/src/main/java/com/waalothmany/linkbot/whatsapp/WhatsAppAdapter.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/NavigationFallbackPolicy.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/ControlLabelPolicy.kt` if required by regression.

**Interfaces:**
- Produces: current Groups/All selector suffixes for Personal/Business/generic and conservative fallback threshold.

- [ ] Add regression assertions for official Personal and Business IDs/labels.
- [ ] Verify failure if advanced branch lacks the hotfix.
- [ ] Apply only the missing hotfix parts.
- [ ] Re-run core suite.

### Task 5: Release verification and artifact
**Files:**
- Modify: `tools/release-audit.sh` to include adaptive selector integration smoke.
- Create: `UNIVERSAL_ADAPTER_VERIFICATION.md`

- [ ] Run `tools/release-audit.sh`.
- [ ] Run `git diff --check` equivalent whitespace validation.
- [ ] Attempt `./gradlew test`, `./gradlew lint`, `./gradlew assembleDebug`.
- [ ] Package source ZIP with verification report and checksums.
