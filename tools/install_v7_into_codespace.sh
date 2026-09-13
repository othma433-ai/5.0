#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="/workspaces/5.0"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
REPO="othma433-ai/5.0"
BRANCH="main"

if [ ! -d "$ROOT/.git" ]; then
  echo "ERROR: $ROOT is not the Codespace repository"
  exit 1
fi

BACKUP="$ROOT/.v7-backup-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP"
git -C "$ROOT" status --porcelain -uall > "$BACKUP/status.txt"
git -C "$ROOT" diff > "$BACKUP/uncommitted.patch" || true

rsync -a --delete \
  --exclude='.git/' \
  --exclude='APK_OUTPUT*/' \
  "$HERE/" "$ROOT/"

cd "$ROOT"
chmod +x tools/verify-core.sh tools/release-audit.sh
TERM=xterm ./tools/release-audit.sh

git diff --check
git add -A
if ! git diff --cached --quiet; then
  git commit -m "release: install v7 verified runtime"
else
  git commit --allow-empty -m "ci: build v7 verified runtime"
fi
git push origin "$BRANCH"
SHA="$(git rev-parse HEAD)"
echo "Pushed commit: $SHA"
echo "Open Actions: https://github.com/$REPO/actions"
