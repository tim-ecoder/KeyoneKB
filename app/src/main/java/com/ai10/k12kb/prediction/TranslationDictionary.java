package com.ai10.k12kb.prediction;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline bilingual dictionary loaded from TSV files.
 * Format: word\ttranslation1,translation2,...
 *
 * Supports single-word and phrase (bigram) lookups.
 * Phrase entries use space-separated words as key: "hot dog\tхот-дог"
 *
 * Supports loading from assets (bundled) or from external file (user-provided).
 */
public class TranslationDictionary {
    private static final String TAG = "TranslationDict";

    private final Map<String, String[]> dictionary = new HashMap<>();
    private final Map<String, String[]> phraseDictionary = new HashMap<>();
    private String sourceLang;
    private String targetLang;
    private boolean loaded = false;
    private boolean lastWasPhraseMatch = false;

    public TranslationDictionary() {
    }

    /**
     * Load dictionary from assets. File name format: dict/{from}_{to}.tsv
     * e.g. dict/ru_en.tsv, dict/en_ru.tsv
     */
    public synchronized void load(Context context, String fromLang, String toLang) {
        this.sourceLang = fromLang;
        this.targetLang = toLang;
        dictionary.clear();
        phraseDictionary.clear();
        loaded = false;

        String assetName = "dict/" + fromLang + "_" + toLang + ".tsv";

        // First try external file override
        File externalFile = new File("/sdcard/k12kb/dict/" + fromLang + "_" + toLang + ".tsv");
        if (externalFile.exists()) {
            try {
                InputStream is = new FileInputStream(externalFile);
                loadFromStream(is);
                Log.i(TAG, "Loaded external dict " + externalFile + ": " + dictionary.size() +
                        " words, " + phraseDictionary.size() + " phrases");
                loaded = true;
                return;
            } catch (Exception e) {
                Log.w(TAG, "Failed to load external dict, falling back to assets: " + e);
            }
        }

        // Load from assets
        try {
            InputStream is = context.getAssets().open(assetName);
            loadFromStream(is);
            Log.i(TAG, "Loaded asset dict " + assetName + ": " + dictionary.size() +
                    " words, " + phraseDictionary.size() + " phrases");
            loaded = true;
        } catch (Exception e) {
            Log.w(TAG, "No dictionary found for " + fromLang + " -> " + toLang + ": " + e);
        }
    }

    private void loadFromStream(InputStream is) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) {
            int tab = line.indexOf('\t');
            if (tab <= 0 || tab >= line.length() - 1) continue;
            String key = line.substring(0, tab).trim().toLowerCase();
            String translations = line.substring(tab + 1).trim();
            if (!key.isEmpty() && !translations.isEmpty()) {
                String[] values = translations.split(",");
                if (key.indexOf(' ') >= 0) {
                    // Phrase entry (contains space)
                    phraseDictionary.put(key, values);
                } else {
                    dictionary.put(key, values);
                }
            }
        }
        br.close();
        is.close();
    }

    /**
     * Look up translations for a word with optional previous word context.
     * First tries phrase lookup ("prevWord currentWord"), then single word.
     * Returns list of translation strings, or empty list if not found.
     */
    public synchronized List<String> translate(String word, String previousWord) {
        List<String> result = new ArrayList<>();
        lastWasPhraseMatch = false;
        if (!loaded || word == null || word.isEmpty()) return result;

        String currentKey = word.trim().toLowerCase();

        // Try phrase lookup first (context-aware)
        if (previousWord != null && !previousWord.isEmpty()) {
            String phraseKey = previousWord.trim().toLowerCase() + " " + currentKey;
            String[] phraseTranslations = phraseDictionary.get(phraseKey);
            if (phraseTranslations != null) {
                lastWasPhraseMatch = true;
                for (String t : phraseTranslations) {
                    String trimmed = t.trim();
                    if (!trimmed.isEmpty()) {
                        result.add(trimmed);
                    }
                }
                return result;
            }
        }

        // Fall back to single-word lookup
        String[] translations = dictionary.get(currentKey);
        if (translations != null) {
            for (String t : translations) {
                String trimmed = t.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    public boolean wasLastPhraseMatch() {
        return lastWasPhraseMatch;
    }

    /**
     * Look up translations for a word (no context).
     */
    public synchronized List<String> translate(String word) {
        return translate(word, null);
    }

    public boolean isLoaded() {
        return loaded;
    }

    public int size() {
        return dictionary.size();
    }

    public int phraseSize() {
        return phraseDictionary.size();
    }

    public String getSourceLang() {
        return sourceLang;
    }

    public String getTargetLang() {
        return targetLang;
    }
}
