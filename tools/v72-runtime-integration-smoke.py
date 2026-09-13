from pathlib import Path

root = Path(__file__).resolve().parents[1]
service = (root / 'app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt').read_text()
lease = (root / 'app/src/main/java/com/waalothmany/linkbot/automation/OperationLease.kt').read_text()
probe = (root / 'app/src/main/java/com/waalothmany/linkbot/automation/NavigationProbePolicy.kt').read_text()
build = (root / 'app/build.gradle.kts').read_text()

required_service = [
    'operationLease.acquire(AutomationRunKind.SYNC)',
    'operationLease.acquire(AutomationRunKind.EXTRACT)',
    'operationLease.acquire(AutomationRunKind.RETRY_FAILED)',
    'operationLease.isActive(syncLease)',
    'operationLease.isActive(extractionLease)',
    'operationLease.isActive(retryLease)',
    'operationLease.releaseAny()',
    'NavigationProbePolicy.delayMs(effectiveMode, attempt)',
    'NavigationProbePolicy.maxAttempts(effectiveMode)',
    'SYNC_NAV_PROBE_EXHAUSTED',
    'EXTRACT_NAV_PROBE_EXHAUSTED',
    'SYNC_RUNTIME_SERVICE_START_FAILED',
    'operationLease.release(syncLease)',
]
missing = [needle for needle in required_service if needle not in service]
if missing:
    raise SystemExit(f'v7.2 service integration missing: {missing}')

for forbidden in ('repeat(24)', 'repeat(20)', 'delay(500)', 'delay(350)', 'delay(300)'):
    if forbidden in service:
        raise SystemExit(f'legacy fixed navigation polling remains: {forbidden}')

if 'OperationLeaseToken' not in lease or 'AtomicReference' not in lease or 'AtomicLong' not in lease:
    raise SystemExit('OperationLease must remain atomic and generation-tokened')
if 'totalBudgetMs' not in probe:
    raise SystemExit('NavigationProbePolicy budget guard missing')
if 'versionCode = 72' not in build or 'versionName = "7.2.0-rc1"' not in build:
    raise SystemExit('v7.2 version metadata mismatch')


# Startup ordering: persist the session before its queue so Stop/restart cannot leave an orphan queue.
start = service.index('fun startExtraction(')
end = service.index('private fun handleSync', start)
extract_start = service[start:end]
if extract_start.index('sessionDao().upsert') > extract_start.index('queueDao().upsertAll'):
    raise SystemExit('extraction startup must persist session before queue for stop-safe recovery')

# Retry must revalidate the exact generation after the final suspend before clearing stop flags.
retry_start = service[service.index('fun retryFailed('):service.index('fun startGroupSync(')]
update_idx = retry_start.index('sessionDao().updateState(session.id, "RUNNING")')
reset_idx = retry_start.index('BotRuntime.resetControlFlags()')
if 'operationLease.isActive(retryLease)' not in retry_start[update_idx:reset_idx]:
    raise SystemExit('retry startup must revalidate lease after session state update before resetControlFlags')

print('V72RuntimeIntegrationSmoke: PASS')
