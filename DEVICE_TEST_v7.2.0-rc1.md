# v7.2.0-rc1 Physical Device Acceptance

Use one known WhatsApp or WhatsApp Business instance first. Export diagnostics immediately after the first failure; do not repeatedly retry before collecting evidence.

## Gate 1 — Runtime readiness

Expected diagnostic evidence:

- `APP_START`
- `ACCESSIBILITY_CONNECTED`
- selected package is launchable

Reject the run if Accessibility is only enabled in Settings but not connected at runtime.

## Gate 2 — Conflicting-start protection

Start Sync, then immediately attempt Extract/Retry while Sync is active.

Acceptance:

- only one workflow continues;
- diagnostic log contains the corresponding `*_START_IGNORED_BUSY` event for the rejected request;
- active workflow does not reset or become ERROR because of the rejected request.

## Gate 3 — Group synchronization

Expected progression includes:

- `SYNC_START`
- `SYNC_NAV_DECISION`
- `GROUP_FILTER_VERIFIED` or a safe `SYNC_STRATEGY_SWITCH`
- `SYNC_COMPLETE`

Acceptance: real group titles are present and the engine does not stall on nested WhatsApp screens.

## Gate 4 — Extraction

Select a synchronized group containing a known visible HTTPS link and run DEEP.

Expected progression includes:

- `EXTRACTION_START`
- `EXTRACT_NAV_DECISION`
- verified chat evidence
- `LINK_PERSISTED`
- `GROUP_COMPLETE`

Acceptance: the known link is persisted and appears in export output without duplicate runaway processing.

## Gate 5 — Recovery and controls

Verify Pause/Resume, Skip, Stop, process interruption/reopen, and Retry Failed. Confirm progress/checkpoints are preserved and no second operation can start over an active one.

## Gate 6 — Performance modes

Repeat a short Sync + Extract pass in FAST, BALANCED, and SAFE. Confirm none spins indefinitely and SAFE remains more tolerant of slow UI transitions.
