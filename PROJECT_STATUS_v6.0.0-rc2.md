# Project Status — v6.0.0-rc2 Accessibility Binding Fix

## Current status
Source-level verification passes. The critical AccessibilityService manifest declaration now matches the current Android developer guidance: the service is `android:exported="true"` and remains protected by `android.permission.BIND_ACCESSIBILITY_SERVICE`.

## Verified in this source package
- 31 pure-Kotlin core smoke tests PASS.
- Throughput runtime smoke PASS.
- Android XML parsing PASS.
- Feature-preservation audit PASS; no v5.2 app-source files were deleted in the v6 rescue rebuild.
- Accessibility manifest binding regression test PASS.
- Production hardening/static safety audit PASS.
- Previous Android compile blockers are absent in source: `normalizePreview()` exists and the invalid direct Compose `weight` import is absent.

## Not yet certified
This environment does not contain an Android SDK/Gradle toolchain and cannot access the network to install one, so `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` were not executed here.

A physical-device production claim still requires:
1. GitHub Actions build succeeds.
2. APK installs.
3. Accessibility is enabled and `ACCESSIBILITY_CONNECTED` appears.
4. WhatsApp Business group sync succeeds at least once.
5. One known-group extraction persists a real link.

## Privileged adapters
Shizuku/root presence is detected only. Cross-profile privileged execution (Secure Folder / Work Profile / some Dual Messenger cases) is not implemented in rc2 and must not be described as active.
