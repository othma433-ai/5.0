# WA Al-Othmany Link Bot v5.2.0-rc1 — Project Status

## Implemented
- Multi-strategy synchronization: Groups filter scroll -> Select-all verification -> conservative All-chats fallback.
- Semantic `+99`/Arabic-Indic badge-aware control matching.
- Active Accessibility opening probes.
- Duplicate sync-start suppression.
- Safe row classification and preservation rules for fallback sync.
- Durable v5 extraction/recovery architecture retained.

## Local verification available in this package
- ControlLabelPolicy smoke: PASS
- SyncStrategyPolicy smoke: PASS
- ConservativeGroupRowPolicy smoke: PASS
- RowClassificationPolicy smoke: PASS
- `git diff --check`: PASS

## Required before Production label
1. Build with GitHub Actions.
2. Install v5.2 APK on the target Android device.
3. Test both WhatsApp and WhatsApp Business profiles.
4. Capture one successful Group-filter sync log and, when possible, one Select-all verification log.
5. Confirm no personal chats are admitted by the conservative fallback.

`v5.2.0-rc1` remains a release candidate until those physical-device gates pass.
