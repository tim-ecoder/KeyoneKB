package com.sateda.keyonekb2;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;

public class SettingsMoreActivity extends Activity {
    private KbSettings kbSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        kbSettings = KbSettings.Get(getSharedPreferences(KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));

        setContentView(R.layout.activity_more_settings);

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
}
