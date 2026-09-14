from pathlib import Path
sup = Path('app/src/main/java/com/waalothmany/linkbot/runtime/engine/accessibility/AccessibilityRuntimeSupervisor.kt')
svc = Path('app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt').read_text()
cap = Path('app/src/main/java/com/waalothmany/linkbot/capability/CapabilityManager.kt').read_text()
vm = Path('app/src/main/java/com/waalothmany/linkbot/MainViewModel.kt').read_text()
assert sup.exists()
text = sup.read_text()
for token in ('MutableStateFlow', 'markBinderConnected', 'markEvent', 'markWindowReady', 'updateSettingsEnabled'):
    assert token in text, token
for token in ('AccessibilityRuntimeSupervisor.markBinderConnected()', 'AccessibilityRuntimeSupervisor.markEvent(', 'AccessibilityRuntimeSupervisor.markWindowReady()'):
    assert token in svc, token
assert 'AccessibilityRuntimeSupervisor.state.value.connected' in cap
assert 'AccessibilityRuntimeSupervisor.state.collectLatest' in vm
print('AccessibilityRuntimeIntegrationSmoke: PASS')
