from pathlib import Path

ROOT = Path('.')

checks = {
    'app/src/main/java/com/waalothmany/linkbot/automation/AutomationMode.kt': [
        'DEEP', 'UNREAD_ONLY', 'NEW_ONLY',
    ],
    'app/src/main/java/com/waalothmany/linkbot/MainViewModel.kt': [
        'fun selectAll()', 'fun clearSelection()', 'fun selectUnread()',
        'fun selectRead()', 'fun selectActive()', 'fun selectNeverScanned()',
        'fun selectFailed()', 'fun pause()', 'fun resume()', 'fun stop()',
        'fun skip()', 'fun retryFailed()', 'fun syncGroups()', 'fun startExtraction(',
    ],
    'app/src/main/java/com/waalothmany/linkbot/automation/SyncStrategyPolicy.kt': [
        'GROUP_FILTER_SCROLL', 'GROUP_FILTER_SELECT_ALL', 'ALL_CHATS_CLASSIFY',
    ],
    'app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt': [
        'SYNC_STRATEGY_SWITCH', 'SYNC_SELECT_ALL_APPLIED', 'ALL_CHATS_CLASSIFY',
        'startGroupSync', 'startExtraction', 'SYNC_NAV_DECISION', 'EXTRACT_NAV_DECISION', 'LINK_PERSISTED',
    ],
    'app/src/main/java/com/waalothmany/linkbot/core/exporter/ExportUseCase.kt': [
        'XLSX', 'CSV', 'TXT', 'JSON',
    ],
    'app/src/main/java/com/waalothmany/linkbot/MainActivity.kt': [
        'OpenDocument()', 'CreateDocument', 'writeDiagnostics',
    ],
    'app/src/main/AndroidManifest.xml': [
        'WaAccessibilityService', 'BotForegroundService', 'OverlayControllerService',
        'com.whatsapp', 'com.whatsapp.w4b', 'moe.shizuku.privileged.api',
    ],
    'app/src/main/java/com/waalothmany/linkbot/runtime/BotForegroundService.kt': [
        'class BotForegroundService',
    ],
    'app/src/main/java/com/waalothmany/linkbot/runtime/OverlayControllerService.kt': [
        'class OverlayControllerService',
    ],
}

for path, needles in checks.items():
    text = (ROOT / path).read_text()
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise SystemExit(f'Feature preservation failure: {path} missing {missing}')

ui = (ROOT / 'app/src/main/java/com/waalothmany/linkbot/ui/AppUi.kt').read_text()
if 'v4 •' in ui or 'v5 •' in ui:
    raise SystemExit('Hard-coded stale UI version returned')
if 'BuildConfig.VERSION_NAME' not in ui:
    raise SystemExit('UI must derive version from BuildConfig.VERSION_NAME')
if 'import androidx.compose.foundation.layout.weight' in ui:
    raise SystemExit('Invalid Compose weight import returned')

service = (ROOT / 'app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt').read_text()
if 'normalizePreview(row.preview)' in service and 'private fun normalizePreview' not in service:
    raise SystemExit('normalizePreview call exists without implementation')

print('FeaturePreservationSmoke: PASS')
