# WA Al-Othmany Link Bot v6 Corrective Rebuild Design

## Goal
Restore a trustworthy, testable Android runtime on top of the v5.2 source without deleting existing user-facing capabilities. The v6 rescue release must prioritize a working standard Accessibility path first, then retain the advanced synchronization/extraction features behind readiness gates.

## Non-negotiable requirements

1. Do not report READY unless the accessibility service is actually connected.
2. Distinguish Accessibility enabled-in-settings from service-connected runtime state.
3. Preserve existing features: group sync, multi-strategy sync, group filters/selection, NEW_ONLY, UNREAD_ONLY, DEEP extraction, pause/resume/stop/skip, retry failed, import TXT/ZIP, export XLSX/CSV/TXT/JSON, overlay controller, notification control, adaptive timing, diagnostics, recovery/checkpoints.
4. Preserve detection of official WhatsApp Personal and Business and launcher-visible compatible variants.
5. Never claim Secure Folder, Work Profile, Dual Messenger, Shizuku, or Root execution is active unless runtime evidence proves it.
6. Keep overlay optional for core automation.
7. Make UI version text derive from BuildConfig.VERSION_NAME.
8. Start operations only through one central gate that verifies: selected instance exists, package is launchable, Accessibility setting is enabled, Accessibility service is connected, notifications are ready.
9. On accessibility disconnect/interruption, runtime must leave READY/RUNNING states immediately and show an actionable recovery state.
10. Diagnostics must record capability transitions and operation-start rejection reasons without storing message bodies.

## Architecture

### 1. AccessibilityConnectionMonitor
A process-level monitor owns the authoritative runtime connection state. `WaAccessibilityService` marks connected/disconnected/interrupted. `MainViewModel` observes it instead of reading a singleton only when the user manually refreshes.

### 2. Capability model
`CapabilitySnapshot` separates:
- `accessibilityEnabled`
- `accessibilityConnected`
- `notifications`
- `overlay`
- `shizukuPresence` as an honest presence state only
- `rootPresence` as an honest presence state only

No privileged execution mode is advertised unless a real adapter exists and passes a runtime probe.

### 3. OperationStartGate
All Sync/Extract starts go through a pure policy returning `Allowed` or `Blocked(reason)`. This prevents no-op buttons and makes errors deterministic.

### 4. WhatsApp instance inventory
Current profile official packages and launcher-visible variants remain supported. Instance records are reconciled instead of blindly overwritten. The UI labels current-profile detection honestly. Cross-user/profile execution is not claimed in v6 rescue unless later backed by a tested privileged adapter.

### 5. Runtime recovery
Service connect/disconnect events update `BotRuntime` and readiness immediately. When enabled-but-not-connected, the UI shows a reconnect action and disables sync/extraction.

### 6. Feature preservation guard
A pure test verifies that the released source still contains the promised automation modes, selection operations, import/export formats, control operations, and multi-strategy sync definitions.

## Success criteria

A v6 rescue build is acceptable only when:
- the source passes all pure Kotlin regression tests;
- accessibility state transitions are unit tested;
- operation gate behavior is unit tested;
- readiness cannot become true while service connection is false;
- UI does not hard-code an old version;
- existing feature inventory test passes;
- Android source no longer contains the known compile regressions (`normalizePreview` missing / invalid `weight` import);
- CI workflow can build `assembleDebug` on GitHub Actions.

## Deliberately deferred from the rescue gate

Real Shizuku binder integration and cross-user Secure Folder/Work Profile enumeration are separate privileged-runtime tasks. v6 preserves their place in the architecture and reports them honestly, but the rescue release does not block core WhatsApp Personal/Business operation on them.
