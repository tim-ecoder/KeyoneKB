package com.ai10.k12kb.prediction;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class SymSpellEngine implements PredictionEngine {

    private static final String TAG = "SymSpellEngine";

    private WordDictionary dictionary;
    private final HashMap<String, WordDictionary> dictCache = new HashMap<>();
    private LearnedDictionary learnedDict;

    @Override
    public List<WordPredictor.Suggestion> suggest(String input, String previousWord, int limit) {
        if (dictionary == null || !dictionary.isReady()) return Collections.emptyList();
        if (input == null || input.isEmpty()) return Collections.emptyList();

        String normalized = WordDictionary.normalize(input);
        if (normalized.isEmpty()) return Collections.emptyList();

        HashSet<String> seen = new HashSet<>();
        List<WordPredictor.Suggestion> top = new ArrayList<>();

        // 1. Prefix completions
        List<WordDictionary.DictEntry> completions = dictionary.lookupByPrefix(normalized, 100);
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

        // 2. SymSpell fuzzy matches (skip for single char)
        if (normalized.length() > 1) {
            int symLimit = normalized.length() <= 3 ? limit * 2 : limit * 4;
            List<SymSpell.SuggestItem> symResults = dictionary.symSpellLookup(normalized, symLimit);
            for (SymSpell.SuggestItem item : symResults) {
                if (normalized.length() <= 2 && item.distance > 1) continue;
                List<WordDictionary.DictEntry> entries = dictionary.getByNormalized(item.term, 3);
                for (WordDictionary.DictEntry entry : entries) {
                    if (entry.word.equals(input)) continue;
                    double score = computeScore(normalized, item.term, entry, item.distance, false, input.length());
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

    @Override
    public void loadDictionary(Context context, String locale) {
        if (dictionary != null && dictionary.isReady() && locale.equals(dictionary.getLoadedLocale())) {
            return;
        }
        WordDictionary cached = dictCache.get(locale);
        if (cached != null) {
            dictionary = cached;
            if (cached.isReady()) {
                cached.loadUserWords(context);
                cached.loadLearnedWords(learnedDict);
                return;
            }
            cached.load(context, locale);
            cached.loadUserWords(context);
            cached.loadLearnedWords(learnedDict);
            return;
        }
        final WordDictionary newDict = new WordDictionary();
        dictCache.put(locale, newDict);
        dictionary = newDict;
        newDict.load(context, locale);
        newDict.loadUserWords(context);
        newDict.loadLearnedWords(learnedDict);
    }

    @Override
    public void preloadDictionary(Context context, String locale) {
        WordDictionary cached = dictCache.get(locale);
        if (cached != null) {
            if (cached.isReady()) {
                return;
            }
            cached.load(context, locale);
            return;
        }
        final WordDictionary newDict = new WordDictionary();
        dictCache.put(locale, newDict);
        newDict.load(context, locale);
    }

    @Override
    public boolean isReady() {
        return dictionary != null && dictionary.isReady();
    }

    @Override
    public String getLoadedLocale() {
        return dictionary != null ? dictionary.getLoadedLocale() : "";
    }

    public WordDictionary getDictionary() {
        return dictionary;
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
}
