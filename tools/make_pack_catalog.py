#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Каталог пакетов для клавиатуры.

Размеры берутся из собранных APK, а не пишутся руками: каталог — единственное,
по чему клавиатура (у неё нет сети) судит о версиях и объёме загрузки, и
расхождение с релизом заметить было бы негде.

    python3 tools/make_pack_catalog.py
"""
import json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/assets/language_packs.json")
APKS = os.path.join(ROOT, "langpack/build/outputs/apk")
RELEASE = "https://github.com/tim-ecoder/K12KB/releases"
TAG = RELEASE + "/download/lang-packs/"

LANGS = [
    ("en", "English", "QWERTY; словарь 307k и биграммы; кеш на 35k внутри; переводу нужен второй язык"),
    ("fr", "Français", "QWERTY, AZERTY; словарь 612k, биграммы, кеш на 35k; fr↔en 469k / 252k с фразами"),
    ("de", "Deutsch", "QWERTY, QWERTZ; словарь 882k, биграммы, кеш на 35k; de↔en 364k / 242k с фразами"),
    ("ru", "Русский", "Русский, транслит, украинский; словарь 668k, биграммы, кеш на 35k; ru↔en 443k / 227k"),
]
CONTENT = [
    ("base", "", "Языковой пакет", None),
    ("idx150", ".idx150", "Кеш словаря 150k", "Готовый кеш на 150 000 слов — значение настройки по умолчанию; без него соберётся сам за 5–15 секунд"),
    ("idx300", ".idx300", "Кеш словаря 300k", "Готовый кеш на 300 000 слов; без него соберётся сам за 5–15 секунд"),
    ("idxfull", ".idxfull", "Кеш словаря полный", "Готовый кеш по всему словарю языка; без него соберётся сам за 5–15 секунд"),
]


def versions():
    """packVersions живёт в langpack/build.gradle — второй копии быть не должно."""
    text = open(os.path.join(ROOT, "langpack/build.gradle"), encoding="utf-8").read()
    block = re.search(r"packVersions\s*=\s*\[(.*?)\n    \]", text, re.S).group(1)
    out = {}
    for lang, rest in re.findall(r"(\w+):\s*\[(base:.*?)\],\n", block + "\n"):
        kinds = dict((k, (int(c), n)) for k, c, n in
                     re.findall(r"(base|idx):\s*\[code:\s*(\d+),\s*name:\s*\"([^\"]+)\"\]", rest))
        out[lang] = kinds
    return out


def main():
    vers = versions()
    packs = []
    for lang, title, contents in LANGS:
        items = []
        for flavor, suffix, label, note in CONTENT:
            apk = os.path.join(APKS, lang + flavor[0].upper() + flavor[1:],
                               "release", "K12KB-%s%s%s.apk" % (lang, flavor[0].upper(), flavor[1:]))
            if not os.path.exists(apk):
                print("нет APK: " + apk, file=sys.stderr)
                return 1
            code, name = vers[lang]["base" if flavor == "base" else "idx"]
            items.append({
                "kind": flavor,
                "title": label,
                "package": "com.ai10.k12kb.lang." + lang + suffix,
                "version-code": code,
                "version-name": name,
                "size-mb": int(round(os.path.getsize(apk) / 1048576.0)),
                "contents": note if note else contents,
                "url": TAG + os.path.basename(apk),
            })
        packs.append({"language": lang, "title": title, "items": items})
    data = {"release-page": RELEASE + "/tag/lang-packs", "packs": packs}
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("записан " + OUT)
    return 0


sys.exit(main())
