# WA Al-Othmany Link Bot — Architecture Design

Date: 2026-09-13
Status: Design freeze candidate
Scope: Standalone Android application

## 1. Product Goal
Build a standalone Android bot that can, from first installation after required Android permissions are granted, discover supported WhatsApp instances, synchronize group chats rapidly, let the user filter/select groups, extract links from selected chats using autonomous UI automation, import exported WhatsApp chats, deduplicate/classify links, persist progress, recover after interruption, and export results.

## 2. Supported WhatsApp Environments
The application is designed to support:
- Official WhatsApp
- WhatsApp Business
- Samsung/Android Dual Messenger clones when exposed as separate packages/users
- Work Profile instances
- Secure Folder instances where Android permits accessibility visibility/control
- Modified WhatsApp variants when they expose compatible Android accessibility semantics

Support is adapter-based rather than hard-coded into one package path.

## 3. Runtime Capability Tiers
Core functionality must not require root.

### Tier 1 — Standard Android
- AccessibilityService
- Foreground Service
- Overlay controller
- Storage Access Framework
- Notification actions

### Tier 2 — Shizuku Enhanced (optional)
Used only where it materially improves launching, package/user discovery, process/state inspection, or navigation reliability. No core feature may depend on Shizuku.

### Tier 3 — Root Enhanced (optional)
Used only as an acceleration/reliability adapter. Root is never required for the core extraction workflow.

The runtime chooses the highest safe available tier automatically, with user override.

## 4. Core Workflow
1. Select WhatsApp instance.
2. Open WhatsApp and activate Groups filter when available.
3. Run Fast Group Sync without opening chats.
4. Build/update Group Registry.
5. User filters/selects target groups.
6. Build persistent Extraction Queue.
7. Run selected extraction mode.
8. Save links + occurrences + checkpoints continuously.
9. Resume/recover on interruption.
10. Export manually or automatically.

## 5. Fast Group Sync
### Primary strategy
Groups Filter + direct RecyclerView/accessibility-tree scanning.

### Secondary strategy
Selection Mode / Select All / selection counter evidence when direct row semantics are ambiguous.

### Fallback strategy
Structural row fingerprinting based on hierarchy, avatar/title/preview/time/unread structure, package, and screen context.

### Performance rules
- Never open every chat during sync.
- Process all visible rows in one snapshot.
- Use ACTION_SCROLL_FORWARD where possible.
- Avoid fixed delays; advance on verified accessibility events/state transitions.
- Deduplicate in memory during the run and batch-persist to Room.
- End-of-list proof requires repeated no-new-items + stable bottom fingerprint + no scroll progress.

## 6. Group Registry
Each synchronized group receives an internal stable app ID and session evidence.

Suggested fields:
- internalGroupId
- whatsappInstanceId
- normalizedTitle
- displayTitle
- unread flag/count when available
- last-message preview evidence
- last-activity evidence when available
- row fingerprint/evidence
- firstSeenAt
- lastSeenAt
- lastSuccessfulSyncId
- extraction state
- last checkpoint

The registry must not treat WhatsApp resource IDs as permanent group identifiers.

## 7. Group Filtering and Selection
Supported filters:
- All
- Read
- Unread
- Active (configurable time window when evidence exists)
- New since last sync
- Never scanned
- Completed
- Partial
- Failed
- Missing this sync

Selection actions:
- Select all current results
- Clear selection
- Invert selection
- Add/remove individual groups
- Clear extraction queue

## 8. Persistent Extraction Queue
Before extraction starts, selected groups are copied into a persistent Room-backed queue.

Queue states:
- WAITING
- LOCATING
- OPENING
- VERIFYING
- SCANNING
- PAUSED
- COMPLETED
- PARTIAL
- FAILED
- SKIPPED

WhatsApp list reordering must not mutate the queue.

## 9. Extraction Modes
### A. Deep Scan
Read current viewport, extract links, then move toward older messages while extracting continuously. Never scroll to the top first and then re-scan downward.

### B. Unread Only
Locate the unread boundary when WhatsApp exposes it, extract from the unread region toward the newest message, then stop.

### C. New Since Last Scan
Use the group checkpoint from the previous successful run. Extract only newly encountered content and stop when the saved checkpoint is reached.

## 10. Navigation Engine
Navigation is event-driven, not sleep-driven.

State machine:
- IDLE
- PREPARING_INSTANCE
- OPENING_GROUP_LIST
- LOCATING_TARGET
- OPENING_CHAT
- VERIFYING_CHAT
- READING_VIEWPORT
- SCROLLING_HISTORY
- VERIFYING_PROGRESS
- RETURNING_TO_LIST
- LOCATING_NEXT
- PAUSED
- RECOVERY
- STOPPED

Every transition must be confirmed by package/screen/tree evidence before the next action.

## 11. Link Extraction Engine
Default mode: maximum extraction without opening links.

Sources, in priority order:
1. Visible message text
2. Accessibility text/contentDescription from message subnodes
3. Clickable link nodes/previews when their URI or full text is exposed
4. Safe structural fallback for split/wrapped URLs

The engine must not open every link in a browser. If a URI cannot be recovered safely from accessibility data, record the occurrence as unresolved instead of performing expensive navigation by default.

### URL pipeline
Raw candidate -> repair/wrap join -> normalize -> validate -> canonicalize -> classify -> hash -> deduplicate -> persist occurrence.

### Classification
- WhatsApp group invite
- WhatsApp channel/community-related URL
- Telegram
- Google Drive
- OneDrive
- Dropbox
- MEGA
- YouTube
- TikTok
- Instagram
- Facebook
- X/Twitter
- Zoom/Meet/meeting services
- Document/PDF/file URLs
- General website
- Other

## 12. Duplicate Model
Use one canonical Link record plus multiple Occurrence records.

Link:
- canonicalUrl
- normalizedHash
- category
- firstSeenAt
- lastSeenAt
- occurrenceCount

Occurrence:
- linkId
- groupId/importSourceId
- sender when available
- timestamp when available
- message fingerprint
- optional full message text
- extraction session id

Exports can output Unique Links or All Occurrences.

## 13. Message Text Storage
Default: do not save full message text.
Store only URL + group + timestamp/sender when available + source metadata.

Optional setting:
- Save full source message text for each occurrence.

## 14. Export Chat Importer
Supported inputs:
- WhatsApp exported TXT
- ZIP containing exported chat text (and optional media ignored unless later required)

Importer pipeline:
- detect locale/date format
- parse multiline messages
- recover sender/time when available
- extract/classify/normalize URLs
- merge into the same Link/Occurrence database used by autonomous scanning

## 15. Export System
Formats:
- XLSX
- CSV
- TXT
- JSON

Modes:
- Unique Links
- All Occurrences
- One combined file
- Per-group files
- Manual export
- Automatic export after successful session

## 16. Runtime Controls
Two controllers are provided simultaneously:

### Floating overlay
- Pause
- Resume
- Skip current group
- Stop
- compact progress

### Persistent notification
- Pause/Resume
- Stop
- Skip
- Return to app

## 17. Recovery and Checkpointing
Persist after each meaningful state transition and viewport batch:
- current run
- queue position
- group state
- last verified screen
- message/viewport checkpoint
- links already committed

On process death/reboot, the app offers Resume and restores the persistent queue without duplicating committed links.

## 18. Performance Architecture
- Kotlin + coroutines
- Room database with WAL and batched writes
- In-memory HashSet/LRU caches for fingerprints and canonical URLs during active runs
- Snapshot-level parsing rather than per-node waits
- Event-driven navigation
- No fixed multi-second sleeps in normal operation
- RecyclerView actions before coordinate gestures
- Gesture fallback only when semantic actions fail
- Minimal Compose recomposition; state flows scoped by screen
- Foreground service for long-running extraction

Instrumentation metrics:
- sync snapshot parse time
- scroll cycle time
- chat locate/open time
- viewport parse time
- URL normalization time
- DB batch write time
- recovery count
- per-group total time

## 19. Safety Boundaries
- No deletion or modification of WhatsApp chats/groups.
- Local "clear" actions affect only the bot database/queue/results.
- Destructive local operations require confirmation.
- The app does not bypass Android app sandbox protections as a core dependency.

## 20. First-Install Experience
1. Welcome / capability check.
2. Enable Accessibility.
3. Grant overlay permission.
4. Allow notifications/foreground operation.
5. Optional Shizuku detection/connect.
6. Optional Root detection (off unless explicitly enabled).
7. Detect supported WhatsApp instances.
8. Run a small self-test against the selected instance.
9. User can immediately start Group Sync.

No fake demo data should be shown as successful real extraction.

## 21. Quality Gates
Before calling v1 production-ready:
- Build succeeds from clean checkout.
- Unit tests pass for URL parser, normalization, dedupe, export parser, state reducers.
- Instrumented tests pass for persistence/recovery flows where feasible.
- Real-device validation on at least official WhatsApp and WhatsApp Business.
- Group sync proves end-of-list and does not register toolbar/system entries as groups.
- Pause/resume/stop survive app backgrounding/process death scenarios tested.
- Exported files reopen correctly and match database counts.

## 22. Recommended Implementation Order
1. Android project foundation + capability onboarding.
2. WhatsApp instance adapters + package/user discovery.
3. Accessibility snapshot/debug inspector.
4. Fast Group Sync + registry.
5. Group filtering/selection + persistent queue.
6. Navigation state machine.
7. Link extraction/normalization/dedupe.
8. Deep/New/Unread extraction modes.
9. Pause/resume/stop/recovery.
10. Export Chat Importer.
11. XLSX/CSV/TXT/JSON export.
12. Shizuku enhancements.
13. Root enhancement adapter.
14. Performance hardening and real-device regression.
