package com.sateda.keyonekb2;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.*;

import java.io.*;
import java.lang.reflect.Type;

import static com.sateda.keyonekb2.KeyboardLayoutManager.Instance;

public class SettingsMoreActivity extends Activity {
    private KbSettings kbSettings;

    Button btSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        kbSettings = KbSettings.Get(getSharedPreferences(KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));

        setContentView(R.layout.activity_more_settings);

        btSave = (Button)findViewById(R.id.pref_more_bt_save);
        btSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SaveFile();
            }
        });


        TextView textView = (TextView)findViewById(R.id.tv_search_plugins_data);
        textView.setText("Когда захлодишь в приложение, можно не кликать в \"Поиск\", а сразу начать набирать. Плагин сам кликнет в \"Поиск\", когда начинаешь набирать.\n\n");
        if(KeyoneKb2AccessibilityService.Instance == null) {
            textView.append("\n Для работы поисковых плагинов необходимо активировать спец. возможности в главном меню.\n");
            return;
        }
        String text_data = "";
        for(KeyoneKb2AccessibilityService.SearchHackPlugin plugin : KeyoneKb2AccessibilityService.Instance.searchHackPlugins) {
            String value = plugin.getId();
            if(value.isEmpty())
                value = "Зайдите в приложение для автоматического определения.\nЕсли зашли, но тут ничего не изменилось, то либо приложение не добавлено в список плагинов, либо идентификатор поискового поля определяется только динамически и все работает. ";
            text_data += String.format("Приложение:\n%s\nResourceId:\n%s\n\n", plugin.getPackageName(), value);
        }

        textView.append(text_data);
        

    }



    private void SaveFile() {

        Gson gson;// = new Gson();
        gson = new GsonBuilder()
                //.enableComplexMapKeySerialization()
                //.serializeNulls()
                //.registerTypeAdapter(Character.class, new CharacterSerializer())
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .setPrettyPrinting()
                //.setVersion(1.0)
                .excludeFieldsWithoutExposeAnnotation()

                .create();

        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(MapperFeature.AUTO_DETECT_CREATORS,
                MapperFeature.AUTO_DETECT_FIELDS,
                MapperFeature.AUTO_DETECT_GETTERS,
                MapperFeature.AUTO_DETECT_IS_GETTERS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE);



        for (KeyboardLayout keyboardLayout:
        Instance.KeyboardLayoutList) {
            //mapper.writeValue(stream, Instance.KeyboardLayoutList);
            String fileName = keyboardLayout.Resources.XmlRes+".json";
            SerializeToFile(mapper, keyboardLayout, fileName);
        }



        //String baseFolder = getApplicationContext().getFileStreamPath(fileName).getAbsolutePath();
        btSave.setText("OK");

    }

    private void SerializeToFile(ObjectMapper mapper, Object obj, String fileName) {
        String packageName = this.getPackageName();
        String path = Environment.getExternalStorageDirectory().getAbsolutePath()+ "/Android/data/" + packageName + "/files/";

        try {
            boolean exists = (new File(path)).exists();
            if (!exists) {
                new File(path).mkdirs();
            }
            // Open output stream
            FileOutputStream fOut = new FileOutputStream(path + fileName,false);
            // write integers as separated ascii's

            mapper.writeValue(fOut, obj);
            //fOut.write(content.getBytes());
            // Close output stream
            fOut.flush();
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
