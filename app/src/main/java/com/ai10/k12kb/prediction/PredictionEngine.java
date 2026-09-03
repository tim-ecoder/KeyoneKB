package com.ai10.k12kb.prediction;

import android.content.Context;

import java.util.List;

public interface PredictionEngine {

    /**
     * Notified once a dictionary has been published and {@link #isReady()} turns true.
     * Lets callers repaint suggestions that were computed while the engine was still
     * loading (and therefore silently produced nothing).
     */
    interface ReadyListener {
        void onEngineReady(String locale);
    }

    List<WordPredictor.Suggestion> suggest(String input, String previousWord, int limit);
    void loadDictionary(Context context, String locale);
    void preloadDictionary(Context context, String locale);
    boolean isReady();
    String getLoadedLocale();
    void setReadyListener(ReadyListener listener);

    /**
     * Освободить все загруженные словари. Нужно, когда состав языковых пакетов
     * изменился: словарь текущего языка мог приехать из только что
     * установленного пакета, а обычная загрузка считает его уже готовым.
     */
    void closeAll();
}
