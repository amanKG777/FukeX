#!/usr/bin/env python3
"""Builds iconpacks/visual-icons.fxicons, iconpacks/index.json and a preview sheet.

Icons are drawn on a 24x24 grid in a rounded duotone line style: a soft tinted
fill layer under a round-capped outline. Only absolute SVG path commands
(M L H V A C Q Z) are used, which is all the in-app parser has to understand.

    python iconpacks/tools/build_pack.py          # writes pack + index
    python iconpacks/tools/build_pack.py --preview # also writes preview.png (needs Pillow)
"""
import hashlib
import json
import math
import os
import sys

PACK_ID = "visual-icons"
PACK_NAME = "Visual Icons"
PACK_VERSION = 1
STROKE = 1.75
SOFT = 0.28


def n(v):
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def rect(x, y, w, h, r=0):
    if r == 0:
        return "M%s %s H%s V%s H%s Z" % (n(x), n(y), n(x + w), n(y + h), n(x))
    return (
        "M%s %s H%s A%s %s 0 0 1 %s %s V%s A%s %s 0 0 1 %s %s H%s A%s %s 0 0 1 %s %s V%s A%s %s 0 0 1 %s %s Z"
        % (n(x + r), n(y), n(x + w - r), n(r), n(r), n(x + w), n(y + r), n(y + h - r),
           n(r), n(r), n(x + w - r), n(y + h), n(x + r), n(r), n(r), n(x), n(y + h - r),
           n(y + r), n(r), n(r), n(x + r), n(y))
    )


def circle(cx, cy, r):
    return "M%s %s A%s %s 0 1 1 %s %s A%s %s 0 1 1 %s %s Z" % (
        n(cx - r), n(cy), n(r), n(r), n(cx + r), n(cy), n(r), n(r), n(cx - r), n(cy))


def poly(points, close=False):
    d = "M%s %s" % (n(points[0][0]), n(points[0][1]))
    for x, y in points[1:]:
        d += " L%s %s" % (n(x), n(y))
    return d + (" Z" if close else "")


def line(x1, y1, x2, y2):
    return poly([(x1, y1), (x2, y2)])


# A layer is (path, kind, alpha); kind is "fill" or "stroke".
def soft(d):
    return (d, "fill", SOFT)


def solid(d):
    return (d, "fill", 1.0)


def outline(d, alpha=1.0):
    return (d, "stroke", alpha)


def shape(d):
    """Soft tinted fill with an outline: the signature look."""
    return [soft(d), outline(d)]


def bold(d):
    """Solid fill plus a round outline, so corners come out softly rounded."""
    return [solid(d), outline(d)]


ICONS = {}
MIRROR = set()


def add(name, layers, mirror=False):
    ICONS[name] = layers
    if mirror:
        MIRROR.add(name)


# ---- transport -------------------------------------------------------------
add("play", bold(poly([(8, 5.5), (8, 18.5), (18.8, 12)], True)))
add("pause", bold(rect(6.5, 5, 3.6, 14, 1.2)) + bold(rect(13.9, 5, 3.6, 14, 1.2)))
add("skip_next", bold(poly([(5.5, 5.5), (5.5, 18.5), (15.5, 12)], True)) + [outline(line(18.5, 5.5, 18.5, 18.5))])
add("skip_previous", bold(poly([(18.5, 5.5), (18.5, 18.5), (8.5, 12)], True)) + [outline(line(5.5, 5.5, 5.5, 18.5))])
add("fast_forward", bold(poly([(3.5, 6.5), (3.5, 17.5), (11, 12)], True)) + bold(poly([(12.5, 6.5), (12.5, 17.5), (20.3, 12)], True)))
add("play_circle", shape(circle(12, 12, 9)) + bold(poly([(10, 8.6), (10, 15.4), (15.6, 12)], True)))

# ---- actions -----------------------------------------------------------------
add("more", [solid(circle(12, 5.5, 1.6)), solid(circle(12, 12, 1.6)), solid(circle(12, 18.5, 1.6))])
add("add", [outline(line(12, 5, 12, 19)), outline(line(5, 12, 19, 12))])
add("close", [outline(line(6, 6, 18, 18)), outline(line(18, 6, 6, 18))])
add("search", shape(circle(10.5, 10.5, 6.3)) + [outline(line(15.2, 15.2, 19.8, 19.8))])
add("delete", shape(rect(6.5, 7, 11, 12.5, 2.2)) + [
    outline(line(4.5, 7, 19.5, 7)), outline(poly([(9.2, 7), (9.2, 4.5), (14.8, 4.5), (14.8, 7)])),
    outline(line(10.2, 11, 10.2, 15.8)), outline(line(13.8, 11, 13.8, 15.8))])
add("dropdown", [outline(poly([(7, 10), (12, 15), (17, 10)]))])
add("back", [outline(line(19, 12, 5, 12)), outline(poly([(11, 6), (5, 12), (11, 18)]))], mirror=True)
add("forward", [outline(line(5, 12, 19, 12)), outline(poly([(13, 6), (19, 12), (13, 18)]))], mirror=True)
add("file_upload", [outline(line(12, 15, 12, 4.5)), outline(poly([(8, 8.5), (12, 4.5), (16, 8.5)])),
                    outline(poly([(4.5, 14), (4.5, 19), (19.5, 19), (19.5, 14)]))])
add("file_download", [outline(line(12, 4.5, 12, 15)), outline(poly([(8, 11), (12, 15), (16, 11)])),
                      outline(poly([(4.5, 14), (4.5, 19), (19.5, 19), (19.5, 14)]))])
add("cloud_download", shape(
    "M7 18.5 A4.4 4.4 0 0 1 6.4 9.8 A5.9 5.9 0 0 1 17.7 10 A4.3 4.3 0 0 1 17.2 18.5 Z") + [
    outline(line(12, 11.5, 12, 19.5)), outline(poly([(9, 16.5), (12, 19.5), (15, 16.5)]))])

# ---- settings & status -------------------------------------------------------
add("settings", [outline(line(4, 7, 6.3, 7)), outline(line(11.7, 7, 20, 7)), outline(line(4, 17, 12.3, 17)), outline(line(17.7, 17, 20, 17))]
    + shape(circle(9, 7, 2.7)) + shape(circle(15, 17, 2.7)))
add("info", shape(circle(12, 12, 9)) + [outline(line(12, 11, 12, 16.4)), solid(circle(12, 7.9, 1.05))])
add("lock", shape(rect(5, 10.5, 14, 10, 2.5)) + [outline("M8 10.5 V8 A4 4 0 0 1 16 8 V10.5"), solid(circle(12, 15.5, 1.3))])
add("lock_open", shape(rect(5, 10.5, 14, 10, 2.5)) + [outline("M8 10.5 V8 A4 4 0 0 1 15 4.9"), solid(circle(12, 15.5, 1.3))])
add("lock_clock", shape(circle(12, 12, 9)) + [outline(poly([(12, 7), (12, 12), (15.6, 14.2)]))])
add("fingerprint", [
    outline("M5.2 9.2 A7.6 7.6 0 0 1 18.8 9.2"),
    outline("M7.2 20 C5.8 17.6 5.5 14.4 5.6 12"),
    outline("M18.6 12.2 C18.6 15 18.2 17.6 16.8 20"),
    outline("M8.6 12 A3.4 3.4 0 0 1 15.4 12 C15.4 14.8 15 17 14 19.2"),
    outline("M12 11.8 C12 14.6 11.7 16.6 10.8 18.4"),
    outline("M8.7 16.6 C8.4 15.4 8.4 13.8 8.6 12"),
])
add("mic", shape(rect(9, 3.5, 6, 11.5, 3)) + [outline("M5.5 11.5 A6.5 6.5 0 0 0 18.5 11.5"), outline(line(12, 18, 12, 21)),
                                                outline(line(8.5, 21, 15.5, 21))])
add("voice", shape(rect(9, 4, 6, 10, 3)) + [outline("M6.5 11.2 A5.5 5.5 0 0 0 17.5 11.2"), outline(line(12, 16.7, 12, 20)),
                                             outline(line(3.4, 9, 3.4, 13)), outline(line(20.6, 9, 20.6, 13))])

# ---- files & music -----------------------------------------------------------
DOC = "M7 3.5 H13.5 L18.5 8.5 V19 A1.5 1.5 0 0 1 17 20.5 H7 A1.5 1.5 0 0 1 5.5 19 V5 A1.5 1.5 0 0 1 7 3.5 Z"
FOLD = poly([(13.5, 3.5), (13.5, 8.5), (18.5, 8.5)])
add("folder", shape("M3.5 7.5 A2 2 0 0 1 5.5 5.5 H9.5 L12 8 H18.5 A2 2 0 0 1 20.5 10 V17.5 A2 2 0 0 1 18.5 19.5 H5.5 A2 2 0 0 1 3.5 17.5 Z"))
add("file", shape(DOC) + [outline(FOLD)])
add("audio_file", shape(DOC) + [outline(FOLD), solid(circle(10.3, 16.2, 1.7)),
                                outline(line(12, 16.2, 12, 11.6)), outline(poly([(12, 11.6), (14.6, 12.6)]))])
add("music_note", [solid(circle(8, 17.5, 2.9)), outline(circle(8, 17.5, 2.9)), outline(line(10.9, 17.5, 10.9, 5.5)),
                   outline("M10.9 5.5 L18.5 4 V9.2 L10.9 10.8")])
add("library_music", shape(rect(7.5, 3.5, 13, 13, 2.5)) + [
    outline("M3.5 7.5 V18 A2.5 2.5 0 0 0 6 20.5 H16.5"), solid(circle(12.6, 13.2, 1.7)),
    outline(line(14.3, 13.2, 14.3, 7.6)), outline(poly([(14.3, 7.6), (17.4, 8.6)]))])
add("queue_music", [outline(line(4, 6.5, 14, 6.5)), outline(line(4, 11.5, 14, 11.5)), outline(line(4, 16.5, 9.5, 16.5)),
                    solid(circle(16.2, 17, 2.4)), outline(line(18.6, 17, 18.6, 8.5)), outline(poly([(18.6, 8.5), (21, 9.8)]))], mirror=True)
add("playlist_add", [outline(line(4, 6.5, 14, 6.5)), outline(line(4, 11.5, 14, 11.5)), outline(line(4, 16.5, 10, 16.5)),
                     outline(line(17.5, 12.5, 17.5, 20.5)), outline(line(13.5, 16.5, 21.5, 16.5))], mirror=True)
add("album", shape(circle(12, 12, 9)) + [outline(circle(12, 12, 5.2), 0.45), solid(circle(12, 12, 1.8))])
add("person", shape(circle(12, 8, 3.8)) + shape("M4.6 20.2 A7.4 7 0 0 1 19.4 20.2"))
add("category", shape(rect(4, 4, 6.8, 6.8, 2)) + shape(rect(13.2, 4, 6.8, 6.8, 2)) +
    shape(rect(4, 13.2, 6.8, 6.8, 2)) + shape(rect(13.2, 13.2, 6.8, 6.8, 2)))
add("equalizer", bold(rect(5, 11, 3, 8.5, 1.2)) + bold(rect(10.5, 4.5, 3, 15, 1.2)) + bold(rect(16, 8, 3, 11.5, 1.2)))
add("graphic_eq", [outline(line(4, 10, 4, 14)), outline(line(8, 6, 8, 18)), outline(line(12, 3.5, 12, 20.5)),
                   outline(line(16, 7, 16, 17)), outline(line(20, 10, 20, 14))])
add("lyrics", shape("M6.5 4 H17.5 A3 3 0 0 1 20.5 7 V14 A3 3 0 0 1 17.5 17 H11.5 L7 20.5 V17 H6.5 A3 3 0 0 1 3.5 14 V7 A3 3 0 0 1 6.5 4 Z") + [
    outline(line(8, 9, 16, 9)), outline(line(8, 12.4, 13, 12.4))])
SPEAKER = "M4 9.5 H7.5 L12 5.5 V18.5 L7.5 14.5 H4 Z"
add("volume_up", shape(SPEAKER) + [outline("M15.6 9.2 A4.2 4.2 0 0 1 15.6 14.8"), outline("M18.3 6.6 A8 8 0 0 1 18.3 17.4")], mirror=True)
add("volume_off", shape(SPEAKER) + [outline(line(16, 9.5, 21, 14.5)), outline(line(21, 9.5, 16, 14.5))], mirror=True)

# ---- section headers (decorative, shown beside Settings headings) ---------------
SHIELD = "M12 3.5 L19.5 6.5 V12 C19.5 16.5 16.5 19.5 12 20.8 C7.5 19.5 4.5 16.5 4.5 12 V6.5 Z"
add("section_security", shape(SHIELD) + [outline(poly([(8.8, 12), (11, 14.2), (15.3, 9.6)]))])
add("section_playback", ICONS["play_circle"])
add("section_lyrics", ICONS["lyrics"])
add("section_voice", ICONS["mic"])
add("section_fading", ICONS["graphic_eq"])
add("section_about", ICONS["info"])
add("section_icons", shape("M12 3.5 C12.6 8.3 15.7 11.4 20.5 12 C15.7 12.6 12.6 15.7 12 20.5 C11.4 15.7 8.3 12.6 3.5 12 C8.3 11.4 11.4 8.3 12 3.5 Z"))


def pack_json():
    icons = {}
    for name, layers in ICONS.items():
        entry = {"layers": [{"d": d, "style": kind, **({"alpha": a} if a != 1.0 else {})} for d, kind, a in layers]}
        if name in MIRROR:
            entry["mirror"] = True
        icons[name] = entry
    return {
        "format": 1, "id": PACK_ID, "name": PACK_NAME, "version": PACK_VERSION,
        "author": "FukeX", "viewport": 24, "strokeWidth": STROKE, "icons": icons,
    }


# ---- preview renderer (Pillow) ------------------------------------------------
def flatten(d, steps=14):
    """Absolute M L H V A C Q Z -> list of (points, closed)."""
    import re
    t = re.findall(r"[A-Za-z]|-?\d*\.?\d+", d)
    subs, cur, i, pos, start = [], [], 0, (0.0, 0.0), (0.0, 0.0)
    cmd = None
    f = lambda: float(t[i])
    while i < len(t):
        if t[i].isalpha():
            cmd = t[i]; i += 1
            if cmd == "Z":
                if cur: subs.append((cur, True))
                cur = []; pos = start
                continue
        if cmd == "M":
            if cur: subs.append((cur, False))
            pos = (f(), float(t[i + 1])); i += 2; start = pos; cur = [pos]; cmd = "L"
        elif cmd == "L":
            pos = (f(), float(t[i + 1])); i += 2; cur.append(pos)
        elif cmd == "H":
            pos = (f(), pos[1]); i += 1; cur.append(pos)
        elif cmd == "V":
            pos = (pos[0], f()); i += 1; cur.append(pos)
        elif cmd == "C":
            p = [float(x) for x in t[i:i + 6]]; i += 6
            for k in range(1, steps + 1):
                u = k / steps; v = 1 - u
                cur.append((v**3*pos[0] + 3*v*v*u*p[0] + 3*v*u*u*p[2] + u**3*p[4],
                            v**3*pos[1] + 3*v*v*u*p[1] + 3*v*u*u*p[3] + u**3*p[5]))
            pos = (p[4], p[5])
        elif cmd == "A":
            rx, ry, rot, fa, fs, x, y = [float(v) for v in t[i:i + 7]]; i += 7
            cur.extend(arc_points(pos, rx, ry, int(fa), int(fs), (x, y), steps * 2)); pos = (x, y)
        else:
            raise ValueError("unsupported command " + str(cmd))
    if cur: subs.append((cur, False))
    return subs


def arc_points(p0, rx, ry, fa, fs, p1, steps):
    x1, y1 = p0; x2, y2 = p1
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    lam = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry)
    if lam > 1:
        rx *= math.sqrt(lam); ry *= math.sqrt(lam)
    num = rx*rx*ry*ry - rx*rx*dy*dy - ry*ry*dx*dx
    den = rx*rx*dy*dy + ry*ry*dx*dx
    co = math.sqrt(max(0, num / den)) * (-1 if fa == fs else 1)
    cxp, cyp = co * rx * dy / ry, -co * ry * dx / rx
    cx, cy = cxp + (x1 + x2) / 2, cyp + (y1 + y2) / 2
    a1 = math.atan2((dy - cyp) / ry, (dx - cxp) / rx)
    a2 = math.atan2((-dy - cyp) / ry, (-dx - cxp) / rx)
    da = a2 - a1
    if fs == 0 and da > 0: da -= 2 * math.pi
    if fs == 1 and da < 0: da += 2 * math.pi
    return [(cx + rx * math.cos(a1 + da * k / steps), cy + ry * math.sin(a1 + da * k / steps)) for k in range(1, steps + 1)]


def preview(path, cell=96, cols=10):
    from PIL import Image, ImageDraw, ImageChops
    names = list(ICONS)
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * cell, rows * (cell + 18)), (250, 248, 252))
    scale = 8; size = 24 * scale
    for idx, name in enumerate(names):
        mask = Image.new("L", (size, size), 0)
        for d, kind, alpha in ICONS[name]:
            layer = Image.new("L", (size, size), 0)
            subs = flatten(d)
            if kind == "fill":
                acc = Image.new("L", (size, size), 0)
                for pts, _ in subs:
                    m = Image.new("L", (size, size), 0)
                    ImageDraw.Draw(m).polygon([(x * scale, y * scale) for x, y in pts], fill=255)
                    acc = ImageChops.logical_xor(acc.convert("1"), m.convert("1")).convert("L")
                layer = acc
            else:
                dr = ImageDraw.Draw(layer); w = int(STROKE * scale)
                for pts, closed in subs:
                    pp = [(x * scale, y * scale) for x, y in pts] + ([(pts[0][0] * scale, pts[0][1] * scale)] if closed else [])
                    dr.line(pp, fill=255, width=w, joint="curve")
                    for x, y in (pp[0], pp[-1]):
                        dr.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=255)
            layer = layer.point(lambda v, a=alpha: int(v * a))
            mask = ImageChops.lighter(mask, layer)
        glyph = Image.new("RGB", (size, size), (74, 53, 150))
        tile = Image.new("RGB", (size, size), (250, 248, 252)); tile.paste(glyph, (0, 0), mask)
        tile = tile.resize((cell - 24, cell - 24), Image.LANCZOS)
        x, y = (idx % cols) * cell + 12, (idx // cols) * (cell + 18) + 6
        sheet.paste(tile, (x, y))
        ImageDraw.Draw(sheet).text((x - 6, y + cell - 20), name[:16], fill=(90, 90, 100))
    sheet.save(path)


def main():
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
    body = json.dumps(pack_json(), separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    fname = PACK_ID + ".fxicons"
    with open(os.path.join(out, fname), "wb") as fh:
        fh.write(body)
    index = {"format": 1, "packs": [{
        "id": PACK_ID, "name": PACK_NAME, "version": PACK_VERSION,
        "description": "Crisp rounded duotone icons for every screen, menu and dialog. Follows your theme colours.",
        "file": fname, "sha256": hashlib.sha256(body).hexdigest(), "sizeBytes": len(body)}]}
    with open(os.path.join(out, "index.json"), "w", encoding="utf-8") as fh:
        json.dump(index, fh, indent=2); fh.write("\n")
    print("wrote %s (%d bytes, %d icons)" % (fname, len(body), len(ICONS)))
    if "--preview" in sys.argv:
        preview(os.path.join(out, "preview.png"))


if __name__ == "__main__":
    main()
