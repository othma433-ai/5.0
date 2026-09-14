from pathlib import Path
std = Path('app/src/main/java/com/waalothmany/linkbot/runtime/engine/standard/StandardAndroidEngine.kt')
a11y = Path('app/src/main/java/com/waalothmany/linkbot/runtime/engine/accessibility/AccessibilityEngine.kt')
assert std.exists(), std
assert a11y.exists(), a11y
st = std.read_text()
at = a11y.read_text()
for token in ('EngineId.STANDARD_ANDROID', 'getLaunchIntentForPackage', 'AndroidUserIdentity.currentUserId()', 'LAUNCH_PACKAGE_CURRENT_USER'):
    assert token in st, token
assert 'LAUNCH_PACKAGE_FOR_USER' not in st
for token in ('EngineId.ACCESSIBILITY', 'AccessibilityRuntimeSupervisor.state.value', 'ACCESSIBILITY_TREE', 'VERIFY_SCREEN'):
    assert token in at, token
print('SystemEngineIntegrationSmoke: PASS')
