#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Перевод из выгрузки kaikki.org: леммы и словоформы.

Викисловарь описывает словоформу отдельной статьёй без собственного толкования
(«mangiavo — first-person singular imperfect of mangiare»). Объём словаря дают
именно они: у формы нет перевода, поэтому ей раздаются переводы её леммы. Так
собраны французский и немецкий пакеты, см. tools/langpack-data.md.

    python3 tools/kaikki_to_tsv.py kaikki-it.jsonl it_en.tsv

Формат выхода: слово<TAB>перевод1, перевод2, ... (до 8 вариантов).
"""
import json
import re
import sys

MAX_TRANSLATIONS = 8
# Толкование Викисловаря — предложение, а нам нужен перевод: отбрасываем
# пояснения в скобках и всё, что длиннее короткой фразы.
PAREN = re.compile(r"\([^)]*\)")
MAX_WORDS = 4
# Толкования вида «alternative form of X», «plural of X» — не перевод, а ссылка
# на другое слово. Из них получается такая же словоформа, как из form_of: слово
# берёт переводы того, на что ссылается.
LINK = re.compile(
    r"^(?:alternative|apocopic|elided|obsolete|archaic|rare|misspelling|"
    r"abbreviation|acronym|initialism|contraction|pronunciation spelling|"
    r"eye dialect|plural|singular|feminine|masculine|diminutive|augmentative|"
    r"superlative|comparative|past participle|present participle|gerund|"
    r"inflection|form)\b[^:]*?\bof ([^,;:]+)$", re.I)
# «used to indicate…», «see X» — грамматические пометы, переводом не являются.
META = re.compile(r"^(used\b|see\b|synonym of\b|alternative spelling\b)", re.I)


def link_target(gloss):
    """Слово, на которое ссылается толкование, или None."""
    g = PAREN.sub(" ", gloss).strip(" .")
    m = LINK.match(re.sub(r"\s+", " ", g))
    if not m:
        return None
    target = m.group(1).strip().lower()
    return target if target and " " not in target else None


def clean(gloss):
    if META.match(gloss.strip()) or LINK.match(re.sub(r"\s+", " ", PAREN.sub(" ", gloss).strip(" ."))):
        return None
    g = PAREN.sub(" ", gloss)
    g = g.split(";")[0].split(",")[0].strip(" .")
    g = re.sub(r"\s+", " ", g)
    if not g or len(g.split()) > MAX_WORDS:
        return None
    if g.startswith("to "):          # инфинитив без «to» ближе к тому, что ищут
        g = g[3:]
    return g or None


def main(src, dst):
    lemmas = {}     # лемма -> переводы
    forms = []      # (форма, лемма)
    with open(src, encoding="utf-8", errors="replace") as f:
        for line in f:
            try:
                d = json.loads(line)
            except ValueError:
                continue
            if not isinstance(d, dict):
                # Строка-массив или число: выгрузка большая, и падать на ней
                # после часа разбора нельзя.
                continue
            word = (d.get("word") or "").strip().lower()
            if not word or "\t" in word:
                continue
            is_form = False
            got = []
            for s in d.get("senses", []):
                fo = s.get("form_of")
                if fo:
                    is_form = True
                    for item in fo:
                        base = (item.get("word") or "").strip().lower()
                        if base and base != word:
                            forms.append((word, base))
                    continue
                for g in s.get("glosses", []) or []:
                    target = link_target(g)
                    if target and target != word:
                        is_form = True
                        forms.append((word, target))
                        continue
                    c = clean(g)
                    if c and c not in got:
                        got.append(c)
            if got:
                # Слово может быть и леммой, и формой другого слова
                # («solo» — «alone» и форма «solare»). Свои значения при этом
                # терять нельзя: наследование от леммы только добавляет.
                cur = lemmas.setdefault(word, [])
                for g in got:
                    if g not in cur:
                        cur.append(g)

    out = dict((w, list(t)) for w, t in lemmas.items())
    inherited = 0
    for form, base in forms:
        t = lemmas.get(base)
        if not t:
            continue
        cur = out.setdefault(form, [])
        for g in t:
            if g not in cur:
                cur.append(g)
        inherited += 1

    with open(dst, "w", encoding="utf-8") as f:
        for w in sorted(out):
            t = out[w][:MAX_TRANSLATIONS]
            if t:
                f.write(w + "\t" + ", ".join(t) + "\n")
    print("лемм %d, словоформ с переводом %d, всего строк %d"
          % (len(lemmas), inherited, len(out)), file=sys.stderr)


if len(sys.argv) != 3:
    raise SystemExit(__doc__)
main(sys.argv[1], sys.argv[2])
