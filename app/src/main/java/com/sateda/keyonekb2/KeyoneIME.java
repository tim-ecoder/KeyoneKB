package com.sateda.keyonekb2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.os.Vibrator;
import android.support.annotation.Keep;
import android.support.annotation.RequiresApi;
import android.telephony.PhoneStateListener;
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
import com.sateda.keyonekb2.input.CallStateCallback;

import java.util.*;


@Keep
public class KeyoneIME extends InputMethodServiceCoreCustomizable implements KeyboardView.OnKeyboardActionListener, SpellCheckerSession.SpellCheckerSessionListener, View.OnTouchListener {



    private static final boolean DEBUG = false;

    public static KeyoneIME Instance;



    public String TITLE_NAV_TEXT;
    public String TITLE_NAV_FV_TEXT;
    public String TITLE_SYM_TEXT;
    public String TITLE_SYM2_TEXT;
    public String TITLE_GESTURE_INPUT;
    public String TITLE_GESTURE_INPUT_UP_DOWN;
    public String TITLE_GESTURE_INPUT_LIST;
    public String TITLE_GESTURE_VIEW_POINTER;
    public String TITLE_GESTURE_VIEW;
    public String TITLE_GESTURE_OFF;
    public String TITLE_DIGITS_TEXT;








    //settings
    private int pref_height_bottom_bar = 10;
    private boolean pref_flag = false;
    private boolean pref_system_icon_no_notification_text = false;

    //Предзагружаем клавиатуры, чтобы не плодить объекты
    private Keyboard keyboardNavigation;

    private Keyboard onScreenSwipePanelAndLanguage;


    private final NotificationProcessor notificationProcessor = new NotificationProcessor();


    KeyboardLayout.KeyboardLayoutOptions.IconRes AltOneIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes AltAllIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes AltHoldIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes SymOneIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes SymAllIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes SymHoldIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes navIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes navFnIconRes;
    KeyboardLayout.KeyboardLayoutOptions.IconRes digitsPadIconRes;




    //region FixedSizeSet

    public FixedSizeSet<String> PackageHistory = new FixedSizeSet<>(4);


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

    //endregion

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
            TITLE_GESTURE_INPUT_LIST = getString(R.string.notification_kb_state_gesture_input_list);
            TITLE_GESTURE_VIEW_POINTER = getString(R.string.notification_kb_state_gesture_pointer);
            TITLE_DIGITS_TEXT = getString(R.string.notification_kb_state_digits_pad);

            AltOneIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_alt_one, R.drawable.ic_kb_alt_one);
            AltAllIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_alt, R.drawable.ic_kb_alt_all);
            AltHoldIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_alt, R.drawable.ic_kb_alt);
            SymOneIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_sym_one, R.drawable.ic_kb_sym_one);
            SymAllIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_sym, R.drawable.ic_kb_sym_all);
            SymHoldIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_sym, R.drawable.ic_kb_sym);

            navIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_nav, R.drawable.ic_kb_nav);
            navFnIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_nav_fn, R.drawable.ic_kb_nav_fn);
            digitsPadIconRes = KeyboardLayout.KeyboardLayoutOptions.CreateIconRes(R.mipmap.ic_kb_digits, R.drawable.ic_kb_digits);

            callStateCallback = new CallStateCallback();
            telephonyManager = getTelephonyManager();
            telecomManager = getTelecomManager();

            if (telephonyManager != null) {
                telephonyManager.listen(callStateCallback, PhoneStateListener.LISTEN_CALL_STATE);
            }

            keyoneKb2Settings = KeyoneKb2Settings.Get(getSharedPreferences(KeyoneKb2Settings.APP_PREFERENCES, Context.MODE_PRIVATE));

            TIME_VIBRATE = CoreKeyboardSettings.TimeVibrate;

            LoadSettingsAndKeyboards();
            LoadKeyProcessingMechanics(this);

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

            vibratorService = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

            UpdateGestureModeVisualization(false);
            UpdateKeyboardModeVisualization();
        } catch(Throwable ex) {
            Log.e(TAG2, "onCreate Exception: "+ex);
            throw ex;
        }
    }



    @Override
    public void onDestroy() {
        Instance = null;
        notificationProcessor.CancelAll();
        if (telephonyManager != null) {
            telephonyManager.listen(callStateCallback, PhoneStateListener.LISTEN_NONE);
        }
        super.onDestroy();
    }

    @SuppressLint("InflateParams")
    @Override
    public View onCreateInputView() {
        Log.d(TAG2, "onCreateInputView");
        keyboardView.setOnKeyboardActionListener(this);
        return keyboardView;
    }

    @Override
    public synchronized void onStartInput(EditorInfo editorInfo, boolean restarting) {
        super.onStartInput(editorInfo, restarting);
        //TODO: Минорно. Если надо знать какие флаги их надо расшифровывать
        Log.d(TAG2, "onStartInput package: " + editorInfo.packageName
                + " editorInfo.inputType: "+Integer.toBinaryString(editorInfo.inputType)
                +" editorInfo.imeOptions: "+Integer.toBinaryString(editorInfo.imeOptions));
        isPackageChangedInsideSingleEvent = false;
        // Обрабатываем переход между приложениями
        if (!editorInfo.packageName.equals(_lastPackageName)) {

            //TODO: Перенести в keyboard_mechanics с использованием PackageHistory
            SetGestureDefaultPointerMode(_lastPackageName, GesturePointerMode);

            PackageHistory.add(_lastPackageName);
            _lastPackageName = editorInfo.packageName;
            isPackageChangedInsideSingleEvent = true;

        }

        //Это нужно чтобы показать клаву (перейти в режим редактирования)
        if (pref_show_default_onscreen_keyboard
                && !isInputViewShown()
                && getCurrentInputConnection() != null
                && IsInputMode()
                && Orientation == 1) {
            keyboardView.setOnTouchListener(this);
            this.showWindow(true);
        }

        OnStartInput.Process(null);
        if (needUpdateVisualInsideSingleEvent)
            UpdateKeyboardModeVisualization();
        needUpdateVisualInsideSingleEvent = false;
        if(needUpdateGestureNotificationInsideSingleEvent)
            UpdateGestureModeVisualization();
        needUpdateGestureNotificationInsideSingleEvent = false;
        isPackageChangedInsideSingleEvent = false;
    }



    private boolean isPackageChangedInsideSingleEvent;
    @Override
    public boolean MetaIsPackageChanged() {
        return isPackageChangedInsideSingleEvent;
    }

    @Override
    public void onFinishInput() {

        OnFinishInput.Process(null);
        if (needUpdateVisualInsideSingleEvent)
            UpdateKeyboardModeVisualization();
        needUpdateVisualInsideSingleEvent = false;
        if(needUpdateGestureNotificationInsideSingleEvent)
            UpdateGestureModeVisualization();
        needUpdateGestureNotificationInsideSingleEvent = false;
        //TODO: Проверить как это работает
        if (_lastPackageName.equals("com.sateda.keyonekb2")) LoadSettingsAndKeyboards();

        //TODO: Подумать, чтобы не надо было инициализировать свайп-клавиаутуру по настройке pref_show_default_onscreen_keyboard
        keyboardView.showFlag(pref_flag);
        if (onScreenSwipePanelAndLanguage.getHeight() != 70 + pref_height_bottom_bar * 5)
            onScreenSwipePanelAndLanguage = new SatedaKeyboard(this, R.xml.space_empty, 70 + pref_height_bottom_bar * 5);

        Log.d(TAG2, "onFinishInput ");
    }



    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public synchronized boolean onKeyDown(int keyCode, KeyEvent event) {
        keyboardView.hidePopup(false);
        Log.v(TAG2, "onKeyDown " + event);

        //TODO: Hack 4 pocket
        //TODO: Временно не поддерживается
        //if (keyCode == KeyEvent.KEYCODE_BACK) {
        //    return false;
        //}

        if(
            !IsInputMode()
            && IsViewModeKeyCode(keyCode, event.getMetaState())
            && SearchPluginLauncher == null
            && !_digitsHackActive
            && !IsNavMode())  {
            Log.d(TAG2, "App transparency mode");
            return super.onKeyDown(keyCode, event);
        }

        //region Режим "Навигационные клавиши"
        //TODO: Вынести NAV режим в кастомизации keyboard_mechanics

        int navigationKeyCode;
        InputConnection inputConnection = getCurrentInputConnection();
        if (IsNavMode() && IsNavKeyCode(keyCode)) {
            AnyButtonPressTimeForHoldPlusButtonState = event.getEventTime();
            int scanCode = event.getScanCode();
            navigationKeyCode = getNavigationCode(scanCode);

            Log.d(TAG2, "navigationKeyCode " + navigationKeyCode);
            if (navigationKeyCode == -7) {
                keyboardStateFixed_FnSymbolOnScreenKeyboard = !keyboardStateFixed_FnSymbolOnScreenKeyboard;
                UpdateKeyboardModeVisualization();
                keyboardView.SetFnKeyboardMode(keyboardStateFixed_FnSymbolOnScreenKeyboard);
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
        _isKeyTransparencyInsideUpDownEvent = false;
        boolean processed = ProcessNewStatusModelOnKeyDown(keyCode, event);
        if(_isKeyTransparencyInsideUpDownEvent)
            return false;
        if (!processed)
            return false;


        if (needUpdateVisualInsideSingleEvent)
            UpdateKeyboardModeVisualization();
        needUpdateVisualInsideSingleEvent = false;

        if(needUpdateGestureNotificationInsideSingleEvent)
            UpdateGestureModeVisualization();
        needUpdateGestureNotificationInsideSingleEvent = false;

        //Это нужно чтобы работал "чужой"/встроенный механизм выделения с Shift-ом
        return keyCode != KeyEvent.KEYCODE_SHIFT_LEFT;
    }

    @Override
    public synchronized boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.v(TAG2, "onKeyUp " + event);

        //TODO: Hack 4 pocket
        //if (keyCode == KeyEvent.KEYCODE_BACK) {
        //    return false;
        //}

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

        if (_isKeyTransparencyInsideUpDownEvent) {
            _isKeyTransparencyInsideUpDownEvent = false;
            return false;
        }

        if (!processed)
            return false;

        if (needUpdateVisualInsideSingleEvent)
            UpdateKeyboardModeVisualization();
        needUpdateVisualInsideSingleEvent = false;

        if(needUpdateGestureNotificationInsideSingleEvent)
            UpdateGestureModeVisualization();
        needUpdateGestureNotificationInsideSingleEvent = false;

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
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) {
                    keyEventCode = 131; //F1
                    break;
                }
            case 21: //Y
                keyEventCode = 122; //Home
                break;
            case 18: //E (2)
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) {
                    keyEventCode = 132; //F2
                    break;
                }
            case 22: //U
                keyEventCode = 19; //Arrow Up
                break;
            case 19: //R (3)
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) {
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
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) {
                    keyEventCode = 134; //F4
                    break;
                }
            case 35: //H
                keyEventCode = 21; //Arrow Left
                break;
            case 32: //D
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) {
                    keyEventCode = 135; //F5
                    break;
                }
            case 36: //J
                keyEventCode = 20; //Arrow Down
                break;
            case 33: //F
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) {
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
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) keyEventCode = 137; //F7
                break;
            case 45: //X (8)
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) keyEventCode = 138; //F8
                break;
            case 46: //C (9)
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) keyEventCode = 139; //F9
                break;

            case 11: //0
                if (keyboardStateFixed_FnSymbolOnScreenKeyboard) keyEventCode = 140; //F10
                break;

            default:
        }

        return keyEventCode;
    }

    @Override
    public void onPress(int primaryCode) {
        Log.d(TAG2, "onPress");
    }

    @Override
    public void onRelease(int primaryCode) {

    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        Log.d(TAG2, "onKeyLongPress " + event);
        return false;
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
                    keyboardStateFixed_FnSymbolOnScreenKeyboard = !keyboardStateFixed_FnSymbolOnScreenKeyboard;
                    UpdateKeyboardModeVisualization();
                    keyboardView.SetFnKeyboardMode(keyboardStateFixed_FnSymbolOnScreenKeyboard);
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
        if (!keyboardStateFixed_SymbolOnScreenKeyboard) {
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
         //Log.d(TAG2, "onGenericMotionEvent(): " + motionEvent);

        boolean ret = ProcessGestureAtMotionEvent(motionEvent);
         if(needUpdateGestureNotificationInsideSingleEvent)
             UpdateGestureModeVisualization();
         needUpdateGestureNotificationInsideSingleEvent = false;
         return ret;
    }


    public boolean IsSym2Mode() {
        return MetaIsAltMode() && IsShiftModeOrSymPadAltShiftMode();
    }

    private boolean IsShiftModeOrSymPadAltShiftMode() {
        return MetaIsShiftPressed() || MetaIsSymPadAltShiftMode();
    }



    @Override
    protected void ChangeLanguage() {
        keyboardLayoutManager.ChangeLayout();
        if(pref_show_toast) {
            Toast toast = Toast.makeText(getApplicationContext(), keyboardLayoutManager.GetCurrentKeyboardLayout().KeyboardName, Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    protected void UpdateKeyboardVisibilityOnPrefChange() {
        if (pref_show_default_onscreen_keyboard) {
            UpdateKeyboardModeVisualization(true);
            ShowKeyboard();
        } else {
            HideKeyboard();
        }
    }


    //region VISUAL UPDATE


    protected void UpdateGestureModeVisualization() {
        UpdateGestureModeVisualization(IsInputMode());
    }

    //@Override
    protected void UpdateGestureModeVisualization(boolean isInput) {
        boolean changed = false;
        if(IsNoGesturesMode()) {
            changed = setSmallIcon2(R.mipmap.ic_gesture_icon_off);
            changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_OFF);
        }
        else if(isInput) {
            if(_gestureInputScrollViewMode) {
                changed = setSmallIcon2(R.mipmap.ic_gesture_icon_view);
                changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_INPUT_LIST);
            } else if (mode_keyboard_gestures) {
                if (mode_keyboard_gestures_plus_up_down) {
                    changed = setSmallIcon2(R.mipmap.ic_gesture_icon_input_up_down);
                    changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_INPUT_UP_DOWN);
                } else {
                    changed = setSmallIcon2(R.mipmap.ic_gesture_icon_input);
                    changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_INPUT);
                }
            } else {
                    changed = setSmallIcon2(R.mipmap.ic_gesture_icon_off);
                    changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_OFF);
            }
        } else { //!isIsInput
            if(!pref_keyboard_gestures_at_views_enable) {
                changed = setSmallIcon2(R.mipmap.ic_gesture_icon_off);
                changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_OFF);
            } else if (GesturePointerMode) {
                changed = setSmallIcon2(R.mipmap.ic_gesture_icon_pointer);
                changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_VIEW_POINTER);
            } else {
                changed = setSmallIcon2(R.mipmap.ic_gesture_icon_view);
                changed |= notificationProcessor.SetContentTitleGestureMode(TITLE_GESTURE_VIEW);
            }
        }

        if (changed)
            notificationProcessor.UpdateNotificationGestureMode();
    }


    private boolean setSmallIcon2(int resId) {
        return notificationProcessor.SetSmallIconGestureMode(resId);
    }


    private void HideSwipePanelOnHidePreferenceAndVisibleState() {
        if (!pref_show_default_onscreen_keyboard) {
            HideKeyboard();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    protected void HideKeyboard() {
        keyboardView.setOnTouchListener(null);
        if (keyboardView.getVisibility() == View.VISIBLE) {
            keyboardView.setVisibility(View.GONE);
        }
        this.hideWindow();

    }

    @SuppressLint("ClickableViewAccessibility")
    protected void ShowKeyboard() {
        keyboardView.setOnTouchListener(this);
        if (keyboardView.getVisibility() != View.VISIBLE)
            keyboardView.setVisibility(View.VISIBLE);
        if (!keyboardView.isShown())
            this.showWindow(true);
    }

    @Override
    protected void UpdateKeyboardModeVisualization() {
        UpdateKeyboardModeVisualization(pref_show_default_onscreen_keyboard);
    }

    @Override
    protected void UpdateKeyboardModeVisualization(boolean updateSwipePanelData) {
        Log.d(TAG2, "UpdateKeyboardModeVisualization oneTimeShiftOneTimeBigMode=" + metaFixedModeFirstLetterUpper + " doubleShiftCapsMode=" + metaFixedModeCapslock + " doubleAltPressAllSymbolsAlted=" + metaFixedModeAllSymbolsAlt + " altPressSingleSymbolAltedMode=" + metaFixedModeFirstSymbolAlt);
        KeyboardLayout keyboardLayout = keyboardLayoutManager.GetCurrentKeyboardLayout();

        String languageOnScreenNaming = keyboardLayout.KeyboardName;
        boolean changed;
        boolean needUsefulKeyboard = false;
        if(_digitsHackActive) {
            changed = UpdateNotification(digitsPadIconRes, TITLE_DIGITS_TEXT);
        } else if (IsNavMode()) {
            if (!keyboardStateFixed_FnSymbolOnScreenKeyboard) {
                changed = UpdateNotification(navIconRes, TITLE_NAV_TEXT);
            } else {
                changed = UpdateNotification(navFnIconRes, TITLE_NAV_FV_TEXT);
            }
            //onScreenKeyboardSymbols = keyboardNavigation;
            keyboardView.setKeyboard(keyboardNavigation);
            keyboardView.setNavigationLayer();
            needUsefulKeyboard = true;
        } else if (keyboardStateFixed_SymbolOnScreenKeyboard) {

            if (IsSym2Mode()) {
                changed = UpdateNotification(SymAllIconRes, TITLE_SYM2_TEXT);
            } else {
                changed = UpdateNotification(AltAllIconRes, TITLE_SYM_TEXT);
            }

            keyboardView.setKeyboard(keyboardLayoutManager.GetSymKeyboard(symPadAltShift));
            keyboardView.setAltLayer(keyboardLayoutManager.GetCurrentKeyboardLayout(), symPadAltShift);

            needUsefulKeyboard = true;

        } else if (metaFixedModeAllSymbolsAlt || metaHoldAlt) {
            if (IsSym2Mode()) {
                if(metaHoldAlt)
                    changed = UpdateNotification(SymHoldIconRes, TITLE_SYM2_TEXT);
                else
                    changed = UpdateNotification(SymAllIconRes, TITLE_SYM2_TEXT);
            } else if (metaHoldAlt){
                changed = UpdateNotification(AltHoldIconRes, TITLE_SYM_TEXT);
            } else {
                changed = UpdateNotification(AltAllIconRes, TITLE_SYM2_TEXT);
            }
            UpdateKeyboardViewAltMode(updateSwipePanelData);
        } else if (metaFixedModeFirstSymbolAlt) {
            if (IsSym2Mode()) {
                changed = UpdateNotification(SymOneIconRes, TITLE_SYM2_TEXT);
            } else {
                changed = UpdateNotification(AltOneIconRes, TITLE_SYM_TEXT);
            }
            UpdateKeyboardViewAltMode(updateSwipePanelData);
        } else if (metaFixedModeCapslock || metaHoldShift) {
            changed = UpdateNotification(keyboardLayout.Resources.IconCapsRes, languageOnScreenNaming);
            UpdateKeyboardViewShiftMode(updateSwipePanelData, languageOnScreenNaming);
        } else if (metaFixedModeFirstLetterUpper) {
            changed = UpdateNotification(keyboardLayout.Resources.IconFirstShiftRes, languageOnScreenNaming);
            UpdateKeyboardViewShiftOneMode(updateSwipePanelData, languageOnScreenNaming);
        } else {
            // Случай со строными буквами
            changed = UpdateNotification(keyboardLayout.Resources.IconLowercaseRes, languageOnScreenNaming);
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
        for (int keyCode1 : ViewModeExcludeKeyCodes) {
            if(keyCode == keyCode1)
                return false;
        }
        return true;
    }

    private boolean IsViewModeKeyCodeOld(int keyCode, int meta) {
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
        boolean needUpdateVisual = DetermineForceFirstUpper(editorInfo);
        if(needUpdateVisual) {
            UpdateKeyboardModeVisualization();
            return true;
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


    //region LOAD SETTINGS & KEYBOARDS


    public static ArrayList<KeyboardLayout.KeyboardLayoutOptions> allLayouts;

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
        pref_vibrate_on_key_down = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_11_VIBRATE_ON_KEY_DOWN);
        pref_ensure_entered_text = keyoneKb2Settings.GetBooleanValue(keyoneKb2Settings.APP_PREFERENCES_12_ENSURE_ENTERED_TEXT);

        allLayouts = KeyboardLayoutManager.LoadKeyboardLayoutsRes(getResources(), getApplicationContext());
        ArrayList<KeyboardLayout.KeyboardLayoutOptions> activeLayouts = new ArrayList<>();

        boolean isFirst = true;
        //for each keyboard layout in active layouts find in settings and if setting is true then set keyboard layout to active
        for(KeyboardLayout.KeyboardLayoutOptions keyboardLayoutOptions : allLayouts) {
            keyoneKb2Settings.CheckSettingOrSetDefault(keyboardLayoutOptions.getPreferenceName(), keyoneKb2Settings.KEYBOARD_LAYOUT_IS_ENABLED_DEFAULT);
            boolean enabled = keyoneKb2Settings.GetBooleanValue(keyboardLayoutOptions.getPreferenceName());
            if(enabled) {
                activeLayouts.add(keyboardLayoutOptions);
                if(isFirst) {
                    if (keyboardLayoutOptions.CustomKeyboardMechanics != null && !keyboardLayoutOptions.CustomKeyboardMechanics.isEmpty()) {
                        keyboard_mechanics_res = keyboardLayoutOptions.CustomKeyboardMechanics;
                    }
                    isFirst = false;
                }
            }
        }

        if(activeLayouts.isEmpty()) {
            KeyboardLayout.KeyboardLayoutOptions defLayout = allLayouts.get(0);
            keyoneKb2Settings.SetBooleanValue(defLayout.getPreferenceName(), true);
            activeLayouts.add(defLayout);
        }
        keyboardLayoutManager.Initialize(activeLayouts, getResources(), getApplicationContext());
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
