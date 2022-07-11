package com.sateda.keyonekb2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.RequiresApi;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import com.android.internal.telephony.ITelephony;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sateda.keyonekb2.input.CallStateCallback;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public abstract class InputMethodServiceCodeCustomizable extends InputMethodServiceCoreGesture {
    protected boolean pref_show_toast = false;
    protected boolean pref_show_default_onscreen_keyboard = true;
    protected boolean pref_alt_space = true;
    protected boolean pref_manage_call = false;
    protected boolean pref_long_press_key_alt_symbol = false;
    protected boolean pref_keyboard_gestures_at_views_enable = true;
    protected boolean pref_vibrate_on_key_down = false;

    private boolean metaHoldCtrl; // только первая буква будет большая
    protected boolean metaFixedModeFirstLetterUpper; // только первая буква будет большая
    protected boolean metaFixedModeCapslock; //все следующий буквы будут большие
    protected boolean metaHoldShift; //нажатие клавишь с зажатым альтом
    protected boolean symPadAltShift;
    protected boolean metaHoldAlt; //нажатие клавишь с зажатым альтом
    protected boolean metaFixedModeFirstSymbolAlt;
    protected boolean metaFixedModeAllSymbolsAlt;

    protected boolean keyboardStateFixed_SymbolOnScreenKeyboard;
    protected boolean keyboardStateFixed_FnSymbolOnScreenKeyboard;
    protected boolean keyboardStateFixed_NavModeAndKeyboard;

    protected boolean keyboardStateHolding_NavModeAndKeyboard;

    protected boolean needUpdateVisualInsideSingleEvent;

    protected ViewSatedaKeyboard keyboardView;



    protected Processable OnStartInput;
    protected Processable OnFinishInput;
    protected Processable BeforeSendChar;
    protected Processable AfterSendChar;
    protected int[] ViewModeExcludeKeyCodes;

    protected String DEFAULT_KEYBOARD_MECHANICS_RES = "keyboard_mechanics";

    public String keyboard_mechanics_res = DEFAULT_KEYBOARD_MECHANICS_RES;

    protected Vibrator vibratorService;

    protected int TIME_VIBRATE;

    //region LOAD

    public static class KeyboardMechanics {

        @JsonProperty(index=100)
        public ArrayList<KeyGroupProcessor> KeyGroupProcessors = new ArrayList<>();

        @JsonProperty(index=20)
        public ArrayList<Action> OnStartInputActions;
        @JsonProperty(index=30)
        public ArrayList<Action> OnFinishInputActions;
        @JsonProperty(index=40)
        public ArrayList<Action> BeforeSendCharActions;
        @JsonProperty(index=50)
        public ArrayList<Action> AfterSendCharActions;
        @JsonProperty(index=60)
        public ArrayList<String> ViewModeKeyTransparencyExcludeKeyCodes;



        public static class KeyGroupProcessor {
            @JsonProperty(index=10)
            public ArrayList<String> KeyCodes = new ArrayList<>();
            public ArrayList<Integer> KeyCodeList = new ArrayList<>();

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
            public ArrayList<IActionMethod> MetaModeMethods;

            @JsonProperty(index=20)
            public String ActionMethodName;
            public IActionMethod ActionMethod;

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

    @RequiresApi(api = Build.VERSION_CODES.O)
    protected void LoadKeyProcessingMechanics(Context context) {

        try {

            KeyboardMechanics = FileJsonUtils.DeserializeFromJson(keyboard_mechanics_res, new TypeReference<KeyboardMechanics>() {}, context);

            if(KeyboardMechanics == null) {
                Log.e(TAG2, "CAN NOT LOAD KEYBOARD MECHANICS JSON FILE");
                return;
            }

            LoadMethods();

            if(KeyboardMechanics.ViewModeKeyTransparencyExcludeKeyCodes != null) {
                ViewModeExcludeKeyCodes = new int[KeyboardMechanics.ViewModeKeyTransparencyExcludeKeyCodes.size()];
                int i = 0;
                for (String keyCode1 : KeyboardMechanics.ViewModeKeyTransparencyExcludeKeyCodes) {
                    ViewModeExcludeKeyCodes[i] =  FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(keyCode1);
                    i++;
                }
            }
            OnStartInput = ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.OnStartInputActions);
            OnFinishInput = ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.OnFinishInputActions);
            BeforeSendChar = ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.BeforeSendCharActions);
            AfterSendChar = ProcessMethodMappingAndCreateProcessable(KeyboardMechanics.AfterSendCharActions);

            for (KeyboardMechanics.KeyGroupProcessor kgp : KeyboardMechanics.KeyGroupProcessors) {

                KeyProcessingMode keyAction;
                keyAction = new KeyProcessingMode();
                keyProcessingModeList.add(keyAction);

                kgp.KeyCodeList = new ArrayList<>();
                for (String keyCodeCode : kgp.KeyCodes) {

                    int value = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(keyCodeCode);
                    kgp.KeyCodeList.add(value);

                }

                keyAction.KeyCodeArray = Arrays.stream(kgp.KeyCodeList.toArray(new Integer[0])).mapToInt(Integer::intValue).toArray();

                keyAction.OnShortPress = ProcessMethodMappingAndCreateProcessable(kgp.OnShortPress);
                keyAction.OnUndoShortPress = this::DoNothingAndMakeUndoAtSubsequentKeyAction;
                keyAction.OnDoublePress = ProcessMethodMappingAndCreateProcessable(kgp.OnDoublePress);
                keyAction.OnLongPress = ProcessMethodMappingAndCreateProcessable(kgp.OnLongPress);
                keyAction.OnHoldOn = ProcessMethodMappingAndCreateProcessable(kgp.OnHoldOn);
                keyAction.OnHoldOff = ProcessMethodMappingAndCreateProcessable(kgp.OnHoldOff);
                keyAction.OnTriplePress = ProcessMethodMappingAndCreateProcessable(kgp.OnTriplePress);
            }

        } catch(Throwable ex) {
            Log.e(TAG2, "CAN NOT LOAD KEYBOARD MECHANICS: "+ex);
        }


    }


    boolean DoNothingAndMakeUndoAtSubsequentKeyAction(KeyPressData keyPressData) {
        return true;
    }

    private InputMethodServiceCoreKeyPress.Processable ProcessMethodMappingAndCreateProcessable(ArrayList<KeyboardMechanics.Action> list) throws NoSuchFieldException, IllegalAccessException {
        if(list == null || list.isEmpty()) {
            return null;
        }

        for (KeyboardMechanics.Action action : list) {
            ActionMethod method;
            if(action.CustomKeyCode != null && !action.CustomKeyCode.isEmpty()) {
                action.CustomKeyCodeInt = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(action.CustomKeyCode);
                method = FindActionMethodByName(action.ActionMethodName);
                if(!method.getType().equals(Integer.class)) {
                    Log.e(TAG2, "ACTION METHOD PARAMETER TYPE MISMATCH "+action.ActionMethodName+" NEED: "+method.getType());
                }
            } else if(action.CustomChar > 0) {
                method = FindActionMethodByName(action.ActionMethodName);
                if(!method.getType().equals(Character.class)) {
                    Log.e(TAG2, "ACTION METHOD PARAMETER TYPE MISMATCH "+action.ActionMethodName+" NEED: "+method.getType());
                }
            } else if(action.MethodNeedsKeyPressParameter) {
                method = FindActionMethodByName(action.ActionMethodName);
                if(!method.getType().equals(KeyPressData.class)) {
                    Log.e(TAG2, "ACTION METHOD PARAMETER TYPE MISMATCH "+action.ActionMethodName+" NEED: "+method.getType());
                }
            } else {
                method = FindActionMethodByName(action.ActionMethodName);
            }
            action.ActionMethod = method;

            if(action.MetaModeMethodNames != null && !action.MetaModeMethodNames.isEmpty()) {
                action.MetaModeMethods = new ArrayList<>();
                for (String metaMethodName : action.MetaModeMethodNames) {

                    ActionMethod metaMethod = FindActionMethodByName(metaMethodName);
                    action.MetaModeMethods.add(metaMethod);
                    if(!metaMethod.getType().equals(Object.class)) {
                        Log.e(TAG2, "ACTION METHOD PARAMETER TYPE MISMATCH "+metaMethodName+" NEED: "+method.getType());
                    }
                }
            }
        }

        Processable2 p = new Processable2();
        p.Actions = list;
        p.Keyboard = this;
        return p;
    }

    ActionMethod FindActionMethodByName(String methodName) {
        if(!Methods.containsKey(methodName)) {
            Log.e(TAG2, "CAN NOT FIND ACTION METHOD "+methodName);
            return null;
        }
        return Methods.get(methodName);
    }

    //endregion

    //region ActionMethod HashMap

    interface IActionMethod<T> {
        boolean invoke(T t);
    }

    class ActionMethod<T> implements IActionMethod<T> {

        Class _class;
        public Class getType() {
            return _class;
        }

        private ActionMethod() {}

        public ActionMethod(IActionMethod<T> IActionMethod, Class class1) {
            _class = class1;
            _method = IActionMethod;
        }
        IActionMethod<T> _method;
        @Override
        public boolean invoke(T t) {
            return _method.invoke(t);
        }
    }

    HashMap<String, ActionMethod> Methods = new HashMap<>();

    @RequiresApi(api = Build.VERSION_CODES.O)
    void LoadMethods() {
        FulfillMethodsHashMapGenerated2();
        //CodeGenerate();
    }

    private void CodeGenerate() {
        String methodsHashFulfill = "";
        Method[] methods = InputMethodServiceCodeCustomizable.class.getDeclaredMethods();
        for (Method method : methods) {
            if(Modifier.isStatic(method.getModifiers()))
                continue;
            if(!Modifier.isPublic(method.getModifiers()))
                continue;
            if(!method.getReturnType().equals(boolean.class))
                continue;

            if(method.getParameterTypes().length == 0) {
                //Methods.put("ActionTryAcceptCall", (Object o) -> ActionTryAcceptCall());
                methodsHashFulfill +=  "Methods.put(\""+method.getName()+"\", InitializeMethod3((Object o) -> "+method.getName()+"(), Object.class));\n";
            }

            if(method.getParameterTypes().length != 1)
                continue;

            if(method.getParameterTypes()[0].equals(KeyPressData.class)) {
                //Methods.put("ActionSendCtrlPlusKey", (Method3<KeyPressData>)this::ActionSendCtrlPlusKey);
                methodsHashFulfill +=  "Methods.put(\""+method.getName()+"\", InitializeMethod3(this::"+method.getName()+", KeyPressData.class));\n";
            } else if(method.getParameterTypes()[0].equals(int.class)) {
                //Methods.put("ActionKeyDown", (Method3<Integer>)this::ActionKeyDown);
                methodsHashFulfill +=  "Methods.put(\""+method.getName()+"\", InitializeMethod3(this::"+method.getName()+", Integer.class));\n";
            } else if(method.getParameterTypes()[0].equals(char.class)) {
                //Methods.put("ActionKeyDown", (Method3<Character>)this::ActionSendCharToInput);
                methodsHashFulfill +=  "Methods.put(\""+method.getName()+"\", InitializeMethod3(this::"+method.getName()+", Character.class));\n";
            }
        }
        Log.d(TAG2, methodsHashFulfill);
    }



    private <T > ActionMethod<T> InitializeMethod3(IActionMethod<T> IActionMethod, Class class1) {
        return new ActionMethod<T>(IActionMethod, class1);
    }
    
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void FulfillMethodsHashMapGenerated2() {

        Methods.put("ActionChangeFirstLetterShiftMode", InitializeMethod3((Object o) -> ActionChangeFirstLetterShiftMode(), Object.class));
        Methods.put("ActionChangeFirstSymbolAltMode", InitializeMethod3((Object o) -> ActionChangeFirstSymbolAltMode(), Object.class));
        Methods.put("ActionChangeFixedAltModeState", InitializeMethod3((Object o) -> ActionChangeFixedAltModeState(), Object.class));
        Methods.put("ActionChangeGestureModeState", InitializeMethod3((Object o) -> ActionChangeGestureModeState(), Object.class));
        Methods.put("ActionChangeKeyboardLayout", InitializeMethod3((Object o) -> ActionChangeKeyboardLayout(), Object.class));
        Methods.put("ActionChangeShiftCapslockState", InitializeMethod3((Object o) -> ActionChangeShiftCapslockState(), Object.class));
        Methods.put("ActionChangeSwipePanelVisibility", InitializeMethod3((Object o) -> ActionChangeSwipePanelVisibility(), Object.class));
        Methods.put("ActionDeletePreviousSymbol", InitializeMethod3(this::ActionDeletePreviousSymbol, KeyPressData.class));
        Methods.put("ActionDeleteUntilPrevCrLf", InitializeMethod3((Object o) -> ActionDeleteUntilPrevCrLf(), Object.class));
        Methods.put("ActionDisableAndResetGestureMode", InitializeMethod3((Object o) -> ActionDisableAndResetGestureMode(), Object.class));
        Methods.put("ActionDisableAndResetGesturesAtInputMode", InitializeMethod3((Object o) -> ActionDisableAndResetGesturesAtInputMode(), Object.class));
        Methods.put("ActionDisableGestureMode", InitializeMethod3((Object o) -> ActionDisableGestureMode(), Object.class));
        Methods.put("ActionDisableHoldAltMode", InitializeMethod3((Object o) -> ActionDisableHoldAltMode(), Object.class));
        Methods.put("ActionDisableHoldCtrlMode", InitializeMethod3(this::ActionDisableHoldCtrlMode, KeyPressData.class));
        Methods.put("ActionDisableHoldShiftMode", InitializeMethod3((Object o) -> ActionDisableHoldShiftMode(), Object.class));
        Methods.put("ActionDisableNavSymFnKeyboard", InitializeMethod3((Object o) -> ActionDisableNavSymFnKeyboard(), Object.class));
        Methods.put("ActionDoNothing", InitializeMethod3((Object o) -> ActionDoNothing(), Object.class));
        Methods.put("ActionEnableFixedAltModeState", InitializeMethod3((Object o) -> ActionEnableFixedAltModeState(), Object.class));
        Methods.put("ActionEnableGestureAtInputModeAndUpDownMode", InitializeMethod3((Object o) -> ActionEnableGestureAtInputModeAndUpDownMode(), Object.class));
        Methods.put("ActionEnableHoldAltMode", InitializeMethod3((Object o) -> ActionEnableHoldAltMode(), Object.class));
        Methods.put("ActionEnableHoldCtrlMode", InitializeMethod3(this::ActionEnableHoldCtrlMode, KeyPressData.class));
        Methods.put("ActionEnableHoldShiftMode", InitializeMethod3((Object o) -> ActionEnableHoldShiftMode(), Object.class));
        Methods.put("ActionKeyDown", InitializeMethod3(this::ActionKeyDown, Integer.class));
        Methods.put("ActionKeyDownUpDefaultFlags", InitializeMethod3(this::ActionKeyDownUpDefaultFlags, Integer.class));
        Methods.put("ActionKeyDownUpNoMetaKeepTouch", InitializeMethod3(this::ActionKeyDownUpNoMetaKeepTouch, Integer.class));
        Methods.put("ActionResetDoubleClickGestureState", InitializeMethod3((Object o) -> ActionResetDoubleClickGestureState(), Object.class));
        Methods.put("ActionSendCharDoublePressNoMeta", InitializeMethod3(this::ActionSendCharDoublePressNoMeta, KeyPressData.class));
        Methods.put("ActionSendCharLongPressAltSymbolAltMode", InitializeMethod3(this::ActionSendCharLongPressAltSymbolAltMode, KeyPressData.class));
        Methods.put("ActionSendCharLongPressAltSymbolNoMeta", InitializeMethod3(this::ActionSendCharLongPressAltSymbolNoMeta, KeyPressData.class));
        Methods.put("ActionSendCharLongPressAltSymbolShiftMode", InitializeMethod3(this::ActionSendCharLongPressAltSymbolShiftMode, KeyPressData.class));
        Methods.put("ActionSendCharLongPressCapitalize", InitializeMethod3(this::ActionSendCharLongPressCapitalize, KeyPressData.class));
        Methods.put("ActionSendCharLongPressCapitalizeAltMode", InitializeMethod3(this::ActionSendCharLongPressCapitalizeAltMode, KeyPressData.class));
        Methods.put("ActionSendCharSinglePressAltMode", InitializeMethod3(this::ActionSendCharSinglePressAltMode, KeyPressData.class));
        Methods.put("ActionSendCharSinglePressNoMeta", InitializeMethod3(this::ActionSendCharSinglePressNoMeta, KeyPressData.class));
        Methods.put("ActionSendCharSinglePressShiftMode", InitializeMethod3(this::ActionSendCharSinglePressShiftMode, KeyPressData.class));
        Methods.put("ActionSendCharSinglePressSymMode", InitializeMethod3(this::ActionSendCharSinglePressSymMode, KeyPressData.class));
        Methods.put("ActionSendCharToInput", InitializeMethod3(this::ActionSendCharToInput, Character.class));
        Methods.put("ActionSendCtrlPlusKey", InitializeMethod3(this::ActionSendCtrlPlusKey, KeyPressData.class));
        Methods.put("ActionSetNeedUpdateVisualState", InitializeMethod3((Object o) -> ActionSetNeedUpdateVisualState(), Object.class));
        Methods.put("ActionTryAcceptCall", InitializeMethod3((Object o) -> ActionTryAcceptCall(), Object.class));
        Methods.put("ActionTryCapitalizeFirstLetter", InitializeMethod3((Object o) -> ActionTryCapitalizeFirstLetter(), Object.class));
        Methods.put("ActionTryChangeKeyboardLayoutAtBaseMetaShift", InitializeMethod3(this::ActionTryChangeKeyboardLayoutAtBaseMetaShift, KeyPressData.class));
        Methods.put("ActionTryChangeSymPadLayout", InitializeMethod3((Object o) -> ActionTryChangeSymPadLayout(), Object.class));
        Methods.put("ActionTryChangeSymPadVisibilityAtInputMode", InitializeMethod3((Object o) -> ActionTryChangeSymPadVisibilityAtInputMode(), Object.class));
        Methods.put("ActionTryDeclineCall", InitializeMethod3((Object o) -> ActionTryDeclineCall(), Object.class));
        Methods.put("ActionTryDisableAltModeUponSpace", InitializeMethod3((Object o) -> ActionTryDisableAltModeUponSpace(), Object.class));
        Methods.put("ActionTryDisableCapslockShiftMode", InitializeMethod3((Object o) -> ActionTryDisableCapslockShiftMode(), Object.class));
        Methods.put("ActionTryDisableFirstLetterShiftMode", InitializeMethod3((Object o) -> ActionTryDisableFirstLetterShiftMode(), Object.class));
        Methods.put("ActionTryDisableFirstSymbolAltMode", InitializeMethod3((Object o) -> ActionTryDisableFirstSymbolAltMode(), Object.class));
        Methods.put("ActionTryDisableFixedAltModeState", InitializeMethod3((Object o) -> ActionTryDisableFixedAltModeState(), Object.class));
        Methods.put("ActionTryDisableNavModeAndKeyboard", InitializeMethod3((Object o) -> ActionTryDisableNavModeAndKeyboard(), Object.class));
        Methods.put("ActionTryDisableSymPad", InitializeMethod3((Object o) -> ActionTryDisableSymPad(), Object.class));
        Methods.put("ActionTryDoubleSpaceDotSpaceConversion", InitializeMethod3((Object o) -> ActionTryDoubleSpaceDotSpaceConversion(), Object.class));
        Methods.put("ActionTryEnableGestureAtInputOnHoldState", InitializeMethod3((Object o) -> ActionTryEnableGestureAtInputOnHoldState(), Object.class));
        Methods.put("ActionTryEnableNavModeAndKeyboard", InitializeMethod3((Object o) -> ActionTryEnableNavModeAndKeyboard(), Object.class));
        Methods.put("ActionTryResetSearchPlugin", InitializeMethod3((Object o) -> ActionTryResetSearchPlugin(), Object.class));
        Methods.put("ActionTryTurnOffGesturesMode", InitializeMethod3((Object o) -> ActionTryTurnOffGesturesMode(), Object.class));
        Methods.put("ActionUnCrLf", InitializeMethod3((Object o) -> ActionUnCrLf(), Object.class));
        Methods.put("InputIsDate", InitializeMethod3((Object o) -> InputIsDate(), Object.class));
        Methods.put("InputIsNumber", InitializeMethod3((Object o) -> InputIsNumber(), Object.class));
        Methods.put("InputIsPhone", InitializeMethod3((Object o) -> InputIsPhone(), Object.class));
        Methods.put("InputIsText", InitializeMethod3((Object o) -> InputIsText(), Object.class));
        Methods.put("IsActionBeforeMeta", InitializeMethod3((Object o) -> IsActionBeforeMeta(), Object.class));
        Methods.put("IsPackageChanged", InitializeMethod3((Object o) -> IsPackageChanged(), Object.class));
        Methods.put("MetaIsAltMode", InitializeMethod3((Object o) -> MetaIsAltMode(), Object.class));
        Methods.put("MetaIsAltPressed", InitializeMethod3((Object o) -> MetaIsAltPressed(), Object.class));
        Methods.put("MetaIsCtrlPressed", InitializeMethod3((Object o) -> MetaIsCtrlPressed(), Object.class));
        Methods.put("MetaIsShiftMode", InitializeMethod3((Object o) -> MetaIsShiftMode(), Object.class));
        Methods.put("MetaIsShiftPressed", InitializeMethod3((Object o) -> MetaIsShiftPressed(), Object.class));
        Methods.put("MetaIsSymPadAltShiftMode", InitializeMethod3((Object o) -> MetaIsSymPadAltShiftMode(), Object.class));
        Methods.put("MetaStateIsOnCall", InitializeMethod3((Object o) -> MetaStateIsOnCall(), Object.class));
        Methods.put("PrefLongPressAltSymbol", InitializeMethod3((Object o) -> PrefLongPressAltSymbol(), Object.class));
        Methods.put("SearchInputActivateOnLetterHack", InitializeMethod3((Object o) -> SearchInputActivateOnLetterHack(), Object.class));
        Methods.put("SetNavModeHoldOffState", InitializeMethod3((Object o) -> SetNavModeHoldOffState(), Object.class));
        Methods.put("SetNavModeHoldOnState", InitializeMethod3((Object o) -> SetNavModeHoldOnState(), Object.class));
        Methods.put("ActionTryVibrate", InitializeMethod3((Object o) -> ActionTryVibrate(), Object.class));

    }

    //endregion

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
                        for (IActionMethod metaMethod : action.MetaModeMethods) {
                            metaResult &= metaMethod.invoke(Keyboard);
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

        private boolean InvokeMethod(KeyPressData keyPressData, KeyboardMechanics.Action action) {
            boolean result;
            if(action.ActionMethod == null) {
                Log.e(TAG2, "action.ActionMethod == null; MethodName: "+action.ActionMethodName+" KeyCode: "+keyPressData.KeyCode);
                return false;
            }
            if(action.CustomKeyCodeInt > 0) {
                result = action.ActionMethod.invoke(action.CustomKeyCodeInt);
            } else if(action.CustomChar > 0) {
                result = action.ActionMethod.invoke(action.CustomChar);
            } else if(action.MethodNeedsKeyPressParameter) {
                result = action.ActionMethod.invoke(keyPressData);
            } else {
                result = action.ActionMethod.invoke(Keyboard);
            }

            return result;
        }


        ArrayList<KeyboardMechanics.Action> Actions = null;

        InputMethodServiceCodeCustomizable Keyboard = null;
    }

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
            keyboardStateFixed_SymbolOnScreenKeyboard = false;
            metaFixedModeFirstSymbolAlt = false;
            metaFixedModeAllSymbolsAlt = false;
            DetermineForceFirstUpper(getCurrentInputEditorInfo());
            UpdateGestureModeVisualization();
            return true;
        }
        return false;
    }

    public boolean ActionTryEnableNavModeAndKeyboard() {
        if (!keyboardStateFixed_NavModeAndKeyboard) {
            //Двойное нажание SYM -> Режим навигации
            keyboardStateFixed_NavModeAndKeyboard = true;
            keyboardStateFixed_FnSymbolOnScreenKeyboard = false;
            keyboardView.SetFnKeyboardMode(false);
            UpdateGestureModeVisualization();
            return true;
        }
        return false;
    }

    public boolean SetNavModeHoldOnState() {
        keyboardStateHolding_NavModeAndKeyboard = true;
        UpdateGestureModeVisualization();
        return true;
    }

    public boolean SetNavModeHoldOffState() {
        keyboardStateHolding_NavModeAndKeyboard = false;
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

    public boolean ActionResetDoubleClickGestureState() {
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

    public boolean ActionDisableAndResetGestureMode() {
        mode_keyboard_gestures = false;
        mode_keyboard_gestures_plus_up_down = false;
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
        return true;
    }

    public boolean ActionEnableGestureAtInputModeAndUpDownMode() {
        if(IsInputMode()) {
            mode_keyboard_gestures = true;
            mode_keyboard_gestures_plus_up_down = true;
            UpdateGestureModeVisualization();
            //TODO: ???
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
        metaHoldAlt = true;
        return true;
    }

    public boolean ActionDisableHoldAltMode() {
        metaHoldAlt = false;
        return true;
    }

    public boolean ActionChangeFirstSymbolAltMode() {
        metaFixedModeFirstSymbolAlt = !metaFixedModeFirstSymbolAlt;
        return true;
    }

    public boolean ActionTryDisableFirstSymbolAltMode() {
        if (metaFixedModeFirstSymbolAlt && !pref_alt_space) {
            metaFixedModeFirstSymbolAlt = false;
            return true;
        }
        return false;
    }

    public boolean ActionChangeFixedAltModeState() {
        metaFixedModeFirstSymbolAlt = false;
        metaFixedModeAllSymbolsAlt = !metaFixedModeAllSymbolsAlt;
        return true;
    }

    public boolean ActionEnableFixedAltModeState() {
        metaFixedModeFirstSymbolAlt = false;
        metaFixedModeAllSymbolsAlt = true;
        return true;
    }

    public boolean ActionTryDisableFixedAltModeState() {
        if(metaFixedModeAllSymbolsAlt){
            metaFixedModeAllSymbolsAlt = false;
            metaFixedModeFirstSymbolAlt = false;
            return true;
        }
        return false;
    }

    //endregion

    //region Actions SHIFT-MODE

    public boolean ActionEnableHoldShiftMode() {
        metaHoldShift = true;
        return true;
    }

    public boolean ActionDisableHoldShiftMode() {
        metaHoldShift = false;
        return true;
    }

    public boolean ActionChangeFirstLetterShiftMode() {
        metaFixedModeFirstLetterUpper = !metaFixedModeFirstLetterUpper;
        return true;
    }

    public boolean ActionTryDisableFirstLetterShiftMode() {
        if (metaFixedModeFirstLetterUpper) {
            metaFixedModeFirstLetterUpper = false;
            return true;
        }
        return false;
    }

    public boolean ActionTryDisableCapslockShiftMode() {
        if(metaFixedModeCapslock) {
            metaFixedModeCapslock = false;
            DetermineForceFirstUpper(getCurrentInputEditorInfo());
            return true;
        }
        return false;
    }

    public boolean ActionChangeShiftCapslockState() {
        metaFixedModeFirstLetterUpper = false;
        metaFixedModeCapslock = !metaFixedModeCapslock;
        return true;
    }

    //endregion

    //region Actions SYM-PAD

    public boolean ActionDisableNavSymFnKeyboard() {
        //Отключаем режим навигации
        keyboardStateFixed_NavModeAndKeyboard = false;
        keyboardStateFixed_FnSymbolOnScreenKeyboard = false;
        keyboardView.SetFnKeyboardMode(false);
        UpdateGestureModeVisualization();
        return true;
    }

    public boolean ActionTryChangeSymPadVisibilityAtInputMode() {
        if(IsInputMode()) {
            if(!keyboardStateFixed_SymbolOnScreenKeyboard){
                keyboardStateFixed_SymbolOnScreenKeyboard = true;
                metaFixedModeAllSymbolsAlt = true;
                symPadAltShift = true;
                metaFixedModeFirstSymbolAlt = false;
            } else {
                symPadAltShift = false;
                keyboardStateFixed_SymbolOnScreenKeyboard = false;
                metaFixedModeFirstSymbolAlt = false;
                metaFixedModeAllSymbolsAlt = false;
                //TODO: Поубирать
                //DetermineForceFirstUpper(getCurrentInputEditorInfo());
            }
            //TODO: Много лишних вызовов апдейта нотификаций
            return true;
        }
        return false;
    }


    public boolean ActionTryChangeSymPadLayout() {
        if(keyboardStateFixed_SymbolOnScreenKeyboard) {
            symPadAltShift = !symPadAltShift;
            return true;
        }
        return false;
    }

    public boolean ActionTryDisableSymPad() {
        if (keyboardStateFixed_SymbolOnScreenKeyboard) {
            keyboardStateFixed_SymbolOnScreenKeyboard = false;
            symPadAltShift = false;
            return true;
        }
        return false;
    }

    //endregion

    //region Actions CTRL-MODE

    public boolean ActionEnableHoldCtrlMode(KeyPressData keyPressData) {
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        metaHoldCtrl = true;

        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(
                        keyPressData.BaseKeyEvent.getDownTime(),
                        keyPressData.BaseKeyEvent.getEventTime(),
                        KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta));
        return true;
    }

    public boolean ActionDisableHoldCtrlMode(KeyPressData keyPressData) {
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        metaHoldCtrl = false;
        getCurrentInputConnection().sendKeyEvent(new KeyEvent(
                keyPressData.BaseKeyEvent.getDownTime(),
                keyPressData.BaseKeyEvent.getEventTime(),
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta));
        return true;
    }

    //endregion

    //region Actions Search-Hack

    public boolean ActionTryResetSearchPlugin() {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        if(     SearchPluginLauncher != null
                && !editorInfo.packageName.equals(SearchPluginLauncher.PackageName)) {
            SetSearchHack(null);
            return true;
        }
        return false;
    }

    public boolean SearchInputActivateOnLetterHack() {
        if(IsInputMode() && isInputViewShown() && SearchPluginLauncher != null) {
            Log.d(TAG2, "NO_FIRE SearchPluginAction INPUT_MODE");
            SetSearchHack(null);
            return true;
        }
        if(SearchPluginLauncher != null) {
            Log.d(TAG2, "FIRE SearchPluginAction!");
            SearchPluginLauncher.FirePluginAction();
            SetSearchHack(null);
            return true;
        }
        return false;
    }

    //endregion

    //region Action KEYs

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

    //endregion



    //region Actions Misc. InputMode

    public boolean ActionSendCharToInput(char char1) {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null)
            inputConnection.commitText(String.valueOf(char1), 1);
        return true;
    }

    public boolean ActionTryCapitalizeFirstLetter() {
        return DetermineForceFirstUpper(getCurrentInputEditorInfo());
    }



    public boolean ActionDeletePreviousSymbol(KeyPressData keyPressData) {
        DeleteLastSymbol();
        //ActionSetNeedUpdateVisualState();
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
            return true;
        }
        return false;
    }

    //endregion

    //region Actions OTHER

    public boolean ActionTryVibrate() {
        if(pref_vibrate_on_key_down && vibratorService != null) {
            Vibrate(TIME_VIBRATE);
            return true;
        }
        return false;
    }

    //SAME AS: "need-update-visual-state": true,
    public boolean ActionSetNeedUpdateVisualState() {
        needUpdateVisualInsideSingleEvent = true;
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



    public boolean ActionDoNothing() {
        return true;
    }



    public boolean ActionChangeKeyboardLayout() {
        ChangeLanguage();
        return true;
    }

    public boolean ActionTryDisableAltModeUponSpace() {
        if(metaFixedModeFirstSymbolAlt && pref_alt_space) {
            metaFixedModeFirstSymbolAlt = false;
            return true;
        }
        return false;
    }

    //endregion

    //region Actions LETTERS (CHARS)

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

    public boolean IsActionBeforeMeta() {
        return true;
    }

    public boolean PrefLongPressAltSymbol() {
        return pref_long_press_key_alt_symbol;
    }

    public boolean MetaIsShiftPressed() {

        return metaHoldShift;
    }

    public boolean MetaIsCtrlPressed() {

        return metaHoldCtrl;
    }

    public boolean MetaIsShiftMode() {

        return metaFixedModeFirstLetterUpper || metaFixedModeCapslock || metaHoldShift;
    }

    public boolean MetaIsAltMode() {
        return metaFixedModeFirstSymbolAlt || metaFixedModeAllSymbolsAlt || metaHoldAlt;
    }

    public boolean MetaIsAltPressed() {
        return metaHoldAlt;
    }


    public boolean MetaIsSymPadAltShiftMode() {
        return keyboardStateFixed_SymbolOnScreenKeyboard && symPadAltShift;
    }

    //endregion

    //region INPUT_TYPE

    public abstract boolean IsPackageChanged();

    public boolean InputIsNumber() {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        return (editorInfo.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER;
    }

    public boolean InputIsPhone() {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        return (editorInfo.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_PHONE;
    }

    public boolean InputIsDate() {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        return (editorInfo.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_DATETIME;
    }

    public boolean InputIsText() {
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        return (editorInfo.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT;
    }

    //endregion

    //region end all the rest -->
    //endregion

    private void SendLetterOrSymbol(int code2send) {
        Log.v(TAG2, "KEY SEND: "+String.format("%c", code2send));

        BeforeSendChar.Process(null);

        sendKeyChar((char) code2send);

        AfterSendChar.Process(null);
    }

    protected KeyboardLayoutManager keyboardLayoutManager = new KeyboardLayoutManager();

    protected abstract void UpdateKeyboardModeVisualization(boolean updateSwipePanelData);

    protected abstract void UpdateKeyboardModeVisualization();

    protected abstract void ChangeLanguage();

    protected abstract void UpdateKeyboardVisibilityOnPrefChange();

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


    private boolean IsNotPairedLetter(KeyPressData keyPressData) {
        //TODO: По сути - это определение сдвоенная буква или нет, наверное можно как-то оптимальнее сделать потом
        int code2send = keyboardLayoutManager.KeyToCharCode(keyPressData, false, false, true);
        int code2sendNoDoublePress = keyboardLayoutManager.KeyToCharCode(keyPressData, false, false, false);
        return code2send == code2sendNoDoublePress;
    }

    //TODO: Иногда вызывается по несколько раз подряд (видимо из разных мест)
    protected boolean DetermineForceFirstUpper(EditorInfo editorInfo) {
        //Если мы вывалились из зоны ввода текста
        //NOTE: Проверка не дает вводить Заглавную прям на первом входе в приложение. Видимо не успевает еще активироваться.
        //if(!isInputViewShown())
        //    return;

        if(editorInfo == null)
            return false;

        if (MetaIsAltMode()
                || metaFixedModeCapslock)
            return false;

        int makeFirstBig = 0;
        if (editorInfo.inputType != InputType.TYPE_NULL) {
            makeFirstBig = getCurrentInputConnection().getCursorCapsMode(editorInfo.inputType);
        }

        if(makeFirstBig != 0){
            if(!metaFixedModeFirstLetterUpper) {
                metaFixedModeFirstLetterUpper = true;
                Log.d(TAG2, "updateShiftKeyState (changed to) oneTimeShiftOneTimeBigMode = true");
                return true;
            }
        }else {
            //makeFirstBig == 0
            if(metaFixedModeFirstLetterUpper) {
                metaFixedModeFirstLetterUpper = false;
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

    private void Vibrate(int ms) {

        if(vibratorService == null)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibratorService.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            //deprecated in API 26
            vibratorService.vibrate(ms);
        }
    }
}
