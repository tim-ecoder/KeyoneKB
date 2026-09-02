#!/usr/bin/env python3
"""Иконки приложения и языковых пакетов.

Клавиатура рисуется фигурами под конкретный размер, а не увеличением пиксельного
глифа: прямые углы и отсутствие сглаживания сохраняют пиксельный характер, но
края остаются резкими на любой плотности экрана.
"""
import os, sys
from PIL import Image, ImageDraw, ImageFont

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf"
BG = (30, 36, 48, 255)
FG = (255, 255, 255, 255)
DENS = {"mipmap-hdpi": 72, "mipmap-xhdpi": 96, "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192, "drawable-xhdpi": 96}


def keyboard(d, x, y, w, h):
    cols, rows = 6, 3
    gap = max(1, round(w * 0.026))
    pad = max(1, round(w * 0.05))
    d.rectangle([x, y, x + w, y + h], fill=FG)
    kw = (w - 2 * pad - (cols - 1) * gap) / cols
    kh = (h - 2 * pad - rows * gap) / (rows + 1)
    for r in range(rows):
        for c in range(cols):
            kx = x + pad + c * (kw + gap)
            ky = y + pad + r * (kh + gap)
            d.rectangle([round(kx), round(ky), round(kx + kw), round(ky + kh)], fill=BG)
    sx = x + pad + kw + gap
    sy = y + pad + rows * (kh + gap)
    d.rectangle([round(sx), round(sy), round(x + w - pad - kw - gap), round(sy + kh)], fill=BG)


def icon(text, canvas, round_icon=False):
    im = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    if round_icon:
        d.ellipse([0, 0, canvas - 1, canvas - 1], fill=BG)
        kb_w, text_limit = canvas * 0.68, canvas * 0.62
    else:
        d.rounded_rectangle([0, 0, canvas - 1, canvas - 1], radius=canvas // 5, fill=BG)
        kb_w, text_limit = canvas * 0.84, canvas * 0.84

    kb_h = kb_w * 0.50
    kb_y = canvas * 0.50
    keyboard(d, round((canvas - kb_w) / 2), round(kb_y), round(kb_w), round(kb_h))

    # Надпись берём максимально возможную. У круглой иконки предел не постоянный:
    # доступная ширина зависит от того, на какой высоте окажется строка, — считаем
    # хорду круга по верхней и нижней границам текста.
    gap_above_kb = canvas * 0.10
    top_margin = canvas * 0.035
    size = int(kb_y)
    while size > 6:
        f = ImageFont.truetype(FONT, size)
        l, t, r, b = d.textbbox((0, 0), text, font=f)
        tw, th = r - l, b - t
        ty = kb_y - gap_above_kb - th          # верх строки
        if ty >= top_margin:
            if round_icon:
                cx = cy = canvas / 2.0
                rad = canvas / 2.0 - canvas * 0.04
                dy = max(abs(ty - cy), abs(ty + th - cy))
                allowed = 2 * (rad ** 2 - dy ** 2) ** 0.5 if dy < rad else 0
            else:
                allowed = text_limit
            if tw <= allowed:
                break
        size -= 1
    l, t, r, b = d.textbbox((0, 0), text, font=f)
    tw, th = r - l, b - t
    d.text(((canvas - tw) // 2 - l, round(kb_y - gap_above_kb - th) - t),
           text, font=f, fill=FG)
    return im


def write(res_dir, text, skip=()):
    for folder, size in DENS.items():
        path = os.path.join(res_dir, folder)
        if folder in skip or not os.path.isdir(path):
            if folder in skip:
                continue
            os.makedirs(path, exist_ok=True)
        icon(text, size).save(os.path.join(path, "ic_launcher.png"))
        icon(text, size, True).save(os.path.join(path, "ic_launcher_round.png"))


if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    write(os.path.join(root, "app/src/main/res"), "K12KB")
    for lang, label in (("fr", "FR"), ("de", "DE"), ("ru", "RU")):
        write(os.path.join(root, f"langpack/src/{lang}/res"), label, skip=("drawable-xhdpi",))
    print("иконки перерисованы")
