package com.sateda.keyonekb2;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.util.TypedValue;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static com.sateda.keyonekb2.ActivitySettings.REQUEST_PERMISSION_CODE;
import static com.sateda.keyonekb2.KeyboardLayoutManager.Instance;

public class ActivitySettingsMore extends Activity {
    private KeyoneKb2Settings keyoneKb2Settings;

    Button btSave;
    Button btSavePluginData;
    Button btn_sys_kb_accessibility_setting;

    private RelativeLayout layout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keyoneKb2Settings = KeyoneKb2Settings.Get(getSharedPreferences(KeyoneKb2Settings.APP_PREFERENCES, Context.MODE_PRIVATE));
        FileJsonUtils.Initialize(getApplicationContext());

        setContentView(R.layout.activity_more_settings);

        btSave = (Button)findViewById(R.id.pref_more_bt_save);
        btSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SaveResFiles();
            }
        });

        btSavePluginData = (Button)findViewById(R.id.pref_more_bt_save_search_plugins);

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

        List<String> patches = new ArrayList<>();
        for (List<String> value: FileJsonUtils.JsPatchesMap.values())
        {
            patches.addAll(value);
        }

        if(patches.size() == 0)
            return;

        layout = (RelativeLayout) findViewById(R.id.activity_more_settings);

        Switch defaultJsPatch = (Switch) findViewById(R.id.default_js_patch);
        defaultJsPatch.setVisibility(View.VISIBLE);

        int prevId = 0;

        for (String jsPatch: patches ) {
            Switch jsPatchSwitch;
            //Первый язык будет по умолчанию всегда активирован
            //Плюс на уровне загрузчика клав, будет хард код, чтобы первая клава всегда была сразу после установки
            if(prevId == 0) {
                jsPatchSwitch = defaultJsPatch;
                RelativeLayout.LayoutParams llp = (RelativeLayout.LayoutParams)defaultJsPatch.getLayoutParams();
                RelativeLayout.LayoutParams llp2 = new RelativeLayout.LayoutParams(llp);
                jsPatchSwitch.setLayoutParams(llp2);
                prevId = jsPatchSwitch.getId();


            } else {
                jsPatchSwitch = new Switch(this);
                RelativeLayout.LayoutParams llp = (RelativeLayout.LayoutParams)defaultJsPatch.getLayoutParams();
                RelativeLayout.LayoutParams llp2 = new RelativeLayout.LayoutParams(llp);
                jsPatchSwitch.setLayoutParams(llp2);

                llp2.addRule(RelativeLayout.BELOW, prevId);
                prevId = View.generateViewId();
                jsPatchSwitch.setId(prevId);
                jsPatchSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultJsPatch.getTextSize());
                layout.addView(jsPatchSwitch);

            }

            keyoneKb2Settings.CheckSettingOrSetDefault(jsPatch, keyoneKb2Settings.JS_PATCH_IS_ENABLED_DEFAULT);
            boolean enabled = keyoneKb2Settings.GetBooleanValue(jsPatch);
            jsPatchSwitch.setChecked(enabled);
            jsPatchSwitch.setText(jsPatch);

            jsPatchSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    keyoneKb2Settings.SetBooleanValue(jsPatch, isChecked);
                }
            });

        }


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

        UpdateAccessibilityButton();
    }

    private void SavePluginData() {

        if(KeyoneKb2AccessibilityService.Instance == null) {
            return;
        }

        if(!btSavePluginData.getText().toString().equals("SAVED")) {


            SearchClickPlugin.SearchClickPluginData data = new SearchClickPlugin.SearchClickPluginData();

            data.DefaultSearchWords = KeyoneKb2AccessibilityService.Instance.DefaultSearchWords;

            SaveSearchClickPluginData(KeyoneKb2AccessibilityService.Instance.DefaultSearchWords, KeyoneKb2AccessibilityService.Instance.searchClickPlugins, data.SearchPlugins);
            SaveSearchClickPluginData(KeyoneKb2AccessibilityService.Instance.DefaultSearchWords, KeyoneKb2AccessibilityService.Instance.clickerPlugins, data.ClickerPlugins);

            FileJsonUtils.SerializeToFile(data, "plugin_data.json");
                btSavePluginData.setText(FileJsonUtils.PATH_DEF);

        }

    }

    private void SaveSearchClickPluginData(ArrayList<String> defaultSearchWords, ArrayList<SearchClickPlugin> _searchClickPlugins, ArrayList<SearchClickPlugin.SearchClickPluginData.SearchPluginData> clickerPluginDataArray) {
        for (SearchClickPlugin plugin : _searchClickPlugins) {
            SearchClickPlugin.SearchClickPluginData.SearchPluginData pluginData = new SearchClickPlugin.SearchClickPluginData.SearchPluginData();
            pluginData.PackageName = plugin.getPartiallyPackageName();
            pluginData.SearchFieldId = plugin.getId();

            if(pluginData.SearchFieldId == null || pluginData.SearchFieldId.isEmpty()) {


                if(plugin.DynamicSearchMethod != null && !plugin.DynamicSearchMethod.isEmpty()) {
                    pluginData.DynamicSearchMethod = plugin.DynamicSearchMethod;
                } else {

                    pluginData.DynamicSearchMethod = new ArrayList<>();
                    for (String searchWord : defaultSearchWords) {

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

            clickerPluginDataArray.add(pluginData);
        }
    }

    private void SaveResFiles() {
        String path = "NOT SAVED";

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_CODE);
            return;
        }

        path = FileJsonUtils.SaveJsonResToFile(getResources().getResourceEntryName(R.raw.keyboard_layouts), getApplicationContext());
        path = FileJsonUtils.SaveJsonResToFile(getResources().getResourceEntryName(R.raw.keyboard_core), getApplicationContext());
        path = FileJsonUtils.SaveJsonResToFile(KeyoneIME.Instance.keyboard_mechanics_res, getApplicationContext());
        path = FileJsonUtils.SaveJsonResToFile(KeyoneKb2AccessibilityService.KeyoneKb2AccServiceOptions.ResName, getApplicationContext());
        for (KeyboardLayout keyboardLayout: Instance.KeyboardLayoutList) {
            path = FileJsonUtils.SaveJsonResToFile(keyboardLayout.Resources.KeyboardMapping, getApplicationContext());
            path = FileJsonUtils.SaveJsonResToFile(keyboardLayout.AltModeLayout, getApplicationContext());
        }

        btSave.setText(path);

    }

}
