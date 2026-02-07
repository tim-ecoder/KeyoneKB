package com.ai10.k12kb.prediction;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Dictionary for word prediction. Loads word-frequency pairs from JSON assets.
 * Provides prefix lookup and SymSpell fuzzy matching.
 * Ported from Pastiera DictionaryRepository.
 */
public class WordDictionary {

    private static final String TAG = "WordDictionary";
    private static final int MAX_PREFIX_CACHE = 4;

    private final SymSpell symSpell = new SymSpell(2, 7);
    private final HashMap<String, List<DictEntry>> prefixCache = new HashMap<>();
    private final HashMap<String, List<DictEntry>> normalizedIndex = new HashMap<>();
    private boolean ready = false;
    private String loadedLocale = "";

    public static class DictEntry {
        public final String word;
        public final int frequency;

        public DictEntry(String word, int frequency) {
            this.word = word;
            this.frequency = frequency;
        }
    }

    public boolean isReady() {
        return ready;
    }

    public String getLoadedLocale() {
        return loadedLocale;
    }

    /**
     * Load dictionary from assets file.
     * Tries tab-separated txt format first (word\tfreq), falls back to JSON.
     * @param locale e.g. "en", "ru"
     */
    public void load(Context context, String locale) {
        long startTime = System.currentTimeMillis();
        ready = false;
        prefixCache.clear();
        normalizedIndex.clear();
        symSpell.setBulkLoading(true);

        // Try fast txt format first, fall back to JSON
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

            if (useTxt) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (tab <= 0) continue;
                    String word = line.substring(0, tab);
                    int freq;
                    try { freq = Integer.parseInt(line.substring(tab + 1)); }
                    catch (NumberFormatException e) { continue; }
                    addEntry(word, freq);
                    // Yield CPU every 500 words to avoid lag on main thread
                    if (++lineCount % 500 == 0) {
                        try { Thread.sleep(1); } catch (InterruptedException ignored) {}
                    }
                }
            } else {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int read;
                while ((read = reader.read(buf)) != -1) {
                    sb.append(buf, 0, read);
                }
                JSONArray arr = new JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    addEntry(obj.getString("w"), obj.getInt("f"));
                    // Yield CPU every 500 words to avoid lag on main thread
                    if (i % 500 == 499) {
                        try { Thread.sleep(1); } catch (InterruptedException ignored) {}
                    }
                }
            }
            reader.close();

            symSpell.setBulkLoading(false);
            symSpell.buildIndex();
            ready = true;
            loadedLocale = locale;
            long elapsed = System.currentTimeMillis() - startTime;
            Log.d(TAG, "Loaded " + locale + " dictionary: " + symSpell.size() + " words in " + elapsed + "ms");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load dictionary: " + e);
        }
    }

    private void addEntry(String word, int frequency) {
        String normalized = normalize(word);
        symSpell.addWord(normalized, frequency);

        // Normalized index
        List<DictEntry> list = normalizedIndex.get(normalized);
        if (list == null) {
            list = new ArrayList<>();
            normalizedIndex.put(normalized, list);
        }
        list.add(new DictEntry(word, frequency));

        // Prefix cache (up to MAX_PREFIX_CACHE chars)
        for (int len = 1; len <= Math.min(normalized.length(), MAX_PREFIX_CACHE); len++) {
            String prefix = normalized.substring(0, len);
            List<DictEntry> plist = prefixCache.get(prefix);
            if (plist == null) {
                plist = new ArrayList<>();
                prefixCache.put(prefix, plist);
            }
            // Keep list manageable - only store high-frequency entries
            if (plist.size() < 200 || frequency > 50) {
                plist.add(new DictEntry(word, frequency));
            }
        }
    }

    /**
     * Lookup by prefix. Returns entries whose normalized form starts with the given prefix.
     */
    public List<DictEntry> lookupByPrefix(String prefix, int maxSize) {
        if (prefix == null || prefix.isEmpty()) return Collections.emptyList();
        String normalized = normalize(prefix);

        // Use cache for short prefixes
        if (normalized.length() <= MAX_PREFIX_CACHE) {
            List<DictEntry> cached = prefixCache.get(normalized);
            if (cached == null) return Collections.emptyList();
            // Filter to actual prefix matches and sort by frequency
            List<DictEntry> result = new ArrayList<>();
            for (DictEntry e : cached) {
                if (normalize(e.word).startsWith(normalized)) {
                    result.add(e);
                }
            }
            sortByFrequency(result);
            if (result.size() > maxSize) return result.subList(0, maxSize);
            return result;
        }

        // For longer prefixes, scan normalized index
        List<DictEntry> result = new ArrayList<>();
        for (Map.Entry<String, List<DictEntry>> entry : normalizedIndex.entrySet()) {
            if (entry.getKey().startsWith(normalized)) {
                result.addAll(entry.getValue());
            }
        }
        sortByFrequency(result);
        if (result.size() > maxSize) return result.subList(0, maxSize);
        return result;
    }

    /**
     * SymSpell fuzzy lookup.
     */
    public List<SymSpell.SuggestItem> symSpellLookup(String input, int maxSuggestions) {
        String normalized = normalize(input);
        return symSpell.lookup(normalized, maxSuggestions);
    }

    /**
     * Get best dictionary entries for a normalized word.
     */
    public List<DictEntry> getByNormalized(String normalizedWord, int limit) {
        List<DictEntry> entries = normalizedIndex.get(normalizedWord);
        if (entries == null) return Collections.emptyList();
        List<DictEntry> copy = new ArrayList<>(entries);
        sortByFrequency(copy);
        if (copy.size() > limit) return copy.subList(0, limit);
        return copy;
    }

    /**
     * Check if word is known in dictionary.
     */
    public boolean isKnownWord(String word) {
        String normalized = normalize(word);
        return normalizedIndex.containsKey(normalized);
    }

    public int getExactFrequency(String word) {
        return symSpell.getFrequency(normalize(word));
    }

    /**
     * Effective frequency: scale 0-255 to 0-1600 using power curve.
     */
    public static int effectiveFrequency(int rawFrequency) {
        double normalized = rawFrequency / 255.0;
        return Math.max(1, (int) (Math.pow(normalized, 0.75) * 1600.0));
    }

    /**
     * Normalize a word: lowercase, strip accents, remove non-letter chars (keep apostrophe).
     */
    public static String normalize(String word) {
        if (word == null || word.isEmpty()) return "";
        String lower = word.toLowerCase(Locale.ROOT);
        // Normalize apostrophes
        lower = lower.replace('\u2018', '\'').replace('\u2019', '\'').replace('\u02BC', '\'');
        // Strip accents
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                if (Character.isLetterOrDigit(c) || c == '\'') {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private void sortByFrequency(List<DictEntry> list) {
        Collections.sort(list, new Comparator<DictEntry>() {
            public int compare(DictEntry a, DictEntry b) {
                return Integer.compare(b.frequency, a.frequency);
            }
        });
    }
}
