package com.ai10.k12kb;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;

import static com.ai10.k12kb.ActivitySettings.*;
import static com.ai10.k12kb.BuildConfig.*;
import static com.ai10.k12kb.KeyboardLayoutManager.Instance;
import static com.ai10.k12kb.KeyboardLayoutManager.getDeviceFullMODEL;
import static com.ai10.k12kb.K12KbSettings.*;

public class ActivityMain extends Activity {

    private Button btn_power_manager;

    private Button btn_sys_phone_permission;

    private K12KbSettings k12KbSettings;

    Button btn_sys_kb_accessibility_setting;

    Button btn_sys_kb_setting;
    Button btn_keyboard_reload;
    Button btn_file_permissions;
    TextView tv_version;

    Activity _this_act;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FileJsonUtils.Initialize();
        super.onCreate(savedInstanceState);

        _this_act = this;
        setContentView(R.layout.activity_main);
        k12KbSettings = K12KbSettings.Get(getSharedPreferences(K12KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));
        Button btn_settings = (Button) findViewById(R.id.btn_settings);
        Button btn_test_key = (Button) findViewById(R.id.btn_test_key);
        btn_power_manager = (Button) findViewById(R.id.btn_power_manager);
        btn_sys_kb_setting = (Button) findViewById(R.id.btn_sys_kb_setting);
        btn_sys_kb_accessibility_setting = (Button) findViewById(R.id.btn_sys_kb_accessibility_setting);
        btn_sys_phone_permission = (Button) findViewById(R.id.btn_sys_phone_permission);
        btn_file_permissions = (Button) findViewById(R.id.btn_file_permissions);
        tv_version = (TextView) findViewById(R.id.tv_version);
        Button btn_more_settings = (Button) findViewById(R.id.btn_more_settings);

        btn_keyboard_reload = (Button) findViewById(R.id.btn_keyboard_reload);
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
                CheckCallPermissionState(true);
            }
        });



        this_act = this;

        btn_keyboard_reload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if(imm != null) {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(new Runnable() { public void run() { imm.showInputMethodPicker(); } }, 200);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            this_act.Redraw();
                        }
                    },3000);
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

        CheckFilePermissions();
        CheckCallPermissionState(false);
        CheckPowerState(this, false);
        UpdateKeyboardButton();
        UpdateAccessibilityButton();
        ChangeKeyboard();
    }

    private void CheckFilePermissions() {
        if(!AnyJsonExists() && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            btn_file_permissions.setEnabled(false);
            btn_file_permissions.setText(R.string.main_btn_file_permissions_not_need);
        } else if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            if(btn_file_permissions.isEnabled()) {
                btn_file_permissions.setEnabled(false);
                btn_file_permissions.setText(R.string.main_btn_file_permissions_activated);

                btn_keyboard_reload.setText(R.string.main_btn_keyboard_reload_need);
                btn_keyboard_reload.setBackgroundColor(Color.YELLOW);

            } else {
                btn_keyboard_reload.setText(R.string.main_btn_keyboard_reload);
                btn_keyboard_reload.setBackground(btn_file_permissions.getBackground());
            }

        } else {
            btn_file_permissions.setEnabled(true);
            btn_file_permissions.setText(R.string.main_btn_file_permissions_needed);
            btn_file_permissions.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(_this_act, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_CODE);
                        _this_act.recreate();
                    }
                }
            });
        }
    }

    private boolean AnyJsonExists()
    {
        if(FileJsonUtils.JsonsExist(RES_KEYBOARD_LAYOUTS)) return true;
        if(FileJsonUtils.JsonsExist(RES_KEYBOARD_CORE)) return true;
        if(K12KbIME.Instance != null)
            if(FileJsonUtils.JsonsExist(K12KbIME.Instance.keyboard_mechanics_res)) return true;
        if(FileJsonUtils.JsonsExist(K12KbAccessibilityService.K12KbAccServiceOptions.ResName)) return true;
        if(Instance == null)
            return false;
        for (KeyboardLayout keyboardLayout: Instance.KeyboardLayoutList) {
            if(FileJsonUtils.JsonsExist(keyboardLayout.Resources.KeyboardMapping)) return true;
            if(FileJsonUtils.JsonsExist(keyboardLayout.AltModeLayout)) return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        CheckFilePermissions();
        CheckCallPermissionState(false);
        CheckPowerState(this, false);
        UpdateKeyboardButton();
        UpdateAccessibilityButton();
        ChangeKeyboard();
    }

    public void Redraw() {
        onResume();
    }

    ActivityMain this_act;

    void ChangeKeyboard() {
        if(!isKbEnabled())
            return;
        if (!Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD).contains(".K12KbIME")) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm != null) {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(new Runnable() { public void run() { imm.showInputMethodPicker(); } }, 200);
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

    private void CheckCallPermissionState(boolean andRequest) {
        if(k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_6_MANAGE_CALL)) {
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