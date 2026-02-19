# Translation Feature Implementation Plan

## Overview
Add inline translation to prediction pillows (suggestion bar). When translation mode is ON, the bar shows translations of the current word/phrase instead of predictions. Toggle via Ctrl+T or a new action. Translation direction follows keyboard layouts: current layout = source language, next layout = target language.

## Architecture

### Translation API
- **Google Cloud Translation API v2** at `translation.googleapis.com` — the only translation service reachable through the proxy
- Requires API key (free tier: 500K chars/month)
- API key stored in SharedPreferences, configurable in prediction settings
- Fallback: if no API key or offline — show "no translation" in bar

### New Files
1. **`TranslationEngine.java`** (in `prediction/`)
   - HTTP client calling `translation.googleapis.com/language/translate/v2`
   - `translate(String text, String fromLang, String toLang, Callback)` — async
   - Result cached in LRU map (last ~100 translations) to avoid redundant calls
   - Runs on background thread, delivers result on main thread

### Modified Files

2. **`K12KbIME.java`**
   - New field: `boolean translationMode = false`
   - New action method: `ActionToggleTranslationMode()` — flips translationMode, shows Toast
   - When translationMode ON + cursor moves or word changes:
     - Get current word from text before cursor
     - Determine source lang from current layout, target lang from next layout
     - Call `TranslationEngine.translate()`, on result → push to suggestion bar as special "translation suggestions"
   - When user clicks a translation suggestion → replace current word with translation
   - On `ChangeLanguage()` (layout switch): swap source↔target automatically (translation direction reverses since layouts swapped)

3. **`K12KbSettings.java`**
   - New pref: `APP_PREFERENCES_22_TRANSLATION_API_KEY = "translation_api_key"` (String)
   - New pref: `APP_PREFERENCES_23_TRANSLATION_ENABLED = "translation_enabled"` (boolean, default false)

4. **`SuggestionBar.java`**
   - New method `updateTranslation(String original, String translated, String langPair)`
   - Shows translation in center pillow (bold), original in side pillow (italic, dimmed)
   - Different pillow color (e.g., accent tint) to distinguish from predictions

5. **`InputMethodServiceCoreCustomizable.java`**
   - New action method: `ActionToggleTranslationMode()` — calls K12KbIME toggle
   - New meta method: `MetaIsTranslationMode()` — returns translationMode state

6. **`keyboard_mechanics.json`** (bb_key_1_2 and others)
   - Add Ctrl+T binding: when `MetaIsCtrlPressed`, keycode T → `ActionToggleTranslationMode` with `stop-processing-at-success-result: true`
   - Since Ctrl+key already goes through `ActionSendCtrlPlusKey`, we need to add a specific T handler BEFORE the generic Ctrl+key handler

7. **`activity_prediction_settings.xml` + `ActivityPredictionSettings.java`**
   - New section "TRANSLATION" with:
     - Switch: enable/disable translation feature
     - Text field: API key input
     - Status: shows configured languages, last translation test result

8. **`strings.xml` / `strings-ru.xml`**
   - String resources for translation UI

### Language Detection Logic
```
currentLayout = keyboardLayoutManager.GetCurrentKeyboardLayout()
nextLayout = keyboardLayoutManager.getNextLayout() // need to add this method

sourceLang = layoutToLangCode(currentLayout)  // "Русский" → "ru", "English" → "en"
targetLang = layoutToLangCode(nextLayout)
```

### KeyboardLayoutManager.java
- Add `getNextLayout()` method that returns the layout at `(CurrentLanguageListIndex + 1) % LangListCount`

## Steps (in order)

1. **Create `TranslationEngine.java`** — HTTP client with async translate, LRU cache
2. **Add settings** — API key pref, translation enabled pref in K12KbSettings
3. **Add `getNextLayout()`** to KeyboardLayoutManager
4. **Add lang detection** — helper to map KeyboardLayout.KeyboardName → ISO lang code
5. **Add `ActionToggleTranslationMode`** in InputMethodServiceCoreCustomizable
6. **Wire Ctrl+T** — add to keyboard_mechanics.json for all device variants
7. **Modify SuggestionBar** — add `updateTranslation()` with distinct styling
8. **Wire translation into K12KbIME** — on word change when translationMode=true, call TranslationEngine, update bar
9. **Swap languages on layout switch** — in ChangeLanguage(), the direction auto-reverses
10. **Add settings UI** — translation section in prediction settings
11. **Add string resources** — EN + RU
12. **Build and test**
