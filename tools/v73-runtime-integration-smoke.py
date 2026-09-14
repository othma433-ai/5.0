from pathlib import Path

build = Path("app/build.gradle.kts").read_text()
manifest = Path("app/src/main/AndroidManifest.xml").read_text()

assert 'versionCode = 73' in build
assert 'versionName = "7.3.0-rc1"' in build
assert 'dev.rikka.shizuku:api:13.1.5' in build
assert 'dev.rikka.shizuku:provider:13.1.5' in build
assert 'rikka.shizuku.ShizukuProvider' in manifest
assert '${applicationId}.shizuku' in manifest
print("v7.3 Shizuku configuration: PASS")
