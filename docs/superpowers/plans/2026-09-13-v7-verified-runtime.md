# v7 Verified Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make real WhatsApp group synchronization and link extraction resilient to missing/decorated filter chips while preserving existing features.

**Architecture:** Harden semantic control detection, add deterministic navigation fallback, and decouple extraction from the Groups filter. Keep persistence and safety policies unchanged where already sound.

**Tech Stack:** Kotlin, Android AccessibilityService, Coroutines, Room, Jetpack Compose.

**Spec:** `docs/superpowers/specs/2026-09-13-v7-verified-runtime-design.md`

## Global Constraints
- Android minSdk 26, target/compile SDK 35.
- Java/Kotlin target 17.
- Preserve all v6 user-visible capabilities.
- Never click unknown controls by coordinates.
- Never mark unseen groups missing during conservative all-chats fallback.

---

### Task 1: Semantic WhatsApp control matching
**Files:**
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/ControlLabelPolicy.kt`
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/AccessibilityTree.kt`
- Test: `tools/jvmtests/ControlLabelPolicySmoke.kt`

**Interfaces:**
- Produces: `ControlLabelPolicy.matchesDecorated(candidate, labels)` and robust `findByControlLabel` behavior.

- [ ] Add failing smoke cases for `المجموعات +99 دردشة`, `Groups, 12 unread`, RTL counter placement.
- [ ] Run smoke test and confirm failure.
- [ ] Implement semantic token matching without allowing arbitrary conversation titles.
- [ ] Run smoke test and confirm pass.

### Task 2: Deterministic sync navigation
**Files:**
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt`
- Create: `app/src/main/java/com/waalothmany/linkbot/automation/NavigationFallbackPolicy.kt`
- Test: `tools/jvmtests/NavigationFallbackPolicySmoke.kt`

**Interfaces:**
- Produces: `NavigationFallbackPolicy.decide(groupsFound, chatsFound, missCount, inChat)`.

- [ ] Write failing policy smoke test.
- [ ] Run and confirm failure.
- [ ] Implement bounded Chats-anchor -> group-filter -> all-chats fallback decisions.
- [ ] Wire OPENING stage to policy and fresh-root probes.
- [ ] Run smoke tests.

### Task 3: Extraction without Groups filter dependency
**Files:**
- Modify: `app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt`
- Create: `app/src/main/java/com/waalothmany/linkbot/automation/ExtractionNavigationPolicy.kt`
- Test: `tools/jvmtests/ExtractionNavigationPolicySmoke.kt`

**Interfaces:**
- Produces: bounded choice `GROUP_FILTER_SEARCH`, `CHATS_SEARCH`, `WAIT`, `FAIL`.

- [ ] Write failing policy smoke tests.
- [ ] Run and confirm failure.
- [ ] Implement direct Chats -> Search fallback after bounded misses.
- [ ] Add active probes for extraction navigation stages.
- [ ] Run smoke tests.

### Task 4: Runtime diagnostics and anti-loop guard
**Files:**
- Modify: `WaAccessibilityService.kt`
- Modify: `DiagnosticSanitizer.kt` if needed.

**Interfaces:**
- Produces diagnostic events `SYNC_NAV_DECISION`, `EXTRACT_NAV_DECISION`, `CONTROL_EVIDENCE` without message bodies.

- [ ] Add diagnostic events for strategy decisions.
- [ ] Ensure repeated sync taps are ignored while active.
- [ ] Ensure package mismatch never performs actions.
- [ ] Run core audit.

### Task 5: Version, preservation, release audit
**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `tools/verify-core.sh`
- Modify: `tools/release-audit.sh`
- Add/update smoke tests.

**Interfaces:**
- Produces v7.0.0-rc1 source package.

- [ ] Bump version code/name.
- [ ] Update release gates.
- [ ] Run full release audit.
- [ ] Package source, patch, release notes, device test instructions and SHA-256.
