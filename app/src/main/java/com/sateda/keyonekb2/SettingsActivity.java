package com.sateda.keyonekb2;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import java.util.ArrayList;

public class SettingsActivity extends AppCompatActivity {

    public static final String APP_PREFERENCES = "kbsettings";
    public static final String APP_PREFERENCES_RU_LANG = "switch_ru_lang";
    public static final String APP_PREFERENCES_TRANSLIT_RU_LANG = "switch_translit_ru_lang";
    public static final String APP_PREFERENCES_UA_LANG = "switch_ua_lang";
    public static final String APP_PREFERENCES_SENS_BOTTOM_BAR = "sens_bottom_bar";
    public static final String APP_PREFERENCES_SHOW_TOAST = "show_toast";
    public static final String APP_PREFERENCES_ALT_SPACE = "alt_space";
    public static final String APP_PREFERENCES_LONG_PRESS_ALT = "long_press_alt";
    public static final String APP_PREFERENCES_MANAGE_CALL = "manage_call";
    public static final String APP_PREFERENCES_FLAG = "flag";
    public static final String APP_PREFERENCES_HEIGHT_BOTTOM_BAR = "height_bottom_bar";
    public static final String APP_PREFERENCES_SHOW_DEFAULT_ONSCREEN_KEYBOARD = "show_default_onscreen_keyboard";
    public static final String APP_PREFERENCES_KEYBOARD_GESTURES_AT_VIEWS_ENABLED = "keyboard_gestures_at_views_enabled";

    private static final int REQUEST_PERMISSION_CODE = 101;

    private SharedPreferences mSettings;

    private Switch switch_ru_lang;
    private Switch switch_translit_ru_lang;
    private Switch switch_ua_lang;
    private Switch toast_show_lang;
    private SeekBar sens_bottom_bar;
    private Switch switch_alt_space;
    private Switch switch_long_press_alt;
    private Switch switch_manage_call;
    private Switch switch_flag;
    private RelativeLayout layout;
    private SeekBar height_bottom_bar;
    private Switch switch_show_default_onscreen_keyboard;
    private Switch switch_keyboard_gestures_at_views_enabled;

    private float touchY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);

        layout = (RelativeLayout) findViewById(R.id.activity_settings);
        //layout.setMinimumHeight(5000);
        ArrayList<KeyboardLayoutRes> activeLayouts = KeyboardLayoutManager.LoadKeyboardLayoutsRes(getResources(), getApplicationContext());
        Switch defaultLangSwitch = (Switch) findViewById(R.id.default_lang);
        int prevId = 0;
        for (KeyboardLayoutRes keyboardLayoutRes : activeLayouts) {
            Switch switch_lang;
            if(prevId == 0) {
                switch_lang = defaultLangSwitch;
                prevId = switch_lang.getId();
            } else {
                switch_lang = new Switch(this);
                RelativeLayout.LayoutParams llp = (RelativeLayout.LayoutParams)defaultLangSwitch.getLayoutParams();
                RelativeLayout.LayoutParams llp2 = new RelativeLayout.LayoutParams(llp);
                switch_lang.setLayoutParams(llp2);

                llp2.addRule(RelativeLayout.BELOW, prevId);
                prevId = keyboardLayoutRes.getHash();
                switch_lang.setId(prevId);
                switch_lang.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultLangSwitch.getTextSize());
                layout.addView(switch_lang);
            }

            switch_lang.setText(keyboardLayoutRes.OptionsName);

            if (mSettings.contains(keyboardLayoutRes.getPreferenceName())
                && mSettings.getBoolean(keyboardLayoutRes.getPreferenceName(), false)) {
                switch_lang.setChecked(true);
            } else {
                switch_lang.setChecked(false);
            }

            switch_lang.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mSettings.edit().putBoolean(keyboardLayoutRes.getPreferenceName(), isChecked).apply();
                }
            });

        }

        View divider = findViewById(R.id.divider2);
        RelativeLayout.LayoutParams llp = (RelativeLayout.LayoutParams)divider.getLayoutParams();
        llp.addRule(RelativeLayout.BELOW, prevId);

        toast_show_lang = (Switch) findViewById(R.id.toast_show_lang);
        if(mSettings.contains(APP_PREFERENCES_SHOW_TOAST)) {
            toast_show_lang.setChecked(mSettings.getBoolean(APP_PREFERENCES_SHOW_TOAST, false));
        } else {
            toast_show_lang.setChecked(false);
        }
        toast_show_lang.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                changeToast(isChecked);
            }
        });

        sens_bottom_bar = (SeekBar) findViewById(R.id.seekBar);

        if(mSettings.contains(APP_PREFERENCES_SENS_BOTTOM_BAR)) {
            sens_bottom_bar.setProgress(mSettings.getInt(APP_PREFERENCES_SENS_BOTTOM_BAR, 1));
        } else {
            sens_bottom_bar.setProgress(1);
        }
        sens_bottom_bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                changeSensitivityBottomBar(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        switch_alt_space = (Switch) findViewById(R.id.switch_alt_space);

        if(mSettings.contains(APP_PREFERENCES_ALT_SPACE)) {
            switch_alt_space.setChecked(mSettings.getBoolean(APP_PREFERENCES_ALT_SPACE, false));
        } else {
            switch_alt_space.setChecked(false);
        }
        switch_alt_space.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                changeAltSpace(isChecked);
            }
        });

        switch_long_press_alt = (Switch) findViewById(R.id.switch_long_press_alt);

        if(mSettings.contains(APP_PREFERENCES_LONG_PRESS_ALT)) {
            switch_long_press_alt.setChecked(mSettings.getBoolean(APP_PREFERENCES_LONG_PRESS_ALT, true));
        } else {
            switch_long_press_alt.setChecked(true);
        }
        switch_long_press_alt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                changeLongPressAlt(isChecked);
            }
        });

        switch_manage_call = (Switch) findViewById(R.id.switch_manage_call);

        if(mSettings.contains(APP_PREFERENCES_MANAGE_CALL)) {
            switch_manage_call.setChecked(mSettings.getBoolean(APP_PREFERENCES_MANAGE_CALL, true));
        } else {
            switch_manage_call.setChecked(true);
        }
        switch_manage_call.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                changeManageCall(isChecked);
            }
        });

        switch_show_default_onscreen_keyboard = (Switch) findViewById(R.id.switch_show_default_onscreen_keyboard);

        if(mSettings.contains(APP_PREFERENCES_SHOW_DEFAULT_ONSCREEN_KEYBOARD)) {
            switch_show_default_onscreen_keyboard.setChecked(mSettings.getBoolean(APP_PREFERENCES_SHOW_DEFAULT_ONSCREEN_KEYBOARD, false));
        } else {
            switch_show_default_onscreen_keyboard.setChecked(false);
        }
        switch_show_default_onscreen_keyboard.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                changeShowDefaultOnScreenKeyboard(isChecked);
            }
        });

        switch_keyboard_gestures_at_views_enabled = (Switch) findViewById(R.id.switch_keyboard_gestures_at_views_enabled);


        if(mSettings.contains(APP_PREFERENCES_KEYBOARD_GESTURES_AT_VIEWS_ENABLED)) {
            switch_keyboard_gestures_at_views_enabled.setChecked(mSettings.getBoolean(APP_PREFERENCES_KEYBOARD_GESTURES_AT_VIEWS_ENABLED, true));
        } else {
            switch_keyboard_gestures_at_views_enabled.setChecked(true);
        }
        switch_keyboard_gestures_at_views_enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                changeKeyboardGesturesAtViewsEnabled(isChecked);
            }
        });

        switch_flag = (Switch) findViewById(R.id.switch_flag);

        if(mSettings.contains(APP_PREFERENCES_FLAG)) {
            switch_flag.setChecked(mSettings.getBoolean(APP_PREFERENCES_FLAG, true));
        } else {
            switch_flag.setChecked(true);
        }
        switch_flag.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                changeFlag(isChecked);
            }
        });

        height_bottom_bar = (SeekBar) findViewById(R.id.seekBarBtnPanel);

        if(mSettings.contains(APP_PREFERENCES_HEIGHT_BOTTOM_BAR)) {
            height_bottom_bar.setProgress(mSettings.getInt(APP_PREFERENCES_HEIGHT_BOTTOM_BAR, 10));
        } else {
            height_bottom_bar.setProgress(10);
        }
        height_bottom_bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                changeHeightBottomBar(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }



    private void changeSensitivityBottomBar(int val){
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putInt(APP_PREFERENCES_SENS_BOTTOM_BAR, val);
        editor.apply();
    }

    private void changeHeightBottomBar(int val){
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putInt(APP_PREFERENCES_HEIGHT_BOTTOM_BAR, val);
        editor.apply();
    }

    private void changeToast(boolean isChecked){
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putBoolean(APP_PREFERENCES_SHOW_TOAST, isChecked);
        editor.apply();
    }

    private void changeAltSpace(boolean isChecked){
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putBoolean(APP_PREFERENCES_ALT_SPACE, isChecked);
        editor.apply();
    }

    private void changeLongPressAlt(boolean isChecked){
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putBoolean(APP_PREFERENCES_LONG_PRESS_ALT, isChecked);
        editor.apply();
    }

    private void changeManageCall(boolean isChecked){

        if(isChecked && this.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED){
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)!=PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ANSWER_PHONE_CALLS}, REQUEST_PERMISSION_CODE);
            }
        }

        SharedPreferences.Editor editor = mSettings.edit();
        editor.putBoolean(APP_PREFERENCES_MANAGE_CALL, isChecked);
        editor.apply();
    }

    private void changeShowDefaultOnScreenKeyboard(boolean isChecked){
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putBoolean(APP_PREFERENCES_SHOW_DEFAULT_ONSCREEN_KEYBOARD, isChecked);
        editor.apply();
    }

    private void changeKeyboardGesturesAtViewsEnabled(boolean isChecked){
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putBoolean(APP_PREFERENCES_KEYBOARD_GESTURES_AT_VIEWS_ENABLED, isChecked);
        editor.apply();
    }

    private void changeFlag(boolean isChecked){
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putBoolean(APP_PREFERENCES_FLAG, isChecked);
        editor.apply();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                touchY = event.getY() - layout.getY();
            }
            break;
            case MotionEvent.ACTION_MOVE: {
                display.getSize(size);
                if((event.getY() - touchY) >= 10) break;
                if((event.getY() - touchY) <=  size.y - layout.getHeight() - 300) break;
                layout.setY(event.getY() - touchY);
            }
            break;
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        int step = 500;
        if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN){
            display.getSize(size);
            if(layout.getY()+step <=  size.y - layout.getHeight() - 300){
                layout.setY(layout.getY()+step);
            }else{
                layout.setY(size.y - layout.getHeight() - 300);
            }
            return true;
        }
        if(keyCode == KeyEvent.KEYCODE_VOLUME_UP){
            display.getSize(size);
            if(layout.getY()-step >=  10){
                layout.setY(layout.getY()-step);
            }else{
                layout.setY(10);
            }
            return true;
        }
        if(keyCode == KeyEvent.KEYCODE_BACK){
            Intent switchActivityIntent = new Intent(this, MainActivity.class);
            startActivity(switchActivityIntent);
        }
        return false;
    }
}