# v7.1.0-rc1 Project Status

## Target
Reliable current-profile WhatsApp / WhatsApp Business group synchronization and link extraction through Android Accessibility.

## Verified in source package
- Pure Kotlin policy suite: 37/37 smoke tests pass.
- Android XML parsing passes.
- Feature-preservation audit passes.
- Accessibility manifest audit passes.
- Gesture-click regression audit passes.
- v7.1 runtime-integration audit passes.
- Static hardening audit passes.

## Runtime architecture
1. OperationStartGate requires a selected launchable WhatsApp instance and a connected AccessibilityService.
2. Sync launches the selected package and attempts Groups Filter Scroll.
3. If Select-All cannot be verified, it exits selection safely and moves to conservative All-Chats classification.
4. Extraction locates each selected group through a verified Chats/Search surface, verifies the resulting chat, scans message viewports, extracts URLs, and persists them before advancing.

## Not claimed until device verification
A successful source audit or Android compilation cannot prove WhatsApp's live accessibility hierarchy on a specific device/version. Device success requires the diagnostic sequence documented in DEVICE_TEST_v7.1.0-rc1.md.

## Explicitly not implemented as real execution paths
- Shizuku Binder execution
- Cross-user/profile automation for Secure Folder / Work Profile / Dual Messenger when they are not launcher-visible in the current profile
