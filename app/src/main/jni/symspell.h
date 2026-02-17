#ifndef SYMSPELL_H
#define SYMSPELL_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Maximum edit distance supported */
#define SYMSPELL_MAX_EDIT_DIST 3
/* Default prefix length for indexing */
#define SYMSPELL_DEFAULT_PREFIX_LEN 7

typedef struct {
    const char *term;    /* points into arena or mmap region */
    int         distance;
    int         frequency;
    float       weighted_distance; /* keyboard-weighted distance, -1 if unused */
} ss_suggest_item_t;

typedef struct symspell symspell_t;

/* Create/destroy */
symspell_t *ss_create(int max_edit_distance, int prefix_length);
void        ss_destroy(symspell_t *ss);

/* Dictionary building */
void ss_add_word(symspell_t *ss, const char *word, int frequency);
void ss_build_index(symspell_t *ss);

/* Lookup — returns number of results written to out[] */
int ss_lookup(symspell_t *ss, const char *input, int max_suggestions,
              ss_suggest_item_t *out, int out_capacity);

/* Lookup with keyboard-weighted distance */
int ss_lookup_weighted(symspell_t *ss, const char *input, int max_suggestions,
                       ss_suggest_item_t *out, int out_capacity,
                       const char *layout);

/* Binary serialization */
int  ss_save(symspell_t *ss, const char *path);
symspell_t *ss_load_mmap(const char *path);

/* Query */
int  ss_size(const symspell_t *ss);
int  ss_contains(const symspell_t *ss, const char *word);
int  ss_get_frequency(const symspell_t *ss, const char *word);

#ifdef __cplusplus
}
#endif

#endif /* SYMSPELL_H */
