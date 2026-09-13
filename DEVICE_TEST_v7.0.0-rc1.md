# v7 Real Device Acceptance Test

Do not test every feature at once.

## Gate 1 — Accessibility
Expected diagnostics:
`APP_START`
`ACCESSIBILITY_CONNECTED`

The UI must show both Accessibility enabled and service connected.

## Gate 2 — Synchronization
Select WhatsApp Business first and press Sync once.
Expected useful events include one of:
- `SYNC_GROUP_FILTER_CLICKED`
- `SYNC_NAV_DECISION ... FALLBACK_ALL_CHATS`
- `SYNC_STRATEGY_ACTIVE strategy=ALL_CHATS_CLASSIFY`

Acceptance:
- `SYNC_COMPLETE`
- group count > 0

A zero-group first sync must stop as an error, not report success.

## Gate 3 — Extraction
Select one known group that contains a visible URL, choose NEW or DEEP, press Extract once.
Expected:
- `EXTRACT_NAV_DECISION`
- group search/open succeeds
- `LINK_PERSISTED count=...`
- group completes

Only after these three gates pass should larger queues be tested.
