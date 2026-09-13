# WA Al-Othmany Link Bot v5.0.0-rc1 — Durable Execution

## Release focus

v5 is a reliability release aimed at the two most expensive failure classes in large autonomous runs: advancing the WhatsApp UI before extracted data is durably stored, and blindly retrying groups that failed because their identity could not be proven safely.

## New in v5

### Durable extraction pipeline

- Adds `DurableViewportPolicy` and a bounded `viewportCommitInFlight` barrier.
- A viewport with newly discovered links is written to Room before any scroll action is allowed.
- If persistence fails, the group is failed with `PERSISTENCE_ERROR`; the viewport is not advanced and the stored checkpoint is not moved past unsaved content.
- After commit, the bot reacquires `rootInActiveWindow`, re-verifies the expected chat, reacquires the message container, then scrolls. Stale Accessibility nodes are not reused across the database suspension point.

### Smart queue priority

- Adds `SmartQueuePolicy`.
- UNREAD_ONLY prioritizes unread count/activity/recency.
- NEW_ONLY prioritizes unread/new work before older completed work.
- DEEP prioritizes never-scanned work while retaining every explicitly selected group.
- Ordering is deterministic for stable resumes and diagnostics.

### Failure-aware recovery

- Adds `FailureRecoveryPolicy`.
- Transient failures can be retried within a bounded attempt budget.
- `AMBIGUOUS_GROUP`, `GROUP_MISSING`, `GROUP_IDENTITY_MISMATCH`, and `UNSUPPORTED_UI` are protected for manual review instead of blindly entering the same unsafe path again.
- Retry Failed now reports safe retry candidates separately from protected failures.

### Accurate recovery/progress

- Adds `QueueProgressPolicy`.
- Service/process reconnect restores completed queue progress instead of displaying zero.
- Final sessions with residual failures are recorded as `COMPLETED_WITH_ERRORS`.
- Completion UI reports failed count instead of claiming an all-clear run.

### Lifecycle hardening

- Accessibility coroutine scope is cancelled when the service is destroyed.
- In-flight viewport state is reset when moving to a new group or clearing the operation.
- Queue-position progress is used while locating groups so failures/skips do not make the progress indicator appear stuck.

## Compatibility

- No Room schema change from v4; database version remains 2.
- Core execution still requires neither Root nor Shizuku.
- Physical device/WhatsApp certification remains required before production labeling.
