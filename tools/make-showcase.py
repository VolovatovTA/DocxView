#!/usr/bin/env python3
# Builds docs/showcase-{light,dark}.png from the screenshots in docs/: python3 tools/make-showcase.py docs
# Needs Pillow (pip install pillow) and macOS's Helvetica Neue for the captions.
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont
docs = sys.argv[1]
shots = [
    ("pdf", "PDF"), ("docx_dark", "Word · dark theme"), ("xlsx", "Excel · sheet tabs"),
    ("pdf_search", "PDF search"), ("csv_search", "CSV search"),
]
S = 2                      # render at 2x for crisp edges, screenshots are scaled up from 320 px
W, H = 320 * S, 720 * S
GAP, PAD, RADIUS = 36 * S, 40 * S, 30 * S
CAPTION = 64 * S
font = ImageFont.truetype("/System/Library/Fonts/HelveticaNeue.ttc", 22 * S, index=1)
themes = {
    "light": dict(bg=(243, 245, 249), text=(40, 46, 58), border=(214, 219, 228), shadow=(20, 30, 60, 60)),
    "dark": dict(bg=(18, 21, 27), text=(225, 229, 236), border=(52, 58, 70), shadow=(0, 0, 0, 160)),
}
for name, t in themes.items():
    width = PAD * 2 + W * len(shots) + GAP * (len(shots) - 1)
    height = PAD * 2 + H + CAPTION
    canvas = Image.new("RGBA", (width, height), t["bg"] + (255,))
    for i, (file, caption) in enumerate(shots):
        x, y = PAD + i * (W + GAP), PAD
        shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        ImageDraw.Draw(shadow).rounded_rectangle((x, y + 10 * S, x + W, y + H + 10 * S), RADIUS, fill=t["shadow"])
        canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(14 * S)))
        screen = Image.open(f"{docs}/{file}.png").convert("RGBA").resize((W, H), Image.LANCZOS)
        mask = Image.new("L", (W, H), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, W - 1, H - 1), RADIUS, fill=255)
        canvas.paste(screen, (x, y), mask)
        draw = ImageDraw.Draw(canvas)
        draw.rounded_rectangle((x, y, x + W - 1, y + H - 1), RADIUS, outline=t["border"], width=S)
        tw = draw.textlength(caption, font=font)
        draw.text((x + (W - tw) / 2, y + H + 22 * S), caption, font=font, fill=t["text"])
    out = canvas.convert("RGB").resize((width // S, height // S), Image.LANCZOS)
    out.save(f"{docs}/showcase-{name}.png", optimize=True)
    print(name, out.size)
