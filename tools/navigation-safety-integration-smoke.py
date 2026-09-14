from pathlib import Path
service = Path('app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt').read_text()
checks = {
    'search entry policy used': 'SearchEntryPolicy.decide' in service,
    'chat search back recovery': 'SearchEntryAction.BACK_TO_CHATS' in service,
    'verified editor': 'SearchEntryAction.USE_EXISTING_EDITOR' in service,
    'global search action': 'SearchEntryAction.OPEN_GLOBAL_SEARCH' in service,
    'safe all-chats surface': 'safeAllChatsSurface' in service,
    'query screen verification': 'EXTRACT_QUERY_SCREEN_NOT_VERIFIED' in service,
}
missing = [k for k,v in checks.items() if not v]
if missing:
    raise SystemExit('Navigation safety integration missing: ' + ', '.join(missing))
print('NavigationSafetyIntegrationSmoke: PASS')
