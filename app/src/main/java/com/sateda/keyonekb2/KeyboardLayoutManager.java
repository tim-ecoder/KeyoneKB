package com.sateda.keyonekb2;

import android.content.Context;
import android.content.res.Resources;
import android.inputmethodservice.Keyboard;
import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class KeyboardLayoutManager {

    public static KeyboardLayoutManager Instance = null;
    public ArrayList<KeyboardLayout> KeyboardLayoutList = new ArrayList<>();

    public HashMap<String, ArrayList<KeyVariants>> KeyboardAltLayouts = new HashMap<>();

    public HashMap<String, Double> ScanCodeKeyCodeMapping = new HashMap<String, Double>();
    private int CurrentLanguageListIndex = 0;
    private int LangListCount = 0;


    HashMap<String, Keyboard> symKeyboardsHashMap = new HashMap<>();


    public synchronized void Initialize(ArrayList<KeyboardLayoutRes> activeLayouts, Resources resources, Context context) {

        if(Instance != null) {
            return;
        }

        Instance = this;

        LoadMappingFile(context);
        KeyboardLayout currentLayout;

        for (KeyboardLayoutRes layout : activeLayouts) {
            LangListCount++;
            currentLayout = LoadLayoutAndCache(layout.XmlResId, LangListCount - 1, KeyboardLayoutList, resources, context);
            currentLayout.Resources = layout;
            KeyboardLayoutList.add(currentLayout);
            AddSymKeyboard(currentLayout.SymXmlId, context);
        }
    }


    private void LoadMappingFile(Context context) {

        Gson gson;
        gson = new GsonBuilder()
                .enableComplexMapKeySerialization()
                .serializeNulls()
                //.setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
                //.setPrettyPrinting()
                //.setVersion(1.0)
                //.excludeFieldsWithoutExposeAnnotation()
                .create();

        //ObjectMapper mapper = new ObjectMapper();




        String fileName = "scan_code_key_code.json";
        //mapper.writeValue(stream, Instance.KeyboardLayoutList);
        String packageName = context.getPackageName();
        String path = Environment.getExternalStorageDirectory().getAbsolutePath()+ "/Android/data/" + packageName + "/files/";

        try {
            boolean exists = (new File(path)).exists();
            if (!exists) {
                new File(path).mkdirs();
            }
            // Open output stream
            FileInputStream fOut = new FileInputStream(path + fileName);
            // write integers as separated ascii's

            //mapper.writeValue(stream, Instance.KeyboardLayoutList);
            java.io.Reader w = new InputStreamReader(fOut);
            //gson.toJson(Instance.ScanCodeKeyCodeMapping, w);
            Instance.ScanCodeKeyCodeMapping = gson.fromJson(w, Instance.ScanCodeKeyCodeMapping.getClass());
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //String baseFolder = getApplicationContext().getFileStreamPath(fileName).getAbsolutePath();
        //btSave.setText(baseFolder);

    }

    public synchronized Keyboard GetSymKeyboard(boolean isAlt) {
        if(isAlt)
            return symKeyboardsHashMap.get(getSymKbdKey1(GetCurrentKeyboardLayout().SymXmlId));
        return symKeyboardsHashMap.get(getSymKbdKey2(GetCurrentKeyboardLayout().SymXmlId));
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
                    Double intValue = Instance.ScanCodeKeyCodeMapping.get(String.format("%d",scan_code));
                    if(intValue != null) {
                        keyLayout.KeyCode = intValue.intValue();
                    } else {
                        Log.e(KeyPressKeyboardBase.TAG2, "NO KEYCODE FOR SCAN_CODE: "+ scan_code);
                    }
                    keyLayout.one_press = one_press;
                    keyLayout.SinglePress = Character.valueOf((char) one_press);
                    keyLayout.one_press_shift = shift;
                    keyLayout.SinglePressShiftMode = Character.valueOf((char)shift);
                    keyLayout.double_press = double_press;
                    keyLayout.DoublePress = Character.valueOf((char)double_press);
                    keyLayout.double_press_shift = double_press_shift;
                    keyLayout.DoublePressShiftMode = Character.valueOf((char)double_press_shift);
                    keyLayout.alt = alt;
                    keyLayout.SinglePressAltMode = Character.valueOf((char)alt);
                    keyLayout.alt_shift = alt_shift;
                    keyLayout.SinglePressAltShiftMode = Character.valueOf((char)alt_shift);
                    keyLayout.alt_popup = alt_popup;
                    keyLayout.alt_shift_popup = alt_shift_popup;

                    keyboardLayout.KeyVariantsMap.put(scan_code, keyLayout);
                }
                parser.next();
            }
            keyboardLayout.KeyboardName = languageOnScreenNaming;
            keyboardLayout.AltModeLayout = alt_hw;
            keyboardLayout.SymModeLayout = sym_sw_res;
            keyboardLayout.XmlId = keyboardLayoutXmlId;
            keyboardLayoutArrayList.add(currentKeyBoardSetId, keyboardLayout);
        } catch (Throwable t) {
            Log.e(KeyPressKeyboardBase.TAG2, "ERROR LOADING XML KEYBOARD LAYOUT "+ t);
        }

        keyboardLayout.SymXmlId = resources.getIdentifier(sym_sw_res, "xml", context.getPackageName());
        int altHwResId = resources.getIdentifier(alt_hw, "xml", context.getPackageName());
        ArrayList<KeyVariants> list = LoadAltLayout2(keyboardLayout.KeyVariantsMap, resources, altHwResId);
        Instance.KeyboardAltLayouts.put(alt_hw, list);
        keyboardLayout.KeyMapping = keyboardLayout.KeyVariantsMap.values();
        return keyboardLayout;
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

    private static ArrayList<KeyVariants> LoadAltLayout2(HashMap<Integer, KeyVariants> keyLayoutsHashMap, Resources resources, int altHwResId)
    {
        if(altHwResId == 0)
            return null;
        ArrayList<KeyVariants> array = new ArrayList<>();
        int scan_code;
        int key_code = 0;
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
                    Double intValue = Instance.ScanCodeKeyCodeMapping.get(String.format("%d",scan_code));
                    if(intValue != null) {
                        key_code = intValue.intValue();
                        KeyVariants altKeyVariants = new KeyVariants();
                        altKeyVariants.KeyCode = key_code;
                        if(alt != 0 )
                            altKeyVariants.SinglePressAltMode = Character.valueOf((char)alt);
                        if(alt_shift != 0)
                            altKeyVariants.SinglePressShiftMode = Character.valueOf((char)alt_shift);
                        if(!alt_popup.isEmpty())
                            altKeyVariants.AltMoreVariants = alt_popup;
                        if(!alt_shift_popup.isEmpty())
                            altKeyVariants.AltShiftMoreVariants = alt_shift_popup;
                        array.add(altKeyVariants);
                    } else {
                        Log.e(KeyPressKeyboardBase.TAG2, "NO KEYCODE FOR SCAN_CODE: "+ scan_code);
                    }


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
            Log.e(KeyPressKeyboardBase.TAG2, "ERROR LOADING XML KEYBOARD LAYOUT "+ t);
        }
        return array;
    }

    public synchronized void ChangeLayout() {
        CurrentLanguageListIndex++;
        if(CurrentLanguageListIndex > LangListCount - 1) CurrentLanguageListIndex = 0;
    }

    public synchronized KeyboardLayout GetCurrentKeyboardLayout(){
        return KeyboardLayoutList.get(CurrentLanguageListIndex);
    }

    public synchronized int KeyToCharCode(int key, boolean alt_press, boolean shift_press, boolean is_double_press)
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

    public synchronized int KeyToAltPopup(int key) {
        KeyVariants keyVariants = KeyboardLayoutList.get(CurrentLanguageListIndex).KeyVariantsMap.get(key);
        if(keyVariants == null)
            return 0;
        if(keyVariants.alt_popup == null || keyVariants.alt_popup.isEmpty())
            return 0;
        return keyVariants.alt_popup.charAt(0);
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
            Log.e(KeyPressKeyboardBase.TAG2, "ERROR LOADING XML KEYBOARD LAYOUT "+ t);
        }
        return keyboardLayoutResArray;
    }
}
