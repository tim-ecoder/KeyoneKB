/*
 * JNI bridge for native CDB-based translation dictionary.
 * Bridges com.ai10.k12kb.prediction.NativeTranslationDictionary <-> C cdb_t
 *
 * CDB value format: comma-separated translations, e.g. "собака,пёс"
 * Keys are lowercase UTF-8. Phrase keys contain space: "hot dog"
 */

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>
#include <stdio.h>
#include <android/log.h>
#include "cdb.h"

#define LOG_TAG "NativeTransDict"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static const char *jstr_get(JNIEnv *env, jstring js) {
    if (!js) return NULL;
    return (*env)->GetStringUTFChars(env, js, NULL);
}

static void jstr_release(JNIEnv *env, jstring js, const char *cs) {
    if (js && cs) (*env)->ReleaseStringUTFChars(env, js, cs);
}

/* Lowercase a UTF-8 string in-place (ASCII portion only; Cyrillic stays as-is
   since the TSV source is already lowercased). */
static void ascii_lower(char *s, size_t len) {
    for (size_t i = 0; i < len; i++) {
        if (s[i] >= 'A' && s[i] <= 'Z') s[i] += 32;
    }
}

/*
 * Split comma-separated value into a Java String[].
 * val points into mmap'd memory, vlen is its length.
 */
static jobjectArray split_translations(JNIEnv *env, const char *val, size_t vlen) {
    /* Count commas to determine array size */
    int count = 1;
    for (size_t i = 0; i < vlen; i++) {
        if (val[i] == ',') count++;
    }

    jclass strClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray arr = (*env)->NewObjectArray(env, count, strClass, NULL);
    if (!arr) return NULL;

    int idx = 0;
    const char *start = val;
    for (size_t i = 0; i <= vlen; i++) {
        if (i == vlen || val[i] == ',') {
            size_t seglen = (val + i) - start;
            /* Trim leading/trailing spaces */
            while (seglen > 0 && *start == ' ') { start++; seglen--; }
            while (seglen > 0 && start[seglen - 1] == ' ') seglen--;
            if (seglen > 0 && idx < count) {
                /* NewStringUTF needs null-terminated; copy segment */
                char buf[512];
                if (seglen >= sizeof(buf)) seglen = sizeof(buf) - 1;
                memcpy(buf, start, seglen);
                buf[seglen] = '\0';
                jstring js = (*env)->NewStringUTF(env, buf);
                (*env)->SetObjectArrayElement(env, arr, idx, js);
                (*env)->DeleteLocalRef(env, js);
                idx++;
            }
            start = val + i + 1;
        }
    }

    /* Shrink if some segments were empty */
    if (idx < count) {
        jobjectArray trimmed = (*env)->NewObjectArray(env, idx, strClass, NULL);
        for (int i = 0; i < idx; i++) {
            jobject elem = (*env)->GetObjectArrayElement(env, arr, i);
            (*env)->SetObjectArrayElement(env, trimmed, i, elem);
            (*env)->DeleteLocalRef(env, elem);
        }
        (*env)->DeleteLocalRef(env, arr);
        return trimmed;
    }
    return arr;
}

/* ---- JNI Methods ------------------------------------------------------- */

/*
 * static native long nativeOpen(String path);
 */
JNIEXPORT jlong JNICALL
Java_com_ai10_k12kb_prediction_NativeTranslationDictionary_nativeOpen(
        JNIEnv *env, jclass clazz, jstring jpath) {
    const char *path = jstr_get(env, jpath);
    if (!path) return 0;

    cdb_t *cdb = (cdb_t *)calloc(1, sizeof(cdb_t));
    if (!cdb) {
        jstr_release(env, jpath, path);
        return 0;
    }

    if (cdb_open(cdb, path) != 0) {
        LOGW("Failed to open CDB: %s", path);
        free(cdb);
        jstr_release(env, jpath, path);
        return 0;
    }

    LOGI("Opened CDB dict: %s (%zu bytes)", path, cdb->size);
    jstr_release(env, jpath, path);
    return (jlong)(intptr_t)cdb;
}

/*
 * static native long nativeOpenFd(int fd, long offset, long length);
 * Open CDB directly from APK file descriptor (zero-copy mmap).
 */
JNIEXPORT jlong JNICALL
Java_com_ai10_k12kb_prediction_NativeTranslationDictionary_nativeOpenFd(
        JNIEnv *env, jclass clazz, jint fd, jlong offset, jlong length) {
    cdb_t *cdb = (cdb_t *)calloc(1, sizeof(cdb_t));
    if (!cdb) return 0;

    if (cdb_open_fd(cdb, fd, (size_t)offset, (size_t)length) != 0) {
        LOGW("Failed to open CDB from fd=%d offset=%lld len=%lld", fd, offset, length);
        free(cdb);
        return 0;
    }

    LOGI("Opened CDB from APK fd (zero-copy): %zu bytes", cdb->size);
    return (jlong)(intptr_t)cdb;
}

/*
 * static native void nativeClose(long ptr);
 */
JNIEXPORT void JNICALL
Java_com_ai10_k12kb_prediction_NativeTranslationDictionary_nativeClose(
        JNIEnv *env, jclass clazz, jlong ptr) {
    cdb_t *cdb = (cdb_t *)(intptr_t)ptr;
    if (!cdb) return;
    LOGI("Closing CDB dict");
    cdb_close(cdb);
    free(cdb);
}

/*
 * static native String[] nativeLookup(long ptr, String key);
 * Returns translations as String[], or null if not found.
 */
JNIEXPORT jobjectArray JNICALL
Java_com_ai10_k12kb_prediction_NativeTranslationDictionary_nativeLookup(
        JNIEnv *env, jclass clazz, jlong ptr, jstring jkey) {
    cdb_t *cdb = (cdb_t *)(intptr_t)ptr;
    if (!cdb || !jkey) return NULL;

    const char *key_raw = jstr_get(env, jkey);
    if (!key_raw) return NULL;

    size_t klen = strlen(key_raw);
    const char *val;
    size_t vlen;
    int found = cdb_find(cdb, key_raw, klen, &val, &vlen);
    jstr_release(env, jkey, key_raw);
    if (found) {
        return split_translations(env, val, vlen);
    }
    return NULL;
}

/*
 * static native String[] nativeTranslate(long ptr, String word, String previousWord);
 * Tries phrase lookup first ("prevWord currentWord"), then single word.
 * Returns: String[] where first element is the phrase result count as a string,
 * followed by the translation strings.
 */
JNIEXPORT jobjectArray JNICALL
Java_com_ai10_k12kb_prediction_NativeTranslationDictionary_nativeTranslate(
        JNIEnv *env, jclass clazz, jlong ptr, jstring jword, jstring jprev) {
    cdb_t *cdb = (cdb_t *)(intptr_t)ptr;
    if (!cdb || !jword) return NULL;

    const char *word = jstr_get(env, jword);
    if (!word) return NULL;
    const char *prev = jstr_get(env, jprev);
    /* Java side already lowercased both strings */

    size_t wlen = strlen(word);

    /* Collect results: up to 32 translations */
    const char *results[32];
    size_t result_lens[32];
    int count = 0;
    int phrase_count = 0;

    /* 1. Phrase lookup */
    if (prev && prev[0] != '\0') {
        size_t plen = strlen(prev);
        char phrase[1024];
        if (plen + 1 + wlen < sizeof(phrase)) {
            memcpy(phrase, prev, plen);
            phrase[plen] = ' ';
            memcpy(phrase + plen + 1, word, wlen);
            size_t total = plen + 1 + wlen;
            phrase[total] = '\0';

            const char *val;
            size_t vlen;
            if (cdb_find(cdb, phrase, total, &val, &vlen)) {
                /* Parse comma-separated phrase translations */
                const char *start = val;
                for (size_t i = 0; i <= vlen && count < 32; i++) {
                    if (i == vlen || val[i] == ',') {
                        const char *s = start;
                        size_t slen = (val + i) - s;
                        while (slen > 0 && *s == ' ') { s++; slen--; }
                        while (slen > 0 && s[slen-1] == ' ') slen--;
                        if (slen > 0) {
                            results[count] = s;
                            result_lens[count] = slen;
                            count++;
                        }
                        start = val + i + 1;
                    }
                }
                phrase_count = count;
            }
        }
    }
    if (prev) jstr_release(env, jprev, prev);

    /* 2. Single-word lookup */
    const char *val;
    size_t vlen;
    if (cdb_find(cdb, word, wlen, &val, &vlen)) {
        const char *start = val;
        for (size_t i = 0; i <= vlen && count < 32; i++) {
            if (i == vlen || val[i] == ',') {
                const char *s = start;
                size_t slen = (val + i) - s;
                while (slen > 0 && *s == ' ') { s++; slen--; }
                while (slen > 0 && s[slen-1] == ' ') slen--;
                if (slen > 0) {
                    /* Deduplicate against phrase results */
                    int dup = 0;
                    for (int j = 0; j < count && !dup; j++) {
                        if (result_lens[j] == slen &&
                            memcmp(results[j], s, slen) == 0) dup = 1;
                    }
                    if (!dup) {
                        results[count] = s;
                        result_lens[count] = slen;
                        count++;
                    }
                }
                start = val + i + 1;
            }
        }
    }

    jstr_release(env, jword, word);

    if (count == 0) return NULL;

    /* Build result array: [phrase_count_str, translation1, translation2, ...] */
    jclass strClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray arr = (*env)->NewObjectArray(env, count + 1, strClass, NULL);
    if (!arr) return NULL;

    char numbuf[16];
    snprintf(numbuf, sizeof(numbuf), "%d", phrase_count);
    jstring jpc = (*env)->NewStringUTF(env, numbuf);
    (*env)->SetObjectArrayElement(env, arr, 0, jpc);
    (*env)->DeleteLocalRef(env, jpc);

    for (int i = 0; i < count; i++) {
        char buf[512];
        size_t slen = result_lens[i];
        if (slen >= sizeof(buf)) slen = sizeof(buf) - 1;
        memcpy(buf, results[i], slen);
        buf[slen] = '\0';
        jstring js = (*env)->NewStringUTF(env, buf);
        (*env)->SetObjectArrayElement(env, arr, i + 1, js);
        (*env)->DeleteLocalRef(env, js);
    }

    return arr;
}

/* ---- Frequency map for usage-based trimming ---- */

typedef struct {
    const char *key;   /* pointer into loaded buffer (not owned) */
    uint32_t klen;
    int freq;
} fmap_slot_t;

typedef struct {
    fmap_slot_t *slots;
    uint32_t cap;      /* power of 2 */
} fmap_t;

static void fmap_init(fmap_t *m, uint32_t expected) {
    m->cap = 1;
    while (m->cap < expected * 2) m->cap <<= 1;
    m->slots = (fmap_slot_t *)calloc(m->cap, sizeof(fmap_slot_t));
}

static void fmap_put(fmap_t *m, const char *key, uint32_t klen, int freq) {
    uint32_t h = cdb_hash(key, klen);
    uint32_t idx = h & (m->cap - 1);
    while (m->slots[idx].key != NULL) {
        if (m->slots[idx].klen == klen && memcmp(m->slots[idx].key, key, klen) == 0)
            return; /* already present, keep first (higher freq in sorted dict) */
        idx = (idx + 1) & (m->cap - 1);
    }
    m->slots[idx].key = key;
    m->slots[idx].klen = klen;
    m->slots[idx].freq = freq;
}

static int fmap_get(const fmap_t *m, const char *key, uint32_t klen) {
    uint32_t h = cdb_hash(key, klen);
    uint32_t idx = h & (m->cap - 1);
    while (m->slots[idx].key != NULL) {
        if (m->slots[idx].klen == klen && memcmp(m->slots[idx].key, key, klen) == 0)
            return m->slots[idx].freq;
        idx = (idx + 1) & (m->cap - 1);
    }
    return 0;
}

static void fmap_destroy(fmap_t *m) {
    free(m->slots);
    m->slots = NULL;
    m->cap = 0;
}

/* Compute frequency for a TSV key (may be single word or phrase).
   For phrases, returns minimum word frequency (0 if any word unknown). */
static int key_frequency(const fmap_t *fm, const char *key, size_t klen) {
    /* Check for spaces (phrase) */
    int has_space = 0;
    for (size_t i = 0; i < klen; i++) {
        if (key[i] == ' ') { has_space = 1; break; }
    }

    if (!has_space) {
        return fmap_get(fm, key, (uint32_t)klen);
    }

    /* Phrase: min frequency of component words */
    int min_freq = 0x7FFFFFFF;
    const char *start = key;
    for (size_t i = 0; i <= klen; i++) {
        if (i == klen || key[i] == ' ') {
            size_t wlen = (key + i) - start;
            if (wlen > 0) {
                int wf = fmap_get(fm, start, (uint32_t)wlen);
                if (wf < min_freq) min_freq = wf;
            }
            start = key + i + 1;
        }
    }
    return (min_freq == 0x7FFFFFFF) ? 0 : min_freq;
}

/* TSV entry for sorting by frequency */
typedef struct {
    const char *key;
    size_t klen;
    const char *val;
    size_t vlen;
    int freq;
} tsv_entry_t;

static int tsv_cmp_freq_desc(const void *a, const void *b) {
    int fa = ((const tsv_entry_t *)a)->freq;
    int fb = ((const tsv_entry_t *)b)->freq;
    if (fb != fa) return (fb > fa) ? 1 : -1;
    return 0;
}

/* Load file into malloc'd buffer, returns size. Caller must free. */
static char *load_file(const char *path, long *out_size) {
    FILE *f = fopen(path, "r");
    if (!f) return NULL;
    fseek(f, 0, SEEK_END);
    long sz = ftell(f);
    fseek(f, 0, SEEK_SET);
    char *buf = (char *)malloc(sz + 1);
    if (!buf) { fclose(f); return NULL; }
    long rd = fread(buf, 1, sz, f);
    buf[rd] = '\0';
    fclose(f);
    *out_size = rd;
    return buf;
}

/*
 * static native int nativeBuildCdbFromTsv(String tsvPath, String freqPath,
 *                                          String cdbPath, int maxEntries);
 * Reads TSV, sorts entries by word frequency (from freqPath), builds trimmed CDB.
 * Returns number of entries written, or -1 on error.
 */
JNIEXPORT jint JNICALL
Java_com_ai10_k12kb_prediction_NativeTranslationDictionary_nativeBuildCdbFromTsv(
        JNIEnv *env, jclass clazz, jstring jtsvPath, jstring jfreqPath,
        jstring jcdbPath, jint maxEntries) {
    const char *tsvPath = jstr_get(env, jtsvPath);
    const char *freqPath = jstr_get(env, jfreqPath);
    const char *cdbPath = jstr_get(env, jcdbPath);
    if (!tsvPath || !cdbPath) {
        jstr_release(env, jtsvPath, tsvPath);
        jstr_release(env, jfreqPath, freqPath);
        jstr_release(env, jcdbPath, cdbPath);
        return -1;
    }

    /* 1. Load frequency dictionary into hash map */
    fmap_t fm;
    memset(&fm, 0, sizeof(fm));
    char *freq_buf = NULL;
    long freq_size = 0;

    if (freqPath) {
        freq_buf = load_file(freqPath, &freq_size);
        if (freq_buf) {
            /* Count lines for capacity estimate */
            uint32_t line_count = 0;
            for (long i = 0; i < freq_size; i++) {
                if (freq_buf[i] == '\n') line_count++;
            }
            fmap_init(&fm, line_count + 1);

            /* Parse: word\tfreq\n — null-terminate word in-place */
            char *p = freq_buf;
            char *end = freq_buf + freq_size;
            while (p < end) {
                char *line_start = p;
                /* Find end of line */
                while (p < end && *p != '\n' && *p != '\r') p++;
                char *line_end = p;
                while (p < end && (*p == '\n' || *p == '\r')) p++;

                /* Find tab */
                char *tab = NULL;
                for (char *t = line_start; t < line_end; t++) {
                    if (*t == '\t') { tab = t; break; }
                }
                if (!tab || tab == line_start) continue;

                uint32_t klen = (uint32_t)(tab - line_start);
                int freq = atoi(tab + 1);
                if (freq <= 0) continue;

                fmap_put(&fm, line_start, klen, freq);
            }
            LOGI("Loaded freq map: %u capacity from %s", fm.cap, freqPath);
        } else {
            LOGW("No freq dict at %s, will use file order", freqPath);
        }
    }

    /* 2. Load all TSV entries into array */
    long tsv_size = 0;
    char *tsv_buf = load_file(tsvPath, &tsv_size);
    if (!tsv_buf) {
        LOGE("Cannot load TSV: %s", tsvPath);
        if (freq_buf) { fmap_destroy(&fm); free(freq_buf); }
        jstr_release(env, jtsvPath, tsvPath);
        jstr_release(env, jfreqPath, freqPath);
        jstr_release(env, jcdbPath, cdbPath);
        return -1;
    }

    /* Count lines for array alloc */
    uint32_t tsv_lines = 0;
    for (long i = 0; i < tsv_size; i++) {
        if (tsv_buf[i] == '\n') tsv_lines++;
    }

    tsv_entry_t *entries = (tsv_entry_t *)malloc((tsv_lines + 1) * sizeof(tsv_entry_t));
    if (!entries) {
        free(tsv_buf);
        if (freq_buf) { fmap_destroy(&fm); free(freq_buf); }
        jstr_release(env, jtsvPath, tsvPath);
        jstr_release(env, jfreqPath, freqPath);
        jstr_release(env, jcdbPath, cdbPath);
        return -1;
    }

    uint32_t entry_count = 0;
    char *tp = tsv_buf;
    char *tend = tsv_buf + tsv_size;
    while (tp < tend) {
        char *line_start = tp;
        while (tp < tend && *tp != '\n' && *tp != '\r') tp++;
        size_t line_len = tp - line_start;
        while (tp < tend && (*tp == '\n' || *tp == '\r')) tp++;

        /* Find tab */
        char *tab = NULL;
        for (size_t i = 0; i < line_len; i++) {
            if (line_start[i] == '\t') { tab = line_start + i; break; }
        }
        if (!tab || tab == line_start) continue;

        size_t klen = tab - line_start;
        char *val = tab + 1;
        size_t vlen = line_len - klen - 1;
        if (vlen == 0) continue;

        entries[entry_count].key = line_start;
        entries[entry_count].klen = klen;
        entries[entry_count].val = val;
        entries[entry_count].vlen = vlen;
        entries[entry_count].freq = (fm.slots != NULL)
            ? key_frequency(&fm, line_start, klen) : 0;
        entry_count++;
    }

    /* 3. Sort by frequency descending (most-used words first) */
    if (fm.slots != NULL) {
        qsort(entries, entry_count, sizeof(tsv_entry_t), tsv_cmp_freq_desc);
    }

    /* 4. Build CDB from top maxEntries */
    cdb_make_t cm;
    if (cdb_make_start(&cm, cdbPath) != 0) {
        LOGE("Cannot create CDB: %s", cdbPath);
        free(entries);
        free(tsv_buf);
        if (freq_buf) { fmap_destroy(&fm); free(freq_buf); }
        jstr_release(env, jtsvPath, tsvPath);
        jstr_release(env, jfreqPath, freqPath);
        jstr_release(env, jcdbPath, cdbPath);
        return -1;
    }

    uint32_t limit = (maxEntries > 0 && (uint32_t)maxEntries < entry_count)
        ? (uint32_t)maxEntries : entry_count;
    for (uint32_t i = 0; i < limit; i++) {
        cdb_make_add(&cm, entries[i].key, entries[i].klen,
                     entries[i].val, entries[i].vlen);
    }

    int result = (int)limit;
    if (cdb_make_finish(&cm) != 0) {
        LOGE("Failed to finalize CDB: %s", cdbPath);
        result = -1;
    } else {
        LOGI("Built trimmed CDB: %s (%u/%u entries, freq-sorted)", cdbPath, limit, entry_count);
    }

    /* 5. Cleanup */
    free(entries);
    free(tsv_buf);
    if (freq_buf) { fmap_destroy(&fm); free(freq_buf); }
    jstr_release(env, jtsvPath, tsvPath);
    jstr_release(env, jfreqPath, freqPath);
    jstr_release(env, jcdbPath, cdbPath);
    return result;
}
