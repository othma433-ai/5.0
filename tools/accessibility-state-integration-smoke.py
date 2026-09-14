from pathlib import Path
monitor = Path('app/src/main/java/com/waalothmany/linkbot/capability/AccessibilityConnectionMonitor.kt').read_text()
manager = Path('app/src/main/java/com/waalothmany/linkbot/capability/CapabilityManager.kt').read_text()
service = Path('app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt').read_text()
ui = Path('app/src/main/java/com/waalothmany/linkbot/ui/AppUi.kt').read_text()
checks = {
    'monitor heartbeat': 'markHeartbeat' in monitor,
    'manager derived state': 'AccessibilityConnectionPolicy.deriveState' in manager,
    'manager exposes state': 'accessibilityState' in manager,
    'service heartbeat job': 'accessibilityHeartbeatJob' in service,
    'service loss suspension': 'suspendForAccessibilityLoss' in service,
    'ui state visibility': 'accessibilityState.name' in ui,
}
missing=[name for name,ok in checks.items() if not ok]
if missing:
    raise SystemExit('Accessibility state integration missing: ' + ', '.join(missing))
print('AccessibilityStateIntegrationSmoke: PASS')
