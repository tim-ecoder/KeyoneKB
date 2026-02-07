package com.ai10.k12kb.prediction;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Bigram dictionary. Loads from assets/{locale}_bigrams.json.
 * Format: {"the": [["same",200],["first",195],...], "i": [["am",220],...]}
 */
public class BigramDictionary {

    private static final String TAG = "BigramDictionary";

    public static class BigramEntry {
        public final String word;
        public final int frequency;

        public BigramEntry(String word, int frequency) {
            this.word = word;
            this.frequency = frequency;
        }
    }

    // Map from word1 -> list of (word2, freq) pairs, sorted by freq desc
    private final HashMap<String, List<BigramEntry>> bigrams = new HashMap<>();
    private boolean ready = false;

    public boolean isReady() {
        return ready;
    }

    public void load(Context context, String locale) {
        ready = false;
        bigrams.clear();

        String filename = "dictionaries/" + locale + "_bigrams.json";
        try {
            InputStream is = context.getAssets().open(filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int read;
            while ((read = reader.read(buf)) != -1) {
                sb.append(buf, 0, read);
            }
            reader.close();

            JSONObject root = new JSONObject(sb.toString());
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String word1 = keys.next();
                JSONArray arr = root.getJSONArray(word1);
                List<BigramEntry> entries = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONArray pair = arr.getJSONArray(i);
                    String word2 = pair.getString(0);
                    int freq = pair.getInt(1);
                    entries.add(new BigramEntry(word2, freq));
                }
                bigrams.put(word1, entries);
            }

            ready = true;
            Log.d(TAG, "Loaded " + locale + " bigrams: " + bigrams.size() + " keys");
        } catch (java.io.FileNotFoundException e) {
            // Bigram file is optional — engine works without it
            Log.d(TAG, "No bigram file for " + locale + " (optional)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load bigrams " + filename + ": " + e);
        }
    }

    /**
     * Get next words after the given previous word.
     * @return list of (word, freq) pairs sorted by frequency desc, or empty list
     */
    public List<BigramEntry> getNextWords(String normalizedPrevWord, int limit) {
        if (normalizedPrevWord == null || normalizedPrevWord.isEmpty()) {
            return Collections.emptyList();
        }
        List<BigramEntry> entries = bigrams.get(normalizedPrevWord);
        if (entries == null) return Collections.emptyList();
        if (entries.size() <= limit) return entries;
        return entries.subList(0, limit);
    }

    /**
     * Get bigram frequency for word1 -> word2.
     * @return frequency or -1 if not found
     */
    public int getBigramFrequency(String word1, String word2) {
        if (word1 == null || word2 == null) return -1;
        List<BigramEntry> entries = bigrams.get(word1);
        if (entries == null) return -1;
        for (BigramEntry e : entries) {
            if (e.word.equals(word2)) return e.frequency;
        }
        return -1;
    }
}
