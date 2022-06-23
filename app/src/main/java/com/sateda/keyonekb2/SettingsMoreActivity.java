package com.sateda.keyonekb2;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.fasterxml.jackson.databind.json.JsonMapper;

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
                SaveFiles();
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


    private void SaveFiles() {

        //if (ActivityCompat.checkSelfPermission(SettingsMoreActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED) {
        //    ActivityCompat.requestPermissions(SettingsMoreActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_CODE);
        //}

        JsonMapper mapper = FileJsonUtils.PrepareMapper();


        for (KeyboardLayout keyboardLayout:
        Instance.KeyboardLayoutList) {
            //mapper.writeValue(stream, Instance.KeyboardLayoutList);
            String fileName = keyboardLayout.Resources.XmlRes+".json";
            FileJsonUtils.SerializeToFile(mapper, keyboardLayout, fileName);
            String baseFolder = getApplicationContext().getFileStreamPath(fileName).getAbsolutePath();
            btSave.setText(baseFolder);
        }

        for (String key: Instance.KeyboardAltLayouts.keySet()) {
            String fileName = key+".json";
            FileJsonUtils.SerializeToFile(mapper, Instance.KeyboardAltLayouts.get(key), fileName);
            String baseFolder = getApplicationContext().getFileStreamPath(fileName).getAbsolutePath();
            btSave.setText(baseFolder);
        }




    }

}
