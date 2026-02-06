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
 * Word prediction engine. Provides suggestions based on prefix matching
 * and SymSpell fuzzy matching with multi-factor scoring.
 * Ported from Pastiera SuggestionEngine, simplified for K12KB.
 */
public class WordPredictor {

    private static final String TAG = "WordPredictor";

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

    private WordDictionary dictionary;
    private final HashMap<String, WordDictionary> dictCache = new HashMap<>();
    private SuggestionListener listener;
    private String currentWord = "";
    private List<Suggestion> latestSuggestions = Collections.emptyList();
    private boolean enabled = true;

    public WordPredictor() {
    }

    public void setDictionary(WordDictionary dict) {
        this.dictionary = dict;
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

    public List<Suggestion> getLatestSuggestions() {
        return latestSuggestions;
    }

    /**
     * Load dictionary for a locale. Uses cached dictionary if already loaded,
     * otherwise loads in a background thread.
     */
    public void loadDictionary(final Context context, final String locale) {
        // Check if already active for this locale
        if (dictionary != null && dictionary.isReady() && locale.equals(dictionary.getLoadedLocale())) {
            return;
        }
        // Check cache - instant switch if already loaded before
        WordDictionary cached = dictCache.get(locale);
        if (cached != null && cached.isReady()) {
            dictionary = cached;
            if (currentWord.length() > 0) {
                updateSuggestions();
            }
            return;
        }
        // Need to load from disk - do it in background
        final WordDictionary newDict = new WordDictionary();
        dictCache.put(locale, newDict);
        dictionary = newDict;
        Thread t = new Thread(new Runnable() {
            public void run() {
                newDict.load(context, locale);
                // Re-trigger suggestions if there's a current word
                if (currentWord.length() > 0 && newDict.isReady()) {
                    updateSuggestions();
                }
            }
        });
        t.setPriority(Thread.MIN_PRIORITY);
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
                reset();
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
        if (word.isEmpty()) {
            reset();
        } else {
            updateSuggestions();
        }
    }

    public String getCurrentWord() {
        return currentWord;
    }

    /**
     * Reset tracker and clear suggestions.
     */
    public void reset() {
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
        reset();
        return result;
    }

    /**
     * Generate suggestions for the current word.
     */
    private void updateSuggestions() {
        if (dictionary == null || !dictionary.isReady()) {
            latestSuggestions = Collections.emptyList();
            if (listener != null) listener.onSuggestionsUpdated(latestSuggestions);
            return;
        }

        List<Suggestion> results = suggest(currentWord, 4);
        latestSuggestions = results;
        if (listener != null) {
            listener.onSuggestionsUpdated(results);
        }
    }

    /**
     * Core suggestion algorithm.
     */
    private List<Suggestion> suggest(String input, int limit) {
        if (input == null || input.isEmpty()) return Collections.emptyList();

        String normalized = WordDictionary.normalize(input);
        if (normalized.isEmpty()) return Collections.emptyList();

        HashSet<String> seen = new HashSet<>();
        List<Suggestion> top = new ArrayList<>();

        // 1. Prefix completions
        List<WordDictionary.DictEntry> completions = dictionary.lookupByPrefix(normalized, 100);
        for (WordDictionary.DictEntry entry : completions) {
            String normEntry = WordDictionary.normalize(entry.word);
            if (!normEntry.startsWith(normalized)) continue;
            if (entry.word.length() <= input.length()) continue; // Only completions
            if (entry.word.equalsIgnoreCase(input)) continue; // Skip exact match

            int effFreq = WordDictionary.effectiveFrequency(entry.frequency);
            // Filter rare words for short inputs
            int minFreq = normalized.length() <= 2 ? 300 : (normalized.length() == 3 ? 250 : 150);
            if (effFreq < minFreq) continue;

            double score = computeScore(normalized, normEntry, entry, 0, true, input.length());
            String key = entry.word.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                insertSorted(top, new Suggestion(entry.word, 0, score), limit);
            }
        }

        // 2. SymSpell fuzzy matches (skip for single char)
        if (normalized.length() > 1) {
            int symLimit = normalized.length() <= 3 ? limit * 2 : limit * 4;
            List<SymSpell.SuggestItem> symResults = dictionary.symSpellLookup(normalized, symLimit);
            for (SymSpell.SuggestItem item : symResults) {
                if (normalized.length() <= 2 && item.distance > 1) continue;
                List<WordDictionary.DictEntry> entries = dictionary.getByNormalized(item.term, 3);
                for (WordDictionary.DictEntry entry : entries) {
                    if (entry.word.equals(input)) continue; // Skip exact match
                    double score = computeScore(normalized, item.term, entry, item.distance, false, input.length());
                    String key = entry.word.toLowerCase(Locale.ROOT);
                    if (seen.add(key)) {
                        insertSorted(top, new Suggestion(entry.word, item.distance, score), limit);
                    }
                }
            }
        }

        return top;
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

        // Length similarity bonus
        int lenDiff = Math.abs(entry.word.length() - inputLen);
        double lengthBonus;
        if (lenDiff == 0) lengthBonus = 0.35;
        else if (lenDiff == 1) lengthBonus = 0.2;
        else if (lenDiff == 2) lengthBonus = 0.05;
        else lengthBonus = -0.15 * Math.min(lenDiff, 4);

        return distanceScore + frequencyScore + prefixBonus + lengthBonus;
    }

    private void insertSorted(List<Suggestion> list, Suggestion item, int limit) {
        list.add(item);
        Collections.sort(list, new Comparator<Suggestion>() {
            public int compare(Suggestion a, Suggestion b) {
                // Higher score first
                int s = Double.compare(b.score, a.score);
                if (s != 0) return s;
                return Integer.compare(a.distance, b.distance);
            }
        });
        while (list.size() > limit) {
            list.remove(list.size() - 1);
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
