package com.sateda.keyonekb2;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;

import static com.sateda.keyonekb2.KeyboardLayoutManager.Instance;

public class SettingsMoreActivity extends Activity {
    private KbSettings kbSettings;

    Button btSave;
    Button btSavePluginData;
    //Button btAddPlugin;

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

        btSavePluginData = (Button)findViewById(R.id.pref_more_bt_save_search_plugins);

        if(KeyoneKb2AccessibilityService.Instance == null) {
            btSavePluginData.setText("NEED ENABLE ACCESSIBILITY");
            btSavePluginData.setEnabled(false);
        } else {
            btSavePluginData.setEnabled(true);
            btSavePluginData.setText("SAVE PLUGIN DATA");

            btSavePluginData.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) { SavePluginData(); }
            });

        }


        if(KeyoneIME.Instance != null && KeyoneIME.Instance.PackageHistory.size() > 0) {
            int i = 0;
            for (String packageName : KeyoneIME.Instance.PackageHistory) {
                i++;
                if(i == 1) InitAddPluginButton(packageName, findViewById(R.id.pref_more_bt_add_plugin1));
                else if(i == 2) InitAddPluginButton(packageName, findViewById(R.id.pref_more_bt_add_plugin2));
                else if(i == 3) InitAddPluginButton(packageName, findViewById(R.id.pref_more_bt_add_plugin3));
                continue;
            }
        }
        Button btClear = (Button)findViewById(R.id.pref_more_bt_clear);
        btClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (KeyoneKb2AccessibilityService.SearchHackPlugin plugin : KeyoneKb2AccessibilityService.Instance.searchHackPlugins) {
                    plugin.setId("");
                    KeyoneKb2AccessibilityService.Instance.ClearFromSettings(plugin);
                }
                SetTextPluginData();
            }
        });


        SetTextPluginData();


    }

    private void InitAddPluginButton(String packageName, Button btAddPlugin) {
        btAddPlugin.setText("ADD PLUGIN: " + packageName);
        btAddPlugin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (KeyoneIME.Instance == null) return;
                if (packageName.isEmpty()) return;
                if (KeyoneKb2AccessibilityService.Instance == null) return;
                KeyoneKb2AccessibilityService.SearchHackPlugin sp = new KeyoneKb2AccessibilityService.SearchHackPlugin(packageName);
                sp.setEvents(KeyoneKb2AccessibilityService.STD_EVENTS | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
                KeyoneKb2AccessibilityService.Instance.searchHackPlugins.add(sp);
                btAddPlugin.setText("ADDED");
            }
        });
    }

    private void SavePluginData() {

        if(KeyoneKb2AccessibilityService.Instance == null) {
            return;
        }

        if(!btSavePluginData.getText().toString().equals("SAVED")) {


            KeyoneKb2PluginData data = new KeyoneKb2PluginData();

            data.DefaultSearchWords = KeyoneKb2AccessibilityService.Instance.DefaultSearchWords;

            for (KeyoneKb2AccessibilityService.SearchHackPlugin plugin : KeyoneKb2AccessibilityService.Instance.searchHackPlugins) {
                KeyoneKb2PluginData.SearchPluginData pluginData = new KeyoneKb2PluginData.SearchPluginData();
                pluginData.PackageName = plugin.getPackageName();
                pluginData.SearchFieldId = plugin.getId();

                if(pluginData.SearchFieldId == null || pluginData.SearchFieldId.isEmpty()) {


                    if(plugin.DynamicSearchMethod != null && !plugin.DynamicSearchMethod.isEmpty()) {
                        pluginData.DynamicSearchMethod = plugin.DynamicSearchMethod;
                    } else {

                        pluginData.DynamicSearchMethod = new ArrayList<>();
                        for (String searchWord : data.DefaultSearchWords) {

                            KeyoneKb2PluginData.DynamicSearchMethod d1 = new KeyoneKb2PluginData.DynamicSearchMethod();
                            d1.DynamicSearchMethodFunction = KeyoneKb2PluginData.DynamicSearchMethodFunction.FindFirstByTextRecursive;
                            d1.ContainsString = searchWord;
                            pluginData.DynamicSearchMethod.add(d1);

                            d1 = new KeyoneKb2PluginData.DynamicSearchMethod();
                            d1.DynamicSearchMethodFunction = KeyoneKb2PluginData.DynamicSearchMethodFunction.FindAccessibilityNodeInfosByText;
                            d1.ContainsString = searchWord;
                            pluginData.DynamicSearchMethod.add(d1);
                        }
                    }
                }



                if (plugin._converter != null) {
                    pluginData.CustomClickAdapterClickParent = true;
                }

                if ((plugin._events & AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                    pluginData.AdditionalEventTypeTypeWindowContentChanged = true;
                }

                pluginData.WaitBeforeSendCharMs = plugin.WaitBeforeSendChar;

                data.SearchPlugins.add(pluginData);
            }

                FileJsonUtils.SerializeToFile(data, "plugin_data.json");
                btSavePluginData.setText(FileJsonUtils.PATH_DEF);

        }

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
