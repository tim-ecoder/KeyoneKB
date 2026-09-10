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

/* ---- UTF-8 ------------------------------------------------------------- */

/** Длина символа по его первому байту. Для битого байта — 1, чтобы не зациклиться. */
static int utf8_len(unsigned char c) {
    if (c < 0x80) return 1;
    if ((c & 0xE0) == 0xC0) return 2;
    if ((c & 0xF0) == 0xE0) return 3;
    if ((c & 0xF8) == 0xF0) return 4;
    return 1;
}

/**
 * Сколько байт занимают первые max_chars символов.
 *
 * Длина префикса задана в символах, а не в байтах. При счёте по байтам
 * кириллица получала вдвое более короткий префикс — индекс строился по трём
 * буквам вместо семи, и на один шаблон приходилась сотня слов вместо горстки.
 */
static int utf8_prefix_bytes(const char *s, int len, int max_chars) {
    int i = 0, chars = 0;
    while (i < len && chars < max_chars) {
        i += utf8_len((unsigned char)s[i]);
        chars++;
    }
    return i > len ? len : i;
}

/** Сколько символов в строке (битый байт считается за символ). */
static int utf8_count(const char *s, int len) {
    int i = 0, chars = 0;
    while (i < len) {
        int clen = utf8_len((unsigned char)s[i]);
        if (i + clen > len) clen = len - i;
        i += clen;
        chars++;
    }
    return chars;
}

/**
 * Разбор строки в кодовые точки. Расстояние правок считается по символам:
 * иначе одна кириллическая опечатка стоит двух правок и съедает весь бюджет.
 */
static int utf8_to_cp(const char *s, int len, uint32_t *out, int out_cap) {
    int i = 0, n = 0;
    while (i < len && n < out_cap) {
        unsigned char c = (unsigned char)s[i];
        int clen = utf8_len(c);
        /* Обрезаем так же, как utf8_count: иначе счётчик и разбор расходятся на
         * битой последовательности, и последний символ молча теряется. */
        if (i + clen > len) clen = len - i;
        uint32_t cp;
        switch (clen) {
            case 2: cp = c & 0x1Fu; break;
            case 3: cp = c & 0x0Fu; break;
            case 4: cp = c & 0x07u; break;
            default: cp = c; break;
        }
        for (int k = 1; k < clen; k++)
            cp = (cp << 6) | ((unsigned char)s[i + k] & 0x3Fu);
        out[n++] = cp;
        i += clen;
    }
    return n;
}

static int ss_damerau_distance_cp(const uint32_t *a, int alen,
                                  const uint32_t *b, int blen, int max_dist);

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
    const char *key;      /* NULL = empty slot (normalized form) */
    int         freq;
    const char *original; /* original (display) form, may == key */
} dict_entry_t;

/* Deletes entry: delete_pattern -> bucket of word indices */
typedef struct {
    const char *key;        /* NULL = empty slot */
    uint32_t   *word_ids;   /* array of indices into dict_words[] */
    uint32_t    count;
    uint32_t    capacity;
} delete_entry_t;

/* ---- Bigram types ------------------------------------------------------ */

typedef struct {
    const char *word1;
    const char *word2;
    const char *original2;
    int32_t     frequency;
} ss_bigram_t;

typedef struct {
    const char *word1;
    const char *word2;
    int32_t     frequency;
} bigram_hash_entry_t;

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
    const char  **dict_original_words;  /* parallel array: original forms */
    uint32_t      dict_words_count;
    uint32_t      dict_words_cap;

    /* Deletes table (delete_pattern -> [word_index, ...]) */
    delete_entry_t *deletes;
    uint32_t        deletes_cap;
    uint32_t        deletes_count;

    /* Bigram flat array (sorted by word1, freq desc after build) */
    ss_bigram_t *bigrams;
    uint32_t     bigram_count;
    uint32_t     bigram_cap;

    /* Bigram hash table for O(1) pair lookup */
    bigram_hash_entry_t *bigram_hash;
    uint32_t             bigram_hash_cap;
    uint32_t             bigram_hash_count;

    /* String storage */
    arena_t arena;

    /* mmap support */
    void  *mmap_base;
    size_t mmap_size;
    int    mmap_fd;

    /*
     * Формат v4: индекс читается прямо из отображённого файла.
     * Прежний v3 после отображения перекладывал таблицы в кучу — на полном
     * словаре это сотни мегабайт занятой памяти. Здесь в куче не остаётся
     * ничего, кроме крошечных биграмм: поиск идёт по страницам файла, и
     * система вытесняет их сама, когда памяти мало.
     */
    int             mapped;
    const uint8_t  *map_words;    /* word_count записей ss_map_word_t */
    const uint8_t  *map_deletes;  /* deletes_cap слотов ss_map_del_t */
    const uint32_t *map_ids;      /* списки индексов слов */
    const char     *map_strings;  /* строки, каждая с нулём на конце */
    size_t          map_strings_len; /* сколько байт занимает блок строк */
    uint32_t        map_ids_count;   /* сколько всего идентификаторов в файле */
};

/* Записи файла v4: только смещения, никаких указателей — файл переносим. */
typedef struct { uint32_t norm_off; uint32_t orig_off; int32_t freq; } ss_map_word_t;
typedef struct { uint32_t key_off; uint32_t ids_off; uint32_t ids_count; } ss_map_del_t;

#define SS_MAP_EMPTY 0xFFFFFFFFu

static const ss_map_word_t *map_word(const symspell_t *ss, uint32_t i) {
    return (const ss_map_word_t *)(ss->map_words + (size_t)i * sizeof(ss_map_word_t));
}

static const ss_map_del_t *map_del(const symspell_t *ss, uint32_t i) {
    return (const ss_map_del_t *)(ss->map_deletes + (size_t)i * sizeof(ss_map_del_t));
}

/* объявлены ниже: доступ к таблицам в куче */
static dict_entry_t *dict_find(const symspell_t *ss, const char *key);
static delete_entry_t *deletes_find(const symspell_t *ss, const char *key);

/* ---- Доступ к словам: одинаковый для кучи и для отображения ------------- */

/** Строка по смещению в блоке; пустая, если смещение указывает мимо блока. */
static const char *map_str(const symspell_t *ss, uint32_t off) {
    if (off >= ss->map_strings_len) return "";
    return ss->map_strings + off;
}

static const char *word_at(const symspell_t *ss, uint32_t i) {
    if (ss->mapped) return map_str(ss, map_word(ss, i)->norm_off);
    return ss->dict_words[i];
}

static const char *orig_at(const symspell_t *ss, uint32_t i) {
    if (ss->mapped) return map_str(ss, map_word(ss, i)->orig_off);
    return ss->dict_original_words[i];
}

/**
 * Частота слова. В отображении словарь отсортирован, поэтому двоичный поиск —
 * ради него хеш-таблицы слов в файле нет вовсе.
 * @return 1 если слово найдено.
 */
static int dict_freq(const symspell_t *ss, const char *key, int *freq_out) {
    if (!ss->mapped) {
        dict_entry_t *e = dict_find(ss, key);
        if (!e) return 0;
        if (freq_out) *freq_out = e->freq;
        return 1;
    }
    uint32_t lo = 0, hi = ss->dict_words_count;
    while (lo < hi) {
        uint32_t mid = (lo + hi) / 2;
        int c = strcmp(word_at(ss, mid), key);
        if (c == 0) {
            if (freq_out) *freq_out = map_word(ss, mid)->freq;
            return 1;
        }
        if (c < 0) lo = mid + 1; else hi = mid;
    }
    return 0;
}

/** Частота слова по его номеру в таблице. */
static int word_freq_at(const symspell_t *ss, uint32_t i) {
    if (ss->mapped) return map_word(ss, i)->freq;
    dict_entry_t *e = dict_find(ss, ss->dict_words[i]);
    return e ? e->freq : 0;
}

/** Номер слова в таблице и его частота. @return 1 если слово найдено. */
static int word_index(const symspell_t *ss, const char *key, uint32_t *idx_out, int *freq_out) {
    uint32_t lo = 0, hi = ss->dict_words_count;
    while (lo < hi) {
        uint32_t mid = (lo + hi) / 2;
        int c = strcmp(word_at(ss, mid), key);
        if (c == 0) {
            if (idx_out) *idx_out = mid;
            if (freq_out) *freq_out = word_freq_at(ss, mid);
            return 1;
        }
        if (c < 0) lo = mid + 1; else hi = mid;
    }
    return 0;
}

/** Список слов для шаблона удалений. @return 1 если шаблон найден. */
static int deletes_lookup(const symspell_t *ss, const char *key,
                          const uint32_t **ids_out, uint32_t *count_out) {
    if (!ss->mapped) {
        delete_entry_t *b = deletes_find(ss, key);
        if (!b) return 0;
        *ids_out = b->word_ids;
        *count_out = b->count;
        return 1;
    }
    if (!ss->deletes_cap) return 0;
    uint32_t h = fnv1a(key) & (ss->deletes_cap - 1);
    for (uint32_t probe = 0; probe < ss->deletes_cap; probe++) {
        const ss_map_del_t *e = map_del(ss, h);
        if (e->key_off == SS_MAP_EMPTY) return 0;
        if (strcmp(map_str(ss, e->key_off), key) == 0) {
            /* Смещение и длина списка тоже приходят из файла: за их границами
             * лежит уже не наша память. */
            if (e->ids_off > ss->map_ids_count
                    || e->ids_count > ss->map_ids_count - e->ids_off)
                return 0;
            *ids_out = ss->map_ids + e->ids_off;
            *count_out = e->ids_count;
            return 1;
        }
        h = (h + 1) & (ss->deletes_cap - 1);
    }
    return 0;
}

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

static void dict_words_push(symspell_t *ss, const char *word, const char *original) {
    if (ss->dict_words_count >= ss->dict_words_cap) {
        uint32_t new_cap = ss->dict_words_cap ? ss->dict_words_cap * 2 : 1024;
        const char **nw = (const char **)realloc(ss->dict_words, new_cap * sizeof(char *));
        if (!nw) return;
        ss->dict_words = nw;
        const char **now = (const char **)realloc(ss->dict_original_words, new_cap * sizeof(char *));
        if (!now) return;
        ss->dict_original_words = now;
        ss->dict_words_cap = new_cap;
    }
    ss->dict_words[ss->dict_words_count] = word;
    ss->dict_original_words[ss->dict_words_count] = original;
    ss->dict_words_count++;
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
            /* Existing bucket — add word_id if not duplicate.
             *
             * Достаточно сравнить с последним: все удаления одного слова
             * порождаются подряд, поэтому повтор может быть только соседним.
             * Прежняя проверка перебирала весь список, и на больших словарях
             * вставка вырождалась в квадратичную — корзины у частых шаблонов
             * разрастаются до тысяч слов. */
            delete_entry_t *e = &ss->deletes[h];
            if (e->count && e->word_ids[e->count - 1] == word_id) return;
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
    /* Считаем по символам, а не по байтам: кириллическая буква занимает два
     * байта, и побайтовый счёт превращал одну опечатку в две правки — при
     * бюджете в две правки перестановка или вторая ошибка уже не находились. */
    uint32_t a_stack[64], b_stack[64];
    uint32_t *acp = a_stack, *bcp = b_stack;
    uint32_t *a_heap = NULL, *b_heap = NULL;
    int alen_cp = utf8_count(a, alen);
    int blen_cp = utf8_count(b, blen);

    if (alen_cp > (int)(sizeof(a_stack) / sizeof(uint32_t))) {
        a_heap = (uint32_t *)malloc((size_t)alen_cp * sizeof(uint32_t));
        if (!a_heap) return -1;
        acp = a_heap;
    }
    if (blen_cp > (int)(sizeof(b_stack) / sizeof(uint32_t))) {
        b_heap = (uint32_t *)malloc((size_t)blen_cp * sizeof(uint32_t));
        if (!b_heap) { free(a_heap); return -1; }
        bcp = b_heap;
    }
    alen_cp = utf8_to_cp(a, alen, acp, alen_cp);
    blen_cp = utf8_to_cp(b, blen, bcp, blen_cp);

    int result = ss_damerau_distance_cp(acp, alen_cp, bcp, blen_cp, max_dist);
    free(a_heap);
    free(b_heap);
    return result;
}

/** Та же матрица, но по уже разобранным кодовым точкам. */
static int ss_damerau_distance_cp(const uint32_t *a, int alen,
                                  const uint32_t *b, int blen, int max_dist) {
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
    /* Снимается символ целиком: удаление одного байта разрывало кириллицу и
     * порождало ломаные последовательности вместо осмысленных шаблонов. */
    for (int i = 0; i < word_len; ) {
        int clen = utf8_len((unsigned char)word[i]);
        if (i + clen > word_len) clen = word_len - i;
        int pos = 0;
        for (int j = 0; j < word_len; j++) {
            if (j < i || j >= i + clen) buf[pos++] = word[j];
        }
        buf[pos] = '\0';
        i += clen;
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
    }
    if (ss->mapped) {
        /* Таблицы лежали в файле: освобождать нечего, кроме биграмм. */
        free(ss->bigrams);
        free(ss->bigram_hash);
        arena_free(&ss->arena);
        free(ss);
        return;
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
    free(ss->dict_original_words);
    free(ss->bigrams);
    free(ss->bigram_hash);
    arena_free(&ss->arena);
    free(ss);
}

void ss_add_word(symspell_t *ss, const char *word, const char *original, int frequency) {
    /* Отображённый словарь неизменяем: таблиц в куче у него нет, дописывать
     * некуда. Раньше попытка добавить пользовательское слово роняла процесс. */
    if (ss && ss->mapped) return;
    if (!ss || !word || !*word) return;
    dict_ensure_cap(ss);

    dict_entry_t *existing = dict_find(ss, word);
    if (existing) {
        if (frequency > existing->freq) existing->freq = frequency;
        return;
    }

    const char *stored = arena_strdup(&ss->arena, word);
    if (!stored) return;

    const char *stored_orig;
    if (!original || !*original || strcmp(word, original) == 0) {
        stored_orig = stored;  /* reuse same arena pointer */
    } else {
        stored_orig = arena_strdup(&ss->arena, original);
        if (!stored_orig) return;
    }

    uint32_t h = fnv1a(stored) & (ss->dict_cap - 1);
    while (ss->dict[h].key) h = (h + 1) & (ss->dict_cap - 1);
    ss->dict[h].key = stored;
    ss->dict[h].freq = frequency;
    ss->dict[h].original = stored_orig;
    ss->dict_count++;

    dict_words_push(ss, stored, stored_orig);
}

/* Comparison function for sorting word pairs by normalized form */
typedef struct { const char *norm; const char *orig; } word_pair_t;

static int wp_cmp(const void *a, const void *b) {
    const word_pair_t *wa = (const word_pair_t *)a;
    const word_pair_t *wb = (const word_pair_t *)b;
    return strcmp(wa->norm, wb->norm);
}

void ss_build_index(symspell_t *ss) {
    if (!ss || ss->mapped) return;

    /* Sort dict_words[] and dict_original_words[] by normalized form for prefix lookup */
    if (ss->dict_words_count > 1) {
        word_pair_t *pairs = (word_pair_t *)malloc(ss->dict_words_count * sizeof(word_pair_t));
        if (pairs) {
            for (uint32_t i = 0; i < ss->dict_words_count; i++) {
                pairs[i].norm = ss->dict_words[i];
                pairs[i].orig = ss->dict_original_words[i];
            }
            qsort(pairs, ss->dict_words_count, sizeof(word_pair_t), wp_cmp);
            for (uint32_t i = 0; i < ss->dict_words_count; i++) {
                ss->dict_words[i] = pairs[i].norm;
                ss->dict_original_words[i] = pairs[i].orig;
            }
            free(pairs);
        }
    }

    /* Clear existing deletes */
    if (ss->deletes) {
        for (uint32_t i = 0; i < ss->deletes_cap; i++) {
            free(ss->deletes[i].word_ids);
        }
        free(ss->deletes);
    }
    /* Pre-allocate deletes table: ~3x word count at 75% load */
    ss->deletes_cap = 1;
    while (ss->deletes_cap < ss->dict_count * 4) ss->deletes_cap <<= 1;
    ss->deletes = (delete_entry_t *)calloc(ss->deletes_cap, sizeof(delete_entry_t));
    ss->deletes_count = 0;
    if (!ss->deletes) return;

    /* Temp buffer for generating delete strings */
    char buf[512];

    for (uint32_t idx = 0; idx < ss->dict_words_count; idx++) {
        const char *word = ss->dict_words[idx];
        int wlen = (int)strlen(word);
        const char *key = word;
        int klen = utf8_prefix_bytes(word, wlen, ss->prefix_length);

        /* Use prefix for delete generation */
        char prefix[128];
        if (klen < (int)sizeof(prefix)) {
            memcpy(prefix, key, klen);
            prefix[klen] = '\0';
            generate_deletes(ss, prefix, klen, ss->max_edit_distance, idx, buf);
        }
    }
}

int ss_is_mapped(const symspell_t *ss) {
    return ss && ss->mapped ? 1 : 0;
}

int ss_size(const symspell_t *ss) {
    if (!ss) return 0;
    /* в отображении отдельного счётчика слов нет — он равен длине таблицы */
    return ss->mapped ? (int)ss->dict_words_count : (int)ss->dict_count;
}

int ss_contains(const symspell_t *ss, const char *word) {
    return dict_freq(ss, word, NULL);
}

int ss_get_frequency(const symspell_t *ss, const char *word) {
    int freq = 0;
    return dict_freq(ss, word, &freq) ? freq : 0;
}

/* ---- Bigram API -------------------------------------------------------- */

void ss_add_bigram(symspell_t *ss, const char *word1, const char *word2,
                   const char *original2, int frequency) {
    if (!ss || ss->mapped || !word1 || !*word1 || !word2 || !*word2) return;

    if (ss->bigram_count >= ss->bigram_cap) {
        uint32_t new_cap = ss->bigram_cap ? ss->bigram_cap * 2 : 1024;
        ss_bigram_t *nb = (ss_bigram_t *)realloc(ss->bigrams, new_cap * sizeof(ss_bigram_t));
        if (!nb) return;
        ss->bigrams = nb;
        ss->bigram_cap = new_cap;
    }

    /* Try to reuse dict key pointer for word1 */
    dict_entry_t *de = dict_find(ss, word1);
    const char *w1 = de ? de->key : arena_strdup(&ss->arena, word1);
    const char *w2 = arena_strdup(&ss->arena, word2);
    const char *o2;
    if (!original2 || !*original2 || strcmp(word2, original2) == 0) {
        o2 = w2;
    } else {
        o2 = arena_strdup(&ss->arena, original2);
    }

    ss_bigram_t *b = &ss->bigrams[ss->bigram_count++];
    b->word1 = w1;
    b->word2 = w2;
    b->original2 = o2;
    b->frequency = frequency;
}

/* Sort comparator: by word1 ASC, then frequency DESC */
static int bigram_cmp(const void *a, const void *b) {
    const ss_bigram_t *ba = (const ss_bigram_t *)a;
    const ss_bigram_t *bb = (const ss_bigram_t *)b;
    int c = strcmp(ba->word1, bb->word1);
    if (c != 0) return c;
    return bb->frequency - ba->frequency; /* desc */
}

static uint32_t bigram_pair_hash(const char *w1, const char *w2) {
    uint32_t h = 2166136261u;
    for (const char *s = w1; *s; s++) {
        h ^= (uint8_t)*s;
        h *= 16777619u;
    }
    h ^= 0xff; /* separator */
    h *= 16777619u;
    for (const char *s = w2; *s; s++) {
        h ^= (uint8_t)*s;
        h *= 16777619u;
    }
    return h;
}

void ss_build_bigram_index(symspell_t *ss) {
    if (!ss || ss->bigram_count == 0) return;

    /* Sort flat array */
    qsort(ss->bigrams, ss->bigram_count, sizeof(ss_bigram_t), bigram_cmp);

    /* Build hash table at ~50% load */
    uint32_t hash_cap = 1;
    while (hash_cap < ss->bigram_count * 2) hash_cap <<= 1;

    free(ss->bigram_hash);
    ss->bigram_hash = (bigram_hash_entry_t *)calloc(hash_cap, sizeof(bigram_hash_entry_t));
    if (!ss->bigram_hash) { ss->bigram_hash_cap = 0; return; }
    ss->bigram_hash_cap = hash_cap;
    ss->bigram_hash_count = 0;

    for (uint32_t i = 0; i < ss->bigram_count; i++) {
        uint32_t h = bigram_pair_hash(ss->bigrams[i].word1, ss->bigrams[i].word2) & (hash_cap - 1);
        while (ss->bigram_hash[h].word1) h = (h + 1) & (hash_cap - 1);
        ss->bigram_hash[h].word1 = ss->bigrams[i].word1;
        ss->bigram_hash[h].word2 = ss->bigrams[i].word2;
        ss->bigram_hash[h].frequency = ss->bigrams[i].frequency;
        ss->bigram_hash_count++;
    }
}

int ss_bigram_lookup(symspell_t *ss, const char *word1, int max_results,
                     ss_bigram_item_t *out, int out_capacity) {
    if (!ss || !word1 || !*word1 || !out || out_capacity <= 0 || ss->bigram_count == 0)
        return 0;

    int limit = max_results < out_capacity ? max_results : out_capacity;

    /* Binary search for first entry with word1 */
    uint32_t lo = 0, hi = ss->bigram_count;
    while (lo < hi) {
        uint32_t mid = lo + (hi - lo) / 2;
        int c = strcmp(ss->bigrams[mid].word1, word1);
        if (c < 0) lo = mid + 1;
        else hi = mid;
    }

    /* Scan forward collecting results (already sorted by freq desc) */
    int count = 0;
    for (uint32_t i = lo; i < ss->bigram_count && count < limit; i++) {
        if (strcmp(ss->bigrams[i].word1, word1) != 0) break;
        out[count].word = ss->bigrams[i].word2;
        out[count].original = ss->bigrams[i].original2;
        out[count].frequency = ss->bigrams[i].frequency;
        count++;
    }
    return count;
}

int ss_bigram_frequency(const symspell_t *ss, const char *word1, const char *word2) {
    if (!ss || !word1 || !word2 || ss->bigram_hash_cap == 0) return 0;
    uint32_t h = bigram_pair_hash(word1, word2) & (ss->bigram_hash_cap - 1);
    while (ss->bigram_hash[h].word1) {
        if (strcmp(ss->bigram_hash[h].word1, word1) == 0 &&
            strcmp(ss->bigram_hash[h].word2, word2) == 0) {
            return ss->bigram_hash[h].frequency;
        }
        h = (h + 1) & (ss->bigram_hash_cap - 1);
    }
    return 0;
}

int ss_bigram_count(const symspell_t *ss) {
    return ss ? (int)ss->bigram_count : 0;
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

    /* Truncate input to prefix length. Длина префикса приходит из файла
     * индекса, поэтому урезаем ещё и по размеру буфера: испорченный заголовок
     * не должен превращаться в переполнение стека. */
    char input_prefix[128];
    int prefix_len = utf8_prefix_bytes(input, input_len, ss->prefix_length);
    if (prefix_len > (int)sizeof(input_prefix) - 1)
        prefix_len = utf8_prefix_bytes(input, (int)sizeof(input_prefix) - 1,
                                       ss->prefix_length);
    memcpy(input_prefix, input, prefix_len);
    input_prefix[prefix_len] = '\0';
    int prefix_chars = utf8_count(input_prefix, prefix_len);

    queue_push(queue, input_prefix);
    seen_add(seen_candidates, input_prefix);

    /* Direct dictionary hit */
    uint32_t direct_idx;
    int direct_freq = 0;
    if (word_index(ss, input, &direct_idx, &direct_freq) && result_count < out_capacity) {
        if (seen_add(seen_suggestions, input)) {
            out[result_count].term = word_at(ss, direct_idx);
            out[result_count].original = orig_at(ss, direct_idx);
            out[result_count].distance = 0;
            out[result_count].frequency = direct_freq;
            out[result_count].weighted_distance = -1;
            result_count++;
        }
    }

    const char *candidate;
    while ((candidate = queue_pop(queue)) != NULL) {
        int cand_len = (int)strlen(candidate);
        /* Сколько правок уже потрачено — в символах: кандидаты получаются
         * снятием символов целиком, и байтовый счёт давал кириллице вдвое
         * меньший бюджет, чем тот, с которым строился индекс. */
        int distance = prefix_chars - utf8_count(candidate, cand_len);
        if (distance > ss->max_edit_distance) continue;

        /* Check deletes table */
        const uint32_t *bucket_ids = NULL;
        uint32_t bucket_count = 0;
        if (deletes_lookup(ss, candidate, &bucket_ids, &bucket_count)) {
            for (uint32_t i = 0; i < bucket_count; i++) {
                uint32_t wid = bucket_ids[i];
                if (wid >= ss->dict_words_count) continue;
                const char *suggestion = word_at(ss, wid);
                int sug_len = (int)strlen(suggestion);

                int ed = ss_damerau_distance(input, input_len, suggestion, sug_len,
                                              ss->max_edit_distance);
                if (ed >= 0 && ed <= ss->max_edit_distance) {
                    if (seen_add(seen_suggestions, suggestion)) {
                        ss_suggest_item_t item;
                        item.term = suggestion;
                        item.original = orig_at(ss, wid);
                        item.distance = ed;
                        item.frequency = word_freq_at(ss, wid);
                        item.weighted_distance = -1;
                        if (result_count < out_capacity) {
                            out[result_count++] = item;
                        } else {
                            /* Буфер полон — держим лучших, а не первых попавшихся.
                             * Слова находятся в порядке снятия символов, а не по
                             * качеству, и сортировка внизу разбирала уже усечённый
                             * список: для «елка» так терялась «ёлка» (d=1), зато
                             * оставались «белках» и «гжелка» на d=2. */
                            int worst = 0;
                            for (int k = 1; k < out_capacity; k++)
                                if (suggest_cmp(&out[k], &out[worst]) > 0) worst = k;
                            if (suggest_cmp(&item, &out[worst]) < 0)
                                out[worst] = item;
                        }
                    }
                }
            }
        }

        /* Generate further deletes of the candidate */
        if (distance < ss->max_edit_distance) {
            /* Снимаем символ целиком — так же, как строились шаблоны индекса.
             * Побайтовое снятие рвало кириллицу и порождало кандидатов,
             * которым в индексе не соответствует ничего. */
            for (int i = 0; i < cand_len; ) {
                int clen = utf8_len((unsigned char)candidate[i]);
                if (i + clen > cand_len) clen = cand_len - i;
                char del[128];
                int pos = 0;
                for (int j = 0; j < cand_len; j++) {
                    if (j < i || j >= i + clen) del[pos++] = candidate[j];
                }
                del[pos] = '\0';
                i += clen;
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

/* ---- Prefix lookup (binary search on sorted dict_words[]) -------------- */

int ss_prefix_lookup(symspell_t *ss, const char *prefix, int max_results,
                     ss_suggest_item_t *out, int out_capacity) {
    if (!ss || !prefix || !*prefix || !out || out_capacity <= 0) return 0;

    int prefix_len = (int)strlen(prefix);
    int limit = max_results < out_capacity ? max_results : out_capacity;

    /* Binary search for first entry >= prefix */
    uint32_t lo = 0, hi = ss->dict_words_count;
    while (lo < hi) {
        uint32_t mid = lo + (hi - lo) / 2;
        if (strncmp(word_at(ss, mid), prefix, prefix_len) < 0)
            lo = mid + 1;
        else
            hi = mid;
    }

    /* Collect top-N matches by frequency using a selection buffer */
    int count = 0;
    int min_freq = 0;
    int min_idx = 0;

    for (uint32_t i = lo; i < ss->dict_words_count; i++) {
        if (strncmp(word_at(ss, i), prefix, prefix_len) != 0) break;
        int freq = word_freq_at(ss, i);

        if (count < limit) {
            out[count].term = word_at(ss, i);
            out[count].original = orig_at(ss, i);
            out[count].distance = 0;
            out[count].frequency = freq;
            out[count].weighted_distance = -1;
            count++;
            if (count == limit) {
                /* Find min in buffer */
                min_idx = 0;
                min_freq = out[0].frequency;
                for (int k = 1; k < count; k++) {
                    if (out[k].frequency < min_freq) {
                        min_freq = out[k].frequency;
                        min_idx = k;
                    }
                }
            }
        } else if (freq > min_freq) {
            out[min_idx].term = word_at(ss, i);
            out[min_idx].original = orig_at(ss, i);
            out[min_idx].frequency = freq;
            /* Re-find min */
            min_idx = 0;
            min_freq = out[0].frequency;
            for (int k = 1; k < count; k++) {
                if (out[k].frequency < min_freq) {
                    min_freq = out[k].frequency;
                    min_idx = k;
                }
            }
        }
    }

    /* Sort by frequency descending (insertion sort — count is small) */
    for (int i = 1; i < count; i++) {
        ss_suggest_item_t tmp = out[i];
        int j = i - 1;
        while (j >= 0 && out[j].frequency < tmp.frequency) {
            out[j + 1] = out[j];
            j--;
        }
        out[j + 1] = tmp;
    }

    return count;
}

/* ---- Binary save/load (mmap-friendly format) --------------------------- */

#define SS_MAGIC 0x53534E44  /* "SSND" */
/* Заголовок v4: 14 полей по 4 байта. */
#define SS_V4_HEADER_BYTES 56
#define SS_VERSION 3

/*
 * File format v3 (backward-compatible with v2 load):
 *   [Header]
 *     u32 magic
 *     u32 version (= 3, accepts 2 on load)
 *     i32 max_edit_distance
 *     i32 prefix_length
 *     u32 word_count
 *     u32 deletes_count
 *   [Word Table]  (word_count entries, in sorted order)
 *     u16 word_len
 *     char[word_len] word (normalized, NOT null-terminated in file)
 *     u16 original_len  (0 if original == normalized)
 *     char[original_len] original (NOT null-terminated, absent if original_len==0)
 *     i32 frequency
 *   [Deletes Table] (deletes_count entries)
 *     u16 key_len
 *     char[key_len] key
 *     u32 bucket_size
 *     u32[bucket_size] word_indices
 *   [Bigram Table] (v3 only)
 *     u32 bigram_count
 *     Per entry:
 *       u16 w1_len, char[w1_len] w1
 *       u16 w2_len, char[w2_len] w2
 *       u16 o2_len, char[o2_len] o2 (absent if o2_len==0, meaning o2==w2)
 *       i32 frequency
 */

/*
 * Формат v4 — пригоден для поиска прямо в отображённом файле.
 *
 * В v3 таблицы лежали потоком записей переменной длины, поэтому загрузчик был
 * обязан построить хеш-таблицу в куче: на полном словаре это сотни мегабайт.
 * Здесь таблица удалений хранится готовой, ссылки — смещениями, строки лежат
 * в одном блобе с нулём на конце. Файл не зависит от устройства, поэтому его
 * можно собрать заранее и положить в языковой пакет.
 *
 *   [Заголовок]
 *     u32 magic 'SSN4', u32 version = 4
 *     i32 max_edit_distance, i32 prefix_length
 *     u32 word_count, u32 deletes_cap, u32 deletes_count, u32 bigram_count
 *     u32 words_off, deletes_off, ids_off, bigrams_off, strings_off, file_size
 *   [Слова]      word_count × { u32 norm_off, u32 orig_off, i32 freq }
 *   [Удаления]   deletes_cap × { u32 key_off, u32 ids_off, u32 ids_count }
 *                пустой слот: key_off = 0xFFFFFFFF
 *   [Списки]     u32 — номера слов, на которые ссылаются удаления
 *   [Биграммы]   bigram_count × { u32 w1_off, u32 w2_off, u32 o2_off, i32 freq }
 *   [Строки]     все строки подряд, каждая с нулём на конце
 */

/*
 * v5 отличается от v4 тем, что префикс и удаления считаются в символах, а не в
 * байтах. Файлы v4 самосогласованы, но с новым поиском дадут расхождение,
 * поэтому не принимаются: клавиатура пересоберёт индекс.
 */
#define SS_MAGIC_V4 0x53534E34  /* "SSN4" */
#define SS_FORMAT_VERSION 5

/* Накопитель строк с попутным устранением повторов. */
typedef struct {
    char     *data;
    uint32_t  len;
    uint32_t  cap;
    int      failed;  /* не хватило памяти — файл писать нельзя */
} strblob_t;

/**
 * Кладёт строку в блок и возвращает её смещение.
 *
 * При нехватке памяти взводит флаг ошибки: возвращаемый 0 — законное смещение
 * (там лежит пустая строка), и без флага сбой realloc давал бы файл, который
 * читается, но состоит из пустых слов.
 */
static uint32_t blob_put(strblob_t *b, const char *s) {
    if (!s) s = "";
    uint32_t need = (uint32_t)strlen(s) + 1;
    if (b->len + need > b->cap) {
        uint32_t nc = b->cap ? b->cap : 1 << 16;
        while (nc < b->len + need) nc <<= 1;
        char *nd = (char *)realloc(b->data, nc);
        if (!nd) { b->failed = 1; return 0; }
        b->data = nd;
        b->cap = nc;
    }
    uint32_t off = b->len;
    memcpy(b->data + off, s, need);
    b->len += need;
    return off;
}

int ss_save(symspell_t *ss, const char *path) {
    if (!ss || !path || ss->mapped) return -1;   /* отображённый уже сохранён */

    strblob_t blob;
    memset(&blob, 0, sizeof(blob));
    blob_put(&blob, "");   /* смещение 0 — пустая строка */

    uint32_t word_count = ss->dict_words_count;
    ss_map_word_t *words = (ss_map_word_t *)calloc(word_count ? word_count : 1, sizeof(ss_map_word_t));
    if (!words) { free(blob.data); return -1; }
    for (uint32_t i = 0; i < word_count; i++) {
        const char *norm = ss->dict_words[i];
        const char *orig = ss->dict_original_words[i];
        words[i].norm_off = blob_put(&blob, norm);
        words[i].orig_off = (orig && strcmp(orig, norm) != 0) ? blob_put(&blob, orig) : words[i].norm_off;
        dict_entry_t *de = dict_find(ss, norm);
        words[i].freq = de ? de->freq : 0;
    }

    uint32_t ids_total = 0, del_count = 0;
    for (uint32_t i = 0; i < ss->deletes_cap; i++) {
        if (ss->deletes[i].key) { ids_total += ss->deletes[i].count; del_count++; }
    }

    /* Таблица в файле перехеширована под фактическое число шаблонов. В памяти
     * она рассчитана на четыре слота на слово и заполнена процентов на пять —
     * у русского это 48 МБ пустоты против 6 нужных. Поиск от этого не страдает:
     * он вычисляет слот тем же хешем по ёмкости из заголовка. */
    uint32_t cap = 1;
    while (cap < del_count * 2) cap <<= 1;
    ss_map_del_t *dels = (ss_map_del_t *)calloc(cap, sizeof(ss_map_del_t));
    if (!dels) { free(words); free(blob.data); return -1; }
    for (uint32_t i = 0; i < cap; i++) dels[i].key_off = SS_MAP_EMPTY;
    uint32_t *ids = (uint32_t *)malloc((ids_total ? ids_total : 1) * sizeof(uint32_t));
    if (!ids) { free(dels); free(words); free(blob.data); return -1; }

    uint32_t ids_pos = 0;
    for (uint32_t i = 0; i < ss->deletes_cap; i++) {
        if (!ss->deletes[i].key) continue;
        uint32_t h = fnv1a(ss->deletes[i].key) & (cap - 1);
        while (dels[h].key_off != SS_MAP_EMPTY) h = (h + 1) & (cap - 1);
        dels[h].key_off = blob_put(&blob, ss->deletes[i].key);
        dels[h].ids_off = ids_pos;
        dels[h].ids_count = ss->deletes[i].count;
        memcpy(ids + ids_pos, ss->deletes[i].word_ids, ss->deletes[i].count * sizeof(uint32_t));
        ids_pos += ss->deletes[i].count;
    }

    uint32_t bigram_count = ss->bigram_count;
    uint32_t *bigrams = (uint32_t *)calloc((bigram_count ? bigram_count : 1) * 4, sizeof(uint32_t));
    if (!bigrams) { free(ids); free(dels); free(words); free(blob.data); return -1; }
    for (uint32_t i = 0; i < bigram_count; i++) {
        bigrams[i * 4 + 0] = blob_put(&blob, ss->bigrams[i].word1);
        bigrams[i * 4 + 1] = blob_put(&blob, ss->bigrams[i].word2);
        bigrams[i * 4 + 2] = ss->bigrams[i].original2 && strcmp(ss->bigrams[i].original2, ss->bigrams[i].word2) != 0
                ? blob_put(&blob, ss->bigrams[i].original2) : bigrams[i * 4 + 1];
        memcpy(&bigrams[i * 4 + 3], &ss->bigrams[i].frequency, 4);
    }

    uint32_t header[14];
    uint32_t words_off = sizeof(header);
    uint32_t deletes_off = words_off + word_count * (uint32_t)sizeof(ss_map_word_t);
    uint32_t ids_off = deletes_off + cap * (uint32_t)sizeof(ss_map_del_t);
    uint32_t bigrams_off = ids_off + ids_total * 4;
    uint32_t strings_off = bigrams_off + bigram_count * 16;

    header[0] = SS_MAGIC_V4;
    header[1] = SS_FORMAT_VERSION;
    memcpy(&header[2], &ss->max_edit_distance, 4);
    memcpy(&header[3], &ss->prefix_length, 4);
    header[4] = word_count;
    header[5] = cap;
    header[6] = del_count;
    header[7] = bigram_count;
    header[8] = words_off;
    header[9] = deletes_off;
    header[10] = ids_off;
    header[11] = bigrams_off;
    header[12] = strings_off;
    header[13] = strings_off + blob.len;

    if (blob.failed) {
        /* Строки не поместились в память: писать такой индекс нельзя — он
         * прочитается, но все слова в нём будут пустыми. */
        free(bigrams); free(ids); free(dels); free(words); free(blob.data);
        return -1;
    }

    FILE *f = fopen(path, "wb");
    int ok = f != NULL;
    if (ok) {
        ok &= fwrite(header, sizeof(header), 1, f) == 1;
        if (word_count) ok &= fwrite(words, sizeof(ss_map_word_t), word_count, f) == word_count;
        if (cap) ok &= fwrite(dels, sizeof(ss_map_del_t), cap, f) == cap;
        if (ids_total) ok &= fwrite(ids, 4, ids_total, f) == ids_total;
        if (bigram_count) ok &= fwrite(bigrams, 16, bigram_count, f) == bigram_count;
        if (blob.len) ok &= fwrite(blob.data, 1, blob.len, f) == blob.len;
        fclose(f);
    }

    free(bigrams); free(ids); free(dels); free(words); free(blob.data);
    return ok ? 0 : -1;
}

/**
 * Загрузка формата v4: только отображение и проверка заголовка.
 *
 * Отображение (`map_base`/`map_size`) и данные (`base`/`file_size`) разделены:
 * когда индекс лежит внутри APK, отображать приходится со страничной границы,
 * а данные начинаются дальше. Освобождается всегда отображение целиком.
 *
 * Ничего не копируется в кучу — поиск идёт прямо по страницам файла. Биграммы
 * всё же читаются в память: их тысячи, а не миллионы, и хеш пары строится за
 * доли миллисекунды.
 */
static symspell_t *ss_load_mmap_v4(int fd, void *map_base, size_t map_size,
                                   void *base, size_t file_size) {
    const uint32_t *h = (const uint32_t *)base;
    uint32_t word_count = h[4], cap = h[5], bigram_count = h[7];
    uint32_t words_off = h[8], deletes_off = h[9], ids_off = h[10];
    uint32_t bigrams_off = h[11], strings_off = h[12], expect_size = h[13];

    /*
     * Заголовок приходит из файла, который мог быть обрезан, испорчен или
     * подложен пользователем в папку приложения. Ни одно поле нельзя брать на
     * веру: дальше они становятся арифметикой указателей по отображению, и
     * первое же нажатие клавиши уводило бы поиск за границу файла.
     */
    uint64_t words_end = (uint64_t)words_off + (uint64_t)word_count * sizeof(ss_map_word_t);
    uint64_t dels_end  = (uint64_t)deletes_off + (uint64_t)cap * sizeof(ss_map_del_t);
    uint64_t bg_end    = (uint64_t)bigrams_off + (uint64_t)bigram_count * 16u;
    /* Строки читаются strlen/strcmp от смещения: без нуля в конце блока чтение
     * уходит за отображение. И смещения должны быть кратны четырём — по ним
     * читаются структуры и массивы uint32. */
    int blob_terminated = file_size > strings_off
            && ((const char *)base)[file_size - 1] == '\0';
    int aligned = ((words_off | deletes_off | ids_off | bigrams_off) & 3u) == 0;
    int header_sane =
            blob_terminated
            && aligned
            && expect_size == file_size
            && strings_off <= file_size
            && words_off >= SS_V4_HEADER_BYTES
            && words_end <= deletes_off
            && dels_end <= ids_off
            && ids_off <= bigrams_off
            && (bigram_count == 0 || (bigrams_off >= ids_off && bg_end <= strings_off))
            && bigrams_off <= strings_off
            && strings_off <= expect_size;
    if (!header_sane) {
        munmap(map_base, map_size);
        close(fd);
        return NULL;
    }

    int med, pl;
    memcpy(&med, &h[2], 4);
    memcpy(&pl, &h[3], 4);
    /* prefix_length из файла тоже проверяем: с ним считается длина префикса
     * запроса, и заведомо большое значение раздувало бы её без границ. */
    if (med < 1 || med > SYMSPELL_MAX_EDIT_DIST || pl < 1 || pl > 32) {
        munmap(map_base, map_size);
        close(fd);
        return NULL;
    }

    symspell_t *ss = ss_create(med, pl);
    if (!ss) {
        munmap(map_base, map_size);
        close(fd);
        return NULL;
    }

    ss->mmap_base = map_base;
    ss->mmap_size = map_size;
    ss->mmap_fd = fd;
    ss->mapped = 1;
    ss->map_words = (const uint8_t *)base + words_off;
    ss->map_deletes = (const uint8_t *)base + deletes_off;
    ss->map_ids = (const uint32_t *)((const uint8_t *)base + ids_off);
    ss->map_strings = (const char *)base + strings_off;
    ss->map_strings_len = file_size - strings_off;
    ss->map_ids_count = (bigrams_off - ids_off) / 4u;
    ss->dict_words_count = word_count;
    ss->dict_count = word_count;
    ss->deletes_cap = cap;
    ss->deletes_count = h[6];

    if (bigram_count) {
        ss->bigrams = (ss_bigram_t *)malloc(bigram_count * sizeof(ss_bigram_t));
        if (ss->bigrams) {
            const uint32_t *b = (const uint32_t *)((const uint8_t *)base + bigrams_off);
            for (uint32_t i = 0; i < bigram_count; i++) {
                ss->bigrams[i].word1 = map_str(ss, b[i * 4 + 0]);
                ss->bigrams[i].word2 = map_str(ss, b[i * 4 + 1]);
                ss->bigrams[i].original2 = map_str(ss, b[i * 4 + 2]);
                memcpy(&ss->bigrams[i].frequency, &b[i * 4 + 3], 4);
            }
            ss->bigram_count = bigram_count;
            ss->bigram_cap = bigram_count;
            ss_build_bigram_index(ss);
        }
    }
    return ss;
}

/*
 * Общая часть: отобразить [offset, offset+length) файла и разобрать заголовок.
 * fd переходит во владение индекса — при неудаче закрывается здесь.
 */
static symspell_t *ss_load_mmap_range(int fd, size_t offset, size_t length) {
    /* Заголовок v4 занимает 56 байт и читается целиком до всякой проверки:
     * прежний порог в 24 байта позволял читать поля из-за конца файла. */
    if (length < SS_V4_HEADER_BYTES) { close(fd); return NULL; }

    /* mmap умеет только страничные смещения, поэтому отображение начинается
     * раньше данных, а разница добавляется к указателю. Внутри APK файл
     * выровнен по 4 байтам (zipalign), и страничное округление этого не
     * ломает — формат больше четырёх байт и не требует. */
    long page_size = sysconf(_SC_PAGE_SIZE);
    if (page_size <= 0) page_size = 4096;
    size_t aligned = (offset / (size_t)page_size) * (size_t)page_size;
    size_t extra = offset - aligned;
    size_t map_size = length + extra;

    void *map_base = mmap(NULL, map_size, PROT_READ, MAP_PRIVATE, fd, (off_t)aligned);
    if (map_base == MAP_FAILED) { close(fd); return NULL; }
    void *base = (uint8_t *)map_base + extra;

    /* Выравнивание данных отвечает за чтение uint32 по отображению: со сдвинутым
     * началом заголовок читался бы вкось. */
    if (((uintptr_t)base & 3u) != 0) {
        munmap(map_base, map_size);
        close(fd);
        return NULL;
    }

    uint32_t magic, version;
    memcpy(&magic, (const uint8_t *)base, 4);
    memcpy(&version, (const uint8_t *)base + 4, 4);

    if (magic == SS_MAGIC_V4 && version == SS_FORMAT_VERSION)
        return ss_load_mmap_v4(fd, map_base, map_size, base, length);

    /* Всё остальное — индекс, собранный прежней версией. Форматы v2 и v3
     * считали префикс в байтах, и с нынешним посимвольным поиском такой индекс
     * молча не находит ничего; пересобрать дешевле, чем держать оба правила. */
    munmap(map_base, map_size);
    close(fd);
    return NULL;
}

symspell_t *ss_load_mmap(const char *path) {
    if (!path) return NULL;
    int fd = open(path, O_RDONLY);
    if (fd < 0) return NULL;

    struct stat st;
    if (fstat(fd, &st) < 0) { close(fd); return NULL; }
    return ss_load_mmap_range(fd, 0, (size_t)st.st_size);
}

/*
 * Тот же индекс, но куском чужого файла: так он читается прямо из APK
 * языкового пакета, без распаковки копии в папку приложения. Работает, только
 * пока .ssnd лежит в пакете несжатым, — сжатую запись отобразить нельзя.
 *
 * fd остаётся за вызывающим: индекс дублирует его для себя, потому что живёт
 * дольше, чем AssetFileDescriptor на стороне Java.
 */
symspell_t *ss_load_mmap_fd(int fd, size_t offset, size_t length) {
    if (fd < 0) return NULL;
    int own = dup(fd);
    if (own < 0) return NULL;

    struct stat st;
    if (fstat(own, &st) < 0) { close(own); return NULL; }
    /* Смещение и длина приходят из чужого APK: без проверки по концу файла
     * отображение уводило бы за него. */
    if (length == 0 || offset > (size_t)st.st_size
            || length > (size_t)st.st_size - offset) {
        close(own);
        return NULL;
    }
    return ss_load_mmap_range(own, offset, length);
}

