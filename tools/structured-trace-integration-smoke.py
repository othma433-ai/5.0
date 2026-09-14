from pathlib import Path

registry = Path('app/src/main/java/com/waalothmany/linkbot/runtime/EngineRegistry.kt').read_text()
log = Path('app/src/main/java/com/waalothmany/linkbot/runtime/DiagnosticLog.kt').read_text()
assert 'TraceRecorder' in registry
assert 'ENGINE_TRACE' in registry
for token in ('traceId', 'step', 'engine', 'attempt', 'durationMs', 'result', 'failure', 'fallbackTo'):
    assert token in registry, token
assert 'ThreadPoolExecutor' in log
assert 'ArrayBlockingQueue' in log
assert 'flushPending' in log
print('Structured trace integration: PASS')
