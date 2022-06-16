package com.sateda.keyonekb2;

import android.content.SharedPreferences;

public class KbSettings {
    public static final String APP_PREFERENCES = "kbsettings";
    public final String APP_PREFERENCES_10_NOTIFICATION_ICON_SYSTEM = "notification_icon_system";
    public final String APP_PREFERENCES_9_KEYBOARD_GESTURES_AT_VIEWS_ENABLED = "keyboard_gestures_at_views_enabled";
    public final String APP_PREFERENCES_8_SHOW_SWIPE_PANEL = "show_default_onscreen_keyboard";
    public final String APP_PREFERENCES_7_HEIGHT_BOTTOM_BAR = "height_bottom_bar";
    public final String APP_PREFERENCES_6_MANAGE_CALL = "manage_call";
    public final String APP_PREFERENCES_5_LONG_PRESS_ALT = "long_press_alt";
    public final String APP_PREFERENCES_4_FLAG = "flag";
    public final String APP_PREFERENCES_3_ALT_SPACE = "alt_space";
    public final String APP_PREFERENCES_2_SHOW_TOAST = "show_toast";
    public final String APP_PREFERENCES_1_SENS_BOTTOM_BAR = "sens_bottom_bar";

    public final boolean KEYBOARD_IS_ENABLED_DEFAULT = true;
    private static KbSettings _instance;
    SharedPreferences _mSettings;

    public static KbSettings Get(SharedPreferences mSettings) {
        if (_instance == null) {
            _instance = new KbSettings(mSettings);
        }
        return _instance;
    }

    public void CheckSettingOrSetDefault(String pref_name, boolean default_value) {
        if(!_mSettings.contains(pref_name)) {
            _mSettings.edit().putBoolean(pref_name, default_value).apply();
        }
    }

    public void CheckSettingOrSetDefault(String pref_name, int default_value) {
        if(!_mSettings.contains(pref_name)) {
            _mSettings.edit().putInt(pref_name, default_value).apply();
        }
    }

    private KbSettings(SharedPreferences mSettings) {
        _mSettings = mSettings;
        CheckSettingOrSetDefault(APP_PREFERENCES_1_SENS_BOTTOM_BAR, 1);
        CheckSettingOrSetDefault(APP_PREFERENCES_2_SHOW_TOAST, false);
        CheckSettingOrSetDefault(APP_PREFERENCES_3_ALT_SPACE, true);
        CheckSettingOrSetDefault(APP_PREFERENCES_4_FLAG, true);
        CheckSettingOrSetDefault(APP_PREFERENCES_5_LONG_PRESS_ALT, true);
        CheckSettingOrSetDefault(APP_PREFERENCES_6_MANAGE_CALL, true);
        CheckSettingOrSetDefault(APP_PREFERENCES_7_HEIGHT_BOTTOM_BAR, 10);
        CheckSettingOrSetDefault(APP_PREFERENCES_8_SHOW_SWIPE_PANEL, false);
        CheckSettingOrSetDefault(APP_PREFERENCES_9_KEYBOARD_GESTURES_AT_VIEWS_ENABLED, true);
        CheckSettingOrSetDefault(APP_PREFERENCES_10_NOTIFICATION_ICON_SYSTEM, true);
    }

    public boolean GetBooleanValue(String name) {
        return _mSettings.getBoolean(name, false);
    }

    public int GetIntValue(String name) {
        return _mSettings.getInt(name, 0);
    }

    public void SetBooleanValue(String name, boolean value) {
        _mSettings.edit().putBoolean(name, value).apply();
    }

    public void SetIntValue(String name, int value) {
        _mSettings.edit().putInt(name, value).apply();
    }

}
