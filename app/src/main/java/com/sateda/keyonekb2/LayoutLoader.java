package com.sateda.keyonekb2;

import android.content.res.Resources;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashMap;

public class LayoutLoader {
    static int  LoadLayoutsAndIcons(boolean lang_ru_on, boolean lang_translit_ru_on, boolean lang_ua_on, ArrayList<KeybordLayout> KeybordLayoutList, Resources resources) {
        KeybordLayout currentLayout;
        int LangListCount = 1;
        currentLayout = LoadLayoutAndCache(R.xml.english_hw, LangListCount - 1, KeybordLayoutList, resources);
        currentLayout.IconCaps = R.mipmap.ic_eng_shift_all;
        currentLayout.IconCapsTouch = R.mipmap.ic_eng_shift_all_touch;
        currentLayout.IconFirstShift = R.mipmap.ic_eng_shift_first;
        currentLayout.IconFirstShiftTouch = R.mipmap.ic_eng_shift_first_touch;
        currentLayout.IconLittle = R.mipmap.ic_eng_small;
        currentLayout.IconLittleTouch = R.mipmap.ic_eng_small_touch;

        if(lang_ru_on){
            LangListCount++;
            currentLayout = LoadLayoutAndCache(R.xml.russian_hw, LangListCount - 1, KeybordLayoutList, resources);
            currentLayout.IconCaps = R.mipmap.ic_rus_shift_all;
            currentLayout.IconCapsTouch = R.mipmap.ic_rus_shift_all_touch;
            currentLayout.IconFirstShift = R.mipmap.ic_rus_shift_first;
            currentLayout.IconFirstShiftTouch = R.mipmap.ic_rus_shift_first_touch;
            currentLayout.IconLittle = R.mipmap.ic_rus_small;
            currentLayout.IconLittleTouch = R.mipmap.ic_rus_small_touch;
        }
        if(lang_translit_ru_on){
            LangListCount++;
            currentLayout = LoadLayoutAndCache(R.xml.russian_translit_hw, LangListCount - 1, KeybordLayoutList, resources);
            currentLayout.IconCaps = R.mipmap.ic_rus_shift_all;
            currentLayout.IconCapsTouch = R.mipmap.ic_rus_shift_all_touch;
            currentLayout.IconFirstShift = R.mipmap.ic_rus_shift_first;
            currentLayout.IconFirstShiftTouch = R.mipmap.ic_rus_shift_first_touch;
            currentLayout.IconLittle = R.mipmap.ic_rus_small;
            currentLayout.IconLittleTouch = R.mipmap.ic_rus_small_touch;
        }
        if(lang_ua_on){
            LangListCount++;
            currentLayout = LoadLayoutAndCache(R.xml.ukraine_hw, LangListCount - 1, KeybordLayoutList, resources);
            currentLayout.IconCaps = R.mipmap.ic_ukr_shift_all;
            currentLayout.IconCapsTouch = R.mipmap.ic_ukr_shift_all_touch;
            currentLayout.IconFirstShift = R.mipmap.ic_ukr_shift_first;
            currentLayout.IconFirstShiftTouch = R.mipmap.ic_ukr_shift_first_touch;
            currentLayout.IconLittle = R.mipmap.ic_ukr_small;
            currentLayout.IconLittleTouch = R.mipmap.ic_ukr_small_touch;
        }
        return LangListCount;
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
}
