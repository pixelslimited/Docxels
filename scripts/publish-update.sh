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
# What's new: the whole RELEASE_NOTES.txt (lines "build|text"); the app shows the lines newer than the installed build.
NOTES_FILE="$(dirname "$0")/../RELEASE_NOTES.txt"
if [ -f "$NOTES_FILE" ]; then NOTES_JSON=$(tail -n 40 "$NOTES_FILE" | jq -Rs .); else NOTES_JSON='"Improvements and fixes."'; fi

# Patches: one from each of the last few builds, so a phone that skipped a build still gets a small download.
KEEP=5
PATCH_FILES=()
PATCH_ITEMS=()
WORK=$(mktemp -d)
mkdir -p "$WORK/hist"
gh release download history --repo "$SITE" --dir "$WORK/hist" --pattern 'Docxels-*.apk' 2>/dev/null || true
# The currently published build also counts, in case the history release does not have it yet.
if curl -fsSL "$BASE/update.json" -o "$WORK/prev.json" 2>/dev/null; then
  PREV=$(python3 -c "import json;print(json.load(open('$WORK/prev.json'))['versionCode'])" || echo 0)
  if [ "$PREV" != "0" ] && [ ! -f "$WORK/hist/Docxels-$PREV.apk" ]; then
    curl -fsSL "$BASE/Docxels.apk" -o "$WORK/hist/Docxels-$PREV.apk" || rm -f "$WORK/hist/Docxels-$PREV.apk"
  fi
fi
python3 -m pip install --quiet --user bsdiff4 || true
for OLD in $(ls "$WORK/hist"/Docxels-*.apk 2>/dev/null | sort -t- -k2 -n | tail -n "$KEEP"); do
  FROM=$(basename "$OLD" .apk | sed 's/Docxels-//')
  [ "$FROM" = "$CODE" ] && continue
  PF="Docxels-$FROM-to-$CODE.patch"
  if python3 scripts/make-patch.py "$OLD" "$APK" "$PF"; then
    PSHA=$(sha256sum "$PF" | cut -d' ' -f1)
    PSIZE=$(stat -c %s "$PF")
    PATCH_FILES+=("$PF")
    PATCH_ITEMS+=("{\"fromVersionCode\":$FROM,\"url\":\"$BASE/$PF\",\"sha256\":\"$PSHA\",\"size\":$PSIZE}")
  else
    echo "Patch from $FROM could not be built."
  fi
done
PATCH_JSON=""
if [ "${#PATCH_ITEMS[@]}" -gt 0 ]; then
  PATCH_JSON=",\"patches\":[$(IFS=,; echo "${PATCH_ITEMS[*]}")]"
fi

cat > update.json <<JSON
{"versionCode":$CODE,"versionName":"0.2.$CODE","sha256":"$SHA","size":$SIZE,"apkUrl":"$BASE/Docxels.apk","notes":$NOTES_JSON$PATCH_JSON}
JSON
cp "$APK" Docxels.apk 2>/dev/null || true
gh release delete latest --repo "$SITE" --yes --cleanup-tag || true
ASSETS=(Docxels.apk update.json "${PATCH_FILES[@]}")
gh release create latest "${ASSETS[@]}" --repo "$SITE" --title "Docxels $CODE" --notes "Build $CODE. Install from the app: Settings > App updates." --latest
# GitHub sometimes leaves a new release as a hidden draft when it is made right after the old one was deleted; the
# app could not then find update.json. Make sure the release is public, and fail loudly if it is not.
for i in 1 2 3 4 5; do
  DRAFT=$(gh release view latest --repo "$SITE" --json isDraft --jq .isDraft)
  [ "$DRAFT" = "false" ] && break
  gh release edit latest --repo "$SITE" --draft=false --latest || true
  sleep 5
done

# Keep this build (and the last few) so the next release can make patches from them.
cp "$APK" "Docxels-$CODE.apk"
gh release view history --repo "$SITE" >/dev/null 2>&1 || gh release create history --repo "$SITE" --title "Build history" --notes "Older builds, used only to make small update patches." --latest=false
gh release upload history "Docxels-$CODE.apk" --repo "$SITE" --clobber
for OLDNAME in $(gh release view history --repo "$SITE" --json assets --jq '.assets[].name' | sort -t- -k2 -n | head -n -"$KEEP"); do
  gh release delete-asset history "$OLDNAME" --repo "$SITE" --yes || true
done

# Last check: the public update file must be reachable (GitHub's download address can take a little while to appear).
for i in $(seq 1 12); do
  if curl -fsSL "$BASE/update.json" | grep -q "\"versionCode\":$CODE"; then echo "update.json for build $CODE is live"; exit 0; fi
  sleep 5
done
echo "update.json for build $CODE is not reachable at $BASE" >&2
exit 1
