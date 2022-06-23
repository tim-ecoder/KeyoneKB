package com.sateda.keyonekb2;

import android.content.Context;
import android.content.res.Resources;
import android.inputmethodservice.Keyboard;
import android.util.Log;

import com.fasterxml.jackson.core.type.TypeReference;
import org.xmlpull.v1.XmlPullParser;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import static com.sateda.keyonekb2.KeyPressKeyboardBase.TAG2;

public class KeyboardLayoutManager {

    public static KeyboardLayoutManager Instance = null;
    public ArrayList<KeyboardLayout> KeyboardLayoutList = new ArrayList<>();

    public HashMap<String, ArrayList<KeyVariants>> KeyboardAltLayouts = new HashMap<>();


    private int CurrentLanguageListIndex = 0;
    private int LangListCount = 0;


    HashMap<String, Keyboard> symKeyboardsHashMap = new HashMap<>();


    public synchronized void Initialize(ArrayList<KeyboardLayoutRes> activeLayouts, Resources resources, Context context) {

        if(Instance != null) {
            return;
        }

        Instance = this;
        FileJsonUtils.Initialize(context.getPackageName(), context);


        KeyboardLayout currentLayout = null;

        for (KeyboardLayoutRes layout : activeLayouts) {
            LangListCount++;
            String fileName = layout.XmlRes+".json";
            if(FileJsonUtils.FileExists(fileName)) {
                currentLayout = FileJsonUtils.DeserializeFromFile(fileName, new TypeReference<KeyboardLayout>() {});
                currentLayout.SymXmlId = resources.getIdentifier(currentLayout.SymModeLayout, "xml", context.getPackageName());
                LoadAltLayout(resources, context, currentLayout);
            } else if(resources.getIdentifier(layout.XmlRes, "raw", context.getPackageName()) != 0) {
                InputStream is = resources.openRawResource(resources.getIdentifier(layout.XmlRes, "raw", context.getPackageName()));
                currentLayout = FileJsonUtils.DeserializeFromFile(is, new TypeReference<KeyboardLayout>() {});
                currentLayout.SymXmlId = resources.getIdentifier(currentLayout.SymModeLayout, "xml", context.getPackageName());
                LoadAltLayout(resources, context, currentLayout);
            } else {
                Log.e(TAG2, "Can not find Keyboard_layout neither in file, nor in resource "+layout.XmlRes);
            }



            currentLayout.Resources = layout;
            KeyboardLayoutList.add(currentLayout);
            AddSymKeyboard(currentLayout.SymXmlId, context);
        }
    }

    private void LoadAltLayout(Resources resources, Context context, KeyboardLayout currentLayout) {
        String altFileName = currentLayout.AltModeLayout + ".json";
        if (FileJsonUtils.FileExists(altFileName)) {
            Collection<KeyVariants> list = FileJsonUtils.DeserializeFromFile(altFileName, new TypeReference<Collection<KeyVariants>>() {
            });
            if (list != null) {
                FillAltVariantsToCurrentLayout(currentLayout, list);
            }
        } else if (resources.getIdentifier(currentLayout.AltModeLayout, "raw", context.getPackageName()) != 0) {
            InputStream is = resources.openRawResource(resources.getIdentifier(currentLayout.AltModeLayout, "raw", context.getPackageName()));
            Collection<KeyVariants> list = FileJsonUtils.DeserializeFromFile(is, new TypeReference<Collection<KeyVariants>>() {
            });
            if (list != null) {
                FillAltVariantsToCurrentLayout(currentLayout, list);
            }
        } else {
            Log.e(TAG2, "Can not find ALT_Keyboard_layout neither in file, nor in resource "+currentLayout.AltModeLayout);
        }
    }

    private void FillAltVariantsToCurrentLayout(KeyboardLayout currentLayout, Collection<KeyVariants> list) {
        for (KeyVariants keyVariants : list) {
            KeyVariants curKeyVariants = getCurKeyVariants(currentLayout, keyVariants.KeyCode);
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

    public static KeyVariants getCurKeyVariants(KeyboardLayout currentLayout, int keyCode) {
        for (KeyVariants baseKeyVariants : currentLayout.KeyMapping) {
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

    public synchronized int KeyToCharCode(KeyPressKeyboardBase.KeyPressData keyPressData, boolean alt_press, boolean shift_press, boolean is_double_press)
    {
        int result;
        KeyVariants keyVariants = getCurKeyVariants(KeyboardLayoutList.get(CurrentLanguageListIndex), keyPressData.KeyCode);
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

    public synchronized int KeyToAltPopup(KeyPressKeyboardBase.KeyPressData keyPressData) {
        KeyVariants keyVariants = getCurKeyVariants(KeyboardLayoutList.get(CurrentLanguageListIndex), keyPressData.KeyCode);
        if(keyVariants == null)
            return 0;
        if(keyVariants.AltMoreVariants == null || keyVariants.AltMoreVariants.isEmpty())
            return 0;
        return keyVariants.AltMoreVariants.charAt(0);
    }

    public static ArrayList<KeyboardLayoutRes> LoadKeyboardLayoutsRes(Resources resources, Context context) {
        // Load keyboard layouts
        //Открывает R.xml.keyboard_layouts и загружает все настройки клавиатуры

        ArrayList<KeyboardLayoutRes> keyboardLayoutResArray = new ArrayList<>();
        try {
            XmlPullParser parser = resources.getXml(R.xml.keyboard_layouts);
            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                if(
                parser.getEventType() == XmlPullParser.END_TAG
                || parser.getEventType() == XmlPullParser.START_DOCUMENT) {

                    parser.next();
                    continue;
                }
                if(!parser.getName().equals("KeyboardLayout")){

                    parser.next();
                    continue;
                }

                String name = "";
                String layoutRes = "";
                String iconCapsRes = "";
                String iconOneShiftRes = "";
                String iconLittleRes = "";

                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    if (parser.getAttributeName(i).equals("menu_name")) name = parser.getAttributeValue(i);
                    if (parser.getAttributeName(i).equals("res")) layoutRes = parser.getAttributeValue(i);
                    if (parser.getAttributeName(i).equals("icon_caps")) iconCapsRes = parser.getAttributeValue(i);
                    if (parser.getAttributeName(i).equals("icon_first_big")) iconOneShiftRes = parser.getAttributeValue(i);
                    if (parser.getAttributeName(i).equals("icon_little")) iconLittleRes = parser.getAttributeValue(i);
                }
                int layoutResId = resources.getIdentifier(layoutRes, "xml", context.getPackageName());



                KeyboardLayoutRes keyboardLayoutRes = new KeyboardLayoutRes(name, layoutResId, layoutRes);

                keyboardLayoutRes.IconCapsRes.DrawableResId= resources.getIdentifier(iconCapsRes, "drawable", context.getPackageName());
                keyboardLayoutRes.IconFirstShiftRes.DrawableResId = resources.getIdentifier(iconOneShiftRes, "drawable", context.getPackageName());
                keyboardLayoutRes.IconLittleRes.DrawableResId = resources.getIdentifier(iconLittleRes, "drawable", context.getPackageName());

                keyboardLayoutRes.IconCapsRes.MipmapResId = resources.getIdentifier(iconCapsRes, "mipmap", context.getPackageName());
                keyboardLayoutRes.IconFirstShiftRes.MipmapResId = resources.getIdentifier(iconOneShiftRes, "mipmap", context.getPackageName());
                keyboardLayoutRes.IconLittleRes.MipmapResId = resources.getIdentifier(iconLittleRes, "mipmap", context.getPackageName());

                keyboardLayoutResArray.add(keyboardLayoutRes);
                parser.next();
            }

            return keyboardLayoutResArray;

        } catch (Throwable t) {
            Log.e(TAG2, "ERROR LOADING XML KEYBOARD LAYOUT "+ t);
        }
        return keyboardLayoutResArray;
    }
}
