# WA Al-Othmany v7.1 Master-Prompt Alignment Design

## Goal
Evolve the existing v7.1.0-rc1 HARDENING codebase in place into a production-oriented Android automation architecture that matches the approved Concept Design as closely as Android platform constraints permit, without regressing v7.1 hardening.

## Non-negotiable constraints
- Preserve the package `com.waalothmany.linkbot`.
- Kotlin + Jetpack Compose + Material 3 remain the UI stack.
- Standard operation must require neither root nor Shizuku.
- Do not read WhatsApp private databases or bypass Android sandbox in standard mode.
- Do not claim support for an Android profile/clone/variant that cannot be discovered and inspected from the current app context.
- Conversation content and extracted links remain local.
- Preserve v7.1 event coalescing, async diagnostics, persistence accounting, recovery policies, and Room instrumentation coverage.
- Replace fixed navigation sleeps with event-driven/adaptive bounded probes wherever the engine currently depends on fixed polling.
- Every operation must have exclusive runtime ownership and stale callbacks must be rejected.

## Architecture
### Runtime
Expand the runtime phase model to include INITIALIZING, CHECKING_CAPABILITIES and SYNCING_GROUPS while preserving compatibility helpers for old state names during migration. Add a runtime-mode resolver for STANDARD, SHIZUKU_ENHANCED and ROOT_ENHANCED. Enhanced modes are selected only when an actual backend reports it is usable; installation detection alone is insufficient.

### WhatsApp adapter layer
Introduce `WhatsAppAdapter` as the generic contract for package ownership, selectors, structural rules and screen-state evidence. Provide Official Personal, Official Business and Generic Discoverable adapters. A registry chooses an adapter per discovered instance. Generic variant support is structural/evidence-based and must not assume official resource IDs.

### Instance identity
Extend persisted WhatsApp instances with profile and installation identity, adapter id and capability evidence. Default/current-profile records remain backward compatible. Cross-profile records can only be created when Android exposes them to the app; unsupported profiles are reported, not fabricated.

### Capabilities
Replace string-only capability mode with a typed `RuntimeCapabilityMode`. Add explicit foreground-service and SAF readiness. Root capability uses a bounded real `su -c id` probe. Shizuku remains optional and is represented by a backend abstraction; without a linked usable backend the resolver remains STANDARD. This preserves truthfulness while providing the required extension point.

### Synchronization
Retain v7.1 fast visible-row scanning, event coalescing and structural validation. Add adapter-owned filter labels/selector hints and a bounded navigation probe policy. Keep batch persistence and multi-signal end-of-list proof. Never open every group during synchronization.

### Group registry and queue
Add group states/filters needed by the prompt: NEW and PENDING, plus invert selection and select-current-filter operations. Keep the persistent queue and make pending-session discovery explicit for resume.

### Extraction
Retain Deep, Unread and New Only modes, viewport fingerprints, immediate extraction while scrolling, checkpoints and occurrence storage. Ensure queue transitions use canonical states WAITING, RUNNING, PAUSED, COMPLETED, PARTIAL, FAILED and SKIPPED at the persistence boundary while allowing internal navigation sub-stages in memory.

### Recovery and controls
Keep v7.1 recovery policies and add operation-generation ownership to prevent stale coroutine callbacks from mutating a newer operation. Pause persists session/queue state before halting. Resume Pending locates the latest resumable extraction session. Stop never deletes extracted results.

### Verification
Extend pure-Kotlin smoke coverage for adapters, capability-mode resolution, runtime phases, group filtering/selection policies, pending-session/queue state policy and navigation probes. Preserve all existing v7.1 smoke/integration audits. Run `tools/release-audit.sh`; then attempt Gradle `test`, `lint`, and `assembleDebug` if a usable wrapper/Gradle + Android SDK are available. Report environment failures distinctly from source failures.
