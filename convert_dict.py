#!/usr/bin/env python3
"""Convert hermitdave/FrequencyWords full lists to KeyoneKB format.

Input format:  word space rawcount  (sorted by frequency desc)
Output format: word tab scaledfreq  (scaled 45-255, sorted desc)

Filters out junk: non-alpha, too short/long, contractions, numbers, etc.
"""

import sys
import math
import re
import os

MIN_FREQ = 45
MAX_FREQ = 255
MAX_WORDS = 750000


def is_valid_english(word):
    if len(word) < 1 or len(word) > 30:
        return False
    if not re.match(r"^[a-z]+(?:['-][a-z]+)*$", word):
        return False
    if word.startswith("'") or word.startswith("-"):
        return False
    return True


def is_valid_russian(word):
    if len(word) < 1 or len(word) > 40:
        return False
    if not re.match(r"^[а-яё]+(?:-[а-яё]+)*$", word):
        return False
    return True


def convert(input_path, output_path, validator, max_words=MAX_WORDS):
    entries = []
    with open(input_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.rsplit(" ", 1)
            if len(parts) != 2:
                continue
            word, freq_str = parts
            word = word.lower().strip()
            try:
                freq = int(freq_str)
            except ValueError:
                continue
            if freq < 2:
                continue
            if not validator(word):
                continue
            entries.append((word, freq))

    seen = {}
    for word, freq in entries:
        if word not in seen or freq > seen[word]:
            seen[word] = freq
    entries = sorted(seen.items(), key=lambda x: -x[1])

    if len(entries) > max_words:
        entries = entries[:max_words]

    print(f"  Filtered: {len(entries)} words (from {len(seen)} unique valid)")

    max_raw = entries[0][1]
    min_raw = entries[-1][1]
    log_max = math.log(max_raw)
    log_min = math.log(min_raw)
    log_range = log_max - log_min if log_max != log_min else 1.0

    with open(output_path, "w", encoding="utf-8", newline="\n") as f:
        for word, raw_freq in entries:
            t = (math.log(raw_freq) - log_min) / log_range
            scaled = int(round(MIN_FREQ + t * (MAX_FREQ - MIN_FREQ)))
            scaled = max(MIN_FREQ, min(MAX_FREQ, scaled))
            f.write(f"{word}\t{scaled}\n")

    print(f"  Written to: {output_path}")
    print(f"  Freq range: {entries[0][1]} -> {entries[-1][1]} (raw)")
    print(f"  Top 5: {entries[:5]}")


if __name__ == "__main__":
    base = os.path.dirname(os.path.abspath(__file__))
    out_dir = os.path.join(base, "app", "src", "main", "assets", "dictionaries")

    print("Converting English...")
    convert(
        os.path.join(base, "en_full_raw.txt"),
        os.path.join(out_dir, "en_base.txt"),
        is_valid_english,
    )

    print("\nConverting Russian...")
    convert(
        os.path.join(base, "ru_full_raw.txt"),
        os.path.join(out_dir, "ru_base.txt"),
        is_valid_russian,
    )

    print("\nDone!")
