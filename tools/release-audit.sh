#!/usr/bin/env bash

# WA_FORCE_JDK17_V2
if [ -x "/usr/lib/jvm/java-17-openjdk-amd64/bin/java" ]; then
  export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

bash tools/verify-core.sh

echo "[release] static safety audit"
python - <<'PY'
from pathlib import Path
import re
root=Path('.')
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
build=(root/'app/build.gradle.kts').read_text()
service=(root/'app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt').read_text()
ui=(root/'app/src/main/java/com/waalothmany/linkbot/ui/AppUi.kt').read_text()
assert 'versionName = "5.1.0-rc1"' in build
assert 'versionCode = 51' in build
assert 'QUERY_ALL_PACKAGES' not in manifest
assert 'android:allowBackup="false"' in manifest
assert 'AdaptiveTimingPolicy' in service
assert 'StageCircuitBreaker' in service
assert 'SYNC_COVERAGE_SAFETY_STOP' in service
assert 'AMBIGUOUS_GROUP' in service
assert 'AutomationHealthPolicy' in service
assert 'MessageViewportPolicy' in service
assert 'ThroughputMeter' in service
assert 'ScreenKind.GROUP_LIST' in service
assert 'DurableViewportPolicy' in service
assert 'viewportCommitInFlight' in service
assert 'VIEWPORT_PERSISTENCE_FAILED' in service
assert 'SmartQueuePolicy' in service
assert 'FailureRecoveryPolicy' in service
assert 'QueueProgressPolicy' in service
tree=(root/'app/src/main/java/com/waalothmany/linkbot/automation/AccessibilityTree.kt').read_text()
assert 'URLSpan' in tree
assert 'screenEvidence' in tree
assert 'bestConversationScrollable' in tree
assert 'bestMessageScrollable' in tree
assert 'LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {\n        LazyColumn' not in ui
for path in root.glob('app/src/main/java/**/*.kt'):
    text=path.read_text()
    if re.search(r'\b(?:TODO|FIXME|NotImplementedError)\b', text):
        raise AssertionError(f'placeholder marker in {path}')
# Fixed multi-second sleeps are forbidden in the automation engine. Watchdog timeout
# is allowed because it is a failure bound, not a navigation delay.
for m in re.finditer(r'delay\((\d+)\)', service):
    if int(m.group(1)) >= 1000:
        raise AssertionError(f'fixed multi-second delay in service: {m.group(0)}')
print('Static safety audit: PASS')
PY

echo "RELEASE AUDIT: PASS"
