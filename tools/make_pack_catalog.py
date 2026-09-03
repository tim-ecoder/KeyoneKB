#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Каталог пакетов для клавиатуры.

Размеры берутся из собранных APK, а не пишутся руками: каталог — единственное,
по чему клавиатура (у неё нет сети) судит о версиях и объёме загрузки, и
расхождение с релизом заметить было бы негде.

    python3 tools/make_pack_catalog.py
"""
import json, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/assets/language_packs.json")
APKS = os.path.join(ROOT, "langpack/build/outputs/apk")
RELEASE = "https://github.com/tim-ecoder/K12KB/releases"
TAG = RELEASE + "/download/lang-packs/"

TITLES = {"en": "English", "fr": "Français", "de": "Deutsch", "ru": "Русский"}
# Чем язык описывается в каталоге. Сам список языков читается из
# langpack/build.gradle — здесь только текст, который негде взять автоматически.
CONTENTS = {
    "en": "QWERTY; словарь 307k и биграммы; кеш на 35k внутри; переводу нужен второй язык",
    "fr": "QWERTY, AZERTY; словарь 612k, биграммы, кеш на 35k; fr\u2194en 469k / 252k с фразами",
    "de": "QWERTY, QWERTZ; словарь 882k, биграммы, кеш на 35k; de\u2194en 364k / 242k с фразами",
    "ru": "Русский, транслит, украинский; словарь 668k, биграммы, кеш на 35k; ru\u2194en 443k / 227k",
}

CONTENT = [
    ("base", "", "Языковой пакет", None),
    ("idx150", ".idx150", "Кеш словаря 150k", "Готовый кеш на 150 000 слов — значение настройки по умолчанию; без него соберётся сам за 5–15 секунд"),
    ("idx300", ".idx300", "Кеш словаря 300k", "Готовый кеш на 300 000 слов; без него соберётся сам за 5–15 секунд"),
    ("idxfull", ".idxfull", "Кеш словаря полный", "Готовый кеш по всему словарю языка; без него соберётся сам за 5–15 секунд"),
]


def versions():
    """packLanguages из langpack/build.gradle — единственный список языков.

    Разбор не опирается на отступы и запятые: одна регулярка на всю запись
    языка, иначе перенос строки или пропущенная запятая молча теряли язык.
    """
    text = open(os.path.join(ROOT, "langpack/build.gradle"), encoding="utf-8").read()
    entry = re.compile(
        r'(\w+)\s*:\s*\[\s*title\s*:\s*"([^"]+)"\s*,\s*langs\s*:\s*"([^"]+)"\s*,'
        r'\s*(extra\s*:\s*true\s*,)?\s*'
        r'\s*base\s*:\s*\[code\s*:\s*(\d+)\s*,\s*name\s*:\s*"([^"]+)"\]\s*,'
        r'\s*idx\s*:\s*\[code\s*:\s*(\d+)\s*,\s*name\s*:\s*"([^"]+)"\]\s*\]', re.S)
    out = {}
    for lang, title, _langs, extra, bcode, bname, icode, iname in entry.findall(text):
        out[lang] = {"title": title,
                     "extra": bool(extra),
                     "base": (int(bcode), bname),
                     "idx": (int(icode), iname)}
    if not out:
        raise SystemExit("не разобрал packLanguages в langpack/build.gradle")
    return out


def apk_version(path):
    """versionCode из самого APK: каталог обязан описывать то, что выложено."""
    aapt = None
    sdk = os.environ.get("ANDROID_HOME") or os.path.expanduser("~/android-sdk")
    for root, _dirs, files in os.walk(os.path.join(sdk, "build-tools")):
        if "aapt" in files:
            aapt = os.path.join(root, "aapt")
            break
    if not aapt:
        return None
    try:
        out = subprocess.check_output([aapt, "dump", "badging", path],
                                      stderr=subprocess.DEVNULL).decode("utf-8", "replace")
    except Exception:
        return None
    m = re.search(r"versionCode='(\d+)'", out)
    return int(m.group(1)) if m else None


def main():
    langs = versions()
    # Языки с extra в каталог не попадают: их APK выкладываются отдельно, и
    # клавиатура узнаёт о них только по факту установки.
    langs = dict((k, v) for k, v in langs.items() if not v["extra"])
    missing = [lang for lang in langs if lang not in CONTENTS]
    if missing:
        # Язык собирается, а описания для каталога нет — на экране установки он
        # появился бы безымянной строкой.
        raise SystemExit("нет описания в CONTENTS для: " + ", ".join(sorted(missing)))
    packs = []
    for lang in langs:
        items = []
        for flavor, suffix, label, note in CONTENT:
            apk = os.path.join(APKS, lang + flavor[0].upper() + flavor[1:],
                               "release", "K12KB-%s%s%s.apk" % (lang, flavor[0].upper(), flavor[1:]))
            if not os.path.exists(apk):
                print("нет APK: " + apk, file=sys.stderr)
                return 1
            code, name = langs[lang]["base" if flavor == "base" else "idx"]
            built = apk_version(apk)
            if built is not None and built != code:
                raise SystemExit("%s собран с versionCode %d, а в build.gradle %d — "
                                 "пересоберите пакет" % (os.path.basename(apk), built, code))
            items.append({
                "kind": flavor,
                "title": label,
                "package": "com.ai10.k12kb.lang." + lang + suffix,
                "version-code": code,
                "version-name": name,
                "size-mb": int(round(os.path.getsize(apk) / 1048576.0)),
                "contents": note if note else CONTENTS[lang],
                "url": TAG + os.path.basename(apk),
            })
        packs.append({"language": lang, "title": langs[lang]["title"], "items": items})
    data = {"release-page": RELEASE + "/tag/lang-packs", "packs": packs}
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("записан " + OUT)
    return 0


sys.exit(main())
