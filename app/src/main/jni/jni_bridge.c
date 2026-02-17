/*
 * JNI bridge for native SymSpell.
 * Bridges com.ai10.k12kb.prediction.NativeSymSpell <-> C symspell_t
 */

#include <jni.h>
#include <string.h>
#include <android/log.h>
#include "symspell.h"

#define LOG_TAG "NativeSymSpell"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* Helper: get UTF-8 C string from jstring, caller must release */
static const char *jstr_get(JNIEnv *env, jstring js) {
    if (!js) return NULL;
    return (*env)->GetStringUTFChars(env, js, NULL);
}

static void jstr_release(JNIEnv *env, jstring js, const char *cs) {
    if (js && cs) (*env)->ReleaseStringUTFChars(env, js, cs);
}

/* ---- JNI Methods ------------------------------------------------------- */

/*
 * native long nativeCreate(int maxEditDistance, int prefixLength);
 */
JNIEXPORT jlong JNICALL
Java_com_ai10_k12kb_prediction_NativeSymSpell_nativeCreate(
        JNIEnv *env, jobject thiz, jint maxEditDistance, jint prefixLength) {
    symspell_t *ss = ss_create(maxEditDistance, prefixLength);
    LOGI("Created native SymSpell: maxDist=%d prefixLen=%d ptr=%p",
         maxEditDistance, prefixLength, ss);
    return (jlong)(intptr_t)ss;
}

/*
 * static native long nativeLoadMmapStatic(String path);
 */
JNIEXPORT jlong JNICALL
Java_com_ai10_k12kb_prediction_NativeSymSpell_nativeLoadMmapStatic(
        JNIEnv *env, jclass clazz, jstring jpath) {
    const char *path = jstr_get(env, jpath);
    if (!path) return 0;

    symspell_t *ss = ss_load_mmap(path);
    if (ss) {
        LOGI("Loaded native SymSpell from mmap: %s (%d words)", path, ss_size(ss));
    } else {
        LOGW("Failed to load native SymSpell from: %s", path);
    }
    jstr_release(env, jpath, path);
    return (jlong)(intptr_t)ss;
}

/*
 * native void nativeAddWord(long ptr, String word, int frequency);
 */
JNIEXPORT void JNICALL
Java_com_ai10_k12kb_prediction_NativeSymSpell_nativeAddWord(
        JNIEnv *env, jobject thiz, jlong ptr, jstring jword, jint frequency) {
    symspell_t *ss = (symspell_t *)(intptr_t)ptr;
    if (!ss) return;
    const char *word = jstr_get(env, jword);
    if (!word) return;
    ss_add_word(ss, word, frequency);
    jstr_release(env, jword, word);
}

/*
 * native void nativeBuildIndex(long ptr);
 */
JNIEXPORT void JNICALL
Java_com_ai10_k12kb_prediction_NativeSymSpell_nativeBuildIndex(
        JNIEnv *env, jobject thiz, jlong ptr) {
    symspell_t *ss = (symspell_t *)(intptr_t)ptr;
    if (!ss) return;
    LOGI("Building native index for %d words...", ss_size(ss));
    ss_build_index(ss);
    LOGI("Index built.");
}

/*
 * native String[] nativeLookup(long ptr, String input, int maxSuggestions);
 * Returns array of triplets: [term, distance_str, frequency_str, term, ...]
 */
JNIEXPORT jobjectArray JNICALL
Java_com_ai10_k12kb_prediction_NativeSymSpell_nativeLookup(
        JNIEnv *env, jobject thiz, jlong ptr, jstring jinput, jint maxSuggestions) {
    symspell_t *ss = (symspell_t *)(intptr_t)ptr;
    if (!ss || !jinput) return NULL;

    const char *input = jstr_get(env, jinput);
    if (!input) return NULL;

    ss_suggest_item_t results[64];
    int cap = 64;
    if (maxSuggestions < cap) cap = maxSuggestions;

    int count = ss_lookup(ss, input, cap, results, 64);
    jstr_release(env, jinput, input);

    if (count <= 0) return NULL;

    /* Create String array: 3 entries per result (term, distance, frequency) */
    jclass strClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray arr = (*env)->NewObjectArray(env, count * 3, strClass, NULL);
    if (!arr) return NULL;

    char numbuf[16];
    for (int i = 0; i < count; i++) {
        jstring jterm = (*env)->NewStringUTF(env, results[i].term);
        (*env)->SetObjectArrayElement(env, arr, i * 3, jterm);

        snprintf(numbuf, sizeof(numbuf), "%d", results[i].distance);
        jstring jdist = (*env)->NewStringUTF(env, numbuf);
        (*env)->SetObjectArrayElement(env, arr, i * 3 + 1, jdist);

        snprintf(numbuf, sizeof(numbuf), "%d", results[i].frequency);
        jstring jfreq = (*env)->NewStringUTF(env, numbuf);
        (*env)->SetObjectArrayElement(env, arr, i * 3 + 2, jfreq);

        (*env)->DeleteLocalRef(env, jterm);
        (*env)->DeleteLocalRef(env, jdist);
        (*env)->DeleteLocalRef(env, jfreq);
    }

    return arr;
}

/*
 * native String[] nativeLookupWeighted(long ptr, String input,
 *                                       int maxSuggestions, String layout);
 * Returns: [term, weighted_distance_str, frequency_str, ...]
 */
JNIEXPORT jobjectArray JNICALL
Java_com_ai10_k12kb_prediction_NativeSymSpell_nativeLookupWeighted(
        JNIEnv *env, jobject thiz, jlong ptr, jstring jinput,
        jint maxSuggestions, jstring jlayout) {
    symspell_t *ss = (symspell_t *)(intptr_t)ptr;
    if (!ss || !jinput) return NULL;

    const char *input = jstr_get(env, jinput);
    if (!input) return NULL;
    const char *layout = jstr_get(env, jlayout);

    ss_suggest_item_t results[64];
    int cap = 64;
    if (maxSuggestions < cap) cap = maxSuggestions;

    int count = ss_lookup_weighted(ss, input, cap, results, 64,
                                    layout ? layout : "qwerty");
    jstr_release(env, jinput, input);
    jstr_release(env, jlayout, layout);

    if (count <= 0) return NULL;

    jclass strClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray arr = (*env)->NewObjectArray(env, count * 3, strClass, NULL);
    if (!arr) return NULL;

    char numbuf[32];
    for (int i = 0; i < count; i++) {
        jstring jterm = (*env)->NewStringUTF(env, results[i].term);
        (*env)->SetObjectArrayElement(env, arr, i * 3, jterm);

        /* Use weighted_distance if available, else integer distance */
        if (results[i].weighted_distance >= 0) {
            snprintf(numbuf, sizeof(numbuf), "%.2f", results[i].weighted_distance);
        } else {
            snprintf(numbuf, sizeof(numbuf), "%d", results[i].distance);
        }
        jstring jdist = (*env)->NewStringUTF(env, numbuf);
        (*env)->SetObjectArrayElement(env, arr, i * 3 + 1, jdist);

        snprintf(numbuf, sizeof(numbuf), "%d", results[i].frequency);
        jstring jfreq = (*env)->NewStringUTF(env, numbuf);
        (*env)->SetObjectArrayElement(env, arr, i * 3 + 2, jfreq);

        (*env)->DeleteLocalRef(env, jterm);
        (*env)->DeleteLocalRef(env, jdist);
        (*env)->DeleteLocalRef(env, jfreq);
    }

    return arr;
}

/*
 * native boolean nativeSave(long ptr, String path);
 */
JNIEXPORT jboolean JNICALL
Java_com_ai10_k12kb_prediction_NativeSymSpell_nativeSave(
        JNIEnv *env, jobject thiz, jlong ptr, jstring jpath) {
    symspell_t *ss = (symspell_t *)(intptr_t)ptr;
    if (!ss || !jpath) return JNI_FALSE;

    const char *path = jstr_get(env, jpath);
    if (!path) return JNI_FALSE;

    int result = ss_save(ss, path);
    if (result == 0) {
        LOGI("Saved native cache: %s (%d words)", path, ss_size(ss));
    } else {
        LOGE("Failed to save native cache: %s", path);
    }
    jstr_release(env, jpath, path);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

/*
 * native void nativeDestroy(long ptr);
 */
JNIEXPORT void JNICALL
Java_com_ai10_k12kb_prediction_NativeSymSpell_nativeDestroy(
        JNIEnv *env, jobject thiz, jlong ptr) {
    symspell_t *ss = (symspell_t *)(intptr_t)ptr;
    if (!ss) return;
    LOGI("Destroying native SymSpell: %d words", ss_size(ss));
    ss_destroy(ss);
}

/*
 * native int nativeSize(long ptr);
 */
JNIEXPORT jint JNICALL
Java_com_ai10_k12kb_prediction_NativeSymSpell_nativeSize(
        JNIEnv *env, jobject thiz, jlong ptr) {
    symspell_t *ss = (symspell_t *)(intptr_t)ptr;
    return ss ? ss_size(ss) : 0;
}

/*
 * native int nativeGetFrequency(long ptr, String word);
 */
JNIEXPORT jint JNICALL
Java_com_ai10_k12kb_prediction_NativeSymSpell_nativeGetFrequency(
        JNIEnv *env, jobject thiz, jlong ptr, jstring jword) {
    symspell_t *ss = (symspell_t *)(intptr_t)ptr;
    if (!ss || !jword) return 0;
    const char *word = jstr_get(env, jword);
    if (!word) return 0;
    int freq = ss_get_frequency(ss, word);
    jstr_release(env, jword, word);
    return freq;
}

/*
 * native boolean nativeContains(long ptr, String word);
 */
JNIEXPORT jboolean JNICALL
Java_com_ai10_k12kb_prediction_NativeSymSpell_nativeContains(
        JNIEnv *env, jobject thiz, jlong ptr, jstring jword) {
    symspell_t *ss = (symspell_t *)(intptr_t)ptr;
    if (!ss || !jword) return JNI_FALSE;
    const char *word = jstr_get(env, jword);
    if (!word) return JNI_FALSE;
    int found = ss_contains(ss, word);
    jstr_release(env, jword, word);
    return found ? JNI_TRUE : JNI_FALSE;
}
