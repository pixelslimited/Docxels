#!/usr/bin/env bash
# Usage: publish-update.sh <apk> <versionCode>   (needs gh authenticated with write access to the site repo)
# Publishes Docxels.apk, update.json and (when possible) a small patch from the previous build as the "latest"
# release of the public site repo.
set -euo pipefail
# The run counter restarted when the repo moved to the Pixels Limited account; +200 keeps codes above the old repo's.
APK="$1"; CODE=$(( $2 + 200 ))
SITE="${SITE_REPO:-iCode010101/docxels-site}"
BASE="https://github.com/$SITE/releases/latest/download"
SHA=$(sha256sum "$APK" | cut -d' ' -f1)
SIZE=$(stat -c %s "$APK")
# What's new: the stamped list the build already put inside the app (lines "build|TIER|Title|Sentence"). The app shows
# the lines newer than the installed build as cards. Falls back to stamping RELEASE_NOTES.txt here if it is missing.
STAMPED="$(dirname "$0")/../app/src/main/assets/whatsnew.txt"
if [ ! -f "$STAMPED" ]; then python3 "$(dirname "$0")/stamp-notes.py" "$(dirname "$0")/../RELEASE_NOTES.txt" "$CODE" "$STAMPED" "$BASE/update.json" || true; fi
if [ -f "$STAMPED" ]; then NOTES_JSON=$(jq -Rs . < "$STAMPED"); else NOTES_JSON='"Improvements and fixes."'; fi

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

# One-off site tidy: the draft notice was removed from the privacy and terms pages. Does nothing once the pages are clean.
(
  T=$(mktemp -d)
  git clone --depth 1 "https://x-access-token:$(gh auth token)@github.com/$SITE" "$T/site" >/dev/null 2>&1 || exit 0
  cd "$T/site"
  if grep -ql 'class="notice">Draft text for review' privacy.html terms.html 2>/dev/null; then
    sed -i '/<div class="notice">Draft text for review/d' privacy.html terms.html
    git -c user.name="Kelvin Codes" -c user.email="noreply@pixelslimited.ng" commit -qam "Remove draft notice from privacy and terms" && git push -q origin HEAD || true
  fi
) || true

# Site pages kept in this repo under site-extra/ (the admin dashboard) are copied into the site repo and its cPanel deploy list.
if [ -d site-extra/dashboard ]; then
  T=$(mktemp -d)
  TOK="${GH_TOKEN:-$(gh auth token 2>/dev/null || true)}"
  TOK=$(printf %s "$TOK" | tr -d '[:space:]') # a stray newline in the secret made git reject the URL as malformed
  [ -n "$TOK" ] || { echo "Site sync: no token available" >&2; exit 1; }
  git clone --depth 1 "https://x-access-token:$TOK@github.com/$SITE" "$T/site" 2>&1 | sed "s/$TOK/***/g" || true
  [ -d "$T/site/.git" ] || { echo "Site sync: clone of $SITE failed (output above)" >&2; exit 1; }
  mkdir -p "$T/site/dashboard" && cp site-extra/dashboard/* "$T/site/dashboard/"
  grep -q 'dashboard' "$T/site/.cpanel.yml" 2>/dev/null || sed -i 's#^    - /bin/cp -R index.html#    - mkdir -p $DEPLOYPATH/dashboard \&\& /bin/cp -R dashboard/* $DEPLOYPATH/dashboard/\n    - /bin/cp -R index.html#' "$T/site/.cpanel.yml"
  # Docxels logo (the pixel "D"): icons, the site header mark and the cPanel copy list.
  cp site-extra/assets/*.png "$T/site/"
  python3 - "$T/site" <<'PY'
import re, sys, pathlib
d = pathlib.Path(sys.argv[1])
for f in ("index.html", "privacy.html", "terms.html"):
    p = d / f
    if p.exists():
        t = p.read_text()
        t2 = re.sub(r'<span class="logo"><svg.*?</svg></span>', '<span class="logo"><img src="icon-512.png" alt="" width="36" height="36"></span>', t, flags=re.S)
        if t2 != t: p.write_text(t2)
c = d / "styles.css"
if c.exists() and "logo img" not in c.read_text():
    c.write_text(c.read_text().rstrip() + "\n.logo{background:none}.logo img{width:100%;height:100%;border-radius:12px;display:block}\n")
y = d / ".cpanel.yml"
if y.exists():
    t = y.read_text()
    if "icon-512.png" not in t:
        y.write_text(t.replace("index.html privacy.html", "favicon.png icon-512.png apple-touch-icon.png index.html privacy.html"))
PY
  git -C "$T/site" add -A
  if ! git -C "$T/site" diff --cached --quiet; then
    git -C "$T/site" -c user.name="Kelvin Codes" -c user.email="docxels@pixelslimited.ng" commit -qm "Site: admin dashboard" || { echo "Site sync: commit failed" >&2; exit 1; }
    git -C "$T/site" push origin HEAD 2>&1 | sed "s/$TOK/***/g"
    [ "${PIPESTATUS[0]}" = 0 ] || { echo "Site sync: push to $SITE refused (output above)" >&2; exit 1; }
  fi
fi

# Last check: the public update file must be reachable (GitHub's download address can take a little while to appear).
for i in $(seq 1 12); do
  if curl -fsSL "$BASE/update.json" | grep -q "\"versionCode\":$CODE"; then echo "update.json for build $CODE is live"; exit 0; fi
  sleep 5
done
echo "update.json for build $CODE is not reachable at $BASE" >&2
exit 1
