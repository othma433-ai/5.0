#!/usr/bin/env bash
set -euo pipefail

VERSION="${KOTLIN_VERSION:-2.0.21}"
BASE="${HOME}/.cache/wa-kotlinc/${VERSION}"
KOTLINC="${BASE}/kotlinc/bin/kotlinc"

if command -v kotlinc >/dev/null 2>&1; then
    dirname "$(command -v kotlinc)"
    exit 0
fi

if [ ! -x "$KOTLINC" ]; then
    echo "Installing Kotlin compiler ${VERSION}..." >&2
    TMP="$(mktemp -d)"
    trap 'rm -rf "$TMP"' EXIT

    mkdir -p "$BASE"

    curl -fsSL --retry 3 --retry-delay 2 \
      "https://github.com/JetBrains/kotlin/releases/download/v${VERSION}/kotlin-compiler-${VERSION}.zip" \
      -o "$TMP/kotlin.zip"

    unzip -q "$TMP/kotlin.zip" -d "$BASE"
fi

test -x "$KOTLINC"
echo "${BASE}/kotlinc/bin"
