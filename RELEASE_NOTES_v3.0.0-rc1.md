# WA Al-Othmany Link Bot v3.0.0-rc1 — Advanced Stability Release

This release candidate is a production-hardening pass focused on correctness before speed.

## Major upgrades

- Adaptive UI timing learns actual WhatsApp render latency instead of relying on fixed navigation sleeps.
- Per-stage circuit breaker retries transient UI failures and stops repeated unsafe loops.
- Group-filter verification remains a hard safety gate before registry synchronization.
- Sync coverage guard prevents a selector/UI regression from marking a large existing registry as missing.
- Duplicate-title search results are never selected arbitrarily; preview evidence must resolve them uniquely.
- Group identity matching refuses strong conflicting evidence rather than silently rebinding a record.
- RecyclerView adjacent-viewport identity reuse preserves duplicate-title rows one-to-one.
- URL extraction also reads Android URLSpan targets without clicking/opening links.
- URL normalization removes common tracking parameters while preserving functional query parameters and MEGA fragments.
- Balanced-parenthesis URLs are preserved while unmatched chat punctuation is trimmed.
- Export Chat parser recognizes sender-less WhatsApp system events as independent message boundaries.
- `QUERY_ALL_PACKAGES` was removed; discovery uses explicit packages and launcher-visible candidates.
- Overlay is now correctly treated as optional; core readiness requires Accessibility, notifications, a detected WhatsApp instance, and a connected service.
- Compact UI exposes engine readiness and blocks automation start when hard prerequisites are missing.
- Broad backup remains disabled and diagnostic export remains sanitized.

## Verification in this environment

`tools/release-audit.sh` runs all pure-Kotlin smoke tests, XML parsing, hardening invariants, privacy checks, version checks, and static automation safety checks.

An Android SDK/Gradle installation is not available in the current execution container, so the Android APK build and physical WhatsApp interaction remain release gates to run in Android Studio/GitHub Actions and then on the target device.
