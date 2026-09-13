#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/wa-link-bot-verify"
rm -rf "$OUT"
mkdir -p "$OUT"
cd "$ROOT"

export TERM="${TERM:-xterm}"

echo "[1/3] Compile pure Kotlin verification suite"
kotlinc \
  app/src/main/java/com/waalothmany/linkbot/core/link/*.kt \
  app/src/main/java/com/waalothmany/linkbot/core/importer/ParsedMessage.kt \
  app/src/main/java/com/waalothmany/linkbot/core/importer/ExportChatParser.kt \
  app/src/main/java/com/waalothmany/linkbot/core/exporter/ResultExporter.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/AutomationPolicy.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/AutomationMode.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/SearchResolutionPolicy.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/CheckpointCodec.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/PerformanceProfile.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/GroupIdentityMatcher.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/FilterVerificationPolicy.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/RowClassificationPolicy.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/ViewportIdentityPolicy.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/AdaptiveTimingPolicy.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/StageCircuitBreaker.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/SyncCoveragePolicy.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/ScreenEvidencePolicy.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/AutomationHealthPolicy.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/MessageViewportPolicy.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/ContainerRolePolicy.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/SmartQueuePolicy.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/DurableViewportPolicy.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/FailureRecoveryPolicy.kt \
  app/src/main/java/com/waalothmany/linkbot/automation/QueueProgressPolicy.kt \
  app/src/main/java/com/waalothmany/linkbot/whatsapp/PackageCandidatePolicy.kt \
  app/src/main/java/com/waalothmany/linkbot/capability/ReadinessEvaluator.kt \
  app/src/main/java/com/waalothmany/linkbot/runtime/DiagnosticSanitizer.kt \
  tools/jvmtests/LinkEngineSmoke.kt \
  tools/jvmtests/ExportChatParserSmoke.kt \
  tools/jvmtests/ExporterSmoke.kt \
  tools/jvmtests/AutomationPolicySmoke.kt \
  tools/jvmtests/SearchResolutionSmoke.kt \
  tools/jvmtests/CheckpointSmoke.kt \
  tools/jvmtests/PerformanceProfileSmoke.kt \
  tools/jvmtests/GroupIdentityMatcherSmoke.kt \
  tools/jvmtests/FilterVerificationPolicySmoke.kt \
  tools/jvmtests/RowClassificationPolicySmoke.kt \
  tools/jvmtests/ViewportIdentityPolicySmoke.kt \
  tools/jvmtests/AdaptiveTimingSmoke.kt \
  tools/jvmtests/StageCircuitBreakerSmoke.kt \
  tools/jvmtests/SyncCoveragePolicySmoke.kt \
  tools/jvmtests/ScreenEvidencePolicySmoke.kt \
  tools/jvmtests/AutomationHealthPolicySmoke.kt \
  tools/jvmtests/MessageViewportPolicySmoke.kt \
  tools/jvmtests/ContainerRolePolicySmoke.kt \
  tools/jvmtests/SmartQueuePolicySmoke.kt \
  tools/jvmtests/DurableViewportPolicySmoke.kt \
  tools/jvmtests/FailureRecoveryPolicySmoke.kt \
  tools/jvmtests/QueueProgressPolicySmoke.kt \
  tools/jvmtests/PackageCandidatePolicySmoke.kt \
  tools/jvmtests/ReadinessEvaluatorSmoke.kt \
  tools/jvmtests/DiagnosticSanitizerSmoke.kt \
  tools/jvmtests/CoreSmokeSuite.kt \
  -include-runtime -d "$OUT/core.jar"

java -cp "$OUT/core.jar" com.waalothmany.linkbot.tools.CoreSmokeSuite


# ------------------------------------------------------------
# Runtime verification boundary
# ------------------------------------------------------------
#
# BotRuntime depends on kotlinx.coroutines / StateFlow.
#
# It MUST NOT be compiled here with the standalone Kotlin CLI,
# because that environment is not the Android application's real
# dependency graph.
#
# BotRuntime is verified by:
#
#   :app:testDebugUnitTest
#
# using app/src/test/.../BotRuntimeTest.kt and the exact Gradle
# dependencies declared by the Android project.
#
# Only pure-Kotlin runtime utilities remain in this hermetic smoke test.

timeout 120s kotlinc \
  app/src/main/java/com/waalothmany/linkbot/runtime/ThroughputMeter.kt \
  tools/jvmtests/ThroughputMeterSmoke.kt \
  -include-runtime \
  -d "$OUT/runtime.jar"

java -cp "$OUT/runtime.jar" \
  com.waalothmany.linkbot.runtime.ThroughputMeterSmokeKt

echo "[2/3] Android XML parse"
python - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path('.')
files = [
    root/'app/src/main/AndroidManifest.xml',
    root/'app/src/main/res/xml/accessibility_service_config.xml',
    root/'app/src/main/res/values/strings.xml',
    root/'app/src/main/res/values/themes.xml',
    root/'app/src/main/res/drawable/ic_app.xml',
]
for p in files:
    ET.parse(p)
print(f"XML: PASS ({len(files)} files)")
PY

echo "[3/3] Production-hardening source invariants"
python - <<'PY'
from pathlib import Path
checks = {
  'app/build.gradle.kts': ['versionCode = 52', 'versionName = "5.2.0-rc1"'],
  'app/src/main/AndroidManifest.xml': ['android:allowBackup="false"', 'WaAccessibilityService', 'BotForegroundService', 'OverlayControllerService'],
  'app/src/main/java/com/waalothmany/linkbot/automation/AccessibilityTree.kt': ['URLSpan', 'getSpans', 'screenEvidence', 'messageViewport', 'bestConversationScrollable', 'bestMessageScrollable'],
  'app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt': [
      'EndOfListGuard', 'AMBIGUOUS_GROUP', 'STAGE_TIMEOUT', 'STAGE_RETRY', 'recordBatch', 'GroupIdentityMatcher', 'CheckpointCodec', 'ViewportIdentityPolicy', 'GROUP_FILTER_VERIFIED', 'AdaptiveTimingPolicy', 'StageCircuitBreaker', 'SYNC_COVERAGE_SAFETY_STOP', 'AutomationHealthPolicy', 'MessageViewportPolicy', 'ThroughputMeter', 'ScreenKind.GROUP_LIST', 'SmartQueuePolicy', 'DurableViewportPolicy', 'FailureRecoveryPolicy', 'QueueProgressPolicy', 'VIEWPORT_PERSISTENCE_FAILED',
  ],
  'app/src/main/java/com/waalothmany/linkbot/data/AppDatabase.kt': ['version = 2', 'MIGRATION_1_2'],
  'app/src/main/java/com/waalothmany/linkbot/data/Repositories.kt': ['withTransaction', 'recordBatch', 'selected(instanceId: String)'],
  'app/src/main/java/com/waalothmany/linkbot/runtime/DiagnosticLog.kt': ['DiagnosticSanitizer', 'runtime.log'],
}
for name, needles in checks.items():
    text = Path(name).read_text()
    missing = [n for n in needles if n not in text]
    if missing:
        raise SystemExit(f"{name}: missing {missing}")

# Android CI workflow discovery.
#
# Workflow filenames are implementation details and may change.
# The release gate therefore validates workflow CAPABILITIES rather
# than a hard-coded path.
workflow_dir = Path('.github/workflows')

workflow_files = sorted(
    list(workflow_dir.glob('*.yml')) +
    list(workflow_dir.glob('*.yaml'))
)

required_ci_capabilities = (
    ':app:testDebugUnitTest',
    ':app:assembleDebug',
    'upload-artifact',
)

matching_workflows = []

for workflow_path in workflow_files:

    workflow_text = workflow_path.read_text()

    if all(
        capability in workflow_text
        for capability in required_ci_capabilities
    ):
        matching_workflows.append(workflow_path)

if not matching_workflows:
    raise SystemExit(
        'No Android build workflow provides all required capabilities: '
        + ', '.join(required_ci_capabilities)
    )

print(
    'Android CI workflow: PASS ('
    + ', '.join(str(p) for p in matching_workflows)
    + ')'
)


manifest = Path('app/src/main/AndroidManifest.xml').read_text()
if 'QUERY_ALL_PACKAGES' in manifest:
    raise SystemExit('Manifest must not request QUERY_ALL_PACKAGES')
ui = Path('app/src/main/java/com/waalothmany/linkbot/ui/AppUi.kt').read_text()
if 'LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {\n        LazyColumn' in ui:
    raise SystemExit('Duplicate nested LazyColumn source hazard detected')
print('Hardening invariants: PASS')
PY

echo "CORE VERIFICATION: PASS"
