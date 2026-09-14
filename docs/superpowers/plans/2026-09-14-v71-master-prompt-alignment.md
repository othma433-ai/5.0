# WA Al-Othmany v7.1 Master-Prompt Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade v7.1.0-rc1 HARDENING into a buildable, testable codebase aligned with the approved WA Al-Othmany Concept Design without regressing existing hardening.

**Architecture:** Preserve the current automation engine and hardening, add typed runtime/capability state, a real adapter boundary, profile-aware instance metadata, missing registry/queue controls, adaptive navigation probes, and stronger operation ownership. Android-profile and enhanced-mode claims remain capability-driven and truthful.

**Tech Stack:** Kotlin 2.x toolchain as declared by project, Android SDK 35, Jetpack Compose/Material 3, Room, Coroutines/Flow, AccessibilityService, JUnit + pure-Kotlin smoke harness.

**Spec:** `docs/superpowers/specs/2026-09-14-v71-master-prompt-alignment-design.md`

## Global Constraints
- Preserve v7.1 hardening components and current functionality.
- Standard mode must work without root or Shizuku.
- No private WhatsApp DB access or sandbox bypass in standard mode.
- No fake support claims for inaccessible Android profiles or variants.
- No fixed multi-second navigation sleeps.
- Every production behavior change starts with a failing test/smoke.

---

### Task 1: Runtime phases and exclusive operation ownership
**Files:** modify `runtime/BotRuntime.kt`, `runtime/OperationLease.kt`; add/update runtime smoke tests.
**Produces:** full runtime phase enum; atomic/generation-safe operation lease; explicit busy ownership.
- [ ] Add failing tests for missing phases and stale lease rejection.
- [ ] Run focused smoke and confirm failure.
- [ ] Implement typed phases and stronger lease semantics.
- [ ] Run focused + existing runtime/lease tests.

### Task 2: Runtime capability modes
**Files:** modify `capability/CapabilityManager.kt`, `capability/ReadinessEvaluator.kt`; create `capability/RuntimeCapabilityMode.kt`, `capability/EnhancedCapabilityBackends.kt`; add smoke tests.
**Produces:** STANDARD/SHIZUKU_ENHANCED/ROOT_ENHANCED resolver, foreground/SAF readiness and real bounded root probe.
- [ ] Add failing resolver tests.
- [ ] Implement typed capability model and resolver.
- [ ] Verify resolver never promotes from installation evidence alone.

### Task 3: WhatsApp adapter layer
**Files:** create `whatsapp/WhatsAppAdapter.kt`, `whatsapp/WhatsAppAdapterRegistry.kt`; modify detector/service integration; add adapter smoke tests.
**Produces:** official-personal, official-business and generic-discoverable adapters with owned package matching, filter labels, selector hints and structural policy flags.
- [ ] Add failing adapter selection tests.
- [ ] Implement interfaces/adapters/registry.
- [ ] Route detector/service through adapter metadata without breaking legacy packages.

### Task 4: Profile-aware instance persistence
**Files:** modify `data/Entities.kt`, `data/AppDatabase.kt`, `data/Daos.kt`, `whatsapp/WhatsAppInstanceDetector.kt`, inventory policies; add migration/instrumentation assertions.
**Produces:** profileIdentity, installationIdentity, adapterId and discoveryEvidence fields; DB migration.
- [ ] Add failing identity/migration expectations.
- [ ] Implement schema migration and metadata population.
- [ ] Verify current-profile compatibility and uniqueness semantics.

### Task 5: Group filters and selection controls
**Files:** modify DAO/repository/ViewModel/UI; create pure policy for filter matching; add tests.
**Produces:** All, Unread, Read, Active, New, Not Scanned, Completed, Failed, Pending; invert selection; select current filter.
- [ ] Add failing filter-policy tests.
- [ ] Implement canonical filter matching and DAO/repository operations.
- [ ] Add compact UI controls without nested-list regressions.

### Task 6: Queue canonical states and resume pending
**Files:** modify queue/session DAO/repository/service/ViewModel; add pure queue-state policy tests.
**Produces:** canonical persisted states and latest resumable session lookup with Resume Pending action.
- [ ] Add failing state/resume policy tests.
- [ ] Implement normalized persistence transitions and resume lookup.
- [ ] Verify stop preserves data and pause persists state.

### Task 7: Adaptive navigation probes
**Files:** create `automation/NavigationProbePolicy.kt`; modify service scheduling points; add smoke/integration tests.
**Produces:** bounded Fast/Balanced/Safe fallback probes; Accessibility events remain primary driver.
- [ ] Add failing probe schedule tests.
- [ ] Implement policy.
- [ ] Replace applicable fixed 500 ms navigation polling with policy values.
- [ ] Verify no multi-second fixed sleeps exist.

### Task 8: Adapter-aware fast sync and extraction evidence
**Files:** modify `WaAccessibilityService.kt`, `AccessibilityTree.kt` only where needed; extend integration smoke checks.
**Produces:** adapter-owned filter labels and selector/resource hints while retaining structural validation, end-of-list proof and batch persistence.
- [ ] Add failing source/integration invariants.
- [ ] Implement adapter routing at operation start and filter detection.
- [ ] Verify generic adapter does not depend on official resource IDs.

### Task 9: Documentation, versioning and release verification
**Files:** README/status/release notes/build metadata/tools scripts.
**Produces:** new release bundle, verification report, checksums and truthful limitations.
- [ ] Update static release invariants and version.
- [ ] Run full `tools/release-audit.sh` and fix regressions.
- [ ] Attempt Gradle `test`, `lint`, `assembleDebug`; record exact outputs.
- [ ] Package source ZIP and verification report.
