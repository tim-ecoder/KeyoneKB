package com.ai10.k12kb.prediction;

import android.content.Context;
import android.util.Log;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Word prediction coordinator. Tracks input, casing, listeners and delegates
 * suggestion generation to a pluggable PredictionEngine.
 */
public class WordPredictor {

    private static final String TAG = "WordPredictor";

    public static final int ENGINE_SYMSPELL = 0;
    public static final int ENGINE_NGRAM = 1;

    public static class Suggestion {
        public final String word;
        public final int distance;
        public final double score;

        public Suggestion(String word, int distance, double score) {
            this.word = word;
            this.distance = distance;
            this.score = score;
        }
    }

    public interface SuggestionListener {
        void onSuggestionsUpdated(List<Suggestion> suggestions);
    }

    private PredictionEngine engine;
    private int engineMode = ENGINE_SYMSPELL;
    private SuggestionListener listener;
    private String currentWord = "";
    private String previousWord = "";
    private int suggestLimit = 4;
    private List<Suggestion> latestSuggestions = Collections.emptyList();
    private boolean enabled = true;
    private Context appContext;
    private String currentLocale = "";
    private final List<Thread> loadingThreads = new ArrayList<>();
    private volatile boolean shuttingDown = false;

    public WordPredictor() {
    }

    /**
     * Stop all background loading threads. Call from onDestroy().
     * Uses Thread.stop() for immediate hard kill — threads die instantly
     * regardless of what they're doing (I/O, sleep, computation).
     */
    @SuppressWarnings("deprecation")
    public void shutdown() {
        shuttingDown = true;
        synchronized (loadingThreads) {
            for (Thread t : loadingThreads) {
                try {
                    t.interrupt();
                    t.stop();  // hard kill — deprecated but effective on Android 8.x
                } catch (Throwable ignored) {}
            }
            loadingThreads.clear();
        }
        engine = null;
        listener = null;
    }

    public void setSuggestLimit(int limit) {
        this.suggestLimit = Math.max(1, limit);
    }

    public void setListener(SuggestionListener listener) {
        this.listener = listener;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isShutDown() {
        return shuttingDown;
    }

    public boolean isEngineReady() {
        return engine != null && engine.isReady();
    }

    public List<Suggestion> getLatestSuggestions() {
        return latestSuggestions;
    }

    public void setEngineMode(int mode) {
        if (mode < ENGINE_SYMSPELL || mode > ENGINE_NGRAM) mode = ENGINE_SYMSPELL;
        if (this.engineMode == mode && engine != null) return;
        this.engineMode = mode;
        // Recreate engine if we already have a context and locale
        if (appContext != null && !currentLocale.isEmpty()) {
            createAndLoadEngine(appContext, currentLocale);
        }
    }

    public int getEngineMode() {
        return engineMode;
    }

    public void setPreviousWord(String word) {
        this.previousWord = (word != null) ? word : "";
    }

    public String getPreviousWord() {
        return previousWord;
    }

    /**
     * Load dictionary for a locale. Creates the appropriate engine based on engineMode
     * and loads in a background thread.
     */
    public void loadDictionary(final Context context, final String locale) {
        loadDictionary(context, locale, null);
    }

    /**
     * Load dictionary with a completion callback that runs after loading finishes.
     */
    public void loadDictionary(final Context context, final String locale, final Runnable onComplete) {
        if (shuttingDown) return;
        this.appContext = context;
        this.currentLocale = locale;

        // Check if engine already loaded for this locale
        if (engine != null && engine.isReady() && locale.equals(engine.getLoadedLocale())) {
            if (onComplete != null) onComplete.run();
            return;
        }

        // Reuse existing engine if possible — keeps cached dictionaries
        if (engine != null) {
            final PredictionEngine existingEngine = engine;
            final Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        existingEngine.loadDictionary(context, locale);
                        if (shuttingDown) return;
                        if (existingEngine.isReady()) {
                            if (currentWord.length() > 0 || previousWord.length() > 0) {
                                updateSuggestions();
                            }
                        }
                        if (!shuttingDown && onComplete != null) onComplete.run();
                    } finally {
                        synchronized (loadingThreads) { loadingThreads.remove(Thread.currentThread()); }
                    }
                }
            });
            t.setPriority(Thread.MIN_PRIORITY);
            synchronized (loadingThreads) { loadingThreads.add(t); }
            t.start();
            return;
        }

        createAndLoadEngine(context, locale, onComplete);
    }

    /**
     * Preload a dictionary for a locale without switching to it.
     * The dictionary stays in the engine's cache for fast switching later.
     */
    public void preloadDictionary(final Context context, final String locale) {
        if (shuttingDown || engine == null) return;
        final PredictionEngine existingEngine = engine;
        final Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    existingEngine.preloadDictionary(context, locale);
                } finally {
                    synchronized (loadingThreads) { loadingThreads.remove(Thread.currentThread()); }
                }
            }
        });
        t.setPriority(Thread.MIN_PRIORITY);
        synchronized (loadingThreads) { loadingThreads.add(t); }
        t.start();
    }

    private void createAndLoadEngine(final Context context, final String locale) {
        createAndLoadEngine(context, locale, null);
    }

    private void createAndLoadEngine(final Context context, final String locale, final Runnable onComplete) {
        if (shuttingDown) return;
        final PredictionEngine newEngine;
        switch (engineMode) {
            case ENGINE_NGRAM:
                newEngine = new NgramEngine();
                break;
            default:
                newEngine = new SymSpellEngine();
                break;
        }
        engine = newEngine;

        final Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    newEngine.loadDictionary(context, locale);
                    if (shuttingDown) return;
                    if (newEngine.isReady()) {
                        if (currentWord.length() > 0 || previousWord.length() > 0) {
                            updateSuggestions();
                        }
                    }
                    if (!shuttingDown && onComplete != null) onComplete.run();
                } finally {
                    synchronized (loadingThreads) { loadingThreads.remove(Thread.currentThread()); }
                }
            }
        });
        t.setPriority(Thread.MIN_PRIORITY);
        synchronized (loadingThreads) { loadingThreads.add(t); }
        t.start();
    }

    /**
     * Called when user types a character. Updates current word and generates suggestions.
     */
    public void onCharacterTyped(char c) {
        if (!enabled) return;
        // Normalize apostrophe variants
        if (c == '\u2018' || c == '\u2019' || c == '\u02BC') c = '\'';

        boolean isWordChar = Character.isLetterOrDigit(c)
                || (c == '\'' && currentWord.length() > 0
                && Character.isLetterOrDigit(currentWord.charAt(currentWord.length() - 1)));
        if (isWordChar) {
            if (currentWord.length() < 48) {
                currentWord += c;
                updateSuggestions();
            }
        } else {
            reset();
        }
    }

    /**
     * Called on backspace.
     */
    public void onBackspace() {
        if (!enabled) return;
        if (currentWord.length() > 0) {
            currentWord = currentWord.substring(0, currentWord.length() - 1);
            if (currentWord.length() > 0) {
                updateSuggestions();
            } else {
                // Word deleted back to empty — try next-word suggestions if we have previousWord
                updateSuggestions();
            }
        }
    }

    /**
     * Set the current word directly (e.g. when cursor moves).
     */
    public void setCurrentWord(String word) {
        if (!enabled) return;
        if (word == null) word = "";
        currentWord = word;
        updateSuggestions();
    }

    public String getCurrentWord() {
        return currentWord;
    }

    /**
     * Reset tracker and clear suggestions. Saves currentWord as previousWord.
     */
    public void reset() {
        if (currentWord.length() > 0) {
            previousWord = currentWord;
        }
        currentWord = "";
        latestSuggestions = Collections.emptyList();
        if (listener != null) {
            listener.onSuggestionsUpdated(latestSuggestions);
        }
    }

    /**
     * Accept a suggestion - replace current word in input.
     */
    public String acceptSuggestion(int index) {
        if (index < 0 || index >= latestSuggestions.size()) return null;
        Suggestion s = latestSuggestions.get(index);
        String result = applyCasing(s.word, currentWord);
        // Set previousWord to the accepted word (normalized form for bigram lookup)
        String acceptedWord = s.word;
        currentWord = "";
        previousWord = acceptedWord;
        latestSuggestions = Collections.emptyList();
        if (listener != null) {
            listener.onSuggestionsUpdated(latestSuggestions);
        }
        return result;
    }

    /**
     * Generate suggestions for the current word via the active engine.
     */
    private void updateSuggestions() {
        if (engine == null || !engine.isReady()) {
            return; // Dictionary not loaded yet — skip silently
        }

        List<Suggestion> results = engine.suggest(currentWord, previousWord, suggestLimit);
        latestSuggestions = results;
        if (listener != null) {
            listener.onSuggestionsUpdated(results);
        }
    }

    /**
     * Apply casing from original word to candidate.
     * ALL CAPS -> ALL CAPS, First Upper -> First Upper, else lowercase.
     */
    public static String applyCasing(String candidate, String original) {
        if (candidate == null || candidate.isEmpty()) return candidate;
        if (original == null || original.isEmpty()) return candidate;

        // Count case pattern
        int upperCount = 0;
        int letterCount = 0;
        for (int i = 0; i < original.length(); i++) {
            char c = original.charAt(i);
            if (Character.isLetter(c)) {
                letterCount++;
                if (Character.isUpperCase(c)) upperCount++;
            }
        }
        if (letterCount == 0) return candidate;

        boolean allUpper = letterCount > 1 && upperCount == letterCount;
        boolean allLower = upperCount == 0;
        boolean firstUpper = false;
        for (int i = 0; i < original.length(); i++) {
            if (Character.isLetter(original.charAt(i))) {
                firstUpper = Character.isUpperCase(original.charAt(i));
                break;
            }
        }
        boolean restLower = true;
        boolean foundFirst = false;
        for (int i = 0; i < original.length(); i++) {
            char c = original.charAt(i);
            if (Character.isLetter(c)) {
                if (foundFirst && Character.isUpperCase(c)) {
                    restLower = false;
                    break;
                }
                foundFirst = true;
            }
        }

        if (allUpper) {
            return candidate.toUpperCase(Locale.ROOT);
        } else if (firstUpper && restLower) {
            // Capitalize first letter
            for (int i = 0; i < candidate.length(); i++) {
                if (Character.isLetter(candidate.charAt(i))) {
                    return candidate.substring(0, i)
                            + Character.toUpperCase(candidate.charAt(i))
                            + candidate.substring(i + 1);
                }
            }
            return candidate;
        } else if (allLower) {
            return candidate.toLowerCase(Locale.ROOT);
        }
        return candidate;
    }
}
