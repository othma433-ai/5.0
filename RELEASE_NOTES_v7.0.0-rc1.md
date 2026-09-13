# WA Al-Othmany Link Bot v7.0.0-rc1 — Verified Runtime

This release focuses only on the two core outcomes: synchronize real WhatsApp groups and extract links reliably.

## Runtime changes
- Robust semantic matching for WhatsApp controls with RTL/count/state decorations such as `المجموعات +99 دردشة` and `Groups, 12 unread`.
- Deterministic sync opening policy: Groups -> Chats anchor -> conservative All Chats fallback. No blind back/click loops.
- Extraction no longer depends on the Groups filter. If Groups is unavailable, it anchors Chats and uses verified Search.
- Active navigation probes for extraction instead of waiting only for Accessibility events.
- ACTION_CLICK / ACTION_LONG_CLICK now have a bounds-based gesture fallback; state verification remains required after actions.
- Notifications are recommended, not a hard blocker for the core runtime.
- A first sync that discovers zero groups is treated as a safety stop instead of false success.
- Explicit `LINK_PERSISTED` diagnostics after database commit.

## Preserved features
Multi-strategy sync, Select All verification, All Chats fallback, Deep/New/Unread extraction, pause/resume/stop/skip/retry, import TXT/ZIP, export XLSX/CSV/TXT/JSON, overlay, foreground service, adaptive timing, recovery/checkpoints, Personal and Business detection, Shizuku/root visibility.

## Verification performed in this environment
- Pure Kotlin smoke suite: 33 tests PASS.
- XML parse: PASS.
- Feature preservation: PASS.
- Accessibility manifest smoke: PASS.
- Gesture action regression: PASS.
- Static safety audit: PASS.
- Full local release audit: PASS.

Android Gradle compilation and real-device behavior must still be proven by GitHub Actions + device diagnostics before this RC becomes Production.
