#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/gradle/wrapper/gradle-wrapper.jar"
EXPECTED="498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17"
DIST_SHA="d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab"

verify() {
  [ -f "$JAR" ] || return 1
  [ "$(sha256sum "$JAR" | awk '{print $1}')" = "$EXPECTED" ]
}

if verify; then
  echo "Gradle wrapper JAR: verified"
  exit 0
fi

command -v gradle >/dev/null || {
  echo "ERROR: Gradle is not installed" >&2
  exit 2
}

cd "$ROOT"

gradle wrapper \
  --gradle-version 8.9 \
  --distribution-type bin \
  --gradle-distribution-sha256-sum "$DIST_SHA" \
  --no-daemon

verify || {
  echo "ERROR: Gradle wrapper checksum verification failed" >&2
  exit 3
}

echo "Gradle wrapper JAR: generated and verified"
