/*
 * SymSpell — Symmetric Delete spelling correction algorithm (C99).
 * Based on the original algorithm by Wolf Garbe.
 *
 * Uses open-addressing hash tables and arena allocation for
 * cache-friendly, allocation-free lookups on Android.
 */

#include "symspell.h"
#include "keyboard_distance.h"

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>

/* ---- Arena allocator --------------------------------------------------- */

#define ARENA_BLOCK_SIZE (1 << 20)  /* 1 MB blocks */

typedef struct arena_block {
    struct arena_block *next;
    size_t used;
    size_t capacity;
    char   data[];
} arena_block_t;

typedef struct {
    arena_block_t *head;
} arena_t;

static void arena_init(arena_t *a) {
    a->head = NULL;
}

static void arena_free(arena_t *a) {
    arena_block_t *b = a->head;
    while (b) {
        arena_block_t *next = b->next;
        free(b);
        b = next;
    }
    a->head = NULL;
}

static char *arena_alloc(arena_t *a, size_t size) {
    /* Align to 8 bytes */
    size = (size + 7) & ~(size_t)7;
    arena_block_t *b = a->head;
    if (!b || b->used + size > b->capacity) {
        size_t cap = ARENA_BLOCK_SIZE;
        if (size > cap) cap = size + 64;
        b = (arena_block_t *)malloc(sizeof(arena_block_t) + cap);
        if (!b) return NULL;
        b->capacity = cap;
        b->used = 0;
        b->next = a->head;
        a->head = b;
    }
    char *ptr = b->data + b->used;
    b->used += size;
    return ptr;
}

static char *arena_strdup(arena_t *a, const char *s) {
    size_t len = strlen(s) + 1;
    char *dst = arena_alloc(a, len);
    if (dst) memcpy(dst, s, len);
    return dst;
}

/* ---- Hash table (open addressing, linear probing) ---------------------- */

static uint32_t fnv1a(const char *s) {
    uint32_t h = 2166136261u;
    for (; *s; s++) {
        h ^= (uint8_t)*s;
        h *= 16777619u;
    }
    return h;
}

/* Dictionary entry: word -> frequency */
typedef struct {
    const char *key;   /* NULL = empty slot */
    int         freq;
} dict_entry_t;

/* Deletes entry: delete_pattern -> bucket of word indices */
typedef struct {
    const char *key;        /* NULL = empty slot */
    uint32_t   *word_ids;   /* array of indices into dict_words[] */
    uint32_t    count;
    uint32_t    capacity;
} delete_entry_t;

/* ---- SymSpell structure ------------------------------------------------ */

struct symspell {
    int max_edit_distance;
    int prefix_length;

    /* Dictionary (word -> freq), open-addressing hash */
    dict_entry_t *dict;
    uint32_t      dict_cap;
    uint32_t      dict_count;

    /* Ordered word list (for index-based references in deletes) */
    const char  **dict_words;
    uint32_t      dict_words_count;
    uint32_t      dict_words_cap;

    /* Deletes table (delete_pattern -> [word_index, ...]) */
    delete_entry_t *deletes;
    uint32_t        deletes_cap;
    uint32_t        deletes_count;

    /* String storage */
    arena_t arena;

    /* mmap support */
    void  *mmap_base;
    size_t mmap_size;
    int    mmap_fd;
};

/* ---- Dict helpers ------------------------------------------------------ */

static void dict_ensure_cap(symspell_t *ss) {
    if (ss->dict_count * 4 < ss->dict_cap * 3) return; /* < 75% load */
    uint32_t new_cap = ss->dict_cap ? ss->dict_cap * 2 : 1024;
    dict_entry_t *new_dict = (dict_entry_t *)calloc(new_cap, sizeof(dict_entry_t));
    if (!new_dict) return;
    for (uint32_t i = 0; i < ss->dict_cap; i++) {
        if (!ss->dict[i].key) continue;
        uint32_t h = fnv1a(ss->dict[i].key) & (new_cap - 1);
        while (new_dict[h].key) h = (h + 1) & (new_cap - 1);
        new_dict[h] = ss->dict[i];
    }
    free(ss->dict);
    ss->dict = new_dict;
    ss->dict_cap = new_cap;
}

static dict_entry_t *dict_find(const symspell_t *ss, const char *key) {
    if (!ss->dict_cap) return NULL;
    uint32_t h = fnv1a(key) & (ss->dict_cap - 1);
    while (ss->dict[h].key) {
        if (strcmp(ss->dict[h].key, key) == 0) return &ss->dict[h];
        h = (h + 1) & (ss->dict_cap - 1);
    }
    return NULL;
}

static void dict_words_push(symspell_t *ss, const char *word) {
    if (ss->dict_words_count >= ss->dict_words_cap) {
        uint32_t new_cap = ss->dict_words_cap ? ss->dict_words_cap * 2 : 1024;
        const char **nw = (const char **)realloc(ss->dict_words, new_cap * sizeof(char *));
        if (!nw) return;
        ss->dict_words = nw;
        ss->dict_words_cap = new_cap;
    }
    ss->dict_words[ss->dict_words_count++] = word;
}

/* ---- Deletes helpers --------------------------------------------------- */

static void deletes_ensure_cap(symspell_t *ss) {
    if (ss->deletes_count * 4 < ss->deletes_cap * 3) return;
    uint32_t new_cap = ss->deletes_cap ? ss->deletes_cap * 2 : 4096;
    delete_entry_t *ndt = (delete_entry_t *)calloc(new_cap, sizeof(delete_entry_t));
    if (!ndt) return;
    for (uint32_t i = 0; i < ss->deletes_cap; i++) {
        if (!ss->deletes[i].key) continue;
        uint32_t h = fnv1a(ss->deletes[i].key) & (new_cap - 1);
        while (ndt[h].key) h = (h + 1) & (new_cap - 1);
        ndt[h] = ss->deletes[i];
    }
    free(ss->deletes);
    ss->deletes = ndt;
    ss->deletes_cap = new_cap;
}

static delete_entry_t *deletes_find(const symspell_t *ss, const char *key) {
    if (!ss->deletes_cap) return NULL;
    uint32_t h = fnv1a(key) & (ss->deletes_cap - 1);
    while (ss->deletes[h].key) {
        if (strcmp(ss->deletes[h].key, key) == 0) return &ss->deletes[h];
        h = (h + 1) & (ss->deletes_cap - 1);
    }
    return NULL;
}

static void deletes_add(symspell_t *ss, const char *pattern, uint32_t word_id) {
    deletes_ensure_cap(ss);
    uint32_t h = fnv1a(pattern) & (ss->deletes_cap - 1);
    while (ss->deletes[h].key) {
        if (strcmp(ss->deletes[h].key, pattern) == 0) {
            /* Existing bucket — add word_id if not duplicate */
            delete_entry_t *e = &ss->deletes[h];
            for (uint32_t i = 0; i < e->count; i++) {
                if (e->word_ids[i] == word_id) return;
            }
            if (e->count >= e->capacity) {
                uint32_t nc = e->capacity ? e->capacity * 2 : 4;
                uint32_t *nw = (uint32_t *)realloc(e->word_ids, nc * sizeof(uint32_t));
                if (!nw) return;
                e->word_ids = nw;
                e->capacity = nc;
            }
            e->word_ids[e->count++] = word_id;
            return;
        }
        h = (h + 1) & (ss->deletes_cap - 1);
    }
    /* New entry */
    ss->deletes[h].key = arena_strdup(&ss->arena, pattern);
    ss->deletes[h].word_ids = (uint32_t *)malloc(4 * sizeof(uint32_t));
    ss->deletes[h].capacity = 4;
    ss->deletes[h].word_ids[0] = word_id;
    ss->deletes[h].count = 1;
    ss->deletes_count++;
}

/* ---- Damerau-Levenshtein with early termination ------------------------ */

int ss_damerau_distance(const char *a, int alen, const char *b, int blen, int max_dist) {
    if (abs(alen - blen) > max_dist) return -1;
    if (alen == 0) return blen <= max_dist ? blen : -1;
    if (blen == 0) return alen <= max_dist ? alen : -1;

    /* Use stack buffer for small strings, heap for large */
    int stack_buf[3 * 128];
    int *heap_buf = NULL;
    int *buf;
    int row_size = blen + 1;

    if (row_size * 3 <= (int)(sizeof(stack_buf) / sizeof(int))) {
        buf = stack_buf;
    } else {
        heap_buf = (int *)malloc(3 * row_size * sizeof(int));
        if (!heap_buf) return -1;
        buf = heap_buf;
    }

    int *prev_prev = buf;
    int *prev = buf + row_size;
    int *curr = buf + 2 * row_size;

    for (int j = 0; j <= blen; j++) prev[j] = j;

    for (int i = 1; i <= alen; i++) {
        curr[0] = i;
        int min_row = curr[0];
        for (int j = 1; j <= blen; j++) {
            int cost = (a[i - 1] == b[j - 1]) ? 0 : 1;
            int val = prev[j] + 1;           /* delete */
            int ins = curr[j - 1] + 1;       /* insert */
            int rep = prev[j - 1] + cost;    /* replace */
            if (ins < val) val = ins;
            if (rep < val) val = rep;
            /* transposition */
            if (i > 1 && j > 1 &&
                a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                int trans = prev_prev[j - 2] + 1;
                if (trans < val) val = trans;
            }
            curr[j] = val;
            if (val < min_row) min_row = val;
        }
        if (min_row > max_dist) {
            if (heap_buf) free(heap_buf);
            return -1;
        }
        int *tmp = prev_prev;
        prev_prev = prev;
        prev = curr;
        curr = tmp;
    }

    int result = prev[blen] <= max_dist ? prev[blen] : -1;
    if (heap_buf) free(heap_buf);
    return result;
}

/* ---- Generate deletes for a word --------------------------------------- */

static void generate_deletes(symspell_t *ss, const char *word, int word_len,
                              int distance, uint32_t word_id, char *buf) {
    if (distance == 0 || word_len == 0) return;
    for (int i = 0; i < word_len; i++) {
        /* Build delete: skip character at position i */
        int pos = 0;
        for (int j = 0; j < word_len; j++) {
            if (j != i) buf[pos++] = word[j];
        }
        buf[pos] = '\0';
        deletes_add(ss, buf, word_id);
        if (distance > 1) {
            generate_deletes(ss, buf, pos, distance - 1, word_id, buf + pos + 1);
        }
    }
}

/* ---- Public API -------------------------------------------------------- */

symspell_t *ss_create(int max_edit_distance, int prefix_length) {
    if (max_edit_distance < 1) max_edit_distance = 2;
    if (max_edit_distance > SYMSPELL_MAX_EDIT_DIST) max_edit_distance = SYMSPELL_MAX_EDIT_DIST;
    if (prefix_length < 1) prefix_length = SYMSPELL_DEFAULT_PREFIX_LEN;

    symspell_t *ss = (symspell_t *)calloc(1, sizeof(symspell_t));
    if (!ss) return NULL;
    ss->max_edit_distance = max_edit_distance;
    ss->prefix_length = prefix_length;
    ss->mmap_fd = -1;
    arena_init(&ss->arena);
    return ss;
}

void ss_destroy(symspell_t *ss) {
    if (!ss) return;
    if (ss->mmap_base) {
        munmap(ss->mmap_base, ss->mmap_size);
        if (ss->mmap_fd >= 0) close(ss->mmap_fd);
        /* mmap mode: deletes buckets were allocated separately */
    }
    /* Free deletes buckets */
    if (ss->deletes) {
        for (uint32_t i = 0; i < ss->deletes_cap; i++) {
            if (ss->deletes[i].word_ids)
                free(ss->deletes[i].word_ids);
        }
        free(ss->deletes);
    }
    free(ss->dict);
    free(ss->dict_words);
    arena_free(&ss->arena);
    free(ss);
}

void ss_add_word(symspell_t *ss, const char *word, int frequency) {
    if (!ss || !word || !*word) return;
    dict_ensure_cap(ss);

    dict_entry_t *existing = dict_find(ss, word);
    if (existing) {
        if (frequency > existing->freq) existing->freq = frequency;
        return;
    }

    const char *stored = arena_strdup(&ss->arena, word);
    if (!stored) return;

    uint32_t h = fnv1a(stored) & (ss->dict_cap - 1);
    while (ss->dict[h].key) h = (h + 1) & (ss->dict_cap - 1);
    ss->dict[h].key = stored;
    ss->dict[h].freq = frequency;
    ss->dict_count++;

    dict_words_push(ss, stored);
}

void ss_build_index(symspell_t *ss) {
    if (!ss) return;
    /* Clear existing deletes */
    if (ss->deletes) {
        for (uint32_t i = 0; i < ss->deletes_cap; i++) {
            free(ss->deletes[i].word_ids);
        }
        free(ss->deletes);
    }
    /* Pre-allocate deletes table: ~6x word count is typical */
    ss->deletes_cap = 1;
    while (ss->deletes_cap < ss->dict_count * 8) ss->deletes_cap <<= 1;
    ss->deletes = (delete_entry_t *)calloc(ss->deletes_cap, sizeof(delete_entry_t));
    ss->deletes_count = 0;
    if (!ss->deletes) return;

    /* Temp buffer for generating delete strings */
    char buf[512];

    for (uint32_t idx = 0; idx < ss->dict_words_count; idx++) {
        const char *word = ss->dict_words[idx];
        int wlen = (int)strlen(word);
        const char *key = word;
        int klen = wlen;
        if (klen > ss->prefix_length) klen = ss->prefix_length;

        /* Use prefix for delete generation */
        char prefix[128];
        if (klen < (int)sizeof(prefix)) {
            memcpy(prefix, key, klen);
            prefix[klen] = '\0';
            generate_deletes(ss, prefix, klen, ss->max_edit_distance, idx, buf);
        }
    }
}

int ss_size(const symspell_t *ss) {
    return ss ? (int)ss->dict_count : 0;
}

int ss_contains(const symspell_t *ss, const char *word) {
    return dict_find(ss, word) != NULL;
}

int ss_get_frequency(const symspell_t *ss, const char *word) {
    dict_entry_t *e = dict_find(ss, word);
    return e ? e->freq : 0;
}

/* ---- Lookup ------------------------------------------------------------ */

/* Simple set for tracking seen strings during lookup */
#define SEEN_CAP 512
typedef struct {
    uint32_t hashes[SEEN_CAP];
    int      count;
} seen_set_t;

static void seen_init(seen_set_t *s) { s->count = 0; }

static int seen_add(seen_set_t *s, const char *str) {
    uint32_t h = fnv1a(str);
    for (int i = 0; i < s->count; i++) {
        if (s->hashes[i] == h) return 0; /* already seen */
    }
    if (s->count < SEEN_CAP) {
        s->hashes[s->count++] = h;
    }
    return 1;
}

/* Queue for BFS of delete candidates */
#define QUEUE_CAP 4096
typedef struct {
    char  items[QUEUE_CAP][128];
    int   head, tail, count;
} queue_t;

static void queue_init(queue_t *q) { q->head = q->tail = q->count = 0; }

static int queue_push(queue_t *q, const char *s) {
    if (q->count >= QUEUE_CAP) return 0;
    size_t len = strlen(s);
    if (len >= 128) return 0;
    memcpy(q->items[q->tail], s, len + 1);
    q->tail = (q->tail + 1) % QUEUE_CAP;
    q->count++;
    return 1;
}

static const char *queue_pop(queue_t *q) {
    if (q->count <= 0) return NULL;
    const char *s = q->items[q->head];
    q->head = (q->head + 1) % QUEUE_CAP;
    q->count--;
    return s;
}

/* Sort suggestions: by distance ASC, then frequency DESC, then length ASC */
static int suggest_cmp(const void *a, const void *b) {
    const ss_suggest_item_t *sa = (const ss_suggest_item_t *)a;
    const ss_suggest_item_t *sb = (const ss_suggest_item_t *)b;
    if (sa->distance != sb->distance) return sa->distance - sb->distance;
    if (sb->frequency != sa->frequency) return sb->frequency - sa->frequency;
    return (int)strlen(sa->term) - (int)strlen(sb->term);
}

int ss_lookup(symspell_t *ss, const char *input, int max_suggestions,
              ss_suggest_item_t *out, int out_capacity) {
    if (!ss || !input || !*input || !out || out_capacity <= 0) return 0;

    int input_len = (int)strlen(input);
    int result_count = 0;

    /* Allocate queue and seen set on heap to avoid large stack frames */
    queue_t *queue = (queue_t *)malloc(sizeof(queue_t));
    seen_set_t *seen_candidates = (seen_set_t *)malloc(sizeof(seen_set_t));
    seen_set_t *seen_suggestions = (seen_set_t *)malloc(sizeof(seen_set_t));
    if (!queue || !seen_candidates || !seen_suggestions) {
        free(queue); free(seen_candidates); free(seen_suggestions);
        return 0;
    }

    queue_init(queue);
    seen_init(seen_candidates);
    seen_init(seen_suggestions);

    /* Truncate input to prefix length */
    char input_prefix[128];
    int prefix_len = input_len;
    if (prefix_len > ss->prefix_length) prefix_len = ss->prefix_length;
    memcpy(input_prefix, input, prefix_len);
    input_prefix[prefix_len] = '\0';

    queue_push(queue, input_prefix);
    seen_add(seen_candidates, input_prefix);

    /* Direct dictionary hit */
    dict_entry_t *direct = dict_find(ss, input);
    if (direct && result_count < out_capacity) {
        if (seen_add(seen_suggestions, input)) {
            out[result_count].term = direct->key;
            out[result_count].distance = 0;
            out[result_count].frequency = direct->freq;
            out[result_count].weighted_distance = -1;
            result_count++;
        }
    }

    const char *candidate;
    while ((candidate = queue_pop(queue)) != NULL) {
        int cand_len = (int)strlen(candidate);
        int distance = prefix_len - cand_len;
        if (distance > ss->max_edit_distance) continue;

        /* Check deletes table */
        delete_entry_t *bucket = deletes_find(ss, candidate);
        if (bucket) {
            for (uint32_t i = 0; i < bucket->count; i++) {
                uint32_t wid = bucket->word_ids[i];
                if (wid >= ss->dict_words_count) continue;
                const char *suggestion = ss->dict_words[wid];
                int sug_len = (int)strlen(suggestion);

                int ed = ss_damerau_distance(input, input_len, suggestion, sug_len,
                                              ss->max_edit_distance);
                if (ed >= 0 && ed <= ss->max_edit_distance) {
                    if (seen_add(seen_suggestions, suggestion)) {
                        dict_entry_t *de = dict_find(ss, suggestion);
                        int freq = de ? de->freq : 0;
                        if (result_count < out_capacity) {
                            out[result_count].term = suggestion;
                            out[result_count].distance = ed;
                            out[result_count].frequency = freq;
                            out[result_count].weighted_distance = -1;
                            result_count++;
                        }
                    }
                }
            }
        }

        /* Generate further deletes of the candidate */
        if (distance < ss->max_edit_distance) {
            for (int i = 0; i < cand_len; i++) {
                char del[128];
                int pos = 0;
                for (int j = 0; j < cand_len; j++) {
                    if (j != i) del[pos++] = candidate[j];
                }
                del[pos] = '\0';
                if (seen_add(seen_candidates, del)) {
                    queue_push(queue, del);
                }
            }
        }
    }

    free(queue);
    free(seen_candidates);
    free(seen_suggestions);

    /* Sort results */
    if (result_count > 1) {
        qsort(out, result_count, sizeof(ss_suggest_item_t), suggest_cmp);
    }

    return result_count < max_suggestions ? result_count : max_suggestions;
}

/* Lookup with keyboard-weighted distance */
int ss_lookup_weighted(symspell_t *ss, const char *input, int max_suggestions,
                       ss_suggest_item_t *out, int out_capacity,
                       const char *layout) {
    int count = ss_lookup(ss, input, out_capacity, out, out_capacity);
    if (!layout || !*layout) {
        return count < max_suggestions ? count : max_suggestions;
    }

    /* Re-score with keyboard distance */
    int input_len = (int)strlen(input);
    for (int i = 0; i < count; i++) {
        float wd = kb_weighted_distance(input, input_len,
                                         out[i].term, (int)strlen(out[i].term),
                                         ss->max_edit_distance, layout);
        out[i].weighted_distance = wd;
    }

    /* Re-sort by weighted_distance ASC, then frequency DESC */
    /* Simple insertion sort — count is small */
    for (int i = 1; i < count; i++) {
        ss_suggest_item_t tmp = out[i];
        int j = i - 1;
        while (j >= 0) {
            int swap = 0;
            if (tmp.weighted_distance < out[j].weighted_distance) {
                swap = 1;
            } else if (tmp.weighted_distance == out[j].weighted_distance &&
                       tmp.frequency > out[j].frequency) {
                swap = 1;
            }
            if (!swap) break;
            out[j + 1] = out[j];
            j--;
        }
        out[j + 1] = tmp;
    }

    return count < max_suggestions ? count : max_suggestions;
}

/* ---- Binary save/load (mmap-friendly format) --------------------------- */

#define SS_MAGIC 0x53534E44  /* "SSND" */
#define SS_VERSION 1

/*
 * File format:
 *   [Header]
 *     u32 magic
 *     u32 version
 *     i32 max_edit_distance
 *     i32 prefix_length
 *     u32 word_count
 *     u32 deletes_count
 *   [Word Table]  (word_count entries)
 *     u16 word_len
 *     char[word_len] word (NOT null-terminated in file)
 *     i32 frequency
 *   [Deletes Table] (deletes_count entries)
 *     u16 key_len
 *     char[key_len] key
 *     u32 bucket_size
 *     u32[bucket_size] word_indices
 */

int ss_save(symspell_t *ss, const char *path) {
    if (!ss || !path) return -1;
    FILE *f = fopen(path, "wb");
    if (!f) return -1;

    uint32_t magic = SS_MAGIC;
    uint32_t version = SS_VERSION;
    uint32_t word_count = ss->dict_words_count;

    /* Count actual deletes entries */
    uint32_t del_count = 0;
    for (uint32_t i = 0; i < ss->deletes_cap; i++) {
        if (ss->deletes[i].key) del_count++;
    }

    fwrite(&magic, 4, 1, f);
    fwrite(&version, 4, 1, f);
    fwrite(&ss->max_edit_distance, 4, 1, f);
    fwrite(&ss->prefix_length, 4, 1, f);
    fwrite(&word_count, 4, 1, f);
    fwrite(&del_count, 4, 1, f);

    /* Write words + frequencies */
    for (uint32_t i = 0; i < word_count; i++) {
        const char *w = ss->dict_words[i];
        uint16_t wlen = (uint16_t)strlen(w);
        dict_entry_t *de = dict_find(ss, w);
        int32_t freq = de ? de->freq : 0;
        fwrite(&wlen, 2, 1, f);
        fwrite(w, 1, wlen, f);
        fwrite(&freq, 4, 1, f);
    }

    /* Write deletes */
    for (uint32_t i = 0; i < ss->deletes_cap; i++) {
        if (!ss->deletes[i].key) continue;
        delete_entry_t *de = &ss->deletes[i];
        uint16_t klen = (uint16_t)strlen(de->key);
        uint32_t bsz = de->count;
        fwrite(&klen, 2, 1, f);
        fwrite(de->key, 1, klen, f);
        fwrite(&bsz, 4, 1, f);
        fwrite(de->word_ids, 4, bsz, f);
    }

    fclose(f);
    return 0;
}

symspell_t *ss_load_mmap(const char *path) {
    if (!path) return NULL;
    int fd = open(path, O_RDONLY);
    if (fd < 0) return NULL;

    struct stat st;
    if (fstat(fd, &st) < 0) { close(fd); return NULL; }
    size_t file_size = (size_t)st.st_size;
    if (file_size < 24) { close(fd); return NULL; }

    void *base = mmap(NULL, file_size, PROT_READ, MAP_PRIVATE, fd, 0);
    if (base == MAP_FAILED) { close(fd); return NULL; }

    const uint8_t *p = (const uint8_t *)base;
    const uint8_t *end = p + file_size;

    /* Read header */
    uint32_t magic, version;
    int32_t med, pl;
    uint32_t word_count, del_count;
    memcpy(&magic, p, 4); p += 4;
    memcpy(&version, p, 4); p += 4;
    memcpy(&med, p, 4); p += 4;
    memcpy(&pl, p, 4); p += 4;
    memcpy(&word_count, p, 4); p += 4;
    memcpy(&del_count, p, 4); p += 4;

    if (magic != SS_MAGIC || version != SS_VERSION) {
        munmap(base, file_size);
        close(fd);
        return NULL;
    }

    symspell_t *ss = ss_create(med, pl);
    if (!ss) {
        munmap(base, file_size);
        close(fd);
        return NULL;
    }
    ss->mmap_base = base;
    ss->mmap_size = file_size;
    ss->mmap_fd = fd;

    /* Read words */
    for (uint32_t i = 0; i < word_count && p < end; i++) {
        if (p + 2 > end) break;
        uint16_t wlen;
        memcpy(&wlen, p, 2); p += 2;
        if (p + wlen + 4 > end) break;

        char *word = arena_alloc(&ss->arena, wlen + 1);
        memcpy(word, p, wlen);
        word[wlen] = '\0';
        p += wlen;

        int32_t freq;
        memcpy(&freq, p, 4); p += 4;

        /* Insert into dict */
        dict_ensure_cap(ss);
        uint32_t h = fnv1a(word) & (ss->dict_cap - 1);
        while (ss->dict[h].key) h = (h + 1) & (ss->dict_cap - 1);
        ss->dict[h].key = word;
        ss->dict[h].freq = freq;
        ss->dict_count++;
        dict_words_push(ss, word);
    }

    /* Pre-allocate deletes table */
    ss->deletes_cap = 1;
    while (ss->deletes_cap < del_count * 2) ss->deletes_cap <<= 1;
    ss->deletes = (delete_entry_t *)calloc(ss->deletes_cap, sizeof(delete_entry_t));
    ss->deletes_count = 0;

    /* Read deletes */
    for (uint32_t i = 0; i < del_count && p < end; i++) {
        if (p + 2 > end) break;
        uint16_t klen;
        memcpy(&klen, p, 2); p += 2;
        if (p + klen + 4 > end) break;

        char *key = arena_alloc(&ss->arena, klen + 1);
        memcpy(key, p, klen);
        key[klen] = '\0';
        p += klen;

        uint32_t bsz;
        memcpy(&bsz, p, 4); p += 4;
        if (p + bsz * 4 > end) break;

        uint32_t *ids = (uint32_t *)malloc(bsz * sizeof(uint32_t));
        memcpy(ids, p, bsz * 4);
        p += bsz * 4;

        /* Insert into deletes table */
        uint32_t dh = fnv1a(key) & (ss->deletes_cap - 1);
        while (ss->deletes[dh].key) dh = (dh + 1) & (ss->deletes_cap - 1);
        ss->deletes[dh].key = key;
        ss->deletes[dh].word_ids = ids;
        ss->deletes[dh].count = bsz;
        ss->deletes[dh].capacity = bsz;
        ss->deletes_count++;
    }

    return ss;
}
