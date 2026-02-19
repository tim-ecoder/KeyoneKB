package com.ai10.k12kb;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
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

import com.ai10.k12kb.prediction.NativeTranslationDictionary;
import com.ai10.k12kb.prediction.WordDictionary;
import com.ai10.k12kb.prediction.WordPredictor;

import java.io.File;
import java.util.HashMap;
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

        // Engine spinner
        final Spinner spinnerEngine = (Spinner) findViewById(R.id.spinner_prediction_engine);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.pref_prediction_engine_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEngine.setAdapter(adapter);
        // Map engine constant (1=N-gram, 2=NativeSymSpell) to spinner position (0, 1)
        final int engineFromPref = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_19_PREDICTION_ENGINE);
        int spinnerPos = (engineFromPref == WordPredictor.ENGINE_NGRAM) ? 0 : 1;
        spinnerEngine.setSelection(spinnerPos);
        spinnerEngine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Map spinner position back to engine constant: 0→1, 1→2
                int engineConst = (position == 0) ? WordPredictor.ENGINE_NGRAM : WordPredictor.ENGINE_NATIVE_SYMSPELL;
                k12KbSettings.SetIntValue(k12KbSettings.APP_PREFERENCES_19_PREDICTION_ENGINE, engineConst);
            }
            public void onNothingSelected(AdapterView<?> parent) {
                int pos = (engineFromPref == WordPredictor.ENGINE_NGRAM) ? 0 : 1;
                spinnerEngine.setSelection(pos);
            }
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
        final int[] transDictSizeValues = {35000, 100000, 200000, 0};
        final Spinner spinnerTransDictSize = (Spinner) findViewById(R.id.spinner_trans_dict_size);
        ArrayAdapter<CharSequence> transDictAdapter = ArrayAdapter.createFromResource(
                this, R.array.pref_trans_dict_size_array, android.R.layout.simple_spinner_item);
        transDictAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTransDictSize.setAdapter(transDictAdapter);
        int transDictSizeInit = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_25_TRANS_DICT_SIZE);
        int transDictSizePos = 0;
        for (int i = 0; i < transDictSizeValues.length; i++) {
            if (transDictSizeValues[i] == transDictSizeInit) { transDictSizePos = i; break; }
        }
        spinnerTransDictSize.setSelection(transDictSizePos);
        spinnerTransDictSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int newSize = transDictSizeValues[position];
                int currentSize = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_25_TRANS_DICT_SIZE);
                if (newSize == currentSize) return;
                k12KbSettings.SetIntValue(k12KbSettings.APP_PREFERENCES_25_TRANS_DICT_SIZE, newSize);
                NativeTranslationDictionary.clearTrimmedCaches(getApplicationContext());
                refreshTranslationStatus();
                refreshCacheStatus();
                Toast.makeText(getApplicationContext(),
                        getString(R.string.pred_trans_dict_size_changed),
                        Toast.LENGTH_LONG).show();
            }
            public void onNothingSelected(AdapterView<?> parent) {
                spinnerTransDictSize.setSelection(0);
            }
        });

        tvTranslationStatus = (TextView) findViewById(R.id.tv_translation_status);
    }

    private void refreshTranslationStatus() {
        // Pre-fetch string resources on the UI thread (cannot access from background)
        final String strWords = getString(R.string.pred_translation_words);
        final String strPhrases = getString(R.string.pred_translation_phrases);
        final String strNotFound = getString(R.string.pred_translation_not_found);
        final String strExternal = getString(R.string.pred_translation_external);
        final int maxEntries = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_25_TRANS_DICT_SIZE);

        Executors.newSingleThreadExecutor().execute(() -> {
            StringBuilder sb = new StringBuilder();
            // Check which dictionary files exist in assets
            String[] pairs = {"ru_en", "en_ru"};
            for (String pair : pairs) {
                String assetName = "dict/" + pair + ".tsv";
                try {
                    java.io.InputStream is = getAssets().open(assetName);
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
                    boolean trimmed = maxEntries > 0 && maxEntries < totalEntries;
                    sb.append(pair.replace("_", " \u2192 ").toUpperCase()).append(": ");
                    if (trimmed) {
                        sb.append(maxEntries).append(" / ").append(totalEntries)
                          .append(" (").append(strWords).append(" + ").append(strPhrases).append(")");
                    } else {
                        sb.append(wordCount).append(" ").append(strWords);
                        if (phraseCount > 0) {
                            sb.append(" + ").append(phraseCount).append(" ").append(strPhrases);
                        }
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

        // Engine mode
        int engineMode = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_19_PREDICTION_ENGINE);
        String engineName;
        if (engineMode == WordPredictor.ENGINE_NGRAM) engineName = "N-gram";
        else engineName = "Native SymSpell";
        sb.append(getString(R.string.pred_status_engine)).append(": ").append(engineName).append("\n");

        // Per-locale stats
        HashMap<String, WordDictionary.LoadStats> allStats = WordDictionary.getAllLoadStats();
        String[] locales = {"en", "ru"};
        for (String locale : locales) {
            WordDictionary.LoadStats stats = allStats.get(locale);
            if (stats != null && "loading".equals(stats.source)) {
                // Loading in progress
                boolean hasCache = WordDictionary.hasCacheFile(getApplicationContext(), locale);
                sb.append("\n").append(locale.toUpperCase()).append(": ")
                  .append(getString(R.string.pred_status_loading));
                if (hasCache) {
                    sb.append(" (").append(getString(R.string.pred_status_source_cache)).append(")");
                } else {
                    sb.append(" (").append(getString(R.string.pred_status_source_assets)).append(")");
                }
            } else if (stats != null) {
                String src = "cache".equals(stats.source)
                        ? getString(R.string.pred_status_source_cache)
                        : getString(R.string.pred_status_source_assets);
                sb.append("\n").append(locale.toUpperCase()).append(": ")
                  .append(getString(R.string.pred_status_loaded)).append("\n")
                  .append("  ").append(getString(R.string.pred_status_words)).append(": ").append(stats.wordCount).append("\n")
                  .append("  ").append(getString(R.string.pred_status_source)).append(": ").append(src).append("\n")
                  .append("  ").append(getString(R.string.pred_status_time)).append(": ").append(stats.timeMs).append(" ms");
            } else {
                // Never loaded
                boolean hasCache = WordDictionary.hasCacheFile(getApplicationContext(), locale);
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

        String[] locales = {"en", "ru"};
        for (String locale : locales) {
            File nf = new File(nativeCacheDir, locale + ".ssnd");
            if (nf.exists()) {
                long sizeKb = nf.length() / 1024;
                sb.append(locale.toUpperCase()).append(": ")
                  .append(sizeKb).append(" KB");
            } else {
                sb.append(locale.toUpperCase()).append(": ")
                  .append(getString(R.string.pred_cache_missing));
            }
            sb.append("\n");
        }

        tvCacheStatus.setText(sb.toString().trim());
    }
}
