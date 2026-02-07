package com.ai10.k12kb.prediction;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;


/**
 * N-gram prediction engine. Uses a Trie for prefix completions and
 * BigramDictionary for next-word prediction.
 *
 * Two modes:
 * - Next-word mode (empty input): returns top bigram entries for previousWord
 * - Completion mode (partial input): trie prefix lookup with bigram boost scoring
 */
public class NgramEngine implements PredictionEngine {

    private static final String TAG = "NgramEngine";

    private final Trie trie = new Trie();
    private final BigramDictionary bigramDict = new BigramDictionary();
    private boolean ready = false;
    private String loadedLocale = "";

    @Override
    public List<WordPredictor.Suggestion> suggest(String input, String previousWord, int limit) {
        if (!ready) return Collections.emptyList();

        String normalizedPrev = (previousWord != null && !previousWord.isEmpty())
                ? WordDictionary.normalize(previousWord) : "";

        // Next-word mode: empty input, use bigrams
        if (input == null || input.isEmpty()) {
            return suggestNextWord(normalizedPrev, limit);
        }

        // Completion mode: prefix lookup with bigram boost
        return suggestCompletion(input, normalizedPrev, limit);
    }

    private List<WordPredictor.Suggestion> suggestNextWord(String normalizedPrev, int limit) {
        if (normalizedPrev.isEmpty()) return Collections.emptyList();
        if (!bigramDict.isReady()) return Collections.emptyList();

        List<BigramDictionary.BigramEntry> nextWords = bigramDict.getNextWords(normalizedPrev, limit);
        List<WordPredictor.Suggestion> results = new ArrayList<>();
        for (BigramDictionary.BigramEntry entry : nextWords) {
            // Use bigram frequency as score
            double score = 5.0 + (entry.frequency / 255.0) * 3.0;
            results.add(new WordPredictor.Suggestion(entry.word, 0, score));
        }
        return results;
    }

    private List<WordPredictor.Suggestion> suggestCompletion(String input, String normalizedPrev, int limit) {
        String normalized = WordDictionary.normalize(input);
        if (normalized.isEmpty()) return Collections.emptyList();

        List<Trie.TrieResult> prefixResults = trie.findByPrefix(normalized, 100);

        HashSet<String> seen = new HashSet<>();
        List<WordPredictor.Suggestion> top = new ArrayList<>();

        for (Trie.TrieResult result : prefixResults) {
            if (result.word.length() <= input.length()) continue;
            if (result.word.equalsIgnoreCase(input)) continue;

            int effFreq = WordDictionary.effectiveFrequency(result.frequency);
            int minFreq = normalized.length() <= 2 ? 300 : (normalized.length() == 3 ? 250 : 150);
            if (effFreq < minFreq) continue;

            double frequencyScore = effFreq / 1600.0;
            double prefixBonus = (input.length() <= 2) ? 2.0 : 5.0;

            int lenDiff = Math.abs(result.word.length() - input.length());
            double lengthBonus;
            if (lenDiff == 0) lengthBonus = 0.35;
            else if (lenDiff == 1) lengthBonus = 0.2;
            else if (lenDiff == 2) lengthBonus = 0.05;
            else lengthBonus = -0.15 * Math.min(lenDiff, 4);

            // Bigram boost
            double bigramBonus = 0;
            if (!normalizedPrev.isEmpty() && bigramDict.isReady()) {
                String normCandidate = WordDictionary.normalize(result.word);
                int bigramFreq = bigramDict.getBigramFrequency(normalizedPrev, normCandidate);
                if (bigramFreq >= 0) {
                    bigramBonus = 3.0 + (bigramFreq / 255.0) * 2.0;
                }
            }

            double score = 1.0 + frequencyScore + prefixBonus + lengthBonus + bigramBonus;
            String key = result.word.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                insertSorted(top, new WordPredictor.Suggestion(result.word, 0, score), limit);
            }
        }

        return top;
    }

    @Override
    public void loadDictionary(Context context, String locale) {
        long startTime = System.currentTimeMillis();
        ready = false;

        // Load base dictionary into trie - try fast txt format first, fall back to JSON
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
                while ((line = reader.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (tab <= 0) continue;
                    String word = line.substring(0, tab);
                    int freq;
                    try { freq = Integer.parseInt(line.substring(tab + 1)); }
                    catch (NumberFormatException e) { continue; }
                    String normalized = WordDictionary.normalize(word);
                    if (!normalized.isEmpty()) {
                        trie.insert(normalized, freq);
                    }
                }
            } else {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int read;
                while ((read = reader.read(buf)) != -1) {
                    sb.append(buf, 0, read);
                }
                org.json.JSONArray arr = new org.json.JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject obj = arr.getJSONObject(i);
                    String word = obj.getString("w");
                    int freq = obj.getInt("f");
                    String normalized = WordDictionary.normalize(word);
                    if (!normalized.isEmpty()) {
                        trie.insert(normalized, freq);
                    }
                }
            }
            reader.close();

            long elapsed = System.currentTimeMillis() - startTime;
            Log.d(TAG, "Loaded " + locale + " trie: " + trie.size() + " words in " + elapsed + "ms");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load dictionary for " + locale + ": " + e);
            return;
        }

        // Load bigrams (optional, graceful fallback)
        bigramDict.load(context, locale);

        ready = true;
        loadedLocale = locale;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String getLoadedLocale() {
        return loadedLocale;
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
