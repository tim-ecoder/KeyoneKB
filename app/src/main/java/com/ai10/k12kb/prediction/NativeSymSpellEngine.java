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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Native SymSpell prediction engine.
 *
 * Delegates fuzzy matching to the C library (libnativesymspell.so) for
 * faster lookups and lower memory/GC overhead. Falls back to Java
 * SymSpellEngine if the native library is not available.
 *
 * Like SymSpellEngine, maintains a prefix cache and normalized index
 * in Java for prefix completions (which are simple HashMap lookups
 * and don't benefit much from native code).
 */
public class NativeSymSpellEngine implements PredictionEngine {

    private static final String TAG = "NativeSymSpellEngine";
    private static final int MAX_NATIVE_WORDS = 150000;
    private static final int MAX_JAVA_INDEX_WORDS = 35000;
    private static final int MAX_PREFIX_CACHE = 4;
    private static final String CACHE_DIR = "native_dict_cache";

    private volatile NativeSymSpell nativeSymSpell;
    private final Object loadLock = new Object();
    private final HashMap<String, NativeSymSpellEngine> dictCache = new HashMap<>();
    private final HashMap<String, List<WordDictionary.DictEntry>> prefixCache = new HashMap<>();
    private final HashMap<String, List<WordDictionary.DictEntry>> normalizedIndex = new HashMap<>();
    private LearnedDictionary learnedDict;
    private volatile boolean ready = false;
    private volatile String loadedLocale = "";
    private String keyboardLayout = "qwerty";

    @Override
    public List<WordPredictor.Suggestion> suggest(String input, String previousWord, int limit) {
        NativeSymSpell ss = nativeSymSpell; // local snapshot for thread safety
        if (!ready || ss == null) return Collections.emptyList();
        if (input == null || input.isEmpty()) return Collections.emptyList();

        String normalized = WordDictionary.normalize(input);
        if (normalized.isEmpty()) return Collections.emptyList();

        HashSet<String> seen = new HashSet<>();
        List<WordPredictor.Suggestion> top = new ArrayList<>();

        // 1. Prefix completions (from Java-side prefix cache)
        List<WordDictionary.DictEntry> completions = lookupByPrefix(normalized, 100);
        for (WordDictionary.DictEntry entry : completions) {
            String normEntry = WordDictionary.normalize(entry.word);
            if (!normEntry.startsWith(normalized)) continue;
            if (entry.word.length() <= input.length()) continue;
            if (entry.word.equalsIgnoreCase(input)) continue;

            int effFreq = WordDictionary.effectiveFrequency(entry.frequency);
            int minFreq = normalized.length() <= 2 ? 300 : (normalized.length() == 3 ? 250 : 150);
            if (effFreq < minFreq) continue;

            double score = computeScore(normalized, normEntry, entry, 0, true, input.length());
            String key = entry.word.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                insertSorted(top, new WordPredictor.Suggestion(entry.word, 0, score), limit);
            }
        }

        // 2. Native SymSpell fuzzy matches (skip for single char)
        if (normalized.length() > 1) {
            int symLimit = normalized.length() <= 3 ? limit * 2 : limit * 4;
            NativeSymSpell.SuggestItem[] nativeResults =
                    ss.lookupWeighted(normalized, symLimit, keyboardLayout);

            for (NativeSymSpell.SuggestItem item : nativeResults) {
                if (normalized.length() <= 2 && item.distance > 1) continue;
                List<WordDictionary.DictEntry> entries = getByNormalized(item.term, 3);
                for (WordDictionary.DictEntry entry : entries) {
                    if (entry.word.equals(input)) continue;
                    double score = computeScore(normalized, item.term, entry,
                            item.distance, false, input.length());
                    // Bonus for low weighted distance (adjacent key typos)
                    if (item.weightedDistance >= 0 && item.weightedDistance < item.distance) {
                        score += (item.distance - item.weightedDistance) * 0.5;
                    }
                    String key = entry.word.toLowerCase(Locale.ROOT);
                    if (seen.add(key)) {
                        insertSorted(top, new WordPredictor.Suggestion(entry.word, item.distance, score), limit);
                    }
                }
            }
        }

        return top;
    }

    public void setLearnedDictionary(LearnedDictionary learned) {
        this.learnedDict = learned;
    }

    public void setKeyboardLayout(String layout) {
        if (layout != null && !layout.isEmpty()) {
            this.keyboardLayout = layout;
        }
    }

    @Override
    public void loadDictionary(Context context, String locale) {
        synchronized (loadLock) {
            if (ready && locale.equals(loadedLocale)) {
                loadUserWords(context);
                loadLearnedWords();
                return;
            }
            load(context, locale);
            loadUserWords(context);
            loadLearnedWords();
        }
    }

    @Override
    public void preloadDictionary(Context context, String locale) {
        synchronized (loadLock) {
            if (ready && locale.equals(loadedLocale)) return;
            load(context, locale);
        }
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String getLoadedLocale() {
        return loadedLocale;
    }

    private void load(Context context, String locale) {
        long startTime = System.currentTimeMillis();
        ready = false;
        prefixCache.clear();
        normalizedIndex.clear();

        WordDictionary.recordLoadingStart(locale);

        // Try native binary cache first (mmap — very fast)
        String cachePath = new File(context.getFilesDir(), CACHE_DIR + "/" + locale + ".ssnd").getAbsolutePath();
        NativeSymSpell cached = NativeSymSpell.loadFromCache(cachePath);
        if (cached != null && cached.size() > MAX_JAVA_INDEX_WORDS) {
            nativeSymSpell = cached;
            // Still need to rebuild Java-side indexes from the text dict
            loadTextDictForIndex(context, locale);
            ready = true;
            loadedLocale = locale;
            long elapsed = System.currentTimeMillis() - startTime;
            WordDictionary.recordLoadStats(locale, "native-cache", nativeSymSpell.size(), elapsed);
            Log.w(TAG, "Loaded " + locale + " from native cache: " + nativeSymSpell.size()
                    + " words in " + elapsed + "ms");
            return;
        }

        // Build from text dictionary
        if (!NativeSymSpell.isAvailable()) {
            Log.w(TAG, "Native library not available, cannot load");
            return;
        }

        nativeSymSpell = new NativeSymSpell(2, 7);
        if (!nativeSymSpell.isValid()) {
            Log.e(TAG, "Failed to create native SymSpell instance");
            return;
        }

        int wordCount = loadFromAssets(context, locale);
        if (wordCount <= 0) {
            Log.e(TAG, "No words loaded for locale " + locale);
            return;
        }

        long buildStart = System.currentTimeMillis();
        nativeSymSpell.buildIndex();
        long buildElapsed = System.currentTimeMillis() - buildStart;
        Log.w(TAG, "Native buildIndex for " + locale + ": " + buildElapsed + "ms");

        ready = true;
        loadedLocale = locale;
        long elapsed = System.currentTimeMillis() - startTime;
        WordDictionary.recordLoadStats(locale, "native-assets", wordCount, elapsed);
        Log.w(TAG, "Loaded " + locale + " from assets (native): " + wordCount
                + " words in " + elapsed + "ms");

        // Save native binary cache
        saveNativeCache(context, locale);
    }

    private int loadFromAssets(Context context, String locale) {
        String txtFilename = "dictionaries/" + locale + "_base.txt";
        String jsonFilename = "dictionaries/" + locale + "_base.json";
        try {
            InputStream is;
            boolean useTxt;
            try {
                is = context.getAssets().open(txtFilename);
                useTxt = true;
            } catch (java.io.FileNotFoundException e) {
                is = context.getAssets().open(jsonFilename);
                useTxt = false;
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
            if (allEntries.size() > MAX_NATIVE_WORDS) {
                allEntries = new ArrayList<>(allEntries.subList(0, MAX_NATIVE_WORDS));
            }

            // Add to native SymSpell (all words) and Java indexes (top words only)
            int idx = 0;
            for (String[] entry : allEntries) {
                if (Thread.currentThread().isInterrupted()) break;
                int freq;
                try { freq = Integer.parseInt(entry[1]); }
                catch (NumberFormatException e) { continue; }
                String word = entry[0];
                String normalized = WordDictionary.normalize(word);

                // Native side — all words
                nativeSymSpell.addWord(normalized, freq);

                // Java side — only top MAX_JAVA_INDEX_WORDS for prefix/normalized lookups
                if (idx < MAX_JAVA_INDEX_WORDS) {
                    addToJavaIndex(word, normalized, freq);
                }
                idx++;
            }

            return allEntries.size();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load dictionary: " + e);
            return 0;
        }
    }

    /**
     * Load text dict only for Java-side indexes (prefix cache, normalizedIndex).
     * Used when native cache was loaded via mmap.
     */
    private void loadTextDictForIndex(Context context, String locale) {
        String txtFilename = "dictionaries/" + locale + "_base.txt";
        try {
            InputStream is = context.getAssets().open(txtFilename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 16384);

            ArrayList<String[]> allEntries = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) break;
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                allEntries.add(new String[]{line.substring(0, tab), line.substring(tab + 1)});
            }
            reader.close();

            Collections.sort(allEntries, new Comparator<String[]>() {
                public int compare(String[] a, String[] b) {
                    try { return Integer.parseInt(b[1]) - Integer.parseInt(a[1]); }
                    catch (NumberFormatException e) { return 0; }
                }
            });
            if (allEntries.size() > MAX_JAVA_INDEX_WORDS) {
                allEntries = new ArrayList<>(allEntries.subList(0, MAX_JAVA_INDEX_WORDS));
            }

            for (String[] entry : allEntries) {
                if (Thread.currentThread().isInterrupted()) break;
                int freq;
                try { freq = Integer.parseInt(entry[1]); }
                catch (NumberFormatException e) { continue; }
                addToJavaIndex(entry[0], WordDictionary.normalize(entry[0]), freq);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load text dict for index: " + e);
        }
    }

    private void addToJavaIndex(String word, String normalized, int frequency) {
        // Normalized index
        List<WordDictionary.DictEntry> list = normalizedIndex.get(normalized);
        if (list == null) {
            list = new ArrayList<>();
            normalizedIndex.put(normalized, list);
        }
        list.add(new WordDictionary.DictEntry(word, frequency));

        // Prefix cache
        for (int len = 1; len <= Math.min(normalized.length(), MAX_PREFIX_CACHE); len++) {
            String prefix = normalized.substring(0, len);
            List<WordDictionary.DictEntry> plist = prefixCache.get(prefix);
            if (plist == null) {
                plist = new ArrayList<>();
                prefixCache.put(prefix, plist);
            }
            if (plist.size() < 200 || frequency > 50) {
                plist.add(new WordDictionary.DictEntry(word, frequency));
            }
        }
    }

    private void loadUserWords(Context context) {
        List<UserDictionaryBridge.UserWord> userWords = UserDictionaryBridge.readAll(context);
        if (userWords.isEmpty()) return;
        int added = 0;
        for (UserDictionaryBridge.UserWord uw : userWords) {
            int freq = Math.max(uw.frequency, 200);
            String normalized = WordDictionary.normalize(uw.word);
            nativeSymSpell.addWord(normalized, freq);
            addToJavaIndex(uw.word, normalized, freq);
            if (uw.shortcut != null && !uw.shortcut.isEmpty()) {
                String normShort = WordDictionary.normalize(uw.shortcut);
                nativeSymSpell.addWord(normShort, freq);
                addToJavaIndex(uw.shortcut, normShort, freq);
            }
            added++;
        }
        if (added > 0) {
            nativeSymSpell.buildIndex();
            Log.d(TAG, "Added " + added + " user dictionary words (native)");
        }
    }

    private void loadLearnedWords() {
        if (learnedDict == null) return;
        List<LearnedDictionary.LearnedWord> learnedWords = learnedDict.getSuggestionWords();
        if (learnedWords.isEmpty()) return;
        int added = 0;
        for (LearnedDictionary.LearnedWord lw : learnedWords) {
            int freq = Math.min(240, LearnedDictionary.getBaseFrequency() + (lw.count - 1) * 5);
            String normalized = WordDictionary.normalize(lw.word);
            nativeSymSpell.addWord(normalized, freq);
            addToJavaIndex(lw.word, normalized, freq);
            added++;
        }
        if (added > 0) {
            nativeSymSpell.buildIndex();
            Log.d(TAG, "Added " + added + " learned words (native)");
        }
    }

    private void saveNativeCache(Context context, String locale) {
        if (Thread.currentThread().isInterrupted()) return;
        try {
            File dir = new File(context.getFilesDir(), CACHE_DIR);
            dir.mkdirs();
            String path = new File(dir, locale + ".ssnd").getAbsolutePath();
            if (nativeSymSpell.save(path)) {
                Log.d(TAG, "Saved native cache: " + path);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save native cache: " + e);
        }
    }

    private List<WordDictionary.DictEntry> lookupByPrefix(String prefix, int maxSize) {
        if (prefix == null || prefix.isEmpty()) return Collections.emptyList();
        String normalized = WordDictionary.normalize(prefix);

        if (normalized.length() <= MAX_PREFIX_CACHE) {
            List<WordDictionary.DictEntry> cached = prefixCache.get(normalized);
            if (cached == null) return Collections.emptyList();
            List<WordDictionary.DictEntry> result = new ArrayList<>();
            for (WordDictionary.DictEntry e : cached) {
                if (WordDictionary.normalize(e.word).startsWith(normalized)) {
                    result.add(e);
                }
            }
            sortByFrequency(result);
            if (result.size() > maxSize) return result.subList(0, maxSize);
            return result;
        }

        List<WordDictionary.DictEntry> result = new ArrayList<>();
        for (java.util.Map.Entry<String, List<WordDictionary.DictEntry>> entry : normalizedIndex.entrySet()) {
            if (entry.getKey().startsWith(normalized)) {
                result.addAll(entry.getValue());
            }
        }
        sortByFrequency(result);
        if (result.size() > maxSize) return result.subList(0, maxSize);
        return result;
    }

    private List<WordDictionary.DictEntry> getByNormalized(String normalizedWord, int limit) {
        List<WordDictionary.DictEntry> entries = normalizedIndex.get(normalizedWord);
        if (entries == null) return Collections.emptyList();
        List<WordDictionary.DictEntry> copy = new ArrayList<>(entries);
        sortByFrequency(copy);
        if (copy.size() > limit) return copy.subList(0, limit);
        return copy;
    }

    private double computeScore(String normalizedInput, String normalizedCandidate,
                                WordDictionary.DictEntry entry, int distance,
                                boolean isPrefix, int inputLen) {
        int effFreq = WordDictionary.effectiveFrequency(entry.frequency);
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

        int lenDiff = Math.abs(entry.word.length() - inputLen);
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

    private void sortByFrequency(List<WordDictionary.DictEntry> list) {
        Collections.sort(list, new Comparator<WordDictionary.DictEntry>() {
            public int compare(WordDictionary.DictEntry a, WordDictionary.DictEntry b) {
                return Integer.compare(b.frequency, a.frequency);
            }
        });
    }
}
