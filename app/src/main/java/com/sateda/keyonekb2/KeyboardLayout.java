package com.sateda.keyonekb2;

import android.view.View;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;


public class KeyboardLayout {

    @JsonProperty(index=10)
    public String KeyboardName = "";

    @JsonProperty(index=20)
    public String AltModeLayout = "";

    @JsonProperty(index=30)
    public String SymModeLayout = "";
    public int SymXmlId = 0;

    public KeyboardLayoutOptions Resources;

    @JsonProperty(index=40)
    public Collection<KeyVariants> KeyMapping;

    public static class KeyVariants {
        @JsonProperty(index=10)
        public int KeyCode = 0;
        @JsonProperty(index=20)
        public Character SinglePress = null;
        @JsonProperty(index=30)
        public Character SinglePressShiftMode = null;
        @JsonProperty(index=40)
        public Character DoublePress = null;
        @JsonProperty(index=50)
        public Character DoublePressShiftMode = null;
        @JsonProperty(index=60)
        public Character SinglePressAltMode = null;
        @JsonProperty(index=70)
        public Character SinglePressAltShiftMode = null;
        @JsonProperty(index=80)
        public String AltMoreVariants = null;
    }

    public static class KeyboardLayoutOptions {

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
        IconRes IconLowercaseRes;
        @JsonProperty(index=40)
        String IconFirstShift;
        IconRes IconFirstShiftRes;
        @JsonProperty(index=50)
        String IconCapslock;
        IconRes IconCapsRes;
        @JsonProperty(index=60)
        String CustomKeyboardMechanics;

        int id = 0;

        public KeyboardLayoutOptions() {
            IconCapsRes = new IconRes();
            IconFirstShiftRes = new IconRes();
            IconLowercaseRes = new IconRes();
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
}
