package com.sateda.keyonekb2;


import android.view.View;

public class KeyboardLayoutRes {

    public static class IconRes {
        int MipmapResId;
        int DrawableResId;
    }

    public static IconRes CreateIconRes(int mipmapResId, int drawableResId) {
        IconRes res = new IconRes();
        res.MipmapResId = mipmapResId;
        res.DrawableResId = drawableResId;
        return res;
    }

    String OptionsName;

    String XmlRes;
    int XmlResId;

    IconRes IconCapsRes;
    IconRes IconFirstShiftRes;
    IconRes IconLittleRes;

    int id = 0;

    private KeyboardLayoutRes() {}

    public KeyboardLayoutRes(String optionsName, int layoutResId, String xmlRes) {
        XmlRes = xmlRes;
        OptionsName = optionsName;
        XmlResId = layoutResId;
        IconCapsRes = new IconRes();
        IconFirstShiftRes = new IconRes();
        IconLittleRes = new IconRes();

    }

    String getPreferenceName() {
        return "GENERATED_PREF_KEYBOARD_LAYOUT_" + XmlRes;
    }

    int getId() {
        if(id == 0) {
            id = View.generateViewId();
        }
        return id;
    }
}
