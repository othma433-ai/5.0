#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="/workspaces/5.0"
ZIP="$ROOT/WA-Al-Othmany-Link-Bot-v7.1.0-rc1-Runtime-Reliability-source.zip"
cd "$ROOT"
test -f "$ZIP" || { echo "Missing: $ZIP"; exit 1; }
BACKUP="$HOME/WA-LinkBot-backup-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP"
rsync -a --exclude '.git' ./ "$BACKUP/"
TMP="$(mktemp -d)"
unzip -q "$ZIP" -d "$TMP"
SRC="$(find "$TMP" -mindepth 1 -maxdepth 2 -type f -name settings.gradle.kts -printf '%h\n' | head -1)"
test -n "$SRC"
rsync -a --delete --exclude '.git' "$SRC/" "$ROOT/"
chmod +x tools/*.sh
TERM=xterm ./tools/release-audit.sh
git add -A
git diff --check
git commit -m "feat: v7.1 runtime reliability review" || git commit --allow-empty -m "ci: verify v7.1 runtime reliability review"
git push origin HEAD:main
echo "Pushed v7.1. GitHub Actions will perform Android unit tests, lint and assembleDebug."
