package com.sateda.keyonekb2;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.inputmethodservice.Keyboard;

public class SatedaKeyboard  extends Keyboard {

    private int currentHeight;

    public SatedaKeyboard(Context context, int xmlLayoutResId, int pref_swipe_panel_height) {
        super(context, xmlLayoutResId);
        currentHeight = ViewSatedaKeyboard.BASE_HEIGHT + pref_swipe_panel_height * 10;
    }

    @Override
    public int getHeight() {
        return currentHeight;
    }

    @Override
    protected Key createKeyFromXml(Resources res, Row parent, int x, int y,
                                   XmlResourceParser parser) {
        Key key = new Key(res, parent, x, y, parser);

        return key;
    }

    public void changeKeyHeight(double height_modifier)
    {
        int height = 0;
        for(Keyboard.Key key : getKeys()) {
            key.height *= height_modifier;
            key.y *= height_modifier;
            height = key.height;
        }
        setKeyHeight(height);
        getNearestKeys(0, 0); //somehow adding this fixed a weird bug where bottom row keys could not be pressed if keyboard height is too tall.. from the Keyboard source code seems like calling this will recalculate some values used in keypress detection calculation
    }
}
