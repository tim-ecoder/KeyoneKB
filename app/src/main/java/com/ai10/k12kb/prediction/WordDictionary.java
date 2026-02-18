package com.ai10.k12kb.prediction;

import android.content.Context;

import java.io.File;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;

/**
 * Static utilities for word prediction: normalization, frequency scaling,
 * load-stats tracking, and cache management.
 */
public class WordDictionary {

    /** Loading stats per locale — survives across instances (static). */
    public static class LoadStats {
        public final String locale;
        public final String source;  // "cache", "assets", or "loading"
        public final int wordCount;
        public final long timeMs;
        public final long timestamp;

        public LoadStats(String locale, String source, int wordCount, long timeMs) {
            this.locale = locale;
            this.source = source;
            this.wordCount = wordCount;
            this.timeMs = timeMs;
            this.timestamp = System.currentTimeMillis();
        }
    }
    private static final HashMap<String, LoadStats> loadStatsMap = new HashMap<>();

    public static LoadStats getLoadStats(String locale) {
        return loadStatsMap.get(locale);
    }

    public static HashMap<String, LoadStats> getAllLoadStats() {
        return loadStatsMap;
    }

    /** Record that loading has started for a locale (used by all engines). */
    public static void recordLoadingStart(String locale) {
        loadStatsMap.put(locale, new LoadStats(locale, "loading", 0, 0));
    }

    /** Record completed load stats for a locale (used by all engines). */
    public static void recordLoadStats(String locale, String source, int wordCount, long timeMs) {
        loadStatsMap.put(locale, new LoadStats(locale, source, wordCount, timeMs));
    }

    public static void clearCacheFiles(Context context) {
        String[] dirs = {"dict_cache", "native_dict_cache"};
        for (String dirName : dirs) {
            File dir = new File(context.getFilesDir(), dirName);
            if (dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        f.delete();
                    }
                }
            }
        }
    }

    public static class DictEntry {
        public final String word;
        public final int frequency;

        public DictEntry(String word, int frequency) {
            this.word = word;
            this.frequency = frequency;
        }
    }

    /**
     * Check if native binary cache file exists for a locale.
     */
    public static boolean hasCacheFile(Context context, String locale) {
        return new File(context.getFilesDir(), "native_dict_cache/" + locale + ".ssnd").exists();
    }

    /**
     * Effective frequency: scale 0-255 to 0-1600 using power curve.
     */
    public static int effectiveFrequency(int rawFrequency) {
        double normalized = rawFrequency / 255.0;
        return Math.max(1, (int) (Math.pow(normalized, 0.75) * 1600.0));
    }

    /**
     * Check if a character is a "word character" for prediction purposes.
     * Includes letters, digits, apostrophe, and email-like chars (@, ., -, _).
     */
    public static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '\'' || c == '@' || c == '.' || c == '-' || c == '_';
    }

    /**
     * Normalize a word: lowercase, strip accents, keep letters/digits/apostrophe
     * and email-like characters (@, ., -, _).
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
                if (isWordChar(c)) {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
