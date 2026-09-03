package com.ai10.k12kb;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.ai10.k12kb.prediction.LanguagePacks;
import com.ai10.k12kb.prediction.NativeTranslationDictionary;
import com.ai10.k12kb.prediction.WordDictionary;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;

public class ActivityPredictionSettings extends Activity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    private K12KbSettings k12KbSettings;
    private TextView tvDictStatus;
    private TextView tvCacheStatus;
    private TextView tvTranslationStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        k12KbSettings = K12KbSettings.Get(getSharedPreferences(K12KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));
        if (k12KbSettings.isDarkTheme()) {
            setTheme(R.style.AppTheme_Dark);
        }
        setContentView(R.layout.activity_prediction_settings);

        setupPredictionSettings();
        setupTranslation();
        setupStatus();
        setupCache();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshCacheStatus();
        refreshTranslationStatus();
    }

    private void setupPredictionSettings() {
        // Enable/disable prediction
        Switch switchEnabled = (Switch) findViewById(R.id.switch_prediction_enabled);
        boolean enabled = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_17_PREDICTION_ENABLED);
        switchEnabled.setChecked(enabled);
        switchEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_17_PREDICTION_ENABLED, isChecked);
            }
        });

        // Hide prediction bar by default (show on Ctrl+W / Ctrl+T)
        Switch switchBarHidden = (Switch) findViewById(R.id.switch_prediction_bar_hidden);
        boolean barHidden = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_23_PREDICTION_BAR_HIDDEN);
        switchBarHidden.setChecked(barHidden);
        switchBarHidden.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_23_PREDICTION_BAR_HIDDEN, isChecked);
            }
        });

        // Next-word prediction toggle
        Switch switchNextWord = (Switch) findViewById(R.id.switch_next_word_prediction);
        boolean nextWordEnabled = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_26_NEXT_WORD_PREDICTION);
        switchNextWord.setChecked(nextWordEnabled);
        switchNextWord.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_26_NEXT_WORD_PREDICTION, isChecked);
            }
        });

        // Keyboard-aware corrections toggle
        Switch switchKeyboardAware = (Switch) findViewById(R.id.switch_keyboard_aware);
        boolean kbAwareEnabled = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_27_KEYBOARD_AWARE);
        switchKeyboardAware.setChecked(kbAwareEnabled);
        switchKeyboardAware.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                k12KbSettings.SetBooleanValue(k12KbSettings.APP_PREFERENCES_27_KEYBOARD_AWARE, isChecked);
            }
        });

        // Bar height
        SeekBar seekHeight = (SeekBar) findViewById(R.id.seekBarPredictionHeight);
        int height = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_15_PREDICTION_HEIGHT);
        seekHeight.setProgress(height);
        seekHeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                k12KbSettings.SetIntValue(k12KbSettings.APP_PREFERENCES_15_PREDICTION_HEIGHT, Math.max(10, progress));
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Slots count
        SeekBar seekCount = (SeekBar) findViewById(R.id.seekBarPredictionCount);
        int count = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_16_PREDICTION_COUNT);
        seekCount.setProgress(count);
        seekCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                k12KbSettings.SetIntValue(k12KbSettings.APP_PREFERENCES_16_PREDICTION_COUNT, Math.max(1, progress));
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Dictionary size spinner
        final int[] dictSizeValues = {35000, 150000, 300000, 0};
        final Spinner spinnerDictSize = (Spinner) findViewById(R.id.spinner_dict_size);
        ArrayAdapter<CharSequence> dictSizeAdapter = ArrayAdapter.createFromResource(
                this, R.array.pref_dict_size_array, android.R.layout.simple_spinner_item);
        dictSizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDictSize.setAdapter(dictSizeAdapter);
        int dictSizeInit = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_24_DICT_SIZE);
        int dictSizePos = 0;
        for (int i = 0; i < dictSizeValues.length; i++) {
            if (dictSizeValues[i] == dictSizeInit) { dictSizePos = i; break; }
        }
        spinnerDictSize.setSelection(dictSizePos);
        SetupDictSizeNote();
        SetupTranslationTarget();
        spinnerDictSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int newSize = dictSizeValues[position];
                int currentSize = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_24_DICT_SIZE);
                if (newSize == currentSize) return;
                k12KbSettings.SetIntValue(k12KbSettings.APP_PREFERENCES_24_DICT_SIZE, newSize);
                WordDictionary.clearLoadStats();
                WordDictionary.clearCacheFiles(getApplicationContext());
                refreshStatus();
                refreshCacheStatus();
                Toast.makeText(getApplicationContext(),
                        getString(R.string.pred_dict_size_changed),
                        Toast.LENGTH_LONG).show();
            }
            public void onNothingSelected(AdapterView<?> parent) {
                spinnerDictSize.setSelection(0);
            }
        });
    }

    private void setupTranslation() {
        // Translation pillows count
        SeekBar seekTransCount = (SeekBar) findViewById(R.id.seekBarTranslationCount);
        int transCount = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_22_TRANSLATION_COUNT);
        if (transCount < 1) transCount = 4;
        seekTransCount.setProgress(transCount);
        seekTransCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                k12KbSettings.SetIntValue(k12KbSettings.APP_PREFERENCES_22_TRANSLATION_COUNT, Math.max(1, progress));
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Translation dictionary size spinner

        tvTranslationStatus = (TextView) findViewById(R.id.tv_translation_status);
    }

    /** Файл из assets клавиатуры, а если его там нет — из языкового пакета. */
    private java.io.InputStream OpenAssetOrPack(String assetPath) throws java.io.IOException {
        try {
            return getAssets().open(assetPath);
        } catch (java.io.IOException ex) {
            java.io.InputStream is = LanguagePacks.open(getApplicationContext(), assetPath);
            if (is == null)
                throw ex;
            return is;
        }
    }

    private void refreshTranslationStatus() {
        // Pre-fetch string resources on the UI thread (cannot access from background)
        final String strWords = getString(R.string.pred_translation_words);
        final String strPhrases = getString(R.string.pred_translation_phrases);
        final String strNotFound = getString(R.string.pred_translation_not_found);
        final String strExternal = getString(R.string.pred_translation_external);

        // Направления перевода строятся от установленных языков, а не из
        // жёсткого списка: перевод всегда идёт через английский, поэтому для
        // каждого языка пакета есть пара в обе стороны.
        final List<String> pairs = new ArrayList<>();
        for (String lang : LanguagePacks.availableLanguages(getApplicationContext())) {
            if (LanguagePacks.BUILTIN_LANGUAGE.equals(lang))
                continue;
            pairs.add(lang + "_" + LanguagePacks.BUILTIN_LANGUAGE);
            pairs.add(LanguagePacks.BUILTIN_LANGUAGE + "_" + lang);
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            StringBuilder sb = new StringBuilder();
            if (pairs.isEmpty())
                sb.append(getString(R.string.pred_translation_no_packs)).append("\n");
            for (String pair : pairs) {
                String assetName = "dict/" + pair + ".tsv";
                try {
                    java.io.InputStream is = OpenAssetOrPack(assetName);
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                    int wordCount = 0;
                    int phraseCount = 0;
                    String line;
                    while ((line = br.readLine()) != null) {
                        int tab = line.indexOf('\t');
                        if (tab > 0) {
                            String key = line.substring(0, tab);
                            if (key.indexOf(' ') >= 0) {
                                phraseCount++;
                            } else {
                                wordCount++;
                            }
                        }
                    }
                    br.close();
                    int totalEntries = wordCount + phraseCount;
                    // Словарь перевода грузится целиком: он отображается с диска
                    // и памяти почти не занимает, поэтому ограничивать нечего.
                    sb.append(pair.replace("_", " \u2192 ").toUpperCase()).append(": ");
                    sb.append(wordCount).append(" ").append(strWords);
                    if (phraseCount > 0) {
                        sb.append(" + ").append(phraseCount).append(" ").append(strPhrases);
                    }
                    sb.append("\n");
                } catch (Exception e) {
                    sb.append(pair.replace("_", " \u2192 ").toUpperCase()).append(": ")
                      .append(strNotFound).append("\n");
                }
            }
            // Check for external dict overrides
            File extDir = new File("/sdcard/k12kb/dict/");
            if (extDir.exists() && extDir.isDirectory()) {
                File[] files = extDir.listFiles();
                if (files != null && files.length > 0) {
                    sb.append("\n").append(strExternal).append(":\n");
                    for (File f : files) {
                        if (f.getName().endsWith(".tsv")) {
                            sb.append("  ").append(f.getName()).append(" (")
                              .append(f.length() / 1024).append(" KB)\n");
                        }
                    }
                }
            }
            final String result = sb.toString().trim();
            tvTranslationStatus.post(() -> tvTranslationStatus.setText(result));
        });
    }

    private void setupStatus() {
        tvDictStatus = (TextView) findViewById(R.id.tv_dict_status);
        refreshStatus();
    }

    private void refreshStatus() {
        StringBuilder sb = new StringBuilder();

        sb.append(getString(R.string.pred_status_engine)).append(": Native SymSpell\n");

        // Per-locale stats
        HashMap<String, WordDictionary.LoadStats> allStats = WordDictionary.getAllLoadStats();
        List<String> locales = LanguagePacks.availableLanguages(getApplicationContext());
        for (String locale : locales) {
            WordDictionary.LoadStats stats = allStats.get(locale);
            if (stats != null && "loading".equals(stats.source)) {
                // Loading in progress
                boolean hasCache = WordDictionary.hasCacheFile(getApplicationContext(), locale,
                        k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_24_DICT_SIZE));
                sb.append("\n").append(locale.toUpperCase()).append(": ")
                  .append(getString(R.string.pred_status_loading));
                if (hasCache) {
                    sb.append(" (").append(getString(R.string.pred_status_source_cache)).append(")");
                } else {
                    sb.append(" (").append(getString(R.string.pred_status_source_assets)).append(")");
                }
            } else if (stats != null) {
                // Движок пишет источник как "native-cache" и "native-assets";
                // сравнение с "cache" не совпадало никогда, и экран уверял, что
                // словарь всегда собран из assets — даже когда он поднялся из
                // кэша за триста миллисекунд.
                String src = stats.source != null && stats.source.contains("cache")
                        ? getString(R.string.pred_status_source_cache)
                        : getString(R.string.pred_status_source_assets);
                sb.append("\n").append(locale.toUpperCase()).append(": ")
                  .append(getString(R.string.pred_status_loaded)).append("\n")
                  .append("  ").append(getString(R.string.pred_status_words)).append(": ").append(stats.wordCount).append("\n")
                  .append("  ").append(getString(R.string.pred_status_source)).append(": ").append(src).append("\n")
                  .append("  ").append(getString(R.string.pred_status_time)).append(": ").append(stats.timeMs).append(" ms");
            } else {
                // Never loaded
                boolean hasCache = WordDictionary.hasCacheFile(getApplicationContext(), locale,
                        k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_24_DICT_SIZE));
                sb.append("\n").append(locale.toUpperCase()).append(": ")
                  .append(getString(R.string.pred_status_not_loaded));
                if (hasCache) {
                    sb.append(" (").append(getString(R.string.pred_status_cache_available)).append(")");
                }
            }
            sb.append("\n");
        }

        tvDictStatus.setText(sb.toString().trim());
    }

    private void setupCache() {
        tvCacheStatus = (TextView) findViewById(R.id.tv_cache_status);
        refreshCacheStatus();

        Button btnClear = (Button) findViewById(R.id.btn_clear_cache);
        btnClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(ActivityPredictionSettings.this)
                    .setTitle(getString(R.string.pred_btn_clear_cache))
                    .setMessage(getString(R.string.pred_clear_cache_confirm))
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            WordDictionary.clearCacheFiles(getApplicationContext());
                            refreshCacheStatus();
                            Toast.makeText(getApplicationContext(),
                                    getString(R.string.pred_cache_cleared),
                                    Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(android.R.string.no, null)
                    .show();
            }
        });
    }

    private void refreshCacheStatus() {
        File nativeCacheDir = new File(getFilesDir(), "native_dict_cache");
        StringBuilder sb = new StringBuilder();

        // Имя файла кэша несёт предел размера: ru-full.ssnd, ru-150000.ssnd.
        // Раньше здесь искался ru.ssnd, поэтому экран всегда сообщал, что кэша
        // нет, — хотя собранный словарь лежал рядом.
        File[] cacheFiles = nativeCacheDir.listFiles();
        long total = 0;
        List<String> locales = LanguagePacks.availableLanguages(getApplicationContext());
        for (String locale : locales) {
            long size = 0;
            String limit = null;
            boolean fromPack = false;
            if (cacheFiles != null) {
                for (File f : cacheFiles) {
                    String name = f.getName();
                    if (!name.endsWith(".ssnd")) continue;
                    String base = name.substring(0, name.length() - 5);
                    // Распакованный из пакета индекс называется
                    // pack-<язык>-v<версия>-<язык>-<предел>: он занимает то же
                    // место, и не показывать его значило бы врать о диске.
                    boolean pack = base.startsWith("pack-" + locale + "-");
                    if (pack)
                        base = base.substring(base.indexOf('-', ("pack-" + locale + "-v").length()) + 1);
                    else if (!base.equals(locale) && !base.startsWith(locale + "-"))
                        continue;
                    size += f.length();
                    if (base.startsWith(locale + "-")) {
                        limit = base.substring(locale.length() + 1);
                        fromPack = pack;
                    }
                }
            }
            sb.append(locale.toUpperCase()).append(": ");
            if (size > 0) {
                sb.append(size / (1024 * 1024)).append(" MB");
                if (limit != null)
                    sb.append(" (").append(limit).append(fromPack ? ", pack" : "").append(")");
                total += size;
            } else {
                sb.append(getString(R.string.pred_cache_missing));
            }
            sb.append("\n");
        }

        // Итог по диску: решение об объёме словаря принимается по этой цифре,
        // а не по ощущениям — памяти индексы почти не занимают, а место занимают.
        sb.append("\n").append(getString(R.string.pred_cache_total)).append(": ")
          .append(total / (1024 * 1024)).append(" MB\n")
          .append(getString(R.string.pred_cache_hint));

        tvCacheStatus.setText(sb.toString().trim());
    }

    /**
     * Пояснение под выбором размера: почему после смены значения клавиатура
     * какое-то время думает. Слова «кеш словарей» ведут на страницу пакетов —
     * оттуда кеш можно получить готовым и не ждать вовсе.
     */
    private void SetupDictSizeNote() {
        TextView note = (TextView) findViewById(R.id.dict_size_note);
        if (note == null) return;
        String text = getString(R.string.pref_dict_size_note);
        String link = getString(R.string.pref_dict_size_note_link);
        int at = text.indexOf(link);
        if (at < 0) {
            note.setText(text);
            return;
        }
        SpannableString span = new SpannableString(text);
        span.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                startActivity(new Intent(ActivityPredictionSettings.this, ActivityLanguagePacks.class));
            }
        }, at, at + link.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        note.setText(span);
        note.setMovementMethod(LinkMovementMethod.getInstance());
    }


    /**
     * На какой язык переводить, когда набираешь по-английски. Пока установлен
     * один неанглийский язык, вопроса нет — пилюля скрыта; выбор появляется с
     * двух, где иначе направление задавал бы порядок переключения раскладок.
     */
    private void SetupTranslationTarget() {
        final View pill = findViewById(R.id.pill_translation_target);
        Spinner spinner = (Spinner) findViewById(R.id.spinner_translation_target);
        if (pill == null || spinner == null) return;

        // Первым пунктом — «как раньше»: язык той раскладки, на которую
        // переключаешься. Пока пользователь не выбрал язык явно, навязывать ему
        // первый попавшийся нельзя: с английской раскладки перевод уезжал в
        // язык, о котором он не просил.
        final List<String> langs = new ArrayList<>();
        langs.add("");
        for (String lang : LanguagePacks.availableLanguages(getApplicationContext()))
            if (!LanguagePacks.BUILTIN_LANGUAGE.equals(lang))
                langs.add(lang);
        if (langs.size() < 3) {
            pill.setVisibility(View.GONE);
            return;
        }
        pill.setVisibility(View.VISIBLE);

        List<String> titles = new ArrayList<>();
        for (String lang : langs) {
            if (lang.isEmpty())
                titles.add(getString(R.string.pref_translation_target_auto));
            else
                titles.add(new java.util.Locale(lang).getDisplayLanguage() + "  (" + lang + ")");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, titles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        String saved = k12KbSettings.GetStringValue(k12KbSettings.APP_PREFERENCES_30_TRANSLATION_TARGET);
        int pos = langs.indexOf(saved == null ? "" : saved);
        if (pos < 0) pos = 0;
        spinner.setSelection(pos);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                k12KbSettings.SetStringValue(k12KbSettings.APP_PREFERENCES_30_TRANSLATION_TARGET,
                        langs.get(position));
            }

            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

}
