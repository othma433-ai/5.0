from pathlib import Path
service = Path('app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt').read_text()
tree = Path('app/src/main/java/com/waalothmany/linkbot/automation/AccessibilityTree.kt').read_text()
required_service = [
    'SYNC_NAV_BACK_RECOVERY',
    'EXTRACT_NAV_BACK_RECOVERY',
    'findAdaptiveControl(',
    'ExtractionSelectionPolicy.eligible',
    'UnreadTraversalPolicy.next(unreadBoundaryReached)',
    'AutomationMode.UNREAD_ONLY -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD',
]
for token in required_service:
    if token not in service:
        raise SystemExit(f'missing service integration: {token}')
required_tree = ['fun findFilterControl(', 'ControlClusterPolicy.accept(', 'fun controlSelectionEvidence(']
for token in required_tree:
    if token not in tree:
        raise SystemExit(f'missing tree integration: {token}')

# A failed Select-All verification must not mark unseen groups missing.
unsafe_selection_completions = [
    'completeSync(markMissing = true, verification = "direct-scroll; selection-mode unavailable")',
    'completeSync(markMissing = true, verification = "direct-scroll; select-all unavailable")',
    'verification = "direct-scroll; selection timeout",',
]
for token in unsafe_selection_completions:
    if token in service:
        raise SystemExit(f'unsafe selection completion still present: {token}')
if service.count('SyncStrategySignal.SELECTION_UNAVAILABLE') < 3:
    raise SystemExit('selection failures are not routed through ALL_CHATS fallback consistently')

print('V71RuntimeIntegrationSmoke: PASS')
