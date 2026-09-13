#!/usr/bin/env bash

# WA_FORCE_JDK17_V2
if [ -x "/usr/lib/jvm/java-17-openjdk-amd64/bin/java" ]; then
  export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

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

# Runtime verification boundary:
# Only pure-Kotlin utilities are compiled here.
#
# BotRuntime depends on kotlinx.coroutines and MUST be compiled/tested
# by Gradle using the exact app dependency graph. Never use the
# coroutine jar bundled inside a Kotlin compiler distribution.

kotlinc \
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
  'app/build.gradle.kts': ['versionCode = 50', 'versionName = "5.0.0-rc1"'],
  'app/src/main/AndroidManifest.xml': ['android:allowBackup="false"', 'WaAccessibilityService', 'BotForegroundService', 'OverlayControllerService'],
  'app/src/main/java/com/waalothmany/linkbot/automation/AccessibilityTree.kt': ['URLSpan', 'getSpans', 'screenEvidence', 'messageViewport', 'bestConversationScrollable', 'bestMessageScrollable'],
  'app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt': [
      'EndOfListGuard', 'AMBIGUOUS_GROUP', 'STAGE_TIMEOUT', 'STAGE_RETRY', 'recordBatch', 'GroupIdentityMatcher', 'CheckpointCodec', 'ViewportIdentityPolicy', 'GROUP_FILTER_VERIFIED', 'AdaptiveTimingPolicy', 'StageCircuitBreaker', 'SYNC_COVERAGE_SAFETY_STOP', 'AutomationHealthPolicy', 'MessageViewportPolicy', 'ThroughputMeter', 'ScreenKind.GROUP_LIST', 'SmartQueuePolicy', 'DurableViewportPolicy', 'FailureRecoveryPolicy', 'QueueProgressPolicy', 'VIEWPORT_PERSISTENCE_FAILED',
  ],
  'app/src/main/java/com/waalothmany/linkbot/data/AppDatabase.kt': ['version = 2', 'MIGRATION_1_2'],
  'app/src/main/java/com/waalothmany/linkbot/data/Repositories.kt': ['withTransaction', 'recordBatch', 'selected(instanceId: String)'],
  'app/src/main/java/com/waalothmany/linkbot/runtime/DiagnosticLog.kt': ['DiagnosticSanitizer', 'runtime.log'],
  '.github/workflows/build-android-apk.yml': [':app:testDebugUnitTest', ':app:assembleDebug', 'upload-artifact'],
}
for name, needles in checks.items():
    text = Path(name).read_text()
    missing = [n for n in needles if n not in text]
    if missing:
        raise SystemExit(f"{name}: missing {missing}")
manifest = Path('app/src/main/AndroidManifest.xml').read_text()
if 'QUERY_ALL_PACKAGES' in manifest:
    raise SystemExit('Manifest must not request QUERY_ALL_PACKAGES')
ui = Path('app/src/main/java/com/waalothmany/linkbot/ui/AppUi.kt').read_text()
if 'LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {\n        LazyColumn' in ui:
    raise SystemExit('Duplicate nested LazyColumn source hazard detected')
print('Hardening invariants: PASS')
PY

echo "CORE VERIFICATION: PASS"
