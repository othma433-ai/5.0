# WA Al-Othmany Link Bot v6.0.0-rc1 — Corrective Rebuild

This rescue release restores a trustworthy Standard Android Accessibility execution path while preserving the existing v5.2 feature set.

## First device test

1. Install the APK built by `.github/workflows/build-android-apk.yml`.
2. Open Android Settings → Accessibility → Installed apps/services.
3. Enable **WA Al-Othmany Link Bot**.
4. Return to the app.
5. The dashboard must show both:
   - `الوصول مفعّل في النظام` = ready
   - `خدمة الوصول متصلة فعليًا` = ready
6. Select WhatsApp Personal or Business.
7. Run **مزامنة** once.
8. Export Diagnostics and verify the log contains `ACCESSIBILITY_CONNECTED` before `SYNC_START`.

If Accessibility is enabled but not connected, the app intentionally blocks Sync/Extraction instead of pretending to run.

## Capability truthfulness

- Overlay: optional.
- Shizuku: presence is detected, but v6 rescue does not claim a privileged adapter until a real binder/permission/execution probe exists.
- Root: binary presence is detected, but privileged execution is not automatically enabled.
- WhatsApp Personal / Business / launcher-visible variants are detected in the current Android profile.
- Secure Folder / cross-user Work Profile / Dual Messenger require a separately tested privileged adapter and are not falsely claimed by this release.

## Preserved functions

Multi-strategy group sync, group selection filters, NEW_ONLY/UNREAD_ONLY/DEEP extraction, pause/resume/stop/skip, retry failed, TXT/ZIP import, XLSX/CSV/TXT/JSON export, overlay controller, foreground notification service, adaptive timing, diagnostics, and checkpoints/recovery remain in source and are enforced by `tools/feature-preservation-smoke.py`.
