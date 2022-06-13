package com.sateda.keyonekb2;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashMap;

public class KeyboardLayoutManager {

    public ArrayList<KeyboardLayout> KeyboardLayoutList = new ArrayList<>();
    private int CurrentLanguageListIndex = 0;
    private int LangListCount = 0;
    public boolean isEnglishKb = false;

    public void Initialize(KeyboardLayoutRes[] activeLayouts, Resources resources, Context context) {
        if(LangListCount != 0)
            return;

        KeyboardLayout currentLayout;

        for (KeyboardLayoutRes layout : activeLayouts) {
            LangListCount++;
            currentLayout = LoadLayoutAndCache(layout.XmlResId, LangListCount - 1, KeyboardLayoutList, resources, context);
            currentLayout.IconCaps = layout.IconCapsResId;
            currentLayout.IconFirstShift = layout.IconFirstShiftResId;
            currentLayout.IconLittle = layout.IconLittleResId;
            KeyboardLayoutList.add(currentLayout);
        }

    }

    private static KeyboardLayout LoadLayoutAndCache(int keyboardLayoutXmlId, int currentKeyBoardSetId, ArrayList<KeyboardLayout> keyboardLayoutArrayList, Resources resources, Context context)  {


        int scan_code;
        int one_press;
        int double_press;
        int double_press_shift;
        int alt;
        int shift;
        int alt_shift;
        String alt_popup;
        String alt_shift_popup;
        String languageOnScreenNaming = "";
        String alt_hw = "";
        String sym_sw_res = "";

        KeyboardLayout keyboardLayout = new KeyboardLayout();
        keyboardLayout.Id = keyboardLayoutXmlId;
        keyboardLayout.KeyVariantsMap = new HashMap<>();

        try {
            XmlPullParser parser = resources.getXml(keyboardLayoutXmlId);

            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                scan_code = 0;
                one_press = 0;
                double_press = 0;
                double_press_shift = 0;
                alt = 0;
                shift = 0;
                alt_shift = 0;
                alt_popup = "";
                alt_shift_popup = "";


                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    if (parser.getAttributeName(i).equals("lang")) languageOnScreenNaming = parser.getAttributeValue(i);
                    if (parser.getAttributeName(i).equals("alt_hw_res")) alt_hw = parser.getAttributeValue(i);
                    if (parser.getAttributeName(i).equals("sym_sw_res")) sym_sw_res = parser.getAttributeValue(i);
                    if (parser.getAttributeName(i).equals("scan_code")) scan_code = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("one_press")) one_press = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("double_press")) double_press = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("double_press_shift")) double_press_shift = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("alt")) alt = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("shift")) shift = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("alt_shift")) alt_shift = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("alt_popup")) alt_popup = parser.getAttributeValue(i);
                    if (parser.getAttributeName(i).equals("alt_shift_popup")) alt_shift_popup = parser.getAttributeValue(i);
                }

                if(scan_code != 0){
                    KeyVariants keyLayout = new KeyVariants();
                    keyLayout.scan_code = scan_code;
                    keyLayout.one_press = one_press;
                    keyLayout.one_press_shift = shift;
                    keyLayout.double_press = double_press;
                    keyLayout.double_press_shift = double_press_shift;
                    keyLayout.alt = alt;
                    keyLayout.alt_shift = alt_shift;
                    keyLayout.alt_popup = alt_popup;
                    keyLayout.alt_shift_popup = alt_shift_popup;

                    keyboardLayout.KeyVariantsMap.put(scan_code, keyLayout);
                }
                parser.next();
            }
            keyboardLayout.LanguageOnScreenNaming = languageOnScreenNaming;
            keyboardLayout.XmlId = keyboardLayoutXmlId;
            keyboardLayoutArrayList.add(currentKeyBoardSetId, keyboardLayout);
        } catch (Throwable t) {
            Log.e(KeyboardBaseKeyLogic.TAG2, "ERROR LOADING XML KEYBOARD LAYOUT "+ t);
        }

        keyboardLayout.SymXmlId = resources.getIdentifier(sym_sw_res, "xml", context.getPackageName());
        int altHwResId = resources.getIdentifier(alt_hw, "xml", context.getPackageName());
        LoadAltLayout2(keyboardLayout.KeyVariantsMap, resources, altHwResId);
        return keyboardLayout;
    }

    private static void LoadAltLayout2(HashMap<Integer, KeyVariants> keyLayoutsHashMap, Resources resources, int altHwResId)
    {
        if(altHwResId == 0)
            return;

        int scan_code;
        int alt;
        int alt_shift;
        String alt_popup;
        String alt_shift_popup;

        try {
            XmlPullParser parser;
            parser = resources.getXml(altHwResId);

            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                scan_code = 0;
                alt = 0;
                alt_shift = 0;
                alt_popup = "";
                alt_shift_popup = "";

                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    if (parser.getAttributeName(i).equals("scan_code")) scan_code = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("alt")) alt = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("alt_shift")) alt_shift = Integer.parseInt(parser.getAttributeValue(i));
                    if (parser.getAttributeName(i).equals("alt_popup")) alt_popup = parser.getAttributeValue(i);
                    if (parser.getAttributeName(i).equals("alt_shift_popup")) alt_shift_popup = parser.getAttributeValue(i);
                }

                if(scan_code != 0){
                    KeyVariants keyVariants = keyLayoutsHashMap.get(scan_code);
                    if(keyVariants == null)
                    {
                        keyVariants = new KeyVariants();
                        keyLayoutsHashMap.put(scan_code, keyVariants);
                    }

                    if(alt != 0 && keyVariants.alt == 0)
                        keyVariants.alt = alt;
                    if(alt_shift != 0 && keyVariants.alt_shift == 0)
                        keyVariants.alt_shift = alt_shift;
                    if(!alt_popup.isEmpty() && keyVariants.alt_popup.isEmpty())
                        keyVariants.alt_popup = alt_popup;
                    if(!alt_shift_popup.isEmpty() && keyVariants.alt_shift_popup.isEmpty())
                        keyVariants.alt_shift_popup = alt_popup;
                }
                parser.next();
            }
        } catch (Throwable t) {
            Log.e(KeyboardBaseKeyLogic.TAG2, "ERROR LOADING XML KEYBOARD LAYOUT "+ t);
        }
    }

    public void ChangeLayout() {
        CurrentLanguageListIndex++;
        if(CurrentLanguageListIndex > LangListCount - 1) CurrentLanguageListIndex = 0;
        isEnglishKb = CurrentLanguageListIndex == 0;
    }

    public KeyboardLayout GetCurrentKeyboardLayout(){
        return KeyboardLayoutList.get(CurrentLanguageListIndex);
    }

    public int KeyToCharCode(int key, boolean alt_press, boolean shift_press, boolean is_double_press)
    {
        int result;
        KeyVariants keyVariants = KeyboardLayoutList.get(CurrentLanguageListIndex).KeyVariantsMap.get(key);
        if(keyVariants == null)
            return 0;
        if (alt_press && shift_press && keyVariants.alt_shift != 0) {
            result = keyVariants.alt_shift;
        } else if (alt_press && keyVariants.alt != 0) {
            result = keyVariants.alt;
        } else if (is_double_press && shift_press && keyVariants.double_press_shift != 0) {
            result = keyVariants.double_press_shift;
        } else if (is_double_press && keyVariants.double_press != 0) {
            result = keyVariants.double_press;
        } else if (shift_press && keyVariants.one_press_shift != 0) {
            result = keyVariants.one_press_shift;
        } else {
            result = keyVariants.one_press;
        }

        return result;
    }

    public int KeyToAltPopup(int key) {
        KeyVariants keyVariants = KeyboardLayoutList.get(CurrentLanguageListIndex).KeyVariantsMap.get(key);
        if(keyVariants == null)
            return 0;
        if(keyVariants.alt_popup == null || keyVariants.alt_popup.isEmpty())
            return 0;
        return keyVariants.alt_popup.charAt(0);
    }

    public KeyboardLayoutRes[] LoadKeyboardLayoutsRes(Resources resources, Context context) {
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
                int iconCapsResId = resources.getIdentifier(iconCapsRes, "mipmap", context.getPackageName());
                int iconOneShiftResId = resources.getIdentifier(iconOneShiftRes, "mipmap", context.getPackageName());
                int iconLittleResId = resources.getIdentifier(iconLittleRes, "mipmap", context.getPackageName());

                keyboardLayoutResArray.add(new KeyboardLayoutRes(name, layoutResId, iconCapsResId, iconLittleResId, iconOneShiftResId));
                parser.next();
            }

            return keyboardLayoutResArray.toArray(new KeyboardLayoutRes[keyboardLayoutResArray.size()]);

        } catch (Throwable t) {
            Log.e(KeyboardBaseKeyLogic.TAG2, "ERROR LOADING XML KEYBOARD LAYOUT "+ t);
        }
        return keyboardLayoutResArray.toArray(new KeyboardLayoutRes[keyboardLayoutResArray.size()]);
    }
}
