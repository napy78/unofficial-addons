#!/usr/bin/env python3
"""Genera le icone dei pulsanti (normale + premuto) e l'icona dell'app."""
import math, os
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "common.r")
S = 4            # supersampling
W = 120          # dimensione tile in px finali
R = 26           # raggio angoli

# nome: (colore alto, colore basso)
PALETTE = {
    "bulb":   ((255, 214, 90), (245, 150, 30)),
    "power":  ((255, 110, 110), (205, 40, 70)),
    "lock":   ((120, 190, 255), (50, 100, 220)),
    "door":   ((190, 150, 255), (110, 70, 210)),
    "garage": ((110, 225, 200), (20, 150, 140)),
    "fan":    ((110, 220, 255), (30, 140, 220)),
    "bell":   ((255, 190, 100), (230, 110, 40)),
    "home":   ((140, 235, 140), (40, 160, 80)),
    "music":  ((255, 130, 200), (200, 50, 140)),
    "flame":  ((255, 160, 90), (225, 60, 40)),
    "snow":   ((170, 225, 255), (80, 150, 230)),
    "bolt":   ((255, 235, 110), (220, 170, 20)),
    "dot":    ((170, 180, 200), (90, 100, 125)),
}


def gradient(size, top, bottom):
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        t = y / (size - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        for x in range(size):
            px[x, y] = c
    return img


def glyph(name, d, c, w):
    """Disegna il glifo bianco; coordinate su canvas w x w (area utile in alto)."""
    cx, cy = w / 2, w * 0.40
    u = w / 120.0
    white = (255, 255, 255, 255)
    lw = int(7 * u)

    if name == "bulb":
        d.ellipse([cx - 20*u, cy - 28*u, cx + 20*u, cy + 12*u], fill=white)
        d.polygon([(cx - 10*u, cy + 8*u), (cx + 10*u, cy + 8*u), (cx + 8*u, cy + 22*u), (cx - 8*u, cy + 22*u)], fill=white)
        d.rounded_rectangle([cx - 9*u, cy + 24*u, cx + 9*u, cy + 29*u], 2*u, fill=white)
    elif name == "power":
        d.arc([cx - 22*u, cy - 20*u, cx + 22*u, cy + 24*u], start=-60, end=240, fill=white, width=lw)
        d.rounded_rectangle([cx - 3.5*u, cy - 30*u, cx + 3.5*u, cy + 2*u], 3*u, fill=white)
    elif name in ("lock",):
        d.arc([cx - 15*u, cy - 34*u, cx + 15*u, cy - 2*u], 180, 360, fill=white, width=lw)
        d.line([cx - 15*u, cy - 18*u, cx - 15*u, cy - 6*u], fill=white, width=lw)
        d.line([cx + 15*u, cy - 18*u, cx + 15*u, cy - 6*u], fill=white, width=lw)
        d.rounded_rectangle([cx - 24*u, cy - 8*u, cx + 24*u, cy + 28*u], 7*u, fill=white)
        d.ellipse([cx - 4.5*u, cy + 3*u, cx + 4.5*u, cy + 12*u], fill=c)
        d.rectangle([cx - 2*u, cy + 9*u, cx + 2*u, cy + 19*u], fill=c)
    elif name == "door":
        d.rounded_rectangle([cx - 20*u, cy - 32*u, cx + 20*u, cy + 30*u], 4*u, fill=white)
        d.ellipse([cx + 8*u, cy - 3*u, cx + 15*u, cy + 4*u], fill=c)
        d.rounded_rectangle([cx - 13*u, cy - 25*u, cx + 13*u, cy - 6*u], 2*u, outline=c, width=int(2.5*u))
    elif name == "garage":
        d.polygon([(cx - 30*u, cy - 6*u), (cx, cy - 32*u), (cx + 30*u, cy - 6*u), (cx + 30*u, cy + 30*u), (cx - 30*u, cy + 30*u)], fill=white)
        for i in range(4):
            y = cy - 2*u + i * 8 * u
            d.line([cx - 22*u, y, cx + 22*u, y], fill=c, width=int(3*u))
    elif name == "fan":
        for k in range(3):
            a = math.radians(k * 120 - 90)
            bx, by = cx + math.cos(a) * 17*u, cy + 2*u + math.sin(a) * 17*u
            d.ellipse([bx - 12*u, by - 12*u, bx + 12*u, by + 12*u], fill=white)
        d.ellipse([cx - 7*u, cy - 5*u, cx + 7*u, cy + 9*u], fill=c)
        d.ellipse([cx - 3.5*u, cy - 1.5*u, cx + 3.5*u, cy + 5.5*u], fill=white)
    elif name == "bell":
        d.pieslice([cx - 22*u, cy - 30*u, cx + 22*u, cy + 14*u], 180, 360, fill=white)
        d.polygon([(cx - 22*u, cy - 8*u), (cx + 22*u, cy - 8*u), (cx + 28*u, cy + 18*u), (cx - 28*u, cy + 18*u)], fill=white)
        d.ellipse([cx - 7*u, cy + 18*u, cx + 7*u, cy + 31*u], fill=white)
        d.ellipse([cx - 4*u, cy - 36*u, cx + 4*u, cy - 28*u], fill=white)
    elif name == "home":
        d.polygon([(cx, cy - 32*u), (cx + 34*u, cy - 2*u), (cx - 34*u, cy - 2*u)], fill=white)
        d.rectangle([cx - 22*u, cy - 4*u, cx + 22*u, cy + 28*u], fill=white)
        d.rounded_rectangle([cx - 6*u, cy + 8*u, cx + 6*u, cy + 28*u], 2*u, fill=c)
    elif name == "music":
        d.ellipse([cx - 22*u, cy + 8*u, cx - 2*u, cy + 26*u], fill=white)
        d.ellipse([cx + 8*u, cy + 2*u, cx + 28*u, cy + 20*u], fill=white)
        d.rectangle([cx - 5*u, cy - 26*u, cx - 1*u, cy + 18*u], fill=white)
        d.rectangle([cx + 25*u, cy - 32*u, cx + 29*u, cy + 12*u], fill=white)
        d.polygon([(cx - 5*u, cy - 26*u), (cx + 29*u, cy - 34*u), (cx + 29*u, cy - 22*u), (cx - 5*u, cy - 14*u)], fill=white)
    elif name == "flame":
        d.polygon([(cx, cy - 34*u), (cx + 22*u, cy - 2*u), (cx + 18*u, cy + 22*u), (cx, cy + 30*u),
                   (cx - 18*u, cy + 22*u), (cx - 22*u, cy - 2*u), (cx - 8*u, cy - 14*u)], fill=white)
        d.ellipse([cx - 9*u, cy + 6*u, cx + 9*u, cy + 28*u], fill=c)
    elif name == "snow":
        for k in range(3):
            a = math.radians(k * 60)
            dx, dy = math.cos(a) * 30*u, math.sin(a) * 30*u
            d.line([cx - dx, cy + 0*u - dy, cx + dx, cy + 0*u + dy], fill=white, width=lw)
        for k in range(6):
            a = math.radians(k * 60)
            ex, ey = cx + math.cos(a) * 30*u, cy + math.sin(a) * 30*u
            d.ellipse([ex - 4*u, ey - 4*u, ex + 4*u, ey + 4*u], fill=white)
    elif name == "bolt":
        d.polygon([(cx + 6*u, cy - 34*u), (cx - 22*u, cy + 6*u), (cx - 3*u, cy + 6*u),
                   (cx - 9*u, cy + 32*u), (cx + 22*u, cy - 8*u), (cx + 3*u, cy - 8*u)], fill=white)
    else:  # dot
        d.ellipse([cx - 24*u, cy - 24*u, cx + 24*u, cy + 24*u], outline=white, width=lw)
        d.ellipse([cx - 9*u, cy - 9*u, cx + 9*u, cy + 9*u], fill=white)


def make_tile(name, pressed):
    top, bot = PALETTE[name]
    if pressed:
        top = tuple(int(v * 0.62) for v in top)
        bot = tuple(int(v * 0.62) for v in bot)
    w = W * S
    base = gradient(w, top, bot).convert("RGBA")

    # lucentezza in alto
    gloss = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    gd = ImageDraw.Draw(gloss)
    gd.ellipse([-w * 0.25, -w * 0.9, w * 1.25, w * 0.42], fill=(255, 255, 255, 46))
    base = Image.alpha_composite(base, gloss)

    # glifo con ombra morbida
    mid = tuple((top[i] + bot[i]) // 2 for i in range(3)) + (255,)
    sh = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    glyph(name, ImageDraw.Draw(sh), mid, w)
    shadow = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    alpha = sh.split()[3].filter(ImageFilter.GaussianBlur(3 * S))
    shadow.putalpha(alpha.point(lambda a: int(a * 0.35)))
    shadow = shadow.transform(shadow.size, Image.AFFINE, (1, 0, 0, 0, 1, -3 * S))
    base = Image.alpha_composite(base, shadow)
    gl = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    glyph(name, ImageDraw.Draw(gl), mid, w)
    base = Image.alpha_composite(base, gl)

    # bordo interno sottile
    ov = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    ImageDraw.Draw(ov).rounded_rectangle([S, S, w - S - 1, w - S - 1], R * S, outline=(255, 255, 255, 70), width=2 * S)
    base = Image.alpha_composite(base, ov)

    # maschera angoli arrotondati
    mask = Image.new("L", (w, w), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, w - 1], R * S, fill=255)
    base.putalpha(mask)
    return base.resize((W, W), Image.LANCZOS)


def make_app_icon():
    size = 192 * 2
    img = gradient(size, (60, 70, 120), (18, 20, 40)).convert("RGBA")
    d = ImageDraw.Draw(img)
    cols = [(110, 220, 255), (255, 190, 100), (140, 235, 140), (255, 130, 200)]
    g = size // 8
    for i in range(4):
        x = size // 2 + (i % 2 - 1) * (g * 2 + g // 4) + g // 8
        y = size // 2 + (i // 2 - 1) * (g * 2 + g // 4) + g // 8
        d.rounded_rectangle([x + g // 8, y + g // 8, x + g * 2 - g // 8, y + g * 2 - g // 8], g // 2.5, fill=cols[i])
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    img.putalpha(mask)
    return img.resize((192, 192), Image.LANCZOS)


def main():
    img_dir = os.path.join(OUT, "image")
    os.makedirs(img_dir, exist_ok=True)
    for name in PALETTE:
        make_tile(name, False).save(os.path.join(img_dir, f"{name}.png"))
        make_tile(name, True).save(os.path.join(img_dir, f"{name}_p.png"))
    make_app_icon().save(os.path.join(OUT, "icon.png"))
    print("icone generate:", len(PALETTE) * 2 + 1)


if __name__ == "__main__":
    main()
