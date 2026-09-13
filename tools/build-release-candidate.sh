#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
chmod +x gradlew tools/bootstrap-gradle-wrapper.sh tools/release-audit.sh
./tools/bootstrap-gradle-wrapper.sh
TERM=xterm ./tools/release-audit.sh
./gradlew --no-daemon --stacktrace :app:testDebugUnitTest
./gradlew --no-daemon --stacktrace :app:lintDebug
./gradlew --no-daemon --stacktrace :app:assembleDebug
VERSION_NAME="$(sed -nE 's/^[[:space:]]*versionName = "([^"]+)"/\1/p' app/build.gradle.kts | head -1)"
mkdir -p release-output
cp app/build/outputs/apk/debug/app-debug.apk "release-output/WA-Al-Othmany-Link-Bot-v${VERSION_NAME}.apk"
(cd release-output && sha256sum "WA-Al-Othmany-Link-Bot-v${VERSION_NAME}.apk" > SHA256.txt)
echo "Built release-output/WA-Al-Othmany-Link-Bot-v${VERSION_NAME}.apk"
