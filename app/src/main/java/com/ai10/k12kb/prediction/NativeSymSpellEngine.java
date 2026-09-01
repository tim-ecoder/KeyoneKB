package com.ai10.k12kb.prediction;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Native SymSpell prediction engine.
 *
 * Delegates fuzzy matching AND prefix lookups to the C library
 * (libnativesymspell.so). All word indexes live in native memory —
 * no Java-side HashMaps needed. On cache hit, loading is just mmap + ready.
 */
public class NativeSymSpellEngine implements PredictionEngine {

    private static final String TAG = "NativeSymSpellEngine";
    private static final int DEFAULT_MAX_WORDS = 35000;
    private static final String CACHE_DIR = "native_dict_cache";
    private int maxWords = DEFAULT_MAX_WORDS;

    /**
     * Immutable (dictionary, locale) pair. Publishing a whole snapshot with one
     * volatile write keeps the two in sync — separate volatile fields could be
     * read torn, so a lookup could run against another locale's dictionary.
     */
    private static final class Snapshot {
        final NativeSymSpell ss;
        final String locale;

        Snapshot(NativeSymSpell ss, String locale) {
            this.ss = ss;
            this.locale = locale;
        }
    }

    /** The dictionary lookups run against. Null means "nothing loaded yet". */
    private volatile Snapshot active;
    /**
     * Dictionaries already built, keyed by locale, most recently used last.
     *
     * The engine used to hold exactly one: every language switch rebuilt the
     * incoming dictionary from its cache file and destroyed the outgoing one
     * (130-700 ms on a KEY2). Until that finished, lookups were answered by the
     * previous language's dictionary — that is, no suggestions at all for what
     * the user was typing. With Ctrl bound to language switch that window opens
     * constantly, which is what made the bar look like it worked only sometimes.
     * Keeping the built dictionaries makes a switch a pointer swap.
     *
     * Guarded by {@link #loadLock}.
     */
    private final LinkedHashMap<String, NativeSymSpell> loaded =
            new LinkedHashMap<>(4, 0.75f, true);
    /** How many dictionaries to keep alive. Two languages plus room to switch. */
    private static final int MAX_CACHED = 3;
    /** Serialises loads so two threads never build the same locale at once. */
    private final Object loadLock = new Object();
    /**
     * Guards native calls against the destroy() that follows a swap: taking the
     * write lock waits for every in-flight suggest() to finish, so the freed
     * instance can no longer be reached through a stale snapshot.
     */
    private final ReentrantReadWriteLock accessLock = new ReentrantReadWriteLock();
    private volatile ReadyListener readyListener;
    private String keyboardLayout = "qwerty";
    private boolean nextWordEnabled = true;
    private boolean keyboardAwareEnabled = true;

    @Override
    public void setReadyListener(ReadyListener listener) {
        this.readyListener = listener;
    }

    @Override
    public List<WordPredictor.Suggestion> suggest(String input, String previousWord, int limit) {
        accessLock.readLock().lock();
        try {
            Snapshot snap = active;
            if (snap == null) return Collections.emptyList();
            return suggestFrom(snap.ss, input, previousWord, limit);
        } finally {
            accessLock.readLock().unlock();
        }
    }

    private List<WordPredictor.Suggestion> suggestFrom(NativeSymSpell ss, String input,
                                                       String previousWord, int limit) {
        String normalizedPrev = (previousWord != null && !previousWord.isEmpty())
                ? WordDictionary.normalize(previousWord) : null;

        // Next-word prediction: empty input with a previous word
        if (input == null || input.isEmpty()) {
            if (!nextWordEnabled) return Collections.emptyList();
            if (normalizedPrev == null || normalizedPrev.isEmpty()) return Collections.emptyList();
            return bigramNextWord(ss, normalizedPrev, limit);
        }

        String normalized = WordDictionary.normalize(input);
        if (normalized.isEmpty()) return Collections.emptyList();

        HashSet<String> seen = new HashSet<>();
        List<WordPredictor.Suggestion> top = new ArrayList<>();

        // 1. Prefix completions (from native prefix lookup)
        NativeSymSpell.SuggestItem[] completions = ss.prefixLookup(normalized, 100);
        for (NativeSymSpell.SuggestItem item : completions) {
            String word = item.original;
            String normEntry = WordDictionary.normalize(word);
            if (!normEntry.startsWith(normalized)) continue;
            if (word.length() <= input.length()) continue;
            if (word.equalsIgnoreCase(input)) continue;

            int effFreq = WordDictionary.effectiveFrequency(item.frequency);
            int minFreq = normalized.length() <= 2 ? 300 : (normalized.length() == 3 ? 250 : 150);
            if (effFreq < minFreq) continue;

            double score = computeScore(normalized, normEntry, word, item.frequency, 0, true, input.length());
            // Bigram boost for completions
            if (nextWordEnabled && normalizedPrev != null) {
                int bgFreq = ss.getBigramFrequency(normalizedPrev, normEntry);
                if (bgFreq > 0) {
                    score += bgFreq / 100.0;
                }
            }
            String key = word.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                insertSorted(top, new WordPredictor.Suggestion(word, 0, score), limit);
            }
        }

        // 2. Native SymSpell fuzzy matches (skip for single char, skip entirely if disabled)
        if (keyboardAwareEnabled && normalized.length() > 1) {
            int symLimit = normalized.length() <= 3 ? limit * 2 : limit * 4;
            NativeSymSpell.SuggestItem[] nativeResults =
                    ss.lookupWeighted(normalized, symLimit, keyboardLayout);

            for (NativeSymSpell.SuggestItem item : nativeResults) {
                if (normalized.length() <= 2 && item.distance > 1) continue;
                String word = item.original;
                if (word.equals(input)) continue;
                double score = computeScore(normalized, item.term, word, item.frequency,
                        item.distance, false, input.length());
                // Bonus for low weighted distance (adjacent key typos)
                if (item.weightedDistance >= 0 && item.weightedDistance < item.distance) {
                    score += (item.distance - item.weightedDistance) * 0.5;
                }
                // Bigram boost for fuzzy matches
                if (nextWordEnabled && normalizedPrev != null) {
                    int bgFreq = ss.getBigramFrequency(normalizedPrev, item.term);
                    if (bgFreq > 0) {
                        score += bgFreq / 100.0;
                    }
                }
                String key = word.toLowerCase(Locale.ROOT);
                if (seen.add(key)) {
                    insertSorted(top, new WordPredictor.Suggestion(word, item.distance, score), limit);
                }
            }
        }

        return top;
    }

    private List<WordPredictor.Suggestion> bigramNextWord(NativeSymSpell ss, String normalizedPrev, int limit) {
        NativeSymSpell.BigramItem[] items = ss.bigramLookup(normalizedPrev, limit);
        if (items.length == 0) return Collections.emptyList();

        List<WordPredictor.Suggestion> results = new ArrayList<>();
        for (NativeSymSpell.BigramItem item : items) {
            double score = item.frequency / 50.0;
            results.add(new WordPredictor.Suggestion(item.word, 0, score));
        }
        return results;
    }

    public void setMaxWords(int max) {
        if (max > 0) this.maxWords = max;
    }

    public void setKeyboardLayout(String layout) {
        if (layout != null && !layout.isEmpty()) {
            this.keyboardLayout = layout;
        }
    }

    public void setNextWordEnabled(boolean enabled) {
        this.nextWordEnabled = enabled;
    }

    public void setKeyboardAwareEnabled(boolean enabled) {
        this.keyboardAwareEnabled = enabled;
    }

    @Override
    public void loadDictionary(Context context, String locale) {
        synchronized (loadLock) {
            Snapshot cur = active;
            if (cur != null && locale.equals(cur.locale)) {
                return; // already the active dictionary
            }
            // Already built — switching languages is then just a pointer swap.
            NativeSymSpell cached = loaded.get(locale);
            if (cached != null) {
                publish(cached, locale);
                Log.w(TAG, "Switched to cached dictionary " + locale);
                return;
            }
            // Built into a fresh instance: the currently active dictionary keeps
            // serving suggestions for the whole rebuild instead of going dead.
            NativeSymSpell built = build(context, locale, true);
            if (built == null) {
                Log.e(TAG, "Load failed for " + locale + ", keeping previous dictionary");
                return;
            }
            loaded.put(locale, built);
            publish(built, locale);
            evictExtras();
        }
    }

    @Override
    public void preloadDictionary(Context context, String locale) {
        synchronized (loadLock) {
            Snapshot cur = active;
            if (cur != null && locale.equals(cur.locale)) return;
            if (loaded.containsKey(locale)) return;

            // Build it for real and keep it: the point of a preload is that the
            // switch to this language costs nothing. Materialising only the cache
            // file (what this did before) still left a full rebuild on the switch.
            NativeSymSpell built = build(context, locale, true);
            if (built != null) {
                loaded.put(locale, built);
                evictExtras();
                Log.w(TAG, "Preloaded " + locale
                        + ", active dict remains " + getLoadedLocale());
            }
        }
    }

    /**
     * Drop dictionaries past {@link #MAX_CACHED}, oldest first, never the active one.
     * Call under {@link #loadLock}.
     */
    private void evictExtras() {
        if (loaded.size() <= MAX_CACHED) return;
        String activeLocale = getLoadedLocale();
        Iterator<Map.Entry<String, NativeSymSpell>> it = loaded.entrySet().iterator();
        while (loaded.size() > MAX_CACHED && it.hasNext()) {
            Map.Entry<String, NativeSymSpell> e = it.next();
            if (e.getKey().equals(activeLocale)) continue;
            it.remove();
            retire(e.getValue());
        }
    }

    /**
     * Free a dictionary that is no longer reachable through {@link #active}.
     * Taking the write lock waits for every in-flight suggest() to return, so no
     * thread can still be holding it when the native memory goes away.
     */
    private void retire(NativeSymSpell ns) {
        if (ns == null) return;
        accessLock.writeLock().lock();
        accessLock.writeLock().unlock();
        ns.destroy();
    }

    @Override
    public boolean isReady() {
        return active != null;
    }

    @Override
    public String getLoadedLocale() {
        Snapshot snap = active;
        return (snap != null) ? snap.locale : "";
    }

    /**
     * Swap in a dictionary. The outgoing one is kept in {@link #loaded} for the next
     * switch back and is freed only by {@link #evictExtras()}, so nothing is
     * destroyed here.
     */
    private void publish(NativeSymSpell ns, String locale) {
        accessLock.writeLock().lock();
        try {
            active = new Snapshot(ns, locale);
        } finally {
            accessLock.writeLock().unlock();
        }
        ReadyListener l = readyListener;
        if (l != null) {
            try {
                l.onEngineReady(locale);
            } catch (Throwable t) {
                Log.w(TAG, "ready listener failed: " + t);
            }
        }
    }

    private String cachePath(Context context, String locale) {
        return new File(context.getFilesDir(), CACHE_DIR + "/" + locale + ".ssnd").getAbsolutePath();
    }

    /**
     * Build a dictionary for a locale into a fresh, unpublished instance.
     * Never touches {@link #active}, so it is safe to run while lookups are in flight.
     *
     * @param withUserWords merge the user dictionary in (skip for cache-warming preloads)
     * @return the built instance, or null on failure — caller keeps the old dictionary
     */
    private NativeSymSpell build(Context context, String locale, boolean withUserWords) {
        long startTime = System.currentTimeMillis();
        String cachePath = cachePath(context, locale);

        // Try native binary cache first (mmap — very fast, no text re-parse needed)
        NativeSymSpell ns = NativeSymSpell.loadFromCache(cachePath);
        boolean fromCache = (ns != null && ns.size() > 0);
        if (ns != null && !fromCache) {
            ns.destroy();
            ns = null;
        }

        if (fromCache) {
            // v2→v3 upgrade: if cache has 0 bigrams, load from JSON and re-save
            if (ns.bigramCount() == 0) {
                loadBigrams(ns, context, locale);
                if (ns.bigramCount() > 0) {
                    ns.buildBigramIndex();
                    saveNativeCache(ns, context, locale);
                    Log.w(TAG, "Upgraded cache to v3 with " + ns.bigramCount() + " bigrams");
                }
            }
            long elapsed = System.currentTimeMillis() - startTime;
            WordDictionary.recordLoadStats(locale, "native-cache", ns.size(), elapsed);
            Log.w(TAG, "Loaded " + locale + " from native cache: " + ns.size()
                    + " words, " + ns.bigramCount() + " bigrams in " + elapsed + "ms");
        } else {
            WordDictionary.recordLoadingStart(locale);

            // v1 cache exists but couldn't be loaded (version mismatch) — delete it
            File cacheFile = new File(cachePath);
            if (cacheFile.exists()) {
                cacheFile.delete();
                Log.w(TAG, "Deleted stale v1 cache: " + cachePath);
            }

            if (!NativeSymSpell.isAvailable()) {
                Log.w(TAG, "Native library not available, cannot load");
                return null;
            }
            ns = new NativeSymSpell(2, 7);
            if (!ns.isValid()) {
                Log.e(TAG, "Failed to create native SymSpell instance");
                return null;
            }

            int wordCount = loadFromAssets(ns, context, locale);
            if (wordCount <= 0) {
                Log.e(TAG, "No words loaded for locale " + locale);
                ns.destroy();
                return null;
            }

            long buildStart = System.currentTimeMillis();
            ns.buildIndex();
            ns.buildBigramIndex();
            Log.w(TAG, "Native buildIndex for " + locale + ": "
                    + (System.currentTimeMillis() - buildStart) + "ms"
                    + " (" + ns.bigramCount() + " bigrams)");

            long elapsed = System.currentTimeMillis() - startTime;
            WordDictionary.recordLoadStats(locale, "native-assets", wordCount, elapsed);
            Log.w(TAG, "Loaded " + locale + " from assets (native): " + wordCount
                    + " words in " + elapsed + "ms");
        }

        if (withUserWords) {
            // Always merge user words (even after a cache load — user may have added new ones)
            int before = ns.size();
            loadUserWords(ns, context);
            if (ns.size() > before) {
                ns.buildIndex();
                Log.w(TAG, "Added " + (ns.size() - before) + " new user words, rebuilt index");
                saveNativeCache(ns, context, locale);
                return ns;
            }
        }
        if (!fromCache) {
            saveNativeCache(ns, context, locale);
        }
        return ns;
    }

    private void loadBigrams(NativeSymSpell ns, Context context, String locale) {
        String bigramFile = "dictionaries/" + locale + "_bigrams.json";
        try {
            InputStream is;
            try {
                is = context.getAssets().open(bigramFile);
            } catch (java.io.FileNotFoundException e) {
                is = LanguagePacks.open(context, bigramFile);
                if (is == null)
                    throw e;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 8192);
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int read;
            while ((read = reader.read(buf)) != -1) sb.append(buf, 0, read);
            reader.close();

            JSONObject root = new JSONObject(sb.toString());
            int count = 0;
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String word1 = keys.next();
                JSONArray pairs = root.getJSONArray(word1);
                for (int i = 0; i < pairs.length(); i++) {
                    JSONArray pair = pairs.getJSONArray(i);
                    String word2 = pair.getString(0);
                    int freq = pair.getInt(1);
                    String normalized2 = WordDictionary.normalize(word2);
                    ns.addBigram(word1, normalized2, word2, freq);
                    count++;
                }
            }
            Log.d(TAG, "Loaded " + count + " bigrams for " + locale);
        } catch (java.io.FileNotFoundException e) {
            Log.d(TAG, "No bigram file for " + locale);
        } catch (Exception e) {
            Log.w(TAG, "Failed to load bigrams for " + locale + ": " + e);
        }
    }

    private int loadFromAssets(NativeSymSpell ns, Context context, String locale) {
        String txtFilename = "dictionaries/" + locale + "_base.txt";
        String jsonFilename = "dictionaries/" + locale + "_base.json";
        try {
            InputStream is;
            boolean useTxt;
            try {
                is = context.getAssets().open(txtFilename);
                useTxt = true;
            } catch (java.io.FileNotFoundException e) {
                try {
                    is = context.getAssets().open(jsonFilename);
                    useTxt = false;
                } catch (java.io.FileNotFoundException e2) {
                    // Своего словаря для языка нет — ищем в установленных пакетах.
                    is = LanguagePacks.open(context, txtFilename);
                    useTxt = true;
                    if (is == null) {
                        is = LanguagePacks.open(context, jsonFilename);
                        useTxt = false;
                    }
                    if (is == null)
                        throw e;
                }
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 16384);

            // Collect all word-frequency pairs
            ArrayList<String[]> allEntries = new ArrayList<>();
            if (useTxt) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) break;
                    int tab = line.indexOf('\t');
                    if (tab <= 0) continue;
                    allEntries.add(new String[]{line.substring(0, tab), line.substring(tab + 1)});
                }
            } else {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int read;
                while ((read = reader.read(buf)) != -1) sb.append(buf, 0, read);
                org.json.JSONArray arr = new org.json.JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    if (Thread.currentThread().isInterrupted()) break;
                    org.json.JSONObject obj = arr.getJSONObject(i);
                    allEntries.add(new String[]{obj.getString("w"), String.valueOf(obj.getInt("f"))});
                }
            }
            reader.close();

            // Sort by frequency descending
            Collections.sort(allEntries, new Comparator<String[]>() {
                public int compare(String[] a, String[] b) {
                    try { return Integer.parseInt(b[1]) - Integer.parseInt(a[1]); }
                    catch (NumberFormatException e) { return 0; }
                }
            });
            if (maxWords > 0 && allEntries.size() > maxWords) {
                allEntries = new ArrayList<>(allEntries.subList(0, maxWords));
            }

            // Add to native SymSpell with both normalized and original forms
            for (String[] entry : allEntries) {
                if (Thread.currentThread().isInterrupted()) break;
                int freq;
                try { freq = Integer.parseInt(entry[1]); }
                catch (NumberFormatException e) { continue; }
                String word = entry[0];
                String normalized = WordDictionary.normalize(word);

                ns.addWord(normalized, word, freq);
            }

            // Load bigrams
            loadBigrams(ns, context, locale);

            return allEntries.size();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load dictionary: " + e);
            return 0;
        }
    }

    private void loadUserWords(NativeSymSpell ns, Context context) {
        List<UserDictionaryBridge.UserWord> userWords = UserDictionaryBridge.readAll(context);
        if (userWords.isEmpty()) return;
        int added = 0;
        for (UserDictionaryBridge.UserWord uw : userWords) {
            int freq = Math.max(uw.frequency, 200);
            String normalized = WordDictionary.normalize(uw.word);
            ns.addWord(normalized, uw.word, freq);
            if (uw.shortcut != null && !uw.shortcut.isEmpty()) {
                String normShort = WordDictionary.normalize(uw.shortcut);
                ns.addWord(normShort, uw.shortcut, freq);
            }
            added++;
        }
        if (added > 0) {
            Log.d(TAG, "Added " + added + " user dictionary words (native)");
        }
    }

    private void saveNativeCache(NativeSymSpell ns, Context context, String locale) {
        if (Thread.currentThread().isInterrupted()) return;
        try {
            File dir = new File(context.getFilesDir(), CACHE_DIR);
            dir.mkdirs();
            String path = new File(dir, locale + ".ssnd").getAbsolutePath();
            if (ns.save(path)) {
                Log.d(TAG, "Saved native cache: " + path);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save native cache: " + e);
        }
    }

    private double computeScore(String normalizedInput, String normalizedCandidate,
                                String word, int frequency, int distance,
                                boolean isPrefix, int inputLen) {
        int effFreq = WordDictionary.effectiveFrequency(frequency);
        double distanceScore = 1.0 / (1 + distance);
        double frequencyScore = effFreq / 1600.0;
        double prefixBonus = 0;
        if (isPrefix) {
            if (inputLen <= 2) prefixBonus = 2.0;
            else prefixBonus = 5.0;
        } else if (normalizedCandidate.startsWith(normalizedInput)) {
            if (inputLen <= 2) prefixBonus = 1.5;
            else prefixBonus = 3.0;
        }

        int lenDiff = Math.abs(word.length() - inputLen);
        double lengthBonus;
        if (lenDiff == 0) lengthBonus = 0.35;
        else if (lenDiff == 1) lengthBonus = 0.2;
        else if (lenDiff == 2) lengthBonus = 0.05;
        else lengthBonus = -0.15 * Math.min(lenDiff, 4);

        return distanceScore + frequencyScore + prefixBonus + lengthBonus;
    }

    private void insertSorted(List<WordPredictor.Suggestion> list, WordPredictor.Suggestion item, int limit) {
        list.add(item);
        Collections.sort(list, new Comparator<WordPredictor.Suggestion>() {
            public int compare(WordPredictor.Suggestion a, WordPredictor.Suggestion b) {
                int s = Double.compare(b.score, a.score);
                if (s != 0) return s;
                return Integer.compare(a.distance, b.distance);
            }
        });
        while (list.size() > limit) {
            list.remove(list.size() - 1);
        }
    }
}
