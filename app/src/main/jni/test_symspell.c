/*
 * Native test harness for SymSpell — runs on host (x86_64).
 * Usage: ./test_symspell [path_to_en_base.txt]
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "symspell.h"

static int tests_passed = 0;
static int tests_failed = 0;

#define ASSERT_EQ(msg, expected, actual) do { \
    if ((expected) == (actual)) { tests_passed++; } \
    else { tests_failed++; printf("FAIL: %s: expected %d, got %d\n", msg, (int)(expected), (int)(actual)); } \
} while(0)

#define ASSERT_TRUE(msg, cond) do { \
    if (cond) { tests_passed++; } \
    else { tests_failed++; printf("FAIL: %s\n", msg); } \
} while(0)

static void test_basic(void) {
    printf("--- test_basic ---\n");
    symspell_t *ss = ss_create(2, 7);
    ASSERT_TRUE("create not null", ss != NULL);

    ss_add_word(ss, "hello", "hello", 100);
    ss_add_word(ss, "world", "world", 90);
    ss_add_word(ss, "help", "help", 80);
    ss_add_word(ss, "hero", "hero", 70);
    ss_add_word(ss, "hell", "hell", 60);
    ss_build_index(ss);

    ASSERT_EQ("size", 5, ss_size(ss));
    ASSERT_TRUE("contains hello", ss_contains(ss, "hello"));
    ASSERT_TRUE("!contains xyz", !ss_contains(ss, "xyz"));
    ASSERT_EQ("freq hello", 100, ss_get_frequency(ss, "hello"));

    /* Exact match */
    ss_suggest_item_t results[10];
    int n = ss_lookup(ss, "hello", 5, results, 10);
    ASSERT_TRUE("lookup hello >= 1", n >= 1);
    if (n > 0) {
        ASSERT_TRUE("first result is hello", strcmp(results[0].term, "hello") == 0);
        ASSERT_EQ("hello distance 0", 0, results[0].distance);
    }

    /* Typo: helo -> hello (distance 1) */
    n = ss_lookup(ss, "helo", 5, results, 10);
    ASSERT_TRUE("lookup helo >= 1", n >= 1);
    int found_hello = 0;
    for (int i = 0; i < n; i++) {
        if (strcmp(results[i].term, "hello") == 0) { found_hello = 1; break; }
    }
    ASSERT_TRUE("helo -> hello found", found_hello);

    /* Typo: wrld -> world (distance 1) */
    n = ss_lookup(ss, "wrld", 5, results, 10);
    int found_world = 0;
    for (int i = 0; i < n; i++) {
        if (strcmp(results[i].term, "world") == 0) { found_world = 1; break; }
    }
    ASSERT_TRUE("wrld -> world found", found_world);

    /* Typo: hlep -> help (transposition, distance 1) */
    n = ss_lookup(ss, "hlep", 5, results, 10);
    int found_help = 0;
    for (int i = 0; i < n; i++) {
        if (strcmp(results[i].term, "help") == 0) { found_help = 1; break; }
    }
    ASSERT_TRUE("hlep -> help found", found_help);

    ss_destroy(ss);
    printf("--- test_basic done ---\n\n");
}

static void test_prefix(void) {
    printf("--- test_prefix ---\n");
    symspell_t *ss = ss_create(2, 7);

    /* Add words with different original forms */
    ss_add_word(ss, "hello", "Hello", 100);
    ss_add_word(ss, "help", "Help", 80);
    ss_add_word(ss, "hero", "Hero", 70);
    ss_add_word(ss, "world", "World", 90);
    ss_add_word(ss, "hell", "hell", 60);
    ss_add_word(ss, "heap", "heap", 50);
    ss_build_index(ss);

    ss_suggest_item_t results[10];

    /* Prefix "hel" should match hello, help, hell */
    int n = ss_prefix_lookup(ss, "hel", 10, results, 10);
    ASSERT_TRUE("prefix hel >= 3", n >= 3);
    /* Should be sorted by frequency: hello(100), help(80), hell(60) */
    if (n >= 3) {
        ASSERT_EQ("prefix hel[0] freq", 100, results[0].frequency);
        ASSERT_EQ("prefix hel[1] freq", 80, results[1].frequency);
        ASSERT_EQ("prefix hel[2] freq", 60, results[2].frequency);
    }
    /* Results should have original forms */
    if (n >= 1) {
        ASSERT_TRUE("prefix hel[0] original is Hello",
                     strcmp(results[0].original, "Hello") == 0);
    }

    /* Prefix "he" should match all he* words */
    n = ss_prefix_lookup(ss, "he", 10, results, 10);
    ASSERT_TRUE("prefix he >= 4", n >= 4);
    /* First should be highest freq: hello(100) */
    ASSERT_EQ("prefix he[0] freq", 100, results[0].frequency);

    /* Prefix "w" should match world */
    n = ss_prefix_lookup(ss, "w", 10, results, 10);
    ASSERT_EQ("prefix w count", 1, n);
    if (n >= 1) {
        ASSERT_TRUE("prefix w[0] original is World",
                     strcmp(results[0].original, "World") == 0);
    }

    /* Prefix "xyz" should match nothing */
    n = ss_prefix_lookup(ss, "xyz", 10, results, 10);
    ASSERT_EQ("prefix xyz count", 0, n);

    /* Test max_results limiting */
    n = ss_prefix_lookup(ss, "he", 2, results, 10);
    ASSERT_EQ("prefix he limited to 2", 2, n);
    ASSERT_EQ("prefix he limited [0] freq", 100, results[0].frequency);

    ss_destroy(ss);
    printf("--- test_prefix done ---\n\n");
}

static void test_save_load_v2(void) {
    printf("--- test_save_load_v2 ---\n");
    symspell_t *ss = ss_create(2, 7);
    ss_add_word(ss, "hello", "Hello", 100);
    ss_add_word(ss, "world", "World", 90);
    ss_add_word(ss, "test", "test", 80);  /* original == normalized */
    ss_build_index(ss);

    const char *tmpfile = "/tmp/symspell_test_v2.ssnd";
    int rc = ss_save(ss, tmpfile);
    ASSERT_EQ("save v2 ok", 0, rc);
    ss_destroy(ss);

    /* Load via mmap */
    ss = ss_load_mmap(tmpfile);
    ASSERT_TRUE("load v2 not null", ss != NULL);
    if (ss) {
        ASSERT_EQ("loaded v2 size", 3, ss_size(ss));
        ASSERT_EQ("loaded v2 freq", 100, ss_get_frequency(ss, "hello"));

        /* Verify original forms survive roundtrip via lookup */
        ss_suggest_item_t results[10];
        int n = ss_lookup(ss, "hello", 5, results, 10);
        ASSERT_TRUE("v2 lookup hello >= 1", n >= 1);
        if (n > 0) {
            ASSERT_TRUE("v2 lookup hello original=Hello",
                         strcmp(results[0].original, "Hello") == 0);
        }

        /* Verify prefix lookup with originals */
        n = ss_prefix_lookup(ss, "hel", 10, results, 10);
        ASSERT_TRUE("v2 prefix hel >= 1", n >= 1);
        if (n > 0) {
            ASSERT_TRUE("v2 prefix hel[0] original=Hello",
                         strcmp(results[0].original, "Hello") == 0);
        }

        /* Verify original==normalized case */
        n = ss_lookup(ss, "test", 5, results, 10);
        ASSERT_TRUE("v2 lookup test >= 1", n >= 1);
        if (n > 0) {
            ASSERT_TRUE("v2 lookup test original=test",
                         strcmp(results[0].original, "test") == 0);
        }

        ss_destroy(ss);
    }
    remove(tmpfile);
    printf("--- test_save_load_v2 done ---\n\n");
}

static void test_save_load(void) {
    printf("--- test_save_load ---\n");
    symspell_t *ss = ss_create(2, 7);
    ss_add_word(ss, "hello", "hello", 100);
    ss_add_word(ss, "world", "world", 90);
    ss_add_word(ss, "test", "test", 80);
    ss_build_index(ss);

    const char *tmpfile = "/tmp/symspell_test.ssnd";
    int rc = ss_save(ss, tmpfile);
    ASSERT_EQ("save ok", 0, rc);
    ss_destroy(ss);

    /* Load via mmap */
    ss = ss_load_mmap(tmpfile);
    ASSERT_TRUE("load not null", ss != NULL);
    if (ss) {
        ASSERT_EQ("loaded size", 3, ss_size(ss));
        ASSERT_EQ("loaded freq", 100, ss_get_frequency(ss, "hello"));

        ss_suggest_item_t results[10];
        int n = ss_lookup(ss, "helo", 5, results, 10);
        int found = 0;
        for (int i = 0; i < n; i++) {
            if (strcmp(results[i].term, "hello") == 0) { found = 1; break; }
        }
        ASSERT_TRUE("loaded lookup helo -> hello", found);
        ss_destroy(ss);
    }
    remove(tmpfile);
    printf("--- test_save_load done ---\n\n");
}

static void test_with_dict(const char *dict_path) {
    printf("--- test_with_dict: %s ---\n", dict_path);
    FILE *f = fopen(dict_path, "r");
    if (!f) {
        printf("  Cannot open dict file, skipping\n");
        return;
    }

    symspell_t *ss = ss_create(2, 7);
    char line[256];
    int word_count = 0;
    int max_words = 35000;

    while (fgets(line, sizeof(line), f) && word_count < max_words) {
        /* Format: word\tfrequency */
        char *tab = strchr(line, '\t');
        if (!tab) continue;
        *tab = '\0';
        int freq = atoi(tab + 1);
        if (freq <= 0) continue;
        ss_add_word(ss, line, line, freq);
        word_count++;
    }
    fclose(f);
    printf("  Loaded %d words\n", word_count);

    /* Build index */
    clock_t start = clock();
    ss_build_index(ss);
    double build_ms = (double)(clock() - start) / CLOCKS_PER_SEC * 1000.0;
    printf("  buildIndex: %.1f ms\n", build_ms);

    /* Benchmark lookups */
    const char *test_inputs[] = {
        "hello", "helo", "wrld", "teh", "becaus", "programing",
        "langauge", "recieve", "definately", "seperate"
    };
    int ntest = sizeof(test_inputs) / sizeof(test_inputs[0]);
    ss_suggest_item_t results[20];

    start = clock();
    int total_results = 0;
    for (int round = 0; round < 1000; round++) {
        for (int i = 0; i < ntest; i++) {
            int n = ss_lookup(ss, test_inputs[i], 10, results, 20);
            total_results += n;
        }
    }
    double lookup_ms = (double)(clock() - start) / CLOCKS_PER_SEC * 1000.0;
    printf("  10000 lookups: %.1f ms (%.2f us/lookup)\n",
           lookup_ms, lookup_ms * 1000.0 / 10000.0);

    /* Print some example results */
    for (int i = 0; i < ntest; i++) {
        int n = ss_lookup(ss, test_inputs[i], 5, results, 20);
        printf("  \"%s\" -> ", test_inputs[i]);
        for (int j = 0; j < n && j < 3; j++) {
            printf("%s(d=%d,f=%d) ", results[j].term, results[j].distance, results[j].frequency);
        }
        printf("\n");
    }

    /* Test save/load roundtrip */
    const char *cache = "/tmp/symspell_dict_test.ssnd";
    start = clock();
    ss_save(ss, cache);
    double save_ms = (double)(clock() - start) / CLOCKS_PER_SEC * 1000.0;
    printf("  save: %.1f ms\n", save_ms);

    start = clock();
    symspell_t *loaded = ss_load_mmap(cache);
    double load_ms = (double)(clock() - start) / CLOCKS_PER_SEC * 1000.0;
    printf("  load (mmap): %.1f ms, %d words\n", load_ms, ss_size(loaded));

    /* Verify loaded instance works */
    int n = ss_lookup(loaded, "helo", 5, results, 20);
    int ok = 0;
    for (int j = 0; j < n; j++) {
        if (strcmp(results[j].term, "hello") == 0) { ok = 1; break; }
    }
    ASSERT_TRUE("loaded dict lookup helo->hello", ok);

    ss_destroy(loaded);
    ss_destroy(ss);
    remove(cache);
    printf("--- test_with_dict done ---\n\n");
}

int main(int argc, char **argv) {
    test_basic();
    test_prefix();
    test_save_load_v2();
    test_save_load();

    if (argc > 1) {
        test_with_dict(argv[1]);
    } else {
        /* Try default path */
        const char *default_dict = "app/src/main/assets/dictionaries/en_base.txt";
        FILE *f = fopen(default_dict, "r");
        if (f) {
            fclose(f);
            test_with_dict(default_dict);
        } else {
            printf("(No dict file provided, skipping dict test)\n");
        }
    }

    printf("=== Results: %d passed, %d failed ===\n", tests_passed, tests_failed);
    return tests_failed > 0 ? 1 : 0;
}
