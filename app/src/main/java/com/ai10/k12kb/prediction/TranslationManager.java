package com.ai10.k12kb.prediction;

import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * Manages translation state: on/off, source/target languages,
 * dictionary loading, and word lookup.
 */
public class TranslationManager {
    private static final String TAG = "TranslationMgr";

    private boolean enabled = false;
    private String sourceLang = "ru";
    private String targetLang = "en";
    private TranslationDictionary dictionary;
    private Context context;
    private boolean loading = false;

    public TranslationManager(Context context) {
        this.context = context.getApplicationContext();
        this.dictionary = new TranslationDictionary();
    }

    /**
     * Toggle translation mode on/off.
     * Returns the new state.
     */
    public synchronized boolean toggle() {
        enabled = !enabled;
        if (enabled && !dictionary.isLoaded()) {
            loadDictionary();
        }
        Log.i(TAG, "Translation mode " + (enabled ? "ON" : "OFF") +
                " (" + sourceLang + " -> " + targetLang + ")");
        return enabled;
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled && !dictionary.isLoaded()) {
            loadDictionary();
        }
    }

    /**
     * Update languages based on current and next keyboard layout.
     * Called when user switches layout.
     */
    public synchronized void updateLanguages(String currentLayoutLang, String nextLayoutLang) {
        if (currentLayoutLang.equals(sourceLang) && nextLayoutLang.equals(targetLang)) {
            return; // no change
        }
        this.sourceLang = currentLayoutLang;
        this.targetLang = nextLayoutLang;
        Log.i(TAG, "Languages updated: " + sourceLang + " -> " + targetLang);
        if (enabled) {
            loadDictionary();
        }
    }

    /**
     * Translate a word with context of previous word. Returns translations or empty list.
     * Tries phrase lookup first ("previousWord currentWord"), then single word.
     */
    public synchronized List<String> translate(String word, String previousWord) {
        if (!enabled || !dictionary.isLoaded()) {
            return java.util.Collections.emptyList();
        }
        return dictionary.translate(word, previousWord);
    }

    /**
     * Translate a word without context. Returns translations or empty list.
     */
    public synchronized List<String> translate(String word) {
        return translate(word, null);
    }

    /**
     * Check if a dictionary is available for the current language pair.
     */
    public synchronized boolean hasDictionary() {
        return dictionary.isLoaded() &&
                sourceLang.equals(dictionary.getSourceLang()) &&
                targetLang.equals(dictionary.getTargetLang());
    }

    public String getSourceLang() { return sourceLang; }
    public String getTargetLang() { return targetLang; }
    public int getDictionarySize() { return dictionary.size(); }

    private void loadDictionary() {
        if (loading) return;
        loading = true;
        final String from = sourceLang;
        final String to = targetLang;
        new Thread(new Runnable() {
            public void run() {
                try {
                    dictionary.load(context, from, to);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load dictionary: " + e);
                } finally {
                    loading = false;
                }
            }
        }, "TranslationDictLoader").start();
    }
}
