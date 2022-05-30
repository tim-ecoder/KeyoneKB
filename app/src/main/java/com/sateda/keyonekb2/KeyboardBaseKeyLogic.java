package com.sateda.keyonekb2;

import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class KeyboardBaseKeyLogic extends InputMethodService {

    int TIME_DOUBLE_PRESS = 400;
    int TIME_LONG_PRESS = 300;

    List<KeyDownPress> KeyHoldList = new ArrayList<>();
    List<KeyDownPress> KeyDownList = new ArrayList<>();
    List<KeyDownPress> KeyHoldPlusDownList = new ArrayList<>();

    public static final String TAG2 = "KeyoneKb2";

    protected List<KeyActionsOption> KeyActionsOptionList = new ArrayList<>();
    KeyDownPress LastShortPressKeyUpForDoublePress = null;
    KeyDownPress LastPressKeyDown = null;
    KeyDownPress LastDoublePress = null;
    final ExecutorService ExecutorService = Executors.newFixedThreadPool(2);

    public KeyboardBaseKeyLogic() {
        //Executors.prestartAllCoreThreads();
    }


    protected boolean ProcessNewStatusModelOnKeyDown(int keyCode, KeyEvent event) {
        int scanCode = event.getScanCode();
        int repeatCount1 = event.getRepeatCount();
        long now2 = System.currentTimeMillis();

        KeyActionsOption keyActionsOption = FindAtKeyActionOptionList(keyCode, scanCode);
        if (keyActionsOption == null)
            return false;
        //Только короткое нажатие - делаем действие сразу FAST_TRACK
        //TODO: Поискать может быть так не бывает
        if (IsShortPressFastTrack(keyActionsOption)) {
            KeyDownPress keyDownPress1 = new KeyDownPress();
            keyDownPress1.KeyDownTime = now2;
            keyDownPress1.KeyCode = keyCode;
            keyDownPress1.ScanCode = scanCode;
            keyDownPress1.KeyActionsOption = keyActionsOption;
            LastShortPressKeyUpForDoublePress = keyDownPress1;
            LastPressKeyDown = keyDownPress1;
            ProcessShortPress(keyDownPress1);
            return true;
        }

        //Логика для KeyHoldPlusKey
        if(repeatCount1 >= 0 && keyActionsOption.KeyHoldPlusKey)
        {
            if(repeatCount1 > 0)
                return true;
            KeyDownPress keyDownPress1 = new KeyDownPress();
            keyDownPress1.KeyDownTime = now2;
            keyDownPress1.KeyCode = keyCode;
            keyDownPress1.ScanCode = scanCode;
            keyDownPress1.KeyActionsOption = keyActionsOption;
            LastPressKeyDown = keyDownPress1;

            //Потом разделить
            if(keyActionsOption.OnShortPress != null
            || keyActionsOption.OnDoublePress != null
            || keyActionsOption.OnLongPress != null) {
                KeyHoldPlusDownList.add(keyDownPress1);
            }
            //TODO: HoldOn and Off only if not key_up and not long_press and not double_press
            if(keyActionsOption.OnHoldOn != null) {
                keyDownPress1.HoldBeginTime = now2;
                ProcessHoldBegin(keyDownPress1);
            }
            return true;
        }
        if (repeatCount1 == 0 && keyActionsOption.OnShortPress != null) {
            KeyDownPress keyDownPress1 = new KeyDownPress();
            keyDownPress1.KeyDownTime = now2;
            keyDownPress1.KeyCode = keyCode;
            keyDownPress1.ScanCode = scanCode;
            keyDownPress1.KeyActionsOption = keyActionsOption;
            LastPressKeyDown = keyDownPress1;
            KeyDownList.add(keyDownPress1);
        } else if (repeatCount1 > 0) {
            KeyDownPress keyDownPress = FindAtKeyDownList(keyCode, scanCode);
            if (keyDownPress == null) {
                Log.e(TAG2, "onKeyDown: repeatCount() > 1; KeyDownPress not found.");
            }
            //TODO: Сделать fast-track для только зажимаемых
            if (repeatCount1 == 1) {
                if (keyActionsOption.OnHoldOn != null) {
                    keyDownPress.HoldBeginTime = now2;
                    KeyHoldList.add(keyDownPress);
                    ProcessHoldBegin(keyDownPress);
                } else if (keyActionsOption.OnHoldOff != null) {
                    keyDownPress.HoldBeginTime = now2;
                    KeyHoldList.add(keyDownPress);
                }
            }
            //repeatCount1 > 2
            else {
                if (keyDownPress.LongPressBeginTime == 0 && keyActionsOption.OnLongPress != null) {
                    if (keyDownPress.KeyDownTime + TIME_LONG_PRESS <= now2) {
                        keyDownPress.LongPressBeginTime = now2;
                        ProcessLongPress(keyDownPress);
                    }
                }
            }
        } else {
            Log.e(TAG2, "onKeyDown: repeatCount() <= 0");
        }
        return true;
    }

    protected boolean ProcessNewStatusModelOnKeyUp(int keyCode, KeyEvent event) {
        int scanCode = event.getScanCode();
        long now = System.currentTimeMillis();
        KeyActionsOption keyActionsOption = FindAtKeyActionOptionList(keyCode, scanCode);
        if(keyActionsOption == null)
            return false;
        if (IsShortPressFastTrack(keyActionsOption))
            return true;

        //Логика для KeyHoldPlusKey
        if(keyActionsOption.KeyHoldPlusKey) {
            KeyDownPress keyDownPress = FindAtKeyHoldPlusDownList(keyCode, scanCode);
            if(keyDownPress == null) {
                Log.e(TAG2, "ProcessNewStatusModelOnKeyUp: FindAtKeyHoldPlusDownList(keyCode, scanCode) == null");
                return true;
            }
            KeyHoldPlusDownList.remove(keyDownPress);
            //Если ничего не было нажато после этого
            if(IsLastKeyDownSame(keyCode, scanCode)) {
                if (keyActionsOption.OnDoublePress != null
                        && IsLastShortPressedSame(keyDownPress)
                        && now - LastShortPressKeyUpForDoublePress.KeyDownTime <= TIME_DOUBLE_PRESS) {
                    keyDownPress.DoublePressTime = now;
                    keyDownPress.KeyUpTime = now;
                    ProcessDoublePressKeyUp(keyDownPress);
                    LastDoublePress = keyDownPress;
                    return true;
                }
                if (keyActionsOption.OnShortPress != null && now - keyDownPress.KeyDownTime <= TIME_LONG_PRESS) {
                    if(!keyActionsOption.WaitForDoublePress) {
                        LastShortPressKeyUpForDoublePress = keyDownPress;
                        keyDownPress.KeyUpTime = now;
                        ProcessShortPress(keyDownPress);
                    } else {
                        LastShortPressKeyUpForDoublePress = keyDownPress;
                        keyDownPress.KeyUpTime = now;
                        Log.e(TAG2, "NOW: "+now+" DELTA KEY_DOWN: "+(now-keyDownPress.KeyDownTime));
                        ExecutorService.execute(() ->ProcessShortPressIfNoDoublePress(keyDownPress));
                    }
                }
                if (keyActionsOption.OnLongPress != null && now - keyDownPress.KeyDownTime > TIME_LONG_PRESS) {
                    //LongPressKeyUp ???
                    ProcessLongPress(keyDownPress);
                    return true;
                }
                if(keyActionsOption.OnHoldOff != null) {
                    ProcessKeyUnhold(keyDownPress);
                }
            } else {
                //А если что-то было нажато то надо выйти из режима холда
                if(keyActionsOption.OnHoldOff != null) {
                    ProcessKeyUnhold(keyDownPress);
                }
            }
            return true;
        }

        KeyDownPress keyDownPress = FindAtKeyDownList(keyCode, scanCode);
        if (keyDownPress == null) {
            Log.e(TAG2, "onKeyUp: ERROR NOT FOUND KEY DOWN");
        }

        if (keyDownPress != null) {
            keyDownPress.KeyUpTime = System.currentTimeMillis();
            KeyDownList.remove(keyDownPress);
            if (keyDownPress.LongPressBeginTime == 0 || keyDownPress.KeyDownTime + TIME_LONG_PRESS > now) {
                if (LastShortPressKeyUpForDoublePress != null
                        && IsLastShortPressedSame(keyDownPress)
                        && keyDownPress.KeyDownTime - LastShortPressKeyUpForDoublePress.KeyDownTime < TIME_DOUBLE_PRESS) {
                    keyDownPress.DoublePressTime = now;
                    keyDownPress.KeyUpTime = now;
                    ProcessDoublePressKeyUp(keyDownPress);
                    LastDoublePress = keyDownPress;

                } else {
                    if(!keyActionsOption.WaitForDoublePress) {
                        LastShortPressKeyUpForDoublePress = keyDownPress;
                        keyDownPress.KeyUpTime = now;
                        ProcessShortPress(keyDownPress);
                    } else {
                        LastShortPressKeyUpForDoublePress = keyDownPress;
                        keyDownPress.KeyUpTime = now;
                        ExecutorService.execute(() ->ProcessShortPressIfNoDoublePress(keyDownPress));
                    }
                }
            } else {
                //ProcessLongPressKeyUp(keyDownPress);
            }
        }

        KeyDownPress keyDownPress1 = FindAtHoldKeyList(keyCode, scanCode);
        if (keyDownPress1 != null) {
            KeyHoldList.remove(keyDownPress1);
            ProcessKeyUnhold(keyDownPress1);
        }
        return true;
    }

    private void ProcessShortPressIfNoDoublePress(KeyDownPress keyDownPress) {
        try {
            Thread.sleep(TIME_DOUBLE_PRESS - (keyDownPress.KeyUpTime - keyDownPress.KeyDownTime));
            long now = System.currentTimeMillis();
            if(IsLastSameDoublePress(keyDownPress.KeyCode, keyDownPress.ScanCode)
            && LastDoublePress.DoublePressTime > keyDownPress.KeyUpTime )
               return;
            Log.e(TAG2, "ProcessShortPressIfNoDoublePress: NOW: "+now+" DELTA_UP_TIME "+ (now - keyDownPress.KeyUpTime) + " DELTA_DOWN_TIME "+(now - keyDownPress.KeyDownTime));
            LastShortPressKeyUpForDoublePress = keyDownPress;
            keyDownPress.KeyUpTime = now;
            ProcessShortPress(keyDownPress);
        } catch (Exception ex) {}
    }

    private boolean IsLastShortPressedSame(KeyDownPress keyDownPress) {
        if(LastShortPressKeyUpForDoublePress == null)
            return false;
        return IsLastShortPressedSame(keyDownPress.KeyCode,keyDownPress.ScanCode);
    }

    private boolean IsLastShortPressedSame(int keyCode, int scanCode) {
        if(LastShortPressKeyUpForDoublePress == null)
            return false;
        return LastShortPressKeyUpForDoublePress.KeyCode == keyCode
                || LastShortPressKeyUpForDoublePress.ScanCode == scanCode;
    }

    private boolean IsLastKeyDownSame(int keyCode, int scanCode) {
        if(LastPressKeyDown == null)
            return false;
        return LastPressKeyDown.KeyCode == keyCode
                || LastPressKeyDown.ScanCode == scanCode;
    }

    private boolean IsLastSameDoublePress(int keyCode, int scanCode) {
        if(LastDoublePress == null)
            return false;
        return LastDoublePress.KeyCode == keyCode
                || LastDoublePress.ScanCode == scanCode;
    }

    KeyDownPress FindAtKeyDownList(int keyCode, int scanCode) {
        for (KeyDownPress keyCodeScanCode : KeyDownList) {
            if (keyCodeScanCode.ScanCode == scanCode || keyCodeScanCode.KeyCode == keyCode)
                return keyCodeScanCode;
        }
        return null;
    }

    KeyDownPress FindAtKeyHoldPlusDownList(int keyCode, int scanCode) {
        for (KeyDownPress keyCodeScanCode : KeyHoldPlusDownList) {
            if (keyCodeScanCode.ScanCode == scanCode || keyCodeScanCode.KeyCode == keyCode)
                return keyCodeScanCode;
        }
        return null;
    }


    private static boolean IsShortPressFastTrack(KeyActionsOption keyActionsOption) {
        return keyActionsOption.OnShortPress != null
                && keyActionsOption.OnLongPress == null
                && keyActionsOption.OnDoublePress == null
                && keyActionsOption.OnHoldOn == null
                && keyActionsOption.OnHoldOff == null;
    }

    KeyDownPress FindAtHoldKeyList(int keyCode, int scanCode) {
        for (KeyDownPress keyDownPress : KeyHoldList) {
            if (keyDownPress.ScanCode == scanCode || keyDownPress.KeyCode == keyCode)
                return keyDownPress;
        }
        return null;
    }

    //TODO: Удалить
    Pair<KeyActionsOption, Processable> FindAtKeyActionOptionList(int keyCode, int scanCode, KeyPressType keyPressType) {
        KeyActionsOption keyActionsOption = FindAtKeyActionOptionList(keyCode, scanCode);
        if (keyActionsOption == null)
            return null;
        Processable delegate = null;
        delegate = GetKeyProcessor(keyPressType, keyActionsOption);
        if (delegate != null) {
            Pair<KeyActionsOption, Processable> pair = new Pair<>(keyActionsOption, delegate);
            return pair;
        }
        return null;
    }

    KeyActionsOption FindAtKeyActionOptionList(KeyCodeScanCode keyCodeScanCode) {
        if(keyCodeScanCode == null) {
            Log.e(TAG2, "FindAtKeyActionOptionList: keyCodeScanCode == null");
            return null;
        }
        return FindAtKeyActionOptionList(keyCodeScanCode.KeyCode, keyCodeScanCode.ScanCode);
    }

    KeyActionsOption FindAtKeyActionOptionList(int keyCode, int scanCode) {
        for (KeyActionsOption keyActionsOption : KeyActionsOptionList) {
            if(keyActionsOption.KeyCodeScanCode == null && keyActionsOption.KeyCodeArray == null){
                Log.e(TAG2, "KeyActionsOptionList contains keyActionsOption.KeyCodeScanCode == null && keyActionsOption.KeyCodeArray == null");
                continue;
            }
            if(keyActionsOption.KeyCodeArray != null) {
                for (int keyCode1: keyActionsOption.KeyCodeArray) {
                    if(keyCode1 == keyCode)
                        return keyActionsOption;
                }
                continue;
            }
            if (keyActionsOption.KeyCodeScanCode.ScanCode == scanCode
                    || keyActionsOption.KeyCodeScanCode.KeyCode == keyCode) {
                return keyActionsOption;
            }
        }
        return null;
    }

    private Processable GetKeyProcessor(KeyPressType keyPressType, KeyActionsOption keyActionsOption) {
        Processable delegate = null;
        switch (keyPressType) {
            case ShortPress:
                if (keyActionsOption.OnShortPress != null)
                    delegate = keyActionsOption.OnShortPress;
                break;
            case LongPress:
                if (keyActionsOption.OnLongPress != null)
                    delegate = keyActionsOption.OnLongPress;
                break;
            case DoublePress:
                if (keyActionsOption.OnDoublePress != null)
                    delegate = keyActionsOption.OnDoublePress;
                break;
            case HoldOn:
                if (keyActionsOption.OnHoldOn != null)
                    delegate = keyActionsOption.OnHoldOn;
                break;
            case HoldOff:
                if (keyActionsOption.OnHoldOff != null)
                    delegate = keyActionsOption.OnHoldOff;
                break;
            default:
                delegate = null;
        }
        return delegate;
    }

    void ProcessHoldBegin(KeyDownPress keyDownPress) {
        KeyActionsOption keyActionsOption = FindAtKeyActionOptionList(keyDownPress);
        if(keyActionsOption != null && keyActionsOption.OnHoldOn != null) {
            Log.d(TAG2, "HOLD_ON "+keyDownPress.KeyCode);
            keyActionsOption.OnHoldOn.Process(keyDownPress);
        }
    }

    void ProcessLongPress(KeyDownPress keyDownPress) {
        KeyActionsOption keyActionsOption = FindAtKeyActionOptionList(keyDownPress);
        if(keyActionsOption != null && keyActionsOption.OnLongPress != null) {
            Log.d(TAG2, "LONG_PRESS "+keyDownPress.KeyCode);
            keyActionsOption.OnLongPress.Process(keyDownPress);
        }
    }
    void ProcessKeyUnhold(KeyDownPress keyDownPress) {
        KeyActionsOption keyActionsOption = FindAtKeyActionOptionList(keyDownPress);
        if(keyActionsOption != null && keyActionsOption.OnHoldOff != null) {
            Log.d(TAG2, "HOLD_OFF "+keyDownPress.KeyCode);
            keyActionsOption.OnHoldOff.Process(keyDownPress);
        }
    }

    //TODO: Отделить эвент просто нажатия, от долго нажатия
    void ProcessShortPress(KeyDownPress keyDownPress) {
        KeyActionsOption keyActionsOption = FindAtKeyActionOptionList(keyDownPress);
        if(keyActionsOption != null && keyActionsOption.OnShortPress != null) {
            Log.d(TAG2, "SHORT_PRESS "+keyDownPress.KeyCode);
            keyActionsOption.OnShortPress.Process(keyDownPress);
        }
    }

    //void ProcessLongPressKeyUp(KeyDownPress keyDownPress) {    }

    void ProcessDoublePressKeyUp(KeyDownPress keyDownPress) {
        KeyActionsOption keyActionsOption = FindAtKeyActionOptionList(keyDownPress);
        if(keyActionsOption != null && keyActionsOption.OnDoublePress != null) {
            Log.d(TAG2, "DOUBLE_PRESS "+keyDownPress.KeyCode);
            keyActionsOption.OnDoublePress.Process(keyDownPress);
        }
    }

    enum KeyPressType {
        ShortPress,
        DoublePress,
        LongPress,
        HoldOn,
        HoldOff,
    }

    interface Processable {
        boolean Process(KeyDownPress keyDownPress);
    }

    class KeyActionsOption {
        public KeyCodeScanCode KeyCodeScanCode;
        public int[] KeyCodeArray;
        public Processable OnShortPress;
        public Processable OnDoublePress;
        public Processable OnLongPress;
        public Processable OnHoldOn;
        public Processable OnHoldOff;
        public boolean KeyHoldPlusKey;
        public boolean WaitForDoublePress;
    }

    class KeyCodeScanCode {
        public int KeyCode;
        public int ScanCode;
    }

    class KeyDownPress extends KeyCodeScanCode {
        public long KeyDownTime = 0;
        public long LongPressBeginTime = 0;
        public long HoldBeginTime = 0;
        public long KeyUpTime = 0;
        public long DoublePressTime = 0;
        public KeyActionsOption KeyActionsOption;
    }
}
