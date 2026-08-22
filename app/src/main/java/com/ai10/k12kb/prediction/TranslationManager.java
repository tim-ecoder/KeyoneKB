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

    /**
     * A translation lookup plus the phrase-match state that belongs to it.
     * Reading those as three separate calls could tear if another lookup lands
     * in between, mislabelling which entries are bigram results.
     */
    public static final class Result {
        public final List<String> translations;
        public final boolean phraseMatch;
        public final int phraseResultCount;

        Result(List<String> translations, boolean phraseMatch, int phraseResultCount) {
            this.translations = translations;
            this.phraseMatch = phraseMatch;
            this.phraseResultCount = phraseResultCount;
        }

        public boolean isEmpty() {
            return translations.isEmpty();
        }
    }

    private static final Result EMPTY_RESULT =
            new Result(java.util.Collections.<String>emptyList(), false, 0);

    private boolean enabled = false;
    private String sourceLang = "ru";
    private String targetLang = "en";
    private NativeTranslationDictionary dictionary;
    private Context context;
    private boolean loading = false;
    /** Bumped whenever the wanted language pair changes; invalidates an in-flight load. */
    private int loadGeneration = 0;

    public TranslationManager(Context context) {
        this.context = context.getApplicationContext();
        this.dictionary = new NativeTranslationDictionary();
    }

    public void setMaxEntries(int max) {
        dictionary.setMaxEntries(max);
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
        Log.w(TAG, "Translation mode " + (enabled ? "ON" : "OFF") +
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
        // Invalidate old dictionary — direction changed. Bumping the generation
        // makes any in-flight loader reload for the new pair instead of finishing
        // last and leaving the old direction loaded.
        loadGeneration++;
        dictionary.invalidate();
        Log.w(TAG, "Languages updated: " + sourceLang + " -> " + targetLang);
        if (enabled) {
            loadDictionary();
        }
    }

    /**
     * Translate a word with context of previous word. Returns translations or empty list.
     * Tries phrase lookup first ("previousWord currentWord"), then single word.
     */
    public synchronized List<String> translate(String word, String previousWord) {
        return lookup(word, previousWord).translations;
    }

    /**
     * Translate a word with context, returning the translations and their
     * phrase-match state together as one consistent snapshot.
     */
    public synchronized Result lookup(String word, String previousWord) {
        if (!enabled || !dictionary.isLoaded()) {
            return EMPTY_RESULT;
        }
        List<String> translations = dictionary.translate(word, previousWord);
        if (translations.isEmpty()) {
            return EMPTY_RESULT;
        }
        return new Result(translations,
                dictionary.wasLastPhraseMatch(),
                dictionary.getLastPhraseResultCount());
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

    public synchronized boolean wasLastPhraseMatch() {
        return dictionary.wasLastPhraseMatch();
    }

    public synchronized int getLastPhraseResultCount() {
        return dictionary.getLastPhraseResultCount();
    }

    public String getSourceLang() { return sourceLang; }
    public String getTargetLang() { return targetLang; }
    public int getDictionarySize() { return dictionary.size(); }

    private Runnable onDictionaryLoaded;

    public void setOnDictionaryLoadedListener(Runnable listener) {
        this.onDictionaryLoaded = listener;
    }

    /** Caller must hold this manager's monitor. */
    private void loadDictionary() {
        if (loading) return;
        loading = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    // Reload until the pair we loaded is still the pair that is wanted,
                    // so a language switch mid-load can't leave the old direction active.
                    while (true) {
                        final int generation;
                        final String from;
                        final String to;
                        synchronized (TranslationManager.this) {
                            generation = loadGeneration;
                            from = sourceLang;
                            to = targetLang;
                        }
                        try {
                            dictionary.load(context, from, to);
                        } catch (Throwable e) {
                            Log.e(TAG, "Failed to load dictionary " + from + "->" + to + ": " + e);
                        }
                        synchronized (TranslationManager.this) {
                            if (generation == loadGeneration) break;
                        }
                    }
                } finally {
                    synchronized (TranslationManager.this) { loading = false; }
                }
                Runnable cb = onDictionaryLoaded;
                if (cb != null) {
                    try {
                        cb.run();
                    } catch (Throwable e) {
                        Log.w(TAG, "dictionary loaded callback failed: " + e);
                    }
                }
            }
        }, "TranslationDictLoader").start();
    }
}
