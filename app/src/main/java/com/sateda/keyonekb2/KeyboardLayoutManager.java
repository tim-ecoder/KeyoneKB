package com.sateda.keyonekb2;

import android.content.res.Resources;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashMap;

public class KeyboardLayoutManager {

    public ArrayList<KeybordLayout> KeybordLayoutList = new ArrayList<>();
    private int CurrentLanguageListIndex = 0;
    private int LangListCount = 0;
    public boolean isEnglishKb = false;

    public void Initialize(boolean lang_ru_on, boolean lang_translit_ru_on, boolean lang_ua_on, Resources resources) {
        if(LangListCount != 0)
            return;
        KeybordLayout currentLayout;
        LangListCount = 1;
        currentLayout = LoadLayoutAndCache(R.xml.english_hw, LangListCount - 1, KeybordLayoutList, resources);
        currentLayout.IconCaps = R.mipmap.ic_eng_shift_all;
        currentLayout.IconFirstShift = R.mipmap.ic_eng_shift_first;
        currentLayout.IconLittle = R.mipmap.ic_eng_small;

        if(lang_ru_on){
            LangListCount++;
            currentLayout = LoadLayoutAndCache(R.xml.russian_hw, LangListCount - 1, KeybordLayoutList, resources);
            currentLayout.IconCaps = R.mipmap.ic_rus_shift_all;
            currentLayout.IconFirstShift = R.mipmap.ic_rus_shift_first;
            currentLayout.IconLittle = R.mipmap.ic_rus_small;
        }
        if(lang_translit_ru_on){
            LangListCount++;
            currentLayout = LoadLayoutAndCache(R.xml.russian_translit_hw, LangListCount - 1, KeybordLayoutList, resources);
            currentLayout.IconCaps = R.mipmap.ic_rus_shift_all;
            currentLayout.IconFirstShift = R.mipmap.ic_rus_shift_first;
            currentLayout.IconLittle = R.mipmap.ic_rus_small;
        }
        if(lang_ua_on){
            LangListCount++;
            currentLayout = LoadLayoutAndCache(R.xml.ukraine_hw, LangListCount - 1, KeybordLayoutList, resources);
            currentLayout.IconCaps = R.mipmap.ic_ukr_shift_all;
            currentLayout.IconFirstShift = R.mipmap.ic_ukr_shift_first;
            currentLayout.IconLittle = R.mipmap.ic_ukr_small;
        }
    }

    private static KeybordLayout LoadLayoutAndCache(int xmlId, int currentKeyBoardSetId, ArrayList<KeybordLayout> KeybordLayoutList, Resources resources)
    {

        int scan_code = 0;
        int one_press = 0;
        int double_press = 0;
        int double_press_shift = 0;
        int alt = 0;
        int shift = 0;
        int alt_shift = 0;
        String alt_popup = "";
        String alt_shift_popup = "";
        String languageOnScreenNaming = "";

        KeybordLayout keyboardLayout = new KeybordLayout();
        keyboardLayout.Id = xmlId;
        keyboardLayout.KeyVariantsMap = new HashMap<Integer, KeyVariants>();

        try {
            XmlPullParser parser = resources.getXml(xmlId);

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
            keyboardLayout.XmlId = xmlId;
            KeybordLayoutList.add(currentKeyBoardSetId, keyboardLayout);
        } catch (Throwable t) {
            Log.e(KeyboardBaseKeyLogic.TAG2, "ERROR LOADING XML KEYBOARD LAYOUT "+t.toString());
        }

        LoadAltLayout2(keyboardLayout.KeyVariantsMap, resources);
        return keyboardLayout;
    }

    private static void LoadAltLayout2(HashMap<Integer, KeyVariants> keyLayoutsHashMap, Resources resources)
    {
        int scan_code = 0;
        int alt = 0;
        int alt_shift = 0;
        String alt_popup = "";
        String alt_shift_popup = "";

        try {
            XmlPullParser parser;
            parser = resources.getXml(R.xml.alt_hw);

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
            Log.e(KeyboardBaseKeyLogic.TAG2, "ERROR LOADING XML KEYBOARD LAYOUT "+t.toString());
        }
    }

    public void ChangeLayout() {
        CurrentLanguageListIndex++;
        if(CurrentLanguageListIndex > LangListCount - 1) CurrentLanguageListIndex = 0;
        if(CurrentLanguageListIndex == 0){
            isEnglishKb = true;
        }else{
            isEnglishKb = false;
        }
    }

    public KeybordLayout GetCurrentKeyboardLayout(){
        return KeybordLayoutList.get(CurrentLanguageListIndex);
    }

    public int KeyToCharCode(int key, boolean alt_press, boolean shift_press, boolean is_double_press)
    {
        int result = 0;
        KeyVariants keyVariants = KeybordLayoutList.get(CurrentLanguageListIndex).KeyVariantsMap.get(key);
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
}
