
package com.sateda.keyonekb;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.Keep;
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
    public static final String APP_PREFERENCES_SHOW_DEFAULT_ONSCREEN_KEYBOARD = "show_default_onscreen_keyboard";

    public static final int SCAN_CODE_KEY_0 = 11;
    public static final int CHAR_0 = 48;
    public static final int KEY_SYM = 100;
    public static final int SCAN_CODE_SHIFT = 110;
    public static final int DOUBLE_CLICK_TIME = 400;
    public static final int CHAR_SPACE = 32;
    public static final int DOUBLE_CLICK_TIME2 = 500;
    public static final int SCAN_CODE_CURRENCY = 183;
    public static final int MAGIC_KEYBOARD_GESTURE_MOTION_CONST = 36;

    private CallStateCallback callStateCallback;

    private NotificationManager notificationManager;
    private CandidateView mCandidateView;

    private SatedaKeyboardView keyboardView;
    private Keyboard onScreenKeyboardDefaultGesturesAndLanguage;
    private Keyboard onScreenKeyboardSymbols;

    private Boolean startInputAtBbContactsApp = false; // костыль для приложения Блекбери контакты
    private Boolean startInputAtBbPhoneApp = false; // аналогичный костыль для приложения Телефон чтобы в нем искалось на русском языке
    private Boolean inputAtBbLauncherApp = false;

    private SharedPreferences mSettings;

    private boolean isEnglishKb = false;
    private int langNum = 0;
    private int langCount = 1;
    private int[] langArray;

    private Toast toast;

    private boolean ctrlPressed = false; // только первая буква будет большая

    private long mShiftFirstPressTime;
    private boolean shiftPressFirstButtonBig; // только первая буква будет большая
    private boolean shiftPressFirstButtonBigDoublePress; // только первая буква будет большая (для двойного нажатия на одну и туже кнопку)
    private boolean shiftAfterPoint; // большая буква после точки (все отдано на откуп операционке, скорее всего надо будет удалить эту переменную)
    private boolean shiftPressAllButtonBig; //все следующий буквы будут большие
    private boolean shiftPressed; //нажатие клавишь с зажатым альтом

    private long mCtrlPressTime;
    private long mKey0PressTime;
    private long mAltPressTime;
    private boolean altPressFirstButtonBig;
    private boolean altPressAllButtonBig;
    private boolean altShift;
    private boolean showSymbolOnScreenKeyboard;
    private boolean navigationOnScreenKeyboardMode;
    private boolean fnSymbolOnScreenKeyboardMode;

    private boolean menuEmulatedKeyPressed; //нажатие клавишь с зажатым альтом

    private boolean altPressed; //нажатие клавишь с зажатым альтом
    private boolean altPlusBtn;

    private String languageOnScreenNaming = "";
    private String lastPackageName = "";

    private float lastGestureX;
    private float lastGestureY;

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
    private int gesture_motion_sensivity = 10;
    private boolean pref_show_toast = false;
    private boolean pref_alt_space = true;
     private boolean pref_flag = false;
    private boolean pref_longpress_alt = false;
    private boolean pref_show_default_onscreen_keyboard = true;
    private boolean key_0_hold = false;

    private boolean mode_keyboard_gestures = false;
    private boolean mode_altMode = false;

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
        shiftPressFirstButtonBig = false;
        shiftPressFirstButtonBigDoublePress = false;
        shiftAfterPoint = false;
        shiftPressAllButtonBig  = false;
        shiftPressed = false;
        mKey0PressTime = 0;
        mCtrlPressTime = 0;
        mAltPressTime = 0;
        altPressFirstButtonBig = false;
        altPressAllButtonBig = false;
        menuEmulatedKeyPressed = false;
        altShift = false;
        altPressed = false;
        altPlusBtn = false;
        showSymbolOnScreenKeyboard = false;
        navigationOnScreenKeyboardMode = false;
        fnSymbolOnScreenKeyboardMode = false;
        ctrlPressed = false;
        key_0_hold = false;

        pref_height_botton_bar = 10;

        pref_show_toast = false;
        pref_alt_space = true;
        pref_longpress_alt = false;

        initKeys();
        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        loadSetting();

        onScreenKeyboardDefaultGesturesAndLanguage = new SatedaKeyboard(this, R.xml.space_empty, 70+pref_height_botton_bar*5);

        onScreenKeyboardSymbols = new SatedaKeyboard(this, R.xml.symbol);

        keyboardView = (SatedaKeyboardView) getLayoutInflater().inflate(R.layout.keyboard,null);
        keyboardView.setKeyboard(onScreenKeyboardDefaultGesturesAndLanguage);
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

        //Борьба с Warning для SDK_V=27 (KEY2 вестимо)
        //04-10 20:36:34.040 13838-13838/xxx.xxxx.xxxx W/Notification: Use of stream types is deprecated for operations other than volume control
        //See the documentation of setSound() for what to use instead with android.media.AudioAttributes to qualify your playback use case
//        String channelId = "default_channel_id";
//        String channelDescription = "Default Channel";
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
//            NotificationChannel notificationChannel = notificationManager.getNotificationChannel(channelId);
//            if (notificationChannel == null) {
//                int importance = NotificationManager.IMPORTANCE_HIGH; //Set the importance level
//                notificationChannel = new NotificationChannel(channelId, channelDescription, importance);
//                notificationChannel.setLightColor(Color.GREEN); //Set if it is necesssary
//                notificationChannel.enableVibration(false); //Set if it is necesssary
//                notificationManager.createNotificationChannel(notificationChannel);
//            }
//        }
//         new NotificationCompat.Builder(getApplicationContext(), channelId);

        //NotificationCompat.Builder builder;

        Context context = getApplicationContext();

        builder = new android.support.v7.app.NotificationCompat.Builder(context);

        builder.setSmallIcon(R.mipmap.ic_rus_small);
        builder.setContentTitle("Русский");
        builder.setOngoing(true);
        builder.setAutoCancel(false);
        builder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        notificationManager.notify(1, builder.build());

        //TODO: BUG зачем это тут?
        //Сделать подгрузку языков и апдейт экрана тут-же
        //По дефолту английский
        ChangeLanguage();
    }

    private void loadSetting(){
        langCount = 1;
        pref_show_toast = false;
        gesture_motion_sensivity = 10;

        boolean lang_ru_on = true;
        boolean lang_translit_ru_on = false;
        boolean lang_ua_on = false;

        if(mSettings.contains(APP_PREFERENCES_SHOW_DEFAULT_ONSCREEN_KEYBOARD)) {
            pref_show_default_onscreen_keyboard = mSettings.getBoolean(APP_PREFERENCES_SHOW_DEFAULT_ONSCREEN_KEYBOARD, true);
        }

        if(mSettings.contains(APP_PREFERENCES_RU_LANG)) {
            lang_ru_on = mSettings.getBoolean(APP_PREFERENCES_RU_LANG, true);
            if(lang_ru_on) langCount++;
        }else{
            if(lang_ru_on) langCount++;
        }

        if(mSettings.contains(APP_PREFERENCES_TRANSLIT_RU_LANG)) {
            lang_translit_ru_on = mSettings.getBoolean(APP_PREFERENCES_TRANSLIT_RU_LANG, false);
            if(lang_translit_ru_on) langCount++;
        }

        if(mSettings.contains(APP_PREFERENCES_UA_LANG)) {
            lang_ua_on = mSettings.getBoolean(APP_PREFERENCES_UA_LANG, false);
            if(lang_ua_on) langCount++;
        }

        if(mSettings.contains(APP_PREFERENCES_SENS_BOTTON_BAR)) {
            gesture_motion_sensivity = mSettings.getInt(APP_PREFERENCES_SENS_BOTTON_BAR, 10);
        }

        if(mSettings.contains(APP_PREFERENCES_SHOW_TOAST)) {
            pref_show_toast = mSettings.getBoolean(APP_PREFERENCES_SHOW_TOAST, false);
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

            langArray[langCount] = R.xml.russian_hw;
            langCount++;
        }
        if(lang_translit_ru_on){
            langArray[langCount] = R.xml.russian_translit_hw;
            langCount++;
        }
        if(lang_ua_on){
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
        if(showSymbolOnScreenKeyboard == true) {
            showSymbolOnScreenKeyboard = false;
            altPressFirstButtonBig = false;
            altPressAllButtonBig = false;
            altShift = false;
            UpdateKeyboardModeVisualization();
        }

        if(lastPackageName.equals("com.sateda.keyonekb")) loadSetting();

        //TODO: Подумать, чтобы не надо было инициализировать свайп-клавиаутуру по настройке pref_show_default_onscreen_keyboard
         keyboardView.showFlag(pref_flag);
        if (onScreenKeyboardDefaultGesturesAndLanguage.getHeight() != 70 + pref_height_botton_bar * 5)
            onScreenKeyboardDefaultGesturesAndLanguage = new SatedaKeyboard(this, R.xml.space_empty, 70 + pref_height_botton_bar * 5);

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

        // Обрабатываем переход между приложениями
        if(!attribute.packageName.equals(lastPackageName))
        {
            lastPackageName = attribute.packageName;

            //Отключаем режим навигации
            //TODO: ExtractMethod не думаю что это делается только тут
            navigationOnScreenKeyboardMode = false;
            fnSymbolOnScreenKeyboardMode = false;
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
            && scanCode != KEY_SYM
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
        boolean altMode = false;
        int navigationKeyCode = 0;
        int code = 0;

        EditorInfo currentInputEditorInfo = getCurrentInputEditorInfo();

        //region нажатие клавиши CTRL, CTRL+SHIFT, 2xCTRL
        if(keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT || scanCode == SCAN_CODE_SHIFT){
            int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
            if(inputConnection!=null)
                inputConnection.sendKeyEvent(new KeyEvent(
                    now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta));
            ctrlPressed = true;
            //show hide display keyboard
            //TODO: Вынести в настройку
            if(shiftPressed && keyboardView.isShown()) {
                keyboardView.setVisibility(View.GONE);
                updateShiftKeyState(currentInputEditorInfo);
            }else if(shiftPressed && !keyboardView.isShown()) {
                keyboardView.setVisibility(View.VISIBLE);
                updateShiftKeyState(currentInputEditorInfo);
            }
            //Двойное нажатие Ctrl активирует сенсор на клавиатуре
            if(repeatCount == 0) {
                if (mCtrlPressTime + DOUBLE_CLICK_TIME2 > now && !shiftPressed) {
                    mode_keyboard_gestures = !mode_keyboard_gestures;
                    mCtrlPressTime = 0;
                    //Вызываем тут облегченную версию апдейта только верха и толькоо "точки"
                    //UpdateOnScreenStatus();
                    UpdateKeyboardGesturesModeVisualization();
                } else {
                    mCtrlPressTime = now;
                }
            }
            return true;
        }
        //endregion

        //region CTRL+CVXA..etc

        if(ctrlPressed && keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT && scanCode != SCAN_CODE_SHIFT){
            int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
            if(inputConnection!=null)
                inputConnection.sendKeyEvent(new KeyEvent(
                    now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta));
            updateShiftKeyState(currentInputEditorInfo);
            return true;
        }

        //endregion

        //region Нажатие клавиши ALT
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT){

            if (handleAltOnCalling()) {
                return true;
            }

            if(showSymbolOnScreenKeyboard == true){
                showSymbolOnScreenKeyboard = false;
                altPressFirstButtonBig = false;
                altPressAllButtonBig = false;
                altShift = false;
                updateShiftKeyState(currentInputEditorInfo);
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
                updateShiftKeyState(currentInputEditorInfo);
            } else if (!altPressed && altPressAllButtonBig == true){
                altPressFirstButtonBig = false;
                altPressAllButtonBig = false;
                altShift = false;
                updateShiftKeyState(currentInputEditorInfo);
            }
            altPressed = true;
            UpdateKeyboardModeVisualization();
            return true;
        }
        //endregion

        //region Нажатие клавиши SYM
        if ( scanCode == KEY_SYM && repeatCount == 0 || (keyCode == KeyEvent.KEYCODE_3 && DEBUG)){

            if(altPressed){ //вызов меню
                menuEmulatedKeyPressed = true;
                if(inputConnection!=null)inputConnection.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
                return true;
            }

            if(navigationOnScreenKeyboardMode) {
                navigationOnScreenKeyboardMode = false;
                showSymbolOnScreenKeyboard = false;
                altPressFirstButtonBig = false;
                altPressAllButtonBig = false;
                updateShiftKeyState(currentInputEditorInfo);
            }else if(!showSymbolOnScreenKeyboard){
                showSymbolOnScreenKeyboard = true;
                altShift = true;
                altPressAllButtonBig = true;
                altPressFirstButtonBig = false;
            } else if(showSymbolOnScreenKeyboard && altShift){
                altShift = false;
            } else if(showSymbolOnScreenKeyboard && !altShift) {
                showSymbolOnScreenKeyboard = false;
                altPressFirstButtonBig = false;
                altPressAllButtonBig = false;
                updateShiftKeyState(currentInputEditorInfo);
            }

            UpdateKeyboardModeVisualization();
            return true;

        } else if ( scanCode == KEY_SYM && repeatCount >= 1 && !navigationOnScreenKeyboardMode){
            //Двойное нажание SYM -> Режим навигации
            //TODO: Вынести OnScreenKeyboardMode-ы в Enum
            navigationOnScreenKeyboardMode = true;
            fnSymbolOnScreenKeyboardMode = false;
            //TODO: BUG: Вроде как это не работает или я не понял как
            keyboardView.setFnSymbol(fnSymbolOnScreenKeyboardMode);
            UpdateKeyboardModeVisualization();
            return true;
        } else if (scanCode == KEY_SYM && repeatCount >= 1 && navigationOnScreenKeyboardMode){
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

        if(altPressFirstButtonBig || altPressAllButtonBig || altPressed) altMode = true;
        //Log.d(TAG, "onKeyDown altPressFirstButtonBig="+altPressFirstButtonBig+" altPressAllButtonBig="+altPressAllButtonBig+" altPressed="+altPressed);

        //region Нажатие клавиши SHIFT
        if (!shiftPressed && (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || (keyCode == KeyEvent.KEYCODE_2 && DEBUG))){
            if (handleShiftOnCalling()) {
                return true;
            }
            if(!altMode)
            {
                if(shiftPressAllButtonBig == false && shiftPressFirstButtonBig == false){
                    mShiftFirstPressTime = now;
                    shiftPressFirstButtonBig = true;
                } else if (shiftPressFirstButtonBig == true && mShiftFirstPressTime + 1000 > now){
                    shiftPressFirstButtonBig = false;
                    shiftPressAllButtonBig = true;
                } else if (shiftPressFirstButtonBig == true && mShiftFirstPressTime + 1000 < now){
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
            UpdateKeyboardModeVisualization();
            return true;
        }
        //TODO: Выделение с шифтом в приложении BB.Notes не работает
        if(shiftPressFirstButtonBig || shiftPressAllButtonBig || shiftPressed) shift = true;
        //endregion

        if(altMode) shift = altShift;
        //Log.d(TAG, "onKeyDown shiftPressFirstButtonBig="+shiftPressFirstButtonBig+" shiftPressAllButtonBig="+shiftPressAllButtonBig+" altShift="+altShift);

        //region Обработка KEY_0 (смена языка, зажатие, ATL+KEY_0='0'
        if(scanCode == SCAN_CODE_KEY_0 && repeatCount == 0 && altMode == false ){
            //Перенос смену языка в KeyUp чтобы не делать хак обратной смены языка для случая удержания
            //ChangeLanguage();
            //this.getWindow().dispatchGenericMotionEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis()+10,MotionEvent.ACTION_DOWN, 500f, 500f, 0));
            //SystemClock.sleep(10);
            return true;
        }else if(scanCode == SCAN_CODE_KEY_0 && repeatCount == 1 && altMode == false ){
            //TODO: BUG Работает через раз в приложениях BB, не работает в сторонних приложениях
            //TODO: Добавить навигацию внутри текстового поля включительно с выделением
            key_0_hold = true;
            mode_keyboard_gestures = true;
            //this.getWindow().dispatchGenericMotionEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis()+10,MotionEvent.ACTION_MOVE, 500f, 500f, 0));
            //SystemClock.sleep(10);
            //Хак больше не нужен
            //ChangeLanguageBack();
            //Вызываем тут облегченную версию апдейта только верха и толькоо "точки"
            //UpdateOnScreenStatus();
            UpdateKeyboardGesturesModeVisualization();
            return true;
        }else if(scanCode == SCAN_CODE_KEY_0 && repeatCount > 1 && altMode == false ){
            //SystemClock.sleep(100);
            //Пробуем костылизировать чтобы разбить спам KEY_DOWN при зажатии
            //this.getWindow().dispatchGenericMotionEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis()+10,MotionEvent.ACTION_UP, 500f, 500f, 0));
            //SystemClock.sleep(10);
            //TODO: Как-то надо операционку подтолкнуть дать воздуха для других событий через спам
            return true;
        }else if(scanCode == SCAN_CODE_KEY_0 && altMode == true ){
            if(inputConnection!=null)
                inputConnection.commitText(String.valueOf((char) CHAR_0), 1);
            return true;
        }
        //endregion

        //region Ввод символов одиночным нажатием или удержанием

        if(repeatCount == 0) { //Единичное нажание
            code = KeyToButton(scanCode, altMode, shift, false);
            //if (!altMode && code != 0 && !shiftPressFirstButtonBigDoublePress) shiftPressFirstButtonBigDoublePress = shiftPressFirstButtonBig;
        }else if(repeatCount == 1) { //удержание клавиши
            if(!pref_longpress_alt) {
                code = KeyToButton(scanCode, altMode, true, false);
            }else {
                altMode = true;
                code = KeyToButton(scanCode, altMode, false, false);
            }
            if(code != 0 && inputConnection!=null) {
                //TODO: Удаляем символ. Да. А где вводим, дальше по коду?
                //Я бы сюда сделал ExtractMethod оттуда для читаемости кода
                inputConnection.deleteSurroundingText(1, 0);
            }
        }else{ //TODO: Непонятная ситуация
            code = KeyToButton(scanCode, altMode, shift, false);
            if(code != 0) return true;
        }
        //endregion

        //region Обрабока сдвоенных букв. Подготовка СИМВОЛА на печать
        int code_double_press = 0;
        Log.d(TAG, "onKeyDown prev_key_press_btn_r0="+prev_key_press_btn_r0+" event.getScanCode() ="+ scanCode +" shiftPressFirstButtonBig="+shiftPressFirstButtonBig);
        if(prev_key_press_btn_r0 == scanCode && repeatCount == 0 &&  now < prev_key_press_time + DOUBLE_CLICK_TIME){
            if(shiftPressFirstButtonBigDoublePress) {
                code_double_press = KeyToButton(scanCode, altMode, true, true);
            } else {
                code_double_press = KeyToButton(scanCode, altMode, shift, true);
            }
            if(code != code_double_press && code_double_press != 0){
                is_double_press = true;
                if(inputConnection!=null) {
                    //TODO: Удаляем символ, а где вводим?
                    inputConnection.deleteSurroundingText(1, 0);
                }
                code = code_double_press;
            }
        } else if(prev_key_press_btn_r1 == scanCode && repeatCount == 1){
            is_double_press = true;
            code = KeyToButton(scanCode, altMode, true, true);;
        }
        //endregion

        //region Ввод СИМВОЛОВ/БУКВ/ЦИФР
        if(code != 0)
        {
            //region BB Apps HACK
            if(startInputAtBbContactsApp && !isEnglishKb){
                if(!isInputViewShown() && inputConnection!=null){
                    //Данный хак работает неплохо на первый взгляд и не выделяется виджет погоды на рабочем столе
                    keyDownUp4dpadMovements(KeyEvent.KEYCODE_0, inputConnection);
                    keyDownUp4dpadMovements(KeyEvent.KEYCODE_DEL, inputConnection);
                }
                startInputAtBbContactsApp = false;
            }
            if(startInputAtBbPhoneApp && !isEnglishKb){
                if(!isInputViewShown() && inputConnection!=null){
                    keyDownUp4dpadMovements(KeyEvent.KEYCODE_0, inputConnection);
                    keyDownUp4dpadMovements(KeyEvent.KEYCODE_DEL, inputConnection);
                }
                startInputAtBbPhoneApp = false;
            }
            //endregion

            mAltPressTime = 0;
            if(event.isAltPressed()) altPlusBtn = true;

            if(pref_alt_space == false && altPressFirstButtonBig == true) altPressFirstButtonBig = false;

            if(is_double_press || altMode == true){
                prev_key_press_btn_r1 = prev_key_press_btn_r0;
                prev_key_press_btn_r0 = 0;
                if (shiftPressFirstButtonBig == true){
                    shiftPressFirstButtonBig = false;
                    UpdateKeyboardModeVisualization();
                }
                shiftPressFirstButtonBigDoublePress = false;
            }else{
                prev_key_press_time = now;
                prev_key_press_btn_r0 = scanCode;
                prev_key_press_btn_r1 = 0;
                Log.d(TAG, "onKeyDown shiftPressFirstButtonBig="+shiftPressFirstButtonBig);
                if (shiftPressFirstButtonBig == false) shiftPressFirstButtonBigDoublePress = false;
                if (shiftPressFirstButtonBig == true){
                    shiftPressFirstButtonBigDoublePress = true;
                    shiftPressFirstButtonBig = false;
                    UpdateKeyboardModeVisualization();
                }
            }

            if(inputConnection!=null)
            {
                //inputConnection.commitText(String.valueOf((char) code), 1);
                sendKeyChar((char) code);
                press_code_true = scanCode;
            }
            else
            {
                Log.d(TAG, "onKeyDown inputConnection==null");
            }
               if (!inputViewShown && inputConnection != null) {
                if (inputConnection.getTextBeforeCursor(1, 0).length() > 0)
                    this.showWindow(true);

            }
            //это отслеживать больше не нужно. По этому закоментил
            //if(shiftAfterPoint && isAlphabet(code)) shiftAfterPoint = false;
            //if(!shiftPressAllButtonBig && (code == 46 || code == 63 ||  code == 33 || code == 191)) shiftAfterPoint = true;
            return true;
        }
        //endregion

        //region Обработка ENTER, SPACE, SHIFT+SPACE, 2xSPACE, DEL/BACKSPACE
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                keyDownUp(KeyEvent.KEYCODE_ENTER, inputConnection);
                prev_key_press_btn_r0 = 0;
                if (altPressFirstButtonBig == true){
                    altPressFirstButtonBig = false;
                    altShift = false;
                    UpdateKeyboardModeVisualization();
                }
                updateShiftKeyState(currentInputEditorInfo);
                return true;
            case KeyEvent.KEYCODE_SPACE:

                if (altPressFirstButtonBig == true){
                    altPressFirstButtonBig = false;
                    altShift = false;
                    UpdateKeyboardModeVisualization();
                }
                if(shiftPressed && repeatCount == 0){
                    ChangeLanguage();
                    updateShiftKeyState(currentInputEditorInfo);
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
                    inputConnection.commitText(String.valueOf((char) CHAR_SPACE), 1);
                Log.d(TAG, "KEYCODE_SPACE");
                updateShiftKeyState(currentInputEditorInfo);
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
                updateShiftKeyState(currentInputEditorInfo);
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

        //region отжание KEY_0
        if(scanCode == SCAN_CODE_KEY_0) {
            //long now = System.currentTimeMillis();
            if (!key_0_hold && event.getRepeatCount() == 0 && !event.isLongPress() && mode_altMode == false ){
                /* TODO: На подумать активировать режим Gestures по двойному KEY0
                //Двойное нажатие KEY_0 активирует сенсор на клавиатуре
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
            }else if (key_0_hold) {
                key_0_hold = false;
                mode_keyboard_gestures = false;
                UpdateKeyboardModeVisualization();
                return true;
            }
        }
        //endregion

        //region отжатие ALT
        if ((keyCode == KeyEvent.KEYCODE_ALT_LEFT) || (keyCode == KeyEvent.KEYCODE_1 && DEBUG)){
            if(menuEmulatedKeyPressed){ //вызов меню
                menuEmulatedKeyPressed = false;
                ic.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));
            }
            altPressed = false;
            if(altPlusBtn){
                altPressFirstButtonBig = false;
                altPressAllButtonBig = false;
                UpdateKeyboardModeVisualization();
                altPlusBtn = false;
                updateShiftKeyState(getCurrentInputEditorInfo());
            }
        }
        //endregion

        //region отжатие SHIFT LEFT, SHIFT RIGHT
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || (keyCode == KeyEvent.KEYCODE_2 && DEBUG)){
            shiftPressed = false;
        }

        //TODO: На отжатие есть, а на зажатие нет. WTF?
        if(keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT || scanCode == SCAN_CODE_SHIFT){
            int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
            long now = System.currentTimeMillis();
            ic.sendKeyEvent(new KeyEvent(
                    now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta));
            ctrlPressed = false;
            return true;
        }
        //endregion

        if(keyCode == KEY_SYM && menuEmulatedKeyPressed){ //вызов меню
            menuEmulatedKeyPressed = false;
            ic.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));
        }

        if(navigationOnScreenKeyboardMode &&
                ((scanCode == 11) ||
                        (scanCode == 5) ||
                        (scanCode >= 16 && scanCode <= 25) ||
                        (scanCode >= 30 && scanCode <= 38) ||
                        (scanCode >= 44 && scanCode <= 50)))
        {
            navigationKeyCode = getNavigationCode(scanCode);

            if(navigationKeyCode == -7) return true;
            if(ic!=null && navigationKeyCode != 0) ic.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_UP, navigationKeyCode));
            return true;
        }

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
        if(navigationOnScreenKeyboardMode) {
            switch (primaryCode) {

                case 19: //UP
                    keyDownUp(KeyEvent.KEYCODE_DPAD_UP, inputConnection);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;
                case 20: //DOWN
                    keyDownUp(KeyEvent.KEYCODE_DPAD_DOWN, inputConnection);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;

                case 21: //LEFT
                    keyDownUp(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;
                case 22: //RIGHT
                    keyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
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
                    keyDownUp(primaryCode, inputConnection);
                    break;

                case -7:  //Switch F1-F12
                    fnSymbolOnScreenKeyboardMode = !fnSymbolOnScreenKeyboardMode;
                    UpdateKeyboardModeVisualization();
                    keyboardView.setFnSymbol(fnSymbolOnScreenKeyboardMode);
                    break;

                case Keyboard.KEYCODE_DELETE:
                    inputConnection.deleteSurroundingText(1, 0);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DONE:
                    keyDownUp(KeyEvent.KEYCODE_ENTER, inputConnection);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;

                default:

            }
        }else{
            switch (primaryCode) {

                case 21: //LEFT
                    keyDownUp(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;
                case 22: //RIGHT
                    keyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DELETE:
                    inputConnection.deleteSurroundingText(1, 0);
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DONE:
                    keyDownUp(KeyEvent.KEYCODE_ENTER, inputConnection);
                    updateShiftKeyState(getCurrentInputEditorInfo());
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
            if(motionEventAction == MotionEvent.ACTION_MOVE && lastGestureX +(36- gesture_motion_sensivity) < motionEvent.getX()){
                if(this.isInputViewShown()) {
                    CharSequence c = inputConnection.getTextAfterCursor(1, 0);
                    if(c.length() > 0) {
                        keyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
                        updateShiftKeyState(getCurrentInputEditorInfo());
                    }
                    lastGestureX = motionEvent.getX();
                    Log.d(TAG, "onTouch KEYCODE_DPAD_RIGHT " + motionEvent);
                }
            }else  if(motionEventAction == MotionEvent.ACTION_MOVE && lastGestureX -(36- gesture_motion_sensivity) > motionEvent.getX()){
                if(this.isInputViewShown()) {
                    CharSequence c = inputConnection.getTextBeforeCursor(1, 0);
                    if (c.length() > 0) {
                        keyDownUp(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
                        updateShiftKeyState(getCurrentInputEditorInfo());
                    }
                    lastGestureX = motionEvent.getX();
                    Log.d(TAG, "onTouch sens_botton_bar "+ gesture_motion_sensivity +" KEYCODE_DPAD_LEFT " + motionEvent);
                }
            }
        }else{
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

    @Override
    public boolean onGenericMotionEvent(MotionEvent motionEvent) {
        if (DEBUG) Log.v(TAG, "onGenericMotionEvent(): " + motionEvent);


        if(mode_keyboard_gestures && !navigationOnScreenKeyboardMode){

            //TODO: Подумать отдельно обрабатывать жесты по горизонтали и отдельно по вертикали ориентируясь на событие ACTION_UP
            //TODO: Для выделения с зажатым нулем - подумать переходить в режим выделения-с-SHIFT-ом через 2xSHIFT

            InputConnection inputConnection = getCurrentInputConnection();
            float motionEventX = motionEvent.getX();
            float motionEventY = motionEvent.getY();

            //Не ловим движение на нижнем ряду где поблел и переключение языка
            if(motionEventY > 400) return true;

            int motionEventAction = motionEvent.getAction();
            if(!showSymbolOnScreenKeyboard){

//                if(!this.isInputViewShown()) {
//                    //Если мы не в поле ввода передаем жесты дальше
//                    return false;
//                }
                //Жесть по клавиатуре всегда начинается с ACTION_DOWN
                if(motionEventAction == MotionEvent.ACTION_DOWN) {
                    Log.d(TAG, "onGenericMotionEvent ACTION_DOWN " + motionEvent);
                    lastGestureX = motionEventX;
                    lastGestureY = motionEventY;
                }

                if(motionEventAction == MotionEvent.ACTION_MOVE || motionEventAction == MotionEvent.ACTION_UP) {
                    float deltaX = motionEventX - lastGestureX;
                    float absDeltaX = deltaX < 0 ? -1*deltaX : deltaX;
                    float deltaY = motionEventY - lastGestureY;
                    float absDeltaY = deltaY < 0 ? -1*deltaY : deltaY;

                    int motion_delta_min_x = MAGIC_KEYBOARD_GESTURE_MOTION_CONST - gesture_motion_sensivity;
                    int motion_delta_min_y = MAGIC_KEYBOARD_GESTURE_MOTION_CONST - gesture_motion_sensivity;

                    if(absDeltaX >= absDeltaY) {
                        if(absDeltaX < motion_delta_min_x)
                            return true;
                        if (deltaX > 0) {
                            if(!this.isInputViewShown()) {
                                keyDownUp4dpadMovements(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
                            }
                            else
                            {
                                CharSequence c = inputConnection.getTextAfterCursor(1, 0);
                                if(c.length() > 0) {
                                    keyDownUp4dpadMovements(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
                                    updateShiftKeyState(getCurrentInputEditorInfo());
                                }
                            }
                            Log.d(TAG, "onGenericMotionEvent KEYCODE_DPAD_RIGHT " + motionEvent);
                        } else {
                            if(!this.isInputViewShown()) {
                                keyDownUp4dpadMovements(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
                            }
                            else
                            {
                                CharSequence c = inputConnection.getTextBeforeCursor(1, 0);
                                if (c.length() > 0) {
                                    keyDownUp4dpadMovements(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
                                    updateShiftKeyState(getCurrentInputEditorInfo());
                                }
                            }
                            Log.d(TAG, "onGenericMotionEvent" + motion_delta_min_x + " KEYCODE_DPAD_LEFT " + motionEvent);
                        }
                    } else {
                        if(absDeltaY < motion_delta_min_y)
                            return true;
                        //int times = Math.round(absDeltaY / motion_delta_min_y);
                        if (deltaY < 0) {
                            if(!this.isInputViewShown()) {
                                keyDownUp4dpadMovements(KeyEvent.KEYCODE_DPAD_UP, inputConnection);
                            }else
                            {
                                //TODO: Сделать хождение по большим текстам, пока оставляем только горизонтальные движения
                                //keyDownUp2(KeyEvent.KEYCODE_DPAD_UP, inputConnection);
                            }
                            Log.d(TAG, "onGenericMotionEvent KEYCODE_DPAD_UP " + motionEvent);
                        } else {
                            if(!this.isInputViewShown()) {
                                keyDownUp4dpadMovements(KeyEvent.KEYCODE_DPAD_DOWN, inputConnection);
                            }
                            else
                            {
                                //TODO: Родная клава просто вылеает из режима Keypad, когда заползаешь за поле ввода, найти где это происходит и сделать также или как минимум взять это условие в вернуть курсор обратно
                                //keyDownUp2(KeyEvent.KEYCODE_DPAD_DOWN, inputConnection);
                            }
                            Log.d(TAG, "onGenericMotionEvent" + motion_delta_min_y + " KEYCODE_DPAD_DOWN " + motionEvent);
                        }
                    }

                    lastGestureX = motionEventX;
                    lastGestureY = motionEventY;
                }
            }else{
                if(motionEventAction == MotionEvent.ACTION_MOVE)keyboardView.coordsToIndexKey(motionEventX);
                if(motionEventAction == MotionEvent.ACTION_UP  )keyboardView.hidePopup(true);
            }
            return true;
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
            //TODO: Сделать предзагрузку (чтобы не парсить xml каждый раз в момент смены языка) или на худой конец lazy-load или кеширование
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

    private void UpdateKeyboardModeVisualization() {
        //notificationManager.cancelAll();
        Log.d(TAG, "UpdateKeyboardModeVisualization shiftPressFirstButtonBig="+shiftPressFirstButtonBig+" shiftPressAllButtonBig="+shiftPressAllButtonBig+" altPressAllButtonBig="+altPressAllButtonBig+" altPressFirstButtonBig="+altPressFirstButtonBig);
        if(navigationOnScreenKeyboardMode){
            if(!fnSymbolOnScreenKeyboardMode)
            {
                builder.setSmallIcon(R.mipmap.ic_kb_nav);
                builder.setContentTitle("Навигация");
            }
            else
            {
                builder.setSmallIcon(R.mipmap.ic_kb_nav_fn);
                builder.setContentTitle("Навигация + F1-F10");
            }

            onScreenKeyboardSymbols = new Keyboard(this, R.xml.navigation);
            keyboardView.setKeyboard(onScreenKeyboardSymbols);
            keyboardView.setNavigationLayer();

            MakeVisible();
            //updateShiftKeyState(getCurrentInputEditorInfo());
            notificationManager.notify(1, builder.build());
        }else if(showSymbolOnScreenKeyboard) {
            builder.setSmallIcon(R.mipmap.ic_kb_sym);
            builder.setContentTitle("Символы 1-9");
            onScreenKeyboardSymbols = new Keyboard(this, R.xml.symbol);
            keyboardView.setKeyboard(onScreenKeyboardSymbols);
            if(altShift) {
                keyboardView.setAltLayer(scan_code, alt_shift, alt_shift_popup);
            }else{
                keyboardView.setAltLayer(scan_code, alt, alt_popup);
            }
            MakeVisible();
            //updateShiftKeyState(getCurrentInputEditorInfo());
            //keyboardView.setAlt();
            notificationManager.notify(1, builder.build());
        }else if(altPressAllButtonBig){
            builder.setSmallIcon(R.mipmap.ic_kb_sym);
            builder.setContentTitle("Символы 1-9");

            //keyboard_symbol = new Keyboard(this, R.xml.symbol);
            keyboardView.setKeyboard(onScreenKeyboardDefaultGesturesAndLanguage);
            if(!pref_show_default_onscreen_keyboard){
                keyboardView.setVisibility(View.GONE);
            }
            if (altShift) {
                //keyboardView.setAltLayer(scan_code, alt_shift);
                keyboardView.setLang("СИМВОЛЫ {} [] | / ");
            } else {
                //keyboardView.setAltLayer(scan_code, alt);
                keyboardView.setLang("СИМВОЛЫ 1-9");
            }

            keyboardView.setAlt();
            notificationManager.notify(1, builder.build());
        }else if(altPressFirstButtonBig){
            builder.setSmallIcon(R.mipmap.ic_kb_sym_one);
            builder.setContentTitle("Символы 1-9");

            keyboardView.setKeyboard(onScreenKeyboardDefaultGesturesAndLanguage);
            if(!pref_show_default_onscreen_keyboard){
                keyboardView.setVisibility(View.GONE);
            }
            if (altShift) {
                keyboardView.setLang("Символы {} [] | / ");
            } else {
                keyboardView.setLang("Символы 1-9");
            }

            keyboardView.setAlt();
            notificationManager.notify(1, builder.build());
        }else if(shiftPressAllButtonBig){
            if(langArray[langNum] == R.xml.ukraine_hw) {
                if(mode_keyboard_gestures == false) {
                    builder.setSmallIcon(R.mipmap.ic_ukr_shift_all);
                }else{
                    builder.setSmallIcon(R.mipmap.ic_ukr_shift_all_touch);
                }
            }else if(langArray[langNum] == R.xml.russian_hw || langArray[langNum] == R.xml.pocket_russian_hw || langArray[langNum] == R.xml.russian_translit_hw) {
                if(mode_keyboard_gestures == false) {
                    builder.setSmallIcon(R.mipmap.ic_rus_shift_all);
                }else{
                    builder.setSmallIcon(R.mipmap.ic_rus_shift_all_touch);
                }
            }else{
                if(mode_keyboard_gestures == false) {
                    builder.setSmallIcon(R.mipmap.ic_eng_shift_all);
                }else{
                    builder.setSmallIcon(R.mipmap.ic_eng_shift_all_touch);
                }
            }
            builder.setContentTitle(languageOnScreenNaming);

            keyboardView.setLang(languageOnScreenNaming);
            keyboardView.setShiftAll();
            keyboardView.setKeyboard(onScreenKeyboardDefaultGesturesAndLanguage);
            keyboardView.setLetterKB();
            HideIfPrefAndVisible();

            notificationManager.notify(1, builder.build());

        }else if(shiftPressFirstButtonBig){
            if(langArray[langNum] == R.xml.ukraine_hw) {
                if(mode_keyboard_gestures == false) {
                    builder.setSmallIcon(R.mipmap.ic_ukr_shift_first);
                }else{
                    builder.setSmallIcon(R.mipmap.ic_ukr_shift_first_touch);
                }
            }else if(langArray[langNum] == R.xml.russian_hw || langArray[langNum] == R.xml.pocket_russian_hw || langArray[langNum] == R.xml.russian_translit_hw) {
                if(mode_keyboard_gestures == false) {
                    builder.setSmallIcon(R.mipmap.ic_rus_shift_first);
                }else{
                    builder.setSmallIcon(R.mipmap.ic_rus_shift_first_touch);
                }
            }else{
                if(mode_keyboard_gestures == false) {
                    builder.setSmallIcon(R.mipmap.ic_eng_shift_first);
                }else{
                    builder.setSmallIcon(R.mipmap.ic_eng_shift_first_touch);
                }
            }
            builder.setContentTitle(languageOnScreenNaming);

            keyboardView.setLang(languageOnScreenNaming);
            keyboardView.setShiftFirst();
            keyboardView.setKeyboard(onScreenKeyboardDefaultGesturesAndLanguage);
            keyboardView.setLetterKB();
            HideIfPrefAndVisible();

            notificationManager.notify(1, builder.build());
        }else{
            // Inside method: +notificationManager.notify(1, builder.build());
            UpdateKeyboardGesturesModeVisualization();
        }
    }

    private void HideIfPrefAndVisible()
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
        if(langArray[langNum] == R.xml.ukraine_hw) {
            if(mode_keyboard_gestures == false) {
                builder.setSmallIcon(R.mipmap.ic_ukr_small);
            }else{
                builder.setSmallIcon(R.mipmap.ic_ukr_small_touch);
            }
        }else if(langArray[langNum] == R.xml.russian_hw || langArray[langNum] == R.xml.pocket_russian_hw || langArray[langNum] == R.xml.russian_translit_hw) {
            if(mode_keyboard_gestures == false) {
                builder.setSmallIcon(R.mipmap.ic_rus_small);
            }else{
                builder.setSmallIcon(R.mipmap.ic_rus_small_touch);
            }
        }else{
            if(mode_keyboard_gestures == false) {
                builder.setSmallIcon(R.mipmap.ic_eng_small);
            }else{
                builder.setSmallIcon(R.mipmap.ic_eng_small_touch);
            }
        }

        //TODO: Зачем это тут?
        builder.setContentTitle(languageOnScreenNaming);

        keyboardView.notShift();
        keyboardView.setLang(languageOnScreenNaming);
        keyboardView.setKeyboard(onScreenKeyboardDefaultGesturesAndLanguage);
        keyboardView.setLetterKB();
        if(!pref_show_default_onscreen_keyboard){
            keyboardView.setVisibility(View.GONE);
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
        langNum++;
        if(langNum > langCount) langNum = 0;
        if(langNum == 0){
            isEnglishKb = true;
        }else{
            isEnglishKb = false;
        }
        LoadLayout(langArray[langNum]);
        if(pref_show_toast) {
            toast = Toast.makeText(getApplicationContext(), languageOnScreenNaming, Toast.LENGTH_SHORT);
            toast.show();
        }
        UpdateKeyboardModeVisualization();
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
        if(pref_show_toast) {
            toast = Toast.makeText(getApplicationContext(), languageOnScreenNaming, Toast.LENGTH_SHORT);
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

    //TODO: Разобраться, он вызывается постоянно зачем-то
    private void updateShiftKeyState(EditorInfo editorInfo) {
        //Если мы вывалились из зоны ввода текста
//        if(!isInputViewShown())
//            return;
        Log.d(TAG, "updateShiftKeyState editorInfo "+editorInfo.inputType);

        if (editorInfo != null && !altPressFirstButtonBig && !altPressAllButtonBig && !altPressed ) {
            int makeFirstBig = 0;
            if (editorInfo != null && editorInfo.inputType != InputType.TYPE_NULL) {
                makeFirstBig = getCurrentInputConnection().getCursorCapsMode(editorInfo.inputType);
                if(makeFirstBig != 0) Log.d(TAG, "updateShiftKeyState");
            }
            if(makeFirstBig != 0 && !shiftPressAllButtonBig){
                shiftPressFirstButtonBig = true;
                UpdateKeyboardModeVisualization();
            }else if (makeFirstBig == 0 && shiftPressAllButtonBig) {
                shiftPressFirstButtonBig = false;
                UpdateKeyboardModeVisualization();
            }

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
