#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Значок приложения из готовых картинок.

Берёт квадратный и круглый варианты, убирает фон по углам и раскладывает по
плотностям mipmap. Фон снимается заливкой от углов, а не по цвету: белое есть
и внутри рисунка, и вырезать его целиком нельзя.

    python3 tools/import_app_icon.py ~/Downloads/ic_launcher.tif \\
        ~/Downloads/ic_launcher_round.tif app/src/main/res
"""
import os
import sys
from collections import deque

from PIL import Image

DENSITIES = (("mipmap-hdpi", 72), ("mipmap-xhdpi", 96),
             ("mipmap-xxhdpi", 144), ("mipmap-xxxhdpi", 192))
NEAR_WHITE = 232          # всё светлее считаем фоном


def drop_background(im):
    """Прозрачность там, куда дотекает заливка от углов."""
    im = im.convert("RGBA")
    w, h = im.size
    px = im.load()
    seen = [[False] * w for _ in range(h)]
    q = deque()
    for start in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        q.append(start)
    while q:
        x, y = q.popleft()
        if x < 0 or y < 0 or x >= w or y >= h or seen[y][x]:
            continue
        r, g, b, _a = px[x, y]
        if min(r, g, b) < NEAR_WHITE:
            continue
        seen[y][x] = True
        # Цвет прозрачных точек берём тёмный: при уменьшении LANCZOS смешивает
        # соседей вместе с их цветом, и белый фон давал бы светлый ореол.
        px[x, y] = (0, 0, 0, 0)
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            # Проверяем до постановки в очередь: иначе каждая точка попадает в
            # неё по четыре раза, и на большой картинке очередь съедает память.
            if 0 <= nx < w and 0 <= ny < h and not seen[ny][nx]:
                q.append((nx, ny))
    return im


def write(im, res_dir, name):
    for folder, size in DENSITIES:
        path = os.path.join(res_dir, folder)
        os.makedirs(path, exist_ok=True)
        # LANCZOS: рисунок не пиксельный, уменьшение ближайшим соседом рвало бы
        # тонкие линии клавиатуры на значке
        im.resize((size, size), Image.LANCZOS).save(
            os.path.join(path, name + ".png"), optimize=True)
    print("%s: %s" % (name, ", ".join("%s %dpx" % (f, s) for f, s in DENSITIES)))


def main(square, round_, res_dir):
    write(drop_background(Image.open(square)), res_dir, "ic_launcher")
    write(drop_background(Image.open(round_)), res_dir, "ic_launcher_round")


if len(sys.argv) != 4:
    raise SystemExit(__doc__)
main(*sys.argv[1:])
