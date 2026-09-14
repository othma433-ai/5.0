from pathlib import Path
svc=Path('app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt').read_text()
vm=Path('app/src/main/java/com/waalothmany/linkbot/MainViewModel.kt').read_text()
dao=Path('app/src/main/java/com/waalothmany/linkbot/data/Daos.kt').read_text()
assert 'EngineRegistry.discovery.discover()' in vm
assert 'instance?.reachable == true' in vm
assert 'EngineRegistry.launchInstance' in svc
assert 'EngineRegistry.recoverInstance' in svc
assert 'WhatsAppInstanceDetector.launch(this@WaAccessibilityService' not in svc
assert 'updateRuntimeRoute' in dao
print('OrchestratorWorkflowSmoke: PASS')
