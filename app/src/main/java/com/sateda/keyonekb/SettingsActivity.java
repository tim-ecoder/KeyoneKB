package com.sateda.keyonekb;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Switch;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {

    public static final String APP_PREFERENCES = "kbsettings";
    public static final String APP_PREFERENCES_RU_LANG = "switch_ru_lang";
    public static final String APP_PREFERENCES_TRANSLIT_RU_LANG = "switch_translit_ru_lang";
    public static final String APP_PREFERENCES_UA_LANG = "switch_ua_lang";
    public static final String APP_PREFERENCES_SENS_BOTTON_BAR = "sens_botton_bar";
    public static final String APP_PREFERENCES_SHOW_TOAST = "show_toast";
    public static final String APP_PREFERENCES_ALT_SPACE = "alt_space";
    public static final String APP_PREFERENCES_SYMPAD = "sympad";
    public static final String APP_PREFERENCES_FLAG = "flag";

    private SharedPreferences mSettings;

    private Switch switch_ru_lang;
    private Switch switch_translit_ru_lang;
    private Switch switch_ua_lang;
    private Switch toast_show_lang;
    private SeekBar sens_botton_bar;
    private Switch switch_alt_space;
    private Switch switch_sympad;
    private Switch switch_flag;
    private RelativeLayout layout;

    private float touchY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        layout = (RelativeLayout) findViewById(R.id.activity_settings);

        switch_ru_lang = (Switch) findViewById(R.id.switch_ru_lang);
        switch_ru_lang.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                changeRuLang(isChecked);
            }
        });

        switch_translit_ru_lang = (Switch) findViewById(R.id.switch_translit_ru_lang);
        switch_translit_ru_lang.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                changeTranslitRuLang(isChecked);
            }
        });

        switch_ua_lang = (Switch) findViewById(R.id.switch_ua_lang);
        switch_ua_lang.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                changeUaLang(isChecked);
            }
        });

        toast_show_lang = (Switch) findViewById(R.id.toast_show_lang);
        toast_show_lang.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                changeToast(isChecked);
            }
        });

        sens_botton_bar = (SeekBar) findViewById(R.id.seekBar);
        sens_botton_bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                changeSensitivityBottonBar(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        switch_alt_space = (Switch) findViewById(R.id.switch_alt_space);
        switch_alt_space.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                changeAltSpace(isChecked);
            }
        });

        switch_sympad = (Switch) findViewById(R.id.switch_sympad);
        switch_sympad.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                changeSymPad(isChecked);
            }
        });

        switch_flag = (Switch) findViewById(R.id.switch_flag);
        switch_flag.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                changeFlag(isChecked);
            }
        });

        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        loadSetting();
    }

    private void loadSetting(){
        if(mSettings.contains(APP_PREFERENCES_RU_LANG)) {
            switch_ru_lang.setChecked(mSettings.getBoolean(APP_PREFERENCES_RU_LANG, true));
        }
        if(mSettings.contains(APP_PREFERENCES_TRANSLIT_RU_LANG)) {
            switch_translit_ru_lang.setChecked(mSettings.getBoolean(APP_PREFERENCES_TRANSLIT_RU_LANG, true));
        }
        if(mSettings.contains(APP_PREFERENCES_UA_LANG)) {
            switch_ua_lang.setChecked(mSettings.getBoolean(APP_PREFERENCES_UA_LANG, true));
        }
        if(mSettings.contains(APP_PREFERENCES_SENS_BOTTON_BAR)) {
            sens_botton_bar.setProgress(mSettings.getInt(APP_PREFERENCES_SENS_BOTTON_BAR, 10));
        }
        if(mSettings.contains(APP_PREFERENCES_SHOW_TOAST)) {
            toast_show_lang.setChecked(mSettings.getBoolean(APP_PREFERENCES_SHOW_TOAST, false));
        }
        if(mSettings.contains(APP_PREFERENCES_ALT_SPACE)) {
            switch_alt_space.setChecked(mSettings.getBoolean(APP_PREFERENCES_ALT_SPACE, true));
        }
        if(mSettings.contains(APP_PREFERENCES_SYMPAD)) {
            switch_sympad.setChecked(mSettings.getBoolean(APP_PREFERENCES_SYMPAD, false));
        }
        if(mSettings.contains(APP_PREFERENCES_FLAG)) {
            switch_flag.setChecked(mSettings.getBoolean(APP_PREFERENCES_FLAG, false));
        }
    }

    private void changeRuLang(boolean isChecked){
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putBoolean(APP_PREFERENCES_RU_LANG, isChecked);
        editor.apply();
    }

    private void changeTranslitRuLang(boolean isChecked){
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putBoolean(APP_PREFERENCES_TRANSLIT_RU_LANG, isChecked);
        editor.apply();
    }

    private void changeUaLang(boolean isChecked){
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putBoolean(APP_PREFERENCES_UA_LANG, isChecked);
        editor.apply();
    }

    private void changeSensitivityBottonBar(int val){
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putInt(APP_PREFERENCES_SENS_BOTTON_BAR, val);
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

    private void changeSymPad(boolean isChecked){
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putBoolean(APP_PREFERENCES_SYMPAD, isChecked);
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

}