#!/usr/bin/env python3
"""Small helpers for .github/smoke/smoke.sh. Standard library only (Pillow is used when it is there).

    smoke_tools.py dump OUT.xml                  dump the screen's UI tree with uiautomator (retries)
    smoke_tools.py texts FILE.xml                list what is written on screen, with where
    smoke_tools.py has FILE.xml TEXT [TEXT...]   exit 0 when every TEXT is on screen
    smoke_tools.py tap TEXT [--topmost|--bottommost] [--timeout SEC]
                                                 find TEXT on screen and tap its centre
    smoke_tools.py seed PACKAGE                  print a SharedPreferences file that makes EyeBlink start running
                                                 at once, with PACKAGE selected and a very short interval
    smoke_tools.py ledger [--quiet]              read EyeBlink's SharedPreferences XML from stdin and print what its
                                                 usage ledger recorded; exit 0 when at least one blink was shown
    smoke_tools.py black IMAGE.png               print the share of (nearly) black pixels in a screenshot
"""
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.sax.saxutils import escape

DUMP_ON_DEVICE = "/sdcard/eyeblink-ui.xml"
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def adb(*args, timeout=60):
    return subprocess.run(["adb", *args], capture_output=True, timeout=timeout)


# ── the UI tree ───────────────────────────────────────────────────────────────────────────────────────────────────────


def dump(path, tries=8, pause=1.5):
    """uiautomator refuses while the screen is still changing, so ask again a few times."""
    for _ in range(tries):
        try:
            adb("shell", "rm", "-f", DUMP_ON_DEVICE)
            adb("shell", "uiautomator", "dump", DUMP_ON_DEVICE)
            out = adb("exec-out", "cat", DUMP_ON_DEVICE)
        except subprocess.TimeoutExpired:
            out = None
        if out is not None and out.returncode == 0 and b"<hierarchy" in out.stdout:
            Path(path).write_bytes(out.stdout)
            return True
        time.sleep(pause)
    return False


def nodes(xml_text):
    """Every node that says something: (text, (left, top, right, bottom), clickable)."""
    found = []
    for node in ET.fromstring(xml_text).iter("node"):
        bounds = BOUNDS.fullmatch(node.get("bounds", ""))
        if not bounds:
            continue
        box = tuple(int(g) for g in bounds.groups())
        for said in (node.get("text", ""), node.get("content-desc", "")):
            if said.strip():
                found.append((said.strip(), box, node.get("clickable") == "true"))
    return found


def find(xml_text, wanted, where="smallest"):
    """The box of the node that says [wanted]: an exact match first, else one that merely contains it."""
    all_nodes = nodes(xml_text)
    pool = [n for n in all_nodes if n[0] == wanted] or [n for n in all_nodes if wanted in n[0]]
    if not pool:
        return None
    if where == "topmost":
        pick = min(pool, key=lambda n: (n[1][1], n[1][0]))
    elif where == "bottommost":
        pick = max(pool, key=lambda n: (n[1][1], -n[1][0]))
    else:  # the smallest box: a label rather than the container it sits in
        pick = min(pool, key=lambda n: (n[1][2] - n[1][0]) * (n[1][3] - n[1][1]))
    return pick[1]


def centre(box):
    left, top, right, bottom = box
    return (left + right) // 2, (top + bottom) // 2


def tap(wanted, where, timeout):
    """Looks for [wanted] until [timeout] seconds have passed, and taps it."""
    deadline = time.time() + timeout
    scratch = "/tmp/eyeblink-smoke-tap.xml"
    while True:
        if dump(scratch, tries=2, pause=1.0):
            box = find(Path(scratch).read_text(encoding="utf-8", errors="replace"), wanted, where)
            if box:
                x, y = centre(box)
                adb("shell", "input", "tap", str(x), str(y))
                print(f"tapped '{wanted}' at {x},{y}")
                return True
        if time.time() >= deadline:
            print(f"'{wanted}' never appeared on screen")
            return False
        time.sleep(1.0)


# ── what to start EyeBlink with, and what it recorded ─────────────────────────────────────────────────────────────────


def seed(package):
    """The preferences file EyeBlink reads on start-up: onboarding done, one app selected, a blink every few seconds,
    and "it was running", which together with start-on-boot makes the engine wake itself without any tap.
    The settings live in seed-settings.json, which a unit test in :core decodes with the real codec."""
    text = Path(__file__).with_name("seed-settings.json").read_text(encoding="utf-8").replace("PACKAGE", package)
    settings = json.dumps(json.loads(text))
    return (
        "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
        '    <boolean name="was_running" value="true" />\n'
        f'    <string name="settings.v1">{escape(settings)}</string>\n'
        "</map>\n"
    )


def ledger(prefs_xml):
    """Totals over every day of the usage ledger, or None when there is none yet."""
    try:
        root = ET.fromstring(prefs_xml)
    except ET.ParseError:
        return None
    for entry in root.iter("string"):
        if entry.get("name") == "ledger.v1":
            days = json.loads(entry.text or "{}").get("days", {})
            totals, deferred = {}, {}
            for day in days.values():
                for key, value in day.items():
                    if key == "deferredByReason":
                        for reason, count in value.items():
                            deferred[reason] = deferred.get(reason, 0) + count
                    elif isinstance(value, int):
                        totals[key] = totals.get(key, 0) + value
            totals["deferredByReason"] = deferred
            return totals
    return None


def black_share(path):
    try:
        from PIL import Image
    except ImportError:
        return None
    image = Image.open(path).convert("RGB")
    pixels = image.getdata()
    return sum(1 for r, g, b in pixels if max(r, g, b) <= 8) / max(1, len(pixels))


# ── command line ──────────────────────────────────────────────────────────────────────────────────────────────────────


def main(argv):
    if not argv:
        sys.exit(__doc__)
    command, rest = argv[0], argv[1:]

    if command == "dump" and len(rest) == 1:
        return 0 if dump(rest[0]) else 1

    if command == "texts" and len(rest) == 1:
        for said, (left, top, right, bottom), clickable in nodes(Path(rest[0]).read_text(encoding="utf-8", errors="replace")):
            print(f"{'*' if clickable else ' '} [{left},{top}][{right},{bottom}] {said}")
        return 0

    if command == "has" and len(rest) >= 2:
        said = [n[0] for n in nodes(Path(rest[0]).read_text(encoding="utf-8", errors="replace"))]
        missing = [t for t in rest[1:] if not any(t in s for s in said)]
        if missing:
            print("not on screen: " + ", ".join(missing))
        return 1 if missing else 0

    if command == "tap" and rest:
        where, timeout, wanted = "smallest", 15.0, rest[0]
        flags = rest[1:]
        while flags:
            flag = flags.pop(0)
            if flag in ("--topmost", "--bottommost"):
                where = flag[2:]
            elif flag == "--timeout" and flags:
                timeout = float(flags.pop(0))
            else:
                sys.exit(f"unknown option {flag}")
        return 0 if tap(wanted, where, timeout) else 1

    if command == "seed" and len(rest) == 1:
        sys.stdout.write(seed(rest[0]))
        return 0

    if command == "ledger":
        totals = ledger(sys.stdin.read())
        if totals is None:
            if "--quiet" not in rest:
                print("no ledger written yet")
            return 1
        if "--quiet" not in rest:
            print(json.dumps(totals, sort_keys=True))
        return 0 if totals.get("microShown", 0) >= 1 else 1

    if command == "black" and len(rest) == 1:
        share = black_share(rest[0])
        print("n/a" if share is None else f"{share:.3f}")
        return 0

    sys.exit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
