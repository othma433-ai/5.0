# v6.0.0-rc2 Device Acceptance Test

1. Install the APK produced by GitHub Actions.
2. Open Android Settings > Accessibility > Installed apps/services.
3. Turn WA Al-Othmany Link Bot OFF, then ON once after installing rc2.
4. Return to the app and press "إعادة فحص حالة النظام ونسخ واتساب".
5. Required dashboard state:
   - الوصول مفعّل في النظام = ready
   - خدمة الوصول متصلة فعليًا = ready
   - الإشعارات = ready
6. Export Diagnostics. Required line before any sync:
   - `ACCESSIBILITY_CONNECTED`
7. Select WhatsApp Business.
8. Run Sync once and do not press Sync again until completion or failure.
9. Expected strategy progression is one of:
   - `GROUP_FILTER_SCROLL` -> `GROUP_FILTER_SELECT_ALL` -> `SYNC_COMPLETE`, or
   - fallback to `ALL_CHATS_CLASSIFY` -> `SYNC_COMPLETE`.
10. Confirm group count becomes > 0.
11. Select one known group containing a known URL and run NEW_ONLY or UNREAD_ONLY.
12. Confirm at least one link is persisted and exportable.

If step 6 never produces `ACCESSIBILITY_CONNECTED`, do not test sync; export diagnostics and treat it as an Android service-binding failure.
