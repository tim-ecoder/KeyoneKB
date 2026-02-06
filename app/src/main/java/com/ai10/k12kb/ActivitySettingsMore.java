package com.ai10.k12kb;

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.ai10.k12kb.ActivitySettings.REQUEST_PERMISSION_CODE;
import static com.ai10.k12kb.FileJsonUtils.*;
import static com.ai10.k12kb.KeyboardLayoutManager.Instance;
import static com.ai10.k12kb.K12KbSettings.*;

public class ActivitySettingsMore extends Activity {
    private K12KbSettings k12KbSettings;

    Button btSave;
    Button btSavePluginData;
    Button btn_sys_kb_accessibility_setting;

    private RelativeLayout layout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        k12KbSettings = K12KbSettings.Get(getSharedPreferences(K12KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));

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

            k12KbSettings.CheckSettingOrSetDefault(jsPatch, k12KbSettings.JS_PATCH_IS_ENABLED_DEFAULT);
            boolean enabled = k12KbSettings.GetBooleanValue(jsPatch);
            jsPatchSwitch.setChecked(enabled);
            jsPatchSwitch.setText(jsPatch);

            jsPatchSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    k12KbSettings.SetBooleanValue(jsPatch, isChecked);
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

        if(K12KbAccessibilityService.Instance == null) {
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

        if(K12KbAccessibilityService.Instance == null) {
            return;
        }

        if(!btSavePluginData.getText().toString().equals("SAVED")) {


            SearchClickPlugin.SearchClickPluginData data = new SearchClickPlugin.SearchClickPluginData();

            data.DefaultSearchWords = K12KbAccessibilityService.Instance.DefaultSearchWords;

            SaveSearchClickPluginData(K12KbAccessibilityService.Instance.DefaultSearchWords, K12KbAccessibilityService.Instance.searchClickPlugins, data.SearchPlugins);
            SaveSearchClickPluginData(K12KbAccessibilityService.Instance.DefaultSearchWords, K12KbAccessibilityService.Instance.clickerPlugins, data.ClickerPlugins);

            FileJsonUtils.SerializeToFile(data, RES_PLUGIN_DATA + JsonFileExt);
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

    public void saveAssetFiles(String path) {
        String[] list;

        try {
            list = getAssets().list(path);
            if (list.length > 0) {
                for (String file : list) {
                    if (file.indexOf(".") < 0) { // <<-- check if filename has a . then it is a file - hopefully directory names dont have .

                        if (path.equals("")) {
                            FileJsonUtils.CheckFoldersAndCreateJsPatches(path);
                            saveAssetFiles(file); // <<-- To get subdirectory files and directories list and check
                        } else {
                            String subFile = path + "/" + file;
                            FileJsonUtils.CheckFoldersAndCreateJsPatches(subFile);

                            saveAssetFiles(subFile); // <<-- For Multiple level subdirectories
                        }
                    } else {
                        String subFile = path + "/" + file;
                        FileJsonUtils.SaveAssetToFile(subFile, PATH, file, this);
                    }
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void SaveResFiles() {
        String path = "NOT SAVED";

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_CODE);
            return;
        }

        path = FileJsonUtils.SaveJsonResToFile(RES_KEYBOARD_LAYOUTS, getApplicationContext());
        path = FileJsonUtils.SaveJsonResToFile(RES_KEYBOARD_CORE, getApplicationContext());
        path = FileJsonUtils.SaveJsonResToFile(K12KbIME.Instance.keyboard_mechanics_res, getApplicationContext());
        path = FileJsonUtils.SaveJsonResToFile(K12KbAccessibilityService.K12KbAccServiceOptions.ResName, getApplicationContext());
        for (KeyboardLayout keyboardLayout: Instance.KeyboardLayoutList) {
            path = FileJsonUtils.SaveJsonResToFile(keyboardLayout.Resources.KeyboardMapping, getApplicationContext());
            path = FileJsonUtils.SaveJsonResToFile(keyboardLayout.AltModeLayout, getApplicationContext());
        }

        saveAssetFiles(JsPatchesAssetFolder);

        btSave.setText(path);

    }

}
