package com.sateda.keyonekb2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import static com.sateda.keyonekb2.KeyboardLayoutManager.Instance;

public class ActivitySettingsMore extends Activity {
    private KeyoneKb2Settings keyoneKb2Settings;

    Button btSave;
    Button btSavePluginData;
    Button btn_sys_kb_accessibility_setting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keyoneKb2Settings = KeyoneKb2Settings.Get(getSharedPreferences(KeyoneKb2Settings.APP_PREFERENCES, Context.MODE_PRIVATE));
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


        Button btClear = (Button)findViewById(R.id.pref_more_bt_clear);
        btClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (SearchClickPlugin plugin : KeyoneKb2AccessibilityService.Instance.searchClickPlugins) {
                    plugin.setId("");
                    KeyoneKb2AccessibilityService.Instance.ClearFromSettings(plugin);
                }
                SetTextPluginData();
            }
        });

        btn_sys_kb_accessibility_setting = (Button) findViewById(R.id.btn_sys_kb_accessibility_setting);
        btn_sys_kb_accessibility_setting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                getApplicationContext().startActivity(intent);
            }
        });

        RedrawViewData();


    }

    @Override
    public void onResume(){
        super.onResume();
        RedrawViewData();
    }

    private boolean isAccessibilityEnabled() {
        String accEnabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return accEnabled != null && accEnabled.contains(getPackageName());
    }

    private boolean UpdateAccessibilityButton() {
        boolean accEnabledFlag = isAccessibilityEnabled();
        if(accEnabledFlag) {
            btn_sys_kb_accessibility_setting.setEnabled(false);
            btn_sys_kb_accessibility_setting.setText(R.string.main_btn_sys_kb_accessibility_setting_disabled);
        } else {
            btn_sys_kb_accessibility_setting.setEnabled(true);
            btn_sys_kb_accessibility_setting.setText(R.string.main_btn_sys_kb_accessibility_setting_enabled);
        }
        return accEnabledFlag;
    }

    private void RedrawViewData() {

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
                if(i == 1) InitAddPluginButton(packageName, findViewById(R.id.pref_more_bt_add_plugin3));
                else if(i == 2) InitAddPluginButton(packageName, findViewById(R.id.pref_more_bt_add_plugin2));
                else if(i == 3) InitAddPluginButton(packageName, findViewById(R.id.pref_more_bt_add_plugin1));
            }
        }

        UpdateAccessibilityButton();
        SetTextPluginData();
    }

    private void InitAddPluginButton(String packageName, Button btAddPlugin) {
        if(packageName == null || packageName.isEmpty())
            return;
        boolean isAdded = false;
        for(SearchClickPlugin searchClickPlugin : KeyoneKb2AccessibilityService.Instance.searchClickPlugins) {
            if(searchClickPlugin.getPackageName().equals(packageName)) {
                isAdded = true;
                break;
            }
        }

        if(!isAdded) {
            btAddPlugin.setEnabled(true);
            btAddPlugin.setText("ADD PLUGIN: " + packageName);
            btAddPlugin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (KeyoneIME.Instance == null) return;
                    if (packageName.isEmpty()) return;
                    if (KeyoneKb2AccessibilityService.Instance == null) return;
                    SearchClickPlugin sp = new SearchClickPlugin(packageName);
                    sp.setEvents(KeyoneKb2AccessibilityService.STD_EVENTS | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
                    KeyoneKb2AccessibilityService.TEMP_ADDED_SEARCH_CLICK_PLUGINS.add(sp);
                    btAddPlugin.setText("ADDED: " + packageName);
                    btAddPlugin.setEnabled(false);
                    ShowToast("Application added. Accessibility service automatic stopped. Need start manually.");
                    KeyoneKb2AccessibilityService.Instance.StopService();
                    UpdateAccessibilityButton();
                }
            });
        } else {
            btAddPlugin.setText("PLUGIN EXISTS: " + packageName);
            btAddPlugin.setEnabled(false);
            btAddPlugin.setOnClickListener(null);
        }
    }

    private void ShowToast(String text) {
        Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
        toast.show();
    }


    private void SavePluginData() {

        if(KeyoneKb2AccessibilityService.Instance == null) {
            return;
        }

        if(!btSavePluginData.getText().toString().equals("SAVED")) {


            SearchClickPlugin.SearchClickPluginData data = new SearchClickPlugin.SearchClickPluginData();

            data.DefaultSearchWords = KeyoneKb2AccessibilityService.Instance.DefaultSearchWords;

            for (SearchClickPlugin plugin : KeyoneKb2AccessibilityService.Instance.searchClickPlugins) {
                SearchClickPlugin.SearchClickPluginData.SearchPluginData pluginData = new SearchClickPlugin.SearchClickPluginData.SearchPluginData();
                pluginData.PackageName = plugin.getPackageName();
                pluginData.SearchFieldId = plugin.getId();

                if(pluginData.SearchFieldId == null || pluginData.SearchFieldId.isEmpty()) {


                    if(plugin.DynamicSearchMethod != null && !plugin.DynamicSearchMethod.isEmpty()) {
                        pluginData.DynamicSearchMethod = plugin.DynamicSearchMethod;
                    } else {

                        pluginData.DynamicSearchMethod = new ArrayList<>();
                        for (String searchWord : data.DefaultSearchWords) {

                            SearchClickPlugin.SearchClickPluginData.DynamicSearchMethod d1 = new SearchClickPlugin.SearchClickPluginData.DynamicSearchMethod();
                            d1.DynamicSearchMethodFunction = SearchClickPlugin.SearchClickPluginData.DynamicSearchMethodFunction.FindFirstByTextRecursive;
                            d1.ContainsString = searchWord;
                            pluginData.DynamicSearchMethod.add(d1);

                            d1 = new SearchClickPlugin.SearchClickPluginData.DynamicSearchMethod();
                            d1.DynamicSearchMethodFunction = SearchClickPlugin.SearchClickPluginData.DynamicSearchMethodFunction.FindAccessibilityNodeInfosByText;
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
        for(SearchClickPlugin plugin : KeyoneKb2AccessibilityService.Instance.searchClickPlugins) {
            String value = plugin.getId();
            if(value.isEmpty()) {
                if(plugin.DynamicSearchMethod != null && plugin.DynamicSearchMethod.size() == 1)
                    value = getString(R.string.pref_more_tv_single_plugin_data2)+
                            " SearchFunction: "+plugin.DynamicSearchMethod.get(0).DynamicSearchMethodFunction+
                            " SearchWord: "+plugin.DynamicSearchMethod.get(0).ContainsString;
                else
                    value = getString(R.string.pref_more_tv_single_plugin_data);
            }
            text_data += String.format("Application:\n%s\nResourceId:\n%s\n\n", plugin.getPackageName(), value);
        }

        textView.append(text_data);
    }


    private void SaveResFiles() {
        String path = "NOT SAVED";
        path = FileJsonUtils.SaveJsonResToFile(getResources().getResourceEntryName(R.raw.keyboard_layouts), getApplicationContext());
        path = FileJsonUtils.SaveJsonResToFile(getResources().getResourceEntryName(R.raw.keyboard_core), getApplicationContext());
        path = FileJsonUtils.SaveJsonResToFile(KeyoneIME.Instance.keyboard_mechanics_res, getApplicationContext());
        for (KeyboardLayout keyboardLayout: Instance.KeyboardLayoutList) {
            path = FileJsonUtils.SaveJsonResToFile(keyboardLayout.Resources.KeyboardMapping, getApplicationContext());
            path = FileJsonUtils.SaveJsonResToFile(keyboardLayout.AltModeLayout, getApplicationContext());
        }

        btSave.setText(path);

    }

}
