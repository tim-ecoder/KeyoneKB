/*
 * Keyboard-weighted edit distance for hardware keyboards.
 *
 * Physical key proximity on QWERTY/ЙЦУКЕН layouts is used to
 * reduce the cost of substituting adjacent keys (common typos on
 * BlackBerry KEY1/2 and Unihertz Titan).
 */

#include "keyboard_distance.h"
#include <stdint.h>
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

/* Раскладка определяется по самим символам, а не по имени: у пары кириллических
 * букв близость считается по ЙЦУКЕН, у латинских — по QWERTY. Прежняя версия
 * смотрела только на latin a-z и на строку layout, которую никто не задавал,
 * поэтому для русского учёт соседних клавиш не работал вовсе, а таблицы
 * ЙЦУКЕН лежали без дела. */
/**
 * Буква без диакритики: ё -> е, й -> и, ї -> і, ў -> у.
 *
 * Нормализация слов кириллицу не разлагает (WordDictionary.normalize), поэтому
 * такие буквы доходят до поиска как есть, и замена одной на другую попадала в
 * общий счёт по геометрии клавиатуры: ё на ЙЦУКЕН лежит слева от единицы, далеко
 * от е, и для «елка» «белка» оказывалась ближе, чем «ёлка».
 *
 * Но это не промах по соседней клавише, а другое написание того же слова.
 */
uint32_t kb_letter_base(uint32_t cp) {
    switch (cp) {
        case 0x451: return 0x435;  /* ё -> е */
        case 0x450: return 0x435;  /* ѐ -> е */
        case 0x439: return 0x438;  /* й -> и */
        case 0x45D: return 0x438;  /* ѝ -> и */
        case 0x457: return 0x456;  /* ї -> і */
        case 0x45E: return 0x443;  /* ў -> у */
        case 0x453: return 0x433;  /* ѓ -> г */
        case 0x45C: return 0x43A;  /* ќ -> к */
        default:    return cp;
    }
}

/** Замена буквы на её же написание с диакритикой — почти бесплатно. */
#define KB_SPELLING_SUB_COST 0.15f

float kb_substitution_cost_cp(uint32_t a, uint32_t b, const char *layout) {
    (void)layout;
    if (a == b) return 0.0f;

    if (kb_letter_base(a) == kb_letter_base(b)) return KB_SPELLING_SUB_COST;

    if (a >= 'a' && a <= 'z' && b >= 'a' && b <= 'z') {
        int ia = (int)a - 'a', ib = (int)b - 'a';
        return key_distance(qwerty_row[ia], qwerty_col[ia],
                            qwerty_row[ib], qwerty_col[ib]);
    }

    /* ё стоит отдельно от ряда а-я, на клавише слева от единицы */
    if ((a >= 0x430 && a <= 0x44F) || a == 0x451) {
        if ((b >= 0x430 && b <= 0x44F) || b == 0x451) {
            int ia = (a == 0x451) ? -1 : (int)(a - 0x430);
            int ib = (b == 0x451) ? -1 : (int)(b - 0x430);
            int ra = ia < 0 ? 0 : russian_row[ia];
            int ca = ia < 0 ? 12 : russian_col[ia];
            int rb = ib < 0 ? 0 : russian_row[ib];
            int cb = ib < 0 ? 12 : russian_col[ib];
            return key_distance(ra, ca, rb, cb);
        }
    }

    return 1.0f;
}

float kb_substitution_cost(unsigned char a, unsigned char b, const char *layout) {
    return kb_substitution_cost_cp((uint32_t)a, (uint32_t)b, layout);
}

/** Разбор UTF-8 в кодовые точки. @return число символов, -1 если не поместилось. */
static int utf8_decode(const char *s, int len, uint32_t *out, int out_cap) {
    int i = 0, n = 0;
    while (i < len) {
        if (n >= out_cap) return -1;
        unsigned char c = (unsigned char)s[i];
        uint32_t cp;
        int clen;
        if (c < 0x80) { cp = c; clen = 1; }
        else if ((c & 0xE0) == 0xC0) { cp = c & 0x1F; clen = 2; }
        else if ((c & 0xF0) == 0xE0) { cp = c & 0x0F; clen = 3; }
        else if ((c & 0xF8) == 0xF0) { cp = c & 0x07; clen = 4; }
        else { cp = c; clen = 1; }
        for (int k = 1; k < clen && i + k < len; k++)
            cp = (cp << 6) | ((unsigned char)s[i + k] & 0x3F);
        out[n++] = cp;
        i += clen;
    }
    return n;
}

/* ---- Weighted Damerau-Levenshtein -------------------------------------- */

float kb_weighted_distance(const char *a, int alen,
                           const char *b, int blen,
                           int max_distance,
                           const char *layout) {
    /* Считаем по символам: у кириллицы байтовая длина вдвое больше, и на байтах
     * расстояние получалось завышенным, а перестановки букв не опознавались. */
    uint32_t abuf[128], bbuf[128];
    uint32_t *ca = abuf, *cb = bbuf;
    int alen_cp = utf8_decode(a, alen, abuf, 128);
    int blen_cp = utf8_decode(b, blen, bbuf, 128);
    if (alen_cp < 0 || blen_cp < 0) return -1.0f;
    alen = alen_cp;
    blen = blen_cp;

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
            float sub_cost = kb_substitution_cost_cp(ca[i - 1], cb[j - 1], layout);
            float del_val = prev[j] + 1.0f;
            float ins_val = curr[j - 1] + 1.0f;
            float rep_val = prev[j - 1] + sub_cost;
            float val = del_val;
            if (ins_val < val) val = ins_val;
            if (rep_val < val) val = rep_val;
            /* Transposition */
            if (i > 1 && j > 1 &&
                ca[i - 1] == cb[j - 2] && ca[i - 2] == cb[j - 1]) {
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
