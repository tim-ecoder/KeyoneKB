/*
 * Сборка .cdb из TSV на хосте — тем же кодом cdb.c, что и на устройстве.
 * Повторяет шаги nativeBuildCdbFromTsv: сортировка по частоте исходного
 * языка и запись всех записей (лимит 0 = без обрезки).
 */
#include "cdb.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct { char *key; size_t klen; char *val; size_t vlen; int freq; } entry_t;

static char *load_file(const char *path, long *out_size) {
    FILE *f = fopen(path, "rb");
    if (!f) return NULL;
    fseek(f, 0, SEEK_END); long sz = ftell(f); fseek(f, 0, SEEK_SET);
    char *buf = malloc(sz + 1);
    if (!buf) { fclose(f); return NULL; }
    long rd = fread(buf, 1, sz, f);
    buf[rd] = '\0'; fclose(f); *out_size = rd; return buf;
}

/* Частоты держим в простом отсортированном массиве с бинарным поиском. */
typedef struct { char *w; size_t wlen; int freq; } fw_t;
static int fw_cmp(const void *a, const void *b) {
    const fw_t *x = a, *y = b;
    size_t n = x->wlen < y->wlen ? x->wlen : y->wlen;
    int c = memcmp(x->w, y->w, n);
    if (c) return c;
    return (x->wlen > y->wlen) - (x->wlen < y->wlen);
}
static int entry_cmp(const void *a, const void *b) {
    const entry_t *x = a, *y = b;
    if (x->freq != y->freq) return y->freq - x->freq;
    return 0;
}

int main(int argc, char **argv) {
    if (argc < 4) { fprintf(stderr, "usage: %s tsv freq cdb\n", argv[0]); return 2; }
    long fsz = 0, tsz = 0;
    char *fbuf = load_file(argv[2], &fsz);
    fw_t *fw = NULL; size_t fw_n = 0;
    if (fbuf) {
        size_t cap = 1024; fw = malloc(cap * sizeof(fw_t));
        char *p = fbuf, *end = fbuf + fsz;
        while (p < end) {
            char *ls = p;
            while (p < end && *p != '\n' && *p != '\r') p++;
            char *le = p;
            while (p < end && (*p == '\n' || *p == '\r')) p++;
            char *tab = memchr(ls, '\t', le - ls);
            if (!tab || tab == ls) continue;
            if (fw_n == cap) { cap *= 2; fw = realloc(fw, cap * sizeof(fw_t)); }
            fw[fw_n].w = ls; fw[fw_n].wlen = tab - ls; fw[fw_n].freq = atoi(tab + 1);
            if (fw[fw_n].freq > 0) fw_n++;
        }
        qsort(fw, fw_n, sizeof(fw_t), fw_cmp);
    }

    char *tbuf = load_file(argv[1], &tsz);
    if (!tbuf) { fprintf(stderr, "no tsv %s\n", argv[1]); return 1; }
    size_t lines = 1;
    for (long i = 0; i < tsz; i++) if (tbuf[i] == '\n') lines++;
    entry_t *e = malloc(lines * sizeof(entry_t));
    size_t n = 0;
    char *p = tbuf, *end = tbuf + tsz;
    while (p < end) {
        char *ls = p;
        while (p < end && *p != '\n' && *p != '\r') p++;
        size_t llen = p - ls;
        while (p < end && (*p == '\n' || *p == '\r')) p++;
        char *tab = memchr(ls, '\t', llen);
        if (!tab || tab == ls) continue;
        e[n].key = ls; e[n].klen = tab - ls;
        e[n].val = tab + 1; e[n].vlen = llen - e[n].klen - 1;
        if (e[n].vlen == 0) continue;
        e[n].freq = 0;
        if (fw) {
            fw_t probe = { e[n].key, e[n].klen, 0 };
            fw_t *hit = bsearch(&probe, fw, fw_n, sizeof(fw_t), fw_cmp);
            if (hit) e[n].freq = hit->freq;
        }
        n++;
    }
    if (fw) qsort(e, n, sizeof(entry_t), entry_cmp);

    cdb_make_t cm;
    if (cdb_make_start(&cm, argv[3]) != 0) { fprintf(stderr, "cannot create %s\n", argv[3]); return 1; }
    for (size_t i = 0; i < n; i++)
        cdb_make_add(&cm, e[i].key, e[i].klen, e[i].val, e[i].vlen);
    if (cdb_make_finish(&cm) != 0) { fprintf(stderr, "finish failed\n"); return 1; }
    printf("%s: %zu записей\n", argv[3], n);
    return 0;
}
