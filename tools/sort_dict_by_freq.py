#!/usr/bin/env python3
"""
Sort translation dictionary entries by target language word frequency.
Uses FrequencyWords data (word\tfreq 0-255) to rank translations.

For each dictionary entry with multiple translations:
- Looks up each translation's frequency in the target language
- Sorts translations: highest frequency first
- Multi-word translations use the average frequency of component words

Usage:
  python3 sort_dict_by_freq.py <dict.tsv> <freq_file> <output.tsv>

Examples:
  python3 sort_dict_by_freq.py en_ru.tsv ru_base.txt en_ru_sorted.tsv
  python3 sort_dict_by_freq.py ru_en.tsv en_base.txt ru_en_sorted.tsv
"""

import sys
import re

def load_freq(freq_file):
    """Load frequency data: word -> freq (0-255)"""
    freq = {}
    with open(freq_file, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split('\t')
            if len(parts) >= 2:
                word = parts[0].lower()
                try:
                    score = int(parts[1])
                    freq[word] = score
                except ValueError:
                    pass
    return freq

def word_freq(word, freq_data):
    """Get frequency score for a word/phrase.
    For multi-word phrases, use the minimum component frequency.
    Unknown words get -1 (sorted last)."""
    word_lower = word.lower().strip()

    # Direct lookup
    if word_lower in freq_data:
        return freq_data[word_lower]

    # For multi-word translations, use min frequency of components
    # (the rarest word determines phrase rarity)
    words = re.split(r'[\s\-]+', word_lower)
    if len(words) > 1:
        scores = []
        for w in words:
            w = w.strip()
            if not w:
                continue
            if w in freq_data:
                scores.append(freq_data[w])
        if scores:
            return min(scores)

    return -1  # Unknown word

def sort_dict(dict_file, freq_file, output_file):
    freq_data = load_freq(freq_file)
    print(f"Loaded {len(freq_data)} frequency entries from {freq_file}")

    sorted_count = 0
    total_entries = 0
    multi_trans = 0

    with open(dict_file, 'r', encoding='utf-8') as fin, \
         open(output_file, 'w', encoding='utf-8') as fout:
        for line in fin:
            line = line.rstrip('\n')
            if not line:
                continue
            total_entries += 1

            tab = line.find('\t')
            if tab <= 0:
                fout.write(line + '\n')
                continue

            key = line[:tab]
            translations_str = line[tab+1:]

            # Split translations by comma
            translations = [t.strip() for t in translations_str.split(',') if t.strip()]

            if len(translations) <= 1:
                fout.write(line + '\n')
                continue

            multi_trans += 1

            # Score each translation
            scored = [(t, word_freq(t, freq_data)) for t in translations]

            # Remove exact duplicates (case-insensitive) keeping highest-freq version
            seen = {}
            unique = []
            for t, score in scored:
                lower = t.lower()
                if lower not in seen or score > seen[lower][1]:
                    seen[lower] = (t, score)
            for t, score in scored:
                lower = t.lower()
                if lower in seen and seen[lower][0] == t:
                    unique.append((t, score))
                    del seen[lower]
            scored = unique

            # Sort by frequency (highest first), then alphabetically for ties
            scored.sort(key=lambda x: (-x[1], x[0].lower()))

            sorted_translations = [t for t, _ in scored]

            # Check if order changed
            if sorted_translations != translations[:len(sorted_translations)]:
                sorted_count += 1

            fout.write(key + '\t' + ','.join(sorted_translations) + '\n')

    print(f"Total entries: {total_entries}")
    print(f"Multi-translation entries: {multi_trans}")
    print(f"Entries with changed order: {sorted_count}")

if __name__ == '__main__':
    if len(sys.argv) != 4:
        print(f"Usage: {sys.argv[0]} <dict.tsv> <freq_file> <output.tsv>")
        sys.exit(1)
    sort_dict(sys.argv[1], sys.argv[2], sys.argv[3])
