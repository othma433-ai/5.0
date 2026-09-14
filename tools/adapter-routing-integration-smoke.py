from pathlib import Path
service = Path('app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt').read_text()
vm = Path('app/src/main/java/com/waalothmany/linkbot/MainViewModel.kt').read_text()
detector = Path('app/src/main/java/com/waalothmany/linkbot/whatsapp/WhatsAppInstanceDetector.kt').read_text()
required_service = [
    'targetAdapter',
    'WhatsAppAdapterRegistry.byId',
    'targetAdapter.groupFilterLabels',
    'targetAdapter.filterPeerLabels',
    'targetAdapter.selectAllLabels',
    'WhatsAppInstanceDetector.launch(this@WaAccessibilityService, instance)',
]
for needle in required_service:
    assert needle in service, f'missing adapter routing: {needle}'
assert 'service.startGroupSync(instance!!)' in vm
assert 'service.startExtraction(instance!!, mode)' in vm
assert 'LauncherApps' in detector and 'profileIdentity' in detector
print('adapter-routing-integration-smoke: PASS')
