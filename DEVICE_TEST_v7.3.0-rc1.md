# v7.3.0-rc1 Physical Device Runtime Certification

This checklist certifies runtime behavior on real Android hardware. A green CI build is **not** device certification.

## Test record

For every run record:

- Device / model
- Android version
- App version
- WhatsApp variant + package
- Android user/profile ID
- Shizuku version and mode (ADB/root/Sui), if used
- Root state, if used
- Diagnostic `traceId`
- Result: PASS / FAIL / PLATFORM_RESTRICTED
- Relevant diagnostic excerpt
- Tester date

## Gate 1 — Installation and upgrade

1. Fresh-install v7.3.0-rc1.
2. Upgrade from v7.2.0-rc1 without clearing app data.
3. Verify Room v2 -> v3 migration preserves groups, links, occurrences, queue/session history and instance selection.
4. Verify no crash during first launch after migration.

## Gate 2 — Accessibility lifecycle

1. Start with Accessibility disabled.
2. Enable WA Al-Othmany in Android Accessibility settings.
3. Confirm diagnostics progress through binding instead of remaining permanently at `a11yConnected=false`.
4. Confirm `ACCESSIBILITY_CONNECTED` / supervisor binder-connected state.
5. Bring WhatsApp to foreground and confirm first Accessibility event.
6. Confirm active-window root proof before automation starts.
7. Disable/re-enable the service and confirm recovery without reinstalling the app.

Acceptance: the app never reports UI automation ready solely from Settings enabled state.

## Gate 3 — Shizuku lifecycle

Run each state independently:

1. Shizuku not installed.
2. Shizuku installed but service/Binder not running.
3. Binder alive without app permission.
4. Grant app permission from the app action.
5. Confirm `SHIZUKU_READY` only after Binder + permission + privileged probe.
6. Revoke permission and confirm readiness drops.
7. Stop/restart Shizuku and confirm Binder death/reconnect is reflected.
8. Kill Shizuku during a recoverable operation and confirm the operation replans/falls back without corrupting session state.

Required diagnostic evidence includes `SHIZUKU_BINDER_RECEIVED`, `SHIZUKU_PERMISSION_RESULT`, `SHIZUKU_READY`, or the corresponding failure state.

## Gate 4 — Root fallback

Only run on a rooted test device.

1. Keep Root fallback disabled: no root operation should be selected.
2. Enable Root fallback.
3. Grant `su` access.
4. Confirm `id -u` probe returns UID 0 before Root is shown as READY.
5. Deny `su` and confirm Root is not reported READY.
6. Verify a Shizuku failure may fall back to Root only when the required capability is supported.

Acceptance: presence of an `su` file alone must never produce READY.

## Gate 5 — Adaptive engine selection

For each run, inspect `ENGINE_TRACE` records:

- `TRACE_START`
- `ENGINE_SELECTED`
- `ENGINE_EXECUTED`
- `FALLBACK_SELECTED` when applicable
- `TRACE_FINISH`

Verify each state-changing launch includes a postcondition result. A launch is not accepted solely because the privileged command returned exit code 0.

## Gate 6 — WhatsApp personal and Business

Test, when installed:

- `com.whatsapp`
- `com.whatsapp.w4b`

For each instance:

1. Select the exact instance.
2. Start group sync.
3. Verify the correct app is foregrounded.
4. Verify Accessibility window evidence belongs to the target package.
5. Complete at least one short sync.

## Gate 7 — Profile-aware routing

Where available, test separately:

- Personal profile
- Work Profile
- Dual Messenger / OEM clone
- Secure Folder / Knox

Acceptance:

- Same package name in different Android users appears as separate instances.
- Instance labels include profile/user identity.
- The app does not silently route to the wrong user.
- If Samsung Knox blocks cross-profile control, result is `PLATFORM_RESTRICTED`, not false success.

## Gate 8 — Group synchronization

Expected progression includes:

- `SYNC_START`
- system launch trace
- target foreground verification
- Accessibility window readiness
- `SYNC_NAV_DECISION`
- `GROUP_FILTER_VERIFIED` or safe strategy switch
- group persistence
- `SYNC_COMPLETE`

Acceptance: real group titles are captured without runaway duplicates or indefinite scrolling.

## Gate 9 — Extraction modes

Test at least one synchronized group containing a known HTTPS link with:

- NEW_ONLY
- UNREAD_ONLY
- DEEP

Expected evidence includes:

- `EXTRACTION_START`
- `EXTRACT_NAV_DECISION`
- verified chat evidence
- `LINK_PERSISTED`
- `GROUP_COMPLETE`

Acceptance: known links appear exactly once in the unique-link store while occurrences remain traceable.

## Gate 10 — Controls and recovery

Verify:

- Pause
- Resume
- Skip
- Stop
- Retry Failed
- App process recreation
- Accessibility interruption
- Shizuku Binder death
- returning from another app

Acceptance: durable queue/session checkpoints survive recoverable interruptions and stale generations cannot commit over a newer run.

## Gate 11 — Performance and ANR resistance

Repeat a short workflow in FAST, BALANCED and SAFE.

Verify:

- no indefinite busy loop;
- no repeated full-tree traversal while idle;
- no visible ANR;
- Accessibility event coalescing does not lose required state transitions;
- diagnostic queue remains bounded;
- battery/CPU usage settles after Stop.

## Gate 12 — Export and diagnostics

1. Export CSV/TXT/JSON/XLSX as supported by the UI.
2. Export diagnostics immediately after a forced fallback.
3. Confirm `traceId`, engine, attempt, duration, failure and fallback are visible.
4. Confirm full message text remains absent unless the user explicitly enabled message-text storage.

## Certification states

- `SOURCE_VERIFIED`: source/static tests passed.
- `APK_BUILT`: GitHub Actions produced APK + SHA-256.
- `INSTALL_VERIFIED`: APK installed and launched.
- `RUNTIME_CORE_VERIFIED`: Accessibility + core WhatsApp workflow passed on a real device.
- `PROFILE_VERIFIED`: target profile/clone scenarios tested individually.
- `RELEASE_CANDIDATE`: required gates above passed with evidence.
