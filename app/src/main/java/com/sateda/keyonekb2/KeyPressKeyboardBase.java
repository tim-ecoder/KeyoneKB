package com.sateda.keyonekb2;

import android.annotation.SuppressLint;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.List;

public class KeyPressKeyboardBase extends InputMethodService {

    protected final int[] KEY2_LATIN_ALPHABET_KEYS_CODES = new int[]{
            KeyEvent.KEYCODE_4, //DOLLAR
            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_C,
            KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_E,
            KeyEvent.KEYCODE_F,
            KeyEvent.KEYCODE_G,
            KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_I,
            KeyEvent.KEYCODE_J,
            KeyEvent.KEYCODE_K,
            KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_M,
            KeyEvent.KEYCODE_N,
            KeyEvent.KEYCODE_O,
            KeyEvent.KEYCODE_P,
            KeyEvent.KEYCODE_Q,
            KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_U,
            KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_Y,
            KeyEvent.KEYCODE_Z,
    };
    int TIME_DOUBLE_PRESS;
    int TIME_SHORT_2ND_LONG_PRESS;
    int TIME_LONG_PRESS;
    int TIME_SHORT_PRESS;
    public static String DEBUG_TEXT = "";
    public static boolean IS_KEYBOARD_TEST = false;

    List<KeyPressData> KeyDownList1 = new ArrayList<>();

    long AnyHoldPlusButtonSignalTime = 0;

    public static final String TAG2 = "KeyoneKb2";

    protected List<KeyProcessingMode> keyProcessingModeList = new ArrayList<>();
    KeyPressData LastShortPressKeyUpForDoublePress = null;
    KeyPressData LastDoublePress = null;


    KeyPressData LastShortPressKey1 = null;
    KeyPressData LastDoublePressKey = null;



    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate() {
        super.onCreate();
        TIME_DOUBLE_PRESS = Integer.parseInt(getString(R.string.KB_CORE_TIME_DOUBLE_PRESS));
        TIME_SHORT_2ND_LONG_PRESS = Integer.parseInt(getString(R.string.KB_CORE_TIME_SHORT_2ND_LONG_PRESS));
        TIME_LONG_PRESS = Integer.parseInt(getString(R.string.KB_CORE_TIME_LONG_PRESS));
        TIME_SHORT_PRESS = Integer.parseInt(getString(R.string.KB_CORE_TIME_SHORT_PRESS));
    }

    //region KEY_DOWN_UP

    //Если через него отправлять навигационные и прочие команды, не будет активироваться выделение виджета на рабочем столе
    protected void keyDownUpNoMetaKeepTouch(int keyEventCode, InputConnection ic) {
        keyDownUpKeepTouch(keyEventCode, ic, 0);
    }


    protected static void keyDownUpDefaultFlags(int keyEventCode, InputConnection ic) {
        if (ic == null) return;

        ic.sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        ic.sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    protected static void keyDownUp(int keyEventCode, InputConnection ic, int meta, int flag) {
        if (ic == null) return;
        long now = SystemClock.uptimeMillis();

        ic.sendKeyEvent(
                new KeyEvent(now - 3, now - 2, KeyEvent.ACTION_DOWN, keyEventCode, 0,
                        meta, -1, 0,
                        flag, 0x101));
        ic.sendKeyEvent(
                new KeyEvent(now - 1, now, KeyEvent.ACTION_UP, keyEventCode, 0,
                        meta, -1, 0,
                        flag, 0x101));
    }

    protected static void keyDownUpKeepTouch(int keyEventCode, InputConnection ic, int meta) {
        keyDownUp(keyEventCode, ic, meta,KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);
    }

    //endregion

    boolean IsSameKeyDownPress(KeyPressData keyPressData1, KeyPressData keyPressData2) {
        return keyPressData1 != null && keyPressData2 != null
                && (keyPressData1.KeyCode == keyPressData2.KeyCode
                    || keyPressData1.ScanCode == keyPressData2.ScanCode);
    }

    protected boolean ProcessNewStatusModelOnKeyDown(int keyCode, KeyEvent event) {
        int scanCode = event.getScanCode();
        int repeatCount1 = event.getRepeatCount();
        long eventTime = event.getEventTime();
        long keyDownTime = event.getDownTime();

        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyCode, scanCode);
        if (keyProcessingMode == null)
            return false;
        //Только короткое нажатие - делаем действие сразу FAST_TRACK
        if (keyProcessingMode.IsShortPressOnly()) {
            AnyHoldPlusButtonSignalTime = eventTime;

            KeyPressData keyPressData1 = CreateKeyPressData(keyCode, scanCode, keyDownTime, keyProcessingMode, event.getMetaState());
            ProcessShortPress(keyPressData1);


            return true;
        }

        if(keyProcessingMode.IsShortDoublePressMode()) {
            AnyHoldPlusButtonSignalTime = eventTime;

            KeyPressData keyPressData1 = CreateKeyPressData(keyCode, scanCode, keyDownTime, keyProcessingMode, event.getMetaState());
            if(repeatCount1 == 0)
                KeyDownList1.add(keyPressData1);

            if(LastShortPressKey1 == null || !IsSameKeyDownPress(LastShortPressKey1, keyPressData1)) {
                ProcessShortPress(keyPressData1);
            } else if(LastShortPressKey1 != null && IsSameKeyDownPress(LastShortPressKey1, keyPressData1)) {
                if(eventTime - LastShortPressKey1.KeyDownTime <= TIME_DOUBLE_PRESS) {
                    ProcessUndoLastShortPress(keyPressData1);
                    keyPressData1.DoublePressTime = eventTime;
                    LastDoublePressKey = keyPressData1;
                    ProcessDoublePress(keyPressData1);
                }
                else ProcessShortPress(keyPressData1);
            }
            return true;
        }
        if(keyProcessingMode.IsLetterShortDoubleLongPressMode()) {
            if(repeatCount1 == 0) {
                AnyHoldPlusButtonSignalTime = eventTime;

                KeyPressData keyPressData1 = CreateKeyPressData(keyCode, scanCode, keyDownTime, keyProcessingMode, event.getMetaState());
                KeyDownList1.add(keyPressData1);

                if (LastShortPressKey1 == null || !IsSameKeyDownPress(LastShortPressKey1, keyPressData1)) {
                    ProcessShortPress(keyPressData1);
                } else if (LastShortPressKey1 != null && IsSameKeyDownPress(LastShortPressKey1, keyPressData1)) {
                    if (eventTime - LastShortPressKey1.KeyDownTime <= TIME_DOUBLE_PRESS) {
                        keyPressData1.DoublePressTime = eventTime;
                        LastDoublePressKey = keyPressData1;
                        ProcessUndoLastShortPress(keyPressData1);
                        ProcessDoublePress(keyPressData1);
                    } else ProcessShortPress(keyPressData1);
                }
            } else {
                KeyPressData keyPressData2 = FindAtKeyDownList(keyCode, scanCode);
                if(keyPressData2.LongPressBeginTime == 0
                    && eventTime - keyPressData2.KeyDownTime > TIME_LONG_PRESS ) {
                    keyPressData2.LongPressBeginTime = eventTime;
                    if(IsSameKeyDownPress(LastShortPressKey1, keyPressData2)
                        && eventTime - LastShortPressKey1.KeyUpTime <= TIME_SHORT_2ND_LONG_PRESS) {
                        keyPressData2.Short2ndLongPress = true;
                    }
                    ProcessUndoLastShortPress(keyPressData2);
                    ProcessLongPress(keyPressData2);
                }

            }
            return true;
        }
        if(keyProcessingMode.IsMetaShortDoubleHoldPlusButtonPressMode()) {
            if(repeatCount1 == 0) {
                KeyPressData keyPressData1 = CreateKeyPressData(keyCode, scanCode, keyDownTime, keyProcessingMode, event.getMetaState());
                KeyDownList1.add(keyPressData1);

                if (LastShortPressKey1 != null
                    && IsSameKeyDownPress(LastShortPressKey1, keyPressData1)
                    && (eventTime - LastShortPressKey1.KeyDownTime <= TIME_DOUBLE_PRESS) ) {

                    keyPressData1.DoublePressTime = eventTime;

                    if(keyPressData1.KeyProcessingMode.OnTriplePress != null
                            && LastDoublePressKey != null
                            && IsSameKeyDownPress(LastDoublePressKey, keyPressData1)
                            && (LastShortPressKey1.KeyDownTime  - LastDoublePressKey.KeyDownTime <= TIME_DOUBLE_PRESS)) {
                        ProcessTriplePress(keyPressData1);
                    } else {
                        keyPressData1.DoublePressTime = eventTime;
                        LastDoublePressKey = keyPressData1;
                        ProcessUndoLastShortPress(keyPressData1);
                        ProcessDoublePress(keyPressData1);
                    }

                } else {
                    keyPressData1.HoldBeginTime = eventTime;
                    ProcessHoldBegin(keyPressData1);
                }
            }
            return true;
        }
        return true;
    }

    private KeyPressData CreateKeyPressData(int keyCode, int scanCode, long keyDownTime, KeyProcessingMode keyProcessingMode, int meta) {
        KeyPressData keyPressData1 = new KeyPressData();
        keyPressData1.KeyDownTime = keyDownTime;
        keyPressData1.KeyCode = keyCode;
        keyPressData1.ScanCode = scanCode;
        keyPressData1.KeyProcessingMode = keyProcessingMode;
        keyPressData1.MetaBase = meta;
        return keyPressData1;
    }

    protected boolean ProcessNewStatusModelOnKeyUp(int keyCode, KeyEvent event) {
        int scanCode = event.getScanCode();
        //long eventTime = SystemClock.uptimeMillis();
        long eventTime = event.getEventTime();
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyCode, scanCode);
        if(keyProcessingMode == null)
            return false;
        if (keyProcessingMode.IsShortPressOnly()) {
            return true;
        }
        if(keyProcessingMode.IsShortDoublePressMode()) {
            KeyPressData keyPressData = FindAtKeyDownList(keyCode, scanCode);
            if(keyPressData == null) {
                Log.w(TAG2, "NO KEY_DOWN AT KEYONEKB2. FOREIGN (?) KEY. KEY_CODE: "+keyCode+" IGNORING.");
                return false;
            }
            KeyDownList1.remove(keyPressData);
            if(eventTime - keyPressData.KeyDownTime <= TIME_SHORT_PRESS) {
                keyPressData.KeyUpTime = eventTime;
                LastShortPressKey1 = keyPressData;

            }
            return true;
        }
        if(keyProcessingMode.IsLetterShortDoubleLongPressMode()) {
            KeyPressData keyPressData = FindAtKeyDownList(keyCode, scanCode);
            if(keyPressData == null) {
                Log.w(TAG2, "NO KEY_DOWN AT KEYONEKB2. FOREIGN (?) KEY. KEY_CODE: "+keyCode+" IGNORING.");
                return false;
            }
            KeyDownList1.remove(keyPressData);
            if(eventTime - keyPressData.KeyDownTime <= TIME_SHORT_PRESS) {
                keyPressData.KeyUpTime = eventTime;
                LastShortPressKey1 = keyPressData;
            }
            return true;
        }
        if(keyProcessingMode.IsMetaShortDoubleHoldPlusButtonPressMode()) {
            KeyPressData keyPressData = FindAtKeyDownList(keyCode, scanCode);
            if(keyPressData == null) {
                Log.w(TAG2, "NO KEY_DOWN AT KEYONEKB2. FOREIGN (?) KEY. KEY_CODE: "+keyCode+" IGNORING.");
                return false;
            }

            KeyDownList1.remove(keyPressData);
            keyPressData.KeyUpTime = eventTime;
            if(eventTime - keyPressData.KeyDownTime <= TIME_SHORT_PRESS
                && !(AnyHoldPlusButtonSignalTime > keyPressData.KeyDownTime)
                && keyPressData.DoublePressTime == 0) {
                ProcessKeyUnhold(keyPressData);
                ProcessShortPress(keyPressData);
                LastShortPressKey1 = keyPressData;
            } else if (keyPressData.HoldBeginTime != 0) {
                ProcessKeyUnhold(keyPressData);
            }
            return true;
        }

        return true;
    }

    //Для нажатий, где нельзя себе позволить FAST_TRACK (OnKeyDown) реакцию (например откатывать OnShorPress нельзя)
    //final ExecutorService ExecutorService = Executors.newFixedThreadPool(2);
    private void ProcessShortPressIfNoDoublePress(KeyPressData keyPressData) {
        try {
            Thread.sleep(TIME_DOUBLE_PRESS - (keyPressData.KeyUpTime - keyPressData.KeyDownTime));
            long now = SystemClock.uptimeMillis();
            if(IsLastSameDoublePress(keyPressData.KeyCode, keyPressData.ScanCode)
                && LastDoublePress.DoublePressTime > keyPressData.KeyUpTime )
                   return;
            Log.e(TAG2, "ProcessShortPressIfNoDoublePress: NOW: "+now+" DELTA_UP_TIME "+ (now - keyPressData.KeyUpTime) + " DELTA_DOWN_TIME "+(now - keyPressData.KeyDownTime));
            LastShortPressKeyUpForDoublePress = keyPressData;
            keyPressData.KeyUpTime = now;
            ProcessShortPress(keyPressData);
        } catch (Exception ex) {}
    }

    private boolean IsLastSameDoublePress(int keyCode, int scanCode) {
        if(LastDoublePress == null)
            return false;
        return LastDoublePress.KeyCode == keyCode
                || LastDoublePress.ScanCode == scanCode;
    }

    KeyPressData FindAtKeyDownList(int keyCode, int scanCode) {
        for (KeyPressData keyCodeScanCode : KeyDownList1) {
            if (keyCodeScanCode.ScanCode == scanCode || keyCodeScanCode.KeyCode == keyCode)
                return keyCodeScanCode;
        }
        return null;
    }

    KeyProcessingMode FindAtKeyActionOptionList(KeyCodeScanCode keyCodeScanCode) {
        if(keyCodeScanCode == null) {
            Log.e(TAG2, "FindAtKeyActionOptionList: keyCodeScanCode == null");
            return null;
        }
        return FindAtKeyActionOptionList(keyCodeScanCode.KeyCode, keyCodeScanCode.ScanCode);
    }

    //TODO: Педелать на KeyValue
    KeyProcessingMode FindAtKeyActionOptionList(int keyCode, int scanCode) {
        for (KeyProcessingMode keyProcessingMode : keyProcessingModeList) {
            if(keyProcessingMode.KeyCodeScanCode == null && keyProcessingMode.KeyCodeArray == null){
                Log.e(TAG2, "KeyActionsOptionList contains keyActionsOption.KeyCodeScanCode == null && keyActionsOption.KeyCodeArray == null");
                continue;
            }
            if(keyProcessingMode.KeyCodeArray != null) {
                for (int keyCode1: keyProcessingMode.KeyCodeArray) {
                    if(keyCode1 == keyCode)
                        return keyProcessingMode;
                }
                continue;
            }
            if (keyProcessingMode.KeyCodeScanCode.ScanCode == scanCode
                    || keyProcessingMode.KeyCodeScanCode.KeyCode == keyCode) {
                return keyProcessingMode;
            }
        }
        return null;
    }

    private void LogD(String tag, String msg) {
        Log.d(tag, msg);
        if(IS_KEYBOARD_TEST) {
            DEBUG_TEXT += String.format("%s\r\n", msg);
        }
    }

    //region PROCESS_KEY_EVENT
    void ProcessHoldBegin(KeyPressData keyPressData) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData);
        if(keyProcessingMode != null && keyProcessingMode.OnHoldOn != null) {
            LogD(TAG2, String.format("%d HOLD_ON %d", keyPressData.HoldBeginTime, keyPressData.KeyCode));
            keyProcessingMode.OnHoldOn.Process(keyPressData);
        }
    }

    void ProcessLongPress(KeyPressData keyPressData) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData);
        if(keyProcessingMode != null && keyProcessingMode.OnLongPress != null) {
            if(keyPressData.Short2ndLongPress)
                LogD(TAG2, String.format("%d SHORT_2ND_LONG_PRESS %d", keyPressData.LongPressBeginTime, keyPressData.KeyCode));
            else
                LogD(TAG2, String.format("%d LONG_PRESS %d", keyPressData.LongPressBeginTime, keyPressData.KeyCode));
            keyProcessingMode.OnLongPress.Process(keyPressData);
        }
    }
    void ProcessKeyUnhold(KeyPressData keyPressData) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData);
        if(keyProcessingMode != null && keyProcessingMode.OnHoldOff != null) {
            LogD(TAG2, String.format("%d HOLD_OFF %d", keyPressData.KeyUpTime, keyPressData.KeyCode));
            keyProcessingMode.OnHoldOff.Process(keyPressData);
        }
    }

    void ProcessShortPress(KeyPressData keyPressData) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData);
        if(keyProcessingMode != null && keyProcessingMode.OnShortPress != null) {
            LogD(TAG2, String.format("%d SHORT_PRESS %d", keyPressData.KeyDownTime, keyPressData.KeyCode));
            keyProcessingMode.OnShortPress.Process(keyPressData);
        }
    }

    void ProcessDoublePress(KeyPressData keyPressData) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData);
        if(keyProcessingMode != null && keyProcessingMode.OnDoublePress != null) {
            LogD(TAG2, String.format("%d DOUBLE_PRESS %d", keyPressData.DoublePressTime, keyPressData.KeyCode));
            keyProcessingMode.OnDoublePress.Process(keyPressData);
        }
    }

    void ProcessTriplePress(KeyPressData keyPressData) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData);
        if(keyProcessingMode != null && keyProcessingMode.OnTriplePress != null) {
            LogD(TAG2, String.format("%d TRIPLE_PRESS %d", keyPressData.DoublePressTime, keyPressData.KeyCode));
            keyProcessingMode.OnTriplePress.Process(keyPressData);
        }
    }

    void ProcessUndoLastShortPress(KeyPressData keyPressData) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData);
        if(keyProcessingMode != null && keyProcessingMode.OnUndoShortPress != null) {
            Log.d(TAG2, "UNDO_SHORT_PRESS "+ keyPressData.KeyCode);
            keyProcessingMode.OnUndoShortPress.Process(keyPressData);
        }
    }

    //endregion


    interface Processable {
        boolean Process(KeyPressData keyPressData);
    }

    class KeyProcessingMode {
        public KeyCodeScanCode KeyCodeScanCode;
        public int[] KeyCodeArray;
        public Processable OnShortPress;
        public Processable OnDoublePress;
        public Processable OnTriplePress;
        public Processable OnLongPress;
        public Processable OnHoldOn;
        public Processable OnHoldOff;
        public Processable OnUndoShortPress;
        public boolean KeyHoldPlusKey;
        public boolean WaitForDoublePress;

        //Только короткое нажатие, если зажать - символ будет повторяться
        public boolean IsShortPressOnly() {
            if(OnDoublePress != null) return false;
            if(OnLongPress != null) return false;
            if(OnHoldOn != null) return false;
            if(OnHoldOff != null) return false;
            if(KeyHoldPlusKey) return false;
            if(OnShortPress == null) return false;
            return true;
        }

        //И короткое и двойное и длинное (зажатие уже не работает)
        public boolean IsLetterShortDoubleLongPressMode() {
            if(OnHoldOn != null) return false;
            if(OnHoldOff != null) return false;
            if(KeyHoldPlusKey) return false;

            return OnShortPress != null
                    && OnUndoShortPress != null
                    && OnDoublePress != null
                    && OnLongPress != null;
        }

        //Зажатие срабатывает сразу по keyDown а вот короткое нажатие по отжатию (если успел), двойное тоже работает
        public boolean IsMetaShortDoubleHoldPlusButtonPressMode() {
            if(OnLongPress != null) return false;
            return KeyHoldPlusKey
                    && OnShortPress != null
                    && OnUndoShortPress != null
                    && OnDoublePress != null
                    && OnHoldOn != null
                    && OnHoldOff != null;
        }

        //Одинарное и двойное нажате, если зажать будет печататься много
        public boolean IsShortDoublePressMode() {
            if(OnLongPress != null) return false;
            if(OnHoldOn != null) return false;
            if(OnHoldOff != null) return false;
            if(KeyHoldPlusKey) return false;
            return OnShortPress != null && OnDoublePress != null && OnUndoShortPress != null;
        }
    }

    class KeyCodeScanCode {
        public int KeyCode;
        public int ScanCode;
    }

    class KeyPressData extends KeyCodeScanCode {
        public long KeyDownTime = 0;
        public long LongPressBeginTime = 0;
        public long HoldBeginTime = 0;
        public long KeyUpTime = 0;
        public long DoublePressTime = 0;
        public boolean Short2ndLongPress = false;
        public KeyProcessingMode KeyProcessingMode;
        public int MetaBase = 0;
    }
}
