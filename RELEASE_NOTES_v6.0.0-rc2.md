# WA Al-Othmany Link Bot v6.0.0-rc2 — Accessibility Binding Fix

## Critical correction
- `WaAccessibilityService` is now declared with `android:exported="true"` while still protected by `android.permission.BIND_ACCESSIBILITY_SERVICE`. This matches the Android accessibility-service declaration used by the current Android developer guide and allows the Android system to bind to the service.
- Added `tools/accessibility-manifest-smoke.py` to prevent this regression.
- Preserves the v6 rescue capability gating and the entire v5.2 automation feature set.

## Device gate
A real Android device still must confirm `ACCESSIBILITY_CONNECTED`, one successful group sync, and one known-link extraction before production status.
