package com.sateda.keyonekb2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;
import com.android.internal.telephony.ITelephony;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sateda.keyonekb2.input.CallStateCallback;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

public abstract class InputMethodServiceCodeCustomizable extends InputMethodServiceCoreGesture {
    protected boolean pref_show_toast = false;
    protected boolean pref_show_default_onscreen_keyboard = true;
    protected boolean pref_alt_space = true;
    protected boolean pref_manage_call = false;

    protected boolean pref_long_press_key_alt_symbol = false;
    protected boolean pref_keyboard_gestures_at_views_enable = true;

    private boolean metaCtrlPressed = false; // только первая буква будет большая

    protected boolean oneTimeShiftOneTimeBigMode; // только первая буква будет большая
    protected boolean doubleShiftCapsMode; //все следующий буквы будут большие
    protected boolean metaShiftPressed; //нажатие клавишь с зажатым альтом

    protected boolean symPadAltShift;

    protected boolean altPressSingleSymbolAltedMode;
    protected boolean doubleAltPressAllSymbolsAlted;

    protected boolean symbolOnScreenKeyboardMode = false;
    protected boolean keyboardStateFixed_NavModeAndKeyboard;
    protected boolean fnSymbolOnScreenKeyboardMode;

    protected boolean keyboardStateHolding_NavModeAndKeyboard;
    protected boolean metaAltPressed; //нажатие клавишь с зажатым альтом

    protected boolean needUpdateVisualInsideSingleEvent = false;

    protected ViewSatedaKeyboard keyboardView;


    //region LOAD

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

    KeyboardMechanics KeyboardMechanics;

    protected void LoadKeyProcessingMechanics(Context context) {

        try {

            KeyboardMechanics = FileJsonUtils.DeserializeFromJson("keyboard_mechanics", new TypeReference<KeyboardMechanics>() {}, context);

            if(KeyboardMechanics == null) {
                Log.e(TAG2, "CAN NOT LOAD KEYBOARD MECHANICS JSON FILE");
                return;
            }

            for (KeyboardMechanics.KeyGroupProcessor kgp : KeyboardMechanics.KeyGroupProcessors) {

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

    private InputMethodServiceCoreKeyPress.Processable ProcessReflectionMappingAndCreateProcessable(ArrayList<KeyboardMechanics.Action> list) throws NoSuchMethodException, NoSuchFieldException, IllegalAccessException {
        if(list == null || list.isEmpty()) {
            return null;
        }

        for (KeyboardMechanics.Action action : list) {
            Method method;
            if(action.CustomKeyCode != null && !action.CustomKeyCode.isEmpty()) {
                Field f = KeyEvent.class.getField(action.CustomKeyCode);
                action.CustomKeyCodeInt = f.getInt(null);
                method = InputMethodServiceCodeCustomizable.class.getDeclaredMethod(action.ActionMethodName, int.class);
            } else if(action.CustomChar > 0) {
                method = InputMethodServiceCodeCustomizable.class.getDeclaredMethod(action.ActionMethodName, char.class);
            } else if(action.MethodNeedsKeyPressParameter) {
                method = InputMethodServiceCodeCustomizable.class.getDeclaredMethod(action.ActionMethodName, KeyPressData.class);
            } else {
                method = InputMethodServiceCodeCustomizable.class.getDeclaredMethod(action.ActionMethodName);
            }
            action.ActionMethod = method;




            if(action.MetaModeMethodNames != null && !action.MetaModeMethodNames.isEmpty()) {
                action.MetaModeMethods = new ArrayList<>();
                for (String metaMethodName : action.MetaModeMethodNames) {

                    Method metaMethod = InputMethodServiceCodeCustomizable.class.getDeclaredMethod(metaMethodName);
                    action.MetaModeMethods.add(metaMethod);
                }
            }
        }

        Processable2 p = new Processable2();
        p.Actions = list;
        p.Keyboard = this;
        return p;
    }

    //region Processable2

    class Processable2 implements InputMethodServiceCoreKeyPress.Processable {
        @Override
        public boolean Process(KeyPressData keyPressData) {
            if(Actions == null || Actions.isEmpty())
                return true;
            try {
                for (KeyboardMechanics.Action action : Actions) {

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
                for (KeyboardMechanics.Action action : Actions) {

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

        private boolean InvokeMethod(KeyPressData keyPressData, KeyboardMechanics.Action action) throws IllegalAccessException, InvocationTargetException {
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


        ArrayList<KeyboardMechanics.Action> Actions = null;

        InputMethodServiceCodeCustomizable Keyboard = null;
    }

    //endregion

    //endregion

    //region CALL_MANAGER

    protected CallStateCallback callStateCallback;

    protected TelephonyManager telephonyManager;
    protected TelecomManager telecomManager;

    protected TelephonyManager getTelephonyManager() {
        return (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
    }

    protected TelecomManager getTelecomManager() {
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
            keyboardStateFixed_NavModeAndKeyboard = true;
            fnSymbolOnScreenKeyboardMode = false;
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



    //TODO: Блин вот хоть убей не помню нафига этот хак, но помню что без него что-то не работало (возвожно на К1)
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

    //region end
    //endregion

    protected abstract void HideKeyboard();
    protected abstract void ShowKeyboard();
    protected KeyboardLayoutManager keyboardLayoutManager = new KeyboardLayoutManager();

    protected abstract void UpdateKeyboardModeVisualization(boolean updateSwipePanelData);

    protected abstract void UpdateKeyboardModeVisualization();

    private boolean ChangeLanguage() {
        keyboardLayoutManager.ChangeLayout();
        if(pref_show_toast) {
            Toast toast = Toast.makeText(getApplicationContext(), keyboardLayoutManager.GetCurrentKeyboardLayout().KeyboardName, Toast.LENGTH_SHORT);
            toast.show();
        }
        UpdateKeyboardModeVisualization();
        return true;
    }

    void UpdateKeyboardVisibilityOnPrefChange() {
        if (pref_show_default_onscreen_keyboard) {
            UpdateKeyboardModeVisualization(true);
            ShowKeyboard();
        } else {
            HideKeyboard();
        }
    }

    protected boolean IsShiftMeta(int meta) {
        return (meta & ( KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_ON)) > 0;
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
    public SearchClickPlugin.SearchPluginLauncher SearchPluginLauncher;

    //TODO: Иногда вызывается по несколько раз подряд (видимо из разных мест)
    protected boolean DetermineFirstBigCharAndReturnChangedState(EditorInfo editorInfo) {
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





    private boolean IsNotPairedLetter(KeyPressData keyPressData) {
        //TODO: По сути - это определение сдвоенная буква или нет, наверное можно как-то оптимальнее сделать потом
        int code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, false, true);
        int code2sendNoDoublePress = keyboardLayoutManager.KeyToCharCode(keyPressData, false, false, false);
        return code2send == code2sendNoDoublePress;
    }





}
