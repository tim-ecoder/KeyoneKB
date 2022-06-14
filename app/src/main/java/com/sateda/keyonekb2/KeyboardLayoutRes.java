package com.sateda.keyonekb2;

public class KeyboardLayoutRes {
    String OptionsName;

    String XmlRes;
    int XmlResId;
    int IconCapsResId;
    int IconFirstShiftResId;
    int IconLittleResId;

    boolean IsActive;

    public KeyboardLayoutRes(String optionsName, int layoutResId, int iconCapsResId, int iconLittleResId, int iconOneShiftResId, String xmlRes) {
        XmlRes = xmlRes;
        OptionsName = optionsName;
        XmlResId = layoutResId;
        IconCapsResId = iconCapsResId;
        IconFirstShiftResId = iconOneShiftResId;
        IconLittleResId = iconLittleResId;
    }

    String getPreferenceName() {
        return "GENERATED_PREF_KEYBOARD_LAYOUT_" + XmlRes;
    }

    int getHash() {
        return XmlRes.hashCode();
    }

    public void setActive() {
        IsActive = true;
    }
}
