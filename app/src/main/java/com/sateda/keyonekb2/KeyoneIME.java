package com.sateda.keyonekb2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.Keep;
import android.support.annotation.RequiresApi;
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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sateda.keyonekb2.input.CallStateCallback;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

@Keep
public class KeyoneIME extends InputMethodServiceCoreGesture implements KeyboardView.OnKeyboardActionListener, SpellCheckerSession.SpellCheckerSessionListener, View.OnTouchListener {

    interface Processable {
        void Process();
    }

    public void SetSearchHack(SearchClickPlugin.SearchPluginLauncher searchPluginLaunchData) {
        if(SearchPluginLauncher == null && searchPluginLaunchData == null)
            return;
        if((IsInputMode() || isInputViewShown()) && searchPluginLaunchData != null) {
            Log.d(TAG2, "SetSearchHack IS NOT SET IsInputMode()=true");
            return;
        }
        if(SearchPluginLauncher != null && searchPluginLaunchData == null) {
            Log.d(TAG2, "SetSearchHack NULL");
        } else if(SearchPluginLauncher != null && !SearchPluginLauncher.Equals(searchPluginLaunchData)) {
            Log.d(TAG2, "SetSearchHack CHANGE");
        } else {
            Log.d(TAG2, "SetSearchHack NEW");
        }
        SearchPluginLauncher = searchPluginLaunchData;
    }

    private static final boolean DEBUG = false;

    public static KeyoneIME Instance;

    public SearchClickPlugin.SearchPluginLauncher SearchPluginLauncher;
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
    private ViewSatedaKeyboard keyboardView;
    private Keyboard onScreenSwipePanelAndLanguage;

    KeyboardLayoutManager keyboardLayoutManager = new KeyboardLayoutManager();

    private boolean metaCtrlPressed = false; // только первая буква будет большая

    private boolean oneTimeShiftOneTimeBigMode; // только первая буква будет большая
    private boolean doubleShiftCapsMode; //все следующий буквы будут большие
    private boolean metaShiftPressed; //нажатие клавишь с зажатым альтом

    private boolean symPadAltShift;

    private boolean altPressSingleSymbolAltedMode;
    private boolean doubleAltPressAllSymbolsAlted;

    private boolean symbolOnScreenKeyboardMode = false;
    private boolean keyboardStateFixed_NavModeAndKeyboard;
    private boolean fnSymbolOnScreenKeyboardMode;

    private boolean keyboardStateHolding_NavModeAndKeyboard;
    private boolean metaAltPressed; //нажатие клавишь с зажатым альтом

    private String _lastPackageName = "";

    public FixedSizeSet<String> PackageHistory = new FixedSizeSet<>(4);

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

    KeyboardLayout.KeyboardLayoutOptions.IconRes AltOneIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes AltAllIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes AltHoldIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes SymOneIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes SymAllIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes SymHoldIconRes;

    KeyboardLayout.KeyboardLayoutOptions.IconRes navIconRes;

    KeyboardLayout.KeyboardLayoutOptions.IconRes navFnIconRes;

    public class FixedSizeSet<E> extends AbstractSet<E> {
        private final LinkedHashMap<E, E> contents;

        FixedSizeSet(final int maxCapacity) {
            contents = new LinkedHashMap<E, E>(maxCapacity * 4 /3, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<E, E> eldest) {
                    return size() == maxCapacity;
                }
            };
        }

        @Override
        public Iterator<E> iterator() {
            return contents.keySet().iterator();
        }

        @Override
        public int size() {
            return contents.size();
        }

        public boolean add(E e) {
            boolean hadNull = false;
            if (e == null) {
                hadNull = contents.containsKey(null);
            }
            E previous = contents.put(e, e);
            return e == null ? hadNull : previous != null;
        }

        @Override
        public boolean contains(Object o) {
            return contents.containsKey(o);
        }
    }

    @Override
    public void onDestroy() {
        Instance = null;
        notificationProcessor.CancelAll();
        super.onDestroy();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint({"ClickableViewAccessibility", "InflateParams"})
    @Override
    public synchronized void onCreate() {
        try {
            super.onCreate();
            Instance = this;

            TITLE_NAV_TEXT = getString(R.string.notification_kb_state_nav_mode);
            TITLE_NAV_FV_TEXT = getString(R.string.notification_kb_state_nav_fn_mode);
            TITLE_SYM_TEXT = getString(R.string.notification_kb_state_alt_mode);
            TITLE_SYM2_TEXT = getString(R.string.notification_kb_state_sym_mode);
            TITLE_GESTURE_INPUT = getString(R.string.notification_kb_state_gesture_input);
            TITLE_GESTURE_INPUT_UP_DOWN = getString(R.string.notification_kb_state_gesture_input_up_down);
            TITLE_GESTURE_VIEW = getString(R.string.notification_kb_state_gesture_view);
            TITLE_GESTURE_OFF = getString(R.string.notification_kb_state_gesture_off);

            AltOneIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_alt_one, R.drawable.ic_kb_alt_one);
            AltAllIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_alt, R.drawable.ic_kb_alt_all);
            AltHoldIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_alt, R.drawable.ic_kb_alt);
            SymOneIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_sym_one, R.drawable.ic_kb_sym_one);
            SymAllIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_sym, R.drawable.ic_kb_sym_all);
            SymHoldIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_sym, R.drawable.ic_kb_sym);

            navIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_nav, R.drawable.ic_kb_nav);
            navFnIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_nav_fn, R.drawable.ic_kb_nav_fn);

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

            keyoneKb2Settings = KeyoneKb2Settings.Get(getSharedPreferences(KeyoneKb2Settings.APP_PREFERENCES, Context.MODE_PRIVATE));
            LoadSettingsAndKeyboards();
            LoadKeyProcessingMechanics();

            onScreenSwipePanelAndLanguage = new SatedaKeyboard(this, R.xml.space_empty, 70 + pref_height_bottom_bar * 5);

            keyboardView = (ViewSatedaKeyboard) getLayoutInflater().inflate(R.layout.keyboard, null);
            keyboardView.setKeyboard(onScreenSwipePanelAndLanguage);
            keyboardView.setOnKeyboardActionListener(this);
            keyboardView.setPreviewEnabled(false);
            keyboardView.setService(this);
            keyboardView.clearAnimation();
            keyboardView.showFlag(pref_flag);

            keyboardNavigation = new Keyboard(this, R.xml.navigation);

            notificationProcessor.Initialize(getApplicationContext());
            UpdateGestureModeVisualization(false);
            UpdateKeyboardModeVisualization();
        } catch(Throwable ex) {
            Log.e(TAG2, "onCreate Exception: "+ex);
            throw ex;
        }
    }


    @Override
    public void onPress(int primaryCode) {
        Log.d(TAG2, "onPress");
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

        if (_lastPackageName.equals("com.sateda.keyonekb2")) LoadSettingsAndKeyboards();

        //TODO: Подумать, чтобы не надо было инициализировать свайп-клавиаутуру по настройке pref_show_default_onscreen_keyboard
        keyboardView.showFlag(pref_flag);
        if (onScreenSwipePanelAndLanguage.getHeight() != 70 + pref_height_bottom_bar * 5)
            onScreenSwipePanelAndLanguage = new SatedaKeyboard(this, R.xml.space_empty, 70 + pref_height_bottom_bar * 5);

        Log.d(TAG2, "onFinishInput ");

    }

    @Override
    public synchronized void onStartInput(EditorInfo editorInfo, boolean restarting) {
        super.onStartInput(editorInfo, restarting);
        //TODO: Минорно. Если надо знать какие флаги их надо расшифровывать
        Log.d(TAG2, "onStartInput package: " + editorInfo.packageName
                + " editorInfo.inputType: "+Integer.toBinaryString(editorInfo.inputType)
                +" editorInfo.imeOptions: "+Integer.toBinaryString(editorInfo.imeOptions));

        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.

        if(     SearchPluginLauncher != null
                && !editorInfo.packageName.equals(SearchPluginLauncher.PackageName)) {
            SetSearchHack(null);
        }

        // Обрабатываем переход между приложениями
        if (!editorInfo.packageName.equals(_lastPackageName)) {
            PackageHistory.add(_lastPackageName);
            _lastPackageName = editorInfo.packageName;

            //Отключаем режим навигации
            keyboardStateFixed_NavModeAndKeyboard = false;
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

        //Это нужно чтобы показать клаву (перейти в режим редактирования)
        if (pref_show_default_onscreen_keyboard
                && !isInputViewShown()
                && getCurrentInputConnection() != null
                && IsInputMode()
                && Orientation == 1) {
            this.showWindow(true);
        }

        UpdateGestureModeVisualization(IsInputMode());
        UpdateKeyboardModeVisualization();
        // Update the label on the enter key, depending on what the application
        // says it will do.
    }

    public void Vibrate(int ms) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if(v == null)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            //deprecated in API 26
            v.vibrate(ms);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public synchronized boolean onKeyDown(int keyCode, KeyEvent event) {
        keyboardView.hidePopup(false);
        Log.v(TAG2, "onKeyDown " + event);

        //TODO: Hack 4 pocket
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return false;
        }

        if(
            !IsInputMode()
            && IsViewModeKeyCode(keyCode, event.getMetaState())
            && SearchPluginLauncher == null
            && !IsNavMode())  {
            Log.d(TAG2, "App transparency mode");
            return super.onKeyDown(keyCode, event);
        }

        //Vibrate(50);

        //region Режим "Навигационные клавиши"

        int navigationKeyCode;
        InputConnection inputConnection = getCurrentInputConnection();
        if (IsNavMode() && IsNavKeyCode(keyCode)) {
            int scanCode = event.getScanCode();
            navigationKeyCode = getNavigationCode(scanCode);

            Log.d(TAG2, "navigationKeyCode " + navigationKeyCode);
            if (navigationKeyCode == -7) {
                fnSymbolOnScreenKeyboardMode = !fnSymbolOnScreenKeyboardMode;
                UpdateKeyboardModeVisualization();
                keyboardView.SetFnKeyboardMode(fnSymbolOnScreenKeyboardMode);
                return true;
            }
            if (inputConnection != null && navigationKeyCode != 0) {
                //Удаляем из meta состояния SYM т.к. он мешает некоторым приложениям воспринимать NAV символы с зажатием SYM
                int meta = event.getMetaState() & ~KeyEvent.META_SYM_ON;
                keyDownUpMeta(navigationKeyCode, inputConnection, meta);
                //keyDownUpDefaultFlags(navigationKeyCode, inputConnection);
            }
            return true;
        }
        //endregion

        needUpdateVisualInsideSingleEvent = false;
        boolean processed = ProcessNewStatusModelOnKeyDown(keyCode, event);
        if (!processed)
            return false;


        if (needUpdateVisualInsideSingleEvent)
            UpdateKeyboardModeVisualization();
        needUpdateVisualInsideSingleEvent = false;

        //Это нужно чтобы работал "чужой"/встроенный механизм выделения с Shift-ом
        return keyCode != KeyEvent.KEYCODE_SHIFT_LEFT;
    }

    @Override
    public synchronized boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.v(TAG2, "onKeyUp " + event);

        //TODO: Hack 4 pocket
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return false;
        }

        //region Блок навигационной клавиатуры

        if (IsNavMode() && IsNavKeyCode(keyCode)) {
            return true;
        }
        //endregion

        if(
            !IsInputMode()
            && FindAtKeyDownList(keyCode, event.getScanCode()) == null
            && IsViewModeKeyCode(keyCode, event.getMetaState())
            && SearchPluginLauncher == null)  {

            Log.d(TAG2, "App transparency mode");
            return super.onKeyUp(keyCode, event);
        }

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
        Log.d(TAG2, "onKeyLongPress " + event);
        return false;
    }

    @SuppressLint("InflateParams")
    @Override
    public View onCreateInputView() {
        Log.d(TAG2, "onCreateInputView");
        keyboardView.setOnKeyboardActionListener(this);
        return keyboardView;
    }

    //private int lastOrientation = 0;
    private int lastVisibility = -1;

    private int Orientation = 1;

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (newConfig.orientation == 2) {
            Orientation = 2;
            if (keyboardView.getVisibility() == View.VISIBLE) {
                lastVisibility = View.VISIBLE;
                keyboardView.setVisibility(View.GONE);
            }
        } else if (newConfig.orientation == 1) {
            Orientation = 1;
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
            Log.i(TAG2, "onUpdateSelection: oss=" + oldSelStart + ", ose=" + oldSelEnd
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

        Log.d(TAG2, "onKey " + primaryCode);
        InputConnection inputConnection = getCurrentInputConnection();
        playClick(primaryCode);
        if (keyboardStateFixed_NavModeAndKeyboard) {
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
        Log.d(TAG2, "onText: " + text);
    }

    @Override
    public void swipeLeft() {
        LogKeyboardTest("swipeLeft");
    }

    @Override
    public void swipeRight() {
        LogKeyboardTest("swipeRight");
    }

    @Override
    public void swipeDown() {
        LogKeyboardTest("swipeDown");

    }

    @Override
    public void swipeUp() {
        LogKeyboardTest("swipeUp");
    }

    float lastGestureX = 0;

    void printSamples(MotionEvent ev) {
        final int historySize = ev.getHistorySize();
        final int pointerCount = ev.getPointerCount();
        for (int h = 0; h < historySize; h++) {
            LogKeyboardTest(String.format("At time %d:", ev.getHistoricalEventTime(h)));
            for (int p = 0; p < pointerCount; p++) {
                LogKeyboardTest(String.format("  pointer HISTORY %d: (%f,%f)",
                        ev.getPointerId(p), ev.getHistoricalX(p, h), ev.getHistoricalY(p, h)));
            }
        }
        LogKeyboardTest(String.format("At time %d:", ev.getEventTime()));
        for (int p = 0; p < pointerCount; p++) {
            LogKeyboardTest(String.format("  pointer %d: RAW (%f,%f)",
                    ev.getPointerId(p), ev.getRawX(), ev.getRawY()));
            MotionEvent.PointerCoords pc =  new MotionEvent.PointerCoords();
            ev.getPointerCoords(p, pc);
            LogKeyboardTest(String.format("  pointer %d: PC (%f,%f)",
                    ev.getPointerId(p), pc.x, pc.y));

            LogKeyboardTest(String.format("  pointer %d: AXIS (%f,%f)",
                    ev.getPointerId(p), ev.getAxisValue(MotionEvent.AXIS_VSCROLL), ev.getAxisValue(MotionEvent.AXIS_HSCROLL)));
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        LogKeyboardTest("onTouch: "+motionEvent.getAction());
        printSamples(motionEvent);


        //Log.d(TAG, "onTouch "+motionEvent);
        InputConnection inputConnection = getCurrentInputConnection();
        int motionEventAction = motionEvent.getAction();
        if (!symbolOnScreenKeyboardMode) {
            if (motionEventAction == MotionEvent.ACTION_DOWN) lastGestureX = motionEvent.getX();
            if (motionEventAction == MotionEvent.ACTION_MOVE && lastGestureX + (36 - pref_gesture_motion_sensitivity) < motionEvent.getX()) {
                if (this.isInputViewShown()) {
                    MoveCursorRightSafe(inputConnection);
                    lastGestureX = motionEvent.getX();
                    LogKeyboardTest(TAG2, "onTouch KEYCODE_DPAD_RIGHT " + motionEvent);
                }
            } else if (motionEventAction == MotionEvent.ACTION_MOVE && lastGestureX - (36 - pref_gesture_motion_sensitivity) > motionEvent.getX()) {
                if (this.isInputViewShown()) {
                    MoveCursorLeftSafe(inputConnection);
                    lastGestureX = motionEvent.getX();
                    LogKeyboardTest(TAG2, "onTouch KEYCODE_DPAD_LEFT " + motionEvent);
                }
            }
        } else {
            //alt-popup-keyboard
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


    public boolean IsSym2Mode() {
        return MetaIsAltMode() && IsShiftModeOrSymPadAltShiftMode();
    }

    private boolean IsShiftModeOrSymPadAltShiftMode() {
        return MetaIsShiftPressed() || MetaIsSymPadAltShiftMode();
    }

    private boolean IsShiftMeta(int meta) {
        return (meta & ( KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_ON)) > 0;
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

    @SuppressLint("ClickableViewAccessibility")
    private void HideKeyboard() {
        keyboardView.setOnTouchListener(null);
        if (keyboardView.getVisibility() == View.VISIBLE) {
            keyboardView.setVisibility(View.GONE);
        }
        this.hideWindow();

    }

    @SuppressLint("ClickableViewAccessibility")
    private void ShowKeyboard() {
        keyboardView.setOnTouchListener(this);
        if (keyboardView.getVisibility() != View.VISIBLE)
            keyboardView.setVisibility(View.VISIBLE);
        if (!keyboardView.isShown())
            this.showWindow(true);
    }

    private void UpdateKeyboardModeVisualization() {
        UpdateKeyboardModeVisualization(pref_show_default_onscreen_keyboard);
    }

    private void UpdateKeyboardModeVisualization(boolean updateSwipePanelData) {
        Log.d(TAG2, "UpdateKeyboardModeVisualization oneTimeShiftOneTimeBigMode=" + oneTimeShiftOneTimeBigMode + " doubleShiftCapsMode=" + doubleShiftCapsMode + " doubleAltPressAllSymbolsAlted=" + doubleAltPressAllSymbolsAlted + " altPressSingleSymbolAltedMode=" + altPressSingleSymbolAltedMode);
        KeyboardLayout keyboardLayout = keyboardLayoutManager.GetCurrentKeyboardLayout();

        String languageOnScreenNaming = keyboardLayout.KeyboardName;
        boolean changed;
        boolean needUsefulKeyboard = false;
        if (IsNavMode()) {
            if (!fnSymbolOnScreenKeyboardMode) {
                changed = UpdateNotification(navIconRes, TITLE_NAV_TEXT);
            } else {
                changed = UpdateNotification(navFnIconRes, TITLE_NAV_FV_TEXT);
            }
            //onScreenKeyboardSymbols = keyboardNavigation;
            keyboardView.setKeyboard(keyboardNavigation);
            keyboardView.setNavigationLayer();
            needUsefulKeyboard = true;
        } else if (symbolOnScreenKeyboardMode) {

            if (IsSym2Mode()) {
                changed = UpdateNotification(SymAllIconRes, TITLE_SYM2_TEXT);
            } else {
                changed = UpdateNotification(AltAllIconRes, TITLE_SYM_TEXT);
            }

            keyboardView.setKeyboard(keyboardLayoutManager.GetSymKeyboard(symPadAltShift));
            keyboardView.setAltLayer(keyboardLayoutManager.GetCurrentKeyboardLayout(), symPadAltShift);

            needUsefulKeyboard = true;

        } else if (doubleAltPressAllSymbolsAlted || metaAltPressed) {
            if (IsSym2Mode()) {
                if(metaAltPressed)
                    changed = UpdateNotification(SymHoldIconRes, TITLE_SYM2_TEXT);
                else
                    changed = UpdateNotification(SymAllIconRes, TITLE_SYM2_TEXT);
            } else if (metaAltPressed){
                changed = UpdateNotification(AltHoldIconRes, TITLE_SYM_TEXT);
            } else {
                changed = UpdateNotification(AltAllIconRes, TITLE_SYM2_TEXT);
            }
            UpdateKeyboardViewAltMode(updateSwipePanelData);
        } else if (altPressSingleSymbolAltedMode) {
            if (IsSym2Mode()) {
                changed = UpdateNotification(SymOneIconRes, TITLE_SYM2_TEXT);
            } else {
                changed = UpdateNotification(AltOneIconRes, TITLE_SYM_TEXT);
            }
            UpdateKeyboardViewAltMode(updateSwipePanelData);
        } else if (doubleShiftCapsMode || metaShiftPressed) {
            changed = UpdateNotification(keyboardLayout.Resources.IconCapsRes, languageOnScreenNaming);
            UpdateKeyboardViewShiftMode(updateSwipePanelData, languageOnScreenNaming);
        } else if (oneTimeShiftOneTimeBigMode) {
            changed = UpdateNotification(keyboardLayout.Resources.IconFirstShiftRes, languageOnScreenNaming);
            UpdateKeyboardViewShiftOneMode(updateSwipePanelData, languageOnScreenNaming);
        } else {
            // Случай со строными буквами
            changed = UpdateNotification(keyboardLayout.Resources.IconLittleRes, languageOnScreenNaming);
            UpdateKeyboardViewLetterMode(updateSwipePanelData, languageOnScreenNaming);
        }

        if (needUsefulKeyboard)
            if (IsInputMode())
                ShowKeyboard();
            else
                HideKeyboard();
        else
            HideSwipePanelOnHidePreferenceAndVisibleState();

        if (changed && !pref_system_icon_no_notification_text)
            notificationProcessor.UpdateNotificationLayoutMode();
    }

    private void UpdateKeyboardViewShiftOneMode(boolean updateSwipePanelData, String languageOnScreenNaming) {
        keyboardView.setKeyboard(onScreenSwipePanelAndLanguage);
        if (updateSwipePanelData) {
            keyboardView.setLang(languageOnScreenNaming);
            keyboardView.setShiftFirst();
            keyboardView.setLetterKB();
        }
    }

    private void UpdateKeyboardViewLetterMode(boolean updateSwipePanelData, String languageOnScreenNaming) {
        keyboardView.setKeyboard(onScreenSwipePanelAndLanguage);
        if (updateSwipePanelData) {
            keyboardView.notShift();
            keyboardView.setLang(languageOnScreenNaming);
            keyboardView.setLetterKB();
        }
    }

    private void UpdateKeyboardViewShiftMode(boolean updateSwipePanelData, String languageOnScreenNaming) {
        keyboardView.setKeyboard(onScreenSwipePanelAndLanguage);
        if (updateSwipePanelData) {
            keyboardView.setLang(languageOnScreenNaming);
            keyboardView.setShiftAll();
            keyboardView.setLetterKB();
        }
    }

    private void UpdateKeyboardViewAltMode(boolean updateSwipePanelData) {
        keyboardView.setKeyboard(onScreenSwipePanelAndLanguage);
        if (updateSwipePanelData) {
            if (IsSym2Mode()) {
                keyboardView.setLang(TITLE_SYM2_TEXT);
            } else {
                keyboardView.setLang(TITLE_SYM_TEXT);
            }
            keyboardView.setAlt();
        }
    }

    private boolean UpdateNotification(KeyboardLayout.KeyboardLayoutOptions.IconRes iconRes, String notificationText) {
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
        Log.d(TAG2, "handleShiftOnCalling hello");
        if(telecomManager == null) {
            Log.e(TAG2, "telecomManager == null");
            return false;
        }
        if(!IsCalling())
            return false;
        // Accept calls using SHIFT key
        if (this.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG2, "handleShiftOnCalling callStateCallback - Calling");
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
        Log.d(TAG2, "onGetSuggestions");
    }

    @Override
    public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] results) {
        Log.d(TAG2, "onGetSentenceSuggestions");
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

    private boolean IsViewModeKeyCode(int keyCode, int meta) {
        if (keyCode == KeyEvent.KEYCODE_0 && (meta & KeyEvent.META_ALT_ON) == 0) return false;
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT) return false;
        if (keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) return false;
        if (keyCode == KeyEvent.KEYCODE_FUNCTION) return false;
        if(pref_manage_call) {
            if (keyCode == KeyEvent.KEYCODE_SPACE) return false;
            if (keyCode == KeyEvent.KEYCODE_SYM) return false;
        } else {
            if (keyCode == KeyEvent.KEYCODE_SPACE && IsShiftMeta(meta)) return false;
        }
        return true;
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

        if (MetaIsAltMode()
                || doubleShiftCapsMode)
            return false;

        int makeFirstBig = 0;
        if (editorInfo.inputType != InputType.TYPE_NULL) {
            makeFirstBig = getCurrentInputConnection().getCursorCapsMode(editorInfo.inputType);
        }

        if(makeFirstBig != 0){
            if(!oneTimeShiftOneTimeBigMode) {
                oneTimeShiftOneTimeBigMode = true;
                Log.d(TAG2, "updateShiftKeyState (changed to) oneTimeShiftOneTimeBigMode = true");
                return true;
            }
        }else {
            //makeFirstBig == 0
            if(oneTimeShiftOneTimeBigMode) {
                oneTimeShiftOneTimeBigMode = false;
                Log.d(TAG2, "updateShiftKeyState (changed to) oneTimeShiftOneTimeBigMode = false");
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




    //endregion

    //region LOAD SETTINGS & KEYBOARDS
    public static ArrayList<KeyboardLayout.KeyboardLayoutOptions> allLayouts;
    KeyoneKb2Settings keyoneKb2Settings;
    private void LoadSettingsAndKeyboards(){

        pref_show_default_onscreen_keyboard = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_8_SHOW_SWIPE_PANEL);
        pref_keyboard_gestures_at_views_enable = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_9_KEYBOARD_GESTURES_AT_VIEWS_ENABLED);
        pref_gesture_motion_sensitivity = keyoneKb2Settings.GetIntValue(keyoneKb2Settings.APP_PREFERENCES_1_SENS_BOTTOM_BAR);
        pref_show_toast = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_2_SHOW_TOAST);
        pref_manage_call = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_6_MANAGE_CALL);
        pref_alt_space = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_3_ALT_SPACE);
        pref_long_press_key_alt_symbol = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_5_LONG_PRESS_ALT);
        pref_flag = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_4_FLAG);
        pref_height_bottom_bar = keyoneKb2Settings.GetIntValue(keyoneKb2Settings.APP_PREFERENCES_7_HEIGHT_BOTTOM_BAR);
        pref_system_icon_no_notification_text = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_10_NOTIFICATION_ICON_SYSTEM);

        allLayouts = KeyboardLayoutManager.LoadKeyboardLayoutsRes(getResources(), getApplicationContext());
        ArrayList<KeyboardLayout.KeyboardLayoutOptions> activeLayouts = new ArrayList<>();
        //for each keyboard layout in active layouts find in settings and if setting is true then set keyboard layout to active
        for(KeyboardLayout.KeyboardLayoutOptions keyboardLayoutOptions : allLayouts) {
            keyoneKb2Settings.CheckSettingOrSetDefault(keyboardLayoutOptions.getPreferenceName(), keyoneKb2Settings.KEYBOARD_LAYOUT_IS_ENABLED_DEFAULT);
            boolean enabled = keyoneKb2Settings.GetBooleanValue(keyboardLayoutOptions.getPreferenceName());
            if(enabled) {
                activeLayouts.add(keyboardLayoutOptions);
            }
        }
        keyboardLayoutManager.Initialize(activeLayouts, getResources(), getApplicationContext());
    }

    public static class KeyboardMechanics {

        @JsonProperty(index=10)
        public ArrayList<KeyGroupProcessor> KeyGroupProcessors = new ArrayList<>();

        public static class KeyGroupProcessor {
            @JsonProperty(index=10)
            public ArrayList<String> KeyCodes = new ArrayList<String>();
            public ArrayList<Integer> KeyCodeList = new ArrayList<Integer>();

            @JsonProperty(index=20)
            public ArrayList<Action>  OnShortPress;
            @JsonProperty(index=30)
            public ArrayList<Action>  OnDoublePress;
            @JsonProperty(index=40)
            public ArrayList<Action>  OnLongPress;
            @JsonProperty(index=50)
            public ArrayList<Action>  OnHoldOn;
            @JsonProperty(index=60)
            public ArrayList<Action>  OnHoldOff;
            @JsonProperty(index=70)
            public ArrayList<Action>  OnTriplePress;
        }

        public static class Action {

            @JsonProperty(index=10)
            public ArrayList<String> MetaModeMethodNames;
            public ArrayList<Method> MetaModeMethods;

            @JsonProperty(index=20)
            public String ActionMethodName;
            public Method ActionMethod;

            @JsonProperty(index=30)
            public boolean MethodNeedsKeyPressParameter;
            @JsonProperty(index=40)
            public boolean NeedUpdateVisualState;
            @JsonProperty(index=50)
            public boolean StopProcessingAtSuccessResult;
            @JsonProperty(index=60)
            public String CustomKeyCode;
            public int CustomKeyCodeInt;
            @JsonProperty(index=70)
            public char CustomChar;

        }
    }

    KeyoneIME.KeyboardMechanics KeyboardMechanics;

    void LoadKeyProcessingMechanics() {

        try {

            KeyboardMechanics = FileJsonUtils.DeserializeFromJson("keyboard_mechanics", new TypeReference<KeyoneIME.KeyboardMechanics>() {}, this);

            if(KeyboardMechanics == null) {
                Log.e(TAG2, "CAN NOT LOAD KEYBOARD MECHANICS JSON FILE");
                return;
            }

            for (KeyoneIME.KeyboardMechanics.KeyGroupProcessor kgp : KeyboardMechanics.KeyGroupProcessors) {

                KeyProcessingMode keyAction;
                keyAction = new KeyProcessingMode();
                keyProcessingModeList.add(keyAction);

                kgp.KeyCodeList = new ArrayList<>();
                for (String keyCodeCode : kgp.KeyCodes) {

                    Field f = KeyEvent.class.getField(keyCodeCode);
                    kgp.KeyCodeList.add(f.getInt(null));

                }

                keyAction.KeyCodeArray = Arrays.stream(kgp.KeyCodeList.toArray(new Integer[0])).mapToInt(Integer::intValue).toArray();

                keyAction.OnShortPress = ProcessReflectionMappingAndCreateProcessable(kgp.OnShortPress);
                keyAction.OnUndoShortPress = this::DoNothingAndMakeUndoAtSubsequentKeyAction;
                keyAction.OnDoublePress = ProcessReflectionMappingAndCreateProcessable(kgp.OnDoublePress);
                keyAction.OnLongPress = ProcessReflectionMappingAndCreateProcessable(kgp.OnLongPress);
                keyAction.OnHoldOn = ProcessReflectionMappingAndCreateProcessable(kgp.OnHoldOn);
                keyAction.OnHoldOff = ProcessReflectionMappingAndCreateProcessable(kgp.OnHoldOff);
                keyAction.OnTriplePress = ProcessReflectionMappingAndCreateProcessable(kgp.OnTriplePress);
            }

        } catch(Throwable ex) {
            Log.e(TAG2, "CAN NOT LOAD KEYBOARD MECHANICS: "+ex);
        }

        //OldAndStableStaticMechanics();

        //endregion
    }

    private InputMethodServiceCoreKeyPress.Processable ProcessReflectionMappingAndCreateProcessable(ArrayList<KeyoneIME.KeyboardMechanics.Action> list) throws NoSuchMethodException, NoSuchFieldException, IllegalAccessException {
        if(list == null || list.isEmpty()) {
            return null;
        }

        for (KeyoneIME.KeyboardMechanics.Action action : list) {
            Method method;
            if(action.CustomKeyCode != null && !action.CustomKeyCode.isEmpty()) {
                Field f = KeyEvent.class.getField(action.CustomKeyCode);
                action.CustomKeyCodeInt = f.getInt(null);
                method = KeyoneIME.class.getDeclaredMethod(action.ActionMethodName, int.class);
            } else if(action.CustomChar > 0) {
                method = KeyoneIME.class.getDeclaredMethod(action.ActionMethodName, char.class);
            } else if(action.MethodNeedsKeyPressParameter) {
                method = KeyoneIME.class.getDeclaredMethod(action.ActionMethodName, KeyPressData.class);
            } else {
                method = KeyoneIME.class.getDeclaredMethod(action.ActionMethodName);
            }
            action.ActionMethod = method;




            if(action.MetaModeMethodNames != null && !action.MetaModeMethodNames.isEmpty()) {
                action.MetaModeMethods = new ArrayList<>();
                for (String metaMethodName : action.MetaModeMethodNames) {

                    Method metaMethod = KeyoneIME.class.getDeclaredMethod(metaMethodName);
                    action.MetaModeMethods.add(metaMethod);
                }
            }
        }

        Processable2 p = new Processable2();
        p.Actions = list;
        p.Keyboard = this;
        return p;
    }


    private void OldAndStableStaticMechanics() {
        KeyProcessingMode keyAction;


        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeArray = KEY2_LATIN_ALPHABET_KEYS_CODES;
        keyAction.OnShortPress = this::onLetterShortPress;
        keyAction.OnUndoShortPress = this::DoNothingAndMakeUndoAtSubsequentKeyAction;
        keyAction.OnDoublePress = this::onLetterDoublePress;
        keyAction.OnLongPress = this::onLetterLongPress;
        keyProcessingModeList.add(keyAction);

        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeScanCode.KeyCode = KeyEvent.KEYCODE_ENTER;
        keyAction.OnShortPress = this::onShortPressEnter;
        keyProcessingModeList.add(keyAction);

        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeScanCode.KeyCode = KeyEvent.KEYCODE_DEL;
        keyAction.OnShortPress = this::onDelShortPress;
        keyProcessingModeList.add(keyAction);

        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeScanCode.KeyCode = KeyEvent.KEYCODE_SPACE;
        keyAction.OnShortPress = this::onSpaceShortPress;
        keyAction.OnUndoShortPress = this::DoNothingAndMakeUndoAtSubsequentKeyAction;
        keyAction.OnDoublePress = this::onSpaceDoublePress;
        keyProcessingModeList.add(keyAction);

        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeScanCode.KeyCode = KeyEvent.KEYCODE_SYM;
        keyAction.OnShortPress = this::onSymShortPress;
        keyAction.OnUndoShortPress = this::DoNothingAndMakeUndoAtSubsequentKeyAction;
        keyAction.OnDoublePress = this::onSymDoublePress;
        keyAction.OnHoldOn = this::onSymHoldOn;
        keyAction.OnHoldOff = this::onSymHoldOff;
        keyProcessingModeList.add(keyAction);

        //region KeyHoldPlusKey (ALT, SHIFT, CTRL, KEY_0)

        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeArray = new int[] {
                KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_ALT_RIGHT,
        };
        keyAction.OnShortPress = this::onAltShortPress;
        keyAction.OnUndoShortPress = this::DoNothingAndMakeUndoAtSubsequentKeyAction;
        keyAction.OnDoublePress = this::onAltDoublePress;
        keyAction.OnHoldOn = this::onAltHoldOn;
        keyAction.OnHoldOff = this::onAltHoldOff;
        keyProcessingModeList.add(keyAction);

        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeScanCode.KeyCode = KeyEvent.KEYCODE_SHIFT_LEFT;
        keyAction.OnShortPress = this::onShiftShortPress;
        keyAction.OnUndoShortPress = this::DoNothingAndMakeUndoAtSubsequentKeyAction;
        keyAction.OnDoublePress = this::onShiftDoublePress;
        keyAction.OnHoldOn = this::onShiftHoldOn;
        keyAction.OnHoldOff = this::onShiftHoldOff;
        keyProcessingModeList.add(keyAction);

        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeArray = new int[] {
                KeyEvent.KEYCODE_CTRL_LEFT,
                KeyEvent.KEYCODE_CTRL_RIGHT,
                KeyEvent.KEYCODE_SHIFT_RIGHT,
                KeyEvent.KEYCODE_FUNCTION
        };
        keyAction.OnShortPress = this::onCtrlShortPress;
        keyAction.OnUndoShortPress = this::DoNothingAndMakeUndoAtSubsequentKeyAction;
        keyAction.OnDoublePress = this::onCtrlDoublePress;
        keyAction.OnTriplePress = this::onCtrlTriplePress;
        keyAction.OnHoldOn = this::onCtrlHoldOn;
        keyAction.OnHoldOff = this::onCtrlHoldOff;
        keyProcessingModeList.add(keyAction);

        keyAction = new KeyProcessingMode();
        keyAction.KeyCodeScanCode.KeyCode = KeyEvent.KEYCODE_0;
        keyAction.OnShortPress = this::onKey0ShortPress;
        keyAction.OnUndoShortPress = this::DoNothingAndMakeUndoAtSubsequentKeyAction;
        keyAction.OnDoublePress = this::onKey0DoublePress;
        keyAction.OnHoldOn = this::onKey0HoldOn;
        keyAction.OnHoldOff = this::onKey0HoldOff;
        keyProcessingModeList.add(keyAction);
    }

    //endregion

    boolean IsBitMaskContaining(int bitmask, int bits) {
        return (bitmask & bits) > 0;
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

    private boolean IsCtrlPressed(KeyPressData keyPressData) {
        return (keyPressData.MetaBase & (KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON)) > 0;
    }

    int findPrevEnterAbsPos(CharSequence c) {
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

    int findPrevEnterDistance(CharSequence c) {
        if(c == null || c.length() == 0) {
            return 0;
        }
        int len = c.length();
        for(int i = len; i > 0; i--) {
            if(c.charAt(i - 1) == '\r' || c.charAt(i - 1) == '\n')
                return len - i + 1;
        }
        return 0;
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



    //region ALT OK OK

    boolean onAltShortPress(KeyPressData keyPressData) {
        if(ActionTryChangeSymPadLayout()) return true;
        if (ActionTryDisableFixedAltModeState()) return true;
        return ActionChangeFirstSymbolAltMode();
    }



    boolean onAltDoublePress(KeyPressData keyPressData) {
        return ActionChangeFixedAltModeState();
    }


    boolean onAltHoldOn(KeyPressData keyPressData) {
        return ActionEnableHoldAltMode();
    }


    boolean onAltHoldOff(KeyPressData keyPressData) {
        return ActionDisableHoldAltMode();
    }



    //endregion

    //region SYM OK OK

    boolean onSymShortPress(KeyPressData keyPressData) {

        if(MetaIsAltPressed()) { //вызов меню
            return ActionKeyDown(KeyEvent.KEYCODE_MENU);
        }

        if (ActionTryDisableNavModeAndKeyboard()) return true;
        ActionTryChangeSymPadVisibilityAtInputMode();
        return true;
    }





    boolean onSymDoublePress(KeyPressData keyPressData) {
        if (ActionTryDeclineCall()) return true;
        ActionTryEnableNavModeAndKeyboard();
        return true;
    }

    boolean onSymHoldOn(KeyPressData keyPressData) {
        return SetNavModeHoldOnState();
    }

    boolean onSymHoldOff(KeyPressData keyPressData) {
        return SetNavModeHoldOffState();
    }

    //endregion

    //region OTHER OK OK

    boolean onShortPressEnter(KeyPressData keyPressData) {
        if(MetaIsShiftPressed()) {
            ActionUnCrLf();
            return true;
        }
        ActionTryTurnOffGesturesMode();
        ActionResetDoubleClickGestureState();
        ActionCustomKeyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_ENTER);
        return true;

    }




    boolean onDelShortPress(KeyPressData keyPressData) {


        if(MetaIsAltPressed()) {
            ActionDeleteUntilPrevCrLf();
            return true;
        }
        if(MetaIsShiftPressed()) {
            ActionKeyDownUpDefaultFlags(KeyEvent.KEYCODE_FORWARD_DEL);
            return true;
        }

        ActionTryTurnOffGesturesMode();
        ActionResetDoubleClickGestureState();
        ActionKeyDownUpDefaultFlags(KeyEvent.KEYCODE_DEL);
        return true;

    }





    //endregion

    //region SPACE OK OK

    boolean onSpaceShortPress(KeyPressData keyPressData) {

        if(MetaIsShiftPressed()) {
            return ActionChangeKeyboardLayout();
        }
        if(IsShiftMeta(keyPressData.MetaBase)) {
            return ActionChangeKeyboardLayout();
        }

        if (MetaStateIsOnCall()) return ActionDoNothing();
        ActionTryTurnOffGesturesMode();
        ActionTryDisableAltModeUponSpace();
        ActionKeyDownUpDefaultFlags(KeyEvent.KEYCODE_SPACE);
        ActionSetNeedUpdateVisualState(); //ok
        return true;
    }




    @RequiresApi(api = Build.VERSION_CODES.O)
    boolean onSpaceDoublePress(KeyPressData keyPressData) {
        if (ActionTryAcceptCall()) return true;
        if (ActionTryDoubleSpaceDotSpaceConversion()) return true;
        ActionKeyDownUpDefaultFlags(KeyEvent.KEYCODE_SPACE);
        ActionSetNeedUpdateVisualState();//ok
        return true;
    }

    //endregion

    //region K2:CTRL_LEFT (K1: SHIFT_RIGHT) OK OK

    boolean onCtrlShortPress(KeyPressData keyPressData) {
        if(MetaIsShiftPressed()) {
            return ActionChangeSwipePanelVisibility();
        }
        ActionDisableAndResetGesturesAtInputMode();
        return true;
    }




    boolean onCtrlDoublePress(KeyPressData keyPressData) {
        ActionChangeGestureModeState();
        return true;
    }



    boolean onCtrlTriplePress(KeyPressData keyPressData) {
        ActionEnableGestureAtInputModeAndUpDownMode();
        return true;
    }



    boolean onCtrlHoldOn(KeyPressData keyPressData) {
        return ActionEnableHoldCtrlMode(keyPressData);
    }



    boolean onCtrlHoldOff(KeyPressData keyPressData) {
        return ActionDisableHoldCtrlMode(keyPressData);
    }



    //endregion

    //region SHIFT_LEFT OK OK

    boolean onShiftShortPress(KeyPressData keyPressData) {
        if (ActionTryChangeSymPadLayout()) return true;

        if (ActionTryDisableCapslockShiftMode()) return true;

        return ActionChangeFirstLetterShiftMode();
    }

    boolean onShiftDoublePress(KeyPressData keyPressData) {
        return ActionChangeShiftCapslockState();
    }



    boolean onShiftHoldOn(KeyPressData keyPressData) {
        return ActionEnableHoldShiftMode();
    }



    boolean onShiftHoldOff(KeyPressData keyPressData) {
        return ActionDisableHoldShiftMode();
    }

    //endregion

    //region KEY_0 OK OK
    boolean onKey0ShortPress(KeyPressData keyPressData) {
        if(MetaIsAltMode()) {
            ActionTryTurnOffGesturesMode();
            ActionSendCharToInput((char) CHAR_0);
            ActionTryDisableFirstSymbolAltMode();
            ActionTryDisableFirstLetterShiftMode();
            return true;
        }

        ActionChangeKeyboardLayout();
        return true;
    }

    //onKey0DoublePress
    boolean onKey0DoublePress(KeyPressData keyPressData) {
        if (MetaIsAltMode()) {
            ActionSendCharToInput((char) CHAR_0);
            //ResetSingleAltSingleShiftModeAfterOneLetter();
        }
        return true;
    }

    boolean onKey0HoldOn(KeyPressData keyPressData) {
        if (MetaIsAltMode()) return ActionDoNothing();
        ActionTryEnableGestureAtInputOnHoldState();
        return true;
    }

    boolean onKey0HoldOff(KeyPressData keyPressData) {
        if (MetaIsAltMode()) return ActionDoNothing();
        ActionDisableGestureMode();
        return true;
    }


    //endregion

    //region LETTER OK OK OK
    boolean onLetterShortPress(KeyPressData keyPressData) {
        if(MetaIsCtrlPressed()) {
            return ActionSendCtrlPlusKey(keyPressData);
        }

        if(MetaIsAltMode()) {
            return DeprecatedActionSendCharSinglePressAltOrSymMode(keyPressData);
        }

        if(MetaIsShiftMode()) {
            return ActionSendCharSinglePressShiftMode(keyPressData);
        }

        return ActionSendCharSinglePressNoMeta(keyPressData);

    }

    private void SendLetterOrSymbol(int code2send) {
        Log.v(TAG2, "KEY SEND: "+String.format("%c", code2send));
        SearchInputActivateOnLetterHack();
        sendKeyChar((char) code2send);
        ActionTryDisableFirstSymbolAltMode();
        ActionTryDisableFirstLetterShiftMode();
        ActionTryTurnOffGesturesMode();
        ActionResetDoubleClickGestureState();
    }

    private void SearchInputActivateOnLetterHack() {
        if(IsInputMode() && isInputViewShown() && SearchPluginLauncher != null) {
            Log.d(TAG2, "NO_FIRE SearchPluginAction INPUT_MODE");
            SetSearchHack(null);
        }
        if(SearchPluginLauncher != null) {
            Log.d(TAG2, "FIRE SearchPluginAction!");
            SearchPluginLauncher.FirePluginAction();
            SetSearchHack(null);
        }
    }

    boolean onLetterDoublePress(KeyPressData keyPressData) {
        if(MetaIsCtrlPressed()) {
            return ActionSendCtrlPlusKey(keyPressData);
        }
        int code2send;

        //TODO: Проверить: Двойное нажатие ALT->SYM символ т.е. инвертировать SHIFT
        if(MetaIsAltMode()) {
            if(MetaIsShiftMode()) {
                return ActionSendCharSinglePressSymMode(keyPressData);
            }
            return ActionSendCharSinglePressAltMode(keyPressData);
        }



        return ActionSendCharDoublePressNoMeta(keyPressData);
    }



    private boolean IsNotPairedLetter(KeyPressData keyPressData) {
        //TODO: По сути - это определение сдвоенная буква или нет, наверное можно как-то оптимальнее сделать потом
        int code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, false, true);
        int code2sendNoDoublePress = keyboardLayoutManager.KeyToCharCode(keyPressData, false, false, false);
        return code2send == code2sendNoDoublePress;
    }

    boolean onLetterLongPress(KeyPressData keyPressData) {
        if(MetaIsCtrlPressed()) {
            return ActionDoNothing();
        }
        if(PrefLongPressAltSymbol() && MetaIsAltMode()) {
            ActionSendCharLongPressAltSymbolAltMode(keyPressData);
        } else if(PrefLongPressAltSymbol() ) {
            ActionSendCharLongPressAltSymbolNoMeta(keyPressData);

        } else if (!PrefLongPressAltSymbol()){
            if(MetaIsAltMode()) {
                ActionSendCharLongPressCapitalizeAltMode(keyPressData);
            } else {
                ActionSendCharLongPressCapitalize(keyPressData);
            }

        }
        return true;
    }





    private boolean IsNavMode() {
        return keyboardStateFixed_NavModeAndKeyboard || keyboardStateHolding_NavModeAndKeyboard;
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

    //region Processable2

    class Processable2 implements InputMethodServiceCoreKeyPress.Processable {
        @Override
        public boolean Process(KeyPressData keyPressData) {
            if(Actions == null || Actions.isEmpty())
                return true;
            try {
                for (KeyoneIME.KeyboardMechanics.Action action : Actions) {

                    if (action.MetaModeMethods != null && !action.MetaModeMethods.isEmpty()) {
                        boolean metaResult = true;
                        for (Method metaMethod : action.MetaModeMethods) {
                            metaResult &= (Boolean) metaMethod.invoke(Keyboard);
                        }

                        if (metaResult) {
                            boolean result = InvokeMethod(keyPressData, action);
                            if (result && action.NeedUpdateVisualState)
                                Keyboard.ActionSetNeedUpdateVisualState();
                            if (action.StopProcessingAtSuccessResult && result) {
                                return true;
                            }
                        }
                    }
                }
                for (KeyoneIME.KeyboardMechanics.Action action : Actions) {

                    if (action.MetaModeMethods == null || action.MetaModeMethods.isEmpty()) {
                        boolean result = InvokeMethod(keyPressData, action);
                        if (result && action.NeedUpdateVisualState)
                            Keyboard.ActionSetNeedUpdateVisualState();
                        if (action.StopProcessingAtSuccessResult && result) {
                            return true;
                        }
                    }

                }
            } catch (Throwable ex) {
                Log.e(TAG2, "Can not Process Actions "+ex);
            }
            return true;
        }

        private boolean InvokeMethod(KeyPressData keyPressData, KeyoneIME.KeyboardMechanics.Action action) throws IllegalAccessException, InvocationTargetException {
            boolean result;
            if(action.CustomKeyCodeInt > 0) {
                result = (Boolean) action.ActionMethod.invoke(Keyboard, action.CustomKeyCodeInt);
            } else if(action.CustomChar > 0) {
                result = (Boolean) action.ActionMethod.invoke(Keyboard, action.CustomChar);
            } else if(action.MethodNeedsKeyPressParameter) {
                result = (Boolean) action.ActionMethod.invoke(Keyboard, keyPressData);
            } else {
                result = (Boolean) action.ActionMethod.invoke(Keyboard);
            }

            return result;
        }


        ArrayList<KeyoneIME.KeyboardMechanics.Action> Actions = null;

        KeyoneIME Keyboard = null;
    }

    //endregion

    //region Actions NAV-MODE

    public boolean ActionTryDisableNavModeAndKeyboard() {
        if(keyboardStateFixed_NavModeAndKeyboard) {
            keyboardStateFixed_NavModeAndKeyboard = false;
            symbolOnScreenKeyboardMode = false;
            altPressSingleSymbolAltedMode = false;
            doubleAltPressAllSymbolsAlted = false;
            DetermineFirstBigCharAndReturnChangedState(getCurrentInputEditorInfo());
            UpdateGestureModeVisualization();
            ActionSetNeedUpdateVisualState1();
            return true;
        }
        return false;
    }

    public boolean ActionTryEnableNavModeAndKeyboard() {
        if (!keyboardStateFixed_NavModeAndKeyboard) {
            //Двойное нажание SYM -> Режим навигации
            //TODO: Вынести OnScreenKeyboardMode-ы в Enum
            keyboardStateFixed_NavModeAndKeyboard = true;
            fnSymbolOnScreenKeyboardMode = false;
            //TODO: Зачем это?
            keyboardView.SetFnKeyboardMode(false);
            ActionSetNeedUpdateVisualState1();
            UpdateGestureModeVisualization();
            return true;
        }
        return false;
    }

    public boolean SetNavModeHoldOnState() {
        keyboardStateHolding_NavModeAndKeyboard = true;
        ActionSetNeedUpdateVisualState1();
        UpdateGestureModeVisualization();
        return true;
    }

    public boolean SetNavModeHoldOffState() {
        keyboardStateHolding_NavModeAndKeyboard = false;
        ActionSetNeedUpdateVisualState1();
        UpdateGestureModeVisualization();
        return true;
    }

    //endregion

    //region Actions CALL

    @RequiresApi(api = Build.VERSION_CODES.O)
    public boolean ActionTryAcceptCall() {
        if(pref_manage_call && IsCalling() && !IsInputMode()) {
            if (AcceptCallOnCalling()) return true;
        }
        return false;
    }

    public boolean ActionTryDeclineCall() {
        if(pref_manage_call && IsCalling() && !IsInputMode()) {
            return DeclinePhone();
        }
        return false;
    }

    public boolean MetaStateIsOnCall() {
        if(pref_manage_call && IsCalling() && !IsInputMode()) {
            return true;
        }
        return false;
    }


    //endregion

    //region Actions GESTURE

    public boolean ActionTryTurnOffGesturesMode() {
        return super.ActionTryTurnOffGesturesMode();
    }

    protected boolean ActionResetDoubleClickGestureState() {
        return super.ActionResetDoubleClickGestureState();
    }

    public boolean ActionTryEnableGestureAtInputOnHoldState() {
        if (SystemClock.uptimeMillis() - lastGestureSwipingBeginTime < TIME_WAIT_GESTURE_UPON_KEY_0) {
            Log.d(TAG2, "GestureMode at key_0_down first time");
            mode_keyboard_gestures = true;
            UpdateGestureModeVisualization();
            return true;
        }
        return false;
    }

    public boolean ActionDisableGestureMode() {
        mode_keyboard_gestures = false;
        UpdateGestureModeVisualization();
        return true;
    }

    public boolean ActionChangeGestureModeState() {
        if(IsInputMode()) {
            mode_keyboard_gestures = !mode_keyboard_gestures;
        } else {
            pref_keyboard_gestures_at_views_enable = !pref_keyboard_gestures_at_views_enable;
        }
        UpdateGestureModeVisualization();
        //TODO: ???
        ActionSetNeedUpdateVisualState1();
        return true;
    }

    public boolean ActionEnableGestureAtInputModeAndUpDownMode() {
        if(IsInputMode()) {
            mode_keyboard_gestures = true;
            mode_keyboard_gestures_plus_up_down = true;
            UpdateGestureModeVisualization();
            //TODO: ???
            ActionSetNeedUpdateVisualState1();
        }

        return true;
    }

    public boolean ActionDisableAndResetGesturesAtInputMode() {
        if(mode_keyboard_gestures && IsInputMode()) {
            mode_keyboard_gestures = false;
            mode_keyboard_gestures_plus_up_down = false;
            UpdateGestureModeVisualization(true);
            return true;
        }
        return false;
    }

    //endregion

    //region Actions ALT-MODE
    public boolean ActionEnableHoldAltMode() {
        metaAltPressed = true;
        ActionSetNeedUpdateVisualState1();
        return true;
    }

    public boolean ActionDisableHoldAltMode() {
        metaAltPressed = false;
        ActionSetNeedUpdateVisualState1();
        return true;
    }

    public boolean ActionChangeFirstSymbolAltMode() {
        altPressSingleSymbolAltedMode = !altPressSingleSymbolAltedMode;
        ActionSetNeedUpdateVisualState1();
        return true;
    }

    public boolean ActionTryDisableFirstSymbolAltMode() {
        if (altPressSingleSymbolAltedMode && !pref_alt_space) {
            altPressSingleSymbolAltedMode = false;
            ActionSetNeedUpdateVisualState1();
            return true;
        }
        return false;
    }

    public boolean ActionChangeFixedAltModeState() {
        altPressSingleSymbolAltedMode = false;
        doubleAltPressAllSymbolsAlted = !doubleAltPressAllSymbolsAlted;
        ActionSetNeedUpdateVisualState1();
        return true;
    }

    public boolean ActionTryDisableFixedAltModeState() {
        if(doubleAltPressAllSymbolsAlted){
            doubleAltPressAllSymbolsAlted = false;
            altPressSingleSymbolAltedMode = false;
            ActionSetNeedUpdateVisualState1();
            return true;
        }
        return false;
    }

    //endregion

    //region Actions SHIFT-MODE

    public boolean ActionEnableHoldShiftMode() {
        metaShiftPressed = true;
        ActionSetNeedUpdateVisualState1();
        return true;
    }

    public boolean ActionDisableHoldShiftMode() {
        metaShiftPressed = false;
        ActionSetNeedUpdateVisualState1();
        return true;
    }

    public boolean ActionChangeFirstLetterShiftMode() {
        oneTimeShiftOneTimeBigMode = !oneTimeShiftOneTimeBigMode;
        ActionSetNeedUpdateVisualState1();
        return true;
    }

    public boolean ActionTryDisableFirstLetterShiftMode() {
        if (oneTimeShiftOneTimeBigMode) {
            oneTimeShiftOneTimeBigMode = false;
            ActionSetNeedUpdateVisualState1();
            return true;
        }
        return false;
    }

    public boolean ActionTryDisableCapslockShiftMode() {
        if(doubleShiftCapsMode) {
            doubleShiftCapsMode = false;
            DetermineFirstBigCharAndReturnChangedState(getCurrentInputEditorInfo());
            ActionSetNeedUpdateVisualState1();
            return true;
        }
        return false;
    }

    public boolean ActionChangeShiftCapslockState() {
        oneTimeShiftOneTimeBigMode = false;
        doubleShiftCapsMode = !doubleShiftCapsMode;
        ActionSetNeedUpdateVisualState1();
        return true;
    }

    //endregion

    //region Actions SYM-PAD

    public boolean ActionTryChangeSymPadVisibilityAtInputMode() {
        if(IsInputMode()) {
            if(!symbolOnScreenKeyboardMode){
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
            ActionSetNeedUpdateVisualState1();
            return true;
        }
        return false;
    }


    public boolean ActionTryChangeSymPadLayout() {
        if(symbolOnScreenKeyboardMode) {
            symPadAltShift = !symPadAltShift;
            ActionSetNeedUpdateVisualState1();
            return true;
        }
        return false;
    }

    //endregion

    //region Actions CTRL-MODE

    public boolean ActionEnableHoldCtrlMode(KeyPressData keyPressData) {
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        metaCtrlPressed = true;

        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(
                        keyPressData.BaseKeyEvent.getDownTime(),
                        keyPressData.BaseKeyEvent.getEventTime(),
                        KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta));
        return true;
    }

    public boolean ActionDisableHoldCtrlMode(KeyPressData keyPressData) {
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        metaCtrlPressed = false;
        getCurrentInputConnection().sendKeyEvent(new KeyEvent(
                keyPressData.BaseKeyEvent.getDownTime(),
                keyPressData.BaseKeyEvent.getEventTime(),
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta));
        return true;
    }

    //endregion

    //region Actions OTHER


    //SAME AS: "need-update-visual-state": true,
    public boolean ActionSetNeedUpdateVisualState() {
        needUpdateVisualInsideSingleEvent = true;
        return true;
    }

    public boolean ActionSetNeedUpdateVisualState1() {
        //IS ON: "need-update-visual-state": true
        //needUpdateVisualInsideSingleEvent = true;
        return true;
    }

    public boolean ActionDeletePreviousSymbol(KeyPressData keyPressData) {
        DeleteLastSymbol();
        //ActionSetNeedUpdateVisualState();
        return true;
    }

    boolean DoNothingAndMakeUndoAtSubsequentKeyAction(KeyPressData keyPressData) {
        return true;
    }

    public boolean ChangeLanguage() {
        keyboardLayoutManager.ChangeLayout();
        if(pref_show_toast) {
            Toast toast = Toast.makeText(getApplicationContext(), keyboardLayoutManager.GetCurrentKeyboardLayout().KeyboardName, Toast.LENGTH_SHORT);
            toast.show();
        }
        UpdateKeyboardModeVisualization();
        return true;
    }

    public boolean ActionTryChangeKeyboardLayoutAtBaseMetaShift(KeyPressData keyPressData) {
        if(!IsShiftMeta(keyPressData.MetaBase))
            return false;
        return ActionChangeKeyboardLayout();
    }

    public boolean ActionChangeSwipePanelVisibility() {
        if (keyboardView.isShown()) {
            pref_show_default_onscreen_keyboard = false;
            UpdateKeyboardVisibilityOnPrefChange();
        } else if (!keyboardView.isShown()) {
            pref_show_default_onscreen_keyboard = true;
            UpdateKeyboardVisibilityOnPrefChange();
        }
        return true;
    }

    public boolean ActionKeyDown(int customKeyCode) {
        return super.ActionKeyDown(customKeyCode);
    }

    public boolean ActionKeyDownUpDefaultFlags(int customKeyCode) {
        return super.ActionKeyDownUpDefaultFlags(customKeyCode);
    }

    public boolean ActionKeyDownUpNoMetaKeepTouch(int keyEventCode) {
        super.ActionCustomKeyDownUpNoMetaKeepTouch(keyEventCode);
        return true;
    }

    public boolean ActionDoNothing() {
        return true;
    }

    public boolean ActionDeleteUntilPrevCrLf() {
        InputConnection inputConnection = getCurrentInputConnection();
        CharSequence c = inputConnection.getTextBeforeCursor(Integer.MAX_VALUE, 0);
        int dist = findPrevEnterDistance(c);
        if(dist == 0 && c.length() > 0) {
            //Это первый абзац в тексте
            dist = c.length();
        }
        if(dist > 0) {
            inputConnection.deleteSurroundingText(dist, 0);
        }
        return true;
    }

    public boolean ActionUnCrLf() {
        InputConnection inputConnection = getCurrentInputConnection();
        CharSequence c = inputConnection.getTextBeforeCursor(Integer.MAX_VALUE, 0);
        int pos = findPrevEnterAbsPos(c);
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

    public boolean ActionTryDoubleSpaceDotSpaceConversion() {
        InputConnection inputConnection = getCurrentInputConnection();
        CharSequence back_letter = inputConnection.getTextBeforeCursor(2,0);
        Log.d(TAG2, "KEYCODE_SPACE back_letter "+back_letter);
        if(back_letter.length() == 2 && Character.isLetterOrDigit(back_letter.charAt(0)) && back_letter.charAt(1) == ' ') {
            inputConnection.deleteSurroundingText(1, 0);
            inputConnection.commitText(". ", 2);
            ActionSetNeedUpdateVisualState1();
            return true;
        }
        return false;
    }



    public boolean ActionChangeKeyboardLayout() {
        ChangeLanguage();
        ActionSetNeedUpdateVisualState1();
        return true;
    }

    public boolean ActionTryDisableAltModeUponSpace() {
        if(altPressSingleSymbolAltedMode && pref_alt_space) {
            altPressSingleSymbolAltedMode = false;
            return true;
        }
        return false;
    }



    public boolean ActionSendCharToInput(char char1) {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null)
            inputConnection.commitText(String.valueOf(char1), 1);
        return true;
    }


    //endregion

    //region Actions LETTERS

    public boolean ActionSendCtrlPlusKey(KeyPressData keyPressData) {
        if(keyPressData.KeyCode == KeyEvent.KEYCODE_S) {
            //keyDownUp(KeyEvent.KEYCODE_ESCAPE, getCurrentInputConnection(), 0xFFFFFFFF, 0xFFFFFFFF);
            //Log.d(TAG2, "TEST KEY SENT");
            //Testing open Search containers
            //return true;
        }
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        //Для K1 надо очищать мета статус от всего лишено, оставляем только shift для Ctrl+Shift+Z;
        int metaBase =  (keyPressData.MetaBase & KeyEvent.META_SHIFT_LEFT_ON) > 0 ? KeyEvent.META_SHIFT_LEFT_ON : 0;
        keyDownUpKeepTouch(keyPressData.KeyCode, getCurrentInputConnection(), meta | metaBase);

        return true;
    }



    public boolean ActionSendCharSinglePressSymMode(KeyPressData keyPressData) {
        int code2send;
        code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);
        SendLetterOrSymbol(code2send);
        return true;
    }

    //TODO: DELETE ME
    public boolean DeprecatedActionSendCharSinglePressAltOrSymMode(KeyPressData keyPressData) {
        int code2send;
        code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, IsShiftModeOrSymPadAltShiftMode(), false);
        SendLetterOrSymbol(code2send);
        //keyboardLayoutManager.ScanCodeKeyCodeMapping.put(keyPressData.ScanCode, keyPressData.KeyCode);
        return true;
    }

    public boolean ActionSendCharSinglePressShiftMode(KeyPressData keyPressData) {
        int code2send;
        code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, true, false);
        SendLetterOrSymbol(code2send);
        //keyboardLayoutManager.ScanCodeKeyCodeMapping.put(keyPressData.ScanCode, keyPressData.KeyCode);
        return true;
    }

    public boolean ActionSendCharSinglePressAltMode(KeyPressData keyPressData) {
        int code2send;
        code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, false, false);
        SendLetterOrSymbol(code2send);
        return true;
    }

    public boolean ActionSendCharSinglePressNoMeta(KeyPressData keyPressData) {
        int code2send;
        code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, false, false);
        SendLetterOrSymbol(code2send);
        //keyboardLayoutManager.ScanCodeKeyCodeMapping.put(keyPressData.ScanCode, keyPressData.KeyCode);
        return true;
    }

    public boolean ActionSendCharDoublePressNoMeta(KeyPressData keyPressData) {
        int code2send;

        //TODO: Попробовать: по сути это можно поднять в логику выше
        if(IsNotPairedLetter(keyPressData)) {
            //TODO: Особенно is_double_press
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, false, false);
            SendLetterOrSymbol(code2send);
            return true;
        }

        boolean needShift = false;
        //Определяем была ли первая из сдвоенных букв Заглавной
        int letterShifted = keyboardLayoutManager.KeyToCharCode(keyPressData, false, true, false);
        int letterBeforeCursor = GetLetterBeforeCursor();
        if(letterBeforeCursor == letterShifted)
            needShift = true;


        DeleteLastSymbol();
        //DetermineFirstBigCharAndReturnChangedState(getCurrentInputEditorInfo());
        code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, needShift, true);
        SendLetterOrSymbol(code2send);
        return true;
    }

    public boolean ActionSendCharLongPressCapitalize(KeyPressData keyPressData) {
        int code2send;
        DeleteLastSymbol();
        if (keyPressData.Short2ndLongPress) {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, true, true);
        } else {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, true, false);
        }
        SendLetterOrSymbol(code2send);
        return true;
    }

    public boolean ActionSendCharLongPressCapitalizeAltMode(KeyPressData keyPressData) {
        int code2send;
        DeleteLastSymbol();
        if (keyPressData.Short2ndLongPress) {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, true);
        } else {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);
        }
        SendLetterOrSymbol(code2send);
        return true;
    }

    public boolean ActionSendCharLongPressAltSymbolNoMeta(KeyPressData keyPressData) {
        int code2send;
        DeleteLastSymbol();
        if(keyPressData.Short2ndLongPress ) {
            if (IsNotPairedLetter(keyPressData))
                DeleteLastSymbol();
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);

        } else {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, false, false);
        }

        SendLetterOrSymbol(code2send);
        return true;
    }

    public boolean ActionSendCharLongPressAltSymbolShiftMode(KeyPressData keyPressData) {
        int code2send;
        DeleteLastSymbol();
        if(keyPressData.Short2ndLongPress ) {
            if (IsNotPairedLetter(keyPressData))
                DeleteLastSymbol();
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);

        } else {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);
        }

        SendLetterOrSymbol(code2send);
        return true;
    }

    public boolean ActionSendCharLongPressAltSymbolAltMode(KeyPressData keyPressData) {
        int code2send;
        DeleteLastSymbol();
        if (keyPressData.Short2ndLongPress) {
            DeleteLastSymbol();
            code2send = keyboardLayoutManager.KeyToAltPopup(keyPressData);
            if (code2send == 0) {
                code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);
            }
        } else {
            code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, true, true, false);
        }
        SendLetterOrSymbol(code2send);
        return true;
    }


    //endregion

    //region META

    //TODO: Блин вот хоть убей не помню нафига этот хак, но помню что без него что-то не работало (возвожно на К1)

    public boolean PrefLongPressAltSymbol() {
        return pref_long_press_key_alt_symbol;
    }

    public boolean MetaIsShiftPressed() {

        return metaShiftPressed;
    }

    public boolean MetaIsCtrlPressed() {

        return metaCtrlPressed;
    }

    public boolean MetaIsShiftMode() {

        return oneTimeShiftOneTimeBigMode || doubleShiftCapsMode || metaShiftPressed;
    }

    public boolean MetaIsAltMode() {
        return altPressSingleSymbolAltedMode || doubleAltPressAllSymbolsAlted || metaAltPressed;
    }

    public boolean MetaIsAltPressed() {
        return metaAltPressed;
    }




    public boolean MetaIsSymPadAltShiftMode() {
        return symbolOnScreenKeyboardMode && symPadAltShift;
    }

    //endregion
}
