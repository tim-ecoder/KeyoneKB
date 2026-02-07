#!/usr/bin/env python3
"""
Generate bigram dictionary files for KeyoneKB prediction engine.

Usage:
    python generate_bigrams.py <corpus_file> <output_file> [--locale en] [--max-keys 5000] [--max-next 20]

Input corpus: plain text file, one sentence per line (or paragraph).
Output: JSON file in format {"word1": [["word2", freq], ...], ...}

Example:
    python generate_bigrams.py english_corpus.txt ../app/src/main/assets/dictionaries/en_bigrams.json --locale en
    python generate_bigrams.py russian_corpus.txt ../app/src/main/assets/dictionaries/ru_bigrams.json --locale ru
"""

import argparse
import json
import re
import sys
from collections import Counter, defaultdict


def normalize_word(word, locale="en"):
    """Normalize a word: lowercase, strip punctuation."""
    word = word.lower().strip()
    # Keep apostrophes for English contractions
    if locale == "en":
        word = re.sub(r"[^a-z']", "", word)
        word = word.strip("'")
    elif locale == "ru":
        word = re.sub(r"[^а-яёА-ЯЁ]", "", word.lower())
    else:
        word = re.sub(r"[^a-zа-яёА-ЯЁ']", "", word.lower())
    return word


def extract_bigrams(text, locale="en"):
    """Extract word bigrams from text."""
    bigrams = defaultdict(Counter)
    word_freq = Counter()

    for line in text.split("\n"):
        line = line.strip()
        if not line:
            continue

        words = line.split()
        normalized = []
        for w in words:
            nw = normalize_word(w, locale)
            if nw and len(nw) >= 1:
                normalized.append(nw)

        for w in normalized:
            word_freq[w] += 1

        for i in range(len(normalized) - 1):
            w1 = normalized[i]
            w2 = normalized[i + 1]
            if w1 and w2:
                bigrams[w1][w2] += 1

    return bigrams, word_freq


def scale_frequencies(counter, max_val=255):
    """Scale raw counts to 0-255 range."""
    if not counter:
        return {}
    max_count = max(counter.values())
    if max_count == 0:
        return {}
    scaled = {}
    for word, count in counter.items():
        scaled[word] = max(1, int(count / max_count * max_val))
    return scaled


def main():
    parser = argparse.ArgumentParser(description="Generate bigram dictionaries")
    parser.add_argument("corpus", help="Input corpus file (plain text)")
    parser.add_argument("output", help="Output JSON file")
    parser.add_argument("--locale", default="en", help="Locale (en, ru)")
    parser.add_argument("--max-keys", type=int, default=5000, help="Max number of word1 keys")
    parser.add_argument("--max-next", type=int, default=20, help="Max next words per key")
    parser.add_argument("--min-bigram-count", type=int, default=2, help="Min bigram occurrence count")
    parser.add_argument("--min-word-freq", type=int, default=5, help="Min word frequency to be a key")
    args = parser.parse_args()

    print(f"Reading corpus from {args.corpus}...")
    with open(args.corpus, "r", encoding="utf-8") as f:
        text = f.read()

    print(f"Extracting bigrams (locale={args.locale})...")
    bigrams, word_freq = extract_bigrams(text, args.locale)

    # Filter to top N most frequent words as keys
    top_words = [w for w, c in word_freq.most_common(args.max_keys) if c >= args.min_word_freq]
    print(f"Top words: {len(top_words)}")

    result = {}
    for w1 in top_words:
        if w1 not in bigrams:
            continue
        # Filter low-count bigrams
        filtered = {w2: c for w2, c in bigrams[w1].items() if c >= args.min_bigram_count}
        if not filtered:
            continue
        # Scale and sort
        scaled = scale_frequencies(filtered)
        sorted_pairs = sorted(scaled.items(), key=lambda x: -x[1])[:args.max_next]
        result[w1] = [[w, f] for w, f in sorted_pairs]

    print(f"Writing {len(result)} keys to {args.output}...")
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, separators=(",", ":"))

    # File size
    import os
    size = os.path.getsize(args.output)
    print(f"Done. Output size: {size:,} bytes ({size/1024:.1f} KB)")


if __name__ == "__main__":
    main()
