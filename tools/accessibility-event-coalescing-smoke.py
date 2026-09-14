from pathlib import Path
service = Path('app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt').read_text()
for token in (
    'AccessibilityEventCoalescer',
    'eventCoalescer.offer(signal)',
    'scheduleCoalescedDrain()',
    'EventSignalKind.SCROLL',
    'EventSignalKind.WINDOW_STATE',
):
    assert token in service, token
assert 'if (!eventGuard.compareAndSet(false, true)) return' not in service
print('Accessibility event coalescing integration: PASS')
