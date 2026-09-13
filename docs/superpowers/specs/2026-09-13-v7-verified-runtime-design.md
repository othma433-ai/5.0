# WA Al-Othmany Link Bot v7 Verified Runtime Design

## Goal
Make synchronization and link extraction work reliably on real WhatsApp / WhatsApp Business before adding any new feature.

## Core principle
Every automation transition must be evidence-driven. The bot must never report READY for a capability that is only installed or enabled; it must be runtime-connected and usable.

## Synchronization
Use three strategies in order:
1. Groups filter + RecyclerView scan.
2. Groups filter + selection/select-all verification.
3. Chats/All fallback + conservative group classification.

If the Groups chip is not exposed exactly, semantic control matching accepts decorated labels (counts, RTL, selected/state suffixes) and sibling filter-bar evidence. The engine establishes Chats as an anchor before falling back.

## Extraction
Extraction must not depend on the Groups chip. Preferred route is Groups filter -> Search. If Groups filter is unavailable after bounded probes, fall back to Chats -> Search. Search result selection remains conservative: exact normalized title and preview evidence; ambiguous matches fail closed.

## Runtime safety
- Accessibility enabled and connected are separate gates.
- Foreground package must match selected WhatsApp package before actions.
- Unknown screen => no blind back/click loops.
- Bounded retries and circuit breaker.
- Persist links before scrolling away.
- No loss of previously discovered groups during conservative fallback.

## Feature preservation
Keep import/export, filters, pause/resume/stop/skip/retry, overlay, diagnostics, recovery/checkpoints, adaptive timing, multi-strategy sync, Personal + Business detection.

## Acceptance
Device diagnostics must show:
APP_START -> ACCESSIBILITY_CONNECTED -> SYNC_START -> SYNC_COMPLETE (groups > 0)
then extraction of a selected known group must emit LINK_PERSISTED / completed session with link count > 0.
