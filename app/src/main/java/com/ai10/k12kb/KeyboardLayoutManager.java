package com.ai10.k12kb;

import android.content.Context;
import android.content.res.Resources;
import android.inputmethodservice.Keyboard;
import android.os.Build;
import android.util.Log;

import com.ai10.k12kb.prediction.LanguagePacks;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.InputStream;

import java.util.*;
import java.util.regex.Pattern;

import static com.ai10.k12kb.FileJsonUtils.DeserializeFromJsonApplyPatches;
import static com.ai10.k12kb.InputMethodServiceCoreKeyPress.TAG2;
import static com.ai10.k12kb.K12KbSettings.RES_KEYBOARD_LAYOUTS;

public class KeyboardLayoutManager {

    public static KeyboardLayoutManager Instance = null;
    public ArrayList<KeyboardLayout> KeyboardLayoutList = new ArrayList<>();

    public HashMap<String, ArrayList<KeyboardLayout.KeyVariants>> KeyboardAltLayouts = new HashMap<>();


    private int CurrentLanguageListIndex = 0;
    private int LangListCount = 0;


    HashMap<String, Keyboard> symKeyboardsHashMap = new HashMap<>();



    public synchronized void Initialize(ArrayList<KeyboardLayout.KeyboardLayoutOptions> activeLayouts, Resources resources, Context context) throws Exception {

        Instance = this;
        KeyboardLayout currentLayout = null;
        String LOAD_STAGE="";

        try {
            for (KeyboardLayout.KeyboardLayoutOptions layout : activeLayouts) {
                LangListCount++;
                LOAD_STAGE = "DeserializeFromJson(layout.KeyboardMapping): "+layout.KeyboardMapping;
                currentLayout = DeserializeFromJsonApplyPatches(layout.KeyboardMapping, new TypeReference<KeyboardLayout>() {}, context);

                for (KeyboardLayout.KeyVariants kv : currentLayout.KeyMapping) {
                    if (kv.KeyCodeInt == 0 && kv.KeyCode != null && !kv.KeyCode.isEmpty()) {
                        LOAD_STAGE = "FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(kv.KeyCode): "+kv.KeyCode+" AT LAYOUT: "+layout.KeyboardMapping;
                        kv.KeyCodeInt = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(kv.KeyCode);
                    }
                }
                LOAD_STAGE = "resources.getIdentifier(currentLayout.SymModeLayout): "+currentLayout.SymModeLayout+" AT LAYOUT: "+layout.KeyboardMapping;
                currentLayout.SymXmlId = resources.getIdentifier(currentLayout.SymModeLayout, "xml", context.getPackageName());
                if(currentLayout.SymXmlId == 0)
                    throw new Exception("SYMBOL KEYBOARD RESOURCE NOT FOUND: "+currentLayout.SymModeLayout);
                LoadAltLayout(resources, context, currentLayout);

                currentLayout.Resources = layout;
                KeyboardLayoutList.add(currentLayout);
                //AddSymKeyboard(currentLayout.SymXmlId, context);

            }
        } catch(Throwable ex) {
            throw new Exception("INITIALIZE KEYBOARD LAYOUTS ERROR ON STAGE "+LOAD_STAGE+" ERROR: "+ex.toString());
        }
    }

    private void LoadAltLayout(Resources resources, Context context, KeyboardLayout currentLayout) throws Exception {
        String LOAD_STAGE="";
        //TODO: Можно подкешировать, чтобы быстрее грузилась клава
        try {
            LOAD_STAGE = "DeserializeFromJson(currentLayout.AltModeLayout): "+currentLayout.AltModeLayout;
            Collection<KeyboardLayout.KeyVariants> list = DeserializeFromJsonApplyPatches(currentLayout.AltModeLayout, new TypeReference<Collection<KeyboardLayout.KeyVariants>>() {
            }, context);

            for (KeyboardLayout.KeyVariants kv : list) {
                if (kv.KeyCodeInt == 0 && kv.KeyCode != null && !kv.KeyCode.isEmpty()) {
                    LOAD_STAGE = "FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(kv.KeyCode): "+kv.KeyCode;
                    kv.KeyCodeInt = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(kv.KeyCode);
                }
            }
            FillAltVariantsToCurrentLayout(currentLayout, list);
        } catch(Throwable ex) {
            throw new Exception("INITIALIZE ALT LAYOUTS ERROR ON STAGE "+LOAD_STAGE+" ERROR: "+ex.toString());
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

    public synchronized KeyboardLayout GetNextKeyboardLayout(){
        int nextIndex = (CurrentLanguageListIndex + 1) % LangListCount;
        return KeyboardLayoutList.get(nextIndex);
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

    /** Цепочка вариантов буквы для повторных нажатий, null — вариантов нет. */
    public synchronized String KeyToDoublePressVariants(InputMethodServiceCoreKeyPress.KeyPressData keyPressData) {
        KeyboardLayout.KeyVariants keyVariants = getCurKeyVariants(KeyboardLayoutList.get(CurrentLanguageListIndex), keyPressData.KeyCode);
        if(keyVariants == null)
            return null;
        return keyVariants.DoublePressVariants;
    }

    public synchronized int KeyToAltPopup(InputMethodServiceCoreKeyPress.KeyPressData keyPressData) {
        KeyboardLayout.KeyVariants keyVariants = getCurKeyVariants(KeyboardLayoutList.get(CurrentLanguageListIndex), keyPressData.KeyCode);
        if(keyVariants == null)
            return 0;
        if(keyVariants.AltMoreVariants == null || keyVariants.AltMoreVariants.isEmpty())
            return 0;
        return keyVariants.AltMoreVariants.charAt(0);
    }

    public static ArrayList<KeyboardLayout.KeyboardLayoutOptions> LoadKeyboardLayoutsRes(Resources resources, Context context) throws Exception {
        // Load keyboard layouts
        //Открывает R.xml.keyboard_layouts и загружает все настройки клавиатуры

        // Сначала склеиваем реестр из клавиатуры и установленных языковых
        // пакетов, и уже на склейку накладываются js-патчи: патч, дописывающий
        // или правящий раскладку, должен видеть языки из пакетов.
        String merged = FileJsonUtils.MergedJsonArrayWithPacks(RES_KEYBOARD_LAYOUTS, context);
        ArrayList<KeyboardLayout.KeyboardLayoutOptions> keyboardLayoutOptionsArray =
                FileJsonUtils.DeserializeFromJsonApplyPatches(RES_KEYBOARD_LAYOUTS,
                        new TypeReference<ArrayList<KeyboardLayout.KeyboardLayoutOptions>>() {}, context, merged);

        for ( KeyboardLayout.KeyboardLayoutOptions keyboardLayoutOptions : keyboardLayoutOptionsArray) {

            // Значок языка ищется у себя (см. ResolveIcon), флаг — у себя, а
            // затем в пакетах: флаг рисуется нами на панели, поэтому картинку из
            // чужого APK для него взять можно, в отличие от значка статус-бара.
            String lang = LanguageCodeOf(keyboardLayoutOptions);
            ResolveIcon(context, resources, keyboardLayoutOptions.IconCapsRes,
                    keyboardLayoutOptions.IconCapslock, lang, "shift_all");
            ResolveIcon(context, resources, keyboardLayoutOptions.IconFirstShiftRes,
                    keyboardLayoutOptions.IconFirstShift, lang, "shift_first");
            ResolveIcon(context, resources, keyboardLayoutOptions.IconLowercaseRes,
                    keyboardLayoutOptions.IconLowercase, lang, "small");

            keyboardLayoutOptions.FlagResId = resources.getIdentifier(keyboardLayoutOptions.Flag, "drawable", context.getPackageName());
            keyboardLayoutOptions.FlagPackageName = null;
            if (keyboardLayoutOptions.FlagResId == 0) {
                List<String> packs = LanguagePacks.packages(context);
                for (int i = 0; i < packs.size(); i++) {
                    int id = PackResource(context, packs.get(i), keyboardLayoutOptions.Flag, "drawable");
                    if (id != 0) {
                        keyboardLayoutOptions.FlagResId = id;
                        keyboardLayoutOptions.FlagPackageName = packs.get(i);
                        break;
                    }
                }
            }

        }

        return keyboardLayoutOptionsArray;


    }

    /**
     * Код языка раскладки: из реестра, иначе по имени раскладки. Нужен для
     * значка статус-бара, который может жить только в ресурсах клавиатуры.
     */
    private static String LanguageCodeOf(KeyboardLayout.KeyboardLayoutOptions options) {
        if (options.Language != null && !options.Language.isEmpty())
            return options.Language.toLowerCase(Locale.ROOT);
        String name = options.OptionsName != null ? options.OptionsName.toLowerCase(Locale.ROOT) : "";
        if (name.contains("русск") || name.contains("russian")) return "ru";
        if (name.contains("украин") || name.contains("ukrain")) return "uk";
        if (name.contains("français") || name.contains("french")) return "fr";
        if (name.contains("deutsch") || name.contains("german")) return "de";
        if (name.contains("español") || name.contains("spanish")) return "es";
        if (name.contains("eesti") || name.contains("estonian")) return "et";
        if (name.contains("latvi")) return "lv";
        return "en";
    }

    /**
     * Значок языка. Порядок: названный ресурс клавиатуры, затем нарисованный
     * заранее значок по коду языка, и лишь потом ресурсы пакета. Средний шаг
     * важен: значок в слоте статус-бара система читает из APK клавиатуры, и
     * картинку из пакета туда передать нельзя вообще.
     */
    private static void ResolveIcon(Context context, Resources resources,
                                    KeyboardLayout.KeyboardLayoutOptions.IconRes iconRes,
                                    String name, String languageCode, String state) {
        iconRes.DrawableResId = resources.getIdentifier(name, "drawable", context.getPackageName());
        iconRes.MipmapResId = resources.getIdentifier(name, "mipmap", context.getPackageName());
        iconRes.PackageName = null;
        if (iconRes.DrawableResId != 0 || iconRes.MipmapResId != 0)
            return;

        String generated = "ic_lang_" + languageCode + "_" + state;
        int drawableByLang = resources.getIdentifier(generated, "drawable", context.getPackageName());
        if (drawableByLang != 0) {
            iconRes.DrawableResId = drawableByLang;
            iconRes.MipmapResId = resources.getIdentifier(generated, "mipmap", context.getPackageName());
            return;
        }
        List<String> packs = LanguagePacks.packages(context);
        for (int i = 0; i < packs.size(); i++) {
            int drawable = PackResource(context, packs.get(i), name, "drawable");
            int mipmap = PackResource(context, packs.get(i), name, "mipmap");
            if (drawable != 0 || mipmap != 0) {
                iconRes.DrawableResId = drawable;
                iconRes.MipmapResId = mipmap;
                iconRes.PackageName = packs.get(i);
                Log.d(TAG2, "Значок " + name + " взят из пакета " + packs.get(i)
                        + " drawable=" + drawable + " mipmap=" + mipmap);
                return;
            }
        }
        Log.w(TAG2, "Значок " + name + " не найден ни у себя, ни в пакетах");
    }

    private static int PackResource(Context context, String pkg, String name, String type) {
        try {
            Context pc = context.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY);
            return pc.getResources().getIdentifier(name, type, pkg);
        } catch (Throwable ex) {
            return 0;
        }
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
