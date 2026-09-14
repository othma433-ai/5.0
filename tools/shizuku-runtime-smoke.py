from pathlib import Path

paths = {
    'aidl': Path('app/src/main/aidl/com/waalothmany/linkbot/runtime/engine/shizuku/IPrivilegedOps.aidl'),
    'service': Path('app/src/main/java/com/waalothmany/linkbot/runtime/engine/shizuku/PrivilegedOpsService.java'),
    'runtime': Path('app/src/main/java/com/waalothmany/linkbot/runtime/engine/shizuku/ShizukuRuntime.kt'),
    'gateway': Path('app/src/main/java/com/waalothmany/linkbot/runtime/engine/shizuku/ShizukuSystemGateway.kt'),
    'engine': Path('app/src/main/java/com/waalothmany/linkbot/runtime/engine/shizuku/ShizukuEngine.kt'),
}
for name, path in paths.items():
    assert path.exists(), f'missing {name}: {path}'

aidl = paths['aidl'].read_text()
assert 'void destroy() = 16777114;' in aidl
assert 'int probeUid() = 1;' in aidl
assert 'String listUsers() = 2;' in aidl
assert 'int launchPackageForUser' in aidl

runtime = paths['runtime'].read_text()
for token in (
    'addBinderReceivedListenerSticky',
    'addBinderDeadListener',
    'addRequestPermissionResultListener',
    'checkSelfPermission',
    'requestPermission',
    'pingBinder',
):
    assert token in runtime, token

gateway = paths['gateway'].read_text()
for token in ('bindUserService', 'UserServiceArgs', 'withTimeout', 'probeUid'):
    assert token in gateway, token

service = paths['service'].read_text()
for token in ('ProcessBuilder', 'waitFor', 'destroyForcibly', 'PACKAGE_PATTERN', 'COMPONENT_PATTERN'):
    assert token in service, token
assert 'Runtime.getRuntime().exec' not in service

engine = paths['engine'].read_text()
assert ': ExecutionEngine' in engine
app = Path('app/src/main/java/com/waalothmany/linkbot/BotApplication.kt').read_text()
assert 'ShizukuRuntime.init(this)' in app
assert app.index('DiagnosticLog.init(this)') < app.index('ShizukuRuntime.init(this)'), 'DiagnosticLog must initialize before sticky Shizuku callbacks'
print('ShizukuRuntimeSmoke: PASS')
