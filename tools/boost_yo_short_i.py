#!/usr/bin/env python3
"""Поднять написания с ё и й над теми же словами без них.

Словарь подсказок отсортирован по убыванию веса, размер кеша — это число первых
строк, а подсказки при равном расстоянии ранжируются по весу. Написание через ё
почти всегда реже: «зеленый» весит 149, «зелёный» — 143, поэтому в панели
предлагалось написание без ё, а при небольшом словаре ё-форма и вовсе не
попадала в индекс.

Слова группируются по свёртке (ё -> е, й -> и). В группе оказываются написания
одного слова: «зеленый» и «зелёный» сворачиваются в «зеленыи». Внутри группы
слову с бо́льшим числом ё и й даётся вес на единицу выше самого тяжёлого слова с
меньшим их числом (потолок 255). Чужие веса не трогаются: у формы без ё остаётся
её собственная частота, меняется только порядок между вариантами.

Группировать по свёртке, а не искать «двойника» отдельным словом, обязательно:
свёртка «зелёного» — «зеленого», это слово в словаре есть, а свёртка «зелёный» —
«зеленыи», которого нет. Прежняя версия скрипта поэтому поднимала одни формы и
пропускала другие.

Запуск идемпотентен: после первого прохода вес уже выше нужного порога.

    python3 tools/boost_yo_short_i.py langpack/src/ruBase/assets/dictionaries/ru_base.txt
"""
import sys
from collections import defaultdict

MAX_WEIGHT = 255

# Насколько редким может быть написание с ё, чтобы всё ещё считаться вариантом
# того же слова, а не опечаткой из корпуса.
SAME_WORD_RATIO = 0.8


def fold(word):
    """Свёртка написаний одного слова: ё -> е, й -> и."""
    return word.replace("ё", "е").replace("й", "и")


def yo_count(word):
    return word.count("ё") + word.count("й")


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

    groups = defaultdict(list)
    for word in words:
        groups[fold(word)].append(word)

    raised = 0
    for members in groups.values():
        if len(members) < 2:
            continue
        # Самое частое написание группы — оно задаёт планку.
        top = max(members, key=lambda w: weight[w])
        richer = [w for w in members if yo_count(w) > yo_count(top)]
        if not richer:
            continue
        best = max(richer, key=lambda w: weight[w])
        # Редкие формы из корпуса («ёл» при «ел», «ёщё» при «еще») — это опечатки
        # чужого набора, а не написание того же слова. Вариантом считаем только
        # то, что встречается сопоставимо часто: вес логарифмический, поэтому
        # доля от планки — это отношение порядков частоты.
        if weight[best] < weight[top] * SAME_WORD_RATIO:
            continue
        new_weight = min(MAX_WEIGHT, weight[top] + 1)
        if new_weight != weight[best]:
            weight[best] = new_weight
            raised += 1

    order = {w: i for i, w in enumerate(words)}
    words.sort(key=lambda w: (-weight[w], order[w]))
    with open(path, "w", encoding="utf-8") as f:
        for word in words:
            f.write("%s\t%d\n" % (word, weight[word]))
    print("%s: слов %d, поднято %d" % (path, len(words), raised))


if __name__ == "__main__":
    main()
