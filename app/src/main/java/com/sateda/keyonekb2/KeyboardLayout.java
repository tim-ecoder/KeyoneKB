package com.sateda.keyonekb2;

import java.util.HashMap;

public class KeyboardLayout {
    public String LanguageOnScreenNaming = "";
    public int XmlId = 0;
    public int SymXmlId = 0;
    public int Id = 0;

    public KeyboardLayoutRes Resources;
    public HashMap<Integer, KeyVariants> KeyVariantsMap = new HashMap<>();
}
