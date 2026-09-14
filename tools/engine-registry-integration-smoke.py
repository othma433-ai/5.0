from pathlib import Path
reg=Path('app/src/main/java/com/waalothmany/linkbot/runtime/EngineRegistry.kt')
ver=Path('app/src/main/java/com/waalothmany/linkbot/runtime/AndroidExecutionPostconditionVerifier.kt')
disc=Path('app/src/main/java/com/waalothmany/linkbot/whatsapp/SystemWhatsAppDiscovery.kt')
for p in (reg,ver,disc): assert p.exists(), p
r=reg.read_text(); v=ver.read_text(); d=disc.read_text()
for token in ('ShizukuEngine', 'RootEngine', 'StandardAndroidEngine', 'AccessibilityEngine', 'ExecutionOrchestrator', 'AndroidExecutionPostconditionVerifier'):
    assert token in r, token
for token in ('VerificationPolicy.FOREGROUND_PACKAGE', 'lastEventPackageName', 'withTimeoutOrNull', 'VerificationPolicy.ACCESSIBILITY_WINDOW'):
    assert token in v, token
for token in ('SystemOperation.DISCOVER_USERS', 'SystemOperation.DISCOVER_PACKAGES', 'ProfileAwareInstanceResolver.parseUsers', 'lastResolvedEngine'):
    assert token in d, token
print('EngineRegistryIntegrationSmoke: PASS')
