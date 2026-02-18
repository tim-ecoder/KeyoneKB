#!/usr/bin/env python3
"""Convert translation TSV files to CDB (Constant DataBase) format.

Input format:  key<TAB>translation1,translation2,...
Output format: CDB binary (D.J. Bernstein's constant database)

Both single-word and phrase keys are stored in the same CDB file.
Keys are lowercased. Values are comma-separated translations (UTF-8).

Usage:
    python3 tsv_to_cdb.py input.tsv output.cdb
    python3 tsv_to_cdb.py  # converts all dict/*.tsv in project
"""

import struct
import os
import sys


def cdb_hash(key: bytes) -> int:
    """DJB hash — must match the C cdb_hash() function."""
    h = 5381
    for b in key:
        h = ((h << 5) + h) ^ b
        h &= 0xFFFFFFFF
    return h


def write_cdb(records: list, output_path: str):
    """Write a CDB file from a list of (key_bytes, value_bytes) tuples."""
    # Phase 1: write records after 2048-byte header placeholder
    header_size = 256 * 8  # 256 hash table pointers, 8 bytes each
    rec_data = bytearray()
    # Collect hash table entries: buckets[slot] = [(hash, record_pos), ...]
    buckets = [[] for _ in range(256)]

    for key, val in records:
        h = cdb_hash(key)
        pos = header_size + len(rec_data)
        rec_data += struct.pack('<II', len(key), len(val))
        rec_data += key
        rec_data += val
        buckets[h & 0xFF].append((h, pos))

    # Phase 2: build hash tables
    htab_offset = header_size + len(rec_data)
    header = bytearray()
    htab_data = bytearray()

    for i in range(256):
        entries = buckets[i]
        nslots = len(entries) * 2  # load factor 0.5
        if nslots == 0:
            header += struct.pack('<II', htab_offset + len(htab_data), 0)
            continue

        table = [(0, 0)] * nslots
        for h, pos in entries:
            slot = (h >> 8) % nslots
            while table[slot][1] != 0:
                slot = (slot + 1) % nslots
            table[slot] = (h, pos)

        tab_pos = htab_offset + len(htab_data)
        header += struct.pack('<II', tab_pos, nslots)
        for h, pos in table:
            htab_data += struct.pack('<II', h, pos)

    # Write file
    with open(output_path, 'wb') as f:
        f.write(header)
        f.write(rec_data)
        f.write(htab_data)


def convert_tsv_to_cdb(tsv_path: str, cdb_path: str):
    """Read TSV file and produce CDB file."""
    records = []
    with open(tsv_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            tab = line.find('\t')
            if tab <= 0 or tab >= len(line) - 1:
                continue
            key = line[:tab].strip().lower()
            val = line[tab + 1:].strip()
            if key and val:
                records.append((key.encode('utf-8'), val.encode('utf-8')))

    write_cdb(records, cdb_path)
    print(f"  {tsv_path} -> {cdb_path}: {len(records)} entries, "
          f"{os.path.getsize(cdb_path)} bytes")


if __name__ == '__main__':
    if len(sys.argv) == 3:
        convert_tsv_to_cdb(sys.argv[1], sys.argv[2])
    else:
        # Convert all dicts in project
        base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        dict_dir = os.path.join(base, 'app', 'src', 'main', 'assets', 'dict')
        print("Converting TSV dictionaries to CDB...")
        for fname in sorted(os.listdir(dict_dir)):
            if fname.endswith('.tsv'):
                tsv = os.path.join(dict_dir, fname)
                cdb = os.path.join(dict_dir, fname.replace('.tsv', '.cdb'))
                convert_tsv_to_cdb(tsv, cdb)
        print("Done!")
