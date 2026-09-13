# WA Al-Othmany Link Bot MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Android MVP that can onboard permissions, detect supported WhatsApp instances, synchronize visible WhatsApp group rows through Accessibility, persist a group registry and extraction queue, extract/deduplicate/classify links, import exported chats, and provide pause/resume/stop controls.

**Architecture:** Single Android app module with focused feature packages. Android-bound automation uses an AccessibilityService and foreground runtime controller; pure parsing/normalization/import/export logic stays framework-independent for testability. Room persists groups, links, occurrences, sessions and queue state.

**Tech Stack:** Kotlin, Android SDK, Jetpack Compose Material 3, Room, Coroutines/Flow, AccessibilityService, Foreground Service, Storage Access Framework.

**Spec:** `docs/superpowers/specs/2026-09-13-wa-al-othmany-link-bot-design.md`

## Global Constraints
- Core functionality does not require root.
- Shizuku and root are optional capability enhancements only.
- No destructive operation modifies WhatsApp chats or groups.
- Group synchronization never opens every chat.
- Navigation is event-driven; fixed multi-second sleeps are not used in normal operation.
- Full message text storage is off by default.

---

### Task 1: Android foundation and compact dashboard
**Files:** Gradle configuration, manifest, app theme, `MainActivity`, `BotApplication`, dashboard/onboarding UI.
**Produces:** Installable application shell and capability actions.

### Task 2: Link intelligence engine
**Files:** `LinkExtractor`, `UrlNormalizer`, `LinkClassifier`, pure JVM tests.
**Produces:** `LinkCandidate`, canonical URLs and categories.

### Task 3: Persistence model
**Files:** Room entities/DAOs/database/repositories.
**Produces:** group registry, link/occurrence store, queue/session persistence.

### Task 4: WhatsApp instance and capability discovery
**Files:** instance detector, accessibility/overlay/root/Shizuku capability checks.
**Produces:** selectable supported WhatsApp instances and runtime readiness.

### Task 5: Accessibility group synchronization
**Files:** accessibility service, UI tree helpers, `GroupSyncEngine`.
**Produces:** Groups-filter activation, row snapshot scanning, smart scroll, registry synchronization.

### Task 6: Selection and extraction queue
**Files:** groups screen/viewmodel and queue repository.
**Produces:** all/read/unread selection and persistent extraction jobs.

### Task 7: Autonomous extraction runtime
**Files:** runtime state machine, foreground service, overlay controller, extraction coordinator.
**Produces:** deep/new/unread modes plus pause/resume/stop/skip/retry controls.

### Task 8: Export-chat importer and result exporters
**Files:** TXT/ZIP parser, CSV/TXT/JSON/XLSX exporters, file picker integration.
**Produces:** import into unified link database and local exports.

### Task 9: Verification and handoff
**Files:** JVM smoke tests, static source checks, README.
**Produces:** verified source bundle and explicit environment/build limitations.
