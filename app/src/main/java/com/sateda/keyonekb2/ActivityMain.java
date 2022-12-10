package com.sateda.keyonekb2;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;

import static com.sateda.keyonekb2.ActivitySettings.*;
import static com.sateda.keyonekb2.BuildConfig.*;
import static com.sateda.keyonekb2.KeyboardLayoutManager.getDeviceFullMODEL;

public class ActivityMain extends Activity {

    private Button btn_power_manager;

    private Button btn_sys_phone_permission;

    private KeyoneKb2Settings keyoneKb2Settings;

    Button btn_sys_kb_accessibility_setting;

    Button btn_sys_kb_setting;
    TextView tv_version;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        keyoneKb2Settings = KeyoneKb2Settings.Get(getSharedPreferences(KeyoneKb2Settings.APP_PREFERENCES, Context.MODE_PRIVATE));
        Button btn_settings = (Button) findViewById(R.id.btn_settings);
        Button btn_test_key = (Button) findViewById(R.id.btn_test_key);
        btn_power_manager = (Button) findViewById(R.id.btn_power_manager);
        btn_sys_kb_setting = (Button) findViewById(R.id.btn_sys_kb_setting);
        btn_sys_kb_accessibility_setting = (Button) findViewById(R.id.btn_sys_kb_accessibility_setting);
        btn_sys_phone_permission = (Button) findViewById(R.id.btn_sys_phone_permission);
        tv_version = (TextView) findViewById(R.id.tv_version);
        Button btn_more_settings = (Button) findViewById(R.id.btn_more_settings);

        Button btn_keyboard_reload = (Button) findViewById(R.id.btn_keyboard_reload);
        Button btn_accessibility_reload = (Button) findViewById(R.id.btn_accessibility_reload);

        String text = String.format("\nDevice: %s\nApp: %s\nVersion: %s\nBuild type: %s", getDeviceFullMODEL(), APPLICATION_ID, VERSION_NAME, BUILD_TYPE);
        tv_version.setText(text);

        btn_settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent switchActivityIntent = new Intent(ActivityMain.this, ActivitySettings.class);
                startActivity(switchActivityIntent);
            }
        });

        btn_more_settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent switchActivityIntent = new Intent(ActivityMain.this, ActivitySettingsMore.class);
                startActivity(switchActivityIntent);
            }
        });

        btn_test_key.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent switchActivityIntent = new Intent(ActivityMain.this, ActivityKeyboardTest.class);
                startActivity(switchActivityIntent);
            }
        });

        btn_sys_kb_setting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_INPUT_METHOD_SETTINGS);
                getApplicationContext().startActivity(intent);
            }
        });



        btn_sys_kb_accessibility_setting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                getApplicationContext().startActivity(intent);
            }
        });

        btn_power_manager.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CheckPowerState(ActivityMain.this, true);
            }
        });

        btn_sys_phone_permission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CheckPermissionState(true);
            }
        });

        btn_keyboard_reload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if(imm != null) {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(imm::showInputMethodPicker, 200);
                }

            }
        });

        btn_accessibility_reload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                getApplicationContext().startActivity(intent);

            }
        });



        CheckPermissionState(false);
        CheckPowerState(this, false);
        UpdateKeyboardButton();
        UpdateAccessibilityButton();
        ChangeKeyboard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CheckPermissionState(false);
        CheckPowerState(this, false);
        UpdateKeyboardButton();
        UpdateAccessibilityButton();
        ChangeKeyboard();
    }

    void ChangeKeyboard() {
        if(!isKbEnabled())
            return;
        if (!Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD).contains(".KeyoneIME")) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm != null) {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(imm::showInputMethodPicker, 200);
            }
        }
    }


    private boolean UpdateAccessibilityButton() {
        boolean accEnabledFlag = isAccessibilityEnabled();
        if(accEnabledFlag) {
            btn_sys_kb_accessibility_setting.setEnabled(false);
            btn_sys_kb_accessibility_setting.setText(R.string.main_btn_sys_kb_accessibility_setting_disabled);
            ChangeKeyboard();
        } else {
            btn_sys_kb_accessibility_setting.setEnabled(true);
            btn_sys_kb_accessibility_setting.setText(R.string.main_btn_sys_kb_accessibility_setting_enabled);
        }
        return accEnabledFlag;
    }

    private boolean UpdateKeyboardButton() {
        boolean accEnabledFlag = isKbEnabled();
        if(accEnabledFlag) {
            btn_sys_kb_setting.setEnabled(false);
            btn_sys_kb_setting.setText(R.string.main_btn_sys_kb_setting_disabled);
        } else {
            btn_sys_kb_setting.setEnabled(true);
            btn_sys_kb_setting.setText(R.string.main_btn_sys_kb_setting_enabled);
        }
        return accEnabledFlag;
    }

    private boolean isAccessibilityEnabled() {
        String accEnabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return accEnabled != null && accEnabled.contains(getPackageName());
    }

    private boolean isKbEnabled() {
        String accEnabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS);
        return accEnabled != null && accEnabled.contains(getPackageName());
    }

    private static void CheckPowerState(ActivityMain activityMain, boolean andRequest) {
        Intent intent = new Intent();
        String packageName = activityMain.getApplicationContext().getPackageName();
        PowerManager pm = (PowerManager) activityMain.getApplicationContext().getSystemService(Context.POWER_SERVICE);

        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            if(andRequest) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                activityMain.getApplicationContext().startActivity(intent);
                activityMain.btn_power_manager.setText(R.string.main_btn_power_manager_deactivated);
                activityMain.btn_power_manager.setEnabled(false);
            } else {
                activityMain.btn_power_manager.setText(R.string.main_btn_power_manager_activated);
                activityMain.btn_power_manager.setEnabled(true);
            }
        } else {
            activityMain.btn_power_manager.setText(R.string.main_btn_power_manager_deactivated);
            activityMain.btn_power_manager.setEnabled(false);
        }
    }

    private void CheckPermissionState(boolean andRequest) {
        if(keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_6_MANAGE_CALL)) {
//CALL_PHONE

            if (ActivityCompat.checkSelfPermission(ActivityMain.this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                if(!andRequest) {
                    ButtonPermissionActivate(this);
                }
                else {
                    ActivityCompat.requestPermissions(ActivityMain.this, new String[]{Manifest.permission.CALL_PHONE}, REQUEST_PERMISSION_CODE);
                }
            } else {
                ButtonPermissionDeactivate(this);
            }

            if (ActivityCompat.checkSelfPermission(ActivityMain.this, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                if(!andRequest) {
                    ButtonPermissionActivate(this);
                }
                else {
                    ActivityCompat.requestPermissions(ActivityMain.this, new String[]{Manifest.permission.ANSWER_PHONE_CALLS}, REQUEST_PERMISSION_CODE);
                }
            } else {
                ButtonPermissionDeactivate(this);
            }
            if (ActivityCompat.checkSelfPermission(ActivityMain.this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                if(!andRequest) {
                    ButtonPermissionActivate(this);
                }
                else {
                    ActivityCompat.requestPermissions(ActivityMain.this, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_PERMISSION_CODE);
                }
            } else {
                ButtonPermissionDeactivate(this);
            }

            } else {

                ButtonPermissionDeactivate(this);
            }

    }

    private static void ButtonPermissionActivate(ActivityMain activityMain) {
        activityMain.btn_sys_phone_permission.setText(R.string.main_btn_sys_phone_permission_activated);
        activityMain.btn_sys_phone_permission.setEnabled(true);
    }

    private static void ButtonPermissionDeactivate(ActivityMain activityMain) {
        activityMain.btn_sys_phone_permission.setEnabled(false);
        activityMain.btn_sys_phone_permission.setText(R.string.main_btn_sys_phone_permission_deactivated);
    }


}