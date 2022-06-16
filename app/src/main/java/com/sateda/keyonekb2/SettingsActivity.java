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

    public static final int REQUEST_PERMISSION_CODE = 101;

    private KbSettings kbSettings;

    private Switch toast_show_lang;
    private SeekBar sens_bottom_bar;
    private Switch switch_alt_space;
    private Switch switch_long_press_alt;
    private Switch switch_manage_call;
    private Switch switch_flag;
    private Switch switch_notification_icon_system;
    private RelativeLayout layout;
    private SeekBar height_bottom_bar;
    private Switch switch_show_default_onscreen_keyboard;
    private Switch switch_keyboard_gestures_at_views_enabled;

    private float touchY;

    private void SetSwitchStateOrDefault(Switch switch1, String settingName) {
        switch1.setChecked(kbSettings.GetBooleanValue(settingName));
    }

    private void SetProgressOrDefault(SeekBar seekBar, String settingName) {
        seekBar.setProgress(kbSettings.GetIntValue(settingName));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        kbSettings = KbSettings.Get(getSharedPreferences(KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));

        setContentView(R.layout.activity_settings);

        layout = (RelativeLayout) findViewById(R.id.activity_settings);
        //layout.setMinimumHeight(5000);
        ArrayList<KeyboardLayoutRes> activeLayouts = KeyboardLayoutManager.LoadKeyboardLayoutsRes(getResources(), getApplicationContext());
        Switch defaultKeyboardLayoutSwitch = (Switch) findViewById(R.id.default_lang);
        int prevId = 0;
        for (KeyboardLayoutRes keyboardLayoutRes : activeLayouts) {
            Switch currentKeyboardLayoutSwitch;
            //Первый язык будет по умолчанию всегда активирован
            //Плюс на уровне загрузчика клав, будет хард код, чтобы первая клава всегда была сразу после установки
            if(prevId == 0) {
                currentKeyboardLayoutSwitch = defaultKeyboardLayoutSwitch;
                prevId = currentKeyboardLayoutSwitch.getId();
            } else {
                currentKeyboardLayoutSwitch = new Switch(this);
                RelativeLayout.LayoutParams llp = (RelativeLayout.LayoutParams)defaultKeyboardLayoutSwitch.getLayoutParams();
                RelativeLayout.LayoutParams llp2 = new RelativeLayout.LayoutParams(llp);
                currentKeyboardLayoutSwitch.setLayoutParams(llp2);

                llp2.addRule(RelativeLayout.BELOW, prevId);
                prevId = keyboardLayoutRes.getHash();
                currentKeyboardLayoutSwitch.setId(prevId);
                currentKeyboardLayoutSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultKeyboardLayoutSwitch.getTextSize());
                layout.addView(currentKeyboardLayoutSwitch);
            }

            currentKeyboardLayoutSwitch.setText(keyboardLayoutRes.OptionsName);
            kbSettings.CheckSettingOrSetDefault(keyboardLayoutRes.getPreferenceName(), kbSettings.KEYBOARD_IS_ENABLED_DEFAULT);
            SetSwitchStateOrDefault(currentKeyboardLayoutSwitch, keyboardLayoutRes.getPreferenceName());

            currentKeyboardLayoutSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    kbSettings.SetBooleanValue(keyboardLayoutRes.getPreferenceName(), isChecked);
                }
            });

        }

        View divider = findViewById(R.id.divider2);
        RelativeLayout.LayoutParams llp = (RelativeLayout.LayoutParams)divider.getLayoutParams();
        llp.addRule(RelativeLayout.BELOW, prevId);

        sens_bottom_bar = (SeekBar) findViewById(R.id.seekBar);
        SetProgressOrDefault(sens_bottom_bar, kbSettings.APP_PREFERENCES_1_SENS_BOTTOM_BAR);

        sens_bottom_bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                kbSettings.SetIntValue(kbSettings.APP_PREFERENCES_1_SENS_BOTTOM_BAR, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        toast_show_lang = (Switch) findViewById(R.id.toast_show_lang);
        SetSwitchStateOrDefault(toast_show_lang, kbSettings.APP_PREFERENCES_2_SHOW_TOAST);
        toast_show_lang.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                kbSettings.SetBooleanValue(kbSettings.APP_PREFERENCES_2_SHOW_TOAST, isChecked);
            }
        });



        switch_alt_space = (Switch) findViewById(R.id.switch_alt_space);
        SetSwitchStateOrDefault(switch_alt_space, kbSettings.APP_PREFERENCES_3_ALT_SPACE);

        switch_alt_space.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                kbSettings.SetBooleanValue(kbSettings.APP_PREFERENCES_3_ALT_SPACE, isChecked);

            }
        });

        switch_flag = (Switch) findViewById(R.id.switch_flag);
        SetSwitchStateOrDefault(switch_flag, kbSettings.APP_PREFERENCES_4_FLAG);

        switch_flag.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                kbSettings.SetBooleanValue(kbSettings.APP_PREFERENCES_4_FLAG, isChecked);
            }
        });

        switch_long_press_alt = (Switch) findViewById(R.id.switch_long_press_alt);
        SetSwitchStateOrDefault(switch_long_press_alt, kbSettings.APP_PREFERENCES_5_LONG_PRESS_ALT);

        switch_long_press_alt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                kbSettings.SetBooleanValue(kbSettings.APP_PREFERENCES_5_LONG_PRESS_ALT, isChecked);
            }
        });

        switch_manage_call = (Switch) findViewById(R.id.switch_manage_call);

        SetSwitchStateOrDefault(switch_manage_call, kbSettings.APP_PREFERENCES_6_MANAGE_CALL);
        switch_manage_call.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if(isChecked && SettingsActivity.this.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED){
                    if (ActivityCompat.checkSelfPermission(SettingsActivity.this, Manifest.permission.ANSWER_PHONE_CALLS)!=PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(SettingsActivity.this, new String[]{Manifest.permission.ANSWER_PHONE_CALLS}, REQUEST_PERMISSION_CODE);
                    }
                }
                kbSettings.SetBooleanValue(kbSettings.APP_PREFERENCES_6_MANAGE_CALL, isChecked);
            }
        });

        height_bottom_bar = (SeekBar) findViewById(R.id.seekBarBtnPanel);
        SetProgressOrDefault(height_bottom_bar, kbSettings.APP_PREFERENCES_7_HEIGHT_BOTTOM_BAR);

        height_bottom_bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                kbSettings.SetIntValue(kbSettings.APP_PREFERENCES_7_HEIGHT_BOTTOM_BAR, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        switch_show_default_onscreen_keyboard = (Switch) findViewById(R.id.switch_show_default_onscreen_keyboard);
        SetSwitchStateOrDefault(switch_show_default_onscreen_keyboard, kbSettings.APP_PREFERENCES_8_SHOW_SWIPE_PANEL);

        switch_show_default_onscreen_keyboard.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                kbSettings.SetBooleanValue(kbSettings.APP_PREFERENCES_8_SHOW_SWIPE_PANEL, isChecked);
            }
        });

        switch_keyboard_gestures_at_views_enabled = (Switch) findViewById(R.id.switch_keyboard_gestures_at_views_enabled);
        SetSwitchStateOrDefault(switch_keyboard_gestures_at_views_enabled, kbSettings.APP_PREFERENCES_9_KEYBOARD_GESTURES_AT_VIEWS_ENABLED);

        switch_keyboard_gestures_at_views_enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                kbSettings.SetBooleanValue(kbSettings.APP_PREFERENCES_9_KEYBOARD_GESTURES_AT_VIEWS_ENABLED, isChecked);
            }
        });


        switch_notification_icon_system = (Switch) findViewById(R.id.switch_notification_icon_system);
        SetSwitchStateOrDefault(switch_notification_icon_system, kbSettings.APP_PREFERENCES_10_NOTIFICATION_ICON_SYSTEM);

        switch_notification_icon_system.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                kbSettings.SetBooleanValue(kbSettings.APP_PREFERENCES_10_NOTIFICATION_ICON_SYSTEM, isChecked);
            }
        });

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