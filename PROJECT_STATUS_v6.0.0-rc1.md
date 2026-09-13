# Project Status — v6.0.0-rc1 Corrective Rebuild

## Status
Source-level rescue implementation complete for the standard Android Accessibility path.

## Verified in this source package
- Pure Kotlin policy tests for Accessibility operational state, readiness, operation-start gating, instance inventory reconciliation, and existing automation policies.
- Feature-preservation audit.
- Android XML parse.
- Static release/hardening audit.
- No `QUERY_ALL_PACKAGES` permission.
- UI version is dynamic.
- Accessibility enabled and connected are independent states.

## Device gate still required
A production claim requires an Android build plus device validation:
1. Install APK.
2. Enable Accessibility service.
3. Confirm `ACCESSIBILITY_CONNECTED` appears in diagnostics.
4. Select WhatsApp Business or Personal.
5. Run one group sync.
6. Confirm groups are persisted.
7. Run one known-group extraction and confirm a real link is persisted.

## Privileged adapters
Shizuku/root presence is reported honestly. Real cross-profile privileged execution is deferred until it has its own implementation and device test gate.
