package com.sateda.keyonekb2;

import android.content.Context;
import android.content.res.Resources;
import android.inputmethodservice.Keyboard;
import android.util.Log;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

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
        FileJsonUtils.Initialize(context.getPackageName(), context);
        KeyboardLayout currentLayout = null;

        for (KeyboardLayout.KeyboardLayoutOptions layout : activeLayouts) {
            LangListCount++;
            currentLayout = DeserializeFromJson(layout.KeyboardMapping, new TypeReference<KeyboardLayout>() {}, context);
            if(currentLayout == null) {
                Log.e(TAG2, "Can not find Keyboard_layout neither in file, nor in resource "+layout.KeyboardMapping);
                continue;
            }
            currentLayout.SymXmlId = resources.getIdentifier(currentLayout.SymModeLayout, "xml", context.getPackageName());
            LoadAltLayout(resources, context, currentLayout);

            currentLayout.Resources = layout;
            KeyboardLayoutList.add(currentLayout);
            AddSymKeyboard(currentLayout.SymXmlId, context);
        }
    }

    private void LoadAltLayout(Resources resources, Context context, KeyboardLayout currentLayout) {
        //TODO: Можно подкешировать, чтобы быстрее грузилась клава
        Collection<KeyboardLayout.KeyVariants> list = DeserializeFromJson(currentLayout.AltModeLayout, new TypeReference<Collection<KeyboardLayout.KeyVariants>>() {}, context);
        if(list == null) {
            Log.e(TAG2, "Can not find ALT_Keyboard_layout neither in file, nor in resource "+currentLayout.AltModeLayout);
        } else {
            FillAltVariantsToCurrentLayout(currentLayout, list);
        }
    }

    private void FillAltVariantsToCurrentLayout(KeyboardLayout currentLayout, Collection<KeyboardLayout.KeyVariants> list) {
        for (KeyboardLayout.KeyVariants keyVariants : list) {
            KeyboardLayout.KeyVariants curKeyVariants = getCurKeyVariants(currentLayout, keyVariants.KeyCode);
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
                if (keyVariants.AltShiftMoreVariants != null && !keyVariants.AltShiftMoreVariants.isEmpty() && (curKeyVariants.AltShiftMoreVariants == null || curKeyVariants.AltShiftMoreVariants.isEmpty())) {
                    curKeyVariants.AltShiftMoreVariants = keyVariants.AltShiftMoreVariants;
                }
            }
        }
    }

    public static KeyboardLayout.KeyVariants getCurKeyVariants(KeyboardLayout currentLayout, int keyCode) {
        for (KeyboardLayout.KeyVariants baseKeyVariants : currentLayout.KeyMapping) {
            if(baseKeyVariants.KeyCode == keyCode) {
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

    public synchronized KeyboardLayout GetCurrentKeyboardLayout(){
        return KeyboardLayoutList.get(CurrentLanguageListIndex);
    }

    public synchronized int KeyToCharCode(InputMethodServiceCoreKeyPress.KeyPressData keyPressData, boolean alt_press, boolean shift_press, boolean is_double_press)
    {
        int result;
        KeyboardLayout.KeyVariants keyVariants = getCurKeyVariants(KeyboardLayoutList.get(CurrentLanguageListIndex), keyPressData.KeyCode);
        if(keyVariants == null)
            return 0;
        if (alt_press && shift_press && keyVariants.SinglePressAltShiftMode != null) {
            result = keyVariants.SinglePressAltShiftMode;
        } else if (alt_press && keyVariants.SinglePressAltMode != null) {
            result = keyVariants.SinglePressAltMode;
        } else if (is_double_press && shift_press && keyVariants.DoublePressShiftMode != null) {
            result = keyVariants.DoublePressShiftMode;
        } else if (is_double_press && keyVariants.DoublePress != null) {
            result = keyVariants.DoublePress;
        } else if (shift_press && keyVariants.SinglePressShiftMode != null) {
            result = keyVariants.SinglePressShiftMode;
        } else {
            result = keyVariants.SinglePress;
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
        FileJsonUtils.Initialize(context.getPackageName(), context);

        String resName = context.getResources().getResourceEntryName(R.raw.keyboard_layouts);
        ArrayList<KeyboardLayout.KeyboardLayoutOptions> keyboardLayoutOptionsArray =  FileJsonUtils.DeserializeFromJson(resName, new TypeReference<ArrayList<KeyboardLayout.KeyboardLayoutOptions>>() {}, context);
        if(keyboardLayoutOptionsArray == null) {
            Log.e(TAG2, "keyboardLayoutResArray == null");
            return null;
        }
        for ( KeyboardLayout.KeyboardLayoutOptions keyboardLayoutOptions : keyboardLayoutOptionsArray) {

            keyboardLayoutOptions.IconCapsRes.DrawableResId= resources.getIdentifier(keyboardLayoutOptions.IconCapslock, "drawable", context.getPackageName());
            keyboardLayoutOptions.IconFirstShiftRes.DrawableResId = resources.getIdentifier(keyboardLayoutOptions.IconFirstShift, "drawable", context.getPackageName());
            keyboardLayoutOptions.IconLittleRes.DrawableResId = resources.getIdentifier(keyboardLayoutOptions.IconLowercase, "drawable", context.getPackageName());

            keyboardLayoutOptions.IconCapsRes.MipmapResId = resources.getIdentifier(keyboardLayoutOptions.IconCapslock, "mipmap", context.getPackageName());
            keyboardLayoutOptions.IconFirstShiftRes.MipmapResId = resources.getIdentifier(keyboardLayoutOptions.IconFirstShift, "mipmap", context.getPackageName());
            keyboardLayoutOptions.IconLittleRes.MipmapResId = resources.getIdentifier(keyboardLayoutOptions.IconLowercase, "mipmap", context.getPackageName());
        }

        return keyboardLayoutOptionsArray;


    }
}
