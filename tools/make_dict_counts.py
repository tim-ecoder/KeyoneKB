#!/usr/bin/env python3
"""Счётчики словарей перевода для экрана настроек.

Экран «Предсказания» показывает, сколько в каждом направлении слов и фраз.
Раньше он считал это сам, разбирая .tsv построчно при каждом открытии: при
шести установленных пакетах — двенадцать файлов и больше сотни мегабайт
разжатого чтения. Числа не меняются после сборки, поэтому считаются здесь.

Счёт идёт по .cdb, а не по .tsv: в APK едет именно .cdb, и число, взятое из
него, не разойдётся с тем, что клавиатура на самом деле найдёт.

    python3 tools/make_dict_counts.py           # все пакеты и сама клавиатура
    python3 tools/make_dict_counts.py <каталог> # один каталог с .cdb
"""

import json
import os
import struct
import sys


def cdb_counts(path):
    """(слов, фраз) в CDB: ключ с пробелом — фраза."""
    with open(path, 'rb') as f:
        header = f.read(2048)
        if len(header) != 2048:
            raise ValueError("%s: обрезанный заголовок" % path)
        # Записи лежат между заголовком и первой хеш-таблицей.
        end = min(struct.unpack('<' + 'II' * 256, header)[0::2])
        if end < 2048:
            raise ValueError("%s: хеш-таблица залезает в заголовок" % path)
        words = phrases = 0
        pos = 2048
        f.seek(pos)
        while pos < end:
            rec = f.read(8)
            if len(rec) != 8:
                raise ValueError("%s: записи обрываются на %d" % (path, pos))
            klen, vlen = struct.unpack('<II', rec)
            key = f.read(klen)
            if len(key) != klen:
                raise ValueError("%s: ключ обрывается на %d" % (path, pos))
            f.seek(vlen, os.SEEK_CUR)
            if b' ' in key:
                phrases += 1
            else:
                words += 1
            pos += 8 + klen + vlen
        if pos != end:
            raise ValueError("%s: записи не сошлись с началом таблиц" % path)
    return words, phrases


def write_counts(dict_dir):
    """<пара>.count.json рядом с каждым .cdb этого каталога.

    Файл на пару, а не один на пакет: клавиатура ищет ассет по имени и берёт
    первый пакет, где он нашёлся. Общий файл отдал бы числа одного языка на
    все остальные.
    """
    names = sorted(n for n in os.listdir(dict_dir) if n.endswith('.cdb'))
    if not names:
        return False
    for name in names:
        words, phrases = cdb_counts(os.path.join(dict_dir, name))
        out = os.path.join(dict_dir, name[:-4] + '.count.json')
        with open(out, 'w', encoding='utf-8') as f:
            json.dump({"words": words, "phrases": phrases}, f, sort_keys=True)
            f.write('\n')
        print("  %s: %d слов, %d фраз -> %s"
              % (name, words, phrases, os.path.basename(out)))
    return True


def main():
    if len(sys.argv) == 2:
        write_counts(sys.argv[1])
        return

    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    # Каталоги с .cdb: пакеты языков и assets самой клавиатуры.
    roots = [os.path.join(base, 'app', 'src', 'main', 'assets', 'dict')]
    langsrc = os.path.join(base, 'langpack', 'src')
    for name in sorted(os.listdir(langsrc)):
        roots.append(os.path.join(langsrc, name, 'assets', 'dict'))
    done = 0
    for root in roots:
        if not os.path.isdir(root):
            continue
        print(root)
        if write_counts(root):
            done += 1
    if done == 0:
        print("не нашёл ни одного каталога с .cdb", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()
