#!/usr/bin/env python3
"""Поднять слова с ё и й над их двойниками через е и и.

Словарь подсказок отсортирован по убыванию веса, а размер кеша задаётся числом
первых строк. Написание через ё почти всегда реже своего двойника: «елка» стоит
30140-й строкой, «ёлка» — 59533-й. При словаре на 35 тысяч слов в индекс попадал
только двойник, и набранное через ё слово находилось лишь как исправление на
расстоянии одной правки — то есть предлагалось через е.

Скрипт даёт таким словам вес двойника плюс единицу (потолок 255) и
пересортировывает файл. Тогда ё- и й-написание не может быть отрезано раньше
своего двойника, а при равном расстоянии идёт первым. На точные совпадения это
не влияет: подсказки сортируются сначала по расстоянию (symspell.c, suggest_cmp).

Запуск идемпотентен: после первого прохода вес уже больше, и второй ничего не
меняет.

    python3 tools/boost_yo_short_i.py langpack/src/ruBase/assets/dictionaries/ru_base.txt
"""
import sys

MAX_WEIGHT = 255


def twin(word):
    """Тот же корень через е и и — то, во что слово превращала прежняя нормализация."""
    return word.replace("ё", "е").replace("й", "и")


def main():
    path = sys.argv[1]
    words = []
    weight = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            word, _, raw = line.rstrip("\n").partition("\t")
            if not word or not raw.strip().isdigit():
                continue
            words.append(word)
            weight[word] = int(raw)

    raised = 0
    for word in words:
        if "ё" not in word and "й" not in word:
            continue
        other = twin(word)
        if other == word or other not in weight:
            continue
        if weight[other] >= weight[word]:
            new = min(MAX_WEIGHT, weight[other] + 1)
            if new != weight[word]:
                weight[word] = new
                raised += 1

    order = {w: i for i, w in enumerate(words)}
    words.sort(key=lambda w: (-weight[w], order[w]))
    with open(path, "w", encoding="utf-8") as f:
        for word in words:
            f.write("%s\t%d\n" % (word, weight[word]))
    print("%s: слов %d, поднято %d" % (path, len(words), raised))


if __name__ == "__main__":
    main()
