# WA Al-Othmany Link Bot v4.0.0-rc1 — Adaptive Intelligence Release Candidate

v4 is a correctness-and-performance upgrade over v3. The engine now uses semantic UI evidence and runtime health feedback instead of treating every successful Accessibility action as equally trustworthy.

## Major v4 upgrades

### Semantic screen guard
- New `ScreenEvidencePolicy` classifies the current WhatsApp screen as Group List, Search, Chat, or Unknown from multiple independent signals.
- Dangerous actions are gated by evidence confidence rather than a single label/resource id.
- Search-result opening and chat verification refuse contradictory screen states.

### Intelligent scroll-container selection
- New `ContainerRolePolicy` scores scrollable containers as conversation lists or message lists.
- Group sync no longer blindly uses the largest scrollable node when WhatsApp exposes multiple nested RecyclerViews.
- Chat extraction independently selects the most message-like scrollable container.

### Adaptive stability governor
- New `AutomationHealthPolicy` maintains a bounded 0–100 runtime health score.
- Sustained success allows faster operation; ambiguity/timeouts automatically downgrade execution toward SAFE behavior.
- User FAST/BALANCED/SAFE remains a maximum-speed preference; runtime health is allowed to slow the engine but never silently exceed the user's safety level.
- Mode changes and health events are recorded in sanitized diagnostics.

### Smarter message-window processing
- New unread-marker detection for English and Arabic WhatsApp separators.
- `UNREAD_ONLY` preferentially processes messages after the visible unread boundary.
- New overlap-aware fingerprint cache prevents repeated URL parsing when RecyclerView viewports intentionally overlap during scroll.
- Seen-message memory is bounded to avoid unbounded RAM growth during very deep scans.

### Runtime performance telemetry
- New `ThroughputMeter` reports groups/minute and links/minute.
- Compact UI and foreground notification show health score, effective mode, and live throughput.
- Session completion diagnostics include only sanitized operational metrics, never full message content.

### CI and verification
- GitHub Actions now runs the full release audit, JVM/Android unit tests, Android lint, and debug APK build.
- New pure-Kotlin and JUnit tests cover semantic screen classification, health adaptation, message overlap/unread behavior, scroll-container role scoring, and throughput calculation.

## Preserved v3 hardening

- No `QUERY_ALL_PACKAGES` permission.
- Android backup disabled.
- No arbitrary screen-coordinate tapping.
- No fixed multi-second navigation delays.
- Duplicate-title search results are never selected arbitrarily.
- Sync coverage safety guard preserves an established registry when discovery collapses unexpectedly.
- Adaptive latency timing, stage circuit breaker, persistent extraction queues/checkpoints, sanitized diagnostics, TXT/ZIP Export Chat import, and XLSX/CSV/TXT/JSON export remain enabled.

## Release status

The pure-Kotlin verification suite and static release audit can be run locally with:

```bash
bash tools/release-audit.sh
```

`v4.0.0-rc1` remains a release candidate until the Android project is compiled and the exact target WhatsApp/device/profile combinations pass physical-device sync/extraction gates.
