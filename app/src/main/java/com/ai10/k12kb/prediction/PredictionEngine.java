package com.ai10.k12kb.prediction;

import android.content.Context;

import java.util.List;

public interface PredictionEngine {
    List<WordPredictor.Suggestion> suggest(String input, String previousWord, int limit);
    void loadDictionary(Context context, String locale);
    boolean isReady();
    String getLoadedLocale();
}
