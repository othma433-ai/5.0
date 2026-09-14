from pathlib import Path
policy=Path('app/src/main/java/com/waalothmany/linkbot/runtime/engine/root/RootCommandPolicy.kt').read_text()
gateway=Path('app/src/main/java/com/waalothmany/linkbot/runtime/engine/root/RootCommandGateway.kt').read_text()
engine=Path('app/src/main/java/com/waalothmany/linkbot/runtime/engine/root/RootEngine.kt').read_text()
for bad in ('fun execute(command: String', 'Runtime.getRuntime().exec'):
    assert bad not in gateway
for token in ('ProcessBuilder("su", "-c", rendered)', 'waitFor(timeoutMs, TimeUnit.MILLISECONDS)', 'destroyForcibly()', 'MAX_OUTPUT_CHARS'):
    assert token in gateway, token
for token in ('packagePattern', 'componentPattern', 'Invalid package name', 'Invalid component name'):
    assert token in policy, token
assert 'enabledProvider' in engine
assert 'gateway.probeUid()' in engine
assert 'uid=0' in engine
print('RootEngineSmoke: PASS')
