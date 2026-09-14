from pathlib import Path
service = Path('app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt').read_text()
tree = Path('app/src/main/java/com/waalothmany/linkbot/automation/AccessibilityTree.kt').read_text()
adapter = Path('app/src/main/java/com/waalothmany/linkbot/whatsapp/WhatsAppAdapter.kt').read_text()
store = Path('app/src/main/java/com/waalothmany/linkbot/whatsapp/AdaptiveSelectorStore.kt').read_text()
checks = {
    'suffix tree search': 'fun findByViewIdSuffixes(' in tree,
    'learned selector search': 'fun findBySelectorSignatures(' in tree,
    'selector store attached': 'AdaptiveSelectorStore' in service,
    'groups adaptive lookup': 'SelectorRole.GROUPS_FILTER' in service and 'findAdaptiveControl' in service,
    'all adaptive lookup': 'SelectorRole.ALL_FILTER' in service,
    'chats adaptive lookup': 'SelectorRole.CHATS_ANCHOR' in service,
    'select all adaptive lookup': 'SelectorRole.SELECT_ALL' in service,
    'verified learning': 'recordVerifiedSelector(SelectorRole.GROUPS_FILTER' in service,
    'failure feedback': 'recordFailedSelector(SelectorRole.GROUPS_FILTER' in service,
    'package neutral group suffix': 'conversations_filter_debug_view_id_groups' in adapter,
    'package neutral all suffix': 'conversations_filter_debug_view_id_all' in adapter,
    'no conversation persistence': all(token not in store for token in ['messageText', 'fullMessageText', 'conversationText', 'LinkEntity']),
}
missing = [name for name, ok in checks.items() if not ok]
if missing:
    raise SystemExit('Adaptive selector integration missing: ' + ', '.join(missing))
print('AdaptiveSelectorIntegrationSmoke: PASS')
