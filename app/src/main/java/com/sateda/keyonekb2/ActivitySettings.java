package com.sateda.keyonekb2;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import com.pes.androidmaterialcolorpickerdialog.ColorPicker;

import java.util.ArrayList;

import static com.sateda.keyonekb2.KeyboardLayoutManager.IsCurrentDevice;
import static com.sateda.keyonekb2.KeyboardLayoutManager.getDeviceFullMODEL;

public class ActivitySettings extends AppCompatActivity {

    public static final int REQUEST_PERMISSION_CODE = 101;

    private KeyoneKb2Settings keyoneKb2Settings;

    private RelativeLayout layout;

    private float touchY;

    private boolean SetSwitchStateOrDefault(Switch switch1, String settingName) {
        boolean enabled = keyoneKb2Settings.GetBooleanValue(settingName);
        switch1.setChecked(enabled);
        return enabled;
    }

    private void SetProgressOrDefault(SeekBar seekBar, String settingName) {
        seekBar.setProgress(keyoneKb2Settings.GetIntValue(settingName));
    }

    private String deviceFullMODEL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keyoneKb2Settings = KeyoneKb2Settings.Get(getSharedPreferences(KeyoneKb2Settings.APP_PREFERENCES, Context.MODE_PRIVATE));

        setContentView(R.layout.activity_settings);

        layout = (RelativeLayout) findViewById(R.id.activity_settings);

        ArrayList<KeyboardLayout.KeyboardLayoutOptions> keyboardLayouts = KeyboardLayoutManager.LoadKeyboardLayoutsRes(getResources(), getApplicationContext());
        Switch defaultKeyboardLayoutSwitch = (Switch) findViewById(R.id.default_keyboard_layout);
        int prevId = 0;
        int enableCount = 0;
        deviceFullMODEL = getDeviceFullMODEL();
        for (KeyboardLayout.KeyboardLayoutOptions keyboardLayoutOptions : keyboardLayouts) {

            boolean isDevice = IsCurrentDevice(deviceFullMODEL, keyboardLayoutOptions);
            if(!isDevice)
                continue;

            Switch currentKeyboardLayoutSwitch;
            //Первый язык будет по умолчанию всегда активирован
            //Плюс на уровне загрузчика клав, будет хард код, чтобы первая клава всегда была сразу после установки
            if(prevId == 0) {
                currentKeyboardLayoutSwitch = defaultKeyboardLayoutSwitch;
                RelativeLayout.LayoutParams llp = (RelativeLayout.LayoutParams)defaultKeyboardLayoutSwitch.getLayoutParams();
                RelativeLayout.LayoutParams llp2 = new RelativeLayout.LayoutParams(llp);
                currentKeyboardLayoutSwitch.setLayoutParams(llp2);
                prevId = currentKeyboardLayoutSwitch.getId();
            } else {
                currentKeyboardLayoutSwitch = new Switch(this);
                RelativeLayout.LayoutParams llp = (RelativeLayout.LayoutParams)defaultKeyboardLayoutSwitch.getLayoutParams();
                RelativeLayout.LayoutParams llp2 = new RelativeLayout.LayoutParams(llp);
                currentKeyboardLayoutSwitch.setLayoutParams(llp2);

                llp2.addRule(RelativeLayout.BELOW, prevId);
                prevId = keyboardLayoutOptions.getId();
                currentKeyboardLayoutSwitch.setId(prevId);
                currentKeyboardLayoutSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultKeyboardLayoutSwitch.getTextSize());
                layout.addView(currentKeyboardLayoutSwitch);

            }

            currentKeyboardLayoutSwitch.setText(keyboardLayoutOptions.OptionsName);
            keyoneKb2Settings.CheckSettingOrSetDefault(keyboardLayoutOptions.getPreferenceName(), keyoneKb2Settings.KEYBOARD_LAYOUT_IS_ENABLED_DEFAULT);
            boolean enabled = SetSwitchStateOrDefault(currentKeyboardLayoutSwitch, keyboardLayoutOptions.getPreferenceName());
            if(enabled)
                enableCount++;

            currentKeyboardLayoutSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    keyoneKb2Settings.SetBooleanValue(keyboardLayoutOptions.getPreferenceName(), isChecked);
                }
            });

        }

        if(prevId == 0) {
            Switch currentKeyboardLayoutSwitch;
            currentKeyboardLayoutSwitch = defaultKeyboardLayoutSwitch;
            RelativeLayout.LayoutParams llp = (RelativeLayout.LayoutParams) defaultKeyboardLayoutSwitch.getLayoutParams();
            RelativeLayout.LayoutParams llp2 = new RelativeLayout.LayoutParams(llp);
            currentKeyboardLayoutSwitch.setLayoutParams(llp2);
            prevId = currentKeyboardLayoutSwitch.getId();

            KeyboardLayout.KeyboardLayoutOptions keyboardLayoutOptions = keyboardLayouts.get(0);

            currentKeyboardLayoutSwitch.setText(keyboardLayoutOptions.OptionsName);
            keyoneKb2Settings.CheckSettingOrSetDefault(keyboardLayoutOptions.getPreferenceName(), keyoneKb2Settings.KEYBOARD_LAYOUT_IS_ENABLED_DEFAULT);
            boolean enabled = SetSwitchStateOrDefault(currentKeyboardLayoutSwitch, keyboardLayoutOptions.getPreferenceName());
            if(enabled)
                enableCount++;

            currentKeyboardLayoutSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    keyoneKb2Settings.SetBooleanValue(keyboardLayoutOptions.getPreferenceName(), isChecked);
                }
            });

        }

        if(enableCount == 0) {
            KeyboardLayout.KeyboardLayoutOptions defLayout = keyboardLayouts.get(0);
            keyoneKb2Settings.SetBooleanValue(defLayout.getPreferenceName(), true);
            SetSwitchStateOrDefault(defaultKeyboardLayoutSwitch, defLayout.getPreferenceName());
        }

        View divider = findViewById(R.id.divider2);
        RelativeLayout.LayoutParams llp = (RelativeLayout.LayoutParams)divider.getLayoutParams();
        llp.addRule(RelativeLayout.BELOW, prevId);

        SeekBar sens_bottom_bar = (SeekBar) findViewById(R.id.seekBar);
        SetProgressOrDefault(sens_bottom_bar, keyoneKb2Settings.APP_PREFERENCES_1_SENS_BOTTOM_BAR);

        sens_bottom_bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                keyoneKb2Settings.SetIntValue(keyoneKb2Settings.APP_PREFERENCES_1_SENS_BOTTOM_BAR, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        Switch toast_show_lang = (Switch) findViewById(R.id.toast_show_lang);
        SetSwitchStateOrDefault(toast_show_lang, keyoneKb2Settings.APP_PREFERENCES_2_SHOW_TOAST);
        toast_show_lang.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                keyoneKb2Settings.SetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_2_SHOW_TOAST, isChecked);
            }
        });


        Switch switch_alt_space = (Switch) findViewById(R.id.switch_alt_space);
        SetSwitchStateOrDefault(switch_alt_space, keyoneKb2Settings.APP_PREFERENCES_3_ALT_SPACE);

        switch_alt_space.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                keyoneKb2Settings.SetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_3_ALT_SPACE, isChecked);

            }
        });

        Switch switch_flag = (Switch) findViewById(R.id.switch_flag);
        SetSwitchStateOrDefault(switch_flag, keyoneKb2Settings.APP_PREFERENCES_4_FLAG);

        switch_flag.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                keyoneKb2Settings.SetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_4_FLAG, isChecked);
            }
        });

        Switch switch_long_press_alt = (Switch) findViewById(R.id.switch_long_press_alt);
        SetSwitchStateOrDefault(switch_long_press_alt, keyoneKb2Settings.APP_PREFERENCES_5_LONG_PRESS_ALT);

        switch_long_press_alt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                keyoneKb2Settings.SetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_5_LONG_PRESS_ALT, isChecked);
            }
        });

        Switch switch_manage_call = (Switch) findViewById(R.id.switch_manage_call);

        SetSwitchStateOrDefault(switch_manage_call, keyoneKb2Settings.APP_PREFERENCES_6_MANAGE_CALL);
        switch_manage_call.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if(isChecked && ActivitySettings.this.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED){
                    if (ActivityCompat.checkSelfPermission(ActivitySettings.this, Manifest.permission.ANSWER_PHONE_CALLS)!=PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(ActivitySettings.this, new String[]{Manifest.permission.ANSWER_PHONE_CALLS}, REQUEST_PERMISSION_CODE);
                    }
                }
                keyoneKb2Settings.SetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_6_MANAGE_CALL, isChecked);
            }
        });

        SeekBar height_bottom_bar = (SeekBar) findViewById(R.id.seekBarBtnPanel);
        SetProgressOrDefault(height_bottom_bar, keyoneKb2Settings.APP_PREFERENCES_7_HEIGHT_BOTTOM_BAR);

        height_bottom_bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                keyoneKb2Settings.SetIntValue(keyoneKb2Settings.APP_PREFERENCES_7_HEIGHT_BOTTOM_BAR, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        Switch switch_show_default_onscreen_keyboard = (Switch) findViewById(R.id.switch_show_default_onscreen_keyboard);
        SetSwitchStateOrDefault(switch_show_default_onscreen_keyboard, keyoneKb2Settings.APP_PREFERENCES_8_SHOW_SWIPE_PANEL);

        switch_show_default_onscreen_keyboard.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                keyoneKb2Settings.SetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_8_SHOW_SWIPE_PANEL, isChecked);
            }
        });

        Spinner staticSpinner = (Spinner) findViewById(R.id.static_spinner_p9);

        ArrayAdapter<CharSequence> staticAdapter = ArrayAdapter
                .createFromResource(this, R.array.pref_p9_gesture_modes_array, android.R.layout.simple_spinner_item);

        staticAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        staticSpinner.setAdapter(staticAdapter);

        int spinner_p9_from_pref =  keyoneKb2Settings.GetIntValue(keyoneKb2Settings.APP_PREFERENCES_9_GESTURE_MODE_AT_VIEW_MODE);
        staticSpinner.setSelection(spinner_p9_from_pref);

        staticSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                keyoneKb2Settings.SetIntValue(keyoneKb2Settings.APP_PREFERENCES_9_GESTURE_MODE_AT_VIEW_MODE, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                staticSpinner.setSelection(spinner_p9_from_pref);
            }
        });


        Switch switch_notification_icon_system = (Switch) findViewById(R.id.switch_notification_icon_system);
        SetSwitchStateOrDefault(switch_notification_icon_system, keyoneKb2Settings.APP_PREFERENCES_10_NOTIFICATION_ICON_SYSTEM);

        switch_notification_icon_system.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                keyoneKb2Settings.SetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_10_NOTIFICATION_ICON_SYSTEM, isChecked);
            }
        });

        Switch switch_vibrate_on_key_down = (Switch) findViewById(R.id.switch_vibrate_on_key_down);
        SetSwitchStateOrDefault(switch_vibrate_on_key_down, keyoneKb2Settings.APP_PREFERENCES_11_VIBRATE_ON_KEY_DOWN);

        switch_vibrate_on_key_down.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                keyoneKb2Settings.SetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_11_VIBRATE_ON_KEY_DOWN, isChecked);
            }
        });

        Switch switch_ensure_entered_text = (Switch) findViewById(R.id.switch_ensure_entered_text);
        SetSwitchStateOrDefault(switch_ensure_entered_text, keyoneKb2Settings.APP_PREFERENCES_12_ENSURE_ENTERED_TEXT);

        switch_ensure_entered_text.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                keyoneKb2Settings.SetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_12_ENSURE_ENTERED_TEXT, isChecked);
            }
        });


        Switch switch_pointer_mode_rect = (Switch) findViewById(R.id.switch_pointer_mode_rect);
        SetSwitchStateOrDefault(switch_pointer_mode_rect, keyoneKb2Settings.APP_PREFERENCES_13_POINTER_MODE_RECT);

        switch_pointer_mode_rect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                keyoneKb2Settings.SetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_13_POINTER_MODE_RECT, isChecked);
            }
        });

        int color = keyoneKb2Settings.GetIntValue(keyoneKb2Settings.APP_PREFERENCES_13A_POINTER_MODE_RECT_COLOR);
        final ColorPicker cp = new ColorPicker(this, Color.red(color), Color.green(color), Color.blue(color));

        Button btSave = (Button)findViewById(R.id.button_pointer_mode_rect_color_picker);
        btSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cp.show();

                /* On Click listener for the dialog, when the user select the color */
                Button okColor = (Button)cp.findViewById(R.id.okColorButton);

                okColor.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        keyoneKb2Settings.SetIntValue(keyoneKb2Settings.APP_PREFERENCES_13A_POINTER_MODE_RECT_COLOR, cp.getColor());
                        cp.dismiss();
                    }
                });
            }
        });



        Switch switch_p14 = (Switch) findViewById(R.id.switch_p14_nav_pad_on_hold);
        SetSwitchStateOrDefault(switch_p14, keyoneKb2Settings.APP_PREFERENCES_14_NAV_PAD_ON_HOLD);

        switch_p14.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                keyoneKb2Settings.SetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_14_NAV_PAD_ON_HOLD, isChecked);
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
            Intent switchActivityIntent = new Intent(this, ActivityMain.class);
            startActivity(switchActivityIntent);
        }
        return false;
    }
}