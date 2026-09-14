from pathlib import Path
cap=Path('app/src/main/java/com/waalothmany/linkbot/capability/CapabilityManager.kt').read_text()
vm=Path('app/src/main/java/com/waalothmany/linkbot/MainViewModel.kt').read_text()
ui=Path('app/src/main/java/com/waalothmany/linkbot/ui/AppUi.kt').read_text()
act=Path('app/src/main/java/com/waalothmany/linkbot/MainActivity.kt').read_text()
for token in ('shizukuState', 'shizukuReady', 'shizukuPermissionRequired', 'rootFallbackEnabled'):
    assert token in cap, token
for token in ('requestShizukuPermission', 'setRootFallbackEnabled', 'ShizukuRuntime.state.collectLatest'):
    assert token in vm, token
for token in ('منح صلاحية Shizuku', 'استخدام Root كمسار احتياطي', 'Shizuku: ${caps.shizukuState}'):
    assert token in ui, token
assert 'onOpenShizuku' in act
assert 'المحرك المميز غير مفعّل في نسخة الإنقاذ الحالية' not in ui
print('CapabilityUiSmoke: PASS')
