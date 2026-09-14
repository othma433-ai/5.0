#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/gradle/wrapper/gradle-wrapper.jar"
URL="https://services.gradle.org/distributions/gradle-8.9-wrapper.jar"
EXPECTED="498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17"

verify() {
  [ -f "$JAR" ] || return 1
  local actual
  actual="$(sha256sum "$JAR" | awk '{print $1}')"
  [ "$actual" = "$EXPECTED" ]
}

if verify; then
  echo "Gradle wrapper JAR: verified"
  exit 0
fi

mkdir -p "$(dirname "$JAR")"
tmp="${JAR}.tmp"
rm -f "$tmp"
trap 'rm -f "$tmp"' EXIT
if command -v curl >/dev/null 2>&1; then
  curl --fail --location --silent --show-error --retry 3 --connect-timeout 20 "$URL" -o "$tmp"
elif command -v wget >/dev/null 2>&1; then
  wget -q --tries=3 --timeout=20 "$URL" -O "$tmp"
else
  echo "ERROR: curl or wget is required to bootstrap the verified Gradle wrapper JAR." >&2
  exit 2
fi
actual="$(sha256sum "$tmp" | awk '{print $1}')"
if [ "$actual" != "$EXPECTED" ]; then
  rm -f "$tmp"
  echo "ERROR: Gradle wrapper JAR checksum mismatch: $actual" >&2
  exit 3
fi
mv "$tmp" "$JAR"
trap - EXIT
echo "Gradle wrapper JAR: downloaded and verified"
