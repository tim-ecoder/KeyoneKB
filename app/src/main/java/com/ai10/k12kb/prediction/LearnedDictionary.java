package com.ai10.k12kb.prediction;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks words typed by the user and persists them to a file.
 * Words are stored with usage count — more frequently typed words
 * get higher priority in predictions.
 *
 * File format: simple TSV (word\tcount) in k12kb/ folder on external storage.
 * Also supports internal storage fallback.
 */
public class LearnedDictionary {

    private static final String TAG = "LearnedDictionary";
    private static final String DIR_NAME = "k12kb";
    private static final String FILE_NAME = "learned_words.txt";
    private static final int MIN_WORD_LENGTH = 2;
    private static final int MAX_WORD_LENGTH = 48;
    private static final int MAX_WORDS = 10000;
    /** Words need to be typed this many times before appearing in suggestions */
    private static final int MIN_COUNT_FOR_SUGGESTION = 2;
    /** Frequency assigned to learned words (0-255 scale) */
    private static final int BASE_FREQUENCY = 180;

    public static class LearnedWord {
        public final String word;
        public int count;

        public LearnedWord(String word, int count) {
            this.word = word;
            this.count = count;
        }
    }

    private final HashMap<String, LearnedWord> words = new HashMap<>();
    private boolean dirty = false;
    private boolean loaded = false;

    /**
     * Record a word the user has typed. Only learns "real" words —
     * skips single chars, numbers-only, etc.
     */
    public synchronized void learnWord(String word) {
        if (word == null || word.length() < MIN_WORD_LENGTH || word.length() > MAX_WORD_LENGTH) return;

        // Skip if it's all digits or all non-letters
        boolean hasLetter = false;
        for (int i = 0; i < word.length(); i++) {
            if (Character.isLetter(word.charAt(i))) {
                hasLetter = true;
                break;
            }
        }
        if (!hasLetter) return;

        String key = word.toLowerCase(java.util.Locale.ROOT);
        LearnedWord existing = words.get(key);
        if (existing != null) {
            existing.count++;
        } else {
            if (words.size() >= MAX_WORDS) {
                evictLeastUsed();
            }
            words.put(key, new LearnedWord(word, 1));
        }
        dirty = true;
    }

    /**
     * Get all learned words that qualify for suggestions (count >= threshold).
     */
    public synchronized List<LearnedWord> getSuggestionWords() {
        List<LearnedWord> result = new ArrayList<>();
        for (LearnedWord lw : words.values()) {
            if (lw.count >= MIN_COUNT_FOR_SUGGESTION) {
                result.add(lw);
            }
        }
        return result;
    }

    /**
     * Get all learned words (for export/display).
     */
    public synchronized List<LearnedWord> getAllWords() {
        return new ArrayList<>(words.values());
    }

    /**
     * Get count of learned words.
     */
    public synchronized int size() {
        return words.size();
    }

    /**
     * Check if dictionary has been loaded from file.
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Load learned words from file. Tries external storage first (k12kb/),
     * falls back to internal app storage.
     */
    public synchronized void load(Context context) {
        File file = getFile(context);
        if (file == null || !file.exists()) {
            // Try internal fallback
            file = getInternalFile(context);
        }
        if (file == null || !file.exists()) {
            loaded = true;
            return;
        }
        loadFromFile(file);
        loaded = true;
    }

    /**
     * Save learned words to file. Writes to external storage (k12kb/),
     * also writes to internal as backup.
     */
    public synchronized void save(Context context) {
        if (!dirty) return;

        // Save to external
        File extFile = getFile(context);
        if (extFile != null) {
            saveToFile(extFile);
        }
        // Also save to internal as backup
        File intFile = getInternalFile(context);
        if (intFile != null) {
            saveToFile(intFile);
        }
        dirty = false;
    }

    /**
     * Import words from an external file (e.g. user-provided).
     * Merges with existing learned words.
     */
    public synchronized int importFromFile(File file) {
        if (file == null || !file.exists()) return 0;
        int before = words.size();
        loadFromFile(file);
        dirty = true;
        return words.size() - before;
    }

    /**
     * Clear all learned words and delete files.
     */
    public synchronized void clear(Context context) {
        words.clear();
        dirty = false;
        File extFile = getFile(context);
        if (extFile != null && extFile.exists()) extFile.delete();
        File intFile = getInternalFile(context);
        if (intFile != null && intFile.exists()) intFile.delete();
    }

    /**
     * Get the external storage file path.
     */
    public static File getFile(Context context) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), DIR_NAME);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.w(TAG, "Cannot create " + dir.getAbsolutePath());
                    return null;
                }
            }
            return new File(dir, FILE_NAME);
        } catch (Exception e) {
            Log.w(TAG, "Cannot access external storage: " + e);
            return null;
        }
    }

    private File getInternalFile(Context context) {
        try {
            File dir = new File(context.getFilesDir(), DIR_NAME);
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, FILE_NAME);
        } catch (Exception e) {
            Log.w(TAG, "Cannot access internal storage: " + e);
            return null;
        }
    }

    private void loadFromFile(File file) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), "UTF-8"), 8192);
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int tab = line.indexOf('\t');
                if (tab <= 0) {
                    // Just a word without count — treat as count=2
                    String word = line;
                    if (word.length() >= MIN_WORD_LENGTH && word.length() <= MAX_WORD_LENGTH) {
                        String key = word.toLowerCase(java.util.Locale.ROOT);
                        LearnedWord existing = words.get(key);
                        if (existing != null) {
                            existing.count = Math.max(existing.count, MIN_COUNT_FOR_SUGGESTION);
                        } else {
                            words.put(key, new LearnedWord(word, MIN_COUNT_FOR_SUGGESTION));
                        }
                    }
                    continue;
                }
                String word = line.substring(0, tab);
                int count;
                try {
                    count = Integer.parseInt(line.substring(tab + 1));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (word.length() < MIN_WORD_LENGTH || word.length() > MAX_WORD_LENGTH) continue;
                String key = word.toLowerCase(java.util.Locale.ROOT);
                LearnedWord existing = words.get(key);
                if (existing != null) {
                    existing.count = Math.max(existing.count, count);
                } else {
                    words.put(key, new LearnedWord(word, count));
                }
            }
            reader.close();
            Log.d(TAG, "Loaded " + words.size() + " learned words from " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error loading learned words: " + e);
        }
    }

    private void saveToFile(File file) {
        try {
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file), "UTF-8"), 8192);
            writer.write("# K12KB learned words\n");
            writer.write("# Format: word<TAB>count\n");
            // Sort by count descending for readability
            List<LearnedWord> sorted = new ArrayList<>(words.values());
            Collections.sort(sorted, new Comparator<LearnedWord>() {
                public int compare(LearnedWord a, LearnedWord b) {
                    return Integer.compare(b.count, a.count);
                }
            });
            for (LearnedWord lw : sorted) {
                writer.write(lw.word);
                writer.write('\t');
                writer.write(String.valueOf(lw.count));
                writer.write('\n');
            }
            writer.close();
            Log.d(TAG, "Saved " + words.size() + " learned words to " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error saving learned words: " + e);
        }
    }

    /**
     * Remove least-used words when at capacity.
     */
    private void evictLeastUsed() {
        // Find the word with lowest count
        String lowestKey = null;
        int lowestCount = Integer.MAX_VALUE;
        for (Map.Entry<String, LearnedWord> entry : words.entrySet()) {
            if (entry.getValue().count < lowestCount) {
                lowestCount = entry.getValue().count;
                lowestKey = entry.getKey();
            }
        }
        if (lowestKey != null) {
            words.remove(lowestKey);
        }
    }

    /** Base frequency for learned words in 0-255 scale */
    public static int getBaseFrequency() {
        return BASE_FREQUENCY;
    }

    /** Minimum count before a word appears in suggestions */
    public static int getMinCountForSuggestion() {
        return MIN_COUNT_FOR_SUGGESTION;
    }
}
