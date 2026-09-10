#!/usr/bin/env python3
"""Подготовка словаря для build_ssnd.

Нормализация повторяет WordDictionary.normalize из приложения: нижний регистр,
единый апостроф, разложение NFD со снятием диакритики, оставляем только
буквы, цифры и ' @ . - _ . Повторять это на C значило бы тащить юникодные
таблицы, поэтому шаг вынесен сюда.

    python3 tools/prepare_dict.py ru_base.txt ru_prepared.tsv [ru_bigrams.json bigrams.tsv]
"""
import json
import sys
import unicodedata

APOSTROPHES = {"‘": "'", "’": "'", "ʼ": "'"}
EXTRA = set("'@.-_")


def _is_cyrillic(c):
    """Кириллица, включая расширения: у этих букв диакритику снимать нельзя."""
    return "\u0400" <= c <= "\u052f"


def normalize(word):
    """То же, что WordDictionary.normalize в приложении.

    Нижний регистр, единый апостроф, снятие диакритики через NFD, остаются
    только буквы, цифры и ' @ . - _ .

    Кириллица через NFD не проходит вовсе. Разложение считает ё за «е с
    диерезисом», а й за «и с бреве», и снятие диакритики склеивало разные слова:
    «всё» с «все», «свой» со «свои», «отношений» с «отношении». SymSpell хранит
    один оригинал на ключ, поэтому в индекс попадало только частотное написание,
    а второе слово пропадало совсем — 9934 слова из русского словаря, из них
    6917 из-за ё и 2987 из-за й. Нечёткий поиск не страдает: буквы остаются на
    расстоянии одной правки.
    """
    lower = "".join(APOSTROPHES.get(c, c) for c in word.lower())
    out = []
    for c in lower:
        if unicodedata.category(c) == "Mn":
            continue
        if _is_cyrillic(c):
            if c.isalnum():
                out.append(c)
            continue
        for d in unicodedata.normalize("NFD", c):
            if unicodedata.category(d) == "Mn":
                continue
            if d.isalnum() or d in EXTRA:
                out.append(d)
    return "".join(out)


def main():
    src, dst = sys.argv[1], sys.argv[2]
    with open(src, encoding="utf-8") as f, open(dst, "w", encoding="utf-8") as out:
        n = 0
        for line in f:
            word, _, freq = line.rstrip("\n").partition("\t")
            if not word or not freq.strip().isdigit():
                continue
            norm = normalize(word)
            if not norm:
                continue
            out.write(f"{norm}\t{word}\t{int(freq)}\n")
            n += 1
    print(f"{dst}: {n} слов")

    if len(sys.argv) > 4:
        with open(sys.argv[3], encoding="utf-8") as f:
            data = json.load(f)
        with open(sys.argv[4], "w", encoding="utf-8") as out:
            pairs = 0
            for w1, succ in data.items():
                for w2, freq in succ:
                    out.write(f"{normalize(w1)}\t{normalize(w2)}\t{w2}\t{freq}\n")
                    pairs += 1
        print(f"{sys.argv[4]}: {pairs} биграмм")


if __name__ == "__main__":
    main()
