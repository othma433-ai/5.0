from pathlib import Path
s=Path('app/src/main/java/com/waalothmany/linkbot/runtime/DiagnosticLog.kt').read_text()
needles=['ArrayBlockingQueue', 'DiagnosticQueueMetrics', 'isDaemon = true', 'dropped=', 'queue.offer', 'drainTo']
missing=[n for n in needles if n not in s]
if missing: raise SystemExit('Async DiagnosticLog missing: '+', '.join(missing))
# record() must not directly append/read/write files.
record=s.split('fun record(',1)[1].split('fun exportText',1)[0]
for bad in ['appendText(', 'readBytes(', 'writeBytes(', 'writeText(']:
    if bad in record: raise SystemExit('record() still performs file I/O: '+bad)
print('AsyncDiagnosticLogSmoke: PASS')
