package com.sateda.keyonekb2;

import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;


public class KeyboardBaseKeyLogic extends InputMethodService {

    int TIME_DOUBLE_PRESS = 400;
    int TIME_SHORT_2ND_LONG_PRESS = 600;
    int TIME_LONG_PRESS = 300;
    int TIME_SHORT_PRESS = 200;

    List<KeyPressData> KeyDownList1 = new ArrayList<>();

    long AnyHoldPlusButtonSignalTime = 0;

    public static final String TAG2 = "KeyoneKb2";

    protected List<KeyProcessingMode> keyProcessingModeList = new ArrayList<>();
    KeyPressData LastShortPressKeyUpForDoublePress = null;
    KeyPressData LastDoublePress = null;


    KeyPressData LastShortPressKey1 = null;

    boolean IsSameKeyDownPress(KeyPressData keyPressData1, KeyPressData keyPressData2) {
        return keyPressData1 != null && keyPressData2 != null
                && (keyPressData1.KeyCode == keyPressData2.KeyCode
                    || keyPressData1.ScanCode == keyPressData2.ScanCode);
    }

    protected boolean ProcessNewStatusModelOnKeyDown(int keyCode, KeyEvent event) {
        int scanCode = event.getScanCode();
        int repeatCount1 = event.getRepeatCount();
        //long eventTime = System.currentTimeMillis();
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
                    ProcessUndoLastShortPress(keyPressData1);
                    ProcessDoublePress(keyPressData1);

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

    void ProcessHoldBegin(KeyPressData keyPressData) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData);
        if(keyProcessingMode != null && keyProcessingMode.OnHoldOn != null) {
            Log.d(TAG2, "HOLD_ON "+ keyPressData.KeyCode);
            keyProcessingMode.OnHoldOn.Process(keyPressData);
        }
    }

    void ProcessLongPress(KeyPressData keyPressData) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData);
        if(keyProcessingMode != null && keyProcessingMode.OnLongPress != null) {
            Log.d(TAG2, "LONG_PRESS "+ keyPressData.KeyCode);
            keyProcessingMode.OnLongPress.Process(keyPressData);
        }
    }
    void ProcessKeyUnhold(KeyPressData keyPressData) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData);
        if(keyProcessingMode != null && keyProcessingMode.OnHoldOff != null) {
            Log.d(TAG2, "HOLD_OFF "+ keyPressData.KeyCode);
            keyProcessingMode.OnHoldOff.Process(keyPressData);
        }
    }

    void ProcessShortPress(KeyPressData keyPressData) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData);
        if(keyProcessingMode != null && keyProcessingMode.OnShortPress != null) {
            Log.d(TAG2, "SHORT_PRESS "+ keyPressData.KeyCode);
            keyProcessingMode.OnShortPress.Process(keyPressData);
        }
    }

    void ProcessDoublePress(KeyPressData keyPressData) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData);
        if(keyProcessingMode != null && keyProcessingMode.OnDoublePress != null) {
            Log.d(TAG2, "DOUBLE_PRESS "+ keyPressData.KeyCode);
            keyProcessingMode.OnDoublePress.Process(keyPressData);
        }
    }

    void ProcessUndoLastShortPress(KeyPressData keyPressData) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData);
        if(keyProcessingMode != null && keyProcessingMode.OnUndoShortPress != null) {
            Log.d(TAG2, "UNDO_SHORT_PRESS "+ keyPressData.KeyCode);
            keyProcessingMode.OnUndoShortPress.Process(keyPressData);
        }
    }

    interface Processable {
        boolean Process(KeyPressData keyPressData);
    }

    class KeyProcessingMode {
        public KeyCodeScanCode KeyCodeScanCode;
        public int[] KeyCodeArray;
        public Processable OnShortPress;
        public Processable OnDoublePress;
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
