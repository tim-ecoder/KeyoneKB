package com.ai10.k12kb;

import android.annotation.SuppressLint;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class InputMethodServiceCoreKeyPress extends InputMethodService {

    public static final int[] KEY2_LATIN_ALPHABET_KEYS_CODES = new int[]{
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
    int TIME_TRIPLE_PRESS;
    int TIME_DOUBLE_PRESS;
    int TIME_SHORT_2ND_LONG_PRESS;
    int TIME_LONG_PRESS;
    int TIME_SHORT_PRESS;
    public static String DEBUG_TEXT = "";
    public static boolean IS_KEYBOARD_TEST = false;

    public static IDebugUpdate DEBUG_UPDATE;

    interface IDebugUpdate { void DebugUpdated(); }

    List<KeyPressData> KeyDownList1 = new ArrayList<>();

    public static final String TAG2 = "KeyoneKb2-IME";

    protected HashMap<Integer, KeyProcessingMode> mainModeKeyProcessorsMap = new HashMap<Integer, KeyProcessingMode>();
    protected HashMap<Integer, KeyProcessingMode> navKeyProcessorsMap = new HashMap<Integer, KeyProcessingMode>();
    KeyPressData LastShortPressKeyUpForDoublePress = null;
    KeyPressData LastDoublePress = null;


    KeyPressData LastShortPressKey1 = null;
    KeyPressData LastDoublePressKey = null;

    KeyoneKb2Settings.CoreKeyboardSettings CoreKeyboardSettings;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate() {
        super.onCreate();

        try {
            CoreKeyboardSettings = FileJsonUtils.DeserializeFromJsonApplyPatches(KeyoneKb2Settings.CoreKeyboardSettingsResFileName, new TypeReference<KeyoneKb2Settings.CoreKeyboardSettings>() {}, this);
        } catch (Exception e) {
            Log.e(TAG2, "onCreate exception: "+e);
            FileJsonUtils.LogErrorToGui("onCreate exception: "+e);
            throw new RuntimeException("onCreate exception: ", e);
        }
        TIME_TRIPLE_PRESS = CoreKeyboardSettings.TimeTriplePress;
        TIME_DOUBLE_PRESS = CoreKeyboardSettings.TimeDoublePress;
        TIME_SHORT_2ND_LONG_PRESS = CoreKeyboardSettings.TimeLongAfterShortPress;
        TIME_LONG_PRESS = CoreKeyboardSettings.TimeLongPress;
        TIME_SHORT_PRESS = CoreKeyboardSettings.TimeShortPress;
    }

    //region KEY_DOWN_UP

    //Если через него отправлять навигационные и прочие команды, не будет активироваться выделение виджета на рабочем столе
    protected void keyDownUpNoMetaKeepTouch(int keyEventCode, InputConnection ic) {
        keyDownUpKeepTouch(keyEventCode, ic, 0);
    }

    protected void ActionCustomKeyDownUpNoMetaKeepTouch(int keyEventCode) {
        keyDownUpKeepTouch(keyEventCode, getCurrentInputConnection(), 0);
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


    /**
     * Это (Флаги KeepTouch) нужно чтобы в Telegram не выбивало курсор при движении курсора
     * @param keyEventCode
     * @param ic
     * @param meta
     */
    protected static void keyDownUpKeepTouch(int keyEventCode, InputConnection ic, int meta) {
        keyDownUp(keyEventCode, ic, meta,KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);
    }



    protected static void keyDownUpMeta(int keyEventCode, InputConnection ic, int meta) {
        keyDownUp(keyEventCode, ic, meta,0);
    }

    //endregion

    boolean IsSameKeyDownPress(KeyPressData keyPressData1, KeyPressData keyPressData2) {
        return keyPressData1 != null && keyPressData2 != null
                && (keyPressData1.KeyCode == keyPressData2.KeyCode
                    || keyPressData1.ScanCode == keyPressData2.ScanCode);
    }

    protected boolean ProcessCoreOnKeyDown(int keyCode, KeyEvent keyEvent, HashMap<Integer, KeyProcessingMode> keyProcessingModeList1) {
        int scanCode = keyEvent.getScanCode();
        int repeatCount1 = keyEvent.getRepeatCount();
        long eventTime = keyEvent.getEventTime();
        long keyDownTime = keyEvent.getDownTime();

        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyCode, scanCode, keyProcessingModeList1);
        if (keyProcessingMode == null)
            return false;

        if(NowHoldingPlusKeyNotUndoneSinglePress != null && NowHoldingPlusKeyNotUndoneSinglePress.KeyCode != keyCode) {
            Log.d(TAG2, "FORCE UNDO SHORT PRESS OF HOLDING "+NowHoldingPlusKeyNotUndoneSinglePress.KeyCode+" UPON PRESS "+keyCode);
            ProcessUndoLastShortPress(NowHoldingPlusKeyNotUndoneSinglePress, keyEvent, mainModeKeyProcessorsMap);
            NowHoldingPlusKeyNotUndoneSinglePress = null;
        }

        //Только короткое нажатие - делаем действие сразу FAST_TRACK
        if (keyProcessingMode.IsShortPressOnly()) {
            //AnyHoldPlusButtonSignalTime = eventTime;

            KeyPressData keyPressData1 = CreateKeyPressData(keyCode, scanCode, keyDownTime, keyProcessingMode, keyEvent.getMetaState(), keyEvent);
            KeyDownList1.add(keyPressData1);
            ProcessShortPress(keyPressData1, keyEvent, keyProcessingModeList1);


            //return true;
        } else if(keyProcessingMode.IsShortDoublePressMode()) {

            KeyPressData keyPressData1 = CreateKeyPressData(keyCode, scanCode, keyDownTime, keyProcessingMode, keyEvent.getMetaState(), keyEvent);
            if(repeatCount1 == 0)
                KeyDownList1.add(keyPressData1);

            if(LastShortPressKey1 == null || !IsSameKeyDownPress(LastShortPressKey1, keyPressData1)) {
                ProcessShortPress(keyPressData1, keyEvent, keyProcessingModeList1);
            } else if(LastShortPressKey1 != null && IsSameKeyDownPress(LastShortPressKey1, keyPressData1)) {
                if(keyDownTime - LastShortPressKey1.KeyDownTime <= TIME_DOUBLE_PRESS) {
                    ProcessUndoLastShortPress(keyPressData1, keyEvent, keyProcessingModeList1);
                    keyPressData1.DoublePressTime = keyDownTime;
                    LastDoublePressKey = keyPressData1;
                    ProcessDoublePress(keyPressData1, keyEvent, keyProcessingModeList1);
                }
                else ProcessShortPress(keyPressData1, keyEvent, keyProcessingModeList1);
            }
            //return true;
        } else if(keyProcessingMode.IsLetterShortDoubleLongPressMode()) {
            if(repeatCount1 == 0) {

                KeyPressData keyPressData1 = CreateKeyPressData(keyCode, scanCode, keyDownTime, keyProcessingMode, keyEvent.getMetaState(), keyEvent);
                KeyDownList1.add(keyPressData1);
            /*
            TIME_SHORT_PRESS - время от нажатия кнопки(тапа) до отжатия (первый раз)
            TIME_DOUBLE_PRESS - время от нажатия кнопки(тапа) ПЕРВЫЙ раз до нажатия ВТОРОЙ раз
            TIME_TRIPLE_PRESS - время от нажатия кнопки(тапа) ВТОРОЙ раз до нажатия ТРЕТИЙ раз
             */
                if (LastShortPressKey1 == null
                        || !IsSameKeyDownPress(LastShortPressKey1, keyPressData1)) {
                    ProcessShortPress(keyPressData1, keyEvent, keyProcessingModeList1);
                } else {
                    if (keyPressData1.KeyProcessingMode.OnTriplePress != null
                            && LastDoublePressKey != null
                            && IsSameKeyDownPress(LastDoublePressKey, keyPressData1)
                            && (keyDownTime - LastDoublePressKey.KeyDownTime <= TIME_TRIPLE_PRESS)) {
                        ProcessTriplePress(keyPressData1, keyEvent, keyProcessingModeList1);
                    } else  if (LastShortPressKey1 != null
                            && IsSameKeyDownPress(LastShortPressKey1, keyPressData1)
                            && (keyDownTime - LastShortPressKey1.KeyDownTime <= TIME_DOUBLE_PRESS) ) {
                        keyPressData1.DoublePressTime = keyDownTime;
                        LastDoublePressKey = keyPressData1;
                        ProcessUndoLastShortPress(keyPressData1, keyEvent, keyProcessingModeList1);
                        ProcessDoublePress(keyPressData1, keyEvent, keyProcessingModeList1);
                    } else {
                        ProcessShortPress(keyPressData1, keyEvent, keyProcessingModeList1);
                    }
                }
            } else {
                KeyPressData keyPressData2 = FindAtKeyDownList(keyCode, scanCode);
                if(keyPressData2 == null) {
                    Log.e(TAG2, "keyPressData2 == null at IsLetterShortDoubleLongPressMode");
                    return true;
                }
                if(keyPressData2.LongPressBeginTime == 0
                        /** ВАЖНО! в случе торможения OS eventTime - идет с задержкой и получается SHORT->LONG_PRESS
                         * ПОЭТОМУ ЭТО ОТСЮДА УБРАНО (НО МОЖЕТ РАБОТЬ ПЛОХО БЕЗ ЭТОГО)*/
                    && eventTime - keyPressData2.KeyDownTime > TIME_LONG_PRESS
                ) {
                    keyPressData2.LongPressBeginTime = keyDownTime;
                    if(IsSameKeyDownPress(LastShortPressKey1, keyPressData2)
                        && keyDownTime - LastShortPressKey1.KeyUpTime <= TIME_SHORT_2ND_LONG_PRESS) {
                        keyPressData2.Short2ndLongPress = true;
                    }
                    ProcessUndoLastShortPress(keyPressData2, keyEvent, keyProcessingModeList1);
                    ProcessLongPress(keyPressData2, keyEvent, keyProcessingModeList1);
                }

            }
            //return true;
        } else if(keyProcessingMode.IsMetaShortDoubleHoldPlusButtonPressMode()) {
            if(repeatCount1 == 0) {
                KeyPressData keyPressData1 = CreateKeyPressData(keyCode, scanCode, keyDownTime, keyProcessingMode, keyEvent.getMetaState(), keyEvent);
                KeyDownList1.add(keyPressData1);

                if (keyPressData1.KeyProcessingMode.OnTriplePress != null
                        && LastDoublePressKey != null
                        && IsSameKeyDownPress(LastDoublePressKey, keyPressData1)
                        && (keyDownTime - LastDoublePressKey.KeyDownTime <= TIME_TRIPLE_PRESS)) {
                    NowHoldingPlusKeyNotUndoneSinglePress = null;
                    ProcessTriplePress(keyPressData1, keyEvent, keyProcessingModeList1);
                } else  if (LastShortPressKey1 != null
                        && IsSameKeyDownPress(LastShortPressKey1, keyPressData1)
                        && (keyDownTime - LastShortPressKey1.KeyDownTime <= TIME_DOUBLE_PRESS) ) {
                    keyPressData1.DoublePressTime = keyDownTime;
                    LastDoublePressKey = keyPressData1;
                    NowHoldingPlusKeyNotUndoneSinglePress = null;
                    ProcessUndoLastShortPress(keyPressData1, keyEvent, keyProcessingModeList1);
                    ProcessDoublePress(keyPressData1, keyEvent, keyProcessingModeList1);
                } else {
                    LastShortPressKey1 = keyPressData1;
                    NowHoldingPlusKeyNotUndoneSinglePress = keyPressData1;
                    ProcessShortPress(keyPressData1, keyEvent, keyProcessingModeList1);
                    ProcessHoldBegin(keyPressData1, keyEvent, keyProcessingModeList1);
                }
                //return true;
            } else { // Count > 0
                KeyPressData keyPressData1 = FindAtKeyDownList(keyCode, scanCode);
                if (eventTime - keyDownTime > TIME_SHORT_PRESS) {

                    if (keyPressData1.HoldBeginTime == 0) {
                        if(NowHoldingPlusKeyNotUndoneSinglePress != null) {
                            ProcessUndoLastShortPress(keyPressData1, keyEvent, keyProcessingModeList1);
                            NowHoldingPlusKeyNotUndoneSinglePress = null;
                        }
                        keyPressData1.HoldBeginTime = keyDownTime;
                    }

                }
            }
        }
        return true;
    }

    KeyPressData NowHoldingPlusKeyNotUndoneSinglePress = null;

    protected boolean ProcessCoreOnKeyUp(int keyCode, KeyEvent keyEvent, HashMap<Integer, KeyProcessingMode> keyProcessorsMap) {
        int scanCode = keyEvent.getScanCode();
        //long eventTime = SystemClock.uptimeMillis();
        long eventTime = keyEvent.getEventTime();
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyCode, scanCode, keyProcessorsMap);
        if(keyProcessingMode == null)
            return false;
        if (keyProcessingMode.IsShortPressOnly()) {
            KeyPressData keyPressData = FindAtKeyDownList(keyCode, scanCode);
            if(keyPressData == null) {
                Log.w(TAG2, "NO KEY_DOWN AT KEYONEKB2. FOREIGN (?) KEY. KEY_CODE: "+keyCode+" IGNORING.");
                return false;
            }
            RemoveFromKeyDownList(keyPressData);
            keyPressData.KeyUpTime = eventTime;
            LastShortPressKey1 = keyPressData;
            return true;
        }
        if(keyProcessingMode.IsShortDoublePressMode()) {
            KeyPressData keyPressData = FindAtKeyDownList(keyCode, scanCode);
            if(keyPressData == null) {
                Log.w(TAG2, "NO KEY_DOWN AT KEYONEKB2. FOREIGN (?) KEY. KEY_CODE: "+keyCode+" IGNORING.");
                return false;
            }
            RemoveFromKeyDownList(keyPressData);
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
            RemoveFromKeyDownList(keyPressData);
            keyPressData.KeyUpTime = eventTime;
            if(eventTime - keyPressData.KeyDownTime <= TIME_SHORT_PRESS
                && keyPressData.DoublePressTime == 0) {
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

            keyPressData.KeyUpTime = eventTime;
            RemoveFromKeyDownList(keyPressData);
            ProcessKeyUnhold(keyPressData, keyEvent, keyProcessorsMap);
            if(NowHoldingPlusKeyNotUndoneSinglePress != null && NowHoldingPlusKeyNotUndoneSinglePress.KeyCode == keyCode) {
                if(keyEvent.getEventTime() - keyPressData.KeyDownTime > TIME_LONG_PRESS) {
                    Log.d(TAG2, "FORCE UNDO SHORT PRESS OF HOLDING " + NowHoldingPlusKeyNotUndoneSinglePress.KeyCode + " UPON KEY_UP WHILE NO REPEAT_COUNT");
                    ProcessUndoLastShortPress(NowHoldingPlusKeyNotUndoneSinglePress, keyEvent, mainModeKeyProcessorsMap);
                }
                NowHoldingPlusKeyNotUndoneSinglePress = null;
            }
            return true;
        }

        return true;
    }

    private KeyPressData CreateKeyPressData(int keyCode, int scanCode, long keyDownTime, KeyProcessingMode keyProcessingMode, int meta, KeyEvent event) {
        KeyPressData keyPressData1 = new KeyPressData();
        keyPressData1.BaseKeyEvent = event;
        keyPressData1.KeyDownTime = keyDownTime;
        keyPressData1.KeyCode = keyCode;
        keyPressData1.ScanCode = scanCode;
        keyPressData1.KeyProcessingMode = keyProcessingMode;
        keyPressData1.MetaBase = meta;
        return keyPressData1;
    }

    private void RemoveFromKeyDownList(KeyPressData keyPressData) {
        KeyDownList1.remove(keyPressData);
        while(true) {
            KeyPressData kpd = FindAtKeyDownList(keyPressData.KeyCode, keyPressData.ScanCode);
            if (kpd != null)
                KeyDownList1.remove(kpd);
            else
                break;
        }
    }

    //Для нажатий, где нельзя себе позволить FAST_TRACK (OnKeyDown) реакцию (например откатывать OnShorPress нельзя)
    //final ExecutorService ExecutorService = Executors.newFixedThreadPool(2);
    private void ProcessShortPressIfNoDoublePress(KeyPressData keyPressData, KeyEvent keyEvent, HashMap<Integer, KeyProcessingMode> keyProcessingModeList1) {
        try {
            Thread.sleep(TIME_DOUBLE_PRESS - (keyPressData.KeyUpTime - keyPressData.KeyDownTime));
            long now = SystemClock.uptimeMillis();
            if(IsLastSameDoublePress(keyPressData.KeyCode, keyPressData.ScanCode)
                && LastDoublePress.DoublePressTime > keyPressData.KeyUpTime )
                   return;
            Log.e(TAG2, "ProcessShortPressIfNoDoublePress: NOW: "+now+" DELTA_UP_TIME "+ (now - keyPressData.KeyUpTime) + " DELTA_DOWN_TIME "+(now - keyPressData.KeyDownTime));
            LastShortPressKeyUpForDoublePress = keyPressData;
            keyPressData.KeyUpTime = now;
            ProcessShortPress(keyPressData, keyEvent, keyProcessingModeList1);
        } catch (Exception ex) {}
    }

    private boolean IsLastSameDoublePress(int keyCode, int scanCode) {
        if(LastDoublePress == null)
            return false;
        return LastDoublePress.KeyCode == keyCode
                || LastDoublePress.ScanCode == scanCode;
    }

    KeyPressData FindAtKeyDownList(int keyCode, int scanCode) {
        Collections.reverse(KeyDownList1);
        for (KeyPressData keyCodeScanCode : KeyDownList1) {
            if (keyCodeScanCode.KeyCode == keyCode || keyCodeScanCode.ScanCode == scanCode)
                return keyCodeScanCode;
        }
        return null;
    }

    KeyProcessingMode FindAtKeyActionOptionList(KeyCodeScanCode keyCodeScanCode, HashMap<Integer, KeyProcessingMode> keyProcessingModeList1) {
        if(keyCodeScanCode == null) {
            Log.e(TAG2, "FindAtKeyActionOptionList: keyCodeScanCode == null");
            return null;
        }
        return FindAtKeyActionOptionList(keyCodeScanCode.KeyCode, keyCodeScanCode.ScanCode, keyProcessingModeList1);
    }

    static KeyProcessingMode FindAtKeyActionOptionList(int keyCode, int scanCode, HashMap<Integer, KeyProcessingMode> keyProcessingModeList1) {
        return keyProcessingModeList1.get(keyCode);
    }

    protected void LogKeyboardTest(String tag, String msg) {
        Log.d(tag, msg);
        if(IS_KEYBOARD_TEST) {
            DEBUG_TEXT += String.format("%s\r\n", msg);
            if(DEBUG_UPDATE != null)
                DEBUG_UPDATE.DebugUpdated();
        }
    }

    protected void LogKeyboardTest(String msg) {
        LogKeyboardTest(TAG2, msg);
    }

    //region PROCESS_KEY_EVENT

    protected KeyProcessingMode AnyKeyBeforeAction;

    void ProcessHoldBegin(KeyPressData keyPressData, KeyEvent keyEvent, HashMap<Integer, KeyProcessingMode> keyProcessingModeList1) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData, keyProcessingModeList1);
        if(keyProcessingMode != null && keyProcessingMode.OnHoldOn != null) {
            LogKeyboardTest(TAG2, String.format("[%d] HOLD_ON %d", keyPressData.HoldBeginTime, keyPressData.KeyCode));
            if(AnyKeyBeforeAction != null && AnyKeyBeforeAction.OnHoldOn != null)
                AnyKeyBeforeAction.OnHoldOn.Process(keyPressData, keyEvent);
            keyProcessingMode.OnHoldOn.Process(keyPressData, keyEvent);
        }
    }

    void ProcessLongPress(KeyPressData keyPressData, KeyEvent keyEvent, HashMap<Integer, KeyProcessingMode> keyProcessingModeList1) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData, keyProcessingModeList1);
        if(keyProcessingMode != null && keyProcessingMode.OnLongPress != null) {
            if(keyPressData.Short2ndLongPress)
                LogKeyboardTest(TAG2, String.format("[%d] SHORT_2ND_LONG_PRESS %d", keyPressData.LongPressBeginTime, keyPressData.KeyCode));
            else
                LogKeyboardTest(TAG2, String.format("[%d] LONG_PRESS %d", keyPressData.LongPressBeginTime, keyPressData.KeyCode));
            if(AnyKeyBeforeAction != null && AnyKeyBeforeAction.OnLongPress != null)
                AnyKeyBeforeAction.OnLongPress.Process(keyPressData, keyEvent);
            keyProcessingMode.OnLongPress.Process(keyPressData, keyEvent);
        }
    }
    void ProcessKeyUnhold(KeyPressData keyPressData, KeyEvent keyEvent, HashMap<Integer, KeyProcessingMode> keyProcessingModeList1) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData, keyProcessingModeList1);
        if(keyProcessingMode != null && keyProcessingMode.OnHoldOff != null) {
            LogKeyboardTest(TAG2, String.format("[%d] HOLD_OFF %d HOLD_BEGAN %d", keyPressData.KeyUpTime, keyPressData.KeyCode, keyPressData.HoldBeginTime));
            if(AnyKeyBeforeAction != null && AnyKeyBeforeAction.OnHoldOff != null)
                AnyKeyBeforeAction.OnHoldOff.Process(keyPressData, keyEvent);
            keyProcessingMode.OnHoldOff.Process(keyPressData, keyEvent);
        }
    }

    void ProcessShortPress(KeyPressData keyPressData, KeyEvent keyEvent, HashMap<Integer, KeyProcessingMode> keyProcessingModeList1) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData, keyProcessingModeList1);
        if(keyProcessingMode != null && keyProcessingMode.OnShortPress != null) {
            LogKeyboardTest(TAG2, String.format("[%d] SHORT_PRESS %d", keyPressData.KeyDownTime, keyPressData.KeyCode));
            if(AnyKeyBeforeAction != null && AnyKeyBeforeAction.OnShortPress != null)
                AnyKeyBeforeAction.OnShortPress.Process(keyPressData, keyEvent);
            keyProcessingMode.OnShortPress.Process(keyPressData, keyEvent);
        }
    }

    void ProcessDoublePress(KeyPressData keyPressData, KeyEvent keyEvent, HashMap<Integer, KeyProcessingMode> keyProcessingModeList1) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData, keyProcessingModeList1);
        if(keyProcessingMode != null && keyProcessingMode.OnDoublePress != null) {
            LogKeyboardTest(TAG2, String.format("[%d] DOUBLE_PRESS %d", keyPressData.DoublePressTime, keyPressData.KeyCode));
            if(AnyKeyBeforeAction != null && AnyKeyBeforeAction.OnDoublePress != null)
                AnyKeyBeforeAction.OnDoublePress.Process(keyPressData, keyEvent);
            keyProcessingMode.OnDoublePress.Process(keyPressData, keyEvent);
        }
    }

    void ProcessTriplePress(KeyPressData keyPressData, KeyEvent keyEvent, HashMap<Integer, KeyProcessingMode> keyProcessingModeList1) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData, keyProcessingModeList1);
        if(keyProcessingMode != null && keyProcessingMode.OnTriplePress != null) {
            LogKeyboardTest(TAG2, String.format("[%d] TRIPLE_PRESS %d", keyPressData.DoublePressTime, keyPressData.KeyCode));
            if(AnyKeyBeforeAction != null && AnyKeyBeforeAction.OnTriplePress != null)
                AnyKeyBeforeAction.OnTriplePress.Process(keyPressData, keyEvent);
            keyProcessingMode.OnTriplePress.Process(keyPressData, keyEvent);
        }
    }

    void ProcessUndoLastShortPress(KeyPressData keyPressData, KeyEvent keyEvent, HashMap<Integer, KeyProcessingMode> keyProcessingModeList1) {
        KeyProcessingMode keyProcessingMode = FindAtKeyActionOptionList(keyPressData, keyProcessingModeList1);
        if(keyProcessingMode != null && keyProcessingMode.OnUndoShortPress != null) {
            LogKeyboardTest(TAG2, String.format("[%d] UNDO_SHORT_PRESS %d", keyPressData.KeyDownTime, keyPressData.KeyCode));
            if(AnyKeyBeforeAction != null && AnyKeyBeforeAction.OnUndoShortPress != null)
                AnyKeyBeforeAction.OnUndoShortPress.Process(keyPressData, keyEvent);
            keyProcessingMode.OnUndoShortPress.Process(keyPressData, keyEvent);
        }
    }

    //endregion


    interface Processable {
        boolean Process(KeyPressData keyPressData, KeyEvent keyEvent);

    }



    class KeyProcessingMode {
        public Processable OnShortPress;
        public Processable OnDoublePress;
        public Processable OnTriplePress;
        public Processable OnLongPress;
        public Processable OnHoldOn;
        public Processable OnHoldOff;
        public Processable OnUndoShortPress;

        //Только короткое нажатие, если зажать - символ будет повторяться
        public boolean IsShortPressOnly() {
            if(OnDoublePress != null) return false;
            if(OnLongPress != null) return false;
            if(OnHoldOn != null) return false;
            if(OnHoldOff != null) return false;
            if(OnShortPress == null) return false;
            return true;
        }

        //И короткое и двойное и длинное (зажатие уже не работает)
        public boolean IsLetterShortDoubleLongPressMode() {
            if(OnHoldOn != null) return false;
            if(OnHoldOff != null) return false;

            return OnShortPress != null
                    //&& OnUndoShortPress != null
                    && OnDoublePress != null
                    && OnLongPress != null;
        }

        //Зажатие срабатывает сразу по keyDown а вот короткое нажатие по отжатию (если успел), двойное тоже работает
        public boolean IsMetaShortDoubleHoldPlusButtonPressMode() {
            if(OnLongPress != null) return false;
            return
                       OnShortPress != null
                    //&& OnUndoShortPress != null
                    && OnDoublePress != null
                    && OnHoldOn != null
                    && OnHoldOff != null;
        }

        //Одинарное и двойное нажате, если зажать будет печататься много
        public boolean IsShortDoublePressMode() {
            if(OnLongPress != null) return false;
            if(OnHoldOn != null) return false;
            if(OnHoldOff != null) return false;
            return OnShortPress != null && OnDoublePress != null;// && OnUndoShortPress != null;
        }
    }

    class KeyCodeScanCode {
        public int KeyCode;
        public int ScanCode;
    }

    class KeyPressData extends KeyCodeScanCode {
        private KeyPressData() {}
        public long KeyDownTime = 0;

        public KeyEvent BaseKeyEvent;
        public long LongPressBeginTime = 0;
        public long HoldBeginTime = 0;
        public long KeyUpTime = 0;
        public long DoublePressTime = 0;
        public boolean Short2ndLongPress = false;
        public KeyProcessingMode KeyProcessingMode;
        public int MetaBase = 0;
    }


    protected boolean ActionKeyDownUpDefaultFlags(int keyEventCode) {
        keyDownUpDefaultFlags(keyEventCode, getCurrentInputConnection());
        return true;
    }

    protected boolean ActionKeyDown(int customKeyCode) {
        InputConnection inputConnection = getCurrentInputConnection();
        if(inputConnection!=null)
            inputConnection.sendKeyEvent(new KeyEvent(   KeyEvent.ACTION_DOWN, customKeyCode));
        return true;
    }


}
