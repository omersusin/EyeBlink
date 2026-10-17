#!/usr/bin/env python3
"""Refreshes the pictures in the README from the design lab.

    ./gradlew -p jvm-build :tools:designlab:run --args="build/shots"        # every screen, both themes
    ./gradlew -p jvm-build :tools:designlab:run --args="build/tour tour"    # the page-transition frames
    python3 docs/brand/refresh_docs.py screens build/shots                  # -> docs/screens/*.png
    python3 docs/brand/refresh_docs.py tour build/tour                      # -> docs/brand/tour.gif
    node docs/brand/render.mjs                                              # -> docs/brand/banner.png (uses docs/screens)

Needs Pillow (screens) and ffmpeg (tour). The screens are flat colour, so they are stored as 256-colour PNGs: a
third of the size and indistinguishable by eye.
"""
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# What the README and the banner show. Add a name here to publish another screen.
SCREENS = (
    "home home-off home-tr rhythm studio apps more night power diag stats privacy about "
    "calib-intro calib-video calib-result onboard-0 onboard-1 onboard-3 onboard-4 rest-focus rest-black "
    "home-dark rhythm-dark studio-dark apps-dark more-dark night-dark language language-dark"
).split()


def screens(src: Path):
    from PIL import Image

    dst = ROOT / "docs/screens"
    dst.mkdir(parents=True, exist_ok=True)
    total = 0
    for name in SCREENS:
        image = Image.open(src / f"{name}.png").convert("RGB")
        quantized = image.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
        out = dst / f"{name}.png"
        quantized.save(out, optimize=True)
        total += out.stat().st_size
    print(f"{len(SCREENS)} screens, {total // 1024} KB")


def tour(src: Path):
    files = sorted(f for f in os.listdir(src) if f.startswith("tour-") and f.endswith(".png"))
    entries, skip = [], False
    for f in files:
        hold = int(re.search(r"-(\d+)\.png", f).group(1))
        if hold < 100:  # a recorded transition frame: keep every other one, shown for two
            skip = not skip
            if skip:
                continue
            entries.append((f, 0.066))
        else:  # a settled screen
            skip = False
            entries.append((f, hold / 1000))
    listing = src / "list.txt"
    listing.write_text("".join(f"file '{f}'\nduration {d}\n" for f, d in entries) + f"file '{entries[-1][0]}'\n")
    out = ROOT / "docs/brand/tour.gif"
    subprocess.run(
        [
            "ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(listing), "-vf",
            "scale=360:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=96:stats_mode=full[p];[s1][p]paletteuse=dither=none",
            str(out),
        ],
        check=True,
    )
    print(f"{out.relative_to(ROOT)}: {out.stat().st_size // 1024} KB, {len(entries)} frames")


if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] not in ("screens", "tour"):
        sys.exit(__doc__)
    {"screens": screens, "tour": tour}[sys.argv[1]](Path(sys.argv[2]))
