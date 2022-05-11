
package com.sateda.keyonekb;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.media.AudioManager;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputBinding;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SuggestionsInfo;
import android.widget.Toast;

import com.sateda.keyonekb.input.CallStateCallback;

import org.xmlpull.v1.XmlPullParser;

import java.lang.reflect.Method;

import static android.content.ContentValues.TAG;
import static android.view.View.INVISIBLE;

public class KeyoneIME extends InputMethodService implements KeyboardView.OnKeyboardActionListener, SpellCheckerSession.SpellCheckerSessionListener, View.OnTouchListener {

    private static final int MAX_KEY_COUNT = 50;
    private static final boolean DEBUG = true;

    public static final String APP_PREFERENCES = "kbsettings";
    public static final String APP_PREFERENCES_RU_LANG = "switch_ru_lang";
    public static final String APP_PREFERENCES_TRANSLIT_RU_LANG = "switch_translit_ru_lang";
    public static final String APP_PREFERENCES_UA_LANG = "switch_ua_lang";
    public static final String APP_PREFERENCES_SENS_BOTTON_BAR = "sens_botton_bar";
    public static final String APP_PREFERENCES_SHOW_TOAST = "show_toast";
    public static final String APP_PREFERENCES_LONGPRESS_ALT = "longpress_alt";
    public static final String APP_PREFERENCES_SPACE_ACCEPT_CALL = "space_accept_call";
    public static final String APP_PREFERENCES_ALT_SPACE = "alt_space";
    public static final String APP_PREFERENCES_FLAG = "flag";
    public static final String APP_PREFERENCES_HEIGHT_BOTTON_BAR = "height_botton_bar";
    public static final String APP_PREFERENCES_POCKET_PATCH = "pocket_patch";

    private CallStateCallback callStateCallback;

    private NotificationManager notificationManager;
    private CandidateView mCandidateView;

    private SatedaKeyboardView keyboardView;
    private Keyboard keyboard_empty;
    private Keyboard keyboard_symbol;

    private Boolean fixBbkContact = false; // костыль для приложения Блекбери контакты
    private Boolean fixBbkLauncher = false;

    private SharedPreferences mSettings;

    private boolean isEnglishKb = false;
    private int langNum = 0;
    private int langCount = 1;
    private int[] langArray;

    private Toast toast;

    private boolean ctrlPressed = false; // только первая буква будет большая

    private long mShiftPressTime;
    private boolean shiftPressFirstButtonBig; // только первая буква будет большая
    private boolean shiftPressFirstButtonBigDoublePress; // только первая буква будет большая (для двойного нажатия на одну и туже кнопку)
    private boolean shiftAfterPoint; // большая буква после точки (все отдано на откуп операционке, скорее всего надо будет удалить эту переменную)
    private boolean shiftPressAllButtonBig; //все следующий буквы будут большие
    private boolean shiftPressed; //нажатие клавишь с зажатым альтом

    private long mCtrlPressTime;
    private long mAltPressTime;
    private boolean altPressFirstButtonBig;
    private boolean altPressAllButtonBig;
    private boolean altShift;
    private boolean showSymbol;
    private boolean navigationSymbol;
    private boolean fnSymbol;

    private boolean menuKeyPressed; //нажатие клавишь с зажатым альтом

    private boolean altPressed; //нажатие клавишь с зажатым альтом
    private boolean altPlusBtn;

    private String langStr = "";
    private String lastPackageName = "";

    private float touchX;

    private android.support.v7.app.NotificationCompat.Builder builder;
    private StringBuilder mComposing = new StringBuilder();


    private int press_code_true; // код клавиши, при отпускании которой не нужно обрабатывать событие отпускание
    private int[] scan_code;
    private int[] one_press;
    private int[] one_press_shift;
    private int[] double_press;
    private int[] double_press_shift;
    private int[] shift;
    private int[] alt;
    private String[] alt_popup;
    private int[] alt_shift;
    private String[] alt_shift_popup;

    private int prev_key_press_btn_r0; //repeat 0 - одинарное повторное нажатие
    private int prev_key_press_btn_r1; //repeat 1 - удержание при повторном нажатии
    private long prev_key_press_time = 0;

    //settings
    private int pref_height_botton_bar = 10;
    private int sens_botton_bar = 10;
    private boolean show_toast = false;
    private boolean pref_alt_space = true;
    private boolean pref_pocket_patch = false;
    private boolean pref_flag = false;
    private boolean pref_longpress_alt = false;

    private boolean pref_touch_keyboard = false;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate() {
        super.onCreate();

        callStateCallback = new CallStateCallback();
        TelephonyManager tm = getTelephonyManager();
        if (tm != null) {
            tm.listen(callStateCallback, PhoneStateListener.LISTEN_CALL_STATE);
        }

        mShiftPressTime = 0;
        shiftPressFirstButtonBig = false;
        shiftPressFirstButtonBigDoublePress = false;
        shiftAfterPoint = false;
        shiftPressAllButtonBig  = false;
        shiftPressed = false;
        mCtrlPressTime = 0;
        mAltPressTime = 0;
        altPressFirstButtonBig = false;
        altPressAllButtonBig = false;
        menuKeyPressed = false;
        altShift = false;
        altPressed = false;
        altPlusBtn = false;
        showSymbol = false;
        navigationSymbol = false;
        fnSymbol = false;
        ctrlPressed = false;

        pref_height_botton_bar = 10;

        show_toast = false;
        pref_alt_space = true;
        pref_longpress_alt = false;

        initKeys();
        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        loadSetting();

        keyboard_empty = new SatedaKeyboard(this, R.xml.space_empty, 70+pref_height_botton_bar*5);
        //keyboard_empty = new SatedaKeyboard(this, R.xml.space_empty);
        keyboard_symbol = new SatedaKeyboard(this, R.xml.symbol);

        keyboardView = (SatedaKeyboardView) getLayoutInflater().inflate(R.layout.keyboard,null);
        keyboardView.setKeyboard(keyboard_empty);
        keyboardView.setOnKeyboardActionListener(this);
        keyboardView.setOnTouchListener(this);
        keyboardView.setPreviewEnabled(false);
        keyboardView.setService(this);
        keyboardView.clearAnimation();
        keyboardView.showFlag(pref_flag);

        notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName("com.sateda.keyonekb.satedakeyboard", "com.sateda.keyboard.keyonekb.MainActivity");
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        builder = new android.support.v7.app.NotificationCompat.Builder(getApplicationContext());

        builder.setSmallIcon(R.mipmap.ic_rus_small);
        builder.setContentTitle("Русский");
        builder.setOngoing(true);
        builder.setAutoCancel(false);
        builder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        notificationManager.notify(1, builder.build());

        ChangeLanguage();
    }

    private void loadSetting(){
        langCount = 1;
        show_toast = false;
        sens_botton_bar = 10;

        boolean lang_ru_on = true;
        boolean lang_translit_ru_on = false;
        boolean lang_ua_on = false;

        if(mSettings.contains(APP_PREFERENCES_POCKET_PATCH)) {
            pref_pocket_patch = mSettings.getBoolean(APP_PREFERENCES_POCKET_PATCH, false);
        }

        if(mSettings.contains(APP_PREFERENCES_RU_LANG)) {
            lang_ru_on = mSettings.getBoolean(APP_PREFERENCES_RU_LANG, true);
            if(lang_ru_on) langCount++;
        }else{
            if(lang_ru_on) langCount++;
        }

        if(mSettings.contains(APP_PREFERENCES_TRANSLIT_RU_LANG)) {
            lang_translit_ru_on = mSettings.getBoolean(APP_PREFERENCES_TRANSLIT_RU_LANG, false);
            if(lang_translit_ru_on && !pref_pocket_patch) langCount++;
        }

        if(mSettings.contains(APP_PREFERENCES_UA_LANG)) {
            lang_ua_on = mSettings.getBoolean(APP_PREFERENCES_UA_LANG, false);
            if(lang_ua_on && !pref_pocket_patch) langCount++;
        }

        if(mSettings.contains(APP_PREFERENCES_SENS_BOTTON_BAR)) {
            sens_botton_bar = mSettings.getInt(APP_PREFERENCES_SENS_BOTTON_BAR, 10);
        }

        if(mSettings.contains(APP_PREFERENCES_SHOW_TOAST)) {
            show_toast = mSettings.getBoolean(APP_PREFERENCES_SHOW_TOAST, false);
        }

        if(mSettings.contains(APP_PREFERENCES_ALT_SPACE)) {
            pref_alt_space = mSettings.getBoolean(APP_PREFERENCES_ALT_SPACE, true);
        }

        if(mSettings.contains(APP_PREFERENCES_ALT_SPACE)) {
            pref_longpress_alt = mSettings.getBoolean(APP_PREFERENCES_LONGPRESS_ALT, false);
        }

        if(mSettings.contains(APP_PREFERENCES_FLAG)) {
            pref_flag = mSettings.getBoolean(APP_PREFERENCES_FLAG, false);
        }
        if(mSettings.contains(APP_PREFERENCES_HEIGHT_BOTTON_BAR)) {
            pref_height_botton_bar = mSettings.getInt(APP_PREFERENCES_HEIGHT_BOTTON_BAR, 10);
        }

        langArray = new int[langCount];
        langArray[0] = R.xml.english_hw;
        langCount = 1;
        if(lang_ru_on){
            if(!pref_pocket_patch) {
                langArray[langCount] = R.xml.russian_hw;
            }else{
                langArray[langCount] = R.xml.pocket_russian_hw;
            }
            langCount++;
        }
        if(lang_translit_ru_on && !pref_pocket_patch){
            langArray[langCount] = R.xml.russian_translit_hw;
            langCount++;
        }
        if(lang_ua_on && !pref_pocket_patch){
            langArray[langCount] = R.xml.ukraine_hw;
            langCount++;
        }
        langCount-=1;
        if(langNum > langCount) langNum = 0;
    }

    @Override
    public void onPress(int primaryCode) {
        Log.d(TAG, "onPress");
    }

    @Override
    public void onRelease(int primaryCode) {

    }

    @Override public void onFinishInput() {
        if(showSymbol == true) {
            showSymbol = false;
            altPressFirstButtonBig = false;
            altPressAllButtonBig = false;
            altShift = false;
            UpdateNotify();
        }

        if(lastPackageName.equals("com.sateda.keyonekb")) loadSetting();
        keyboardView.showFlag(pref_flag);
        if(keyboard_empty.getHeight() != 70 + pref_height_botton_bar * 5) keyboard_empty = new SatedaKeyboard(this, R.xml.space_empty, 70+pref_height_botton_bar*5);

        Log.d(TAG, "onFinishInput ");
    }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        Log.d(TAG, "onStartInput "+attribute.packageName+" "+attribute.label);
        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.

        if(attribute.packageName.equals("com.blackberry.contacts")) {
            fixBbkContact = true;
        }else{
            fixBbkContact = false;
        }

        if(attribute.packageName.equals("com.blackberry.blackberrylauncher")) {
            fixBbkLauncher = true;
        }else{
            fixBbkLauncher = false;
        }

        if(!attribute.packageName.equals(lastPackageName))
        {
            lastPackageName = attribute.packageName;
            navigationSymbol = false;
            fnSymbol = false;
            keyboardView.setFnSymbol(fnSymbol);
            if(!keyboardView.isShown()) {
                keyboardView.setVisibility(View.VISIBLE);
            }
        }


        if (!restarting) {
            Log.d(TAG, "onStartInput !restarting");
        }


        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
            case InputType.TYPE_CLASS_NUMBER:
            case InputType.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard, with
                // no extra features.
                altPressAllButtonBig = true;
                break;

            case InputType.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                altPressAllButtonBig = true;
                break;

            case InputType.TYPE_CLASS_TEXT:
                // This is general text editing.  We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).
                altPressAllButtonBig = false;
                altPressFirstButtonBig = false;

                // We now look for a few special variations of text that will
                // modify our behavior.
                int variation = attribute.inputType & InputType.TYPE_MASK_VARIATION;
                if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                    //mPredictionOn = false;
                }

                if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || variation == InputType.TYPE_TEXT_VARIATION_URI
                        || variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                    // Our predictions are not useful for e-mail addresses
                    // or URIs.
                    //mPredictionOn = false;
                }

                if ((attribute.inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // own it displaying its own UI.
                    //mPredictionOn = false;
                    //mCompletionOn = isFullscreenMode();
                }

                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                updateShiftKeyState(attribute);
                break;

            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                altPressAllButtonBig = false;
                altPressFirstButtonBig = false;
                shiftPressFirstButtonBig = false;
                shiftPressAllButtonBig = false;
                updateShiftKeyState(attribute);
        }

        UpdateNotify();
        // Update the label on the enter key, depending on what the application
        // says it will do.
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        keyboardView.hidePopup(false);
        Log.d(TAG, "onKeyDown "+event);
        //обработка главного экрана Блекбери
        //он хочет получать только родные клавиши, по этому ему отправляем почти все клавиши неизменными
        if(fixBbkLauncher &&  !this.isInputViewShown() && event.getScanCode() != 11
                                                       && keyCode != KeyEvent.KEYCODE_SHIFT_LEFT
                                                       && keyCode != KeyEvent.KEYCODE_SPACE
                                                       && keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT
                                                       && keyCode != KeyEvent.KEYCODE_ALT_LEFT
                                                       && event.getScanCode() != 100
                                                       && event.getScanCode() != 110
                                                       && event.getScanCode() != 183){
            Log.d(TAG, "Oh! this fixBbkLauncher "+fixBbkLauncher);
            return super.onKeyDown(keyCode, event);
        }else if(fixBbkLauncher &&  !this.isInputViewShown() && event.getScanCode() == 11 && event.getRepeatCount() == 0 ){
            ChangeLanguage();
            return true;
        }else if(fixBbkLauncher &&  !this.isInputViewShown() && event.getScanCode() == 11 && event.getRepeatCount() == 1 ){
            pref_touch_keyboard = !pref_touch_keyboard;
            ChangeLanguageBack();
            return true;
        }

        long now = System.currentTimeMillis();
        InputConnection ic = getCurrentInputConnection();

        // Обработайте нажатие, верните true, если обработка выполнена
        boolean is_double_press = false;
        boolean shift = false;
        boolean alt = false;
        int navigationKeyCode = 0;
        int code = 0;

        //нажатие клавиши CTRL
        if((!pref_pocket_patch && (keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT || event.getScanCode() == 110)) || (pref_pocket_patch && event.getScanCode() == 183)){
            int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
            if(ic!=null)ic.sendKeyEvent(new KeyEvent(
                    now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta));
            ctrlPressed = true;
            //show hide display keyboard
            if(shiftPressed && keyboardView.isShown()) {
                keyboardView.setVisibility(View.GONE);
                updateShiftKeyState(getCurrentInputEditorInfo());
            }else if(shiftPressed && !keyboardView.isShown()) {
                keyboardView.setVisibility(View.VISIBLE);
                updateShiftKeyState(getCurrentInputEditorInfo());
            }
            //Двойное нажатие Ctrl активирует сенсор на клавиатуре
            if(event.getRepeatCount() == 0) {
                if (mCtrlPressTime + 500 > now && !shiftPressed) {
                    pref_touch_keyboard = !pref_touch_keyboard;
                    mCtrlPressTime = 0;
                    UpdateNotify();
                } else {
                    mCtrlPressTime = now;
                }
            }
            return true;
        }

        if(ctrlPressed && keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT && event.getScanCode() != 110 ||
                (ctrlPressed && pref_pocket_patch && event.getScanCode() != 183)){
            int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
            if(ic!=null)ic.sendKeyEvent(new KeyEvent(
                    now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta));
            updateShiftKeyState(getCurrentInputEditorInfo());
            return true;
        }

        //нажатие клавиши ALT
        if ((!pref_pocket_patch && keyCode == KeyEvent.KEYCODE_ALT_LEFT) || (pref_pocket_patch && event.getScanCode() == 100)){

            if (handleAltOnCalling()) {
                return true;
            }

            if(pref_pocket_patch && shiftPressed == true && event.getRepeatCount() == 0){
                    ChangeLanguage();
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    UpdateNotify();
                    return true;
            }
            if(showSymbol == true){
                showSymbol = false;
                altPressFirstButtonBig = false;
                altPressAllButtonBig = false;
                altShift = false;
                updateShiftKeyState(getCurrentInputEditorInfo());
            }else  if(altPressAllButtonBig == false && altPressFirstButtonBig == false){
                altShift = false;
                mAltPressTime = now;
                altPressFirstButtonBig = true;
            } else if (!altPressed && altPressFirstButtonBig == true && mAltPressTime + 1000 > now){
                altPressFirstButtonBig = false;
                altPressAllButtonBig = true;
            } else if (!altPressed && altPressFirstButtonBig == true && mAltPressTime + 1000 < now){
                altPressFirstButtonBig = false;
                altPressAllButtonBig = false;
                altShift = false;
                updateShiftKeyState(getCurrentInputEditorInfo());
            } else if (!altPressed && altPressAllButtonBig == true){
                altPressFirstButtonBig = false;
                altPressAllButtonBig = false;
                altShift = false;
                updateShiftKeyState(getCurrentInputEditorInfo());
            }
            altPressed = true;
            UpdateNotify();
            return true;
        }

        //Нажатие клавиши SYM
        if (((!pref_pocket_patch && event.getScanCode() == 100) || (pref_pocket_patch && event.getScanCode() == 127)) && event.getRepeatCount() == 0 || (keyCode == KeyEvent.KEYCODE_3 && DEBUG)){

            if(altPressed){ //вызов меню
                menuKeyPressed = true;
                if(ic!=null)ic.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
                return true;
            }

            if(navigationSymbol) {
                navigationSymbol = false;
                showSymbol = false;
                altPressFirstButtonBig = false;
                altPressAllButtonBig = false;
                updateShiftKeyState(getCurrentInputEditorInfo());
            }else if(!showSymbol){
                showSymbol = true;
                altShift = true;
                altPressAllButtonBig = true;
                altPressFirstButtonBig = false;
            } else if(showSymbol && altShift){
                altShift = false;
            } else if(showSymbol && !altShift) {
                showSymbol = false;
                altPressFirstButtonBig = false;
                altPressAllButtonBig = false;
                updateShiftKeyState(getCurrentInputEditorInfo());
            }

            UpdateNotify();
            return true;
        } else if (((!pref_pocket_patch && event.getScanCode() == 100) || (pref_pocket_patch && event.getScanCode() == 127))
                      && event.getRepeatCount() >= 1 && !navigationSymbol){
            navigationSymbol = true;
            fnSymbol = false;
            keyboardView.setFnSymbol(fnSymbol);
            UpdateNotify();
            return true;
        } else if (((!pref_pocket_patch && event.getScanCode() == 100) || (pref_pocket_patch && event.getScanCode() == 127))
                    && event.getRepeatCount() >= 1 && navigationSymbol){
            return true;
        }

        //навигационные клавиши
        if(navigationSymbol &&
                ((event.getScanCode() == 11) ||
                        (event.getScanCode() == 5) ||
                        (event.getScanCode() >= 16 && event.getScanCode() <= 25) ||
                        (event.getScanCode() >= 30 && event.getScanCode() <= 38) ||
                        (event.getScanCode() >= 44 && event.getScanCode() <= 50)))
        {
            navigationKeyCode = getNavigationCode(event.getScanCode());

            Log.d(TAG, "navigationKeyCode "+navigationKeyCode);
            if(navigationKeyCode == -7)
            {
                fnSymbol = !fnSymbol;
                UpdateNotify();
                keyboardView.setFnSymbol(fnSymbol);
                return true;
            }
            if(ic!=null && navigationKeyCode != 0) ic.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_DOWN, navigationKeyCode));
            return true;
        }

        if(altPressFirstButtonBig || altPressAllButtonBig || altPressed) alt = true;
        //Log.d(TAG, "onKeyDown altPressFirstButtonBig="+altPressFirstButtonBig+" altPressAllButtonBig="+altPressAllButtonBig+" altPressed="+altPressed);

        if (!shiftPressed && (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || (keyCode == KeyEvent.KEYCODE_2 && DEBUG))){
            if (handleShiftOnCalling()) {
                return true;
            }
            if(!alt){
                if(shiftPressAllButtonBig == false && shiftPressFirstButtonBig == false){
                    mShiftPressTime = now;
                    shiftPressFirstButtonBig = true;
                } else if (shiftPressFirstButtonBig == true && mShiftPressTime + 1000 > now){
                    shiftPressFirstButtonBig = false;
                    shiftPressAllButtonBig = true;
                } else if (shiftPressFirstButtonBig == true && mShiftPressTime + 1000 < now){
                    shiftPressFirstButtonBig = false;
                    shiftPressAllButtonBig = false;
                } else if (shiftPressAllButtonBig == true){
                    shiftPressFirstButtonBig = false;
                    shiftPressAllButtonBig = false;
                }
            }else{
                altShift = !altShift;
            }
            shiftPressed = true;
            UpdateNotify();
            return true;

        }
        if(shiftPressFirstButtonBig || shiftPressAllButtonBig || shiftPressed) shift = true;
        if(alt) shift = altShift;
        //Log.d(TAG, "onKeyDown shiftPressFirstButtonBig="+shiftPressFirstButtonBig+" shiftPressAllButtonBig="+shiftPressAllButtonBig+" altShift="+altShift);


        if(event.getScanCode() == 11 && event.getRepeatCount() == 0 && alt == false ){
            ChangeLanguage();
            return true;
        }else if(event.getScanCode() == 11 && event.getRepeatCount() == 1 && alt == false ){
            pref_touch_keyboard = !pref_touch_keyboard;
            ChangeLanguageBack();
            return true;
        }else if(event.getScanCode() == 11 && event.getRepeatCount() > 1 && alt == false ){
            return true;
        }else if(event.getScanCode() == 11 && alt == true ){
            if(ic!=null)ic.commitText(String.valueOf((char) 48), 1);
            return true;
        }

        if(event.getRepeatCount() == 0) {
            code = KeyToButton(event.getScanCode(), alt, shift, false);
            //if (!alt && code != 0 && !shiftPressFirstButtonBigDoublePress) shiftPressFirstButtonBigDoublePress = shiftPressFirstButtonBig;
        }else if(event.getRepeatCount() == 1) { //удержание клавиши
            if(!pref_longpress_alt) {
                code = KeyToButton(event.getScanCode(), alt, true, false);
            }else {
                alt = true;
                code = KeyToButton(event.getScanCode(), alt, false, false);
            }
            if(code != 0 && ic!=null)ic.deleteSurroundingText(1,0);
        }else{
            code = KeyToButton(event.getScanCode(), alt, shift, false);
            if(code != 0) return true;
        }
        int code_double_press = 0;
        Log.d(TAG, "onKeyDown prev_key_press_btn_r0="+prev_key_press_btn_r0+" event.getScanCode() ="+event.getScanCode()+" shiftPressFirstButtonBig="+shiftPressFirstButtonBig);
        if(prev_key_press_btn_r0 == event.getScanCode() && event.getRepeatCount() == 0 &&  now < prev_key_press_time+400){
            if(shiftPressFirstButtonBigDoublePress) {
                code_double_press = KeyToButton(event.getScanCode(), alt, shiftPressFirstButtonBigDoublePress, true);
            }else{
                code_double_press = KeyToButton(event.getScanCode(), alt, shift, true);
            }
            if(code != code_double_press && code_double_press != 0){
                is_double_press = true;
                if(ic!=null)ic.deleteSurroundingText(1,0);
                code = code_double_press;
            }
        }else if(prev_key_press_btn_r1 == event.getScanCode() && event.getRepeatCount() == 1){
            is_double_press = true;
            code = KeyToButton(event.getScanCode(), alt, true, true);;
        }

        if(code != 0){
            if(fixBbkContact && !isEnglishKb){
                if(ic!=null){
                    ic.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SEARCH));
                    ic.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SEARCH));
                }
                fixBbkContact = false;
            }
            mAltPressTime = 0;
            if(event.isAltPressed()) altPlusBtn = true;
            if(pref_alt_space == false && altPressFirstButtonBig == true) altPressFirstButtonBig = false;
            if(is_double_press || alt == true){
                prev_key_press_btn_r1 = prev_key_press_btn_r0;
                prev_key_press_btn_r0 = 0;
                if (shiftPressFirstButtonBig == true){
                    shiftPressFirstButtonBig = false;
                    UpdateNotify();
                }
                shiftPressFirstButtonBigDoublePress = false;
            }else{
                prev_key_press_time = now;
                prev_key_press_btn_r0 = event.getScanCode();
                prev_key_press_btn_r1 = 0;
                Log.d(TAG, "onKeyDown shiftPressFirstButtonBig="+shiftPressFirstButtonBig);
                if (shiftPressFirstButtonBig == false) shiftPressFirstButtonBigDoublePress = false;
                if (shiftPressFirstButtonBig == true){
                    shiftPressFirstButtonBigDoublePress = true;
                    shiftPressFirstButtonBig = false;
                    UpdateNotify();
                }
            }

            if(ic!=null)
            {
                //ic.commitText(String.valueOf((char) code), 1);
                sendKeyChar((char) code);
                press_code_true = event.getScanCode();
            }
            else
            {
                Log.d(TAG, "onKeyDown ic==null");
            }
            if(!this.isInputViewShown() && ic!=null){
                if(ic.getTextBeforeCursor(1,0).length() > 0) this.showWindow(true);
            }
            //это отслеживать больше не нужно. По этому закоментил
            //if(shiftAfterPoint && isAlphabet(code)) shiftAfterPoint = false;
            //if(!shiftPressAllButtonBig && (code == 46 || code == 63 ||  code == 33 || code == 191)) shiftAfterPoint = true;
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                prev_key_press_btn_r0 = 0;
                if (altPressFirstButtonBig == true){
                    altPressFirstButtonBig = false;
                    altShift = false;
                    UpdateNotify();
                }
                updateShiftKeyState(getCurrentInputEditorInfo());
                return true;
            case KeyEvent.KEYCODE_SPACE:

                if (altPressFirstButtonBig == true){
                    altPressFirstButtonBig = false;
                    altShift = false;
                    UpdateNotify();
                }
                if(shiftPressed && event.getRepeatCount() == 0){
                    ChangeLanguage();
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    UpdateNotify();
                    return true;
                }
                Log.d(TAG, "KEYCODE_SPACE prev_key_press_btn_r0 "+prev_key_press_btn_r0+" "+keyCode );

                if(this.isInputViewShown() && prev_key_press_btn_r0 == keyCode && now < prev_key_press_time+500 && ic!=null){
                    CharSequence back_letter = ic.getTextBeforeCursor(2,0);
                    Log.d(TAG, "KEYCODE_SPACE back_letter "+back_letter);
                    if(back_letter.length() == 2) {
                        if (Character.isLetterOrDigit(back_letter.charAt(0)) && back_letter.charAt(1) == ' ') {
                            ic.deleteSurroundingText(1, 0);
                            ic.commitText(".", 1);
                        }
                    }
                }else if(prev_key_press_btn_r0 == keyCode && now < prev_key_press_time+500 && handleShiftOnCalling()){
                    //Accept call
                    return true;
                }
                if(ic!=null)ic.commitText(String.valueOf((char) 32), 1);
                Log.d(TAG, "KEYCODE_SPACE");
                updateShiftKeyState(getCurrentInputEditorInfo());
                prev_key_press_time = now;
                prev_key_press_btn_r0 = keyCode;
                return true;
            case KeyEvent.KEYCODE_DEL:
                Log.d(TAG, "KEYCODE_DEL");
                prev_key_press_btn_r0 = 0;
                if(!shiftPressed) {
                    keyDownUp(KeyEvent.KEYCODE_DEL);
                }else{
                    if(ic!=null)ic.deleteSurroundingText(0,1);
                }
                updateShiftKeyState(getCurrentInputEditorInfo());
                return true;

            default:
                prev_key_press_btn_r0 = 0;
        }

        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyUp "+event);
        int navigationKeyCode = 0;
        InputConnection ic = getCurrentInputConnection();
        // Обработайте отпускание клавиши, верните true, если обработка выполнена
        if ((!pref_pocket_patch && keyCode == KeyEvent.KEYCODE_ALT_LEFT) || (pref_pocket_patch && event.getScanCode() == 100) || (keyCode == KeyEvent.KEYCODE_1 && DEBUG)){
            if(menuKeyPressed){ //вызов меню
                menuKeyPressed = false;
                ic.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));
            }
            altPressed = false;
            if(altPlusBtn){
                altPressFirstButtonBig = false;
                altPressAllButtonBig = false;
                UpdateNotify();
                altPlusBtn = false;
                updateShiftKeyState(getCurrentInputEditorInfo());
            }
        }
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || (keyCode == KeyEvent.KEYCODE_2 && DEBUG)){
            shiftPressed = false;
        }

        if((!pref_pocket_patch && (keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT || event.getScanCode() == 110)) || (pref_pocket_patch && event.getScanCode() == 183)){
            int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
            long now = System.currentTimeMillis();
            getCurrentInputConnection().sendKeyEvent(new KeyEvent(
                    now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta));
            ctrlPressed = false;
            return true;
        }

        if(keyCode == 100 && menuKeyPressed){ //вызов меню
            menuKeyPressed = false;
            ic.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));
        }

        if(navigationSymbol &&
                ((event.getScanCode() == 11) ||
                        (event.getScanCode() == 5) ||
                        (event.getScanCode() >= 16 && event.getScanCode() <= 25) ||
                        (event.getScanCode() >= 30 && event.getScanCode() <= 38) ||
                        (event.getScanCode() >= 44 && event.getScanCode() <= 50)))
        {
            navigationKeyCode = getNavigationCode(event.getScanCode());

            if(navigationKeyCode == -7) return true;
            if(ic!=null && navigationKeyCode != 0) ic.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_UP, navigationKeyCode));
            return true;
        }

        if(keyCode == KeyEvent.KEYCODE_ENTER ||keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_DEL) return true;

        //если клавиша обработана при нажатии, то она же будет обработана и при отпускании
        if(press_code_true == event.getScanCode()) return true;

        return false;
    }

    public int getNavigationCode(int scanCode) {
        int keyEventCode = 0;
        switch (scanCode){
            case 16: //Q
                keyEventCode = 111; //ESC
                break;
            case 17: //W (1)
                if(fnSymbol) keyEventCode = 131; //F1
                break;
            case 18: //E (2)
                if(fnSymbol) keyEventCode = 132; //F2
                break;
            case 19: //R (3)
                if(fnSymbol) keyEventCode = 133; //F3
                break;
            case 20: //T
                keyEventCode = 0; //-----------------------------------------------------
                break;
            case 21: //Y
                keyEventCode = 122; //Home
                break;
            case 22: //U
                keyEventCode = 19; //Arrow Up
                break;
            case 23: //I
                keyEventCode = 123; //END
                break;
            case 24: //O
                keyEventCode = 92; //Page Up
                break;
            case 25: //P
                keyEventCode = -7; //FN
                break;

            case 30: //A
                keyEventCode = 61; //Tab
                break;
            case 31: //S (4)
                if(fnSymbol) keyEventCode = 134; //F4
                break;
            case 32: //D (5)
                if(fnSymbol) keyEventCode = 135; //F5
                break;
            case 33: //F (6)
                if(fnSymbol) keyEventCode = 136; //F6
                break;
            case 34: //G
                keyEventCode = 0; //-----------------------------------------------------
                break;
            case 35: //H
                keyEventCode = 21; //Arrow Left
                break;
            case 36: //J
                keyEventCode = 20; //Arrow Down
                break;
            case 37: //K
                keyEventCode = 22; //Arrow Right
                break;
            case 38: //L
                keyEventCode = 93; //Arrow Right
                break;

            case 44: //Z (7)
                if(fnSymbol) keyEventCode = 137; //F7
                break;
            case 45: //X (8)
                if(fnSymbol) keyEventCode = 138; //F8
                break;
            case 46: //C (9)
                if(fnSymbol) keyEventCode = 139; //F9
                break;

            case 11: //0
                if(fnSymbol) keyEventCode = 140; //F10
                break;

            default:
                keyEventCode = 0;
        }

        return keyEventCode;
    }

    @Override
    public boolean onKeyLongPress(int keyCode,KeyEvent event){
        Log.d(TAG, "onKeyLongPress "+event);
        if(keyCode==KeyEvent.KEYCODE_VOLUME_UP){
            //Do your stuff here
            return true;
        }
        return onKeyLongPress(keyCode,event);
    }

    @SuppressLint("InflateParams")
    @Override
    public View onCreateInputView() {
        Log.d(TAG, "onCreateInputView");
        //keyboardView = (KeyboardView) getLayoutInflater().inflate(R.layout.keyboard,null);
        //keyboard = new Keyboard(this, R.xml.qwerty);
        //keyboardView.setKeyboard(keyboard);
        keyboardView.setOnKeyboardActionListener(this);
        return keyboardView;
    }

    @Override
    public void onUpdateSelection(final int oldSelStart, final int oldSelEnd,
                                  final int newSelStart, final int newSelEnd,
                                  final int composingSpanStart, final int composingSpanEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                composingSpanStart, composingSpanEnd);
        if (DEBUG) {
            Log.i(TAG, "onUpdateSelection: oss=" + oldSelStart + ", ose=" + oldSelEnd
                    + ", nss=" + newSelStart + ", nse=" + newSelEnd
                    + ", cs=" + composingSpanStart + ", ce=" + composingSpanEnd);
        }

        // This call happens whether our view is displayed or not, but if it's not then we should
        // not attempt recorrection. This is true even with a hardware keyboard connected: if the
        // view is not displayed we have no means of showing suggestions anyway, and if it is then
        // we want to show suggestions anyway.

        updateShiftKeyState(getCurrentInputEditorInfo());

    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {

        Log.d(TAG, "onKey "+primaryCode);
        InputConnection inputConnection = getCurrentInputConnection();
        playClick(primaryCode);
        if(navigationSymbol) {
            switch (primaryCode) {

                case 19: //UP
                    keyDownUp(KeyEvent.KEYCODE_DPAD_UP);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;
                case 20: //DOWN
                    keyDownUp(KeyEvent.KEYCODE_DPAD_DOWN);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;

                case 21: //LEFT
                    keyDownUp(KeyEvent.KEYCODE_DPAD_LEFT);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;
                case 22: //RIGHT
                    keyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;

                case 0: //SPACE
                case 32: //SPACE
                    break;

                case 111: //ESC
                case 61:  //TAB
                case 122: //HOME
                case 123: //END
                case 92:  //Page UP
                case 93:  //Page DOWN
                    keyDownUp(primaryCode);
                    break;

                case -7:  //Switch F1-F12
                    fnSymbol = !fnSymbol;
                    UpdateNotify();
                    keyboardView.setFnSymbol(fnSymbol);
                    break;

                case Keyboard.KEYCODE_DELETE:
                    inputConnection.deleteSurroundingText(1, 0);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DONE:
                    keyDownUp(KeyEvent.KEYCODE_ENTER);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;

                default:

            }
        }else{
            switch (primaryCode) {

                case 21: //LEFT
                    keyDownUp(KeyEvent.KEYCODE_DPAD_LEFT);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;
                case 22: //RIGHT
                    keyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DELETE:
                    inputConnection.deleteSurroundingText(1, 0);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DONE:
                    keyDownUp(KeyEvent.KEYCODE_ENTER);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;
                default:
                    char code = (char) primaryCode;
                    inputConnection.commitText(String.valueOf(code), 1);
            }
        }

    }

    private void playClick(int i){

        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        switch(i){
            case 32:
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_SPACEBAR);
                break;

            case Keyboard.KEYCODE_DONE:
            case 10:
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN);
                break;

            case Keyboard.KEYCODE_DELETE:
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE);
                break;

            default:
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD);
        }

    }


    @Override
    public void onText(CharSequence text) {
        Log.d(TAG, "onText: "+text);
    }

    @Override
    public void swipeLeft() {

    }

    @Override
    public void swipeRight() {

    }

    @Override
    public void swipeDown() {

    }

    @Override
    public void swipeUp() {

    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        //Log.d(TAG, "onTouch "+motionEvent);
        InputConnection inputConnection = getCurrentInputConnection();
        if(!showSymbol){
            if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) touchX = motionEvent.getX();
            if(motionEvent.getAction() == MotionEvent.ACTION_MOVE && touchX+(36-sens_botton_bar) < motionEvent.getX()){
                if(this.isInputViewShown()) {
                    CharSequence c = inputConnection.getTextAfterCursor(1, 0);
                    if(c.length() > 0) {
                        keyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT);
                        updateShiftKeyState(getCurrentInputEditorInfo());
                    }
                    touchX = motionEvent.getX();
                    Log.d(TAG, "onTouch KEYCODE_DPAD_RIGHT " + motionEvent);
                }
            }else  if(motionEvent.getAction() == MotionEvent.ACTION_MOVE && touchX-(36-sens_botton_bar) > motionEvent.getX()){
                if(this.isInputViewShown()) {
                    CharSequence c = inputConnection.getTextBeforeCursor(1, 0);
                    if (c.length() > 0) {
                        keyDownUp(KeyEvent.KEYCODE_DPAD_LEFT);
                        updateShiftKeyState(getCurrentInputEditorInfo());
                    }
                    touchX = motionEvent.getX();
                    Log.d(TAG, "onTouch sens_botton_bar "+sens_botton_bar+" KEYCODE_DPAD_LEFT " + motionEvent);
                }
            }
        }else{
            if(motionEvent.getAction() == MotionEvent.ACTION_MOVE)keyboardView.coordsToIndexKey(motionEvent.getX());
            if(motionEvent.getAction() == MotionEvent.ACTION_UP  )keyboardView.hidePopup(true);
        }
        return false;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        //if (DEBUG) Log.v(TAG, "onGenericMotionEvent(): event " + event);
        if(lastPackageName.equals("com.sateda.keyonekb")){
            Log.v(TAG, "onGenericMotionEvent(): event " + event);
            return false;
        }

        if(pref_touch_keyboard && !navigationSymbol){
            //если клавиатура показана, то гасим действия.
            if(this.isInputViewShown()) {
                return true;
            }
            return false;
        }

        return true;
    }

    private void initKeys()
    {
        press_code_true = 0;
        scan_code = new int[MAX_KEY_COUNT];
        one_press = new int[MAX_KEY_COUNT];
        one_press_shift = new int[MAX_KEY_COUNT];
        double_press = new int[MAX_KEY_COUNT];
        double_press_shift = new int[MAX_KEY_COUNT];
        alt = new int[MAX_KEY_COUNT];
        shift = new int[MAX_KEY_COUNT];
        alt_popup = new String[MAX_KEY_COUNT];
        alt_shift = new int[MAX_KEY_COUNT];
        alt_shift_popup = new String[MAX_KEY_COUNT];

        resetKeys();
    }

    private void resetKeys()
    {
        for(int i = 0; i < MAX_KEY_COUNT; i++) {
            scan_code[i] = 0;
            one_press[i] = 0;
            one_press_shift[i] = 0;
            double_press[i] = 0;
            double_press_shift[i] = 0;
            alt[i] = 0;
            shift[i] = 0;
            alt_shift[i] = 0;
            alt_popup[i] = "";
            alt_shift_popup[i] = "";
        }
    }

    private void LoadLayout(int id)
    {
        resetKeys();
        int count_key = 0;
        int scan_code = 0;
        int one_press = 0;
        int double_press = 0;
        int double_press_shift = 0;
        int alt = 0;
        int shift = 0;
        int alt_shift = 0;
        String alt_popup = "";
        String alt_shift_popup = "";

        try {
            XmlPullParser parser = getResources().getXml(id);

            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                scan_code = 0;
                one_press = 0;
                double_press = 0;
                double_press_shift = 0;
                alt = 0;
                shift = 0;
                alt_shift = 0;
                alt_popup = "";
                alt_shift_popup = "";

                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    if (parser.getAttributeName(i).equals("lang")) langStr = parser.getAttributeValue(i);
                    if (parser.getAttributeName(i).equals("scan_code")) scan_code = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("one_press")) one_press = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("double_press")) double_press = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("double_press_shift")) double_press_shift = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("alt")) alt = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("shift")) shift = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("alt_shift")) alt_shift = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("alt_popup")) alt_popup = parser.getAttributeValue(i);
                    if (parser.getAttributeName(i).equals("alt_shift_popup")) alt_shift_popup = parser.getAttributeValue(i);
                }

                if(scan_code != 0){
                    this.scan_code[count_key] = scan_code;
                    this.one_press[count_key] = one_press;
                    this.one_press_shift[count_key] = shift;
                    this.double_press[count_key] = double_press;
                    this.double_press_shift[count_key] = double_press_shift;
                    this.alt[count_key] = alt;
                    this.alt_shift[count_key] = alt_shift;
                    this.alt_popup[count_key] = alt_popup;
                    this.alt_shift_popup[count_key] = alt_shift_popup;
                    count_key++;
                }
                parser.next();
            }
        } catch (Throwable t) {
            Toast.makeText(this,
                    "Ошибка при загрузке XML-документа: " + t.toString(),
                    Toast.LENGTH_LONG).show();
        }

        LoadAltLayout();
    }

    private void LoadAltLayout()
    {
        int scan_code = 0;
        int alt = 0;
        int alt_shift = 0;
        String alt_popup = "";
        String alt_shift_popup = "";

        try {
            XmlPullParser parser;
            if(!pref_pocket_patch) {
                parser = getResources().getXml(R.xml.alt_hw);
            }else{
                parser = getResources().getXml(R.xml.pocket_alt_hw);
            }

            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                scan_code = 0;
                alt = 0;
                alt_shift = 0;
                alt_popup = "";
                alt_shift_popup = "";

                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    if (parser.getAttributeName(i).equals("scan_code")) scan_code = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("alt")) alt = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("alt_shift")) alt_shift = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("alt_popup")) alt_popup = parser.getAttributeValue(i);
                    if (parser.getAttributeName(i).equals("alt_shift_popup")) alt_shift_popup = parser.getAttributeValue(i);
                }

                if(scan_code != 0){
                    for(int i = 0; i < MAX_KEY_COUNT; i++){
                        if(this.scan_code[i] == scan_code && this.alt[i] == 0 && alt != 0){
                            this.alt[i] = alt;
                        }
                        if(this.scan_code[i] == scan_code && this.alt_shift[i] == 0 && alt_shift != 0){
                            this.alt_shift[i] = alt_shift;
                        }
                        if(this.scan_code[i] == scan_code && this.alt_popup[i].equals("") && alt_popup != ""){
                            this.alt_popup[i] = alt_popup;
                        }
                        if(this.scan_code[i] == scan_code && this.alt_shift_popup[i].equals("") && alt_shift_popup != ""){
                            this.alt_shift_popup[i] = alt_shift_popup;
                        }
                    }
                }
                parser.next();
            }
        } catch (Throwable t) {
            Toast.makeText(this,
                    "Ошибка при загрузке XML-документа: " + t.toString(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private int KeyToButton(int key, boolean alt_press, boolean shift_press, boolean is_double_press)
    {
        int result = 0;

        for(int i = 0; i < MAX_KEY_COUNT; i++) {
            if (this.scan_code[i]  == key) {
                if (alt_press == true && shift_press == true && this.alt_shift[i] != 0) {
                    result = this.alt_shift[i];
                } else if (alt_press == true && this.alt[i] != 0) {
                    result = this.alt[i];
                } else if (is_double_press == true && shift_press == true && this.double_press_shift[i] != 0) {
                    result = this.double_press_shift[i];
                } else if (is_double_press == true && this.double_press[i] != 0) {
                    result = this.double_press[i];
                } else if (shift_press == true && this.one_press_shift[i] != 0) {
                    result = this.one_press_shift[i];
                } else {
                    result = this.one_press[i];
                }
            }
            if(result != 0) return result;
        }
        return result;
    }

    private void UpdateNotify() {
        //notificationManager.cancelAll();
        Log.d(TAG, "UpdateNotify shiftPressFirstButtonBig="+shiftPressFirstButtonBig+" shiftPressAllButtonBig="+shiftPressAllButtonBig+" altPressAllButtonBig="+altPressAllButtonBig+" altPressFirstButtonBig="+altPressFirstButtonBig);
        if(navigationSymbol){
            if(!fnSymbol)
            {
                builder.setSmallIcon(R.mipmap.ic_kb_nav);
                builder.setContentTitle("Навигация");
            }
            else
            {
                builder.setSmallIcon(R.mipmap.ic_kb_nav_fn);
                builder.setContentTitle("Навигация + F1-F10");
            }

            keyboard_symbol = new Keyboard(this, R.xml.navigation);
            keyboardView.setKeyboard(keyboard_symbol);
            keyboardView.setNavigationLayer();

        }else if(showSymbol) {
            builder.setSmallIcon(R.mipmap.ic_kb_sym);
            builder.setContentTitle("Символы 1-9");
            if (!pref_pocket_patch){
                keyboard_symbol = new Keyboard(this, R.xml.symbol);
            }else {
                keyboard_symbol = new Keyboard(this, R.xml.pocket_symbol);
            }
            keyboardView.setKeyboard(keyboard_symbol);
            if(altShift) {
                keyboardView.setAltLayer(scan_code, alt_shift, alt_shift_popup);
            }else{
                keyboardView.setAltLayer(scan_code, alt, alt_popup);
            }
            //keyboardView.setAlt();
        }else if(altPressAllButtonBig){
            builder.setSmallIcon(R.mipmap.ic_kb_sym);
            builder.setContentTitle("Символы 1-9");
            //keyboard_symbol = new Keyboard(this, R.xml.symbol);
            keyboardView.setKeyboard(keyboard_empty);
            if(altShift) {
                //keyboardView.setAltLayer(scan_code, alt_shift);
                keyboardView.setLang("СИМВОЛЫ {} [] | / ");
            }else{
                //keyboardView.setAltLayer(scan_code, alt);
                keyboardView.setLang("СИМВОЛЫ 1-9");
            }
            keyboardView.setAlt();
        }else if(altPressFirstButtonBig){
            builder.setSmallIcon(R.mipmap.ic_kb_sym_one);
            builder.setContentTitle("Символы 1-9");
            //keyboard_symbol = new Keyboard(this, R.xml.symbol);
            keyboardView.setKeyboard(keyboard_empty);
            if(altShift) {
                keyboardView.setLang("Символы {} [] | / ");
            }else{
                keyboardView.setLang("Символы 1-9");
            }
            keyboardView.setAlt();
        }else if(shiftPressAllButtonBig){
            if(langArray[langNum] == R.xml.ukraine_hw) {
                if(pref_touch_keyboard == false) {
                    builder.setSmallIcon(R.mipmap.ic_ukr_shift_all);
                }else{
                    builder.setSmallIcon(R.mipmap.ic_ukr_shift_all_touch);
                }
            }else if(langArray[langNum] == R.xml.russian_hw || langArray[langNum] == R.xml.pocket_russian_hw || langArray[langNum] == R.xml.russian_translit_hw) {
                if(pref_touch_keyboard == false) {
                    builder.setSmallIcon(R.mipmap.ic_rus_shift_all);
                }else{
                    builder.setSmallIcon(R.mipmap.ic_rus_shift_all_touch);
                }
            }else{
                if(pref_touch_keyboard == false) {
                    builder.setSmallIcon(R.mipmap.ic_eng_shift_all);
                }else{
                    builder.setSmallIcon(R.mipmap.ic_eng_shift_all_touch);
                }
            }
            builder.setContentTitle(langStr);
            keyboardView.setLang(langStr);
            keyboardView.setShiftAll();
            keyboardView.setKeyboard(keyboard_empty);
            keyboardView.setLetterKB();
        }else if(shiftPressFirstButtonBig){
            if(langArray[langNum] == R.xml.ukraine_hw) {
                if(pref_touch_keyboard == false) {
                    builder.setSmallIcon(R.mipmap.ic_ukr_shift_first);
                }else{
                    builder.setSmallIcon(R.mipmap.ic_ukr_shift_first_touch);
                }
            }else if(langArray[langNum] == R.xml.russian_hw || langArray[langNum] == R.xml.pocket_russian_hw || langArray[langNum] == R.xml.russian_translit_hw) {
                if(pref_touch_keyboard == false) {
                    builder.setSmallIcon(R.mipmap.ic_rus_shift_first);
                }else{
                    builder.setSmallIcon(R.mipmap.ic_rus_shift_first_touch);
                }
            }else{
                if(pref_touch_keyboard == false) {
                    builder.setSmallIcon(R.mipmap.ic_eng_shift_first);
                }else{
                    builder.setSmallIcon(R.mipmap.ic_eng_shift_first_touch);
                }
            }
            builder.setContentTitle(langStr);
            keyboardView.setLang(langStr);
            keyboardView.setShiftFirst();
            keyboardView.setKeyboard(keyboard_empty);
            keyboardView.setLetterKB();
        }else{
            if(langArray[langNum] == R.xml.ukraine_hw) {
                if(pref_touch_keyboard == false) {
                    builder.setSmallIcon(R.mipmap.ic_ukr_small);
                }else{
                    builder.setSmallIcon(R.mipmap.ic_ukr_small_touch);
                }
            }else if(langArray[langNum] == R.xml.russian_hw || langArray[langNum] == R.xml.pocket_russian_hw || langArray[langNum] == R.xml.russian_translit_hw) {
                if(pref_touch_keyboard == false) {
                    builder.setSmallIcon(R.mipmap.ic_rus_small);
                }else{
                    builder.setSmallIcon(R.mipmap.ic_rus_small_touch);
                }
            }else{
                if(pref_touch_keyboard == false) {
                    builder.setSmallIcon(R.mipmap.ic_eng_small);
                }else{
                    builder.setSmallIcon(R.mipmap.ic_eng_small_touch);
                }
            }
            builder.setContentTitle(langStr);
            keyboardView.notShift();
            keyboardView.setLang(langStr);
            keyboardView.setKeyboard(keyboard_empty);
            keyboardView.setLetterKB();
        }

        notificationManager.notify(1, builder.build());

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean handleShiftOnCalling() {
        Log.d(TAG, "handleShiftOnCalling hello");
        // Accept calls using SHIFT key
        if (callStateCallback.isCalling() ) {
            Log.d(TAG, "handleShiftOnCalling callStateCallback - Calling");
            TelecomManager tm = getTelecomManager();

            if (tm != null && this.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "handleShiftOnCalling acceptRingingCall");
                tm.acceptRingingCall();
                return true;
            }

        }
        return false;
    }

    private boolean handleAltOnCalling() {
        // End calls using ALT key
        if (callStateCallback.isCalling() ) {
            TelecomManager tm = getTelecomManager();

            if (tm != null && this.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "handleAltOnCalling endCall");
                tm.endCall();
                //keyDownUp(KeyEvent.KEYCODE_ENDCALL);
                return true;
            }

        }
        return false;
    }

    private TelephonyManager getTelephonyManager() {
        return (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
    }

    private TelecomManager getTelecomManager() {
        return (TelecomManager) this.getSystemService(Context.TELECOM_SERVICE);
    }

    private void ChangeLanguage() {
        langNum++;
        if(langNum > langCount) langNum = 0;
        if(langNum == 0){
            isEnglishKb = true;
        }else{
            isEnglishKb = false;
        }
        LoadLayout(langArray[langNum]);
        if(show_toast) {
            toast = Toast.makeText(getApplicationContext(), langStr, Toast.LENGTH_SHORT);
            toast.show();
        }
        UpdateNotify();
    }

    private void ChangeLanguageBack() {
        langNum--;
        if(langNum < 0) langNum = langCount;
        if(langNum == 0){
            isEnglishKb = true;
        }else{
            isEnglishKb = false;
        }
        LoadLayout(langArray[langNum]);
        if(show_toast) {
            toast = Toast.makeText(getApplicationContext(), langStr, Toast.LENGTH_SHORT);
            toast.show();
        }
        UpdateNotify();
    }

    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }

    private void keyDownUp(int keyEventCode) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    private void updateShiftKeyState(EditorInfo attr) {
        Log.d(TAG, "updateShiftKeyState attr "+attr.inputType);
        if (attr != null && !altPressFirstButtonBig && !altPressAllButtonBig && !altPressed ) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != InputType.TYPE_NULL) {
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
                if(caps != 0)Log.d(TAG, "updateShiftKeyState");
            }
            if(caps != 0 && !shiftPressAllButtonBig){
                shiftPressFirstButtonBig = true;
            }else {
                shiftPressFirstButtonBig = false;
            }

            UpdateNotify();
        }
    }

    @Override public View onCreateCandidatesView() {
        mCandidateView = new CandidateView(this);
        mCandidateView.setService(this);
        return mCandidateView;
    }

    @Override
    public void onGetSuggestions(SuggestionsInfo[] results) {
        Log.d(TAG, "onGetSuggestions");
    }

    @Override
    public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] results) {
        Log.d(TAG, "onGetSentenceSuggestions");
    }
}
