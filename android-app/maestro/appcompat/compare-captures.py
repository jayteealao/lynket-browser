#!/usr/bin/env python3
"""Compares two capture passes of window-check.sh, screen by screen.

Usage: python maestro/appcompat/compare-captures.py <before-dir> <after-dir> <report.md>

Pairs every *.png under the two folders by relative path. For each pair:
  - the UI dumps (<same name>.xml, when both exist): every node of the app (package
    arun.com.chromer*) by class, resource id, content description, text and bounds; text that
    reads as a clock or a relative time is left out;
  - the pixels: a pixel differs when one channel differs by more than 8. The status bar (down to
    the lowest System UI node that starts at the top, else the top 3 %) and the nodes whose text
    reads as a time are left out.
Result per pair: same (no pixel differs), noise (at most 0.05 % of the pixels differ, all inside
a text field, where the caret blinks) or DIFFERENT (a red-overlay diff image is written next to
the report). A missing partner or a different UI tree is also DIFFERENT. Exit 1 on any DIFFERENT.
Needs Pillow.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageChops

APP = re.compile(r"^arun\.com\.chromer")
TIME = re.compile(
    r"\b\d{1,2}:\d{2}\b|\bago\b|\bjust now\b|\btoday\b|\byesterday\b|\b\d+\s*(min|mins|minutes|h|hours|d|days)\b",
    re.IGNORECASE,
)
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")
THRESHOLD = 8
NOISE_SHARE = 0.0005


def nodes(xml_path):
    try:
        root = ET.parse(xml_path).getroot()
    except (ET.ParseError, FileNotFoundError):
        return None
    return [n.attrib for n in root.iter("node")]


def box(attrib):
    m = BOUNDS.match(attrib.get("bounds", ""))
    return tuple(int(v) for v in m.groups()) if m else None


def app_tree(all_nodes):
    tree = []
    for n in all_nodes:
        if not APP.match(n.get("package", "")):
            continue
        text = n.get("text", "")
        if TIME.search(text):
            text = "<time>"
        tree.append((n.get("class"), n.get("resource-id"), n.get("content-desc"), text, n.get("bounds")))
    return tree


def masks(all_nodes, width, height):
    """Rectangles left out of the pixel compare, and the text field rectangles."""
    status_bottom = 0
    fields, out = [], []
    for n in all_nodes or []:
        b = box(n)
        if not b:
            continue
        if n.get("package") == "com.android.systemui" and b[1] == 0 and 0 < b[3] < height * 0.1:
            status_bottom = max(status_bottom, b[3])
        if TIME.search(n.get("text", "")):
            out.append(b)
        if n.get("class", "").endswith("EditText"):
            fields.append(b)
    out.append((0, 0, width, status_bottom or int(height * 0.03)))
    return out, fields


def inside(x, y, rects):
    return any(r[0] <= x < r[2] and r[1] <= y < r[3] for r in rects)


def compare(before_png, after_png, diff_png):
    a = Image.open(before_png).convert("RGB")
    b = Image.open(after_png).convert("RGB")
    if a.size != b.size:
        return "DIFFERENT", f"size {a.size} vs {b.size}"
    width, height = a.size
    before_nodes = nodes(before_png.with_suffix(".xml"))
    after_nodes = nodes(after_png.with_suffix(".xml"))
    note = ""
    tree_differs = False
    if before_nodes is not None and after_nodes is not None:
        ta, tb = app_tree(before_nodes), app_tree(after_nodes)
        if ta != tb:
            tree_differs = True
            first = next((i for i in range(max(len(ta), len(tb))) if (ta[i:i + 1] or [None]) != (tb[i:i + 1] or [None])), None)
            note = f"UI tree differs ({len(ta)} vs {len(tb)} app nodes; first at {first}: {ta[first] if first is not None and first < len(ta) else None} vs {tb[first] if first is not None and first < len(tb) else None})"
    mask_rects, fields = masks((before_nodes or []) + (after_nodes or []), width, height)
    channels = ImageChops.difference(a, b).point(lambda v: 255 if v > THRESHOLD else 0).split()
    hits = ImageChops.lighter(ImageChops.lighter(channels[0], channels[1]), channels[2])
    for r in mask_rects:
        hits.paste(0, (max(r[0], 0), max(r[1], 0), min(r[2], width), min(r[3], height)))
    changed = []
    bbox = hits.getbbox()
    if bbox:
        px = hits.load()
        changed = [(x, y) for y in range(bbox[1], bbox[3]) for x in range(bbox[0], bbox[2]) if px[x, y]]
    share = len(changed) / (width * height)
    if not changed and not tree_differs:
        return "same", note
    if changed:
        note = (note + "; " if note else "") + f"{len(changed)} pixels ({share:.4%})"
    if not tree_differs and share <= NOISE_SHARE and all(inside(x, y, fields) for x, y in changed):
        return "noise", note + " inside a text field"
    overlay = b.copy()
    px = overlay.load()
    for x, y in changed:
        px[x, y] = (255, 0, 0)
    overlay.save(diff_png)
    return "DIFFERENT", note


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    before, after, report = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    diffs = report.parent / (report.stem + "-diffs")
    diffs.mkdir(parents=True, exist_ok=True)
    names = sorted({p.relative_to(before).as_posix() for p in before.rglob("*.png")} |
                   {p.relative_to(after).as_posix() for p in after.rglob("*.png")})
    rows, failed = [], 0
    for name in names:
        pa, pb = before / name, after / name
        if not pa.exists() or not pb.exists():
            result, note = "DIFFERENT", "missing in " + ("before" if not pa.exists() else "after")
        else:
            result, note = compare(pa, pb, diffs / name.replace("/", "__"))
        failed += result == "DIFFERENT"
        rows.append(f"| {name} | {result} | {note} |")
    counts = {r: sum(1 for row in rows if f"| {r} |" in row) for r in ("same", "noise", "DIFFERENT")}
    report.write_text(
        f"# Capture compare\n\nBefore: `{before}`\nAfter: `{after}`\n\n"
        f"{len(names)} screens: {counts['same']} same, {counts['noise']} noise, {counts['DIFFERENT']} different\n\n"
        "| Screen | Result | Note |\n|---|---|---|\n" + "\n".join(rows) + "\n",
        encoding="utf-8",
    )
    print(f"{len(names)} screens: {counts['same']} same, {counts['noise']} noise, {counts['DIFFERENT']} different")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
