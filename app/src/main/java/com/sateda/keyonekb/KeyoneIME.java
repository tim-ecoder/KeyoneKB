
package com.sateda.keyonekb;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.Keep;
import android.support.annotation.RequiresApi;
import android.support.v7.app.NotificationCompat;
import android.support.v7.app.NotificationCompat.Builder;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SuggestionsInfo;
import android.widget.Toast;

import com.sateda.keyonekb.input.CallStateCallback;

import org.xmlpull.v1.XmlPullParser;

import static android.content.ContentValues.TAG;

import java.util.ArrayList;
import java.util.HashMap;

@Keep
public class KeyoneIME extends InputMethodService implements KeyboardView.OnKeyboardActionListener, SpellCheckerSession.SpellCheckerSessionListener, View.OnTouchListener {

    private static final int MAX_KEY_COUNT = 50;
    private static final boolean DEBUG = false;

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
    public static final String APP_PREFERENCES_SHOW_DEFAULT_ONSCREEN_SWIPE_PANEL = "show_default_onscreen_keyboard";
    public static final String APP_PREFERENCES_KEYBOARD_GESTURES_AT_VIEWS_ENABLED = "keyboard_gestures_at_views_enabled";

    public static final int SCAN_CODE_CHAR_SPACE = 32;
    public static final int SCAN_CODE_CURRENCY = 183;
    public static final int SCAN_CODE_KEY_SYM = 100;
    public static final int SCAN_CODE_SHIFT = 110;

    public static final int SCAN_CODE_KEY_0 = 11;
    public static final int SCAN_CODE_CHAR_0 = 48;

    public static final int DOUBLE_CLICK_TIME = 400;
    public static final int DOUBLE_CLICK_TIME2 = 500;
    public static final int MAGIC_KEYBOARD_GESTURE_MOTION_CONST = 36;
    public static final int ROW_4_BEGIN_Y = 400;
    public static final String TITLE_NAV_TEXT = "Навигация";
    public static final String TITLE_NAV_FV_TEXT = "Навигация + F1-F10";
    public static final String TITLE_SYM_TEXT = "Символы 1-9";
    public static final String TITLE_SYM2_TEXT = "СИМВОЛЫ {} [] | / ";

    private CallStateCallback callStateCallback;

    private NotificationManager notificationManager;
    private CandidateView mCandidateView;

    private SatedaKeyboardView keyboardView;
    private Keyboard onScreenKeyboardDefaultGesturesAndLanguage;
    private Keyboard onScreenKeyboardSymbols;

    private Boolean startInputAtBbContactsApp = false; // костыль для приложения Блекбери контакты
    private Boolean startInputAtBbPhoneApp = false; // аналогичный костыль для приложения Телефон чтобы в нем искалось на русском языке
    private Boolean inputAtBbLauncherApp = false;
    //ХАК для телеграма т.к. когда входишь в диалог он создает какой-то еще Input и несколько символов улетает туда
    //private Boolean startInputAtTelegram = false;

    private SharedPreferences mSettings;

    private boolean isEnglishKb = false;
    private int CurrentLanguageListIndex = 0;
    private int LangListCount = 0;

    private Toast toast;

    private boolean ctrlImitatedByShiftRightPressed = false; // только первая буква будет большая

    private long mShiftFirstPressTime;
    private boolean oneTimeShiftOneTimeBigMode; // только первая буква будет большая
    private boolean shiftPressFirstButtonBigDoublePress; // только первая буква будет большая (для двойного нажатия на одну и туже кнопку)
    private boolean doubleShiftCapsMode; //все следующий буквы будут большие
    private boolean shiftPressed; //нажатие клавишь с зажатым альтом

    private long mCtrlPressTime;
    private long mAltPressTime;
    private boolean altPressSingleSymbolAltedMode;
    private boolean doubleAltPressAllSymbolsAlted;
    private boolean altShift;
    private boolean showSymbolOnScreenKeyboard;
    private boolean navigationOnScreenKeyboardMode;
    private boolean fnSymbolOnScreenKeyboardMode;

    private boolean menuEmulatedKeyPressed; //нажатие клавишь с зажатым альтом

    private boolean altPressed; //нажатие клавишь с зажатым альтом
    private boolean altPlusBtn;

    private String lastPackageName = "";

    private float lastGestureX;
    private float lastGestureY;

    private android.support.v4.app.NotificationCompat.Builder builder;
    private Notification.Builder builder2;
    private StringBuilder mComposing = new StringBuilder();


    private int press_code_true; // код клавиши, при отпускании которой не нужно обрабатывать событие отпускание

    private int prev_key_press_btn_r0; //repeat 0 - одинарное повторное нажатие
    private int prev_key_press_btn_r1; //repeat 1 - удержание при повторном нажатии
    private long prev_key_press_time = 0;
    private boolean key_0_hold = false;
    private boolean mode_keyboard_gestures = false;

    //settings
    private int pref_height_bottom_bar = 10;
    private int pref_gesture_motion_sensitivity = 10;
    private boolean pref_show_toast = false;
    private boolean pref_alt_space = true;
    private boolean pref_flag = false;
    private boolean pref_long_press_key_alt_symbol = false;
    private boolean pref_show_default_onscreen_keyboard = true;
    private boolean pref_keyboard_gestures_at_views_enable = true;

    //Предзагружаем клавиатуры, чтобы не плодить объекты
    private Keyboard keybardNavigation;
    private Keyboard keyboardSymbols;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate() {
        super.onCreate();

        callStateCallback = new CallStateCallback();
        TelephonyManager tm = getTelephonyManager();
        if (tm != null) {
            tm.listen(callStateCallback, PhoneStateListener.LISTEN_CALL_STATE);
        }

        mShiftFirstPressTime = 0;
        oneTimeShiftOneTimeBigMode = false;
        shiftPressFirstButtonBigDoublePress = false;
        doubleShiftCapsMode = false;
        shiftPressed = false;
        mCtrlPressTime = 0;
        mAltPressTime = 0;
        altPressSingleSymbolAltedMode = false;
        doubleAltPressAllSymbolsAlted = false;
        menuEmulatedKeyPressed = false;
        altShift = false;
        altPressed = false;
        altPlusBtn = false;
        showSymbolOnScreenKeyboard = false;
        navigationOnScreenKeyboardMode = false;
        fnSymbolOnScreenKeyboardMode = false;
        ctrlImitatedByShiftRightPressed = false;
        key_0_hold = false;

        pref_height_bottom_bar = 10;

        pref_show_toast = false;
        pref_alt_space = true;
        pref_long_press_key_alt_symbol = false;

        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        loadSetting();

        onScreenKeyboardDefaultGesturesAndLanguage = new SatedaKeyboard(this, R.xml.space_empty, 70+ pref_height_bottom_bar *5);

        onScreenKeyboardSymbols = new SatedaKeyboard(this, R.xml.symbol);

        keyboardView = (SatedaKeyboardView) getLayoutInflater().inflate(R.layout.keyboard,null);
        keyboardView.setKeyboard(onScreenKeyboardDefaultGesturesAndLanguage);
        keyboardView.setOnKeyboardActionListener(this);
        keyboardView.setOnTouchListener(this);
        keyboardView.setPreviewEnabled(false);
        keyboardView.setService(this);
        keyboardView.clearAnimation();
        keyboardView.showFlag(pref_flag);

        keybardNavigation = new Keyboard(this, R.xml.navigation);
        keyboardSymbols = new Keyboard(this, R.xml.symbol);

        notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName("com.sateda.keyonekb.satedakeyboard", "com.sateda.keyboard.keyonekb.MainActivity");
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);


        if (android.os.Build.VERSION.SDK_INT >= 27) {
            //Борьба с Warning для SDK_V=27 (KEY2 вестимо)
            //04-10 20:36:34.040 13838-13838/xxx.xxxx.xxxx W/Notification: Use of stream types is deprecated for operations other than volume control
            //See the documentation of setSound() for what to use instead with android.media.AudioAttributes to qualify your playback use case

            String channelId = "KeyoneKbNotificationChannel2";
            String channelDescription = "KeyoneKbNotificationChannel";
            NotificationChannel notificationChannel = notificationManager.getNotificationChannel(channelId);
            if (notificationChannel == null) {
                notificationChannel = new NotificationChannel(channelId, channelDescription, NotificationManager.IMPORTANCE_LOW);
                //notificationChannel.setLightColor(Color.GREEN); //Set if it is necesssary
                notificationChannel.enableVibration(false); //Set if it is necesssary
                notificationChannel.setSound(null, null);
                notificationManager.createNotificationChannel(notificationChannel);
            }
            builder2 = new Notification.Builder(getApplicationContext(), channelId);
            builder2.setOngoing(true);
            builder2.setAutoCancel(true);
            builder2.setVisibility(Notification.VISIBILITY_SECRET);
        }
        else
        {
            builder = new Builder(getApplicationContext());
            builder.setOngoing(true);
            builder.setAutoCancel(false);
            builder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
            builder.setPriority(NotificationCompat.PRIORITY_LOW);

        }

        //notificationManager.notify(1, builder.build());
        //builder.setSmallIcon(R.mipmap.ic_rus_small);
        //builder.setContentTitle("Русский");

        UpdateKeyboardModeVisualization();
    }

    private void loadSetting(){
        LangListCount = 1;
        pref_show_toast = false;
        pref_gesture_motion_sensitivity = 10;

        boolean lang_ru_on = true;
        boolean lang_translit_ru_on = false;
        boolean lang_ua_on = false;

        if(mSettings.contains(APP_PREFERENCES_SHOW_DEFAULT_ONSCREEN_SWIPE_PANEL)) {
            pref_show_default_onscreen_keyboard = mSettings.getBoolean(APP_PREFERENCES_SHOW_DEFAULT_ONSCREEN_SWIPE_PANEL, true);
        }

        if(mSettings.contains(APP_PREFERENCES_KEYBOARD_GESTURES_AT_VIEWS_ENABLED)) {
            pref_keyboard_gestures_at_views_enable = mSettings.getBoolean(APP_PREFERENCES_KEYBOARD_GESTURES_AT_VIEWS_ENABLED, false);
        }

        if(mSettings.contains(APP_PREFERENCES_RU_LANG)) {
            lang_ru_on = mSettings.getBoolean(APP_PREFERENCES_RU_LANG, true);
        }

        if(mSettings.contains(APP_PREFERENCES_TRANSLIT_RU_LANG)) {
            lang_translit_ru_on = mSettings.getBoolean(APP_PREFERENCES_TRANSLIT_RU_LANG, false);
        }

        if(mSettings.contains(APP_PREFERENCES_UA_LANG)) {
            lang_ua_on = mSettings.getBoolean(APP_PREFERENCES_UA_LANG, false);
        }

        if(mSettings.contains(APP_PREFERENCES_SENS_BOTTON_BAR)) {
            pref_gesture_motion_sensitivity = mSettings.getInt(APP_PREFERENCES_SENS_BOTTON_BAR, 1);
        }

        if(mSettings.contains(APP_PREFERENCES_SHOW_TOAST)) {
            pref_show_toast = mSettings.getBoolean(APP_PREFERENCES_SHOW_TOAST, false);
        }

        if(mSettings.contains(APP_PREFERENCES_ALT_SPACE)) {
            pref_alt_space = mSettings.getBoolean(APP_PREFERENCES_ALT_SPACE, true);
        }

        if(mSettings.contains(APP_PREFERENCES_ALT_SPACE)) {
            pref_long_press_key_alt_symbol = mSettings.getBoolean(APP_PREFERENCES_LONGPRESS_ALT, false);
        }

        if(mSettings.contains(APP_PREFERENCES_FLAG)) {
            pref_flag = mSettings.getBoolean(APP_PREFERENCES_FLAG, false);
        }
        if(mSettings.contains(APP_PREFERENCES_HEIGHT_BOTTON_BAR)) {
            pref_height_bottom_bar = mSettings.getInt(APP_PREFERENCES_HEIGHT_BOTTON_BAR, 10);
        }

        KeybordLayout currentLayout;
        LangListCount = 1;
        currentLayout = LoadLayout2(R.xml.english_hw, LangListCount - 1);
        currentLayout.IconCaps = R.mipmap.ic_eng_shift_all;
        currentLayout.IconCapsTouch = R.mipmap.ic_eng_shift_all_touch;
        currentLayout.IconFirstShift = R.mipmap.ic_eng_shift_first;
        currentLayout.IconFirstShiftTouch = R.mipmap.ic_eng_shift_first_touch;
        currentLayout.IconLittle = R.mipmap.ic_eng_small;
        currentLayout.IconLittleTouch = R.mipmap.ic_eng_small_touch;

        if(lang_ru_on){
            LangListCount++;
            currentLayout = LoadLayout2(R.xml.russian_hw, LangListCount - 1);
            currentLayout.IconCaps = R.mipmap.ic_rus_shift_all;
            currentLayout.IconCapsTouch = R.mipmap.ic_rus_shift_all_touch;
            currentLayout.IconFirstShift = R.mipmap.ic_rus_shift_first;
            currentLayout.IconFirstShiftTouch = R.mipmap.ic_rus_shift_first_touch;
            currentLayout.IconLittle = R.mipmap.ic_rus_small;
            currentLayout.IconLittleTouch = R.mipmap.ic_rus_small_touch;
        }
        if(lang_translit_ru_on){
            LangListCount++;
            currentLayout = LoadLayout2(R.xml.russian_translit_hw, LangListCount - 1);
            currentLayout.IconCaps = R.mipmap.ic_rus_shift_all;
            currentLayout.IconCapsTouch = R.mipmap.ic_rus_shift_all_touch;
            currentLayout.IconFirstShift = R.mipmap.ic_rus_shift_first;
            currentLayout.IconFirstShiftTouch = R.mipmap.ic_rus_shift_first_touch;
            currentLayout.IconLittle = R.mipmap.ic_rus_small;
            currentLayout.IconLittleTouch = R.mipmap.ic_rus_small_touch;
        }
        if(lang_ua_on){
            LangListCount++;
            currentLayout = LoadLayout2(R.xml.ukraine_hw, LangListCount - 1);
            currentLayout.IconCaps = R.mipmap.ic_ukr_shift_all;
            currentLayout.IconCapsTouch = R.mipmap.ic_ukr_shift_all_touch;
            currentLayout.IconFirstShift = R.mipmap.ic_ukr_shift_first;
            currentLayout.IconFirstShiftTouch = R.mipmap.ic_ukr_shift_first_touch;
            currentLayout.IconLittle = R.mipmap.ic_ukr_small;
            currentLayout.IconLittleTouch = R.mipmap.ic_ukr_small_touch;
        }
    }

    @Override
    public void onPress(int primaryCode) {
        Log.d(TAG, "onPress");
    }

    @Override
    public void onRelease(int primaryCode) {

    }

    @Override public void onFinishInput() {
        if(showSymbolOnScreenKeyboard == true) {
            showSymbolOnScreenKeyboard = false;
            altPressSingleSymbolAltedMode = false;
            doubleAltPressAllSymbolsAlted = false;
            altShift = false;
            mode_keyboard_gestures = false;
            UpdateKeyboardModeVisualization();
        }

        if(lastPackageName.equals("com.sateda.keyonekb")) loadSetting();

        //TODO: Подумать, чтобы не надо было инициализировать свайп-клавиаутуру по настройке pref_show_default_onscreen_keyboard
         keyboardView.showFlag(pref_flag);
        if (onScreenKeyboardDefaultGesturesAndLanguage.getHeight() != 70 + pref_height_bottom_bar * 5)
            onScreenKeyboardDefaultGesturesAndLanguage = new SatedaKeyboard(this, R.xml.space_empty, 70 + pref_height_bottom_bar * 5);

        Log.d(TAG, "onFinishInput ");
    }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        Log.d(TAG, "onStartInput "+attribute.packageName+" "+attribute.label);
        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.

        if(attribute.packageName.equals("com.blackberry.contacts")) {
            startInputAtBbContactsApp = true;
        }else{
            startInputAtBbContactsApp = false;
        }

        if(attribute.packageName.equals("com.blackberry.blackberrylauncher")) {
            inputAtBbLauncherApp = true;
        }else{
            inputAtBbLauncherApp = false;
        }

        if(attribute.packageName.equals("com.android.dialer")) {
            startInputAtBbPhoneApp = true;
        }else{
            startInputAtBbPhoneApp = false;
        }

        /*
        // ХАК для телеграма т.к. когда входишь в диалог он создает какой-то еще Input и несколько символов улетает туда
        if(attribute.packageName.equals("org.telegram.messenger") && attribute.inputType == 0) {
            startInputAtTelegram = true;
        }else{
            startInputAtTelegram = false;
        }
         */

        // Обрабатываем переход между приложениями
        if(!attribute.packageName.equals(lastPackageName))
        {
            lastPackageName = attribute.packageName;

            //Отключаем режим навигации
            navigationOnScreenKeyboardMode = false;
            fnSymbolOnScreenKeyboardMode = false;
            //TODO: Зачем это?
            keyboardView.setFnSymbol(fnSymbolOnScreenKeyboardMode);

            //Пробовал отключать ни на что не влияет
            if(!keyboardView.isShown()) {
                if(!pref_show_default_onscreen_keyboard)
                    keyboardView.setVisibility(View.GONE);
                else keyboardView.setVisibility(View.VISIBLE);
            }
        }

        if (!restarting) {
            Log.d(TAG, "onStartInput !restarting");
        }

        boolean keyboardModeVisualiztionUpdated = false;
        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
            case InputType.TYPE_CLASS_NUMBER:
            case InputType.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard, with
                // no extra features.
            case InputType.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                doubleAltPressAllSymbolsAlted = true;
                break;

            case InputType.TYPE_CLASS_TEXT:
                // This is general text editing.  We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).
                doubleAltPressAllSymbolsAlted = false;
                altPressSingleSymbolAltedMode = false;

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
                keyboardModeVisualiztionUpdated = IsFirstBigCharStateAndUpdateVisualization(attribute);
                break;

            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                doubleAltPressAllSymbolsAlted = false;
                altPressSingleSymbolAltedMode = false;
                oneTimeShiftOneTimeBigMode = false;
                doubleShiftCapsMode = false;
                mode_keyboard_gestures = false;
                keyboardModeVisualiztionUpdated = IsFirstBigCharStateAndUpdateVisualization(attribute);
        }

        //Исключаем двоную перерисовку экрана
        if(!keyboardModeVisualiztionUpdated)
            UpdateKeyboardModeVisualization();
        // Update the label on the enter key, depending on what the application
        // says it will do.
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
        Log.d(TAG, "onKeyMultiple "+event);
        return false;
    }

    // ХАК для телеграма т.к. когда входишь в диалог он создает какой-то еще Input и несколько символов улетает туда
    /*
    private char[] telegramStartInputBuffer = new char[32];
    private int telegramStartInputBufferIndex = 0;
     */

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        keyboardView.hidePopup(false);
        Log.d(TAG, "onKeyDown "+event);

        int scanCode = event.getScanCode();
        int repeatCount = event.getRepeatCount();
        boolean inputViewShown = this.isInputViewShown();

        //region BB Launcher HACK
        //обработка главного экрана Блекбери
        //он хочет получать только родные клавиши, по этому ему отправляем почти все клавиши неизменными
        if(inputAtBbLauncherApp
            && !inputViewShown
            && scanCode != SCAN_CODE_KEY_0
            && keyCode != KeyEvent.KEYCODE_SHIFT_LEFT
            && keyCode != KeyEvent.KEYCODE_SPACE
            && keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT
            && keyCode != KeyEvent.KEYCODE_ALT_LEFT
            && scanCode != SCAN_CODE_KEY_SYM
            && scanCode != SCAN_CODE_SHIFT
            && scanCode != SCAN_CODE_CURRENCY){
            Log.d(TAG, "Oh! this fixBbkLauncher "+ inputAtBbLauncherApp);
            return super.onKeyDown(keyCode, event);

        }else if(inputAtBbLauncherApp &&  !inputViewShown && scanCode == SCAN_CODE_KEY_0 && repeatCount == 0 ){
            //Смена языка в BB Launcher сама заработала в onKeyUp без хака
            //ChangeLanguage();
            return true;
        }/* Пока деактивируем режим kbd_gestures в пользу двойного ctrl или удержания
        else if(startInputAtBbLauncherApp &&  !inputViewShown && scanCode == KEY_0 && repeatCount == 1 ){
            //enable_keyboard_gestures = !enable_keyboard_gestures;
            //Хак
            ChangeLanguageBack();
            return true;
        }
        */
        else if(inputAtBbLauncherApp && !inputViewShown){
            return true;
       }

        //endregion

        long now = System.currentTimeMillis();
        InputConnection inputConnection = getCurrentInputConnection();

        // Обработайте нажатие, верните true, если обработка выполнена
        boolean is_double_press = false;
        boolean shift = false;
        boolean _altMode = false;
        int navigationKeyCode = 0;
        int codeForSendCode = 0;

        EditorInfo currentInputEditorInfo = getCurrentInputEditorInfo();
        boolean visualUpdated = false;
        //region нажатие клавиши CTRL, CTRL+SHIFT, 2xCTRL (SHIFT_RIGHT->CTRL IMITATION)
        if(keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT || scanCode == SCAN_CODE_SHIFT){
            int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
            if(inputConnection!=null)
                inputConnection.sendKeyEvent(new KeyEvent(
                    now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta));
            ctrlImitatedByShiftRightPressed = true;
            if(shiftPressed && keyboardView.isShown()) {
                keyboardView.setVisibility(View.GONE);
            }else if(shiftPressed && !keyboardView.isShown()) {
                UpdateKeyboardModeVisualization(true);
                keyboardView.setVisibility(View.VISIBLE);
            }
            visualUpdated = IsFirstBigCharStateAndUpdateVisualization(currentInputEditorInfo);

            //Двойное нажатие Ctrl активирует сенсор на клавиатуре
            if(repeatCount == 0) {
                if (mCtrlPressTime + DOUBLE_CLICK_TIME2 > now && !shiftPressed) {
                    mode_keyboard_gestures = !mode_keyboard_gestures;
                    mCtrlPressTime = 0;
                    if(!visualUpdated) UpdateKeyboardGesturesModeVisualization();
                } else {
                    mCtrlPressTime = now;
                }
            }
            return true;
        }
        //endregion

        //region CTRL+CVXA..etc (incl. SHIFT_RIGHT->CTRL IMITATION)

        if(ctrlImitatedByShiftRightPressed && keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT && scanCode != SCAN_CODE_SHIFT){
            int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;

            if(inputConnection!=null) {
                    inputConnection.sendKeyEvent(new KeyEvent(
                            now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta));
            }
            IsFirstBigCharStateAndUpdateVisualization(currentInputEditorInfo);
            return true;
        }

        //endregion

        //region Нажатие клавиши ALT
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT){
            visualUpdated = false;
            if (handleAltOnCalling()) {
                return true;
            }

            if(showSymbolOnScreenKeyboard){
                showSymbolOnScreenKeyboard = false;
                altPressSingleSymbolAltedMode = false;
                doubleAltPressAllSymbolsAlted = false;
                altShift = false;
                visualUpdated = IsFirstBigCharStateAndUpdateVisualization(currentInputEditorInfo);
            }else  if(!doubleAltPressAllSymbolsAlted && !altPressSingleSymbolAltedMode){
                altShift = false;
                mAltPressTime = now;
                altPressSingleSymbolAltedMode = true;
            } else if (!altPressed && altPressSingleSymbolAltedMode && mAltPressTime + DOUBLE_CLICK_TIME > now){
                altPressSingleSymbolAltedMode = false;
                doubleAltPressAllSymbolsAlted = true;
            } else if (!altPressed && altPressSingleSymbolAltedMode && mAltPressTime + DOUBLE_CLICK_TIME < now){
                altPressSingleSymbolAltedMode = false;
                doubleAltPressAllSymbolsAlted = false;
                altShift = false;
                visualUpdated = IsFirstBigCharStateAndUpdateVisualization(currentInputEditorInfo);
            } else if (!altPressed && doubleAltPressAllSymbolsAlted){
                altPressSingleSymbolAltedMode = false;
                doubleAltPressAllSymbolsAlted = false;
                altShift = false;
                visualUpdated = IsFirstBigCharStateAndUpdateVisualization(currentInputEditorInfo);
            }
            altPressed = true;
            if(!visualUpdated) UpdateKeyboardModeVisualization();
            return true;
        }
        //endregion

        //region Нажатие клавиши SYM
        if ( scanCode == SCAN_CODE_KEY_SYM && repeatCount == 0 || (keyCode == KeyEvent.KEYCODE_3 && DEBUG)){

            if(altPressed){ //вызов меню
                menuEmulatedKeyPressed = true;
                if(inputConnection!=null)inputConnection.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
                return true;
            }
            visualUpdated = false;
            if(navigationOnScreenKeyboardMode) {
                navigationOnScreenKeyboardMode = false;
                showSymbolOnScreenKeyboard = false;
                altPressSingleSymbolAltedMode = false;
                doubleAltPressAllSymbolsAlted = false;
                visualUpdated = IsFirstBigCharStateAndUpdateVisualization(currentInputEditorInfo);
            }else if(!showSymbolOnScreenKeyboard){
                showSymbolOnScreenKeyboard = true;
                altShift = true;
                doubleAltPressAllSymbolsAlted = true;
                altPressSingleSymbolAltedMode = false;
            } else if(showSymbolOnScreenKeyboard && altShift){
                altShift = false;
            } else if(showSymbolOnScreenKeyboard && !altShift) {
                showSymbolOnScreenKeyboard = false;
                altPressSingleSymbolAltedMode = false;
                doubleAltPressAllSymbolsAlted = false;
                visualUpdated = IsFirstBigCharStateAndUpdateVisualization(currentInputEditorInfo);
            }

            if(!visualUpdated) UpdateKeyboardModeVisualization();
            return true;

        } else if ( scanCode == SCAN_CODE_KEY_SYM && repeatCount >= 1 && !navigationOnScreenKeyboardMode){
            //Двойное нажание SYM -> Режим навигации
            //TODO: Вынести OnScreenKeyboardMode-ы в Enum
            navigationOnScreenKeyboardMode = true;
            fnSymbolOnScreenKeyboardMode = false;
            //TODO: Зачем это?
            keyboardView.setFnSymbol(fnSymbolOnScreenKeyboardMode);
            UpdateKeyboardModeVisualization();
            return true;
        } else if (scanCode == SCAN_CODE_KEY_SYM && repeatCount >= 1 && navigationOnScreenKeyboardMode){
            return true;
        }
        //endregion

        //region Режим "Навигационные клавиши"
        if(navigationOnScreenKeyboardMode &&
                ((scanCode == 11) ||
                        (scanCode == 5) ||
                        (scanCode >= 16 && scanCode <= 25) ||
                        (scanCode >= 30 && scanCode <= 38) ||
                        (scanCode >= 44 && scanCode <= 50)))
        {
            navigationKeyCode = getNavigationCode(scanCode);

            Log.d(TAG, "navigationKeyCode "+navigationKeyCode);
            if(navigationKeyCode == -7)
            {
                fnSymbolOnScreenKeyboardMode = !fnSymbolOnScreenKeyboardMode;
                UpdateKeyboardModeVisualization();
                keyboardView.setFnSymbol(fnSymbolOnScreenKeyboardMode);
                return true;
            }
            if(inputConnection!=null && navigationKeyCode != 0) inputConnection.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_DOWN, navigationKeyCode));
            return true;
        }
        //endregion

        if(altPressSingleSymbolAltedMode || doubleAltPressAllSymbolsAlted || altPressed) _altMode = true;
        //Log.d(TAG, "onKeyDown altPressFirstButtonBig="+altPressFirstButtonBig+" altPressAllButtonBig="+altPressAllButtonBig+" altPressed="+altPressed);

        //region Нажатие клавиши SHIFT
        if (!shiftPressed && (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || (keyCode == KeyEvent.KEYCODE_2 && DEBUG))){
            if (handleShiftOnCalling()) {
                return true;
            }
            if(!_altMode)
            {
                if(doubleShiftCapsMode == false && oneTimeShiftOneTimeBigMode == false){
                    mShiftFirstPressTime = now;
                    oneTimeShiftOneTimeBigMode = true;
                } else if (oneTimeShiftOneTimeBigMode == true && mShiftFirstPressTime + 1000 > now){
                    oneTimeShiftOneTimeBigMode = false;
                    doubleShiftCapsMode = true;
                } else if (oneTimeShiftOneTimeBigMode == true && mShiftFirstPressTime + 1000 < now){
                    oneTimeShiftOneTimeBigMode = false;
                    doubleShiftCapsMode = false;
                } else if (doubleShiftCapsMode == true){
                    oneTimeShiftOneTimeBigMode = false;
                    doubleShiftCapsMode = false;
                }
            }else{
                altShift = !altShift;
            }
            shiftPressed = true;
            UpdateKeyboardModeVisualization();
            return true;
        }
        //TODO: Выделение с шифтом в приложении BB.Notes не работает
        if(oneTimeShiftOneTimeBigMode || doubleShiftCapsMode || shiftPressed) shift = true;
        //endregion

        if(_altMode) shift = altShift;
        //Log.d(TAG, "onKeyDown shiftPressFirstButtonBig="+shiftPressFirstButtonBig+" shiftPressAllButtonBig="+shiftPressAllButtonBig+" altShift="+altShift);

        //region Обработка KEY_0 (смена языка, зажатие, ATL+KEY_0='0'
        if(scanCode == SCAN_CODE_KEY_0) {
            if (!_altMode) {
                if (repeatCount == 0) {
                    if (System.currentTimeMillis() - lastGestureSwipingBeginTime < 1000) {
                        Log.d(TAG, "GestureMode at key_0_down first time");
                        mode_keyboard_gestures = true;
                        key_0_hold = true;
                        UpdateKeyboardGesturesModeVisualization();
                    }
                    return true;
                } else if (repeatCount >= 1) {
                    return true;
                }
            //If AltMode=true
            } else {
                altPlusBtn = true;
                if (inputConnection != null)
                    inputConnection.commitText(String.valueOf((char) SCAN_CODE_CHAR_0), 1);
                return true;
            }
        }
        //endregion

        //region Подготовка СИМВОЛА на печать. С учетом ОДИНОЧНЫХ и ЗАЖАТИЯ (+SHIFT, ALT, ALT+SHIFT)

        if(repeatCount == 0) { //Единичное нажание
            codeForSendCode = KeyToCharCode(scanCode, _altMode, shift, false);
            //if (!_altMode && codeForSendCode != 0 && !shiftPressFirstButtonBigDoublePress) shiftPressFirstButtonBigDoublePress = shiftPressFirstButtonBig;
        }else if(repeatCount == 1) { //удержание клавиши
            if(!pref_long_press_key_alt_symbol) {
                codeForSendCode = KeyToCharCode(scanCode, _altMode, true, false);
            }else {
                _altMode = true;
                codeForSendCode = KeyToCharCode(scanCode, _altMode, false, false);
            }
            if(codeForSendCode != 0 && inputConnection!=null) {
                //TODO: Удаляем символ. Да. А где вводим, дальше по коду?
                //Я бы сюда сделал ExtractMethod оттуда для читаемости кода
                inputConnection.deleteSurroundingText(1, 0);
            }
        }else{ //TODO: Непонятная ситуация
            codeForSendCode = KeyToCharCode(scanCode, _altMode, shift, false);
            if(codeForSendCode != 0) return true;
        }
        //endregion

        //region Подготовка СИМВОЛА на печать. С учетом СДВОЕННЫХ (+SHIFT, ALT, ALT+SHIFT)
        int code_double_press = 0;
        //Если буква не сдвоенная то не удаляем заглавную
        int code_double_pres_no_shift = 0;
        Log.d(TAG, "onKeyDown prev_key_press_btn_r0="+prev_key_press_btn_r0+" event.getScanCode() ="+ scanCode +" shiftPressFirstButtonBig="+ oneTimeShiftOneTimeBigMode);
        if(prev_key_press_btn_r0 == scanCode && repeatCount == 0 &&  now < prev_key_press_time + DOUBLE_CLICK_TIME){
            code_double_pres_no_shift = KeyToCharCode(scanCode, _altMode, false, true);
            //Второе нажатие уже айдет из режима Заглавной буквы, поэтому протаскиваем режим заглавных чуть подольше в методе UpdateShift..
            if(shiftPressFirstButtonBigDoublePress) {
                //Это для сдвоенных букв, чтобы делать Заглавной вторую букву на клавише
                code_double_press = KeyToCharCode(scanCode, _altMode, true, true);
            } else {
                code_double_press = KeyToCharCode(scanCode, _altMode, shift, true);
            }
            if(codeForSendCode != code_double_press && codeForSendCode != code_double_pres_no_shift && code_double_press != 0){
                Log.d(TAG, "onKeyDown isDoublePress=true");
                is_double_press = true;
                if(inputConnection!=null) {
                    //TODO: Подумать может быть не удалять символ, а вместо этого подождать второго нажатия?
                    inputConnection.deleteSurroundingText(1, 0);
                }
                codeForSendCode = code_double_press;
            }
        } else if(prev_key_press_btn_r1 == scanCode && repeatCount == 1){
            is_double_press = true;
            codeForSendCode = KeyToCharCode(scanCode, _altMode, true, true);;
        }
        //endregion

        //region Ввод СИМВОЛОВ/БУКВ/ЦИФР
        if(codeForSendCode != 0)
        {
            //region BB Apps HACK
            if(startInputAtBbContactsApp && !isEnglishKb){
                if(inputConnection!=null){
                    //Данный хак работает неплохо на первый взгляд и не выделяется виджет погоды на рабочем столе
                    keyDownUp4dpadMovements(KeyEvent.KEYCODE_SEARCH, inputConnection);
                }
                startInputAtBbContactsApp = false;
            }
            if(startInputAtBbPhoneApp && !isEnglishKb){
                if(inputConnection!=null){
                    //sendKeyChar((char) '_');
                    //inputConnection.deleteSurroundingText(1, 0);
                    keyDownUp4dpadMovements(KeyEvent.KEYCODE_0, inputConnection);
                    keyDownUp4dpadMovements(KeyEvent.KEYCODE_DEL, inputConnection);
                }
                startInputAtBbPhoneApp = false;
            }
            //endregion

            mAltPressTime = 0;

            // Это нужно только для выхода из других режимов Alt
            //TODO: Есть ощущение что это можно и легче как-то сделать
            if(event.isAltPressed()) altPlusBtn = true;

            if(pref_alt_space == false && altPressSingleSymbolAltedMode == true) altPressSingleSymbolAltedMode = false;

            if(is_double_press || _altMode == true){
                prev_key_press_btn_r1 = prev_key_press_btn_r0;
                prev_key_press_btn_r0 = 0;
                if (oneTimeShiftOneTimeBigMode == true){
                    oneTimeShiftOneTimeBigMode = false;
                    UpdateKeyboardModeVisualization();
                }
                shiftPressFirstButtonBigDoublePress = false;
            }else{
                prev_key_press_time = now;
                prev_key_press_btn_r0 = scanCode;
                prev_key_press_btn_r1 = 0;
                Log.d(TAG, "onKeyDown shiftPressFirstButtonBig="+ oneTimeShiftOneTimeBigMode);
                if (oneTimeShiftOneTimeBigMode == false)
                    shiftPressFirstButtonBigDoublePress = false;
                if (oneTimeShiftOneTimeBigMode == true){
                    shiftPressFirstButtonBigDoublePress = true;
                    oneTimeShiftOneTimeBigMode = false;
                    UpdateKeyboardModeVisualization();
                }
            }

            if(inputConnection!=null)
            {
                Log.d(TAG, "onKeyDown sendKeyChar="+ codeForSendCode);
                /* ХАК для телеграма т.к. когда входишь в диалог он создает какой-то еще Input и несколько символов улетает туда
                if(startInputAtTelegram) {
                    telegramStartInputBuffer[telegramStartInputBufferIndex] = (char) codeForSendCode;
                    telegramStartInputBufferIndex++;
                }
                else if(telegramStartInputBufferIndex > 0) {
                    String s = "";
                    for (int i = 0; i < telegramStartInputBufferIndex; i++) {
                        s = s+telegramStartInputBuffer[i];
                    }
                    //TODO: Доделать хак для Заглавных и Сдвоенных
                    inputConnection.commitText(s, 1);
                    telegramStartInputBufferIndex = 0;
                }
                 */
                //inputConnection.commitText(String.valueOf((char) codeForSendCode), 1);
                sendKeyChar((char) codeForSendCode);
                press_code_true = scanCode;

            }
            else
            {
                Log.d(TAG, "onKeyDown inputConnection==null");
            }

            //TODO: Разобраться зачем это надо!
           if (!inputViewShown && inputConnection != null) {
               CharSequence text = inputConnection.getTextBeforeCursor(1, 0);
            if (text != null && text.length() > 0)
                this.showWindow(true);

            }


            //это отслеживать больше не нужно. По этому закоментил
            //if(shiftAfterPoint && isAlphabet(codeForSendCode)) shiftAfterPoint = false;
            //if(!shiftPressAllButtonBig && (codeForSendCode == 46 || codeForSendCode == 63 ||  codeForSendCode == 33 || codeForSendCode == 191)) shiftAfterPoint = true;
            return true;
        }
        //endregion

        //region Обработка ENTER, SPACE, SHIFT+SPACE, 2xSPACE, DEL/BACKSPACE
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                keyDownUp(KeyEvent.KEYCODE_ENTER, inputConnection);
                prev_key_press_btn_r0 = 0;
                if (altPressSingleSymbolAltedMode == true){
                    altPressSingleSymbolAltedMode = false;
                    altShift = false;
                    //UpdateKeyboardModeVisualization();
                }
                IsFirstBigCharStateAndUpdateVisualization(currentInputEditorInfo);
                return true;
            case KeyEvent.KEYCODE_SPACE:

                if (altPressSingleSymbolAltedMode == true){
                    altPressSingleSymbolAltedMode = false;
                    altShift = false;
                    UpdateKeyboardModeVisualization();
                }
                if(shiftPressed && repeatCount == 0){
                    ChangeLanguage();
                    if(!IsFirstBigCharStateAndUpdateVisualization(currentInputEditorInfo))
                        UpdateKeyboardModeVisualization();
                    return true;
                }
                Log.d(TAG, "KEYCODE_SPACE prev_key_press_btn_r0 "+prev_key_press_btn_r0+" "+keyCode );

                //Двойной пробел -> "."
                if(inputViewShown && prev_key_press_btn_r0 == keyCode && now < prev_key_press_time+ DOUBLE_CLICK_TIME2 && inputConnection!=null){
                    CharSequence back_letter = inputConnection.getTextBeforeCursor(2,0);
                    Log.d(TAG, "KEYCODE_SPACE back_letter "+back_letter);
                    if(back_letter.length() == 2) {
                        if (Character.isLetterOrDigit(back_letter.charAt(0)) && back_letter.charAt(1) == ' ') {
                            inputConnection.deleteSurroundingText(1, 0);
                            inputConnection.commitText(".", 1);
                        }
                    }
                }else if(prev_key_press_btn_r0 == keyCode && now < prev_key_press_time+ DOUBLE_CLICK_TIME2 && handleShiftOnCalling()){
                    //Accept call
                    return true;
                }
                if(inputConnection!=null)
                    inputConnection.commitText(String.valueOf((char) SCAN_CODE_CHAR_SPACE), 1);
                Log.d(TAG, "KEYCODE_SPACE");
                IsFirstBigCharStateAndUpdateVisualization(currentInputEditorInfo);
                prev_key_press_time = now;
                prev_key_press_btn_r0 = keyCode;
                return true;
            case KeyEvent.KEYCODE_DEL:
                Log.d(TAG, "KEYCODE_DEL");
                prev_key_press_btn_r0 = 0;
                if(!shiftPressed) {
                    keyDownUp(KeyEvent.KEYCODE_DEL, inputConnection);
                }else{
                    if(inputConnection!=null)inputConnection.deleteSurroundingText(0,1);
                }
                IsFirstBigCharStateAndUpdateVisualization(currentInputEditorInfo);
                return true;

            default:
                prev_key_press_btn_r0 = 0;
        }
        //endregion

        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyUp "+event);
        int navigationKeyCode = 0;
        InputConnection ic = getCurrentInputConnection();
        // Обработайте отпускание клавиши, верните true, если обработка выполнена
        int scanCode = event.getScanCode();

        //region отжатие ALT
        if ((keyCode == KeyEvent.KEYCODE_ALT_LEFT) || (keyCode == KeyEvent.KEYCODE_1 && DEBUG)){
            if(menuEmulatedKeyPressed){ //вызов меню
                menuEmulatedKeyPressed = false;
                ic.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));
            }
            altPressed = false;
            if(altPlusBtn){
                //Если отпустили Alt после Alt+Bnt то надо оключить любой режим-Альтп и визуализацию
                altPressSingleSymbolAltedMode = false;
                doubleAltPressAllSymbolsAlted = false;
                altPlusBtn = false;
                if(!IsFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo()))
                    UpdateKeyboardModeVisualization();
            }
            return true;
        }
        //endregion

        //region отжание KEY_0
        if(scanCode == SCAN_CODE_KEY_0) {
            //long now = System.currentTimeMillis();
            if (!key_0_hold && event.getRepeatCount() == 0 && altPressed == false ){
                /*
                //Двойное нажатие KEY_0 активирует сенсор на клавиатуре
                //Но может и пригодиться где-то еще
                if(mKey0PressTime == 0) {
                    mKey0PressTime = now;
                } else if (mKey0PressTime + DOUBLE_CLICK_TIME > now) {
                    enable_keyboard_gestures = !enable_keyboard_gestures;
                    mKey0PressTime = 0;
                    UpdateKeyboardGesturesModeVisualization();
                } else {
                    //Пробуем перенос смены языка в KeyUp
                    ChangeLanguage();
                }
                */
                ChangeLanguage();
                return true;
            }else if (key_0_hold || mode_keyboard_gestures) {
                key_0_hold = false;
                mode_keyboard_gestures = false;
                UpdateKeyboardModeVisualization();
                return true;
            }
        }
        //endregion

        //Если идет набор текста, не учитываем движения по клавиатуре для резкого включения жестов-с-зажатым-нулем
        lastGestureSwipingBeginTime = 0;
        enteredGestureMovement = false;

        //region отжатие SHIFT LEFT
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || (keyCode == KeyEvent.KEYCODE_2 && DEBUG)){
            shiftPressed = false;
        }
        //endregion

        //region отжатие SHIFT RIGHT
        if(keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT || scanCode == SCAN_CODE_SHIFT){
            int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
            long now = System.currentTimeMillis();
            ic.sendKeyEvent(new KeyEvent(
                    now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta));
            ctrlImitatedByShiftRightPressed = false;
            return true;
        }
        //endregion

        if(mode_keyboard_gestures) {
            mode_keyboard_gestures = false;
            UpdateKeyboardGesturesModeVisualization();
        }

        //TODO: По идее функционал вызова меню и прочи KEYCODE_ACTION надо отправлять в "Переназначение клавиш"
        if(keyCode == SCAN_CODE_KEY_SYM && menuEmulatedKeyPressed){ //вызов меню
            menuEmulatedKeyPressed = false;
            ic.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));
        }

        //region Блок навигационной клавиатуры
        //TODO: Разобраться
        if(navigationOnScreenKeyboardMode &&
                ((scanCode == 11) ||
                        (scanCode == 5) ||
                        (scanCode >= 16 && scanCode <= 25) ||
                        (scanCode >= 30 && scanCode <= 38) ||
                        (scanCode >= 44 && scanCode <= 50)))
        {
            navigationKeyCode = getNavigationCode(scanCode);

            if(navigationKeyCode == -7) return true;
            //TODO: Возможно надо перевести на способ отправки как в keyDownUp4dpadMovements (с флагами)
            if(ic!=null && navigationKeyCode != 0) ic.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_UP, navigationKeyCode));
            return true;
        }
        //endregion

        if(keyCode == KeyEvent.KEYCODE_ENTER ||keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_DEL) return true;

        //если клавиша обработана при нажатии, то она же будет обработана и при отпускании
        if(press_code_true == scanCode) return true;

        return false;
    }

    //TODO: Вынести в XML
    public int getNavigationCode(int scanCode) {
        int keyEventCode = 0;
        switch (scanCode){
            case 16: //Q
                keyEventCode = 111; //ESC
                break;
            case 17: //W (1)
                if(fnSymbolOnScreenKeyboardMode) keyEventCode = 131; //F1
                break;
            case 18: //E (2)
                if(fnSymbolOnScreenKeyboardMode) keyEventCode = 132; //F2
                break;
            case 19: //R (3)
                if(fnSymbolOnScreenKeyboardMode) keyEventCode = 133; //F3
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
                if(fnSymbolOnScreenKeyboardMode) keyEventCode = 134; //F4
                break;
            case 32: //D (5)
                if(fnSymbolOnScreenKeyboardMode) keyEventCode = 135; //F5
                break;
            case 33: //F (6)
                if(fnSymbolOnScreenKeyboardMode) keyEventCode = 136; //F6
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
                if(fnSymbolOnScreenKeyboardMode) keyEventCode = 137; //F7
                break;
            case 45: //X (8)
                if(fnSymbolOnScreenKeyboardMode) keyEventCode = 138; //F8
                break;
            case 46: //C (9)
                if(fnSymbolOnScreenKeyboardMode) keyEventCode = 139; //F9
                break;

            case 11: //0
                if(fnSymbolOnScreenKeyboardMode) keyEventCode = 140; //F10
                break;

            default:
                keyEventCode = 0;
        }

        return keyEventCode;
    }

    @Override
    public boolean onKeyLongPress(int keyCode,KeyEvent event){
        Log.d(TAG, "onKeyLongPress "+event);
        return false;
    }

    @SuppressLint("InflateParams")
    @Override
    public View onCreateInputView() {
        Log.d(TAG, "onCreateInputView");
        keyboardView.setOnKeyboardActionListener(this);
        return keyboardView;
    }

    //private int lastOrientation = 0;
    private int lastVisibility = -1;

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        /*
        if(lastOrientation == 0)
            lastOrientation = newConfig.orientation;
        else if (lastOrientation != newConfig.orientation) {
            lastOrientation = newConfig.orientation;
        }

         */

        if(newConfig.orientation == 2) {
            if(keyboardView.getVisibility() == View.VISIBLE) {
                lastVisibility = View.VISIBLE;
                keyboardView.setVisibility(View.GONE);
            }
        }
        else if(newConfig.orientation == 1) {
            if(lastVisibility == View.VISIBLE) {
                lastVisibility = 0;
                keyboardView.setVisibility(View.VISIBLE);
            }
            else
                keyboardView.setVisibility(View.GONE);
        }

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

        IsFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());

    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {

        Log.d(TAG, "onKey "+primaryCode);
        InputConnection inputConnection = getCurrentInputConnection();
        playClick(primaryCode);
        if(navigationOnScreenKeyboardMode) {
            switch (primaryCode) {

                case 19: //UP
                    keyDownUp4dpadMovements(KeyEvent.KEYCODE_DPAD_UP, inputConnection);
                    IsFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
                    break;
                case 20: //DOWN
                    keyDownUp4dpadMovements(KeyEvent.KEYCODE_DPAD_DOWN, inputConnection);
                    IsFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
                    break;

                case 21: //LEFT
                    keyDownUp4dpadMovements(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
                    IsFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
                    break;
                case 22: //RIGHT
                    keyDownUp4dpadMovements(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
                    IsFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
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
                    keyDownUp(primaryCode, inputConnection);
                    break;

                case -7:  //Switch F1-F12
                    fnSymbolOnScreenKeyboardMode = !fnSymbolOnScreenKeyboardMode;
                    UpdateKeyboardModeVisualization();
                    keyboardView.setFnSymbol(fnSymbolOnScreenKeyboardMode);
                    break;

                case Keyboard.KEYCODE_DELETE:
                    inputConnection.deleteSurroundingText(1, 0);
                    IsFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DONE:
                    keyDownUp(KeyEvent.KEYCODE_ENTER, inputConnection);
                    IsFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
                    break;

                default:

            }
        }else{
            switch (primaryCode) {
                //Хак чтобы не ставился пробел после свайпа по свайп-анели
                case 0: //SPACE
                case 32: //SPACE
                    break;
                case 21: //LEFT
                    keyDownUp4dpadMovements(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
                    IsFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
                    break;
                case 22: //RIGHT
                    keyDownUp4dpadMovements(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
                    IsFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DELETE:
                    inputConnection.deleteSurroundingText(1, 0);
                    IsFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DONE:
                    keyDownUp(KeyEvent.KEYCODE_ENTER, inputConnection);
                    IsFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
                    break;
                default:
                    char code = (char) primaryCode;
                    inputConnection.commitText(String.valueOf(code), 1);
            }
        }

    }

    private void playClick(int i){
/* Пока запиливаем звук, он ругается в дебаге
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
*/
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
        int motionEventAction = motionEvent.getAction();
        if(!showSymbolOnScreenKeyboard){
            if(motionEventAction == MotionEvent.ACTION_DOWN) lastGestureX = motionEvent.getX();
            if(motionEventAction == MotionEvent.ACTION_MOVE && lastGestureX +(36- pref_gesture_motion_sensitivity) < motionEvent.getX()){
                if(this.isInputViewShown()) {
                    CharSequence c = inputConnection.getTextAfterCursor(1, 0);
                    if(c.length() > 0) {
                        keyDownUp4dpadMovements(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
                        IsFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
                    }
                    lastGestureX = motionEvent.getX();
                    Log.d(TAG, "onTouch KEYCODE_DPAD_RIGHT " + motionEvent);
                }
            }else  if(motionEventAction == MotionEvent.ACTION_MOVE && lastGestureX -(36- pref_gesture_motion_sensitivity) > motionEvent.getX()){
                if(this.isInputViewShown()) {
                    CharSequence c = inputConnection.getTextBeforeCursor(1, 0);
                    if (c.length() > 0) {
                        keyDownUp4dpadMovements(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
                        IsFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
                    }
                    lastGestureX = motionEvent.getX();
                    Log.d(TAG, "onTouch sens_botton_bar "+ pref_gesture_motion_sensitivity +" KEYCODE_DPAD_LEFT " + motionEvent);
                }
            }
        }else{
            //TODO: Разобраться что это
            if(motionEventAction == MotionEvent.ACTION_MOVE)keyboardView.coordsToIndexKey(motionEvent.getX());
            if(motionEventAction == MotionEvent.ACTION_UP  )keyboardView.hidePopup(true);
        }
        return false;
    }
    @Override
    public void onUpdateCursorAnchorInfo(CursorAnchorInfo cursorAnchorInfo)
    {
        //TODO: Готовимся работать с курсором
    }

    private long lastGestureSwipingBeginTime = 0;
    private boolean enteredGestureMovement = false;
    private boolean debug_gestures = false;

    @Override
    public boolean onGenericMotionEvent(MotionEvent motionEvent) {
        //Log.d(TAG, "onGenericMotionEvent(): " + motionEvent);

        if (pref_keyboard_gestures_at_views_enable && !navigationOnScreenKeyboardMode && !this.isInputViewShown()) {

        }
        if (!mode_keyboard_gestures && motionEvent.getY() <= ROW_4_BEGIN_Y) {
            if (debug_gestures)
                Log.d(TAG, "onGenericMotionEvent(): " + motionEvent);
            if (motionEvent.getAction() == MotionEvent.ACTION_UP
                    || motionEvent.getAction() == MotionEvent.ACTION_CANCEL
                    || motionEvent.getAction() == MotionEvent.ACTION_POINTER_UP
            ) {
                lastGestureSwipingBeginTime = 0;
                enteredGestureMovement = false;
            }
            if (motionEvent.getAction() == MotionEvent.ACTION_MOVE && enteredGestureMovement) {
                lastGestureSwipingBeginTime = System.currentTimeMillis();
                lastGestureX = motionEvent.getX();
                lastGestureY = motionEvent.getY();
            }
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN
                    || motionEvent.getAction() == MotionEvent.ACTION_POINTER_DOWN) {
                lastGestureSwipingBeginTime = System.currentTimeMillis();
                lastGestureX = motionEvent.getX();
                lastGestureY = motionEvent.getY();
                enteredGestureMovement = true;
            }
        }
        if(navigationOnScreenKeyboardMode)
            return true;

        if (pref_keyboard_gestures_at_views_enable
                && !showSymbolOnScreenKeyboard
                && !this.isInputViewShown()){
                //Если мы не в поле ввода передаем жесты дальше
                return false;
        }
        else if(mode_keyboard_gestures){

            //TODO: Подумать отдельно обрабатывать жесты по горизонтали и отдельно по вертикали ориентируясь на событие ACTION_UP
            //TODO: Для выделения с зажатым нулем - подумать переходить в режим выделения-с-SHIFT-ом через 2xSHIFT

            InputConnection inputConnection = getCurrentInputConnection();
            float motionEventX = motionEvent.getX();
            float motionEventY = motionEvent.getY();

            //Не ловим движение на нижнем ряду где поблел и переключение языка
            if(motionEventY > ROW_4_BEGIN_Y) return true;

            int motionEventAction = motionEvent.getAction();
            if(!showSymbolOnScreenKeyboard){

                //Жесть по клавиатуре всегда начинается с ACTION_DOWN
                if(motionEventAction == MotionEvent.ACTION_DOWN
                || motionEventAction == MotionEvent.ACTION_POINTER_DOWN) {
                    if(debug_gestures)
                        Log.d(TAG, "onGenericMotionEvent ACTION_DOWN " + motionEvent);
                    lastGestureX = motionEventX;
                    lastGestureY = motionEventY;
                    return true;
                }

                if(motionEventAction == MotionEvent.ACTION_MOVE
                        || motionEventAction == MotionEvent.ACTION_UP
                        || motionEventAction == MotionEvent.ACTION_POINTER_UP) {
                    float deltaX = motionEventX - lastGestureX;
                    float absDeltaX = deltaX < 0 ? -1*deltaX : deltaX;
                    float deltaY = motionEventY - lastGestureY;
                    float absDeltaY = deltaY < 0 ? -1*deltaY : deltaY;

                    int motion_delta_min_x = MAGIC_KEYBOARD_GESTURE_MOTION_CONST - pref_gesture_motion_sensitivity;
                    int motion_delta_min_y = MAGIC_KEYBOARD_GESTURE_MOTION_CONST - pref_gesture_motion_sensitivity;

                    if(absDeltaX >= absDeltaY) {
                        if(absDeltaX < motion_delta_min_x)
                            return true;
                        if (deltaX > 0) {
                            CharSequence c = inputConnection.getTextAfterCursor(1, 0);
                            if(c.length() > 0) {
                                keyDownUp4dpadMovements(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
                                IsFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
                            }

                            Log.d(TAG, "onGenericMotionEvent KEYCODE_DPAD_RIGHT " + motionEvent);
                        } else {
                            CharSequence c = inputConnection.getTextBeforeCursor(1, 0);
                            if (c.length() > 0) {
                                keyDownUp4dpadMovements(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
                                IsFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
                            }

                            Log.d(TAG, "onGenericMotionEvent KEYCODE_DPAD_LEFT " + motionEvent);
                        }
                    } else {
                        if(absDeltaY < motion_delta_min_y)
                            return true;
                        //int times = Math.round(absDeltaY / motion_delta_min_y);
                        if (deltaY < 0) {

                            //TODO: Сделать хождение по большим текстам, пока оставляем только горизонтальные движения
                            //keyDownUp2(KeyEvent.KEYCODE_DPAD_UP, inputConnection);

                            //Log.d(TAG, "onGenericMotionEvent KEYCODE_DPAD_UP " + motionEvent);
                        } else {

                            //TODO: Родная клава просто вылеает из режима Keypad, когда заползаешь за поле ввода, найти где это происходит и сделать также или как минимум взять это условие в вернуть курсор обратно
                            //keyDownUp2(KeyEvent.KEYCODE_DPAD_DOWN, inputConnection);

                            //Log.d(TAG, "onGenericMotionEvent KEYCODE_DPAD_DOWN  " + motionEvent);
                        }
                    }

                    lastGestureX = motionEventX;
                    lastGestureY = motionEventY;
                }
            }else{

                //TODO: Разобраться зачем это
                if(motionEventAction == MotionEvent.ACTION_MOVE)keyboardView.coordsToIndexKey(motionEventX);
                if(motionEventAction == MotionEvent.ACTION_UP  )keyboardView.hidePopup(true);
            }
            return true;
        }

        return true;
    }

    public class KeyLayouts
    {
        public int scan_code = 0;
        public int one_press = 0;
        public int double_press = 0;
        public int one_press_shift = 0;
        public int double_press_shift = 0;
        public int alt = 0;
        public int shift = 0;
        public int alt_shift = 0;
        public String alt_popup = "";
        public String alt_shift_popup = "";
    }

    public class KeybordLayout
    {
        public String LanguageOnScreenNaming = "";
        public int XmlId = 0;
        public int Id = 0;
        public int IconCaps;
        public int IconCapsTouch;
        public int IconFirstShift;
        public int IconFirstShiftTouch;
        public int IconLittle;
        public int IconLittleTouch;

        public HashMap<Integer, KeyLayouts> KeyLayoutsMap = new HashMap<Integer, KeyLayouts>();
    }

    public ArrayList<KeybordLayout> KeybordLayoutList = new ArrayList<KeybordLayout>();

    private KeybordLayout LoadLayout2(int xmlId, int currentKeyBoardSetId)
    {

        int scan_code = 0;
        int one_press = 0;
        int double_press = 0;
        int double_press_shift = 0;
        int alt = 0;
        int shift = 0;
        int alt_shift = 0;
        String alt_popup = "";
        String alt_shift_popup = "";
        String languageOnScreenNaming = "";

        KeybordLayout keyboardLayout = new KeybordLayout();
        keyboardLayout.Id = xmlId;
        keyboardLayout.KeyLayoutsMap = new HashMap<Integer, KeyLayouts>();

        try {
            XmlPullParser parser = getResources().getXml(xmlId);

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
                    if (parser.getAttributeName(i).equals("lang")) languageOnScreenNaming = parser.getAttributeValue(i);
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
                    KeyLayouts keyLayout = new KeyLayouts();
                    keyLayout.scan_code = scan_code;
                    keyLayout.one_press = one_press;
                    keyLayout.one_press_shift = shift;
                    keyLayout.double_press = double_press;
                    keyLayout.double_press_shift = double_press_shift;
                    keyLayout.alt = alt;
                    keyLayout.alt_shift = alt_shift;
                    keyLayout.alt_popup = alt_popup;
                    keyLayout.alt_shift_popup = alt_shift_popup;

                    keyboardLayout.KeyLayoutsMap.put(scan_code, keyLayout);
                }
                parser.next();
            }
            keyboardLayout.LanguageOnScreenNaming = languageOnScreenNaming;
            keyboardLayout.XmlId = xmlId;
            KeybordLayoutList.add(currentKeyBoardSetId, keyboardLayout);
        } catch (Throwable t) {
            Toast.makeText(this,
                    "Ошибка при загрузке XML-документа: " + t.toString(),
                    Toast.LENGTH_LONG).show();
        }

        LoadAltLayout2(keyboardLayout.KeyLayoutsMap);
        return keyboardLayout;
    }

    private void LoadAltLayout2(HashMap<Integer, KeyLayouts> keyLayoutsHashMap)
    {
        int scan_code = 0;
        int alt = 0;
        int alt_shift = 0;
        String alt_popup = "";
        String alt_shift_popup = "";

        try {
            XmlPullParser parser;
            parser = getResources().getXml(R.xml.alt_hw);

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
                    KeyLayouts keyLayouts = keyLayoutsHashMap.get(scan_code);
                    if(keyLayouts == null)
                    {
                        keyLayouts = new KeyLayouts();
                        keyLayoutsHashMap.put(scan_code, keyLayouts);
                    }

                    if(alt != 0 && keyLayouts.alt == 0)
                        keyLayouts.alt = alt;
                    if(alt_shift != 0 && keyLayouts.alt_shift == 0)
                        keyLayouts.alt_shift = alt_shift;
                    if(!alt_popup.isEmpty() && keyLayouts.alt_popup.isEmpty())
                        keyLayouts.alt_popup = alt_popup;
                    if(!alt_shift_popup.isEmpty() && keyLayouts.alt_shift_popup.isEmpty())
                        keyLayouts.alt_shift_popup = alt_popup;
                }
                parser.next();
            }
        } catch (Throwable t) {
            Toast.makeText(this,
                    "Ошибка при загрузке XML-документа: " + t.toString(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private int KeyToCharCode(int key, boolean alt_press, boolean shift_press, boolean is_double_press)
    {
        int result = 0;
        KeyLayouts keyLayouts = KeybordLayoutList.get(CurrentLanguageListIndex).KeyLayoutsMap.get(key);
        if(keyLayouts == null)
            return 0;
        if (alt_press == true && shift_press == true && keyLayouts.alt_shift != 0) {
            result = keyLayouts.alt_shift;
        } else if (alt_press == true && keyLayouts.alt != 0) {
            result = keyLayouts.alt;
        } else if (is_double_press == true && shift_press == true && keyLayouts.double_press_shift != 0) {
            result = keyLayouts.double_press_shift;
        } else if (is_double_press == true && keyLayouts.double_press != 0) {
            result = keyLayouts.double_press;
        } else if (shift_press == true && keyLayouts.one_press_shift != 0) {
            result = keyLayouts.one_press_shift;
        } else {
            result = keyLayouts.one_press;
        }

        return result;
    }
    private void UpdateKeyboardModeVisualization()
    {
        UpdateKeyboardModeVisualization(pref_show_default_onscreen_keyboard);
    }
    private void UpdateKeyboardModeVisualization(boolean updateGesturePanelData) {
        Log.d(TAG, "UpdateKeyboardModeVisualization oneTimeShiftOneTimeBigMode="+ oneTimeShiftOneTimeBigMode +" doubleShiftCapsMode="+ doubleShiftCapsMode +" doubleAltPressAllSymbolsAlted="+ doubleAltPressAllSymbolsAlted +" altPressSingleSymbolAltedMode="+ altPressSingleSymbolAltedMode);
        KeybordLayout keyboardLayout = KeybordLayoutList.get(CurrentLanguageListIndex);
        String languageOnScreenNaming = keyboardLayout.LanguageOnScreenNaming;
        boolean changed = false;

        if(navigationOnScreenKeyboardMode){
            if(!fnSymbolOnScreenKeyboardMode){
                changed |= SetSmallIcon(R.mipmap.ic_kb_nav);
                changed |= SetContentTitle(TITLE_NAV_TEXT);
            } else {
                changed |= SetSmallIcon(R.mipmap.ic_kb_nav_fn);
                changed |= SetContentTitle(TITLE_NAV_FV_TEXT);
            }
            onScreenKeyboardSymbols = keybardNavigation;
            keyboardView.setKeyboard(onScreenKeyboardSymbols);
            keyboardView.setNavigationLayer();
            MakeVisible();
        }else if(showSymbolOnScreenKeyboard) {
            changed |= SetSmallIcon(R.mipmap.ic_kb_sym);
            changed |= SetContentTitle(TITLE_SYM_TEXT);
            //TODO: Тут плодятся объекты зачем-то
            onScreenKeyboardSymbols = new Keyboard(this, R.xml.symbol);;
            keyboardView.setKeyboard(onScreenKeyboardSymbols);
            //TODO: Сделать предзагрузку этой клавиатуры
            if(altShift) {
                keyboardView.setAltLayer(KeybordLayoutList.get(CurrentLanguageListIndex), true);
            }else{
                keyboardView.setAltLayer(KeybordLayoutList.get(CurrentLanguageListIndex), false);
            }
            MakeVisible();
        }else if(doubleAltPressAllSymbolsAlted){
            changed |= SetSmallIcon(R.mipmap.ic_kb_sym);
            changed |= SetContentTitle(TITLE_SYM_TEXT);
            keyboardView.setKeyboard(onScreenKeyboardDefaultGesturesAndLanguage);
            if(updateGesturePanelData) {
                if (altShift) {
                    keyboardView.setLang(TITLE_SYM2_TEXT);
                } else {
                    keyboardView.setLang(TITLE_SYM_TEXT);
                }
                keyboardView.setAlt();
            }
            HideGesturePanelOnHidePreferenceAndVisibleState();
        }else if(altPressSingleSymbolAltedMode){
            changed |= SetSmallIcon(R.mipmap.ic_kb_sym_one);
            changed |= SetContentTitle(TITLE_SYM_TEXT);
            keyboardView.setKeyboard(onScreenKeyboardDefaultGesturesAndLanguage);
            if(updateGesturePanelData) {
                if (altShift) {
                    keyboardView.setLang(TITLE_SYM2_TEXT);
                } else {
                    keyboardView.setLang(TITLE_SYM_TEXT);
                }
                keyboardView.setAlt();
            }
            HideGesturePanelOnHidePreferenceAndVisibleState();
        }else if(doubleShiftCapsMode){
            changed |= SetContentTitle(languageOnScreenNaming);
            keyboardView.setKeyboard(onScreenKeyboardDefaultGesturesAndLanguage);
            if(updateGesturePanelData) {
                keyboardView.setLang(languageOnScreenNaming);
                keyboardView.setShiftAll();
                keyboardView.setLetterKB();
            }
            HideGesturePanelOnHidePreferenceAndVisibleState();
        }else if(oneTimeShiftOneTimeBigMode){
            changed |= SetContentTitle(languageOnScreenNaming);
            keyboardView.setKeyboard(onScreenKeyboardDefaultGesturesAndLanguage);
            if(updateGesturePanelData) {
                keyboardView.setLang(languageOnScreenNaming);
                keyboardView.setShiftFirst();
                keyboardView.setLetterKB();
            }
            HideGesturePanelOnHidePreferenceAndVisibleState();
        } else {
            // Случай со строными буквами
            changed |= SetContentTitle(languageOnScreenNaming);
            keyboardView.setKeyboard(onScreenKeyboardDefaultGesturesAndLanguage);
            if(updateGesturePanelData) {
                keyboardView.notShift();
                keyboardView.setLang(languageOnScreenNaming);
                keyboardView.setLetterKB();
            }
            HideGesturePanelOnHidePreferenceAndVisibleState();
        }


        //INSIDE: notificationManager.notify(1, builder.build());
        UpdateKeyboardGesturesModeVisualization(changed);
    }

    private void HideGesturePanelOnHidePreferenceAndVisibleState()
    {
        if(!pref_show_default_onscreen_keyboard && keyboardView.getVisibility() == View.VISIBLE){
            keyboardView.setVisibility(View.GONE);
        }
    }

    private void MakeVisible() {
        if (keyboardView.getVisibility() != View.VISIBLE)
            keyboardView.setVisibility(View.VISIBLE);
    }

    private void UpdateKeyboardGesturesModeVisualization() {
        UpdateKeyboardGesturesModeVisualization(false);
    }

    private void UpdateKeyboardGesturesModeVisualization(boolean changedTitle) {
        KeybordLayout keyboardLayout = KeybordLayoutList.get(CurrentLanguageListIndex);

        boolean changed = changedTitle;

        if(navigationOnScreenKeyboardMode
        || showSymbolOnScreenKeyboard
        || doubleAltPressAllSymbolsAlted
        || altPressSingleSymbolAltedMode){
            //Ничего делать не надо т.к. иконка для жестов не меняется
        } else if(doubleShiftCapsMode){
            if(mode_keyboard_gestures)
                changed |= SetSmallIcon(keyboardLayout.IconCapsTouch);
            else
                changed |= SetSmallIcon(keyboardLayout.IconCaps);

        } else if(oneTimeShiftOneTimeBigMode){
            if(mode_keyboard_gestures)
                changed |= SetSmallIcon(keyboardLayout.IconFirstShiftTouch);
            else
                changed |= SetSmallIcon(keyboardLayout.IconFirstShift);
        } else {
            // Случай со строными буквами
            if(mode_keyboard_gestures)
                changed |= SetSmallIcon(keyboardLayout.IconLittleTouch);
            else
                changed |= SetSmallIcon(keyboardLayout.IconLittle);
        }
        if(changed)
                NotificationManagerNotify();
    }

    private void NotificationManagerNotify() {
        if(builder != null)
            notificationManager.notify(1, builder.build());
        else if (builder2 != null) {
            notificationManager.notify(1, builder2.build());
        }
    }

    int currentIcon = 0;
    String currentTitle = "";

    private boolean SetSmallIcon(int icon1) {
        if(builder != null) {
            builder.setSmallIcon(icon1);
        } else if (builder2 != null) {
            builder2.setSmallIcon(icon1);
        }
        //Changed
        if(currentIcon != icon1) {
            currentIcon = icon1;
            return true;
        }
        else
            return false;
    }

    private boolean SetContentTitle(String title) {
        if(builder != null)
            builder.setContentTitle(title);
        else if (builder2 != null)
            builder2.setContentTitle(title);
        //Changed
        if(currentTitle.compareTo(title) != 0) {
            currentTitle = title;
            return true;
        }
        else
            return false;
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
                //TODO: Problem: tm.endCall();
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
        CurrentLanguageListIndex++;
        if(CurrentLanguageListIndex > LangListCount - 1) CurrentLanguageListIndex = 0;
        if(CurrentLanguageListIndex == 0){
            isEnglishKb = true;
        }else{
            isEnglishKb = false;
        }
        if(pref_show_toast) {
            toast = Toast.makeText(getApplicationContext(), KeybordLayoutList.get(CurrentLanguageListIndex).LanguageOnScreenNaming, Toast.LENGTH_SHORT);
            toast.show();
        }
        UpdateKeyboardModeVisualization();
    }

    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }

    private void keyDownUp(int keyEventCode, InputConnection ic) {
        if (ic == null) return;

        ic.sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        ic.sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    private void keyDownUp4dpadMovements(int keyEventCode, InputConnection ic) {
        if (ic == null) return;
        long uptimeMillis = SystemClock.uptimeMillis();

        ic.sendKeyEvent(
                new KeyEvent(uptimeMillis, uptimeMillis, KeyEvent.ACTION_DOWN, keyEventCode, 0, 0, -1, 0, 6));
        ic.sendKeyEvent(
                new KeyEvent(SystemClock.uptimeMillis(), uptimeMillis, KeyEvent.ACTION_UP, keyEventCode, 0, 0, -1, 0, 6));
    }

    //TODO: Иногда вызывается по несколько раз подряд (видимо из разных мест)
    private boolean IsFirstBigCharStateAndUpdateVisualization(EditorInfo editorInfo) {
        //Если мы вывалились из зоны ввода текста
        //NOTE: Проверка не дает вводить Заглавную прям на первом входе в приложение. Видимо не успевает еще активироваться.
        //if(!isInputViewShown())
        //    return;

        if(editorInfo == null)
            return false;

        //TODO: Минорно. Если надо знать какие флаги их надо расшифровывать
        Log.d(TAG, "IsFirstBigCharStateAndUpdateVisualization editorInfo.inputType: "+Integer.toBinaryString(editorInfo.inputType));

        if (altPressSingleSymbolAltedMode
                || doubleAltPressAllSymbolsAlted
                || altPressed
                || doubleShiftCapsMode)
            return false;

        int makeFirstBig = 0;
        if (editorInfo.inputType != InputType.TYPE_NULL) {
            makeFirstBig = getCurrentInputConnection().getCursorCapsMode(editorInfo.inputType);
        }

        if(makeFirstBig != 0){
            if(!oneTimeShiftOneTimeBigMode) {
                oneTimeShiftOneTimeBigMode = true;
                Log.d(TAG, "updateShiftKeyState (changed to) oneTimeShiftOneTimeBigMode = true");
                UpdateKeyboardModeVisualization();
                return true;
            }
        }else if (makeFirstBig == 0) {
            if(oneTimeShiftOneTimeBigMode) {
                oneTimeShiftOneTimeBigMode = false;
                Log.d(TAG, "updateShiftKeyState (changed to) oneTimeShiftOneTimeBigMode = false");
                UpdateKeyboardModeVisualization();
                return true;
            }
        }
        return false;
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
