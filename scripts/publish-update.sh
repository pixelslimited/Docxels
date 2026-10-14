#!/usr/bin/env bash
# Usage: publish-update.sh <apk> <versionCode>   (needs gh authenticated with write access to the site repo)
# Publishes Docxels.apk and update.json as the "latest" release of the public site repo.
set -euo pipefail
APK="$1"; CODE="$2"
SITE="${SITE_REPO:-iCode010101/docxels-site}"
SHA=$(sha256sum "$APK" | cut -d' ' -f1)
SIZE=$(stat -c %s "$APK")
cat > update.json <<EOF
{"versionCode":$CODE,"versionName":"0.2.$CODE","sha256":"$SHA","size":$SIZE,"apkUrl":"https://github.com/$SITE/releases/latest/download/Docxels.apk","notes":"${NOTES:-Improvements and fixes.}"}
EOF
cp "$APK" Docxels.apk 2>/dev/null || true
gh release delete latest --repo "$SITE" --yes --cleanup-tag || true
gh release create latest Docxels.apk update.json --repo "$SITE" --title "Docxels $CODE" --notes "Build $CODE. Install from the app: Settings > App updates." --latest
