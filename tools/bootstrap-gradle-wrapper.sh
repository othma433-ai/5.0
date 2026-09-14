#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/gradle/wrapper/gradle-wrapper.jar"

EXPECTED="498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17"

verify() {
    [ -s "$JAR" ] || return 1

    ACTUAL="$(sha256sum "$JAR" | awk '{print $1}')"

    [ "$ACTUAL" = "$EXPECTED" ]
}

if verify; then
    echo "Gradle wrapper JAR: VERIFIED"
    exit 0
fi

echo "Gradle wrapper missing or invalid — restoring official Gradle 8.9 wrapper..."

mkdir -p "$(dirname "$JAR")"

TMP="${JAR}.tmp"
rm -f "$TMP"

URLS=(
"https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar"
"https://services.gradle.org/distributions/gradle-8.9-wrapper.jar"
)

SUCCESS=0

for URL in "${URLS[@]}"; do
    echo "Trying: $URL"

    if curl \
        --fail \
        --location \
        --retry 3 \
        --connect-timeout 30 \
        "$URL" \
        -o "$TMP"
    then
        ACTUAL="$(sha256sum "$TMP" | awk '{print $1}')"

        if [ "$ACTUAL" = "$EXPECTED" ]; then
            mv "$TMP" "$JAR"
            SUCCESS=1
            break
        fi
    fi

    rm -f "$TMP"
done

if [ "$SUCCESS" -ne 1 ]; then
    echo "ERROR: Unable to restore verified Gradle 8.9 wrapper JAR."
    exit 2
fi

echo "Gradle wrapper JAR restored and VERIFIED."
