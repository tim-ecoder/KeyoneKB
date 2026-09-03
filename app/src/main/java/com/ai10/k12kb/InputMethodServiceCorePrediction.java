package com.ai10.k12kb;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.ai10.k12kb.prediction.LanguagePacks;
import com.ai10.k12kb.prediction.SuggestionBar;
import com.ai10.k12kb.prediction.TranslationManager;
import com.ai10.k12kb.prediction.WordDictionary;
import com.ai10.k12kb.prediction.WordPredictor;

import java.util.List;

public abstract class InputMethodServiceCorePrediction extends InputMethodServiceCoreGesture {

    // --- Fields moved from InputMethodServiceCoreCustomizable ---
    protected WordPredictor wordPredictor;
    protected boolean dictLoadingToastShown = false;
    protected KeyboardLayoutManager keyboardLayoutManager = new KeyboardLayoutManager();

    // --- Fields moved from K12KbIME ---
    protected SuggestionBar suggestionBar;
    protected TranslationManager translationManager;
    protected int predictionSlotCount = 4;
    protected int translationSlotCount = 4;
    protected boolean predictionBarHiddenByDefault = false;
    protected boolean predictionBarVisibleThisSession = false;
    protected boolean predictionBarOpenedByTranslation = false;
    /**
     * Dictionary loads finish on background threads. View.post() is not usable for
     * hopping back: an IME's input view is detached whenever the keyboard window is
     * down, and posts on a detached view only run if it is attached again.
     */
    private final android.os.Handler uiHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    // --- Method moved from InputMethodServiceCoreCustomizable ---

    protected void ShowDictLoadingToast() {
        if (!wordPredictor.isEngineReady() && !dictLoadingToastShown) {
            dictLoadingToastShown = true;
            Toast.makeText(getApplicationContext(),
                    getString(R.string.prediction_loading_toast),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // --- Methods moved from K12KbIME ---

    protected void updatePredictorWordAtCursor() {
        try {
            InputConnection ic = getCurrentInputConnection();
            if (ic == null || wordPredictor == null) return;
            CharSequence before = ic.getTextBeforeCursor(96, 0);
            if (before == null || before.length() == 0) {
                // The editor gave us no text, which covers two very different cases
                // that an InputConnection cannot tell apart:
                //   - the field really is empty;
                //   - the editor does not support text extraction at all (terminals
                //     declaring TYPE_NULL, some WebView/Compose and custom views), or
                //     was too busy to answer this synchronous IPC in time.
                // Clearing here wiped the word the keystroke tracker had built up, so
                // in such apps Ctrl+W (and every cursor update) blanked the bar. Keep
                // what we track and just recompute; onStartInputPrediction drops the
                // tracked words when a new field is attached, so nothing leaks across
                // fields.
                wordPredictor.refreshSuggestions();
                return;
            }
            // Extract the word at cursor (characters before cursor until non-word char)
            int end = before.length();
            int start = end;
            while (start > 0) {
                char c = before.charAt(start - 1);
                if (WordDictionary.isWordChar(c)) {
                    start--;
                } else {
                    break;
                }
            }
            String currentWord = (start < end) ? before.subSequence(start, end).toString() : "";

            // Extract previous word (word before the current word)
            int prevEnd = start;
            // Skip whitespace/punctuation between words
            while (prevEnd > 0 && !WordDictionary.isWordChar(before.charAt(prevEnd - 1))) {
                prevEnd--;
            }
            int prevStart = prevEnd;
            while (prevStart > 0) {
                char c = before.charAt(prevStart - 1);
                if (WordDictionary.isWordChar(c)) {
                    prevStart--;
                } else {
                    break;
                }
            }
            String previousWord = (prevStart < prevEnd) ? before.subSequence(prevStart, prevEnd).toString() : "";
            wordPredictor.setPreviousWord(previousWord);
            wordPredictor.setCurrentWord(currentWord);
        } catch (Throwable ex) {
            Log.w(TAG2, "updatePredictorWordAtCursor error: " + ex);
        }
    }

    protected void acceptSuggestion(int index) {
        if (wordPredictor == null) return;
        String currentWord = wordPredictor.getCurrentWord();
        String replacement = wordPredictor.acceptSuggestion(index);
        if (replacement == null) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        // Delete the current word being typed (if any) and insert the suggestion + space
        if (currentWord != null && !currentWord.isEmpty()) {
            ic.deleteSurroundingText(currentWord.length(), 0);
        }
        ic.commitText(replacement + " ", 1);
        suggestionBar.clear();
    }

    protected void updateSuggestionBarWithTranslation(List<WordPredictor.Suggestion> suggestions) {
        if (suggestionBar == null || wordPredictor == null) return;
        if (predictionBarHiddenByDefault && !predictionBarVisibleThisSession)
            return;
        if (translationManager != null && translationManager.isEnabled()) {
            String word = wordPredictor.getCurrentWord();
            String prevWord = wordPredictor.getPreviousWord();
            if (word != null && !word.isEmpty()) {
                TranslationManager.Result result = translationManager.lookup(word, prevWord);
                if (!result.isEmpty()) {
                    List<String> translations = result.translations;
                    // Limit to translationSlotCount
                    if (translations.size() > translationSlotCount) {
                        translations = translations.subList(0, translationSlotCount);
                    }
                    suggestionBar.updateTranslation(translations, word, translationSlotCount,
                            result.phraseMatch, result.phraseResultCount);
                    setSuggestionBarShown(true);
                    return;
                }
            }
            // Translation on but nothing to show yet (dictionary still loading, or no
            // entry) — fall through to predictions rather than blanking the bar.
        }
        // Fall back to normal predictions (limited to predictionSlotCount)
        if (suggestions != null && suggestions.size() > predictionSlotCount) {
            suggestions = suggestions.subList(0, predictionSlotCount);
        }
        suggestionBar.update(suggestions, wordPredictor.getCurrentWord(), predictionSlotCount);
        if (suggestions != null && !suggestions.isEmpty()) {
            setSuggestionBarShown(true);
        } else {
            //Do not close suggestion bar, either it jumps
            //setSuggestionBarShown(false);
        }
    }

    /**
     * Repaint the bar from the predictor's current state, without asking the engine
     * for anything new. Used after a dictionary load and by the Ctrl+W / translation
     * toggles, so all three paths render identically.
     */
    protected void refreshSuggestionBar() {
        if (suggestionBar == null || wordPredictor == null) return;
        updateSuggestionBarWithTranslation(wordPredictor.getLatestSuggestions());
    }

    /**
     * A dictionary finished loading. Suggestions computed while it was loading were
     * dropped (the engine had nothing to answer with), so recompute for the word at
     * the cursor and repaint — otherwise the bar stays blank until the user forces a
     * refresh with Ctrl+W.
     */
    protected void onDictionaryLoaded(String locale) {
        uiHandler.post(new Runnable() {
            public void run() {
                if (wordPredictor == null || suggestionBar == null) return;
                updatePredictorWordAtCursor();
                refreshSuggestionBar();
            }
        });
    }

    protected void acceptTranslation(String translatedWord, boolean isPhraseResult) {
        if (translatedWord == null || translatedWord.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        // Replace the current word (and previous word if bigram phrase match) with the translation
        if (wordPredictor != null) {
            String currentWord = wordPredictor.getCurrentWord();
            String previousWord = wordPredictor.getPreviousWord();
            if (isPhraseResult && previousWord != null && !previousWord.isEmpty()) {
                // Bigram match: delete current word + space + previous word
                int deleteLen = (currentWord != null ? currentWord.length() : 0)
                        + 1 // space between words
                        + previousWord.length();
                ic.deleteSurroundingText(deleteLen, 0);
            } else if (currentWord != null && !currentWord.isEmpty()) {
                // Single word: delete only current word
                ic.deleteSurroundingText(currentWord.length(), 0);
            }
            wordPredictor.reset();
        }
        ic.commitText(translatedWord + " ", 1);
        suggestionBar.clear();
    }

    public boolean ActionToggleTranslationMode(KeyPressData keyPressData) {
        if (translationManager == null || suggestionBar == null) return false;
        // Update languages BEFORE toggle so dictionary loads correct direction
        updateTranslationLanguages();
        String from = translationManager.getSourceLang();
        String to = translationManager.getTargetLang();
        if (from != null && from.equals(to) && !translationManager.isEnabled()) {
            // Второго языка на устройстве нет: включать режим не во что, и
            // тост с направлением вида EN → EN только сбивал бы с толку.
            Toast.makeText(getApplicationContext(),
                    getString(R.string.pred_translation_no_packs), Toast.LENGTH_SHORT).show();
            return true;
        }
        boolean enabled = translationManager.toggle();
        String msg = enabled ?
                "\uD83C\uDF10 Translation " + from.toUpperCase() + " \u2192 " + to.toUpperCase() :
                "\uD83C\uDF10 Translation OFF";
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
        if (enabled) {
            // Show prediction bar if hidden (translation needs visible bar)
            if (predictionBarHiddenByDefault && !predictionBarVisibleThisSession) {
                predictionBarVisibleThisSession = true;
                predictionBarOpenedByTranslation = true;
                setSuggestionBarShown(true);
            }
            // Translate current word immediately
            if (wordPredictor != null) {
                updatePredictorWordAtCursor();
                refreshSuggestionBar();
            }
        } else {
            // Translation disabled — restore predictions
            if (predictionBarOpenedByTranslation) {
                predictionBarOpenedByTranslation = false;
                predictionBarVisibleThisSession = false;
                setSuggestionBarShown(false);
                suggestionBar.clear();
            } else if (wordPredictor != null) {
                // Force engine to recompute suggestions for current word
                // (don't use updatePredictorWordAtCursor — IC can return null)
                wordPredictor.refreshSuggestions();
                refreshSuggestionBar();
            } else {
                suggestionBar.clear();
            }
        }
        return true;
    }

    public boolean ActionTogglePredictionBar(KeyPressData keyPressData) {
        if (suggestionBar == null || wordPredictor == null) return false;

        if (!predictionBarHiddenByDefault) return false;

        if(!predictionBarVisibleThisSession) {
            predictionBarVisibleThisSession = true;
            Toast.makeText(getApplicationContext(), "\uD83D\uDD2E Predictions ON", Toast.LENGTH_SHORT).show();
            // Первое включение панели — здесь и начинается загрузка словаря.
            EnsurePredictionDictionary();
            // Read the word at the cursor and force a prediction update. Uses the
            // shared extractor so previousWord is set too \u2014 without it next-word
            // (bigram) prediction is dead on this path and a stale previousWord from
            // another field can leak in.
            updatePredictorWordAtCursor();
            // Populate before showing: revealing the bar first leaves it visibly
            // empty whenever the engine had nothing cached to paint.
            refreshSuggestionBar();
            setSuggestionBarShown(true);
        } else {
            predictionBarVisibleThisSession = false;
            setSuggestionBarShown(false);
            Toast.makeText(getApplicationContext(), "\uD83D\uDD2E Predictions OFF", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    /** Dictionary locale for the layout in use. "en" when it cannot be determined. */
    protected String currentPredictionLocale() {
        try {
            if (keyboardLayoutManager == null) return "en";
            KeyboardLayout kl = keyboardLayoutManager.GetCurrentKeyboardLayout();
            if (kl == null || kl.KeyboardName == null) return "en";
            String lower = kl.KeyboardName.toLowerCase(java.util.Locale.ROOT);
            // Украинский считает своим русский словарь — своего у нас нет.
            if (lower.contains("украин") || lower.contains("ukrain")) return "ru";
            // Язык раскладки определяется там же, где и для перевода, чтобы два
            // списка языков не расходились. Но словарь может быть не установлен:
            // французский и немецкий живут в отдельных пакетах, и без пакета
            // предсказания должны молча остаться английскими, а не пропасть.
            String lang = layoutToLangCode(kl);
            if (!"en".equals(lang)
                    && !LanguagePacks.exists(getApplicationContext(), "dictionaries/" + lang + "_base.txt")) {
                Log.d(TAG2, "Нет словаря предсказаний для " + lang + ", остаёмся на en");
                return "en";
            }
            return lang;
        } catch (Throwable ex) {
            return "en";
        }
    }

    /** Идут ли сейчас предсказания: панель видна или показывается сразу. */
    protected boolean PredictionsActive() {
        return wordPredictor != null
                && (!predictionBarHiddenByDefault || predictionBarVisibleThisSession);
    }

    /**
     * Загрузить словарь, если предсказания нужны прямо сейчас.
     *
     * Вызывается там, где предсказания включаются: вход в поле ввода при
     * показанной панели, Ctrl+W, смена раскладки при работающих предсказаниях.
     * Раньше словарь грузился при создании клавиатуры и при каждой смене
     * раскладки — то есть и тем, кто предсказаниями не пользуется.
     */
    protected void EnsurePredictionDictionary() {
        if (!PredictionsActive())
            return;
        reloadDictionaryForCurrentLanguage();
    }

    protected void reloadDictionaryForCurrentLanguage() {
        try {
            if (wordPredictor == null) return;
            if (keyboardLayoutManager.GetCurrentKeyboardLayout() == null) return;
            String locale = currentPredictionLocale();
            // The load runs on a background thread; the completion callback is what
            // repaints the bar. Without it the bar stays blank for the whole rebuild
            // and never recovers, since every keystroke in between is dropped by an
            // engine that isn't ready yet.
            final String target = locale;
            wordPredictor.loadDictionary(getApplicationContext(), target, new Runnable() {
                public void run() {
                    onDictionaryLoaded(target);
                }
            });
        } catch (Throwable ex) {
            Log.e(TAG2, "reloadDictionaryForCurrentLanguage error: " + ex);
        }
    }

    protected void updateTranslationLanguages() {
        if (translationManager == null || keyboardLayoutManager == null) return;
        try {
            String currentLang = layoutToLangCode(keyboardLayoutManager.GetCurrentKeyboardLayout());
            String nextLang = layoutToLangCode(keyboardLayoutManager.GetNextKeyboardLayout());
            // Словари перевода есть только через английский, поэтому с любого
            // другого языка направление одно. А с английского выбор задаёт
            // настройка 30: язык следующей раскладки менял бы направление от
            // порядка переключения.
            if (LanguagePacks.BUILTIN_LANGUAGE.equals(currentLang)) {
                List<String> avail = LanguagePacks.availableLanguages(getApplicationContext());
                String chosen = k12KbSettings.GetStringValue(
                        k12KbSettings.APP_PREFERENCES_30_TRANSLATION_TARGET);
                // Выбранный язык мог быть удалён вместе с пакетом. Настройка
                // переживает удаление, и без этой проверки перевод продолжал
                // ссылаться на язык, которого на устройстве уже нет.
                if (chosen != null && !chosen.isEmpty()
                        && !chosen.equals(currentLang) && avail.contains(chosen))
                    nextLang = chosen;
                if (nextLang == null || nextLang.equals(currentLang) || !avail.contains(nextLang)) {
                    // Переводить не на что: берём первый установленный язык,
                    // иначе режим включался бы с направлением en → en.
                    nextLang = null;
                    for (int i = 0; i < avail.size(); i++) {
                        if (!LanguagePacks.BUILTIN_LANGUAGE.equals(avail.get(i))) {
                            nextLang = avail.get(i);
                            break;
                        }
                    }
                }
            } else {
                nextLang = LanguagePacks.BUILTIN_LANGUAGE;
            }
            if (nextLang == null) {
                // Ни одного языка кроме английского — переводить нечем.
                translationManager.updateLanguages(currentLang, currentLang);
                return;
            }
            translationManager.updateLanguages(currentLang, nextLang);
        } catch (Throwable ex) {
            Log.w(TAG2, "updateTranslationLanguages error: " + ex);
        }
    }

    /**
     * Код языка раскладки. Берётся из поля "language" её описания — по имени
     * раскладки угадывать нельзя: у пакета с новым языком имя произвольное, и
     * угадывание молча выдавало бы английский словарь вместо его собственного.
     */
    protected String layoutToLangCode(KeyboardLayout kl) {
        if (kl == null || kl.Resources == null) return LanguagePacks.BUILTIN_LANGUAGE;
        String code = KeyboardLayoutManager.LanguageCodeOf(kl.Resources);
        return (code != null && !code.isEmpty()) ? code : LanguagePacks.BUILTIN_LANGUAGE;
    }

    // --- Suggestion bar visibility (replaces framework setCandidatesViewShown) ---

    protected void setSuggestionBarShown(boolean shown) {
        if (suggestionBar != null) {
            suggestionBar.setVisibility(shown ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Wraps keyboardView and suggestionBar into a single input view container.
     * Call this from onCreateInputView() instead of returning keyboardView alone.
     */
    protected View createInputViewWithSuggestions(View keyboardView) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (suggestionBar != null) {
            if (suggestionBar.getParent() != null) {
                ((ViewGroup) suggestionBar.getParent()).removeView(suggestionBar);
            }
            container.addView(suggestionBar);
        }

        if (keyboardView != null) {
            if (keyboardView.getParent() != null) {
                ((ViewGroup) keyboardView.getParent()).removeView(keyboardView);
            }
            container.addView(keyboardView);
        }

        return container;
    }

    // --- Lifecycle overrides ---

    @Override
    public View onCreateCandidatesView() {
        // Suggestion bar is embedded in the input view — no framework candidates needed
        return null;
    }

    @Override
    public void onComputeInsets(Insets outInsets) {
        super.onComputeInsets(outInsets);
        outInsets.contentTopInsets = outInsets.visibleTopInsets;
    }

    @Override
    public void onDestroy() {
        if (wordPredictor != null) {
            wordPredictor.shutdown();
            wordPredictor = null;
        }
        super.onDestroy();
    }

    // --- Lifecycle helpers called from K12KbIME ---

    protected void initPrediction() {
        boolean predictionEnabled = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_17_PREDICTION_ENABLED);
        if (predictionEnabled) {
            // Fresh predictor — but engine+dictionaries are static inside WordPredictor
            // so if they were loaded before, they're reused instantly (no new threads)
            wordPredictor = new WordPredictor();
            int engineMode = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_19_PREDICTION_ENGINE);
            int dictSize = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_24_DICT_SIZE);
            wordPredictor.setDictSize(dictSize);
            wordPredictor.setNextWordEnabled(k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_26_NEXT_WORD_PREDICTION));
            wordPredictor.setKeyboardAwareEnabled(k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_27_KEYBOARD_AWARE));
            wordPredictor.setEngineMode(engineMode);
            int predictionHeight = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_15_PREDICTION_HEIGHT);
            if (predictionHeight < 10) predictionHeight = 36;
            predictionSlotCount = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_16_PREDICTION_COUNT);
            if (predictionSlotCount < 1) predictionSlotCount = 4;
            translationSlotCount = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_22_TRANSLATION_COUNT);
            if (translationSlotCount < 1) translationSlotCount = 4;
            int barSlots = Math.max(predictionSlotCount, translationSlotCount);
            suggestionBar = new SuggestionBar(this, predictionHeight, barSlots);
            wordPredictor.setSuggestLimit(predictionSlotCount);
            wordPredictor.setListener(new WordPredictor.SuggestionListener() {
                public void onSuggestionsUpdated(final List<WordPredictor.Suggestion> suggestions,
                                                 final String prefix) {
                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                        updateSuggestionBarWithTranslation(suggestions);
                    } else {
                        uiHandler.post(new Runnable() {
                            public void run() {
                                if (wordPredictor == null) return;
                                // Drop results the user has already typed past
                                if (!prefix.equals(wordPredictor.getCurrentWord())) return;
                                updateSuggestionBarWithTranslation(suggestions);
                            }
                        });
                    }
                }
            });
            // Repaint once a dictionary lands — suggestions requested while it was
            // loading produced nothing and were never painted.
            wordPredictor.setDictionaryLoadedListener(new WordPredictor.DictionaryLoadedListener() {
                public void onDictionaryLoaded(String locale) {
                    InputMethodServiceCorePrediction.this.onDictionaryLoaded(locale);
                }
            });
            // Kick the load off only now that the listeners are wired — started any
            // earlier, a fast (cached) load can finish before anyone is listening and
            // the first repaint is lost.
            // Load the locale of the layout actually in use: the engine holds one
            // active dictionary, so hardcoding "en" here left a keyboard that starts
            // in Russian answering Cyrillic prefixes from the English dictionary —
            // that is, no suggestions at all — until the user toggled the language.
            // Словарь здесь не грузим. Сборка большого словаря занимает секунды и
            // сотни мегабайт, а предсказания могут вообще не понадобиться: панель
            // бывает выключена по умолчанию и включается по Ctrl+W. Загрузка
            // начинается тогда, когда предсказания действительно нужны — см.
            // EnsurePredictionDictionary.
            Log.i(TAG2, "onCreate: WordPredictor initialized (engine cached: " + wordPredictor.isEngineReady() + ")");
            suggestionBar.setOnSuggestionClickListener(new SuggestionBar.OnSuggestionClickListener() {
                public void onSuggestionClicked(int index, String word) {
                    if (suggestionBar.isShowingTranslations()) {
                        acceptTranslation(word, suggestionBar.isPhraseResult(index));
                    } else {
                        acceptSuggestion(index);
                    }
                }
            });
            // Initialize translation manager
            translationManager = new TranslationManager(getApplicationContext());
            // Словарь перевода грузится целиком: индекс отображается с диска и
            // в память не копируется, поэтому урезать его незачем.
            translationManager.setMaxEntries(0);
            translationManager.setOnDictionaryLoadedListener(() -> uiHandler.post(() -> {
                if (suggestionBar == null || wordPredictor == null || translationManager == null) return;
                if (!translationManager.isEnabled()) return;
                // Lookups made while the CDB was loading returned nothing and fell
                // back to predictions — repaint now that translations are available.
                refreshSuggestionBar();
            }));
            predictionBarHiddenByDefault = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_23_PREDICTION_BAR_HIDDEN);
        }
    }

    protected void onStartInputPrediction() {
        predictionBarVisibleThisSession = false;
        // A new field is a new context: drop the words tracked for the old one,
        // otherwise the extractor's "editor told us nothing, keep tracking" path
        // could carry them over.
        if (wordPredictor != null) wordPredictor.clearTracking();
        if (wordPredictor != null && !predictionBarHiddenByDefault) {
            // Панель показывается сразу — значит словарь нужен уже сейчас.
            EnsurePredictionDictionary();
            setSuggestionBarShown(true);
            updatePredictorWordAtCursor();
        } else {
            setSuggestionBarShown(false);
        }
    }

    protected void onFinishInputPredictionSettingsReload() {
        if (wordPredictor != null) {
            int newDictSize = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_24_DICT_SIZE);
            wordPredictor.setDictSize(newDictSize);
            wordPredictor.setNextWordEnabled(k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_26_NEXT_WORD_PREDICTION));
            wordPredictor.setKeyboardAwareEnabled(k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_27_KEYBOARD_AWARE));
            // Возврат из настроек тоже не повод собирать словарь: если размер
            // поменяли, он соберётся при следующем включении предсказаний.
            EnsurePredictionDictionary();
        }
        if (translationManager != null) {
            translationManager.setMaxEntries(0);
        }
    }

    protected void onFinishInputPredictionCleanup() {
        if (translationManager != null && translationManager.isEnabled()) {
            translationManager.setEnabled(false);
        }
        predictionBarOpenedByTranslation = false;
    }

    protected void onUpdateSelectionPrediction(int newSelStart, int newSelEnd) {
        if (wordPredictor != null && wordPredictor.isEnabled() && newSelStart == newSelEnd) {
            updatePredictorWordAtCursor();
        }
    }
}
