# WA Al-Othmany Link Bot v6.0.0-rc1 — Corrective Rebuild

## Purpose
This release is a stabilization rescue build. It does not add speculative automation. It makes the existing feature set honest, gated, observable, and recoverable.

## Core fixes
- Separates Accessibility enabled-in-settings from Accessibility service actually connected.
- Adds process-level Accessibility connection monitoring and lifecycle diagnostics.
- Prevents Sync/Extraction from starting unless the selected WhatsApp instance is available and launchable, Accessibility is enabled and connected, notifications are ready, and no other operation is active.
- Reconciles WhatsApp instance inventory instead of treating stale entries as currently available.
- Preserves official Personal and Business detection and launcher-visible variants in the current Android profile.
- Shows Shizuku/root presence honestly without falsely advertising a privileged execution adapter.
- Uses `BuildConfig.VERSION_NAME` in the UI instead of a stale hard-coded version.
- Adds a feature-preservation release gate so future fixes cannot silently remove sync modes, extraction modes, controls, import/export, overlay, notifications, or diagnostics.

## Preserved features
- Multi-strategy synchronization: Groups filter scroll, Select All verification, All Chats conservative fallback.
- Group selection filters: all, unread, read, active, never scanned, failed.
- Extraction modes: NEW_ONLY, UNREAD_ONLY, DEEP.
- Pause, resume, stop, skip, retry failed.
- TXT/ZIP import.
- XLSX/CSV/TXT/JSON export.
- Overlay controller and foreground notification service.
- Adaptive timing, health policy, checkpoints, queue recovery, diagnostics.

## Important limitation
Shizuku and root are detected as capabilities but are not advertised as active privileged execution paths in this rescue release. Cross-user Secure Folder/Work Profile control requires a separately tested privileged adapter and is not faked by this build.
