package com.sateda.keyonekb2;


import android.view.View;
import com.fasterxml.jackson.annotation.JsonProperty;

public class KeyboardLayoutOptions {

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

    @JsonProperty(index=10)
    String OptionsName;
    @JsonProperty(index=20)
    String KeyboardMapping;
    @JsonProperty(index=30)
    String IconLowercase;
    @JsonProperty(index=40)
    String IconFirstShift;
    @JsonProperty(index=50)
    String IconCapslock;

    IconRes IconCapsRes;
    IconRes IconFirstShiftRes;
    IconRes IconLittleRes;

    int id = 0;

    public KeyboardLayoutOptions() {
        IconCapsRes = new IconRes();
        IconFirstShiftRes = new IconRes();
        IconLittleRes = new IconRes();
    }

    String getPreferenceName() {
        return "GENERATED_PREF_KEYBOARD_LAYOUT_" + KeyboardMapping;
    }

    int getId() {
        if(id == 0) {
            id = View.generateViewId();
        }
        return id;
    }
}
