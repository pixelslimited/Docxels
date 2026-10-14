#!/usr/bin/env python3
"""Builds a Docxels delta patch ("DXPATCH1") that turns OLD.apk into NEW.apk.

Usage: make-patch.py OLD NEW OUT
Uses bsdiff (pip install bsdiff4) and repacks its three bzip2 parts as deflate, which the app can read without
extra libraries. The app verifies the rebuilt APK against the published SHA-256 before installing it.
"""
import bz2
import struct
import sys
import zlib

import bsdiff4

old = open(sys.argv[1], "rb").read()
new = open(sys.argv[2], "rb").read()
raw = bsdiff4.diff(old, new)
assert raw[:8] == b"BSDIFF40", "unexpected bsdiff header"
ctrl_len, diff_len, new_size = struct.unpack("<qqq", raw[8:32])
ctrl = bz2.decompress(raw[32:32 + ctrl_len])
diff = bz2.decompress(raw[32 + ctrl_len:32 + ctrl_len + diff_len])
extra = bz2.decompress(raw[32 + ctrl_len + diff_len:])
assert new_size == len(new)
parts = [zlib.compress(p, 9) for p in (ctrl, diff, extra)]
header = b"DXPATCH1" + struct.pack(
    "<qqqqqqq", new_size, len(ctrl), len(diff), len(extra), len(parts[0]), len(parts[1]), len(parts[2])
)
open(sys.argv[3], "wb").write(header + b"".join(parts))
print("patch bytes:", 64 + sum(len(p) for p in parts), "of", len(new))
