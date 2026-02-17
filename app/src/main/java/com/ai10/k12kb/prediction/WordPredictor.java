package com.ai10.k12kb.prediction;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    public static final int ENGINE_NATIVE_SYMSPELL = 2;

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

    // Static — survives across WordPredictor instances (IME restarts)
    private static PredictionEngine sharedEngine;
    private static int sharedEngineMode = -1;
    // Static thread tracking — loading threads keep running across IME restarts
    private static final List<Thread> loadingThreads = new ArrayList<>();
    private static final HashSet<String> loadingLocales = new HashSet<>();
    // Static learned dictionary — persists across IME restarts
    private static LearnedDictionary sharedLearnedDict;

    private PredictionEngine engine;
    private int engineMode = ENGINE_NATIVE_SYMSPELL;
    private SuggestionListener listener;
    private String currentWord = "";
    private String previousWord = "";
    private int suggestLimit = 4;
    private List<Suggestion> latestSuggestions = Collections.emptyList();
    private boolean enabled = true;
    private boolean learningEnabled = true;
    private Context appContext;
    private String currentLocale = "";
    private int unsavedLearnCount = 0;
    private static final int SAVE_INTERVAL = 10; // save every N learned words

    public WordPredictor() {
    }

    /**
     * Release instance references. Call from onDestroy().
     * Loading threads keep running in background — they write into
     * static sharedEngine which survives across IME restarts.
     * Saves learned words before shutting down.
     */
    public void shutdown() {
        saveLearnedWords();
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

    public void setLearningEnabled(boolean enabled) {
        this.learningEnabled = enabled;
    }

    public boolean isLearningEnabled() {
        return learningEnabled;
    }

    /**
     * Initialize the learned dictionary. Call once from IME onCreate.
     * Loads persisted learned words from file.
     */
    public void initLearnedDictionary(Context context) {
        this.appContext = context;
        if (sharedLearnedDict == null) {
            sharedLearnedDict = new LearnedDictionary();
            sharedLearnedDict.load(context);
            Log.d(TAG, "Loaded learned dictionary: " + sharedLearnedDict.size() + " words");
        }
    }

    /**
     * Get the learned dictionary (for status display in settings).
     */
    public static LearnedDictionary getLearnedDictionary() {
        return sharedLearnedDict;
    }

    /**
     * Learn a completed word. Called when user finishes typing a word
     * (space, punctuation, or suggestion acceptance).
     */
    public void learnWord(String word) {
        if (!learningEnabled || word == null || word.isEmpty()) return;
        if (sharedLearnedDict == null) return;
        sharedLearnedDict.learnWord(word);
        unsavedLearnCount++;
        if (unsavedLearnCount >= SAVE_INTERVAL && appContext != null) {
            unsavedLearnCount = 0;
            // Save in background to avoid blocking UI
            final Context ctx = appContext;
            new Thread(new Runnable() {
                public void run() {
                    sharedLearnedDict.save(ctx);
                }
            }).start();
        }
    }

    /**
     * Force save learned words to file.
     */
    public void saveLearnedWords() {
        if (sharedLearnedDict != null && appContext != null) {
            sharedLearnedDict.save(appContext);
            unsavedLearnCount = 0;
        }
    }

    /**
     * Clear all learned words.
     */
    public void clearLearnedWords() {
        if (sharedLearnedDict != null && appContext != null) {
            sharedLearnedDict.clear(appContext);
        }
    }

    public boolean isEngineReady() {
        if (engine != null && engine.isReady()) return true;
        // Check static engine — may have data from previous instance
        if (sharedEngine != null && sharedEngineMode == engineMode && sharedEngine.isReady()) return true;
        return false;
    }

    public List<Suggestion> getLatestSuggestions() {
        return latestSuggestions;
    }

    public void setEngineMode(int mode) {
        if (mode < ENGINE_SYMSPELL || mode > ENGINE_NATIVE_SYMSPELL) mode = ENGINE_SYMSPELL;
        // Fall back to Java SymSpell if native library not available
        if (mode == ENGINE_NATIVE_SYMSPELL && !NativeSymSpell.isAvailable()) mode = ENGINE_SYMSPELL;
        boolean modeChanged = (this.engineMode != mode);
        this.engineMode = mode;
        if (modeChanged) {
            // Engine type changed — old loading threads are for wrong engine type,
            // allow new loads for the new engine
            synchronized (loadingLocales) { loadingLocales.clear(); }
        }
        if (engine == null && sharedEngine != null && sharedEngineMode == mode) {
            engine = sharedEngine;
        }
        if (engine != null) return;
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
        this.appContext = context;
        this.currentLocale = locale;

        // Restore engine from static cache if available and mode matches
        if (engine == null && sharedEngine != null && sharedEngineMode == engineMode) {
            engine = sharedEngine;
        }

        // Check if engine already loaded for this locale
        if (engine != null && engine.isReady() && locale.equals(engine.getLoadedLocale())) {
            if (onComplete != null) onComplete.run();
            return;
        }

        // Skip if this locale is already being loaded by a background thread
        synchronized (loadingLocales) {
            if (loadingLocales.contains(locale)) {
                return;
            }
        }

        // Reuse existing engine if possible — keeps cached dictionaries
        if (engine != null) {
            spawnLoadThread(context, locale, engine, onComplete);
            return;
        }

        createAndLoadEngine(context, locale, onComplete);
    }

    /**
     * Spawn a thread to load a locale into an existing engine.
     */
    private void spawnLoadThread(final Context context, final String locale,
                                  final PredictionEngine targetEngine, final Runnable onComplete) {
        synchronized (loadingLocales) { loadingLocales.add(locale); }
        final Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    targetEngine.loadDictionary(context, locale);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                } finally {
                    synchronized (loadingLocales) { loadingLocales.remove(locale); }
                    synchronized (loadingThreads) { loadingThreads.remove(Thread.currentThread()); }
                }
            }
        });
        t.setPriority(Thread.MIN_PRIORITY);
        synchronized (loadingThreads) { loadingThreads.add(t); }
        t.start();
    }

    /**
     * Preload a dictionary for a locale without switching to it.
     * The dictionary stays in the engine's cache for fast switching later.
     */
    public void preloadDictionary(final Context context, final String locale) {
        // Restore engine from static cache if needed
        if (engine == null && sharedEngine != null && sharedEngineMode == engineMode) {
            engine = sharedEngine;
        }
        if (engine == null) return;

        // Skip if this locale is already being loaded
        synchronized (loadingLocales) {
            if (loadingLocales.contains(locale)) {
                return;
            }
            loadingLocales.add(locale);
        }
        final PredictionEngine existingEngine = engine;
        final Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    existingEngine.preloadDictionary(context, locale);
                } finally {
                    synchronized (loadingLocales) { loadingLocales.remove(locale); }
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
        final PredictionEngine newEngine;
        switch (engineMode) {
            case ENGINE_NGRAM:
                NgramEngine ngramEng = new NgramEngine();
                ngramEng.setLearnedDictionary(sharedLearnedDict);
                newEngine = ngramEng;
                break;
            case ENGINE_NATIVE_SYMSPELL:
                NativeSymSpellEngine nativeEng = new NativeSymSpellEngine();
                nativeEng.setLearnedDictionary(sharedLearnedDict);
                newEngine = nativeEng;
                break;
            default:
                SymSpellEngine symEng = new SymSpellEngine();
                symEng.setLearnedDictionary(sharedLearnedDict);
                newEngine = symEng;
                break;
        }
        engine = newEngine;
        sharedEngine = newEngine;
        sharedEngineMode = engineMode;

        // Use spawnLoadThread which tracks loadingLocales
        spawnLoadThread(context, locale, newEngine, onComplete);
    }

    /**
     * Called when user types a character. Updates current word and generates suggestions.
     */
    public void onCharacterTyped(char c) {
        if (!enabled) return;
        // Normalize apostrophe variants
        if (c == '\u2018' || c == '\u2019' || c == '\u02BC') c = '\'';

        if (WordDictionary.isWordChar(c)) {
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
     * Reset tracker. Saves currentWord as previousWord, learns the word,
     * then requests next-word prediction (engine.suggest with empty input).
     */
    public void reset() {
        if (currentWord.length() > 0) {
            // Learn the completed word
            learnWord(currentWord);
            previousWord = currentWord;
        }
        currentWord = "";
        updateSuggestions();
    }

    /**
     * Accept a suggestion - replace current word in input.
     * Also learns the accepted word.
     */
    public String acceptSuggestion(int index) {
        if (index < 0 || index >= latestSuggestions.size()) return null;
        Suggestion s = latestSuggestions.get(index);
        String result = applyCasing(s.word, currentWord);
        // Learn the accepted word
        learnWord(s.word);
        // Set previousWord to the accepted word (normalized form for bigram lookup)
        previousWord = s.word;
        currentWord = "";
        updateSuggestions();
        return result;
    }

    /**
     * Generate suggestions for the current word via the active engine.
     */
    private void updateSuggestions() {
        // Restore engine from static cache if instance was cleared by shutdown
        if (engine == null && sharedEngine != null && sharedEngineMode == engineMode) {
            engine = sharedEngine;
        }
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
