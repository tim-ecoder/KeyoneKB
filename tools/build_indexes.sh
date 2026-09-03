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

for lang in en fr de ru; do
    case $lang in
        en) base=app/src/main/assets/dictionaries/en_base.txt
            bigrams=app/src/main/assets/dictionaries/en_bigrams.json ;;
        *)  base=langpack/src/${lang}Base/assets/dictionaries/${lang}_base.txt
            bigrams=langpack/src/${lang}Base/assets/dictionaries/${lang}_bigrams.json ;;
    esac

    python3 tools/prepare_dict.py "$base" "$WORK/$lang.tsv" "$bigrams" "$WORK/$lang-bg.tsv"

    # 35k едет в самом языковом пакете: это значение по умолчанию,
    # с ним подсказки работают сразу после установки, без сборки кеша.
    for pair in "35000:Base:35000" "150000:Idx150:150000" "300000:Idx300:300000" "0:Idxfull:full"; do
        limit=${pair%%:*}; rest=${pair#*:}; flavor=${rest%%:*}; name=${rest##*:}
        out="langpack/src/${lang}${flavor}/assets/dictionaries"
        mkdir -p "$out"
        "$WORK/build_ssnd" "$WORK/$lang.tsv" "$out/$lang-$name.ssnd" "$limit" "$WORK/$lang-bg.tsv"
    done
done
