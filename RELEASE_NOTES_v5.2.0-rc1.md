# WA Al-Othmany Link Bot v5.2.0-rc1 — Multi-Strategy Synchronization

## Purpose
This release hardens real-device WhatsApp group synchronization after runtime logs showed repeated `SYNC_OPENING` timeouts on WhatsApp Business when the Groups chip was rendered with counters such as `المجموعات +99` or was temporarily not exposed.

## New synchronization architecture
The synchronizer now uses ordered strategies rather than repeatedly restarting one failing path:

1. **GROUP_FILTER_SCROLL** — locate the semantic Groups chip and scan the filtered RecyclerView.
2. **GROUP_FILTER_SELECT_ALL** — after the direct pass, enter WhatsApp selection mode, invoke **Select all** when exposed, and run a secondary verification scan.
3. **ALL_CHATS_CLASSIFY** — if WhatsApp does not expose the Groups filter, anchor on Chats/All and conservatively register only rows with strong group evidence. This path never marks unseen existing groups missing.

## Real-UI compatibility improvements
- Semantic filter-label normalization handles counts on either side and RTL/LTR variants, including `المجموعات +99`, `+99 المجموعات`, `Groups +99`, `99+ Groups`, and Arabic-Indic digits.
- Active opening probes inspect fresh `rootInActiveWindow` snapshots instead of depending only on Accessibility events.
- Repeated Sync button presses while a sync is already running are ignored instead of resetting adaptive state.
- Selection mode is exited safely when synchronization completes.
- `مؤرشفة`, Tools/`الأدوات`, Meta AI and other non-chat controls are excluded from conversation-row classification.

## Safety
- All-chats fallback is conservative and favors false negatives over registering a personal chat as a group.
- All-chats fallback preserves existing unseen registry entries (`markMissing=false`).
- Control-label lookup requires structural control evidence so a group title similar to `Groups` is not clicked as a navigation filter.
- Stage timeout now changes strategy instead of restarting `SYNC_OPENING` indefinitely.

## Diagnostics
New events include:
- `SYNC_STRATEGY_SWITCH`
- `SYNC_SECONDARY_STRATEGY_START`
- `SYNC_SELECTION_MODE_ENTERED`
- `SYNC_SELECT_ALL_APPLIED`
- `SYNC_SELECTION_UNAVAILABLE`
- `SYNC_ALL_CHATS_ANCHOR_CLICKED`
- `SYNC_ALL_FILTER_CLICKED`
- `SYNC_STRATEGY_ACTIVE`
- `SYNC_START_IGNORED_ALREADY_RUNNING`

## Verification
Pure Kotlin regression tests cover semantic filter labels, strategy transitions, and conservative group classification. Android build remains a GitHub Actions gate (`testDebugUnitTest`, `lintDebug`, `assembleDebug`) before installation.
