#!/usr/bin/env python3
"""Draws EyeBlink's design tokens - both palettes, the type, the depth - as one picture, from the code that defines them.

    python3 docs/brand/build_tokens.py                       # writes docs/brand/tokens.html
    node docs/brand/render.mjs tokens.html tokens.png 1500 1000   # and the picture next to it

Nothing here is typed in by hand: the colours are read out of app/.../ui/kit/Palette.kt and the sizes out of Type.kt, so
the picture cannot disagree with the app. It is the picture in docs/HOW_IT_WAS_MADE.md.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KIT = ROOT / "app/src/main/java/com/eyeblink/app/ui/kit"

HUES = ["sun", "sky", "mint", "coral", "grape"]
ROLES = ["fill", "lip", "soft", "softLip", "text"]
ROLE_NOTE = {
    "fill": "buttons, marks, big shapes",
    "lip": "the edge under a raised fill",
    "soft": "a card's tinted face",
    "softLip": "the edge under that card",
    "text": "small text in that hue",
}
NEUTRALS = ["bg", "bgDeep", "card", "cardLip", "sunk", "line", "ink", "inkSoft", "inkFaint"]


def hex6(argb: str) -> str:
    return "#" + argb[2:]


def palette(source: str, name: str):
    block = re.search(r"val %s = Palette\((.*?)\n\)\n" % name, source, re.S).group(1)
    colors = {k: hex6(v) for k, v in re.findall(r"(\w+) = Color\(0x([0-9A-Fa-f]{8})\)", block) if v.upper().startswith("FF")}
    hues = {}
    for hue, args in re.findall(r"(\w+) = hue\(([^)]*)\)", block):
        values = [hex6(a.strip()[2:]) for a in args.split(",")]
        hues[hue] = dict(zip(ROLES, values))
    return colors, hues


def type_scale(source: str):
    rows = []
    for ink, body in re.findall(r"Ink\.(\w+) -> TextStyle\((.*?)\n        \)", source, re.S):
        size = re.search(r"fontSize = (\d+)\.sp", body)
        line = re.search(r"lineHeight = (\d+)\.sp", body)
        weight = re.search(r"FontWeight\.(\w+)", body)
        family = re.search(r"EbFonts\.(\w+)", body)
        rows.append((ink, family.group(1), weight.group(1), int(size.group(1)), int(line.group(1))))
    return rows


def swatch(name: str, value: str, role: str = "") -> str:
    return (
        f'<div class="sw"><i style="background:{value}"></i><b>{name}</b><span>{value.upper()}</span>'
        f'{f"<em>{role}</em>" if role else ""}</div>'
    )


def panel(title: str, key: str, colors: dict, hues: dict) -> str:
    neutral = "".join(swatch(n, colors[n]) for n in NEUTRALS)
    rows = ""
    for h in HUES:
        cells = "".join(swatch(r, hues[h][r]) for r in ROLES)
        rows += f'<div class="hue"><h4>{h}</h4><div class="roles">{cells}</div></div>'
    lip_demo = (
        f'<div class="demo">'
        f'<div class="card" style="background:{colors["card"]};box-shadow:0 5px 0 {colors["cardLip"]};border:1.5px solid {colors["line"]};color:{colors["ink"]}">'
        f'<b>A card</b><span style="color:{colors["inkSoft"]}">a flat face on a darker lip</span></div>'
        f'<div class="btn" style="background:{hues["sun"]["fill"]};box-shadow:0 5px 0 {hues["sun"]["lip"]};color:{colors["onFill"]}">Start EyeBlink</div>'
        f'<div class="tint" style="background:{hues["sky"]["soft"]};box-shadow:0 5px 0 {hues["sky"]["softLip"]};color:{hues["sky"]["text"]}">0:38</div>'
        f'</div>'
    )
    return (
        f'<section class="panel {key}" style="background:{colors["bg"]};color:{colors["ink"]}">'
        f'<h3>{title}</h3><p style="color:{colors["inkSoft"]}">neutrals</p><div class="neutrals">{neutral}</div>'
        f'<p style="color:{colors["inkSoft"]}">five hues, five roles each - text on a bright fill is always the dark navy onFill ({colors["onFill"].upper()})</p>{rows}{lip_demo}</section>'
    )


def main():
    source = (KIT / "Palette.kt").read_text()
    day_c, day_h = palette(source, "Daylight")
    night_c, night_h = palette(source, "Moonlit")
    rows = type_scale((KIT / "Type.kt").read_text())
    specimen = {"Display": "0:38", "Headline": "Give your eyes the pause back", "Lead": "A soft half-second eyelid", "Title": "Pause during breaks",
                "Body": "Only a video EyeBlink has seen playing, and it checks that the pause worked.", "BodyStrong": "Resume afterwards",
                "Caption": "Never if you paused it yourself.", "Eyebrow": "Your video", "Chip": "Instagram", "Num": "11.3 s",
                "NumSmall": "p50 14 ms", "Code": "cmd media_session dispatch pause", "Button": "Start EyeBlink"}
    family_css = {"display": "'Bricolage Grotesque'", "sans": "'Figtree'", "mono": "'DM Mono'"}
    weight_css = {"Normal": 400, "Medium": 500, "SemiBold": 600, "Bold": 700, "ExtraBold": 800}
    type_rows = [
        f'<div class="trow"><span class="tname">{ink}<small>{fam} {wt} {size}/{line} sp</small></span>'
        f'<span class="tsample" style="font-family:{family_css[fam]},sans-serif;font-weight:{weight_css[wt]};font-size:{min(size, 46)}px;line-height:{min(line, 50)}px;'
        f'{"font-variant-numeric:tabular-nums;" if ink in ("Display", "Num", "NumSmall") else ""}">{specimen.get(ink, ink)}</span></div>'
        for ink, fam, wt, size, line in rows
    ]
    half = (len(type_rows) + 1) // 2
    left_rows, right_rows = "".join(type_rows[:half]), "".join(type_rows[half:])
    html = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>EyeBlink design tokens</title>
<style>
@font-face {{ font-family: 'Bricolage Grotesque'; src: url('../../app/src/main/res/font/bricolage_grotesque_bold.ttf'); font-weight: 700; }}
@font-face {{ font-family: 'Bricolage Grotesque'; src: url('../../app/src/main/res/font/bricolage_grotesque_extrabold.ttf'); font-weight: 800; }}
@font-face {{ font-family: 'Figtree'; src: url('../../app/src/main/res/font/figtree_regular.ttf'); font-weight: 400; }}
@font-face {{ font-family: 'Figtree'; src: url('../../app/src/main/res/font/figtree_medium.ttf'); font-weight: 500; }}
@font-face {{ font-family: 'Figtree'; src: url('../../app/src/main/res/font/figtree_semibold.ttf'); font-weight: 600; }}
@font-face {{ font-family: 'Figtree'; src: url('../../app/src/main/res/font/figtree_bold.ttf'); font-weight: 700; }}
@font-face {{ font-family: 'DM Mono'; src: url('../../app/src/main/res/font/dm_mono_regular.ttf'); font-weight: 400; }}
* {{ box-sizing: border-box; margin: 0; padding: 0; }}
body {{ width: 1500px; background: {day_c["bgDeep"]}; font-family: 'Figtree', sans-serif; padding: 28px; }}
h1 {{ font: 800 40px 'Bricolage Grotesque', sans-serif; color: {day_c["ink"]}; letter-spacing: -1px; }}
.sub {{ color: {day_c["inkSoft"]}; font-size: 17px; margin: 6px 0 22px; }}
.panels {{ display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }}
.panel {{ border-radius: 30px; padding: 26px 28px 30px; border: 2px solid {day_c["line"]}; }}
.panel.moonlit {{ border-color: {night_c["line"]}; }}
.panel h3 {{ font: 800 30px 'Bricolage Grotesque', sans-serif; letter-spacing: -.6px; margin-bottom: 10px; }}
.panel p {{ font-size: 14px; font-weight: 600; margin: 14px 0 8px; }}
.neutrals {{ display: grid; grid-template-columns: repeat(9, 1fr); gap: 8px; }}
.roles {{ display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; }}
.hue {{ margin-bottom: 8px; }}
.hue h4 {{ font: 700 15px 'Figtree', sans-serif; margin-bottom: 4px; opacity: .85; }}
.sw {{ display: flex; flex-direction: column; gap: 3px; font-size: 11px; line-height: 13px; }}
.sw i {{ display: block; height: 44px; border-radius: 12px; border: 1.5px solid rgba(128,128,128,.28); }}
.sw b {{ font-weight: 700; font-size: 12px; }}
.sw span {{ font-family: 'DM Mono', monospace; font-size: 10.5px; opacity: .8; }}
.sw em {{ font-style: normal; opacity: .65; font-size: 10.5px; }}
.demo {{ display: flex; gap: 16px; align-items: center; margin-top: 26px; padding-bottom: 6px; }}
.demo .card {{ border-radius: 26px; padding: 16px 20px; display: flex; flex-direction: column; gap: 3px; flex: 1.4; }}
.demo .card span {{ font-size: 13px; }}
.demo .btn {{ border-radius: 40px; padding: 15px 26px; font: 700 16px 'Bricolage Grotesque', sans-serif; }}
.demo .tint {{ border-radius: 22px; padding: 12px 22px; font: 800 30px 'Bricolage Grotesque', sans-serif; font-variant-numeric: tabular-nums; }}
.type {{ margin-top: 24px; background: {day_c["card"]}; border-radius: 30px; border: 2px solid {day_c["line"]}; padding: 22px 28px 24px; box-shadow: 0 6px 0 {day_c["cardLip"]}; }}
.type h3 {{ font: 800 26px 'Bricolage Grotesque', sans-serif; color: {day_c["ink"]}; margin-bottom: 10px; letter-spacing: -.5px; }}
.type .cols {{ display: grid; grid-template-columns: 1fr 1fr; column-gap: 40px; }}
.trow {{ display: grid; grid-template-columns: 190px 1fr; align-items: center; padding: 7px 0; border-top: 1.5px solid {day_c["line"]}; color: {day_c["ink"]}; min-height: 44px; }}
.tname {{ font: 700 14px 'Figtree', sans-serif; display: flex; flex-direction: column; }}
.tname small {{ font: 500 11px 'DM Mono', monospace; color: {day_c["inkSoft"]}; margin-top: 2px; }}
.tsample {{ white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }}
.foot {{ margin-top: 14px; color: {day_c["inkSoft"]}; font-size: 13px; }}
</style></head>
<body>
<h1>EyeBlink design tokens</h1>
<div class="sub">Read out of <code>ui/kit/Palette.kt</code> and <code>ui/kit/Type.kt</code> by <code>docs/brand/build_tokens.py</code> - the app's own values, not a redraw. Five hues; nothing glows, blurs or is translucent; every raised thing stands on a darker lip.</div>
<div class="panels">{panel("Daylight", "daylight", day_c, day_h)}{panel("Moonlit", "moonlit", night_c, night_h)}</div>
<div class="type"><h3>Type - Bricolage Grotesque for the loud, Figtree for reading, DM Mono for commands</h3>
<div class="cols"><div>{left_rows}</div><div>{right_rows}</div></div></div>
<div class="foot">Tabular numerals wherever a number changes. Fonts are injected into the UI kit (<code>EbFonts</code>), so the desktop design lab and the phone draw with the same files.</div>
</body></html>
"""
    out = ROOT / "docs/brand/tokens.html"
    out.write_text(html)
    print(f"{out.relative_to(ROOT)}: {len(html) // 1024} KB; {len(rows)} text styles")


if __name__ == "__main__":
    main()
