#!/usr/bin/env python3
"""Turns RELEASE_NOTES.txt into the stamped what's-new list that ships inside the app and in update.json.

Usage: stamp-notes.py <RELEASE_NOTES.txt> <build code> <out> [<url of the previous update.json>]
Lines are "build|TIER|Title|Sentence". A line written "*|..." is new: it gets this build's code, unless the previous
update.json already had the same line, in which case it keeps the code it got then. Only the newest three builds are kept.
"""
import json
import re
import sys
import urllib.request

notes_file, code, out = sys.argv[1], sys.argv[2], sys.argv[3]
prev = {}
if len(sys.argv) > 4:
    try:
        with urllib.request.urlopen(sys.argv[4], timeout=20) as r:
            for line in json.load(r).get("notes", "").splitlines():
                m = re.match(r"(\d+)\|(.*)", line)
                if m:
                    prev[m.group(2).strip()] = m.group(1)
    except Exception as e:  # first publish, offline, or a malformed file: new lines simply get this build's code
        print("previous notes not read:", e, file=sys.stderr)

rows = []
for raw in open(notes_file, encoding="utf-8"):
    line = raw.strip()
    if not line or line.startswith("#"):
        continue
    m = re.match(r"(\d+|\*)\|(.*)", line)
    if not m or m.group(2).count("|") < 2:
        print("skipped malformed line:", line, file=sys.stderr)
        continue
    body = m.group(2).strip()
    rows.append((int(prev.get(body, code)) if m.group(1) == "*" else int(m.group(1)), body))

keep = sorted({c for c, _ in rows}, reverse=True)[:3]
with open(out, "w", encoding="utf-8") as f:
    for c, body in rows:
        if c in keep:
            f.write(f"{c}|{body}\n")
