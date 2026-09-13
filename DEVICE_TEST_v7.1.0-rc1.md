# v7.1.0-rc1 Device Acceptance Test

Use one WhatsApp instance for the first acceptance run. WhatsApp Business is preferred if that is where your test groups are.

## Gate 1 — Accessibility
Enable WA Al-Othmany Link Bot in Android Accessibility settings, reopen the app, and confirm the diagnostic log contains:

ACCESSIBILITY_CONNECTED

Do not continue if only APP_START is present.

## Gate 2 — Synchronization
Press Sync once. Expected diagnostic progression includes:

SYNC_START
SYNC_NAV_DECISION
GROUP_FILTER_VERIFIED
or a safe SYNC_STRATEGY_SWITCH to ALL_CHATS_CLASSIFY
SYNC_COMPLETE

Acceptance: the Groups screen in the bot contains real WhatsApp group titles. Zero discovered groups on an account that has groups is a failure.

## Gate 3 — Link extraction
Select one synchronized group that contains a known visible https:// link. Choose DEEP and press Extract.

Expected progression includes:

EXTRACTION_START
EXTRACT_NAV_DECISION
EXTRACT_VERIFY_CHAT
LINK_PERSISTED count=1 (or greater)
GROUP_COMPLETE

Acceptance: the link appears in the bot Links screen and exports correctly.

## Failure evidence
If a gate fails, export the diagnostic log immediately without repeatedly pressing Sync/Extract. The first failing transition is the useful evidence.
