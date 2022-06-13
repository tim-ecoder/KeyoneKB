package com.sateda.keyonekb2;

public class KeyboardLayoutRes {
    String OptionsName;
    int XmlResId;
    int IconCapsResId;
    int IconFirstShiftResId;
    int IconLittleResId;

    public KeyboardLayoutRes(String optionsName, int layoutResId, int iconCapsResId, int iconLittleResId, int iconOneShiftResId) {
        OptionsName = optionsName;
        XmlResId = layoutResId;
        IconCapsResId = iconCapsResId;
        IconFirstShiftResId = iconOneShiftResId;
        IconLittleResId = iconLittleResId;
    }
}
