package com.sateda.keyonekb2;

import android.content.Context;
import android.content.res.Resources;
import android.inputmethodservice.Keyboard;
import android.os.Build;
import android.util.Log;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.regex.Pattern;

import static com.sateda.keyonekb2.FileJsonUtils.DeserializeFromJson;
import static com.sateda.keyonekb2.InputMethodServiceCoreKeyPress.TAG2;

public class KeyboardLayoutManager {

    public static KeyboardLayoutManager Instance = null;
    public ArrayList<KeyboardLayout> KeyboardLayoutList = new ArrayList<>();

    public HashMap<String, ArrayList<KeyboardLayout.KeyVariants>> KeyboardAltLayouts = new HashMap<>();


    private int CurrentLanguageListIndex = 0;
    private int LangListCount = 0;


    HashMap<String, Keyboard> symKeyboardsHashMap = new HashMap<>();


    public synchronized void Initialize(ArrayList<KeyboardLayout.KeyboardLayoutOptions> activeLayouts, Resources resources, Context context) {

        Instance = this;
        FileJsonUtils.Initialize(context);
        KeyboardLayout currentLayout = null;

        for (KeyboardLayout.KeyboardLayoutOptions layout : activeLayouts) {
            LangListCount++;
            currentLayout = DeserializeFromJson(layout.KeyboardMapping, new TypeReference<KeyboardLayout>() {}, context);
            if(currentLayout == null) {
                Log.e(TAG2, "Can not find correct Keyboard_layout neither in file, nor in resource "+layout.KeyboardMapping);
                continue;
            }
            for (KeyboardLayout.KeyVariants kv: currentLayout.KeyMapping) {
                if(kv.KeyCodeInt == 0 && kv.KeyCode != null && !kv.KeyCode.isEmpty()) {
                    try {
                        kv.KeyCodeInt = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(kv.KeyCode);
                    } catch (Throwable e) {
                        Log.e(TAG2, String.format("Can not map KEYCODE %s EX: %s", kv.KeyCode, e.toString()));
                    }
                }
            }
            currentLayout.SymXmlId = resources.getIdentifier(currentLayout.SymModeLayout, "xml", context.getPackageName());
            LoadAltLayout(resources, context, currentLayout);

            currentLayout.Resources = layout;
            KeyboardLayoutList.add(currentLayout);
            //AddSymKeyboard(currentLayout.SymXmlId, context);
        }
    }

    private void LoadAltLayout(Resources resources, Context context, KeyboardLayout currentLayout) {
        //TODO: Можно подкешировать, чтобы быстрее грузилась клава
        Collection<KeyboardLayout.KeyVariants> list = DeserializeFromJson(currentLayout.AltModeLayout, new TypeReference<Collection<KeyboardLayout.KeyVariants>>() {}, context);
        if(list == null) {
            Log.e(TAG2, "Can not find ALT_Keyboard_layout neither in file, nor in resource "+currentLayout.AltModeLayout);
        } else {
            for (KeyboardLayout.KeyVariants kv: list) {
                if(kv.KeyCodeInt == 0 && kv.KeyCode != null && !kv.KeyCode.isEmpty()) {
                    try {
                        kv.KeyCodeInt = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(kv.KeyCode);
                    } catch (Throwable e) {
                        Log.e(TAG2, String.format("Can not map KEYCODE %s EX: %s", kv.KeyCode, e.toString()));
                    }
                }
            }
            FillAltVariantsToCurrentLayout(currentLayout, list);
        }
    }

    private void FillAltVariantsToCurrentLayout(KeyboardLayout currentLayout, Collection<KeyboardLayout.KeyVariants> list) {
        for (KeyboardLayout.KeyVariants keyVariants : list) {
            KeyboardLayout.KeyVariants curKeyVariants = getCurKeyVariants(currentLayout, keyVariants.KeyCodeInt);
            if(curKeyVariants == null) {
                currentLayout.KeyMapping.add(keyVariants);
            } else {
                if (keyVariants.SinglePressAltMode != null && curKeyVariants.SinglePressAltMode == null) {
                    curKeyVariants.SinglePressAltMode = keyVariants.SinglePressAltMode;
                }
                if (keyVariants.SinglePressAltShiftMode != null
                        && curKeyVariants.SinglePressAltShiftMode == null) {
                    curKeyVariants.SinglePressAltShiftMode = keyVariants.SinglePressAltShiftMode;
                }
                if (keyVariants.AltMoreVariants != null && !keyVariants.AltMoreVariants.isEmpty() && (curKeyVariants.AltMoreVariants == null || curKeyVariants.AltMoreVariants.isEmpty())) {
                    curKeyVariants.AltMoreVariants = keyVariants.AltMoreVariants;
                }
            }
        }
    }

    public static KeyboardLayout.KeyVariants getCurKeyVariants(KeyboardLayout currentLayout, int keyCode) {
        for (KeyboardLayout.KeyVariants baseKeyVariants : currentLayout.KeyMapping) {
            if(baseKeyVariants.KeyCodeInt == keyCode) {
                return baseKeyVariants;
            }
        }
        return null;
    }


    public synchronized Keyboard GetSymKeyboard(boolean isAlt) {
        if(isAlt)
            return symKeyboardsHashMap.get(getSymKbdKey1(GetCurrentKeyboardLayout().SymXmlId));
        return symKeyboardsHashMap.get(getSymKbdKey2(GetCurrentKeyboardLayout().SymXmlId));
    }

    private void AddSymKeyboard(int symXmlId, Context context) {
        if(!symKeyboardsHashMap.containsKey(getSymKbdKey1(symXmlId))) {
            symKeyboardsHashMap.put(getSymKbdKey1(symXmlId), new Keyboard(context, symXmlId));
        }
        if(!symKeyboardsHashMap.containsKey(getSymKbdKey2(symXmlId))) {
            symKeyboardsHashMap.put(getSymKbdKey2(symXmlId), new Keyboard(context, symXmlId));
        }
    }

    private String getSymKbdKey2(int symXmlId) {
        return String.format("%s_2", symXmlId);
    }

    private String getSymKbdKey1(int symXmlId) {
        return String.format("%s_1", symXmlId);
    }

    public synchronized void ChangeLayout() {
        CurrentLanguageListIndex++;
        if(CurrentLanguageListIndex > LangListCount - 1) CurrentLanguageListIndex = 0;
    }

    public synchronized void ChangeLayoutBack() {
        if(CurrentLanguageListIndex == 0)
            CurrentLanguageListIndex = LangListCount - 1;
        else
            CurrentLanguageListIndex--;
    }

    public synchronized KeyboardLayout GetCurrentKeyboardLayout(){
        return KeyboardLayoutList.get(CurrentLanguageListIndex);
    }

    public synchronized KeyboardLayout GetDefaultKeyboardLayout(){
        return KeyboardLayoutList.get(0);
    }

    public synchronized int KeyToCharCode(InputMethodServiceCoreKeyPress.KeyPressData keyPressData, boolean alt_press, boolean shift_press, boolean is_double_press)
    {
        int result;
        KeyboardLayout.KeyVariants keyVariants = getCurKeyVariants(KeyboardLayoutList.get(CurrentLanguageListIndex), keyPressData.KeyCode);
        if(keyVariants == null)
            return 0;
        if(!alt_press && !shift_press && !is_double_press) {
            return keyVariants.SinglePress;
        }
        if (alt_press && shift_press) {
            if(keyVariants.SinglePressAltShiftMode != null) {
                result = keyVariants.SinglePressAltShiftMode;
            }
            else {
                Log.e(TAG2, "NO SinglePressAltShiftMode MAPPING FOR "+keyPressData.KeyCode);
                result = 0;
            }
        } else if (alt_press) {
            if(keyVariants.SinglePressAltMode != null)
                result = keyVariants.SinglePressAltMode;
            else {
                Log.e(TAG2, "NO SinglePressAltMode MAPPING FOR "+keyPressData.KeyCode);
                result = 0;
            }
        } else if (is_double_press && shift_press) {
            if(keyVariants.DoublePressShiftMode != null)
                result = keyVariants.DoublePressShiftMode;
            else {
                Log.e(TAG2, "NO DoublePressShiftMode MAPPING FOR "+keyPressData.KeyCode);
                result = 0;
            }
        } else if (is_double_press) {
            if(keyVariants.DoublePress != null)
                result = keyVariants.DoublePress;
            else {
                Log.e(TAG2, "NO DoublePress MAPPING FOR "+keyPressData.KeyCode);
                result = 0;
            }
        } else if (shift_press) {
            if(keyVariants.SinglePressShiftMode != null)
                result = keyVariants.SinglePressShiftMode;
            else {
                Log.e(TAG2, "NO SinglePressShiftMode MAPPING FOR "+keyPressData.KeyCode);
                result = 0;
            }
        } else {
            result = 0;
        }

        return result;
    }

    public synchronized int KeyToAltPopup(InputMethodServiceCoreKeyPress.KeyPressData keyPressData) {
        KeyboardLayout.KeyVariants keyVariants = getCurKeyVariants(KeyboardLayoutList.get(CurrentLanguageListIndex), keyPressData.KeyCode);
        if(keyVariants == null)
            return 0;
        if(keyVariants.AltMoreVariants == null || keyVariants.AltMoreVariants.isEmpty())
            return 0;
        return keyVariants.AltMoreVariants.charAt(0);
    }

    public static ArrayList<KeyboardLayout.KeyboardLayoutOptions> LoadKeyboardLayoutsRes(Resources resources, Context context) {
        // Load keyboard layouts
        //Открывает R.xml.keyboard_layouts и загружает все настройки клавиатуры
        FileJsonUtils.Initialize(context);

        String resName = context.getResources().getResourceEntryName(R.raw.keyboard_layouts);
        ArrayList<KeyboardLayout.KeyboardLayoutOptions> keyboardLayoutOptionsArray =  FileJsonUtils.DeserializeFromJson(resName, new TypeReference<ArrayList<KeyboardLayout.KeyboardLayoutOptions>>() {}, context);
        if(keyboardLayoutOptionsArray == null) {
            Log.e(TAG2, "keyboardLayoutResArray == null");
            return null;
        }
        for ( KeyboardLayout.KeyboardLayoutOptions keyboardLayoutOptions : keyboardLayoutOptionsArray) {

            keyboardLayoutOptions.IconCapsRes.DrawableResId= resources.getIdentifier(keyboardLayoutOptions.IconCapslock, "drawable", context.getPackageName());
            keyboardLayoutOptions.IconFirstShiftRes.DrawableResId = resources.getIdentifier(keyboardLayoutOptions.IconFirstShift, "drawable", context.getPackageName());
            keyboardLayoutOptions.IconLowercaseRes.DrawableResId = resources.getIdentifier(keyboardLayoutOptions.IconLowercase, "drawable", context.getPackageName());

            keyboardLayoutOptions.IconCapsRes.MipmapResId = resources.getIdentifier(keyboardLayoutOptions.IconCapslock, "mipmap", context.getPackageName());
            keyboardLayoutOptions.IconFirstShiftRes.MipmapResId = resources.getIdentifier(keyboardLayoutOptions.IconFirstShift, "mipmap", context.getPackageName());
            keyboardLayoutOptions.IconLowercaseRes.MipmapResId = resources.getIdentifier(keyboardLayoutOptions.IconLowercase, "mipmap", context.getPackageName());

            keyboardLayoutOptions.FlagResId = resources.getIdentifier(keyboardLayoutOptions.Flag, "drawable", context.getPackageName());

        }

        return keyboardLayoutOptionsArray;


    }

    public static boolean IsCurrentDevice(String deviceFullMODEL, KeyboardLayout.KeyboardLayoutOptions keyboardLayoutOptions) {
        Pattern regexp = Pattern.compile(keyboardLayoutOptions.DeviceModelRegexp.toUpperCase());
        return regexp.matcher(deviceFullMODEL).matches();
    }

    public static String getDeviceFullMODEL() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            return model.toUpperCase();
        } else {
            return String.format("%s %s",manufacturer,model).toUpperCase();
        }
    }
}
