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
        /**
         * @param prefix the word the suggestions were computed for — lets a listener
         *               that hops threads drop results the user has already typed past.
         */
        void onSuggestionsUpdated(List<Suggestion> suggestions, String prefix);
    }

    /** Fired on the loading thread once a dictionary becomes usable. */
    public interface DictionaryLoadedListener {
        void onDictionaryLoaded(String locale);
    }

    // Static — survives across WordPredictor instances (IME restarts)
    private static PredictionEngine sharedEngine;
    private static int sharedEngineMode = -1;
    private static int sharedEngineDictSize = -1;
    // Static thread tracking — loading threads keep running across IME restarts
    private static final List<Thread> loadingThreads = new ArrayList<>();
    private static final HashSet<String> loadingLocales = new HashSet<>();
    /** Callbacks queued behind an in-flight load, keyed by locale. Guarded by loadingLocales. */
    private static final java.util.HashMap<String, List<Runnable>> pendingCallbacks = new java.util.HashMap<>();
    private PredictionEngine engine;
    private int engineMode = ENGINE_NATIVE_SYMSPELL;
    private SuggestionListener listener;
    private DictionaryLoadedListener dictionaryLoadedListener;
    private String currentWord = "";
    private String previousWord = "";
    private int suggestLimit = 4;
    private List<Suggestion> latestSuggestions = Collections.emptyList();
    private boolean enabled = true;
    private int dictSize = 35000;
    private boolean nextWordEnabled = true;
    private boolean keyboardAwareEnabled = true;
    private Context appContext;
    private String currentLocale = "";
    public WordPredictor() {
    }

    /**
     * Release instance references. Call from onDestroy().
     * Loading threads keep running in background — they write into
     * static sharedEngine which survives across IME restarts.
     */
    public void shutdown() {
        engine = null;
        listener = null;
        // The engine is static and outlives this instance; clearing the listener
        // here makes any late ready-callback a no-op for a dead IME instance.
        dictionaryLoadedListener = null;
    }

    public void setDictionaryLoadedListener(DictionaryLoadedListener listener) {
        this.dictionaryLoadedListener = listener;
    }

    /**
     * Adopt an engine and subscribe to its ready callback, so suggestions that were
     * dropped while the dictionary was loading get recomputed once it lands.
     */
    private void adoptEngine(PredictionEngine e) {
        engine = e;
        if (e == null) return;
        e.setReadyListener(new PredictionEngine.ReadyListener() {
            public void onEngineReady(String locale) {
                DictionaryLoadedListener l = dictionaryLoadedListener;
                if (l != null) l.onDictionaryLoaded(locale);
            }
        });
    }

    public void setDictSize(int size) {
        this.dictSize = size;
    }

    public void setNextWordEnabled(boolean enabled) {
        this.nextWordEnabled = enabled;
        if (engine instanceof NativeSymSpellEngine) {
            ((NativeSymSpellEngine) engine).setNextWordEnabled(enabled);
        }
    }

    public void setKeyboardAwareEnabled(boolean enabled) {
        this.keyboardAwareEnabled = enabled;
        if (engine instanceof NativeSymSpellEngine) {
            ((NativeSymSpellEngine) engine).setKeyboardAwareEnabled(enabled);
        }
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
        mode = ENGINE_NATIVE_SYMSPELL;
        boolean modeChanged = (this.engineMode != mode);
        this.engineMode = mode;
        if (modeChanged) {
            synchronized (loadingLocales) { loadingLocales.clear(); }
        }
        if (engine == null && sharedEngine != null && sharedEngineMode == mode) {
            adoptEngine(sharedEngine);
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
            adoptEngine(sharedEngine);
        }

        // Check if engine already loaded for this locale with matching dict size
        if (engine != null && engine.isReady() && locale.equals(engine.getLoadedLocale())) {
            if (dictSize == sharedEngineDictSize) {
                if (onComplete != null) onComplete.run();
                return;
            }
            // Dict size changed — invalidate static engine, force fresh load
            Log.d(TAG, "Dict size changed (" + sharedEngineDictSize + " -> " + dictSize + "), reloading");
            engine = null;
            sharedEngine = null;
            sharedEngineMode = -1;
            sharedEngineDictSize = -1;
        }

        // Already being loaded by a background thread — queue the callback behind it
        // rather than dropping it, so whoever asked still gets told when it lands.
        synchronized (loadingLocales) {
            if (loadingLocales.contains(locale)) {
                if (onComplete != null) {
                    List<Runnable> queued = pendingCallbacks.get(locale);
                    if (queued == null) {
                        queued = new ArrayList<>();
                        pendingCallbacks.put(locale, queued);
                    }
                    queued.add(onComplete);
                }
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
     * Забыть загруженные словари: список языковых пакетов изменился, и словарь
     * текущего языка мог приехать из только что установленного пакета.
     */
    public void dropLoadedDictionaries() {
        PredictionEngine e = engine;
        engine = null;
        // Метка идущей загрузки осталась бы от прежнего движка, и следующий
        // запрос того же языка просто встал бы в очередь к нему - подсказки
        // не появились бы до переключения раскладки.
        synchronized (loadingLocales) {
            loadingLocales.clear();
            pendingCallbacks.clear();
        }
        synchronized (WordPredictor.class) {
            sharedEngine = null;
            sharedEngineMode = -1;
            sharedEngineDictSize = -1;
        }
        if (e != null) {
            try {
                e.closeAll();
            } catch (Throwable ex) {
                Log.w(TAG, "Освободить словари не удалось: " + ex);
            }
        }
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
                } catch (Throwable ex) {
                    Log.e(TAG, "loadDictionary(" + locale + ") failed: " + ex);
                } finally {
                    // Clear the in-flight marker before running callbacks, so a
                    // callback that triggers another load isn't silently skipped.
                    synchronized (loadingLocales) { loadingLocales.remove(locale); }
                    synchronized (loadingThreads) { loadingThreads.remove(Thread.currentThread()); }
                }
                runCallback(onComplete);
                runPendingCallbacks(locale);
            }
        });
        t.setPriority(Thread.MIN_PRIORITY);
        synchronized (loadingThreads) { loadingThreads.add(t); }
        t.start();
    }

    private static void runCallback(Runnable r) {
        if (r == null) return;
        try {
            r.run();
        } catch (Throwable ex) {
            Log.w(TAG, "load callback failed: " + ex);
        }
    }

    /** Run everything that was queued behind an in-flight load of this locale. */
    private static void runPendingCallbacks(String locale) {
        List<Runnable> queued;
        synchronized (loadingLocales) {
            queued = pendingCallbacks.remove(locale);
        }
        if (queued == null) return;
        for (Runnable r : queued) {
            runCallback(r);
        }
    }

    /**
     * Preload a dictionary for a locale without switching to it.
     * The dictionary stays in the engine's cache for fast switching later.
     */
    public void preloadDictionary(final Context context, final String locale) {
        // Restore engine from static cache if needed
        if (engine == null && sharedEngine != null && sharedEngineMode == engineMode) {
            adoptEngine(sharedEngine);
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
        NativeSymSpellEngine nativeEng = new NativeSymSpellEngine();
        nativeEng.setMaxWords(dictSize);
        nativeEng.setNextWordEnabled(nextWordEnabled);
        nativeEng.setKeyboardAwareEnabled(keyboardAwareEnabled);
        final PredictionEngine newEngine = nativeEng;
        adoptEngine(newEngine);
        sharedEngine = newEngine;
        sharedEngineMode = engineMode;
        sharedEngineDictSize = dictSize;

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
     * Reset tracker. Saves currentWord as previousWord,
     * then requests next-word prediction (engine.suggest with empty input).
     */
    public void reset() {
        if (currentWord.length() > 0) {
            previousWord = currentWord;
        }
        currentWord = "";
        updateSuggestions();
    }

    /**
     * Accept a suggestion - replace current word in input.
     */
    public String acceptSuggestion(int index) {
        if (index < 0 || index >= latestSuggestions.size()) return null;
        Suggestion s = latestSuggestions.get(index);
        String result = applyCasing(s.word, currentWord);
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
            adoptEngine(sharedEngine);
        }
        if (engine == null || !engine.isReady()) {
            // Dictionary still loading. Don't paint an empty bar — the ready
            // listener recomputes and repaints as soon as it lands.
            return;
        }

        final String prefix = currentWord;
        List<Suggestion> results = engine.suggest(prefix, previousWord, suggestLimit);
        latestSuggestions = results;
        SuggestionListener l = listener;
        if (l != null) {
            l.onSuggestionsUpdated(results, prefix);
        }
    }

    /**
     * Forget the tracked words. Unlike reset() this does not promote currentWord to
     * previousWord — used when a new input field is attached, where nothing about the
     * old field should survive.
     */
    public void clearTracking() {
        currentWord = "";
        previousWord = "";
        latestSuggestions = Collections.emptyList();
    }

    /** Recompute suggestions for the word already held, e.g. after a dictionary load. */
    public void refreshSuggestions() {
        if (!enabled) return;
        updateSuggestions();
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
