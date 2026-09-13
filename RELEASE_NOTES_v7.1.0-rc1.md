# WA Al-Othmany Link Bot v7.1.0-rc1 — Runtime Reliability Review

This release is a corrective reliability pass over v7.0.0-rc1 focused on the two required behaviors: synchronizing WhatsApp groups and extracting links.

## Corrected runtime defects

- WhatsApp filter-chip discovery now has a guarded cluster fallback for generic WhatsApp accessibility nodes. It accepts a decorated Groups/All label only when it is on a clickable path inside a local filter cluster with peer labels, reducing both false negatives and chat-title false positives.
- Sync opening can recover from nested WhatsApp screens by backing toward Chats at bounded miss counts instead of waiting indefinitely.
- Extraction navigation now detects an actual chat screen before considering Search. This prevents the automation from accidentally using the in-chat message search when WhatsApp reopens inside a conversation.
- Failed or timed-out Select-All verification now falls through to the conservative All-Chats strategy instead of marking unseen groups missing.
- UNREAD_ONLY extraction only queues selected groups currently marked unread and traverses toward older messages until the unread boundary is reached.
- Accessibility tree traversal now preserves natural child order. This improves title and message fallback extraction on WhatsApp builds where stable resource IDs are unavailable.
- Existing semantic-click -> parent click -> gesture fallback is retained, and screen/state verification remains the proof of navigation success.

## Preserved behavior

DEEP / NEW_ONLY / UNREAD_ONLY, group filters, selection controls, pause/resume/stop/skip, retry failed, TXT/ZIP import, XLSX/CSV/TXT/JSON export, overlay controls, foreground notification, durable checkpoints, diagnostics, adaptive timing, Personal WhatsApp and WhatsApp Business detection are preserved.

## Important boundary

Real cross-profile Dual Messenger / Work Profile / Secure Folder control and a real Shizuku Binder execution adapter are not claimed by this release. They are intentionally not allowed to masquerade as READY capabilities.
