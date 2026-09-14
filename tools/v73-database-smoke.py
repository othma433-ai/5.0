from pathlib import Path
entities = Path('app/src/main/java/com/waalothmany/linkbot/data/Entities.kt').read_text()
db = Path('app/src/main/java/com/waalothmany/linkbot/data/AppDatabase.kt').read_text()
dao = Path('app/src/main/java/com/waalothmany/linkbot/data/Daos.kt').read_text()
assert 'Index(value = ["androidUserId", "packageName"], unique = true)' in entities
for token in ('androidUserId: Int', 'profileType: String', 'reachable: Boolean', 'lastResolvedEngine: String?'):
    assert token in entities, token
assert 'version = 3' in db
assert 'MIGRATION_2_3' in db
assert 'ALTER TABLE' not in db or 'whatsapp_instances_v3' in db
assert 'getByPackage(userId: Int, packageName: String)' in dao
print('V73DatabaseSmoke: PASS')
