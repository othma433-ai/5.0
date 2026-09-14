from pathlib import Path
s=Path('app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt').read_text()
needles=['AccessibilityEventCoalescer(', 'eventCoalescer.shouldProcess', 'TYPE_WINDOW_STATE_CHANGED', 'TYPE_WINDOW_CONTENT_CHANGED']
missing=[n for n in needles if n not in s]
if missing: raise SystemExit('Event coalescing integration missing: '+', '.join(missing))
print('EventCoalescingIntegrationSmoke: PASS')
