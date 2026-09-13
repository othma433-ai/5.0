# WA Al-Othmany Link Bot v3 Production Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Raise the standalone Android bot to a production-hardened v3 release candidate with safer identity resolution, adaptive event timing, bounded recovery, stricter privacy, deterministic checkpoints, and stronger verification.

**Architecture:** Keep the current event-driven Accessibility state machine and Room persistence, but add pure-Kotlin safety policies around latency, retries, UI drift, and checkpoint semantics. Integrate those policies into the service without introducing fixed sleeps or screen coordinates.

**Tech Stack:** Kotlin, Android AccessibilityService, Jetpack Compose, Room, Coroutines, Gradle/AGP, JVM smoke verification.

**Spec:** `docs/superpowers/specs/2026-09-13-wa-al-othmany-link-bot-design.md`

## Global Constraints

- No fixed multi-second automation delays.
- Never open arbitrary duplicate-title search results.
- Group synchronization must not open each group.
- Existing records remain resumable across process death.
- No message content in diagnostics.
- Root and Shizuku remain optional, never required.
- Real WhatsApp certification requires an Android-device run.

---

### Task 1: Adaptive timing and retry safety
- [ ] Add failing JVM smoke tests for adaptive latency and circuit breaker behavior.
- [ ] Implement `AdaptiveTimingPolicy` and `StageCircuitBreaker`.
- [ ] Integrate dynamic probe timing and bounded stage recovery in Accessibility service.
- [ ] Run core verification.

### Task 2: Identity and checkpoint precision
- [ ] Add failing tests for duplicate-title ambiguity, volatile unread counts, and versioned checkpoints.
- [ ] Harden group identity matching and search resolution.
- [ ] Upgrade checkpoint codec while preserving v2 compatibility.
- [ ] Run core verification.

### Task 3: Privacy and package visibility hardening
- [ ] Remove broad `QUERY_ALL_PACKAGES` permission.
- [ ] Replace installed-app enumeration with visibility-safe official/query-intent discovery plus manual-compatible variants.
- [ ] Add self-test/readiness model and sanitized diagnostic export metadata.
- [ ] Run XML/source verification.

### Task 4: Release engineering
- [ ] Raise version to v3.0.0-rc1 and update docs/status.
- [ ] Fix source-level compile hazards found during review.
- [ ] Add Gradle wrapper/build bootstrap if environment permits.
- [ ] Run all available verification and package source handoff.
