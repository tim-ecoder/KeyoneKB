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
import androidx.core.app.ActivityCompat;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
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
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FileJsonUtils.Initialize(this);
        super.onCreate(savedInstanceState);
        k12KbSettings = K12KbSettings.Get(getSharedPreferences(K12KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));
        if (k12KbSettings.isDarkTheme()) {
            setTheme(R.style.AppTheme_Dark);
        }

        _this_act = this;
        setContentView(R.layout.activity_main);
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

        String text = String.format("App: %s\nVersion: %s\nBuild type: %s\nDevice: %s", APPLICATION_ID, VERSION_NAME, BUILD_TYPE, getDeviceFullMODEL());
        tv_version.setText(text);

        btn_settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent switchActivityIntent = new Intent(ActivityMain.this, ActivitySettings.class);
                startActivity(switchActivityIntent);
            }
        });

        Button btn_prediction_settings = (Button) findViewById(R.id.btn_prediction_settings);
        btn_prediction_settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent switchActivityIntent = new Intent(ActivityMain.this, ActivityPredictionSettings.class);
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
        applyPillBadges();
        updateGrantedGroup();
        setupLanguageSpinner();
    }

    private void setupLanguageSpinner() {
        final Spinner spinnerLang = (Spinner) findViewById(R.id.spinner_interface_language);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.interface_language_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLang.setAdapter(adapter);

        final int langFromPref = k12KbSettings.GetIntValue(k12KbSettings.APP_PREFERENCES_20_INTERFACE_LANG);
        spinnerLang.setSelection(langFromPref);

        spinnerLang.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != langFromPref) {
                    k12KbSettings.SetIntValue(k12KbSettings.APP_PREFERENCES_20_INTERFACE_LANG, position);
                    _this_act.recreate();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void applyPillBadges() {
        ScrollView scrollView = (ScrollView) findViewById(R.id.scrollView);
        LinearLayout rootLayout = (LinearLayout) scrollView.getChildAt(0);
        PillBadgeHelper.applyToContainer(rootLayout);
    }

    private boolean grantedGroupExpanded = false;

    private void updateGrantedGroup() {
        final LinearLayout grantedGroup = (LinearLayout) findViewById(R.id.granted_group);
        final LinearLayout grantedItems = (LinearLayout) findViewById(R.id.granted_group_items);
        final LinearLayout grantedHeader = (LinearLayout) findViewById(R.id.granted_group_header);
        final TextView grantedCount = (TextView) findViewById(R.id.granted_group_count);
        final TextView grantedChevron = (TextView) findViewById(R.id.granted_group_chevron);

        // Return any previously moved pills back to their original positions
        restoreGrantedPills(grantedItems);

        // Collect pills that are disabled (granted/configured)
        int[] pillIds = new int[] {
            R.id.pill_sys_kb_setting,
            R.id.pill_sys_kb_accessibility_setting,
            R.id.pill_sys_phone_permission,
            R.id.pill_power_manager,
            R.id.pill_file_permissions
        };

        ScrollView scrollView = (ScrollView) findViewById(R.id.scrollView);
        final LinearLayout rootLayout = (LinearLayout) scrollView.getChildAt(0);

        int grantedCount_ = 0;
        int visiblePermCount = 0;
        for (int id : pillIds) {
            LinearLayout pill = (LinearLayout) findViewById(id);
            if (pill == null) continue;
            Button btn = findButtonInPill(pill);
            if (btn != null && !btn.isEnabled()) {
                pill.setTag(R.id.pill_original_index, Integer.valueOf(rootLayout.indexOfChild(pill)));
                rootLayout.removeView(pill);
                pill.setAlpha(0.6f);
                grantedItems.addView(pill);
                grantedCount_++;
            } else {
                visiblePermCount++;
            }
        }

        if (grantedCount_ > 0) {
            grantedGroup.setVisibility(View.VISIBLE);
            grantedCount.setText("(" + grantedCount_ + ")");

            grantedItems.setVisibility(grantedGroupExpanded ? View.VISIBLE : View.GONE);
            grantedChevron.setText(grantedGroupExpanded ? "\u25B2" : "\u25BC");

            grantedHeader.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    grantedGroupExpanded = !grantedGroupExpanded;
                    grantedItems.setVisibility(grantedGroupExpanded ? View.VISIBLE : View.GONE);
                    grantedChevron.setText(grantedGroupExpanded ? "\u25B2" : "\u25BC");
                }
            });
        } else {
            grantedGroup.setVisibility(View.GONE);
        }

        // Hide "A." badge on Settings when all permissions are granted
        PillBadgeHelper.setBadgeVisible(rootLayout, R.id.btn_settings, visiblePermCount > 0);

        // Dynamic layout positioning based on whether permission buttons are visible
        repositionDynamicElements(rootLayout, visiblePermCount > 0);
    }

    private void repositionDynamicElements(LinearLayout rootLayout, boolean hasVisiblePermissions) {
        final TextView hintBox = (TextView) findViewById(R.id.tv_initial_hint);
        View pillSuggestions = findViewById(R.id.pill_prediction_settings);
        View pillAdvSettings = findViewById(R.id.pill_more_settings);
        View sectionSetup = findViewById(R.id.section_setup);
        View sectionActions = findViewById(R.id.section_actions);
        View grantedGroup = findViewById(R.id.granted_group);
        View langSwitcher = findViewById(R.id.pill_language_switcher);

        // Remove dynamic elements from current positions
        rootLayout.removeView(hintBox);
        rootLayout.removeView(pillSuggestions);
        rootLayout.removeView(pillAdvSettings);

        if (hasVisiblePermissions) {
            // Green hint right after SETUP section header, collapsed to 3 lines
            int setupIdx = rootLayout.indexOfChild(sectionSetup);
            rootLayout.addView(hintBox, setupIdx + 1);

            hintBox.setText(R.string.pref_first_install);
            hintBox.setSingleLine(false);
            hintBox.setMaxLines(3);
            hintBox.setEllipsize(android.text.TextUtils.TruncateAt.END);
            hintBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (hintBox.getMaxLines() == 3) {
                        hintBox.setSingleLine(false);
                        hintBox.setMaxLines(Integer.MAX_VALUE);
                        hintBox.setEllipsize(null);
                    } else {
                        hintBox.setSingleLine(false);
                        hintBox.setMaxLines(3);
                        hintBox.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    }
                }
            });

            // Suggestions + Advanced Settings after last visible permission button
            // (which is right before ACTIONS section header)
            int actionsIdx = rootLayout.indexOfChild(sectionActions);
            rootLayout.addView(pillSuggestions, actionsIdx);
            rootLayout.addView(pillAdvSettings, actionsIdx + 1);
        } else {
            // Green hint at bottom: different text, fully expanded, right before language switcher
            hintBox.setText(R.string.pref_hint_ready);
            hintBox.setSingleLine(false);
            hintBox.setMaxLines(Integer.MAX_VALUE);
            hintBox.setEllipsize(null);
            hintBox.setOnClickListener(null);
            hintBox.setClickable(false);

            int langIdx = rootLayout.indexOfChild(langSwitcher);
            rootLayout.addView(hintBox, langIdx);

            // Suggestions + Advanced Settings right after SETUP section + Settings button
            int setupIdx = rootLayout.indexOfChild(sectionSetup);
            rootLayout.addView(pillSuggestions, setupIdx + 2); // +1 for section, +1 for Settings btn
            rootLayout.addView(pillAdvSettings, setupIdx + 3);
        }
    }

    private void restoreGrantedPills(LinearLayout grantedItems) {
        ScrollView scrollView = (ScrollView) findViewById(R.id.scrollView);
        LinearLayout rootLayout = (LinearLayout) scrollView.getChildAt(0);

        while (grantedItems.getChildCount() > 0) {
            View pill = grantedItems.getChildAt(0);
            grantedItems.removeViewAt(0);
            pill.setAlpha(1.0f);
            Object savedIndex = pill.getTag(R.id.pill_original_index);
            if (savedIndex instanceof Integer) {
                int idx = (Integer) savedIndex;
                if (idx <= rootLayout.getChildCount()) {
                    rootLayout.addView(pill, idx);
                } else {
                    rootLayout.addView(pill);
                }
            } else {
                rootLayout.addView(pill);
            }
        }
    }

    private static Button findButtonInPill(LinearLayout pill) {
        for (int i = 0; i < pill.getChildCount(); i++) {
            View child = pill.getChildAt(i);
            if (child instanceof Button) return (Button) child;
        }
        return null;
    }

    private void CheckFilePermissions() {
        boolean permGranted = ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        // When permission is denied, File.exists() can't see files inside the folder.
        // Use directory existence as proxy: if K12Kb dir exists, JSONs may be there.
        boolean anyJson;
        if (permGranted) {
            anyJson = AnyJsonExists();
        } else {
            anyJson = FileJsonUtils.PATH != null && new java.io.File(FileJsonUtils.PATH).exists();
        }

        if(!anyJson && !permGranted) {
            btn_file_permissions.setEnabled(false);
            btn_file_permissions.setText(R.string.main_btn_file_permissions_not_need);
        } else if (permGranted) {
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
        applyPillBadges();
        updateGrantedGroup();
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
        boolean manageCallEnabled = k12KbSettings.GetBooleanValue(k12KbSettings.APP_PREFERENCES_6_MANAGE_CALL);

        if(manageCallEnabled) {
            boolean needCallPhone = ActivityCompat.checkSelfPermission(ActivityMain.this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED;
            boolean needAnswerCalls = ActivityCompat.checkSelfPermission(ActivityMain.this, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED;
            boolean needReadPhone = ActivityCompat.checkSelfPermission(ActivityMain.this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED;

            if (needCallPhone || needAnswerCalls || needReadPhone) {
                if (!andRequest) {
                    ButtonPermissionActivate(this);
                } else {
                    if (needCallPhone) {
                        ActivityCompat.requestPermissions(ActivityMain.this, new String[]{Manifest.permission.CALL_PHONE}, REQUEST_PERMISSION_CODE);
                    } else if (needAnswerCalls) {
                        ActivityCompat.requestPermissions(ActivityMain.this, new String[]{Manifest.permission.ANSWER_PHONE_CALLS}, REQUEST_PERMISSION_CODE);
                    } else {
                        ActivityCompat.requestPermissions(ActivityMain.this, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_PERMISSION_CODE);
                    }
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