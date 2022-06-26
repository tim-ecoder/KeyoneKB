package com.sateda.keyonekb2;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import static com.sateda.keyonekb2.KeyboardLayoutManager.Instance;

public class SettingsMoreActivity extends Activity {
    private KbSettings kbSettings;

    Button btSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        kbSettings = KbSettings.Get(getSharedPreferences(KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));
        FileJsonUtils.Initialize(this.getPackageName(), getApplicationContext());

        setContentView(R.layout.activity_more_settings);

        btSave = (Button)findViewById(R.id.pref_more_bt_save);
        btSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SaveResFiles();
            }
        });

        Button btClear = (Button)findViewById(R.id.pref_more_bt_clear);
        btClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (KeyoneKb2AccessibilityService.SearchHackPlugin plugin : KeyoneKb2AccessibilityService.Instance.searchHackPlugins) {
                    plugin.setId("");
                }
                SetTextPluginData();
            }
        });


        SetTextPluginData();


    }

    private void SetTextPluginData() {
        TextView textView = (TextView)findViewById(R.id.tv_search_plugins_data);
        textView.setText(R.string.pref_more_tv_search_plugins_comment);
        if(KeyoneKb2AccessibilityService.Instance == null) {
            textView.append(getString(R.string.pref_more_tv_search_plugins_comment_not_active));
            return;
        }
        String text_data = "";
        for(KeyoneKb2AccessibilityService.SearchHackPlugin plugin : KeyoneKb2AccessibilityService.Instance.searchHackPlugins) {
            String value = plugin.getId();
            if(value.isEmpty())
                value = getString(R.string.pref_more_tv_single_plugin_data);
            text_data += String.format("Application:\n%s\nResourceId:\n%s\n\n", plugin.getPackageName(), value);
        }

        textView.append(text_data);
    }


    private void SaveResFiles() {
        String path = "NOT SAVED";
        path = FileJsonUtils.SaveJsonResToFile(getResources().getResourceEntryName(R.raw.keyboard_layouts), getApplicationContext());
        //FileJsonUtils.SerializeToFile(KeyoneIME.allLayouts, "keyboard_layouts.json");
        for (KeyboardLayout keyboardLayout: Instance.KeyboardLayoutList) {
            path = FileJsonUtils.SaveJsonResToFile(keyboardLayout.Resources.KeyboardMapping, getApplicationContext());
            path = FileJsonUtils.SaveJsonResToFile(keyboardLayout.AltModeLayout, getApplicationContext());
        }

        btSave.setText(path);

    }

}