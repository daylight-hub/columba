#!/usr/bin/env python3
"""
apply-lcs-logo.py — swap the app logo across every Android density + the
About screen, from a single source PNG.

Usage:
    python3 scripts/lcs-branding/apply-lcs-logo.py path/to/lcs-logo.png [--bg "#0B3D2E"]

Requirements:
    pip install pillow

What it writes (all under app/src/main/res):
  * drawable-nodpi/ic_launcher_foreground.png   (adaptive-icon foreground +
                                                 the image shown on the About screen)
  * mipmap-<density>/ic_launcher.png            (legacy square launcher icon)
  * mipmap-<density>/ic_launcher_round.png      (legacy round launcher icon)
  * drawable/ic_launcher_background.xml         (solid brand color, if --bg given)

It also deletes the upstream *vector* foreground so @drawable/ic_launcher_foreground
resolves to the new PNG (resource name is unchanged, so no code/XML edits needed).

Give it a square, transparent-background PNG of your logo (>= 512x512). The logo
is auto-fit inside the adaptive-icon safe zone so it isn't clipped by the launcher mask.
"""
import argparse, pathlib, sys

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("Pillow is required:  pip install pillow")

RES = pathlib.Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res"

# Legacy launcher icon sizes (px) by density bucket.
LAUNCHER = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FG_SIZE = 512          # adaptive-icon foreground canvas
SAFE = 0.66            # fraction of the canvas the logo may occupy (mask safe zone)


def load_square(path):
    img = Image.open(path).convert("RGBA")
    side = max(img.size)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(img, ((side - img.width) // 2, (side - img.height) // 2), img)
    return canvas


def fitted(logo, size, safe):
    inner = int(size * safe)
    resized = logo.resize((inner, inner), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    off = (size - inner) // 2
    canvas.paste(resized, (off, off), resized)
    return canvas


def round_masked(square):
    mask = Image.new("L", square.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, square.width, square.height), fill=255)
    out = Image.new("RGBA", square.size, (0, 0, 0, 0))
    out.paste(square, (0, 0), mask)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("logo", help="source logo PNG (square, transparent, >=512px)")
    ap.add_argument("--bg", default=None, help="solid launcher background color, e.g. #0B3D2E")
    args = ap.parse_args()

    logo = load_square(args.logo)

    # 1. Adaptive-icon foreground + About-screen image.
    fg_dir = RES / "drawable-nodpi"
    fg_dir.mkdir(parents=True, exist_ok=True)
    fitted(logo, FG_SIZE, SAFE).save(fg_dir / "ic_launcher_foreground.png")
    # Remove upstream vector foregrounds so the PNG wins.
    for vec in ("drawable/ic_launcher_foreground.xml",
                "drawable-v24/ic_launcher_foreground.xml",
                "drawable-anydpi-v24/ic_launcher_foreground.xml"):
        f = RES / vec
        if f.exists():
            f.unlink()

    # 2. Legacy per-density launcher icons (square + round).
    for bucket, px in LAUNCHER.items():
        d = RES / f"mipmap-{bucket}"
        d.mkdir(parents=True, exist_ok=True)
        sq = fitted(logo, px, 0.90)
        if args.bg:
            base = Image.new("RGBA", (px, px), args.bg)
            base.alpha_composite(sq)
            sq = base
        sq.save(d / "ic_launcher.png")
        round_masked(sq).save(d / "ic_launcher_round.png")

    # 3. Optional solid brand background for the adaptive icon.
    if args.bg:
        # NOTE: must be a <vector>, not a <shape>. Compose painterResource()
        # only accepts vector drawables and bitmaps, and the onboarding
        # WelcomePage loads this resource that way — a <shape> crashes the app.
        (RES / "drawable" / "ic_launcher_background.xml").write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n'
            '    android:height="108dp"\n'
            '    android:viewportWidth="512"\n'
            '    android:viewportHeight="512">\n'
            '    <path\n'
            '        android:pathData="M0,0h512v512h-512z"\n'
            f'        android:fillColor="{args.bg}" />\n'
            '</vector>\n'
        )

    print("Logo applied. Rebuild the app to see the new launcher + About icon.")


if __name__ == "__main__":
    main()
