#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

bash tools/verify-core.sh

python3 tools/feature-preservation-smoke.py
python3 tools/accessibility-manifest-smoke.py
python3 tools/gesture-click-regression-smoke.py
python3 tools/v71-runtime-integration-smoke.py
python3 tools/v72-runtime-integration-smoke.py
python3 tools/v73-runtime-integration-smoke.py
python3 tools/shizuku-runtime-smoke.py
python3 tools/system-engine-integration-smoke.py
python3 tools/v73-database-smoke.py
python3 tools/root-engine-smoke.py
python3 tools/accessibility-runtime-integration-smoke.py
python3 tools/engine-registry-integration-smoke.py
python3 tools/orchestrator-workflow-smoke.py
python3 tools/capability-ui-smoke.py
python3 tools/structured-trace-integration-smoke.py
python3 tools/accessibility-event-coalescing-smoke.py
python3 tools/ci-v73-smoke.py

echo "[release] static safety audit"
python - <<'PY'
from pathlib import Path
import re
root=Path('.')
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
build=(root/'app/build.gradle.kts').read_text()
service=(root/'app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt').read_text()
ui=(root/'app/src/main/java/com/waalothmany/linkbot/ui/AppUi.kt').read_text()
assert 'versionName = "7.3.0-rc1"' in build
assert 'versionCode = 73' in build
assert 'dev.rikka.shizuku:api:13.1.5' in build
assert 'dev.rikka.shizuku:provider:13.1.5' in build
assert 'rikka.shizuku.ShizukuProvider' in manifest
entities=(root/'app/src/main/java/com/waalothmany/linkbot/data/Entities.kt').read_text()
database=(root/'app/src/main/java/com/waalothmany/linkbot/data/AppDatabase.kt').read_text()
registry=(root/'app/src/main/java/com/waalothmany/linkbot/runtime/EngineRegistry.kt').read_text()
orchestrator=(root/'app/src/main/java/com/waalothmany/linkbot/runtime/engine/ExecutionOrchestrator.kt').read_text()
capability=(root/'app/src/main/java/com/waalothmany/linkbot/capability/CapabilityManager.kt').read_text()
assert 'version = 3' in database
assert 'MIGRATION_2_3' in database
assert 'Index(value = ["androidUserId", "packageName"], unique = true)' in entities
assert 'Index(value = ["packageName"], unique = true)' not in entities
assert 'TraceRecorder' in registry
assert 'RootEngine' in registry
assert 'ShizukuEngine' in registry
assert 'ExecutionPostconditionVerifier' in orchestrator
assert 'shizukuReady' in capability
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
assert 'OperationLease' in service
assert 'NavigationProbePolicy' in service
assert 'SYNC_STRATEGY_SWITCH' in service
assert 'SYNC_SELECT_ALL_APPLIED' in service
assert 'ALL_CHATS_CLASSIFY' in service
assert 'SYNC_NAV_DECISION' in service
assert 'EXTRACT_NAV_DECISION' in service
assert 'LINK_PERSISTED' in service
assert 'clickTarget' in service
assert 'longClickTarget' in service
assert 'BACK_TOWARD_CHATS' in service
assert 'ExtractionSelectionPolicy' in service
assert 'UnreadTraversalPolicy' in service
assert 'findFilterControl' in service
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
assert 'repeat(24)' not in service
assert 'repeat(20)' not in service
assert 'delay(500)' not in service
for m in re.finditer(r'delay\((\d+)\)', service):
    if int(m.group(1)) >= 1000:
        raise AssertionError(f'fixed multi-second delay in service: {m.group(0)}')
wrapper=(root/'gradle/wrapper/gradle-wrapper.properties').read_text()
bootstrap=(root/'tools/bootstrap-gradle-wrapper.sh').read_text()
build_workflow=(root/'.github/workflows/build-android-apk.yml').read_text()
release_workflow=(root/'.github/workflows/release-android-apk.yml').read_text()
assert 'gradle-8.9-bin.zip' in wrapper
assert 'd725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab' in wrapper
assert '498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17' in bootstrap
for token in ('release-audit.sh', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug', 'upload-artifact'):
    assert token in build_workflow
for token in ('tags:', 'gh release create', 'SHA256.txt', 'VERSION_NAME'):
    assert token in release_workflow
print('Static safety audit: PASS')
PY

echo "RELEASE AUDIT: PASS"
