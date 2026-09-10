#!/usr/bin/env python3
"""Выбросить из словаря опечатки корпуса вида «ёл» при «ел» и «другои» при «другой».

Словари собраны из субтитров, и там рядом с настоящими словами лежат чужие
опечатки: «ёщё» (вес 53 при «еще» 212), «ёл» (46 при «ел» 142), «краи» (56 при
«край» 161), «другои» (68 при «другой» 186). В подсказках они занимают место
настоящих слов.

Слова группируются по свёртке (ё -> е, й -> и) — в группу попадают написания,
отличающиеся только этими буквами. Из группы выбрасывается всё, что заметно реже
самого частого написания: вес логарифмический, поэтому доля от него — это
отношение порядков частоты.

Порог выбран так, чтобы не задеть редкие настоящие слова: «нёбо» (0.52 от
«небо»), «совершённый» (0.55), «узнаём» (0.66), «падёж» (0.41) остаются.

    python3 tools/drop_corpus_typos.py langpack/src/ruBase/assets/dictionaries/ru_base.txt [--dry-run]
"""
import sys
from collections import defaultdict

# Ниже этой доли от самого частого написания группы — считаем опечаткой корпуса.
KEEP_RATIO = 0.4


def fold(word):
    return word.replace("ё", "е").replace("й", "и")


def main():
    path = sys.argv[1]
    dry = "--dry-run" in sys.argv
    words = []
    weight = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            word, _, raw = line.rstrip("\n").partition("\t")
            if not word or not raw.strip().isdigit():
                continue
            words.append(word)
            weight[word] = int(raw)

    groups = defaultdict(list)
    for word in words:
        groups[fold(word)].append(word)

    drop = set()
    for members in groups.values():
        if len(members) < 2:
            continue
        top = max(weight[w] for w in members)
        for word in members:
            if weight[word] < top * KEEP_RATIO:
                drop.add(word)

    if dry:
        sample = sorted(drop, key=lambda w: -weight[w])
        print("выбросили бы %d слов, самые частые из них:" % len(drop))
        for word in sample[:25]:
            print("   %-16s %3d" % (word, weight[word]))
        return

    kept = [w for w in words if w not in drop]
    with open(path, "w", encoding="utf-8") as f:
        for word in kept:
            f.write("%s\t%d\n" % (word, weight[word]))
    print("%s: было %d, выброшено %d, осталось %d"
          % (path, len(words), len(drop), len(kept)))


if __name__ == "__main__":
    main()
