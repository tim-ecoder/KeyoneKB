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

import com.ai10.k12kb.prediction.WordDictionary;
import com.ai10.k12kb.prediction.WordPredictor;

import java.io.File;
import java.util.HashMap;

public class ActivityPredictionSettings extends Activity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    private K12KbSettings k12KbSettings;
    private TextView tvDictStatus;
    private TextView tvCacheStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        k12KbSettings = K12KbSettings.Get(getSharedPreferences(K12KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));
        if (k12KbSettings.isDarkTheme()) {
            setTheme(R.style.AppTheme_Dark);
        }
        setContentView(R.layout.activity_prediction_settings);

        setupPredictionSettings();
        setupStatus();
        setupCache();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshCacheStatus();
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
        final int engineFromPref = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_19_PREDICTION_ENGINE);
        spinnerEngine.setSelection(engineFromPref);
        spinnerEngine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                k12KbSettings.SetIntValue(k12KbSettings.APP_PREFERENCES_19_PREDICTION_ENGINE, position);
            }
            public void onNothingSelected(AdapterView<?> parent) {
                spinnerEngine.setSelection(engineFromPref);
            }
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
        String engineName = (engineMode == WordPredictor.ENGINE_NGRAM) ? "N-gram" : "SymSpell";
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
        File cacheDir = new File(getFilesDir(), "dict_cache");
        StringBuilder sb = new StringBuilder();

        String[] locales = {"en", "ru"};
        for (String locale : locales) {
            File f = new File(cacheDir, locale + ".bin");
            if (f.exists()) {
                long sizeKb = f.length() / 1024;
                sb.append(locale.toUpperCase()).append(": ")
                  .append(sizeKb).append(" KB\n");
            } else {
                sb.append(locale.toUpperCase()).append(": ")
                  .append(getString(R.string.pred_cache_missing)).append("\n");
            }
        }

        tvCacheStatus.setText(sb.toString().trim());
    }
}
