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


def normalize(word):
    lower = "".join(APOSTROPHES.get(c, c) for c in word.lower())
    out = []
    for c in unicodedata.normalize("NFD", lower):
        if unicodedata.category(c) == "Mn":
            continue
        if c.isalnum() or c in EXTRA:
            out.append(c)
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
