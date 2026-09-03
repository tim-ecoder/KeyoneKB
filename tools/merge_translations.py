#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Сборка словарей перевода языка: прямое направление и обратное.

Прямое (язык → английский) складывается из выгрузки Викисловаря (леммы и
словоформы) и WikDict: первый даёт объём, второй — статьи, которых в нём нет.

Обратное (английский → язык) получается разворотом прямого: у английского
слова собираются все слова языка, чей перевод на него указывает. Порядок при
этом важен — первым идёт то, что чаще встречается в языке, поэтому нужен
частотный словарь. Английские словоформы (ate, houses, walking) получают
переводы своей леммы из списка AGID, как это сделано у французского и
немецкого.

    python3 tools/merge_translations.py it \\
        kaikki.tsv wikdict_it_en.tsv wikdict_en_it.tsv \\
        it_base.txt en_base.txt agid_infl.txt out_dir

Пишет out_dir/<язык>_en.tsv и out_dir/en_<язык>.tsv.
"""
import os
import re
import sys

MAX_TRANSLATIONS = 8


def read_tsv(path):
    out = {}
    if not path or not os.path.exists(path):
        return out
    for line in open(path, encoding="utf-8", errors="replace"):
        if "\t" not in line:
            continue
        k, v = line.rstrip("\n").split("\t", 1)
        k = k.strip().lower()
        vals = [t.strip() for t in v.split(",") if t.strip()]
        if not k or not vals:
            continue
        cur = out.setdefault(k, [])
        for t in vals:
            if t not in cur:
                cur.append(t)
    return out


def read_freq(path):
    out = {}
    for line in open(path, encoding="utf-8", errors="replace"):
        p = line.rstrip("\n").split("\t")
        if len(p) == 2:
            try:
                out[p[0]] = int(p[1])
            except ValueError:
                pass
    return out


def read_agid(path):
    """Английская словоформа -> лемма."""
    forms = {}
    for line in open(path, encoding="utf-8", errors="replace"):
        if ":" not in line:
            continue
        head, rest = line.split(":", 1)
        lemma = head.split()[0].strip().lower()
        for chunk in rest.replace("|", ",").split(","):
            word = chunk.strip().split(" ")[0].strip("?~! ").lower()
            if word and word != lemma and re.match(r"^[a-z'-]+$", word):
                forms.setdefault(word, lemma)
    return forms


def write(path, table, freq, note):
    rows = []
    for word, trans in table.items():
        if not trans:
            continue
        rows.append((freq.get(word, 0), word, trans[:MAX_TRANSLATIONS]))
    rows.sort(key=lambda r: -r[0])
    with open(path, "w", encoding="utf-8") as f:
        for _fr, word, trans in rows:
            f.write(word + "\t" + ", ".join(trans) + "\n")
    print("%s: %d строк (%s)" % (os.path.basename(path), len(rows), note))


def main(lang, kaikki, wik_fwd, wik_rev, lang_freq_path, en_freq_path, agid, out_dir):
    lang_freq = read_freq(lang_freq_path)
    en_freq = read_freq(en_freq_path)

    fwd = read_tsv(kaikki)
    for k, v in read_tsv(wik_fwd).items():
        cur = fwd.setdefault(k, [])
        for t in v:
            if t not in cur:
                cur.append(t)

    # Разворот: английскому слову — слова языка, начиная с самых частотных.
    rev = {}
    for word, trans in sorted(fwd.items(), key=lambda kv: -lang_freq.get(kv[0], 0)):
        for t in trans:
            t = t.strip().lower()
            if not t or "\t" in t:
                continue
            cur = rev.setdefault(t, [])
            if word not in cur:
                cur.append(word)
    for k, v in read_tsv(wik_rev).items():
        cur = rev.setdefault(k, [])
        for t in v:
            if t not in cur:
                cur.append(t)

    # Английские словоформы наследуют переводы леммы: без этого "eating" не
    # переводится, хотя "eat" переводится.
    inherited = 0
    for form, lemma in read_agid(agid).items():
        if form in rev:
            continue
        base = rev.get(lemma)
        if base:
            rev[form] = list(base)
            inherited += 1

    write(os.path.join(out_dir, "%s_en.tsv" % lang), fwd, lang_freq, "Викисловарь + WikDict")
    write(os.path.join(out_dir, "en_%s.tsv" % lang), rev, en_freq,
          "разворот + WikDict + %d английских словоформ" % inherited)


if len(sys.argv) != 9:
    raise SystemExit(__doc__)
main(*sys.argv[1:])
