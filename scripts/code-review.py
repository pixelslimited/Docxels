#!/usr/bin/env python3
"""Pre-deploy code quality scan (dead code, unused imports, abandoned resources, duplicate helpers, oversized files).
Run on every build. Prints a report; exit code 1 only with --strict. Heuristic: a name that appears once in the
whole source tree is only its own declaration. Destructuring (componentN), reflection and manifest/XML references
are not seen by this scan, so every finding must be checked by hand before deleting."""
import collections, os, re, sys

ROOT = 'app/src/main/java'
src = {}
for d, _, fs in os.walk(ROOT):
    for f in fs:
        if f.endswith('.kt'):
            p = os.path.join(d, f)
            src[p] = open(p).read()
tests = ''
for d, _, fs in os.walk('app/src/test'):
    for f in fs:
        if f.endswith('.kt'):
            tests += open(os.path.join(d, f)).read()
allsrc = '\n'.join(src.values())
words = collections.Counter(re.findall(r'\b\w+\b', allsrc))
findings = collections.OrderedDict()

def add(k, msg):
    findings.setdefault(k, []).append(msg)

decl = re.compile(r'^\s*(?:@\w+(?:\([^)]*\))?\s*)*((?:(?:private|internal|public|protected|override|inline|suspend|data|sealed|enum|open|abstract|const|lateinit|operator|tailrec)\s+)*)(fun|class|object|val|var|interface)\s+(?:<[^>]+>\s*)?(?:[\w.]+\.)?(\w+)', re.M)
for p, s in src.items():
    rel = p.replace(ROOT + '/com/docscan/app/', '')
    for m in decl.finditer(s):
        mods, kind, name = m.groups()
        if 'override' in mods or name == 'MainActivity':
            continue
        if words[name] <= 1:
            used_in_tests = len(re.findall(r'\b' + name + r'\b', tests))
            add('Unused declarations (only their own definition exists)', f'{rel}: {kind} {name}' + (' [used by tests only]' if used_in_tests else ''))
    body = re.sub(r'^import .*$', '', s, flags=re.M)
    for m in re.finditer(r'^import ([\w.]+?)(?: as (\w+))?$', s, re.M):
        n = m.group(2) or m.group(1).split('.')[-1]
        if n not in ('getValue', 'setValue', 'provideDelegate', 'plus', 'minus', 'times', 'div', 'invoke', 'compareTo', 'contains', 'rangeTo', 'get', 'set') and n != '*' and not re.search(r'\b' + re.escape(n) + r'\b', body):
            add('Unused imports', f'{rel}: {m.group(1)}')
    if s.count('\n') > 1200:
        add('Oversized files (over 1200 lines, split)', f'{rel}: {s.count(chr(10))} lines')
    for m in re.finditer(r'^\s*//\s*(?:val|var|fun|if|for|return)\b.*$', s, re.M):
        add('Commented-out code', f'{rel}: {m.group(0).strip()[:60]}')

fun_names = collections.defaultdict(list)
for p, s in src.items():
    for m in re.finditer(r'^\s*(?:private |internal )?fun (?:<[^>]+> )?(?:[\w.]+\.)?(\w+)\(', s, re.M):
        fun_names[m.group(1)].append(p.replace(ROOT + '/com/docscan/app/', ''))
common = {'apply', 'parse', 'run', 'rotate', 'rename', 'flush', 'delete', 'build', 'add', 'Modifier', 'write', 'save', 'put', 'find', 'decode', 'read', 'open', 'file', 'draw', 'place', 'say', 'emu', 'cm', 'messageShown', 'onCreate', 'close', 'get', 'set', 'copy', 'update', 'load', 'clear', 'remove', 'toString'}
for n, where in fun_names.items():
    if len(where) > 1 and n not in common and len(n) > 4:
        add('Same helper name in several files (possible duplicate logic)', f'{n}: ' + ', '.join(where))

res_text = open('app/src/main/AndroidManifest.xml').read()
for d, _, fs in os.walk('app/src/main'):
    for f in fs:
        if f.endswith(('.kt', '.xml')):
            res_text += open(os.path.join(d, f)).read()
for d, _, fs in os.walk('app/src/main/res'):
    if not re.search(r'drawable|raw|mipmap|xml', d):
        continue
    for f in fs:
        n = f.rsplit('.', 1)[0]
        refs = len(re.findall(r'(?:R\.(?:drawable|raw|mipmap|xml)\.|@(?:drawable|raw|mipmap|xml)/)' + re.escape(n) + r'\b', res_text))
        if refs == 0:
            add('Resources nothing refers to', os.path.join(d, f))

build = open('app/build.gradle.kts').read()
for m in re.finditer(r'implementation\("([\w.\-]+):([\w.\-]+):', build):
    g, a = m.groups()
    pk = g.replace('-', '_')
    if g.startswith('androidx.compose') or a.startswith(('camera-', 'room-', 'lifecycle-', 'kotlinx-coroutines-android')):
        continue
    if a != 'kotlinx-coroutines-play-services' and g not in allsrc and pk not in allsrc and a.replace('-', '') not in allsrc.replace('-', '').lower():
        add('Dependencies with no import (check)', f'{g}:{a}')

total = 0
for k, v in findings.items():
    print(f'\n== {k} ({len(v)})')
    for x in v:
        print('  ', x)
    total += len(v)
print(f'\nTOTAL findings: {total}')
sys.exit(1 if total and '--strict' in sys.argv else 0)
