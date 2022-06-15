package com.sateda.keyonekb2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.Keep;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SuggestionsInfo;
import android.widget.Toast;

import com.android.internal.telephony.ITelephony;
import com.sateda.keyonekb2.input.CallStateCallback;

import static android.content.ContentValues.TAG;
import static com.sateda.keyonekb2.SettingsActivity.REQUEST_PERMISSION_CODE;

import java.lang.reflect.Method;
import java.util.ArrayList;

@Keep
public class KeyoneIME extends GestureKeyboardBase implements KeyboardView.OnKeyboardActionListener, SpellCheckerSession.SpellCheckerSessionListener, View.OnTouchListener {

    private static final boolean DEBUG = false;

    public static final int CHAR_0 = 48;
    public String TITLE_NAV_TEXT;
    public String TITLE_NAV_FV_TEXT;
    public String TITLE_SYM_TEXT;
    public String TITLE_SYM2_TEXT;
    public String TITLE_GESTURE_INPUT;
    public String TITLE_GESTURE_INPUT_UP_DOWN;
    public String TITLE_GESTURE_VIEW;
    public String TITLE_GESTURE_OFF;
    protected boolean pref_keyboard_gestures_at_views_enable = true;

    private CallStateCallback callStateCallback;

    private final NotificationProcessor notificationProcessor = new NotificationProcessor();
    private SatedaKeyboardView keyboardView;
    private Keyboard onScreenSwipePanelAndLanguage;

    KeyboardLayoutManager keyboardLayoutManager = new KeyboardLayoutManager();

    private Boolean startInputAtBbContactsApp = false; // костыль для приложения Блекбери контакты
    private Boolean startInputAtBbPhoneApp = false; // аналогичный костыль для приложения Телефон чтобы в нем искалось на русском языке
    private Boolean inputAtBbLauncherApp = false;
    private Boolean startInputAtTelegram = false;

    private SharedPreferences mSettings;

    private boolean metaCtrlPressed = false; // только первая буква будет большая

    private boolean oneTimeShiftOneTimeBigMode; // только первая буква будет большая
    private boolean doubleShiftCapsMode; //все следующий буквы будут большие
    private boolean metaShiftPressed; //нажатие клавишь с зажатым альтом

    private boolean symPadAltShift;

    private boolean altPressSingleSymbolAltedMode;
    private boolean doubleAltPressAllSymbolsAlted;

    private boolean symbolOnScreenKeyboardMode = false;
    private boolean navigationOnScreenKeyboardMode;
    private boolean fnSymbolOnScreenKeyboardMode;

    private boolean metaSymPressed;
    private boolean metaAltPressed; //нажатие клавишь с зажатым альтом

    private String lastPackageName = "";

    //settings
    private int pref_height_bottom_bar = 10;
    private boolean pref_show_toast = false;
    private boolean pref_alt_space = true;
    private boolean pref_manage_call = false;
    private boolean pref_flag = false;
    private boolean pref_long_press_key_alt_symbol = false;
    private boolean pref_show_default_onscreen_keyboard = true;

    private boolean pref_system_icon_no_notification_text = false;

    //Предзагружаем клавиатуры, чтобы не плодить объекты
    private Keyboard keyboardNavigation;

    boolean needUpdateVisualInsideSingleEvent = false;

    TelephonyManager telephonyManager;
    TelecomManager telecomManager;

    KeyboardLayoutRes.IconRes singleAltIconRes;
    KeyboardLayoutRes.IconRes allAltIconRes;
    KeyboardLayoutRes.IconRes singleSymIconRes;
    KeyboardLayoutRes.IconRes allSymIconRes;

    KeyboardLayoutRes.IconRes navIconRes;

    KeyboardLayoutRes.IconRes navFnIconRes;

    @Override
    public void onDestroy() {
        notificationProcessor.CancelAll();
        super.onDestroy();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint({"ClickableViewAccessibility", "InflateParams"})
    @Override
    public void onCreate() {
        super.onCreate();

        TITLE_NAV_TEXT = getString(R.string.kb_state_nav_mode);
        TITLE_NAV_FV_TEXT = getString(R.string.kb_state_nav_fn_mode);
        TITLE_SYM_TEXT = getString(R.string.kb_state_alt_mode);
        TITLE_SYM2_TEXT = getString(R.string.kb_state_sym_mode);
        TITLE_GESTURE_INPUT = getString(R.string.kb_state_gesture_input);
        TITLE_GESTURE_INPUT_UP_DOWN = getString(R.string.kb_state_gesture_input_up_down);
        TITLE_GESTURE_VIEW = getString(R.string.kb_state_gesture_view);
        TITLE_GESTURE_OFF = getString(R.string.kb_state_gesture_off);

        singleAltIconRes = KeyboardLayoutRes.CreateIconRes(R.mipmap.ic_kb_alt_one, R.drawable.ic_kb_alt_one);
        allAltIconRes = KeyboardLayoutRes.CreateIconRes(R.mipmap.ic_kb_alt, R.drawable.ic_kb_alt);
        singleSymIconRes = KeyboardLayoutRes.CreateIconRes(R.mipmap.ic_kb_sym_one, R.drawable.ic_kb_sym_one);
        allSymIconRes = KeyboardLayoutRes.CreateIconRes(R.mipmap.ic_kb_sym, R.drawable.ic_kb_sym);
        navIconRes = KeyboardLayoutRes.CreateIconRes(R.mipmap.ic_kb_nav, R.drawable.ic_kb_nav);
        navFnIconRes = KeyboardLayoutRes.CreateIconRes(R.mipmap.ic_kb_nav_fn, R.drawable.ic_kb_nav_fn);

        callStateCallback = new CallStateCallback();
        telephonyManager = getTelephonyManager();
        telecomManager = getTelecomManager();

        if (telephonyManager != null) {
            telephonyManager.listen(callStateCallback, PhoneStateListener.LISTEN_CALL_STATE);
        }


        pref_height_bottom_bar = 10;

        pref_show_toast = false;
        pref_alt_space = true;
        pref_long_press_key_alt_symbol = false;

        mSettings = getSharedPreferences(SettingsActivity.APP_PREFERENCES, Context.MODE_PRIVATE);
        LoadSettingsAndKeyboards();
        LoadKeyProcessingMechanics();

        onScreenSwipePanelAndLanguage = new SatedaKeyboard(this, R.xml.space_empty, 70 + pref_height_bottom_bar * 5);

        keyboardView = (SatedaKeyboardView) getLayoutInflater().inflate(R.layout.keyboard, null);
        keyboardView.setKeyboard(onScreenSwipePanelAndLanguage);
        keyboardView.setOnKeyboardActionListener(this);
        keyboardView.setOnTouchListener(this);
        keyboardView.setPreviewEnabled(false);
        keyboardView.setService(this);
        keyboardView.clearAnimation();
        keyboardView.showFlag(pref_flag);

        keyboardNavigation = new Keyboard(this, R.xml.navigation);

        notificationProcessor.Initialize(getApplicationContext());
        UpdateGestureModeVisualization(false);
        UpdateKeyboardModeVisualization();
    }


    @Override
    public void onPress(int primaryCode) {
        Log.d(TAG, "onPress");
    }

    @Override
    public void onRelease(int primaryCode) {

    }

    @Override
    public void onFinishInput() {

        //TODO: Не смог понять нафига это нужно
        if (symbolOnScreenKeyboardMode) {
            symbolOnScreenKeyboardMode = false;
            altPressSingleSymbolAltedMode = false;
            doubleAltPressAllSymbolsAlted = false;
            mode_keyboard_gestures = false;
            mode_keyboard_gestures_plus_up_down = false;
            UpdateKeyboardModeVisualization();
            UpdateGestureModeVisualization(false);
        }

        if (lastPackageName.equals("com.sateda.keyonekb2")) LoadSettingsAndKeyboards();

        //TODO: Подумать, чтобы не надо было инициализировать свайп-клавиаутуру по настройке pref_show_default_onscreen_keyboard
        keyboardView.showFlag(pref_flag);
        if (onScreenSwipePanelAndLanguage.getHeight() != 70 + pref_height_bottom_bar * 5)
            onScreenSwipePanelAndLanguage = new SatedaKeyboard(this, R.xml.space_empty, 70 + pref_height_bottom_bar * 5);

        Log.d(TAG, "onFinishInput ");

    }

    @Override
    public void onStartInput(EditorInfo editorInfo, boolean restarting) {
        super.onStartInput(editorInfo, restarting);
        Log.d(TAG, "onStartInput package: " + editorInfo.packageName + " fieldName: "+editorInfo.fieldName+" label: " + editorInfo.label);
        //TODO: Минорно. Если надо знать какие флаги их надо расшифровывать
        Log.d(TAG, "editorInfo.inputType: "+Integer.toBinaryString(editorInfo.inputType));
        Log.d(TAG, "editorInfo.imeOptions: "+Integer.toBinaryString(editorInfo.imeOptions));
        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.

        //region HACK-s

        inputAtBbLauncherApp = editorInfo.packageName.equals("com.blackberry.blackberrylauncher");
        startInputAtBbContactsApp = editorInfo.packageName.equals("com.blackberry.contacts");
        startInputAtBbPhoneApp = editorInfo.packageName.equals("com.android.dialer");
        startInputAtTelegram = editorInfo.packageName.equals("org.telegram.messenger");
        //endregion

        // Обрабатываем переход между приложениями
        if (!editorInfo.packageName.equals(lastPackageName)) {
            lastPackageName = editorInfo.packageName;

            //Отключаем режим навигации
            navigationOnScreenKeyboardMode = false;
            fnSymbolOnScreenKeyboardMode = false;
            keyboardView.SetFnKeyboardMode(false);

            UpdateGestureModeVisualization();
        }

        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (editorInfo.inputType & InputType.TYPE_MASK_CLASS) {
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
                //int variation = editorInfo.inputType & InputType.TYPE_MASK_VARIATION;
                //if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                //        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                    //mPredictionOn = false;
                //}

                //if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                //        || variation == InputType.TYPE_TEXT_VARIATION_URI
                //        || variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                    // Our predictions are not useful for e-mail addresses
                    // or URIs.
                    //mPredictionOn = false;
                //}

                //if ((editorInfo.inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // own it displaying its own UI.
                    //mPredictionOn = false;
                    //mCompletionOn = isFullscreenMode();
                //}

                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                DetermineFirstBigCharAndReturnChangedState(editorInfo);
                break;

            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                ResetMetaState();
                DetermineFirstBigCharAndReturnChangedState(editorInfo);
        }

        UpdateGestureModeVisualization(IsInputMode());
        UpdateKeyboardModeVisualization();
        // Update the label on the enter key, depending on what the application
        // says it will do.
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        keyboardView.hidePopup(false);
        Log.v(TAG, "onKeyDown " + event);

        //region BB Launcher HACK
        //обработка главного экрана Блекбери
        //он хочет получать только родные клавиши, по этому ему отправляем почти все клавиши неизменными

        if (
                inputAtBbLauncherApp
                && !IsInputMode()
                && IsBbLauncherKeyCode(keyCode, event.getMetaState())) {
            Log.d(TAG, "Oh! this fixBbkLauncher " + inputAtBbLauncherApp);
            return super.onKeyDown(keyCode, event);
        }

        //endregion

        //region Режим "Навигационные клавиши"

        int navigationKeyCode;
        InputConnection inputConnection = getCurrentInputConnection();
        if (IsNavMode() && IsNavKeyCode(keyCode)) {
            int scanCode = event.getScanCode();
            navigationKeyCode = getNavigationCode(scanCode);

            Log.d(TAG, "navigationKeyCode " + navigationKeyCode);
            if (navigationKeyCode == -7) {
                fnSymbolOnScreenKeyboardMode = !fnSymbolOnScreenKeyboardMode;
                UpdateKeyboardModeVisualization();
                keyboardView.SetFnKeyboardMode(fnSymbolOnScreenKeyboardMode);
                return true;
            }
            if (inputConnection != null && navigationKeyCode != 0) {
                //Удаляем из meta состояния SYM т.к. он мешает некоторым приложениям воспринимать NAV символы с зажатием SYM
                int meta = event.getMetaState() & ~KeyEvent.META_SYM_ON;
                keyDownUpKeepTouch(navigationKeyCode, inputConnection, meta);
                //keyDownUpDefaultFlags(navigationKeyCode, inputConnection);
            }
            return true;
        }
        //endregion

        needUpdateVisualInsideSingleEvent = false;
        boolean processed = ProcessNewStatusModelOnKeyDown(keyCode, event);
        if (!processed)
            return false;

        //Это нужно чтобы показать клаву (перейти в режим редактирования)
        if (pref_show_default_onscreen_keyboard && !isInputViewShown() && inputConnection != null && IsInputMode()) {
            this.showWindow(true);
        }
        if (needUpdateVisualInsideSingleEvent)
            UpdateKeyboardModeVisualization();
        needUpdateVisualInsideSingleEvent = false;

        //Это нужно чтобы работал "чужой"/встроенный механизм выделения с Shift-ом
        return keyCode != KeyEvent.KEYCODE_SHIFT_LEFT;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.v(TAG, "onKeyUp " + event);

        //region Блок навигационной клавиатуры

        if (IsNavMode() && IsNavKeyCode(keyCode)) {
            return true;
        }
        //endregion

        needUpdateVisualInsideSingleEvent = false;
        boolean processed = ProcessNewStatusModelOnKeyUp(keyCode, event);
        if (!processed)
            return false;
        if (needUpdateVisualInsideSingleEvent)
            UpdateKeyboardModeVisualization();
        needUpdateVisualInsideSingleEvent = false;

        //Это нужно чтобы работал "чужой"/встроенный механизм выделения с Shift-ом
        return keyCode != KeyEvent.KEYCODE_SHIFT_LEFT;
    }

    //TODO: Вынести в XML
    public int getNavigationCode(int scanCode) {
        int keyEventCode = 0;
        switch (scanCode) {
            case 16: //Q
                keyEventCode = 111; //ESC
                break;
            case 17: //W (1)
                if (fnSymbolOnScreenKeyboardMode) {
                    keyEventCode = 131; //F1
                    break;
                }
            case 21: //Y
                keyEventCode = 122; //Home
                break;
            case 18: //E (2)
                if (fnSymbolOnScreenKeyboardMode) {
                    keyEventCode = 132; //F2
                    break;
                }
            case 22: //U
                keyEventCode = 19; //Arrow Up
                break;
            case 19: //R (3)
                if (fnSymbolOnScreenKeyboardMode) {
                    keyEventCode = 133; //F3
                    break;
                }
            case 23: //I
                keyEventCode = 123; //END
                break;
            case 20: //T
            case 24: //O
                keyEventCode = 92; //Page Up
                break;
            case 25: //P
                keyEventCode = -7; //FN
                break;

            case 30: //A
                keyEventCode = 61; //Tab
                break;
            case 31: //S
                if (fnSymbolOnScreenKeyboardMode) {
                    keyEventCode = 134; //F4
                    break;
                }
            case 35: //H
                keyEventCode = 21; //Arrow Left
                break;
            case 32: //D
                if (fnSymbolOnScreenKeyboardMode) {
                    keyEventCode = 135; //F5
                    break;
                }
            case 36: //J
                keyEventCode = 20; //Arrow Down
                break;
            case 33: //F
                if (fnSymbolOnScreenKeyboardMode) {
                    keyEventCode = 135; //F5
                    break;
                }
            case 37: //K
                keyEventCode = 22; //Arrow Right
                break;
            case 34: //G
            case 38: //L
                keyEventCode = 93; //Page down
                break;

            case 44: //Z (7)
                if (fnSymbolOnScreenKeyboardMode) keyEventCode = 137; //F7
                break;
            case 45: //X (8)
                if (fnSymbolOnScreenKeyboardMode) keyEventCode = 138; //F8
                break;
            case 46: //C (9)
                if (fnSymbolOnScreenKeyboardMode) keyEventCode = 139; //F9
                break;

            case 11: //0
                if (fnSymbolOnScreenKeyboardMode) keyEventCode = 140; //F10
                break;

            default:
        }

        return keyEventCode;
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyLongPress " + event);
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
        if (newConfig.orientation == 2) {
            if (keyboardView.getVisibility() == View.VISIBLE) {
                lastVisibility = View.VISIBLE;
                keyboardView.setVisibility(View.GONE);
            }
        } else if (newConfig.orientation == 1) {
            if (lastVisibility == View.VISIBLE) {
                keyboardView.setVisibility(View.VISIBLE);
            } else
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

        ProcessOnCursorMovement(getCurrentInputEditorInfo());

    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {

        Log.d(TAG, "onKey " + primaryCode);
        InputConnection inputConnection = getCurrentInputConnection();
        playClick(primaryCode);
        if (navigationOnScreenKeyboardMode) {
            switch (primaryCode) {

                case 19: //UP
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_UP, inputConnection);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;
                case 20: //DOWN
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_DOWN, inputConnection);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                case 21: //LEFT
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;
                case 22: //RIGHT
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
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
                    keyDownUpNoMetaKeepTouch(primaryCode, inputConnection);
                    break;

                case -7:  //Switch F1-F12
                    fnSymbolOnScreenKeyboardMode = !fnSymbolOnScreenKeyboardMode;
                    UpdateKeyboardModeVisualization();
                    keyboardView.SetFnKeyboardMode(fnSymbolOnScreenKeyboardMode);
                    break;

                case Keyboard.KEYCODE_DELETE:
                    inputConnection.deleteSurroundingText(1, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DONE:
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_ENTER, inputConnection);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                default:

            }
        } else {
            switch (primaryCode) {
                //Хак чтобы не ставился пробел после свайпа по свайп-анели
                case 0: //SPACE
                case 32: //SPACE
                    break;
                case 21: //LEFT
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;
                case 22: //RIGHT
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DELETE:
                    inputConnection.deleteSurroundingText(1, 0);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;

                case Keyboard.KEYCODE_DONE:
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_ENTER, inputConnection);
                    ProcessOnCursorMovement(getCurrentInputEditorInfo());
                    break;
                default:
                    char code = (char) primaryCode;
                    inputConnection.commitText(String.valueOf(code), 1);
            }
        }

    }

    @Override
    public void onText(CharSequence text) {
        Log.d(TAG, "onText: " + text);
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

    float lastGestureX = 0;

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        //Log.d(TAG, "onTouch "+motionEvent);
        InputConnection inputConnection = getCurrentInputConnection();
        int motionEventAction = motionEvent.getAction();
        if (!symbolOnScreenKeyboardMode) {
            if (motionEventAction == MotionEvent.ACTION_DOWN) lastGestureX = motionEvent.getX();
            if (motionEventAction == MotionEvent.ACTION_MOVE && lastGestureX + (36 - pref_gesture_motion_sensitivity) < motionEvent.getX()) {
                if (this.isInputViewShown()) {
                    MoveCursorRightSafe(inputConnection);
                    lastGestureX = motionEvent.getX();
                    Log.d(TAG, "onTouch KEYCODE_DPAD_RIGHT " + motionEvent);
                }
            } else if (motionEventAction == MotionEvent.ACTION_MOVE && lastGestureX - (36 - pref_gesture_motion_sensitivity) > motionEvent.getX()) {
                if (this.isInputViewShown()) {
                    MoveCursorLeftSafe(inputConnection);
                    lastGestureX = motionEvent.getX();
                    Log.d(TAG, "onTouch sens_bottom_bar " + pref_gesture_motion_sensitivity + " KEYCODE_DPAD_LEFT " + motionEvent);
                }
            }
        } else {
            //TODO: Разобраться что это
            if (motionEventAction == MotionEvent.ACTION_MOVE)
                keyboardView.coordsToIndexKey(motionEvent.getX());
            if (motionEventAction == MotionEvent.ACTION_UP) keyboardView.hidePopup(true);
        }
        return false;
    }

     @Override
    public boolean onGenericMotionEvent(MotionEvent motionEvent) {
        //Log.d(TAG, "onGenericMotionEvent(): " + motionEvent);

        return ProcessGestureAtMotionEvent(motionEvent);
    }



    //region VISUAL UPDATE


    @Override
    protected void UpdateGestureModeVisualization() {
        UpdateGestureModeVisualization(IsInputMode());
    }

    protected void UpdateGestureModeVisualization(boolean isInput) {
        boolean changed;

        if (isInput && mode_keyboard_gestures && !IsNoGesturesMode()) {

            if (mode_keyboard_gestures_plus_up_down) {
                changed = setSmallIcon2(R.mipmap.ic_gesture_icon_input_up_down);
                changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_INPUT_UP_DOWN);
            } else {
                changed = setSmallIcon2(R.mipmap.ic_gesture_icon_input);
                changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_INPUT);
            }
        } else if (!isInput && pref_keyboard_gestures_at_views_enable && !IsNoGesturesMode()) {
            changed = setSmallIcon2(R.mipmap.ic_gesture_icon_view);
            changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_VIEW);
        } else {
            changed = setSmallIcon2(R.mipmap.ic_gesture_icon_off);
            changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_OFF);
        }
        if (changed)
            notificationProcessor.UpdateNotificationGestureMode();
    }

    private boolean setSmallIcon2(int resId) {
        return notificationProcessor.SetSmallIconGestureMode(resId);
    }
    void UpdateKeyboardVisibilityOnPrefChange() {
        if (pref_show_default_onscreen_keyboard) {
            UpdateKeyboardModeVisualization(true);
            ShowKeyboard();
        } else {
            HideKeyboard();
        }
    }

    private void HideSwipePanelOnHidePreferenceAndVisibleState() {
        if (!pref_show_default_onscreen_keyboard) {
            HideKeyboard();
        }
    }

    private void HideKeyboard() {
        if (keyboardView.getVisibility() == View.VISIBLE) {
            keyboardView.setVisibility(View.GONE);
        }
        this.hideWindow();
    }

    private void ShowKeyboard() {
        if (keyboardView.getVisibility() != View.VISIBLE)
            keyboardView.setVisibility(View.VISIBLE);
        if (!keyboardView.isShown())
            this.showWindow(true);
    }

    private void UpdateKeyboardModeVisualization() {
        UpdateKeyboardModeVisualization(pref_show_default_onscreen_keyboard);
    }

    private void UpdateKeyboardModeVisualization(boolean updateSwipePanelData) {
        Log.d(TAG, "UpdateKeyboardModeVisualization oneTimeShiftOneTimeBigMode=" + oneTimeShiftOneTimeBigMode + " doubleShiftCapsMode=" + doubleShiftCapsMode + " doubleAltPressAllSymbolsAlted=" + doubleAltPressAllSymbolsAlted + " altPressSingleSymbolAltedMode=" + altPressSingleSymbolAltedMode);
        KeyboardLayout keyboardLayout = keyboardLayoutManager.GetCurrentKeyboardLayout();

        String languageOnScreenNaming = keyboardLayout.LanguageOnScreenNaming;
        boolean changed;
        boolean needUsefullKeyboard = false;
        if (IsNavMode()) {
            if (!fnSymbolOnScreenKeyboardMode) {
                changed = UpdateNotification(navIconRes, TITLE_NAV_TEXT);
            } else {
                changed = UpdateNotification(navFnIconRes, TITLE_NAV_FV_TEXT);
            }
            //onScreenKeyboardSymbols = keyboardNavigation;
            keyboardView.setKeyboard(keyboardNavigation);
            keyboardView.setNavigationLayer();
            needUsefullKeyboard = true;
        } else if (symbolOnScreenKeyboardMode) {

            if (IsSym2Mode()) {
                changed = UpdateNotification(allSymIconRes, TITLE_SYM2_TEXT);
            } else {
                changed = UpdateNotification(allAltIconRes, TITLE_SYM_TEXT);
            }
            //TODO: Тут плодятся объекты зачем-то
            Keyboard onScreenKeyboardSymbols = new Keyboard(this, keyboardLayoutManager.GetCurrentKeyboardLayout().SymXmlId);
            ;
            keyboardView.setKeyboard(onScreenKeyboardSymbols);
            //TODO: Сделать предзагрузку этой клавиатуры
            keyboardView.setAltLayer(keyboardLayoutManager.GetCurrentKeyboardLayout(), symPadAltShift);
            needUsefullKeyboard = true;

        } else if (doubleAltPressAllSymbolsAlted || metaAltPressed) {
            if (IsSym2Mode()) {
                changed = UpdateNotification(allSymIconRes, TITLE_SYM2_TEXT);
            } else {
                changed = UpdateNotification(allAltIconRes, TITLE_SYM_TEXT);
            }
            keyboardView.setKeyboard(onScreenSwipePanelAndLanguage);
            if (updateSwipePanelData) {
                if (IsSym2Mode()) {
                    keyboardView.setLang(TITLE_SYM2_TEXT);
                } else {
                    keyboardView.setLang(TITLE_SYM_TEXT);
                }
                keyboardView.setAlt();
            }
        } else if (altPressSingleSymbolAltedMode) {
            if (IsSym2Mode()) {
                changed = UpdateNotification(singleSymIconRes, TITLE_SYM2_TEXT);
            } else {
                changed = UpdateNotification(singleAltIconRes, TITLE_SYM_TEXT);
            }
            keyboardView.setKeyboard(onScreenSwipePanelAndLanguage);
            if (updateSwipePanelData) {
                if (IsSym2Mode()) {
                    keyboardView.setLang(TITLE_SYM2_TEXT);
                } else {
                    keyboardView.setLang(TITLE_SYM_TEXT);
                }
                keyboardView.setAlt();
            }
        } else if (doubleShiftCapsMode || metaShiftPressed) {
            changed = UpdateNotification(keyboardLayout.Resources.IconCapsResId, languageOnScreenNaming);
            keyboardView.setKeyboard(onScreenSwipePanelAndLanguage);
            if (updateSwipePanelData) {
                keyboardView.setLang(languageOnScreenNaming);
                keyboardView.setShiftAll();
                keyboardView.setLetterKB();
            }
        } else if (oneTimeShiftOneTimeBigMode) {
            changed = UpdateNotification(keyboardLayout.Resources.IconFirstShiftResId, languageOnScreenNaming);
            keyboardView.setKeyboard(onScreenSwipePanelAndLanguage);
            if (updateSwipePanelData) {
                keyboardView.setLang(languageOnScreenNaming);
                keyboardView.setShiftFirst();
                keyboardView.setLetterKB();
            }

        } else {
            // Случай со строными буквами
            changed = UpdateNotification(keyboardLayout.Resources.IconLittleResId, languageOnScreenNaming);
            keyboardView.setKeyboard(onScreenSwipePanelAndLanguage);
            if (updateSwipePanelData) {
                keyboardView.notShift();
                keyboardView.setLang(languageOnScreenNaming);
                keyboardView.setLetterKB();
            }
        }

        if (needUsefullKeyboard)
            if (IsInputMode())
                ShowKeyboard();
            else
                HideKeyboard();
        else
            HideSwipePanelOnHidePreferenceAndVisibleState();

        if (changed && !pref_system_icon_no_notification_text)
            notificationProcessor.UpdateNotificationLayoutMode();
    }

    private boolean UpdateNotification(KeyboardLayoutRes.IconRes iconRes, String notificationText) {
        if(!pref_system_icon_no_notification_text) {
            boolean changed = notificationProcessor.SetSmallIconLayout(iconRes.MipmapResId);
            changed |= notificationProcessor.SetContentTitleLayout(notificationText);
            return changed;
        }
        this.showStatusIcon(iconRes.DrawableResId);
        return true;
    }

    //endregion

    //region CALL_MANAGER

    private TelephonyManager getTelephonyManager() {
        return (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
    }

    private TelecomManager getTelecomManager() {
        return (TelecomManager) this.getSystemService(Context.TELECOM_SERVICE);
    }

    private boolean IsCalling() {
        if(callStateCallback == null) {
            return false;
        }
        return callStateCallback.isCalling();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean AcceptCallOnCalling() {
        Log.d(TAG, "handleShiftOnCalling hello");
        if(telecomManager == null) {
            Log.e(TAG2, "telecomManager == null");
            return false;
        }
        if(!IsCalling())
            return false;
        // Accept calls using SHIFT key
        if (this.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "handleShiftOnCalling callStateCallback - Calling");
            telecomManager.acceptRingingCall();
            return true;
        } else {
            Log.e(TAG2, "AcceptCallOnCalling no permission");
            return false;
        }
    }

    private boolean DeclinePhone() {
        if(telecomManager == null) {
            Log.e(TAG2, "telecomManager == null");
            return false;
        }
        if(telephonyManager == null) {
            Log.e(TAG2, "telephonyManager == null");
            return false;
        }
        if(!IsCalling())
            return false;

        if (this.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (telecomManager != null) {
                    return telecomManager.endCall();
                }
            } else {

                try {
                    Class<?> classTelephony = Class.forName(telephonyManager.getClass().getName());
                    Method methodGetITelephony = classTelephony.getDeclaredMethod("getITelephony");
                    methodGetITelephony.setAccessible(true);
                    ITelephony telephonyService = (ITelephony) methodGetITelephony.invoke(telephonyManager);
                    if (telephonyService != null) {
                        return telephonyService.endCall();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d("LOG", "Cant disconnect call");
                    return false;
                }
            }
        } else {
            Log.e(TAG2, "DeclinePhone no permission");
            return false;
        }
        return false;
    }


    //endregion



    @Override
    public void onGetSuggestions(SuggestionsInfo[] results) {
        Log.d(TAG, "onGetSuggestions");
    }

    @Override
    public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] results) {
        Log.d(TAG, "onGetSentenceSuggestions");
    }


    private boolean IsNavKeyCode(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_Q
                || keyCode == KeyEvent.KEYCODE_A
                || keyCode == KeyEvent.KEYCODE_P
                //LEFT NAV BLOCK
                || keyCode == KeyEvent.KEYCODE_W
                || keyCode == KeyEvent.KEYCODE_E
                || keyCode == KeyEvent.KEYCODE_R
                || keyCode == KeyEvent.KEYCODE_T
                || keyCode == KeyEvent.KEYCODE_S
                || keyCode == KeyEvent.KEYCODE_D
                || keyCode == KeyEvent.KEYCODE_F
                || keyCode == KeyEvent.KEYCODE_G
                //RIGHT BLOCK
                || keyCode == KeyEvent.KEYCODE_Y
                || keyCode == KeyEvent.KEYCODE_U
                || keyCode == KeyEvent.KEYCODE_I
                || keyCode == KeyEvent.KEYCODE_O
                || keyCode == KeyEvent.KEYCODE_H
                || keyCode == KeyEvent.KEYCODE_J
                || keyCode == KeyEvent.KEYCODE_K
                || keyCode == KeyEvent.KEYCODE_L;
    }

    private boolean IsBbLauncherKeyCode(int keyCode, int meta) {
        if (keyCode == KeyEvent.KEYCODE_0 && (meta & KeyEvent.META_ALT_ON) == 0) return false;
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT) return false;
        if (keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) return false;
        if(pref_manage_call) {
            if (keyCode == KeyEvent.KEYCODE_SPACE) return false;
            if (keyCode == KeyEvent.KEYCODE_SYM) return false;
        } else {
            if (keyCode == KeyEvent.KEYCODE_SPACE && IsShiftMeta(meta)) return false;
        }
        return true;
    }

    private void ChangeLanguage() {
        keyboardLayoutManager.ChangeLayout();
        if(pref_show_toast) {
            Toast toast = Toast.makeText(getApplicationContext(), keyboardLayoutManager.GetCurrentKeyboardLayout().LanguageOnScreenNaming, Toast.LENGTH_SHORT);
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


    @Override
    protected boolean ProcessOnCursorMovement(EditorInfo editorInfo)
    {
        return DetermineFirstBigCharStateAndUpdateVisualization1(editorInfo);
    }

    protected boolean DetermineFirstBigCharStateAndUpdateVisualization1(EditorInfo editorInfo)
    {
        boolean needUpdateVisual = DetermineFirstBigCharAndReturnChangedState(editorInfo);
        if(needUpdateVisual) {
            UpdateKeyboardModeVisualization();
            return true;
        }
        return false;

    }

    //TODO: Иногда вызывается по несколько раз подряд (видимо из разных мест)
    private boolean DetermineFirstBigCharAndReturnChangedState(EditorInfo editorInfo) {
        //Если мы вывалились из зоны ввода текста
        //NOTE: Проверка не дает вводить Заглавную прям на первом входе в приложение. Видимо не успевает еще активироваться.
        //if(!isInputViewShown())
        //    return;

        if(editorInfo == null)
            return false;

        if (IsAltMode()
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
                return true;
            }
        }else {
            //makeFirstBig == 0
            if(oneTimeShiftOneTimeBigMode) {
                oneTimeShiftOneTimeBigMode = false;
                Log.d(TAG, "updateShiftKeyState (changed to) oneTimeShiftOneTimeBigMode = false");
                return true;
            }
        }
        return false;
    }

    private void playClick(int i) {
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

    //region MODES/META/RESET

    private void ResetMetaState() {
        doubleAltPressAllSymbolsAlted = false;
        altPressSingleSymbolAltedMode = false;
        oneTimeShiftOneTimeBigMode = false;
        doubleShiftCapsMode = false;
        symPadAltShift = false;
        mode_keyboard_gestures = false;
        mode_keyboard_gestures_plus_up_down = false;
    }

    private boolean IsShiftMode() {
        return oneTimeShiftOneTimeBigMode || doubleShiftCapsMode || metaShiftPressed;
    }

    private boolean IsAltMode() {
        return altPressSingleSymbolAltedMode || doubleAltPressAllSymbolsAlted || metaAltPressed;
    }

    private boolean IsSym2Mode() {
        return IsAltMode() && IsShiftSym2State();
    }

    private boolean IsShiftSym2State() {
        return metaShiftPressed || (symbolOnScreenKeyboardMode && symPadAltShift);
    }

    private void ResetSingleAltSingleShiftModeAfterOneLetter() {
        if (altPressSingleSymbolAltedMode && !pref_alt_space) {
            altPressSingleSymbolAltedMode = false;
            SetNeedUpdateVisualState();
        }
        if (oneTimeShiftOneTimeBigMode) {
            oneTimeShiftOneTimeBigMode = false;
            SetNeedUpdateVisualState();
        }
    }

    //endregion

    //region LOAD

    private void LoadSettingsAndKeyboards(){

        if(mSettings.contains(SettingsActivity.APP_PREFERENCES_SHOW_DEFAULT_ONSCREEN_KEYBOARD)) {
            pref_show_default_onscreen_keyboard = mSettings.getBoolean(SettingsActivity.APP_PREFERENCES_SHOW_DEFAULT_ONSCREEN_KEYBOARD, true);
        }

        if(mSettings.contains(SettingsActivity.APP_PREFERENCES_KEYBOARD_GESTURES_AT_VIEWS_ENABLED)) {
            pref_keyboard_gestures_at_views_enable = mSettings.getBoolean(SettingsActivity.APP_PREFERENCES_KEYBOARD_GESTURES_AT_VIEWS_ENABLED, false);
        }

        if(mSettings.contains(SettingsActivity.APP_PREFERENCES_SENS_BOTTOM_BAR)) {
            pref_gesture_motion_sensitivity = mSettings.getInt(SettingsActivity.APP_PREFERENCES_SENS_BOTTOM_BAR, 1);
        }

        if(mSettings.contains(SettingsActivity.APP_PREFERENCES_SHOW_TOAST)) {
            pref_show_toast = mSettings.getBoolean(SettingsActivity.APP_PREFERENCES_SHOW_TOAST, false);
        }

        if(mSettings.contains(SettingsActivity.APP_PREFERENCES_MANAGE_CALL)) {
            pref_manage_call = mSettings.getBoolean(SettingsActivity.APP_PREFERENCES_MANAGE_CALL, false);
        }

        if(mSettings.contains(SettingsActivity.APP_PREFERENCES_ALT_SPACE)) {
            pref_alt_space = mSettings.getBoolean(SettingsActivity.APP_PREFERENCES_ALT_SPACE, true);
        }

        if(mSettings.contains(SettingsActivity.APP_PREFERENCES_LONG_PRESS_ALT)) {
            pref_long_press_key_alt_symbol = mSettings.getBoolean(SettingsActivity.APP_PREFERENCES_LONG_PRESS_ALT, false);
        }

        if(mSettings.contains(SettingsActivity.APP_PREFERENCES_FLAG)) {
            pref_flag = mSettings.getBoolean(SettingsActivity.APP_PREFERENCES_FLAG, false);
        }
        if(mSettings.contains(SettingsActivity.APP_PREFERENCES_HEIGHT_BOTTOM_BAR)) {
            pref_height_bottom_bar = mSettings.getInt(SettingsActivity.APP_PREFERENCES_HEIGHT_BOTTOM_BAR, 10);
        }

        if(mSettings.contains(SettingsActivity.APP_PREFERENCES_NOTIFICATION_ICON_SYSTEM)) {
            pref_system_icon_no_notification_text = mSettings.getBoolean(SettingsActivity.APP_PREFERENCES_NOTIFICATION_ICON_SYSTEM, false);
        }

        ArrayList<KeyboardLayoutRes> allLayouts = KeyboardLayoutManager.LoadKeyboardLayoutsRes(getResources(), getApplicationContext());
        ArrayList<KeyboardLayoutRes> activeLayouts = new ArrayList<>();
        //for each keyboard layout in active layouts find in settings and if setting is true then set keyboard layout to active
        for(KeyboardLayoutRes keyboardLayoutRes : allLayouts) {
            if(mSettings.contains(keyboardLayoutRes.getPreferenceName())
                    && mSettings.getBoolean(keyboardLayoutRes.getPreferenceName(), false)) {
                activeLayouts.add(keyboardLayoutRes);
            }
        }
        keyboardLayoutManager.Initialize(activeLayouts, getResources(), getApplicationContext());
    }

    void LoadKeyProcessingMechanics() {
        KeyProcessingMode keyAction;


        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeArray = KEY2_LATIN_ALPHABET_KEYS_CODES;
        keyAction.OnShortPress = this::onLetterShortPress;
        keyAction.OnUndoShortPress = this::DoNothing;
        keyAction.OnDoublePress = this::onLetterDoublePress;
        keyAction.OnLongPress = this::onLetterLongPress;
        keyProcessingModeList.add(keyAction);

        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeScanCode = new KeyCodeScanCode();
        keyAction.KeyCodeScanCode.KeyCode = KeyEvent.KEYCODE_ENTER;
        keyAction.OnShortPress = this::onShortPressEnter;
        keyProcessingModeList.add(keyAction);

        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeScanCode = new KeyCodeScanCode();
        keyAction.KeyCodeScanCode.KeyCode = KeyEvent.KEYCODE_DEL;
        keyAction.OnShortPress = this::onDelShortPress;
        keyProcessingModeList.add(keyAction);

        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeScanCode = new KeyCodeScanCode();
        keyAction.KeyCodeScanCode.KeyCode = KeyEvent.KEYCODE_SPACE;
        keyAction.OnShortPress = this::onSpaceShortPress;
        keyAction.OnUndoShortPress = this::DoNothing;
        keyAction.OnDoublePress = this::onSpaceDoublePress;
        keyProcessingModeList.add(keyAction);

        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeScanCode = new KeyCodeScanCode();
        keyAction.KeyCodeScanCode.KeyCode = KeyEvent.KEYCODE_SYM;
        keyAction.OnShortPress = this::onSymShortPress;
        keyAction.OnUndoShortPress = this::DoNothing;
        keyAction.OnDoublePress = this::onSymDoublePress;
        //keyAction.OnLongPress = this::onSymLongPress;
        keyAction.KeyHoldPlusKey = true;
        keyAction.OnHoldOn = this::onSymHoldOn;
        keyAction.OnHoldOff = this::onSymHoldOff;
        keyProcessingModeList.add(keyAction);

        //region KeyHoldPlusKey (ALT, SHIFT, CTRL, KEY_0)

        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeScanCode = new KeyCodeScanCode();
        keyAction.KeyCodeArray = new int[] {
                KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_ALT_RIGHT,
        };
        keyAction.KeyHoldPlusKey = true;
        keyAction.OnShortPress = this::onAltShortPress;
        keyAction.OnUndoShortPress = this::DoNothing;
        keyAction.OnDoublePress = this::onAltDoublePress;
        keyAction.OnHoldOn = this::onAltHoldOn;
        keyAction.OnHoldOff = this::onAltHoldOff;
        keyProcessingModeList.add(keyAction);

        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeScanCode = new KeyCodeScanCode();
        keyAction.KeyCodeScanCode.KeyCode = KeyEvent.KEYCODE_SHIFT_LEFT;
        keyAction.KeyHoldPlusKey = true;
        keyAction.OnShortPress = this::onShiftShortPress;
        keyAction.OnUndoShortPress = this::DoNothing;
        keyAction.OnDoublePress = this::onShiftDoublePress;
        keyAction.OnHoldOn = this::onShiftHoldOn;
        keyAction.OnHoldOff = this::onShiftHoldOff;
        keyProcessingModeList.add(keyAction);

        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeArray = new int[] {
                KeyEvent.KEYCODE_CTRL_LEFT,
                KeyEvent.KEYCODE_CTRL_RIGHT,
                KeyEvent.KEYCODE_SHIFT_RIGHT,
        };
        keyAction.KeyHoldPlusKey = true;
        keyAction.OnShortPress = this::onCtrlShortPress;
        keyAction.OnUndoShortPress = this::DoNothing;
        keyAction.OnDoublePress = this::onCtrlDoublePress;
        keyAction.OnTriplePress = this::onCtrlTriplePress;
        keyAction.OnHoldOn = this::onCtrlHoldOn;
        keyAction.OnHoldOff = this::onCtrlHoldOff;
        keyProcessingModeList.add(keyAction);

        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeScanCode = new KeyCodeScanCode();
        keyAction.KeyCodeScanCode.KeyCode = KeyEvent.KEYCODE_0;
        keyAction.KeyHoldPlusKey = true;
        keyAction.OnShortPress = this::onKey0ShortPress;
        keyAction.OnUndoShortPress = this::DoNothing;
        keyAction.OnDoublePress = this::onKey0DoublePress;
        keyAction.OnHoldOn = this::onKey0HoldOn;
        keyAction.OnHoldOff = this::onKey0HoldOff;
        keyProcessingModeList.add(keyAction);

        //endregion
    }

    //endregion

    //region ALT

    boolean onAltShortPress(KeyPressData keyPressData) {
        if(symbolOnScreenKeyboardMode) {
            symPadAltShift = !symPadAltShift;
        } else if(doubleAltPressAllSymbolsAlted){
            doubleAltPressAllSymbolsAlted = false;
            altPressSingleSymbolAltedMode = false;
        } else {
            altPressSingleSymbolAltedMode = !altPressSingleSymbolAltedMode;
        }
        SetNeedUpdateVisualState();
        return true;
    }

    boolean onAltDoublePress(KeyPressData keyPressData) {
        altPressSingleSymbolAltedMode = false;
        doubleAltPressAllSymbolsAlted = !doubleAltPressAllSymbolsAlted;
        SetNeedUpdateVisualState();
        return true;
    }

    boolean onAltHoldOn(KeyPressData keyPressData) {
        metaAltPressed = true;
        SetNeedUpdateVisualState();
        return true;
    }

    boolean onAltHoldOff(KeyPressData keyPressData) {
        metaAltPressed = false;
        SetNeedUpdateVisualState();
        return true;
    }
    //endregion

    //region SYM

    boolean onSymShortPress(KeyPressData keyPressData) {

        if(metaAltPressed) { //вызов меню
            InputConnection inputConnection = getCurrentInputConnection();
            if(inputConnection!=null)
                inputConnection.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
            return true;
        }

        if(navigationOnScreenKeyboardMode) {
            navigationOnScreenKeyboardMode = false;
            symbolOnScreenKeyboardMode = false;
            altPressSingleSymbolAltedMode = false;
            doubleAltPressAllSymbolsAlted = false;
            DetermineFirstBigCharAndReturnChangedState(getCurrentInputEditorInfo());
            UpdateGestureModeVisualization();
        }else if(!symbolOnScreenKeyboardMode){
            symbolOnScreenKeyboardMode = true;
            doubleAltPressAllSymbolsAlted = true;
            symPadAltShift = true;
            altPressSingleSymbolAltedMode = false;
        } else {
            symPadAltShift = false;
            symbolOnScreenKeyboardMode = false;
            altPressSingleSymbolAltedMode = false;
            doubleAltPressAllSymbolsAlted = false;
            //TODO: Поубирать
            DetermineFirstBigCharAndReturnChangedState(getCurrentInputEditorInfo());
        }
        //TODO: Много лишних вызовов апдейта нотификаций
        SetNeedUpdateVisualState();
        return true;

    }

    boolean onSymLongPress(KeyPressData keyPressData) {
        if (!navigationOnScreenKeyboardMode) {
            //Двойное нажание SYM -> Режим навигации
            //TODO: Вынести OnScreenKeyboardMode-ы в Enum
            navigationOnScreenKeyboardMode = true;
            fnSymbolOnScreenKeyboardMode = false;
            //TODO: Зачем это?
            keyboardView.SetFnKeyboardMode(false);
            SetNeedUpdateVisualState();
            UpdateGestureModeVisualization();
        }
        return true;
    }

    boolean onSymDoublePress(KeyPressData keyPressData) {
        if(pref_manage_call && IsCalling() && !IsInputMode()) {
            if(DeclinePhone()) return true;
        }
        if (!navigationOnScreenKeyboardMode) {
            //Двойное нажание SYM -> Режим навигации
            //TODO: Вынести OnScreenKeyboardMode-ы в Enum
            navigationOnScreenKeyboardMode = true;
            fnSymbolOnScreenKeyboardMode = false;
            //TODO: Зачем это?
            keyboardView.SetFnKeyboardMode(false);
            SetNeedUpdateVisualState();
            UpdateGestureModeVisualization();
        }
        return true;
    }

    boolean onSymHoldOn(KeyPressData keyPressData) {
        metaSymPressed = true;
        SetNeedUpdateVisualState();
        UpdateGestureModeVisualization();
        return true;
    }

    boolean onSymHoldOff(KeyPressData keyPressData) {
        metaSymPressed = false;
        SetNeedUpdateVisualState();
        UpdateGestureModeVisualization();
        return true;
    }

    //endregion

    boolean IsBitMaskContaining(int bitmask, int bits) {
        return (bitmask & bits) > 0;
    }

    private void SetNeedUpdateVisualState() {
        needUpdateVisualInsideSingleEvent = true;
    }


    private void ProcessImeOptions() {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        if(editorInfo != null && editorInfo.inputType != 0 && editorInfo.imeOptions != 0) {
            if(IsBitMaskContaining(editorInfo.imeOptions, EditorInfo.IME_MASK_ACTION)) {
                if(IsBitMaskContaining(editorInfo.imeOptions, EditorInfo.IME_ACTION_DONE)) {
                    //TODO: Доделать
                }
            }
        }
    }

    //region OTHER

    int findPrevEnter(CharSequence c) {
        if(c == null || c.length() == 0) {
            return 0;
        }
        int len = c.length();
        if(c.charAt(len - 1) == '\r' || c.charAt(len-1) == '\n') {
            len--;
        }
        for(int i = len; i > 0; i--) {
            if(c.charAt(i-1) == '\r' || c.charAt(i-1) == '\n')
                return i;
        }
        return 0;
    }

    boolean onShortPressEnter(KeyPressData keyPressData) {
        if(metaShiftPressed) {
            InputConnection inputConnection = getCurrentInputConnection();
            CharSequence c = inputConnection.getTextBeforeCursor(Integer.MAX_VALUE, 0);
            int pos = findPrevEnter(c);
            inputConnection.setSelection(pos, pos);
            //Иначе текст будет выделяться
            //inputConnection.clearMetaKeyStates(KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_ON);
            /*
            if (c.length() > 0 && c.charAt(0) != '\r' && c.charAt(0) != '\n') {
                keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_MOVE_HOME, inputConnection);
            } else {
                MoveCursorUpSafe(inputConnection);
            }*/
            return true;
        }
        TurnOffGesturesMode();
        ResetDoubleClickGestureState();
        keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_ENTER, getCurrentInputConnection());
        return true;

    }



    boolean onShortPressSendAsIs(KeyPressData keyPressData) {
        keyDownUpDefaultFlags(keyPressData.KeyCode, getCurrentInputConnection());
        return true;

    }

    boolean onDelShortPress(KeyPressData keyPressData) {
        TurnOffGesturesMode();
        ResetDoubleClickGestureState();
        InputConnection inputConnection = getCurrentInputConnection();
        if(!metaShiftPressed) {
            keyDownUpDefaultFlags(KeyEvent.KEYCODE_DEL, inputConnection);
        }else{
            if(inputConnection!=null)inputConnection.deleteSurroundingText(0,1);
        }
        return true;

    }

    boolean onUndoLastSymbol(KeyPressData keyPressData) {
        DeleteLastSymbol();
        SetNeedUpdateVisualState();
        return true;
    }

    boolean DoNothing(KeyPressData keyPressData) {
        return true;
    }

    private void DeleteLastSymbol() {
        InputConnection inputConnection = getCurrentInputConnection();
        if(inputConnection!=null) {
            inputConnection.deleteSurroundingText(1, 0);
        }
    }

    private int GetLetterBeforeCursor() {
        InputConnection inputConnection = getCurrentInputConnection();
        if(inputConnection!=null) {
            CharSequence c = inputConnection.getTextBeforeCursor(1, 0);
            if (c.length() > 0)
                return c.charAt(0);
        }
        return 0;
    }

    boolean IsShiftMeta(int meta) {
        return (meta & ( KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_ON)) > 0;
    }

    //endregion
    //region SPACE


    boolean onSpaceShortPress(KeyPressData keyPressData) {
        if(pref_manage_call && IsCalling() && !IsInputMode()) {
            return true;
        }
        if(metaShiftPressed || IsShiftMeta (keyPressData.MetaBase))
            ChangeLanguage();
        else {
            TurnOffGesturesMode();
            if(altPressSingleSymbolAltedMode && pref_alt_space) {
                altPressSingleSymbolAltedMode = false;
            }
            InputConnection inputConnection = getCurrentInputConnection();
            //inputConnection.commitText(" ", 1);
            keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_SPACE, inputConnection);
        }
        SetNeedUpdateVisualState();
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    boolean onSpaceDoublePress(KeyPressData keyPressData) {
        if(pref_manage_call && IsCalling() && !IsInputMode()) {
            if (AcceptCallOnCalling()) return true;
        }
        InputConnection inputConnection = getCurrentInputConnection();
        CharSequence back_letter = inputConnection.getTextBeforeCursor(2,0);
        Log.d(TAG2, "KEYCODE_SPACE back_letter "+back_letter);
        if(back_letter.length() == 2 && Character.isLetterOrDigit(back_letter.charAt(0)) && back_letter.charAt(1) == ' ') {
            inputConnection.deleteSurroundingText(1, 0);
            inputConnection.commitText(". ", 2);
        } else {
            keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_SPACE, inputConnection);
        }

        SetNeedUpdateVisualState();
        return true;
    }
    //endregion

    //region K2:CTRL_LEFT (K1: SHIFT_RIGHT)

    boolean onCtrlShortPress(KeyPressData keyPressData) {
        if(metaShiftPressed && keyboardView.isShown()) {
            pref_show_default_onscreen_keyboard = false;
            UpdateKeyboardVisibilityOnPrefChange();
        }else if(metaShiftPressed && !keyboardView.isShown()) {
            pref_show_default_onscreen_keyboard = true;
            UpdateKeyboardVisibilityOnPrefChange();
        }
        if(mode_keyboard_gestures && IsInputMode()) {
            mode_keyboard_gestures = false;
            mode_keyboard_gestures_plus_up_down = false;
            UpdateGestureModeVisualization(true);
        }
        return true;
    }

    boolean onCtrlDoublePress(KeyPressData keyPressData) {
        if(IsInputMode()) {
            mode_keyboard_gestures = !mode_keyboard_gestures;
        } else {
            pref_keyboard_gestures_at_views_enable = !pref_keyboard_gestures_at_views_enable;
        }
        UpdateGestureModeVisualization();
        //TODO: ???
        SetNeedUpdateVisualState();
        return true;
    }

    boolean onCtrlTriplePress(KeyPressData keyPressData) {
        if(IsInputMode()) {
            mode_keyboard_gestures = true;
            mode_keyboard_gestures_plus_up_down = true;
        }
        UpdateGestureModeVisualization();
        //TODO: ???
        SetNeedUpdateVisualState();
        return true;
    }

    boolean onCtrlHoldOn(KeyPressData keyPressData) {
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        long now = SystemClock.uptimeMillis();
        metaCtrlPressed = true;
        getCurrentInputConnection().sendKeyEvent(new KeyEvent(
                now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta));
        return true;
    }

    boolean onCtrlHoldOff(KeyPressData keyPressData) {
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        long now = SystemClock.uptimeMillis();
        metaCtrlPressed = false;
        getCurrentInputConnection().sendKeyEvent(new KeyEvent(
                now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta));
        return true;
    }

    //endregion

    //region SHIFT_LEFT

    boolean onShiftShortPress(KeyPressData keyPressData) {
        if(symbolOnScreenKeyboardMode) {
            symPadAltShift = !symPadAltShift;
        } else if(doubleShiftCapsMode) {
            doubleShiftCapsMode = false;
            DetermineFirstBigCharAndReturnChangedState(getCurrentInputEditorInfo());
        } else {
            oneTimeShiftOneTimeBigMode = !oneTimeShiftOneTimeBigMode;
        }
        SetNeedUpdateVisualState();

        return true;
    }

    boolean onShiftDoublePress(KeyPressData keyPressData) {
        oneTimeShiftOneTimeBigMode = false;
        doubleShiftCapsMode = !doubleShiftCapsMode;
        SetNeedUpdateVisualState();
        return true;
    }

    boolean onShiftHoldOn(KeyPressData keyPressData) {
        metaShiftPressed = true;
        SetNeedUpdateVisualState();
        return true;
    }

    boolean onShiftHoldOff(KeyPressData keyPressData) {
        metaShiftPressed = false;
        SetNeedUpdateVisualState();
        return true;
    }
    //endregion

    //region KEY_0
    boolean onKey0ShortPress(KeyPressData keyPressData) {
        if (!IsAltMode()) {
            ChangeLanguage();
            SetNeedUpdateVisualState();
        } else {
            TurnOffGesturesMode();
            InputConnection inputConnection = getCurrentInputConnection();
            if (inputConnection != null)
                inputConnection.commitText(String.valueOf((char) CHAR_0), 1);
            ResetSingleAltSingleShiftModeAfterOneLetter();
        }
        return true;
    }

    private boolean IsCtrlPressed(KeyPressData keyPressData) {
        return (keyPressData.MetaBase & (KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON)) > 0;
    }

    //onKey0DoublePress
    boolean onKey0DoublePress(KeyPressData keyPressData) {
        if (IsAltMode()) {
            InputConnection inputConnection = getCurrentInputConnection();
            if (inputConnection != null)
                inputConnection.commitText(String.valueOf((char) CHAR_0), 1);
            //ResetSingleAltSingleShiftModeAfterOneLetter();
        }
        return true;
    }

    boolean onKey0HoldOn(KeyPressData keyPressData) {
        if (IsAltMode()) return true;

        if (SystemClock.uptimeMillis() - lastGestureSwipingBeginTime < TIME_WAIT_GESTURE_UPON_KEY_0) {
            Log.d(TAG, "GestureMode at key_0_down first time");
            mode_keyboard_gestures = true;
            UpdateGestureModeVisualization();
        }
        return true;
    }

    boolean onKey0HoldOff(KeyPressData keyPressData) {
        if (IsAltMode()) return true;
        mode_keyboard_gestures = false;
        UpdateGestureModeVisualization();
        return true;
    }
    //endregion

    //region LETTER
    boolean onLetterShortPress(KeyPressData keyPressData) {
        if(metaCtrlPressed) {
            if(keyPressData.KeyCode == KeyEvent.KEYCODE_S) {
                //keyDownUp(KeyEvent.KEYCODE_ESCAPE, getCurrentInputConnection(), 0xFFFFFFFF, 0xFFFFFFFF);
                //Log.d(TAG2, "TEST KEY SENT");
                //Testing open Search containers
                //return true;
            }
            int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
            keyDownUpKeepTouch(keyPressData.KeyCode, getCurrentInputConnection(), meta | keyPressData.MetaBase);
            return true;
        }
        int code2send = 0;
        if(IsAltMode())
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData.ScanCode, true, IsShiftSym2State(), false);
        else
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData.ScanCode, false, IsShiftMode(), false);
        SendLetterOrSymbol(code2send);
        return true;
    }

    private void SendLetterOrSymbol(int code2send) {
        Log.v(TAG2, "KEY SEND: "+String.format("%c", code2send));
        InputConnection inputConnection = getCurrentInputConnection();
        //region BB Apps HACK
        BbContactsAppHack(inputConnection);
        BbPhoneAppHack(inputConnection);
        TelegramAppHack(inputConnection);
        //endregion
        sendKeyChar((char) code2send);
        ResetSingleAltSingleShiftModeAfterOneLetter();
        TurnOffGesturesMode();
        ResetDoubleClickGestureState();
    }

    private void TelegramAppHack(InputConnection inputConnection) {
        if(startInputAtTelegram) {
            startInputAtTelegram = false;
            if(inputConnection !=null && !IsInputMode()) {
                keyDownUpDefaultFlags(KeyEvent.KEYCODE_TAB, inputConnection);
                keyDownUpDefaultFlags(KeyEvent.KEYCODE_TAB, inputConnection);
                keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_ENTER, inputConnection);
                try {
                    Thread.sleep(50); }
                catch (Throwable ignored) {}

            }
        }
    }

    private void BbPhoneAppHack(InputConnection inputConnection) {
        if(startInputAtBbPhoneApp && !keyboardLayoutManager.isEnglishKb){
            startInputAtBbPhoneApp = false;
            if(inputConnection !=null && !IsInputMode()){
                try {
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_MINUS, inputConnection);
                    Thread.sleep(20);
                    keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DEL, inputConnection);
                    Thread.sleep(50); }
                catch (Throwable ignored) {}
            }
        }
    }

    private void BbContactsAppHack(InputConnection inputConnection) {
        if(startInputAtBbContactsApp && !keyboardLayoutManager.isEnglishKb){
            if(inputConnection !=null && !IsInputMode()){
                //Данный хак работает неплохо но если быстро вводить, то теряется первый символ
                keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_SEARCH, inputConnection);
                try {
                    Thread.sleep(50); }
                catch (Throwable ignored) {}
            }
            startInputAtBbContactsApp = false;
        }
    }

    boolean onLetterDoublePress(KeyPressData keyPressData) {
        if(metaCtrlPressed) {
            int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
            keyDownUpKeepTouch(keyPressData.KeyCode, getCurrentInputConnection(), meta | keyPressData.MetaBase);
            return true;
        }
        int code2send;

        if(IsAltMode()) {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData.ScanCode, true, IsShiftMode(), false);
            SendLetterOrSymbol(code2send);
            return true;
        }

        if(IsNotPairedLetter(keyPressData)) {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData.ScanCode, IsAltMode(), IsShiftMode(), true);
            SendLetterOrSymbol(code2send);
            return true;
        }

        boolean needShift = false;
        //Определяем была ли первая из сдвоенных букв Заглавной
        int letterShifted = keyboardLayoutManager.KeyToCharCode(keyPressData.ScanCode, false, true, false);
        int letterBeforeCursor = GetLetterBeforeCursor();
        if(letterBeforeCursor == letterShifted)
            needShift = true;


        DeleteLastSymbol();
        //DetermineFirstBigCharAndReturnChangedState(getCurrentInputEditorInfo());
        code2send = keyboardLayoutManager.KeyToCharCode(keyPressData.ScanCode, IsAltMode(), needShift, true);
        SendLetterOrSymbol(code2send);
        return true;
    }

    private boolean IsNotPairedLetter(KeyPressData keyPressData) {
        //TODO: По сути - это определение сдвоенная буква или нет, наверное можно как-то оптимальнее сделать потом
        int code2send = keyboardLayoutManager.KeyToCharCode(keyPressData.ScanCode, false, false, true);
        int code2sendNoDoublePress = keyboardLayoutManager.KeyToCharCode(keyPressData.ScanCode, false, false, false);
        return code2send == code2sendNoDoublePress;
    }

    boolean onLetterLongPress(KeyPressData keyPressData) {
        if(metaCtrlPressed) {
            return true;
        }
        int code2send;
        DeleteLastSymbol();
        if(pref_long_press_key_alt_symbol) {
            if(keyPressData.Short2ndLongPress) {
                if(IsAltMode()) {
                    DeleteLastSymbol();
                    code2send = keyboardLayoutManager.KeyToAltPopup(keyPressData.ScanCode);
                    if(code2send == 0) {
                        code2send = keyboardLayoutManager.KeyToCharCode(keyPressData.ScanCode, true, true, false);
                    }
                } else {
                    if (IsNotPairedLetter(keyPressData))
                        DeleteLastSymbol();
                    code2send = keyboardLayoutManager.KeyToCharCode(keyPressData.ScanCode, true, true, false);
                }

            } else {
                //!keyPressData.Short2ndLongPress
                if(IsAltMode()) {
                    code2send = keyboardLayoutManager.KeyToCharCode(keyPressData.ScanCode, true, true, false);
                } else {
                    code2send = keyboardLayoutManager.KeyToCharCode(keyPressData.ScanCode, true, IsShiftMode(), false);
                }
            }
            SendLetterOrSymbol(code2send);
        } else {
            if(keyPressData.Short2ndLongPress) {
                code2send = keyboardLayoutManager.KeyToCharCode(keyPressData.ScanCode, IsAltMode(), true, true);
            } else {
                code2send = keyboardLayoutManager.KeyToCharCode(keyPressData.ScanCode, IsAltMode(), true, false);
            }
            SendLetterOrSymbol(code2send);
        }
        return true;
    }



    private boolean IsNavMode() {
        return navigationOnScreenKeyboardMode || metaSymPressed;
    }

    @Override
    protected boolean IsNoGesturesMode() {
        return IsNavMode();
    }

    @Override
    protected boolean IsGestureModeEnabled() {
        return pref_keyboard_gestures_at_views_enable;
    }
    //endregion
}
