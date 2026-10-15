#!/usr/bin/env python3
"""Tiny UI driver for the emulator check: find a node by its text / description in a uiautomator dump and tap it."""
import re, subprocess, sys, time
import xml.etree.ElementTree as ET

def adb(*a, check=True):
    return subprocess.run(["adb", *a], capture_output=True, text=True, check=check).stdout

def dump(path="/tmp/ui.xml"):
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
        adb("pull", "/sdcard/ui.xml", path, check=False)
        try:
            return ET.parse(path).getroot()
        except Exception:
            time.sleep(1)
    return None

def nodes(root):
    for n in root.iter("node"):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            yield n, ((x1 + x2) // 2, (y1 + y2) // 2), (x1, y1, x2, y2)

def find(pattern, root=None):
    root = root or dump()
    if root is None:
        return None
    rx = re.compile(pattern, re.I)
    for n, c, b in nodes(root):
        if rx.search(n.get("text", "")) or rx.search(n.get("content-desc", "")):
            return c
    return None

def tap(pattern):
    c = find(pattern)
    if not c:
        return False
    adb("shell", "input", "tap", str(c[0]), str(c[1]))
    return True

if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "tap":
        ok = tap(sys.argv[2])
        print(("tapped " if ok else "NOT FOUND ") + sys.argv[2])
        sys.exit(0 if ok else 1)
    if cmd == "texts":
        r = dump()
        for n, c, b in nodes(r):
            t = n.get("text") or n.get("content-desc")
            if t:
                print(c, repr(t))
