package com.sateda.keyonekb2;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;

import static com.sateda.keyonekb2.SettingsActivity.*;

public class MainActivity extends Activity {

    private Button btn_test_key;
    private Button btn_power_manager;
    private Button btn_sys_kb_setting;

    private Button btn_sys_kb_accessibility_setting;
    private Button btn_settings;
    private Button btn_sys_phone_permission;

    private KbSettings kbSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        kbSettings = KbSettings.Get(getSharedPreferences(KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));
        btn_settings = (Button) findViewById(R.id.btn_settings);
        btn_test_key = (Button) findViewById(R.id.btn_test_key);
        btn_power_manager = (Button) findViewById(R.id.btn_power_manager);
        btn_sys_kb_setting = (Button) findViewById(R.id.btn_sys_kb_setting);
        btn_sys_kb_accessibility_setting = (Button) findViewById(R.id.btn_sys_kb_accessibility_setting);
        btn_sys_phone_permission = (Button) findViewById(R.id.btn_sys_phone_permission);

        btn_settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent switchActivityIntent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(switchActivityIntent);
            }
        });

        btn_test_key.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent switchActivityIntent = new Intent(MainActivity.this, KeyboardTestActivity.class);
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
                CheckPowerState(true);
            }
        });

        btn_sys_phone_permission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CheckPermissionState(true);
            }
        });

        CheckPermissionState(false);
        CheckPowerState(false);
    }

    private void CheckPowerState(boolean andRequest) {
        Intent intent = new Intent();
        String packageName = getApplicationContext().getPackageName();
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);

        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            if(andRequest) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                getApplicationContext().startActivity(intent);
                btn_power_manager.setText(R.string.btn_power_manager_deactivated);
                btn_power_manager.setEnabled(false);
            } else {
                btn_power_manager.setText(R.string.btn_power_manager_activated);
                btn_power_manager.setEnabled(true);
            }
        } else {
            btn_power_manager.setText(R.string.btn_power_manager_deactivated);
            btn_power_manager.setEnabled(false);
        }
    }

    private void CheckPermissionState(boolean andRequest) {
        if(kbSettings.GetBooleanValue(kbSettings.APP_PREFERENCES_6_MANAGE_CALL)) {

                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                    if(!andRequest) {
                        ButtonPermissionActivate();
                    }
                    else {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ANSWER_PHONE_CALLS}, REQUEST_PERMISSION_CODE);
                        ButtonPermissionDeactivate();
                    }
                } else {
                    ButtonPermissionDeactivate();
                }
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    if(!andRequest) {
                        ButtonPermissionActivate();
                    }
                    else {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_PERMISSION_CODE);
                        ButtonPermissionDeactivate();
                    }
                } else {
                    ButtonPermissionDeactivate();
                }

            } else {
            ButtonPermissionDeactivate();
        }

        }

    private void ButtonPermissionActivate() {
        btn_sys_phone_permission.setText(R.string.btn_sys_phone_permission_activated);
        btn_sys_phone_permission.setEnabled(true);
    }

    private void ButtonPermissionDeactivate() {
        btn_sys_phone_permission.setEnabled(false);
        btn_sys_phone_permission.setText(R.string.btn_sys_phone_permission_deactivated);
    }


}