package com.ai10.k12kb;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

public class LocaleHelper {

    // 0 = System, 1 = English, 2 = Russian
    private static final String[] LOCALE_CODES = {null, "en", "ru"};

    public static Context applyLocale(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(K12KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE);
        int langIndex = prefs.getInt("interface_lang", 0);
        if (langIndex <= 0 || langIndex >= LOCALE_CODES.length) {
            return context;
        }
        Locale locale = new Locale(LOCALE_CODES[langIndex]);
        return updateResources(context, locale);
    }

    private static Context updateResources(Context context, Locale locale) {
        Locale.setDefault(locale);
        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }
}
