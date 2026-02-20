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

import android.view.Gravity;
import android.view.ViewGroup;

import android.net.Uri;
import android.support.v4.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ai10.k12kb.ActivitySettings.REQUEST_PERMISSION_CODE;
import static com.ai10.k12kb.FileJsonUtils.*;
import static com.ai10.k12kb.KeyboardLayoutManager.Instance;
import static com.ai10.k12kb.K12KbSettings.*;

public class ActivitySettingsMore extends Activity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    private K12KbSettings k12KbSettings;

    Button btSave;
    Button btSavePluginData;
    Button btn_sys_kb_accessibility_setting;

    private LinearLayout layout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        k12KbSettings = K12KbSettings.Get(getSharedPreferences(K12KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));
        if (k12KbSettings.isDarkTheme()) {
            setTheme(R.style.AppTheme_Dark);
        }

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

        if(FileJsonUtils.JsPatchesMap.isEmpty())
            return;

        // Check if any group has actual patches
        boolean hasAnyPatches = false;
        for (List<String> pList : FileJsonUtils.JsPatchesMap.values()) {
            if (!pList.isEmpty()) { hasAnyPatches = true; break; }
        }
        if (!hasAnyPatches)
            return;

        layout = (LinearLayout) findViewById(R.id.activity_more_settings);
        LinearLayout containerJsPatches = (LinearLayout) findViewById(R.id.container_js_patches);

        Switch defaultJsPatch = (Switch) findViewById(R.id.default_js_patch);
        defaultJsPatch.setVisibility(View.VISIBLE);

        // Resolve theme colors once
        TypedValue _tv = new TypedValue();
        getTheme().resolveAttribute(R.attr.textPrimaryColor, _tv, true);
        int textColor = _tv.data;
        TypedValue _tvSec = new TypedValue();
        getTheme().resolveAttribute(R.attr.sectionHeaderColor, _tvSec, true);
        int sectionColor = _tvSec.data;

        boolean isFirst = true;

        for (Map.Entry<String, List<String>> entry : FileJsonUtils.JsPatchesMap.entrySet()) {
            String groupName = entry.getKey();
            List<String> patches = entry.getValue();
            if (patches.isEmpty()) continue;

            // Add group sub-header
            TextView groupHeader = new TextView(this);
            groupHeader.setText(groupName);
            groupHeader.setTextColor(sectionColor);
            groupHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            groupHeader.setAllCaps(true);
            int padStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28, getResources().getDisplayMetrics());
            int padEnd = padStart;
            int padTop = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
            int padBottom = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
            groupHeader.setPadding(padStart, padTop, padEnd, padBottom);
            containerJsPatches.addView(groupHeader);

            // Strip prefix: "keyboard_mechanics." from "keyboard_mechanics.xxx.js"
            String prefix = groupName + ".";

            for (final String jsPatch : patches) {
                // Build pill container: horizontal LinearLayout with TextView + Switch
                LinearLayout pillRow = new LinearLayout(this);
                pillRow.setOrientation(LinearLayout.HORIZONTAL);
                pillRow.setGravity(Gravity.CENTER_VERTICAL);
                pillRow.setBackgroundResource(R.drawable.bg_item_pill);

                LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                int marginH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics());
                int marginT = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
                pillParams.setMargins(marginH, marginT, marginH, 0);
                pillRow.setLayoutParams(pillParams);

                int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics());
                int padH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
                pillRow.setPadding(padH, pad, padH, pad);

                // Text label — clickable, opens the JS file
                TextView pillText = new TextView(this);
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                pillText.setLayoutParams(textParams);
                pillText.setTextColor(textColor);
                pillText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);

                // Use @name description from JS file, or fall back to filename without prefix
                String description = FileJsonUtils.JsPatchDescriptions.get(jsPatch);
                if (description != null) {
                    pillText.setText(description);
                } else {
                    String displayName = jsPatch;
                    if (jsPatch.startsWith(prefix)) {
                        displayName = jsPatch.substring(prefix.length());
                    }
                    pillText.setText(displayName);
                }

                // Clicking on text opens JS file in editor
                pillText.setClickable(true);
                pillText.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openJsPatchFile(jsPatch);
                    }
                });

                pillRow.addView(pillText);

                // Toggle switch — no text, only toggling
                final Switch jsPatchSwitch = new Switch(this);
                jsPatchSwitch.setId(View.generateViewId());
                LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                jsPatchSwitch.setLayoutParams(switchParams);

                k12KbSettings.CheckSettingOrSetDefault(jsPatch, k12KbSettings.JS_PATCH_IS_ENABLED_DEFAULT);
                boolean enabled = k12KbSettings.GetBooleanValue(jsPatch);
                jsPatchSwitch.setChecked(enabled);

                jsPatchSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        k12KbSettings.SetBooleanValue(jsPatch, isChecked);
                    }
                });

                pillRow.addView(jsPatchSwitch);

                containerJsPatches.addView(pillRow);

                if (isFirst) {
                    // Remove the XML-defined default switch since we don't use it anymore
                    ((ViewGroup) defaultJsPatch.getParent()).removeView(defaultJsPatch);
                    isFirst = false;
                }
            }
        }


    }

    @Override
    public void onResume(){
        super.onResume();
        RedrawViewData();
    }

    private void openJsPatchFile(String jsPatchFileName) {
        File file = new File(FileJsonUtils.PATH, jsPatchFileName);
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.js_patch_file_not_found, jsPatchFileName), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getString(R.string.file_provider_authority), file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "text/javascript");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.js_patch_no_editor), Toast.LENGTH_SHORT).show();
        }
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
