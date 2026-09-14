from pathlib import Path
repo=Path('app/src/main/java/com/waalothmany/linkbot/data/Repositories.kt').read_text()
svc=Path('app/src/main/java/com/waalothmany/linkbot/automation/WaAccessibilityService.kt').read_text()
needles_repo=['LinkPersistResult', 'insertedOccurrences', 'duplicateOccurrences', 'finalizeExtractionTransition', 'db.withTransaction']
needles_svc=['val persistResult = ServiceLocator.links.recordBatch', '"count" to persistResult.insertedOccurrences', 'throughputMeter.addLinks(persistResult.insertedOccurrences)', 'finalizeExtractionTransition']
missing=[n for n in needles_repo if n not in repo]+[n for n in needles_svc if n not in svc]
if missing: raise SystemExit('Persistence integration missing: '+', '.join(missing))
print('PersistenceIntegrationSmoke: PASS')
