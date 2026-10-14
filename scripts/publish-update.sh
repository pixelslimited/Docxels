#!/usr/bin/env bash
# Usage: publish-update.sh <apk> <versionCode>   (needs gh authenticated with write access to the site repo)
# Publishes Docxels.apk, update.json and (when possible) a small patch from the previous build as the "latest"
# release of the public site repo.
set -euo pipefail
APK="$1"; CODE="$2"
SITE="${SITE_REPO:-iCode010101/docxels-site}"
BASE="https://github.com/$SITE/releases/latest/download"
SHA=$(sha256sum "$APK" | cut -d' ' -f1)
SIZE=$(stat -c %s "$APK")

# Try to build a patch from the build that is currently published.
PATCH_JSON=""
PATCH_FILE=""
WORK=$(mktemp -d)
if curl -fsSL "$BASE/update.json" -o "$WORK/prev.json" && curl -fsSL "$BASE/Docxels.apk" -o "$WORK/prev.apk"; then
  PREV=$(python3 -c "import json;print(json.load(open('$WORK/prev.json'))['versionCode'])" || echo 0)
  PREV_SHA=$(python3 -c "import json;print(json.load(open('$WORK/prev.json'))['sha256'])" || echo x)
  if [ "$PREV" != "0" ] && [ "$PREV" != "$CODE" ] && [ "$(sha256sum "$WORK/prev.apk" | cut -d' ' -f1)" = "$PREV_SHA" ]; then
    python3 -m pip install --quiet --user bsdiff4 || true
    PATCH_FILE="Docxels-$PREV-to-$CODE.patch"
    if python3 scripts/make-patch.py "$WORK/prev.apk" "$APK" "$PATCH_FILE"; then
      PSHA=$(sha256sum "$PATCH_FILE" | cut -d' ' -f1)
      PSIZE=$(stat -c %s "$PATCH_FILE")
      PATCH_JSON=",\"patch\":{\"fromVersionCode\":$PREV,\"url\":\"$BASE/$PATCH_FILE\",\"sha256\":\"$PSHA\",\"size\":$PSIZE}"
    else
      echo "Patch could not be built; publishing the full update only."
      PATCH_FILE=""
    fi
  fi
fi

cat > update.json <<JSON
{"versionCode":$CODE,"versionName":"0.2.$CODE","sha256":"$SHA","size":$SIZE,"apkUrl":"$BASE/Docxels.apk","notes":"${NOTES:-Improvements and fixes.}"$PATCH_JSON}
JSON
cp "$APK" Docxels.apk 2>/dev/null || true
gh release delete latest --repo "$SITE" --yes --cleanup-tag || true
ASSETS=(Docxels.apk update.json)
[ -n "$PATCH_FILE" ] && ASSETS+=("$PATCH_FILE")
gh release create latest "${ASSETS[@]}" --repo "$SITE" --title "Docxels $CODE" --notes "Build $CODE. Install from the app: Settings > App updates." --latest
