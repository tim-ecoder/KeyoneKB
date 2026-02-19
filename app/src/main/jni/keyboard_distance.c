/*
 * Keyboard-weighted edit distance for hardware keyboards.
 *
 * Physical key proximity on QWERTY/ЙЦУКЕН layouts is used to
 * reduce the cost of substituting adjacent keys (common typos on
 * BlackBerry KEY1/2 and Unihertz Titan).
 */

#include "keyboard_distance.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>

/* ---- QWERTY layout adjacency ------------------------------------------ */

/*
 * Key position table: row, col for each lowercase ASCII letter.
 * Row 0: qwertyuiop
 * Row 1: asdfghjkl
 * Row 2: zxcvbnm
 */
static const int qwerty_row[] = {
    /* a-z positions */
    1, 2, 2, 1, 0, 1, 1, 1, 0, 1, 1, 1, 2, 2, 0, 0, 0, 0, 1, 0,
    0, 2, 0, 2, 0, 2
};
static const int qwerty_col[] = {
    /* a-z columns */
    0, 4, 2, 2, 2, 3, 4, 5, 7, 6, 7, 8, 6, 5, 8, 9, 0, 3, 1, 4,
    6, 3, 1, 1, 5, 0
};

/* ЙЦУКЕН layout (Russian) — map lowercase Cyrillic to row,col.
 * We use the first byte of UTF-8 for quick identification,
 * but for actual distance we map to a position index. */

/* For Russian, we store positions for Unicode code points 0x430-0x44F (а-я).
 * Row 0: йцукенгшщзхъ (12 keys)
 * Row 1: фывапролджэ  (11 keys)
 * Row 2: ячсмитьбю    (9 keys)
 */
static const int russian_row[32] = {
    /* а=0x430 */ 1, /* б=0x431 */ 2, /* в=0x432 */ 1, /* г=0x433 */ 0,
    /* д=0x434 */ 1, /* е=0x435 */ 0, /* ж=0x436 */ 1, /* з=0x437 */ 0,
    /* и=0x438 */ 2, /* й=0x439 */ 0, /* к=0x43A */ 0, /* л=0x43B */ 1,
    /* м=0x43C */ 2, /* н=0x43D */ 0, /* о=0x43E */ 1, /* п=0x43F */ 1,
    /* р=0x440 */ 1, /* с=0x441 */ 2, /* т=0x442 */ 2, /* у=0x443 */ 0,
    /* ф=0x444 */ 1, /* х=0x445 */ 0, /* ц=0x446 */ 0, /* ч=0x447 */ 2,
    /* ш=0x448 */ 0, /* щ=0x449 */ 0, /* ъ=0x44A */ 0, /* ы=0x44B */ 1,
    /* ь=0x44C */ 2, /* э=0x44D */ 1, /* ю=0x44E */ 2, /* я=0x44F */ 2
};

static const int russian_col[32] = {
    /* а=0x430 */ 4, /* б=0x431 */ 7, /* в=0x432 */ 2, /* г=0x433 */ 6,
    /* д=0x434 */ 8, /* е=0x435 */ 4, /* ж=0x436 */ 9, /* з=0x437 */ 9,
    /* и=0x438 */ 5, /* й=0x439 */ 0, /* к=0x43A */ 5, /* л=0x43B */ 7,
    /* м=0x43C */ 3, /* н=0x43D */ 7, /* о=0x43E */ 6, /* п=0x43F */ 5,
    /* р=0x440 */ 3, /* с=0x441 */ 2, /* т=0x442 */ 4, /* у=0x443 */ 3,
    /* ф=0x444 */ 0, /* х=0x445 */ 10, /* ц=0x446 */ 1, /* ч=0x447 */ 0,
    /* ш=0x448 */ 8, /* щ=0x449 */ 9, /* ъ=0x44A */ 11, /* ы=0x44B */ 1,
    /* ь=0x44C */ 6, /* э=0x44D */ 10, /* ю=0x44E */ 8, /* я=0x44F */ 1
};

/* Distance between two key positions */
static float key_distance(int r1, int c1, int r2, int c2) {
    int dr = abs(r1 - r2);
    int dc = abs(c1 - c2);
    if (dr == 0 && dc == 0) return 0.0f;
    if (dr <= 1 && dc <= 1) return 0.3f;   /* adjacent (including diagonal) */
    if (dr == 0 && dc <= 2) return 0.6f;    /* same row, close */
    if (dr <= 1 && dc <= 2) return 0.7f;    /* near */
    return 1.0f;                             /* far */
}

float kb_substitution_cost(unsigned char a, unsigned char b, const char *layout) {
    if (a == b) return 0.0f;

    if (layout && strcmp(layout, "qwerty") == 0) {
        /* ASCII lowercase letters only */
        if (a >= 'a' && a <= 'z' && b >= 'a' && b <= 'z') {
            int ia = a - 'a', ib = b - 'a';
            return key_distance(qwerty_row[ia], qwerty_col[ia],
                               qwerty_row[ib], qwerty_col[ib]);
        }
    }

    /* Default: full cost */
    return 1.0f;
}

/* ---- Weighted Damerau-Levenshtein -------------------------------------- */

float kb_weighted_distance(const char *a, int alen,
                           const char *b, int blen,
                           int max_distance,
                           const char *layout) {
    if (abs(alen - blen) > max_distance) return -1.0f;
    if (alen == 0) return (float)blen;
    if (blen == 0) return (float)alen;

    /* Allocate DP matrices on heap for safety */
    float *buf = (float *)malloc(3 * (blen + 1) * sizeof(float));
    if (!buf) return -1.0f;
    float *prev_prev = buf;
    float *prev = buf + (blen + 1);
    float *curr = buf + 2 * (blen + 1);

    for (int j = 0; j <= blen; j++) prev[j] = (float)j;

    for (int i = 1; i <= alen; i++) {
        curr[0] = (float)i;
        float min_row = curr[0];
        for (int j = 1; j <= blen; j++) {
            float sub_cost = kb_substitution_cost(
                (unsigned char)a[i - 1], (unsigned char)b[j - 1], layout);
            float del_val = prev[j] + 1.0f;
            float ins_val = curr[j - 1] + 1.0f;
            float rep_val = prev[j - 1] + sub_cost;
            float val = del_val;
            if (ins_val < val) val = ins_val;
            if (rep_val < val) val = rep_val;
            /* Transposition */
            if (i > 1 && j > 1 &&
                a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                float trans = prev_prev[j - 2] + 1.0f;
                if (trans < val) val = trans;
            }
            curr[j] = val;
            if (val < min_row) min_row = val;
        }
        if (min_row > (float)max_distance) {
            free(buf);
            return -1.0f;
        }
        float *tmp = prev_prev;
        prev_prev = prev;
        prev = curr;
        curr = tmp;
    }

    float result = prev[blen];
    free(buf);
    return result <= (float)max_distance ? result : -1.0f;
}
