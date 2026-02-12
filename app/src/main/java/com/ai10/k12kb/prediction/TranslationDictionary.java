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
 * Supports loading from assets (bundled) or from external file (user-provided).
 */
public class TranslationDictionary {
    private static final String TAG = "TranslationDict";

    private final Map<String, String[]> dictionary = new HashMap<>();
    private String sourceLang;
    private String targetLang;
    private boolean loaded = false;

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
        loaded = false;

        String assetName = "dict/" + fromLang + "_" + toLang + ".tsv";

        // First try external file override
        File externalFile = new File("/sdcard/k12kb/dict/" + fromLang + "_" + toLang + ".tsv");
        if (externalFile.exists()) {
            try {
                InputStream is = new FileInputStream(externalFile);
                loadFromStream(is);
                Log.i(TAG, "Loaded external dict " + externalFile + ": " + dictionary.size() + " entries");
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
            Log.i(TAG, "Loaded asset dict " + assetName + ": " + dictionary.size() + " entries");
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
            String word = line.substring(0, tab).trim().toLowerCase();
            String translations = line.substring(tab + 1).trim();
            if (!word.isEmpty() && !translations.isEmpty()) {
                dictionary.put(word, translations.split(","));
            }
        }
        br.close();
        is.close();
    }

    /**
     * Look up translations for a word.
     * Returns list of translation strings, or empty list if not found.
     */
    public synchronized List<String> translate(String word) {
        List<String> result = new ArrayList<>();
        if (!loaded || word == null || word.isEmpty()) return result;

        String key = word.trim().toLowerCase();
        String[] translations = dictionary.get(key);
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

    public boolean isLoaded() {
        return loaded;
    }

    public int size() {
        return dictionary.size();
    }

    public String getSourceLang() {
        return sourceLang;
    }

    public String getTargetLang() {
        return targetLang;
    }
}
