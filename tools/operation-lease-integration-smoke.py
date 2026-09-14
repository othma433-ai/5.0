from pathlib import Path
p=Path('app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt')
s=p.read_text()
needles = [
    'OperationLeaseController',
    'TerminalOnceGate<String>',
    'OPERATION_START_REJECTED_BUSY',
    'operationLeaseController.isCurrent',
    'operationLeaseController.claimTerminal',
    'groupTerminalGate.claim',
    'data class Sync(val stage: SyncStage, val lease: OperationLease)',
    'data class Extract(val stage: ExtractStage, val lease: OperationLease)',
]
missing=[x for x in needles if x not in s]
if missing:
    raise SystemExit('Operation lease integration missing: ' + ', '.join(missing))
print('OperationLeaseIntegrationSmoke: PASS')
