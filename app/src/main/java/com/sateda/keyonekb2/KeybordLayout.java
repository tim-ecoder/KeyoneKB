package com.sateda.keyonekb2;

import java.util.HashMap;

public class KeybordLayout {
    public String LanguageOnScreenNaming = "";
    public int XmlId = 0;
    public int Id = 0;
    public int IconCaps;
    public int IconCapsTouch;
    public int IconFirstShift;
    public int IconFirstShiftTouch;
    public int IconLittle;
    public int IconLittleTouch;

    public HashMap<Integer, KeyVariants> KeyVariantsMap = new HashMap<Integer, KeyVariants>();
}
