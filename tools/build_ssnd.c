/*
 * Сборка индекса подсказок (.ssnd v4) на хосте.
 *
 * Формат не зависит от устройства, поэтому индекс можно построить заранее и
 * положить в языковой пакет — клавиатуре останется только отобразить файл.
 *
 * На вход подаётся файл «нормализованное<TAB>исходное<TAB>частота», который
 * готовит tools/prepare_dict.py: нормализация обязана совпадать с
 * WordDictionary.normalize из приложения, а она опирается на юникодные таблицы,
 * поэтому её проще выполнить в Python, чем повторять здесь.
 *
 *   gcc -O2 -o build_ssnd tools/build_ssnd.c app/src/main/jni/symspell.c \
 *       app/src/main/jni/keyboard_distance.c -lm
 *   ./build_ssnd prepared.tsv ru.ssnd [предел_слов] [биграммы.tsv]
 */
#include "../app/src/main/jni/symspell.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static char *next_field(char **p) {
    char *start = *p;
    char *tab = strchr(start, '\t');
    if (tab) { *tab = '\0'; *p = tab + 1; } else { *p = start + strlen(start); }
    return start;
}

int main(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr, "usage: %s prepared.tsv out.ssnd [max_words] [bigrams.tsv]\n", argv[0]);
        return 2;
    }
    long max_words = argc > 3 ? atol(argv[3]) : 0;

    FILE *in = fopen(argv[1], "rb");
    if (!in) { fprintf(stderr, "нет файла %s\n", argv[1]); return 1; }

    symspell_t *ss = ss_create(2, 7);
    char line[1024];
    long added = 0;
    while (fgets(line, sizeof(line), in)) {
        size_t len = strlen(line);
        while (len && (line[len - 1] == '\n' || line[len - 1] == '\r')) line[--len] = '\0';
        if (!len) continue;
        char *p = line;
        char *norm = next_field(&p);
        char *orig = next_field(&p);
        char *freq = next_field(&p);
        if (!*norm || !*freq) continue;
        ss_add_word(ss, norm, *orig ? orig : norm, atoi(freq));
        if (max_words > 0 && ++added >= max_words) break;
    }
    fclose(in);

    if (argc > 4) {
        FILE *bg = fopen(argv[4], "rb");
        if (bg) {
            while (fgets(line, sizeof(line), bg)) {
                size_t len = strlen(line);
                while (len && (line[len - 1] == '\n' || line[len - 1] == '\r')) line[--len] = '\0';
                if (!len) continue;
                char *p = line;
                char *w1 = next_field(&p);
                char *w2 = next_field(&p);
                char *o2 = next_field(&p);
                char *fr = next_field(&p);
                if (!*w1 || !*w2 || !*fr) continue;
                ss_add_bigram(ss, w1, w2, *o2 ? o2 : w2, atoi(fr));
            }
            fclose(bg);
        }
    }

    ss_build_index(ss);
    ss_build_bigram_index(ss);

    if (ss_save(ss, argv[2]) != 0) {
        fprintf(stderr, "не удалось записать %s\n", argv[2]);
        ss_destroy(ss);
        return 1;
    }
    printf("%s: слов %d, биграмм %d\n", argv[2], ss_size(ss), ss_bigram_count(ss));
    ss_destroy(ss);
    return 0;
}
