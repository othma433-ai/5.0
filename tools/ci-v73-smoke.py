from pathlib import Path

bootstrap = Path('tools/bootstrap-gradle-wrapper.sh').read_text()
build = Path('.github/workflows/build-android-apk.yml').read_text()
release = Path('.github/workflows/release-android-apk.yml').read_text()
audit = Path('tools/release-audit.sh').read_text()

assert 'gradle-8.9-wrapper.jar' not in bootstrap
assert 'gradle wrapper' in bootstrap
assert '--gradle-version 8.9' in bootstrap
assert '498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17' in bootstrap
assert 'd725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab' in bootstrap
for text in (build, release):
    assert 'Set up Gradle 8.9' in text
    assert "gradle-version: '8.9'" in text
    assert text.index('Set up Gradle 8.9') < text.index('Bootstrap verified Gradle wrapper')
assert 'versionName = "7.3.0-rc1"' in audit
assert 'tools/v73-runtime-integration-smoke.py' in audit
assert 'tools/capability-ui-smoke.py' in audit
assert 'tools/structured-trace-integration-smoke.py' in audit
print('CI v7.3 smoke: PASS')
