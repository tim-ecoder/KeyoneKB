#!/bin/bash
# Сборка готовых индексов подсказок для языковых пакетов.
#
# Индексы не хранятся в репозитории: они весят сотни мегабайт и собираются из
# словарей, которые в нём есть. Скрипт кладёт их сразу в нужные варианты сборки.
#
#   bash tools/build_indexes.sh
set -e
cd "$(dirname "$0")/.."

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

gcc -O2 -o "$WORK/build_ssnd" tools/build_ssnd.c \
    app/src/main/jni/symspell.c app/src/main/jni/keyboard_distance.c -lm

# Языки читаются из langpack/build.gradle: свой список здесь неминуемо разошёлся
# бы с флейворами сборки.
LANGS=$(sed -n 's/^ *\([a-z][a-z]*\) *: *\[ *title *:.*/\1/p' langpack/build.gradle)
if [ -z "$LANGS" ]; then
    echo "не разобрал packLanguages в langpack/build.gradle" >&2
    exit 1
fi

for lang in $LANGS; do
    # Источник всегда один — копия внутри пакета языка. У английского такой же
    # файл лежит и в клавиатуре; собирать индекс из него значило бы, что после
    # правки словаря в пакете индекс молча остаётся от прежнего списка слов.
    base=langpack/src/${lang}Base/assets/dictionaries/${lang}_base.txt
    bigrams=langpack/src/${lang}Base/assets/dictionaries/${lang}_bigrams.json
    for f in "$base" "$bigrams"; do
        if [ ! -s "$f" ]; then
            echo "нет исходных данных: $f" >&2
            exit 1
        fi
    done

    python3 tools/prepare_dict.py "$base" "$WORK/$lang.tsv" "$bigrams" "$WORK/$lang-bg.tsv"
    # Пустой список биграмм означал бы пакет без предсказания следующего слова,
    # и заметить это можно было бы только по строке в логе сборки.
    if [ ! -s "$WORK/$lang-bg.tsv" ]; then
        echo "биграммы для $lang не собрались" >&2
        exit 1
    fi

    # 35k едет в самом языковом пакете: это значение по умолчанию,
    # с ним подсказки работают сразу после установки, без сборки кеша.
    for pair in "35000:Base:35000" "150000:Idx150:150000" "300000:Idx300:300000" "0:Idxfull:full"; do
        limit=${pair%%:*}; rest=${pair#*:}; flavor=${rest%%:*}; name=${rest##*:}
        out="langpack/src/${lang}${flavor}/assets/dictionaries"
        mkdir -p "$out"
        "$WORK/build_ssnd" "$WORK/$lang.tsv" "$out/$lang-$name.ssnd" "$limit" "$WORK/$lang-bg.tsv"
    done
done
