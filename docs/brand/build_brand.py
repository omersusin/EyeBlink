#!/usr/bin/env python3
"""Draws the EyeBlink mark once and writes it everywhere it is needed.

    python3 docs/brand/build_brand.py

The geometry is the same as `EyeArt.drawEye` in the app (a round-cornered lens, a sun-yellow lid
around it, a sky-blue iris, five lashes), so the logo, the launcher icon and the eye on the home
screen are one drawing. No dependencies: it only writes text.

Outputs
  docs/brand/logo.svg                                   the logo on its sky tile (README, store)
  app/src/main/res/drawable/ic_launcher_foreground.xml  adaptive icon, foreground layer
  app/src/main/res/drawable/ic_launcher_monochrome.xml  adaptive icon, themed (Android 13+) layer
  app/src/main/res/drawable/ic_stat_eye.xml             status-bar glyph
"""
from math import hypot, pi, sin
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# Daylight palette (kit/Palette.kt). The logo is a brand colour, so it is the same in both themes.
SUN, SUN_LIP = "#FFC62E", "#E3A10C"
SKY, SKY_LIP, SKY_SOFT_LIP = "#3AA4FF", "#1C83DE", "#B7DAF5"
INK, WHITE = "#16254A", "#FFFFFF"

K = 1.3333  # control-point reach of the lens curves
ROUND = 0.78  # how far out the controls sit: lower is an almond, higher a rounder eye


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def lens(cx, cy, a, up, down, sag=0.0):
    return (
        f"M{f(cx - a)},{f(cy)} "
        f"C{f(cx - a * ROUND)},{f(cy - up * K + sag)} {f(cx + a * ROUND)},{f(cy - up * K + sag)} {f(cx + a)},{f(cy)} "
        f"C{f(cx + a * ROUND)},{f(cy + down * K + sag)} {f(cx - a * ROUND)},{f(cy + down * K + sag)} {f(cx - a)},{f(cy)} Z"
    )


def top_curve(cx, cy, a, up):
    return f"M{f(cx - a)},{f(cy)} C{f(cx - a * ROUND)},{f(cy - up * K)} {f(cx + a * ROUND)},{f(cy - up * K)} {f(cx + a)},{f(cy)}"


def circle(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)} a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0 a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0 Z"


def top_point(cx, cy, a, up, t):
    """Point on the aperture's top curve at t, and its outward normal (matches EyeArt.topPoint)."""
    x0, x1, x2, x3 = cx - a, cx - a * ROUND, cx + a * ROUND, cx + a
    y0, y1, y3 = cy, cy - up * K, cy
    y2 = y1
    u = 1 - t
    px = u**3 * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t**3 * x3
    py = u**3 * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t**3 * y3
    dx = 3 * u * u * (x1 - x0) + 6 * u * t * (x2 - x1) + 3 * t * t * (x3 - x2)
    dy = 3 * u * u * (y1 - y0) + 6 * u * t * (y2 - y1) + 3 * t * t * (y3 - y2)
    n = hypot(dx, dy)
    return (px, py), (dy / n, -dx / n)


class Eye:
    """The open eye, centred on (cx, cy), W wide (the width of EyeArt's bounds)."""

    def __init__(self, cx, cy, W):
        self.cx, self.cy, self.W = cx, cy, W
        self.a = W / 2 * 0.94
        b = W * 0.62 / 2
        self.b = b
        self.up, self.down = b * 0.78, b * 0.66
        self.socket = lens(cx, cy, self.a * 1.08, self.up * 1.30, self.down * 1.22)
        self.aperture = lens(cx, cy, self.a, self.up, self.down)
        self.crease = top_curve(cx, cy, self.a, self.up)
        r = b * 0.60
        self.r = r
        self.iris = circle(cx, cy, r)
        self.ring_r = r * 0.94
        self.pupil_r = r * 0.46
        self.lip = W * 0.03  # how far the lip hangs below the lid
        # lashes, as (x1, y1, x2, y2)
        self.lashes = []
        for i in range(5):
            t = 0.13 + i * 0.185
            (px, py), (nx, ny) = top_point(cx, cy, self.a, self.up, t)
            length = self.a * (0.17 + 0.07 * sin(pi * t))
            self.lashes.append((px, py, px + nx * length, py + ny * length))
        self.shade = (cx - self.a, cy - self.up * K, 2 * self.a, self.up * 0.62)

    # widths relative to the eye so they scale with it
    @property
    def crease_w(self):
        return self.W * 0.014

    @property
    def lash_w(self):
        return self.W * 0.017


def svg_eye(e: Eye, lip_color=SKY_LIP):
    hl1 = (e.cx - e.r * 0.30, e.cy - e.r * 0.34, e.r * 0.2)
    hl2 = (e.cx + e.r * 0.28, e.cy + e.r * 0.30, e.r * 0.085)
    lashes = "".join(
        f'<line x1="{f(x1)}" y1="{f(y1)}" x2="{f(x2)}" y2="{f(y2)}"/>' for x1, y1, x2, y2 in e.lashes
    )
    return f"""
  <path transform="translate(0 {f(e.lip)})" d="{e.socket}" fill="{lip_color}"/>
  <path d="{e.socket}" fill="{SUN}"/>
  <path d="{e.aperture}" fill="{WHITE}"/>
  <clipPath id="aperture"><path d="{e.aperture}"/></clipPath>
  <g clip-path="url(#aperture)">
    <rect x="{f(e.shade[0])}" y="{f(e.shade[1])}" width="{f(e.shade[2])}" height="{f(e.shade[3])}" fill="{SKY_SOFT_LIP}" fill-opacity="0.55"/>
    <circle cx="{f(e.cx)}" cy="{f(e.cy)}" r="{f(e.r)}" fill="{SKY}"/>
    <circle cx="{f(e.cx)}" cy="{f(e.cy)}" r="{f(e.ring_r)}" fill="none" stroke="{SKY_LIP}" stroke-width="{f(e.r * 0.12)}"/>
    <circle cx="{f(e.cx)}" cy="{f(e.cy)}" r="{f(e.pupil_r)}" fill="{INK}"/>
    <circle cx="{f(hl1[0])}" cy="{f(hl1[1])}" r="{f(hl1[2])}" fill="{WHITE}"/>
    <circle cx="{f(hl2[0])}" cy="{f(hl2[1])}" r="{f(hl2[2])}" fill="{WHITE}" fill-opacity="0.8"/>
  </g>
  <path d="{e.crease}" fill="none" stroke="{SUN_LIP}" stroke-width="{f(e.crease_w)}" stroke-linecap="round"/>
  <g stroke="{INK}" stroke-width="{f(e.lash_w)}" stroke-linecap="round">{lashes}</g>"""


def write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    print("wrote", path.relative_to(ROOT))


# ── logo.svg ────────────────────────────────────────────────────────────────

def logo_svg():
    size, lip = 512, 18
    eye = Eye(256, 256 - lip / 2, size * 0.74)
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" width="{size}" height="{size}" role="img" aria-label="EyeBlink">
  <title>EyeBlink</title>
  <rect x="0" y="{lip}" width="{size}" height="{size - lip}" rx="150" fill="{SKY_LIP}"/>
  <rect x="0" y="0" width="{size}" height="{size - lip}" rx="150" fill="{SKY}"/>{svg_eye(eye)}
</svg>
"""


# ── Android vector drawables ────────────────────────────────────────────────

def vec_header(size):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{size}dp" android:height="{size}dp" android:viewportWidth="{size}" android:viewportHeight="{size}">\n'
    )


def argb(hex_color, alpha=1.0):
    return f"#{int(round(alpha * 255)):02X}{hex_color[1:]}"


def launcher_foreground():
    # The visible part of an adaptive icon is a 72dp circle inside the 108dp canvas; keep the eye in it.
    e = Eye(54, 54 - 1.5, 58)
    out = vec_header(108)
    out += "    <!-- The EyeBlink mark. Generated by docs/brand/build_brand.py - edit the script, not this file. -->\n"
    # lip: the socket drawn again, lower, in the tile's own darker blue
    out += (
        f'    <group android:translateY="{f(e.lip)}">\n'
        f'        <path android:fillColor="{argb(SKY_LIP)}" android:pathData="{e.socket}" />\n'
        "    </group>\n"
    )
    out += f'    <path android:fillColor="{argb(SUN)}" android:pathData="{e.socket}" />\n'
    out += f'    <path android:fillColor="{argb(WHITE)}" android:pathData="{e.aperture}" />\n'
    sx, sy, sw, sh = e.shade
    out += "    <group>\n"
    out += f'        <clip-path android:pathData="{e.aperture}" />\n'
    out += f'        <path android:fillColor="{argb(SKY_SOFT_LIP, 0.55)}" android:pathData="M{f(sx)},{f(sy)} h{f(sw)} v{f(sh)} h{f(-sw)} Z" />\n'
    out += f'        <path android:fillColor="{argb(SKY)}" android:pathData="{e.iris}" />\n'
    out += (
        f'        <path android:fillColor="#00000000" android:strokeColor="{argb(SKY_LIP)}" android:strokeWidth="{f(e.r * 0.12)}" '
        f'android:pathData="{circle(e.cx, e.cy, e.ring_r)}" />\n'
    )
    out += f'        <path android:fillColor="{argb(INK)}" android:pathData="{circle(e.cx, e.cy, e.pupil_r)}" />\n'
    out += f'        <path android:fillColor="{argb(WHITE)}" android:pathData="{circle(e.cx - e.r * 0.30, e.cy - e.r * 0.34, e.r * 0.2)}" />\n'
    out += f'        <path android:fillColor="{argb(WHITE, 0.8)}" android:pathData="{circle(e.cx + e.r * 0.28, e.cy + e.r * 0.30, e.r * 0.085)}" />\n'
    out += "    </group>\n"
    out += (
        f'    <path android:fillColor="#00000000" android:strokeColor="{argb(SUN_LIP)}" android:strokeWidth="{f(e.crease_w)}" '
        f'android:strokeLineCap="round" android:pathData="{e.crease}" />\n'
    )
    lashes = " ".join(f"M{f(x1)},{f(y1)} L{f(x2)},{f(y2)}" for x1, y1, x2, y2 in e.lashes)
    out += (
        f'    <path android:fillColor="#00000000" android:strokeColor="{argb(INK)}" android:strokeWidth="{f(e.lash_w)}" '
        f'android:strokeLineCap="round" android:pathData="{lashes}" />\n'
    )
    out += "</vector>\n"
    return out


def launcher_monochrome():
    """One colour, told apart by shape: the lid is a ring, the iris a disc with the pupil cut out."""
    e = Eye(54, 54 - 1.5, 58)
    out = vec_header(108)
    out += "    <!-- The mark in one colour, for themed icons (Android 13+ tints it). Generated by docs/brand/build_brand.py. -->\n"
    out += f'    <path android:fillColor="#FF000000" android:fillType="evenOdd" android:pathData="{e.socket} {e.aperture}" />\n'
    out += f'    <path android:fillColor="#FF000000" android:fillType="evenOdd" android:pathData="{e.iris} {circle(e.cx, e.cy, e.pupil_r)}" />\n'
    lashes = " ".join(f"M{f(x1)},{f(y1)} L{f(x2)},{f(y2)}" for x1, y1, x2, y2 in e.lashes)
    out += (
        f'    <path android:fillColor="#00000000" android:strokeColor="#FF000000" android:strokeWidth="{f(e.lash_w * 1.2)}" '
        f'android:strokeLineCap="round" android:pathData="{lashes}" />\n'
    )
    out += "</vector>\n"
    return out


def status_glyph():
    e = Eye(12, 12.6, 21)
    out = vec_header(24)
    out += "    <!-- Status-bar glyph: the mark in one colour, which the system tints. Generated by docs/brand/build_brand.py. -->\n"
    out += f'    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{e.socket} {e.aperture}" />\n'
    out += f'    <path android:fillColor="#FFFFFFFF" android:pathData="{e.iris}" />\n'
    lashes = " ".join(f"M{f(x1)},{f(y1)} L{f(x2)},{f(y2)}" for x1, y1, x2, y2 in e.lashes)
    out += (
        f'    <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="{f(e.lash_w * 1.3)}" '
        f'android:strokeLineCap="round" android:pathData="{lashes}" />\n'
    )
    out += "</vector>\n"
    return out


if __name__ == "__main__":
    write(ROOT / "docs/brand/logo.svg", logo_svg())
    res = ROOT / "app/src/main/res/drawable"
    write(res / "ic_launcher_foreground.xml", launcher_foreground())
    write(res / "ic_launcher_monochrome.xml", launcher_monochrome())
    write(res / "ic_stat_eye.xml", status_glyph())
