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
