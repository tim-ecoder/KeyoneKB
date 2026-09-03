#!/usr/bin/env python3
"""Значки языка для статус-бара — на все коды ISO 639-1.

Слот значка клавиатуры (showStatusIcon) принимает только ресурс из APK самой
клавиатуры: картинку из языкового пакета туда передать нельзя. Поэтому значки
рисуются заранее для любого двухбуквенного кода, а пакет лишь объявляет свой
код языка — новый язык получает значок без пересборки клавиатуры.

Три состояния, как у прежних значков: ru | Ru с короткой чертой | RU с длинной.
"""
import os, sys
from PIL import Image, ImageDraw, ImageFont

# Растровый шрифт X11 misc-fixed: буквы в нём изначально нарисованы по клеткам,
# без кривых и засечек — квадратнее и проще уже некуда. Векторные шрифты для
# такого размера приходилось растрировать самим, и рисунок каждый раз плыл.
_FONT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fonts")
# Terminus Bold: растровый шрифт со штрихом в две клетки — тем же зерном, что у
# клавиатуры на значке. Берём самый крупный кегль, который влезает: у длинных
# подписей и там, где под подписью ещё рисунок (навигация), получится мельче.
FONTS = [(os.path.join(_FONT_DIR, f"ter-u{px}b_unicode.pcf"), px) for px in (28, 24, 22, 16, 14, 12)]
SIZE = 36
FG = (255, 255, 255, 255)

# ISO 639-1
CODES = """aa ab ae af ak am an ar as av ay az ba be bg bh bi bm bn bo br bs ca ce ch
co cr cs cu cv cy da de dv dz ee el en eo es et eu fa ff fi fj fo fr fy ga gd gl
gn gu gv ha he hi ho hr ht hu hy hz ia id ie ig ii ik io is it iu ja jv ka kg ki
kj kk kl km kn ko kr ks ku kv kw ky la lb lg li ln lo lt lu lv mg mh mi mk ml mn
mr ms mt my na nb nd ne ng nl nn no nr nv ny oc oj om or os pa pi pl ps pt qu rm
rn ro ru rw sa sc sd se sg si sk sl sm sn so sq sr ss st su sv sw ta te tg th ti
tk tl tn to tr ts tt tw ty ug uk ur uz ve vi vo wa wo xh yi yo za zh zu""".split()


# Надпись — на письменности самого языка: латиница для языков латиницы,
# кириллица для кириллических, греческий для греческого. Код ISO остаётся
# запасным вариантом для всего прочего.
NATIVE = {
    "ru": "ру", "uk": "ук", "be": "бе", "bg": "бг", "sr": "ср", "mk": "мк",
    "kk": "қа", "ky": "кы", "mn": "мо", "tt": "та", "ba": "ба", "cv": "чӑ",
    "os": "ир", "ce": "но", "av": "ав", "ab": "аҧ", "tg": "тҷ", "kv": "ко", "cu": "цс", "el": "ελ",
}


def keyboard_glyph(root):
    """Клавиатура — исходный глиф из прежних значков, вырезанный пиксель в
    пиксель. Лежит отдельным файлом, а не берётся из ic_eng_small: этот значок
    теперь тоже перерисовывается, и глиф вырезался бы сам из себя."""
    im = Image.open(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                 "keyboard_glyph.png")).convert("RGBA")
    return im.crop(im.getbbox()) if im.getbbox() else im


def pixel_text(text, max_w=32, max_h=21):
    """Надпись по клеткам: растровый шрифт уже нарисован пикселями, остаётся
    выбрать кегль, который влезает, и вырезать по чернилам."""
    for path, px in FONTS:
        f = ImageFont.truetype(path, px)
        probe = Image.new("1", (128, 48), 0)
        d = ImageDraw.Draw(probe)
        d.fontmode = "1"                      # без сглаживания
        l, t, r, b = d.textbbox((0, 0), text, font=f)
        d.text((-l, -t), text, font=f, fill=1)
        bbox = probe.getbbox()
        if not bbox:
            continue
        box = probe.crop(bbox)
        if box.width > max_w or box.height > max_h:
            continue
        im = Image.new("RGBA", box.size, (0, 0, 0, 0))
        src, dst = box.load(), im.load()
        for y in range(box.height):
            for x in range(box.width):
                if src[x, y]:
                    dst[x, y] = FG
        return im
    return None


def sprite(name):
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), name)
    im = Image.open(path).convert("RGBA")
    return im.crop(im.getbbox()) if im.getbbox() else im


def render(text, underline, glyph, extra=None, label_max_h=21):
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    im.paste(glyph, ((SIZE - glyph.width) // 2, SIZE - glyph.height - 1), glyph)

    label = pixel_text(text, max_h=label_max_h)
    if label is not None:
        x0 = (SIZE - label.width) // 2
        im.paste(label, (x0, 0), label)
    else:
        x0 = 4

    if extra is not None:
        # рисунок между подписью и клавиатурой: у навигации это стрелки из
        # прежнего значка — подпись NAV одна их не заменяет
        im.paste(extra, ((SIZE - extra.width) // 2, SIZE - glyph.height - extra.height - 3), extra)

    if underline:
        d = ImageDraw.Draw(im)
        w = label.width if label is not None else SIZE - 8
        # короткая черта — под первой буквой, длинная — под всей надписью
        x1 = x0 + (w // 2 if underline == "first" else w)
        d.rectangle([x0, 19, x1 - 1, 20], fill=FG)
    return im


# Значки режимов клавиатуры: подпись, черта и та же клавиатура внизу.
# Черта означает то же, что у языков: короткая — режим на один символ,
# длинная — режим до отмены.
NAV_ARROWS = ("ic_kb_nav", "ic_kb_nav_fn")

MODES = {
    "ic_kb_alt":      ("ALT", None),
    "ic_kb_alt_one":  ("ALT", "first"),
    "ic_kb_alt_all":  ("ALT", "all"),
    "ic_kb_sym":      ("SYM", None),
    "ic_kb_sym_one":  ("SYM", "first"),
    "ic_kb_sym_all":  ("SYM", "all"),
    "ic_kb_nav":      ("NAV", None),
    "ic_kb_nav_fn":   ("FN", None),  # стрелки добавляются ниже, в NAV_ARROWS
    "ic_kb_digits":   ("123", None),
    # английский — такой же значок языка, но с прежним именем: раскладка
    # встроена в клавиатуру и ищется по имени, а не по коду
    "ic_eng_small":       ("EN", None),
    "ic_eng_shift_first": ("En", "first"),
    "ic_eng_shift_all":   ("EN", "all"),
}


def main(root="."):
    # Одна папка: значков 552, и дубль во второй плотности стоил бы лишние
    # мегабайты. Картинки серо-прозрачные и сжимаются в сотни байт.
    folders = [os.path.join(root, "app/src/main/res/drawable-xhdpi")]
    for f in folders:
        os.makedirs(f, exist_ok=True)
    glyph = keyboard_glyph(root)
    for code in CODES:
        label = NATIVE.get(code, code)
        # Обычное состояние — прописными, как у английского значка: разница
        # между состояниями в черте, а не в регистре надписи. Строчные остаются
        # только у состояния «первая заглавная».
        variants = {
            f"ic_lang_{code}_small": (label.upper(), None),
            f"ic_lang_{code}_shift_first": (label.capitalize(), "first"),
            f"ic_lang_{code}_shift_all": (label.upper(), "all"),
        }
        for name, (text, underline) in variants.items():
            im = render(text, underline, glyph)
            im = im.convert("LA")
            for f in folders:
                im.save(os.path.join(f, name + ".png"), optimize=True)
    # Все значки — в одной плотности. Копия в mipmap-hdpi казалась нужной для
    # уведомления, но система масштабировала её под xhdpi, и подпись выходила
    # в полтора раза крупнее, чем у значков языков, которые лежат только в
    # drawable-xhdpi.
    mode_folders = [os.path.join(root, "app/src/main/res", "drawable-xhdpi")]
    arrows = sprite("nav_arrows.png")
    for name, (text, underline) in MODES.items():
        # у навигации подпись ужимается, чтобы под ней поместились стрелки
        with_arrows = name in NAV_ARROWS
        im = render(text, underline, glyph,
                    extra=arrows if with_arrows else None,
                    label_max_h=11 if with_arrows else 21)
        for f in mode_folders:
            if os.path.isdir(f):
                im.save(os.path.join(f, name + ".png"))

    print(f"нарисовано {len(CODES) * 3} значков на {len(CODES)} языков"
          f" и {len(MODES)} значков режимов")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
