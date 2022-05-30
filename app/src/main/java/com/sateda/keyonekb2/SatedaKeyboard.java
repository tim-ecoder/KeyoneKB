package com.sateda.keyonekb2;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.inputmethodservice.Keyboard;

public class SatedaKeyboard  extends Keyboard {
    private int height;
    public SatedaKeyboard(Context context, int xmlLayoutResId) {
        super(context, xmlLayoutResId);
        height = getKeyHeight();
    }

    public SatedaKeyboard(Context context, int xmlLayoutResId, int new_height) {
        super(context, xmlLayoutResId);
        height = new_height;
    }

    public SatedaKeyboard(Context context, int xmlLayoutResId, int modeId, int width, int height) {
        super(context, xmlLayoutResId, modeId, width, height);
    }
/*
    public SatedaKeyboard(Context context, int xmlLayoutResId, int modeId) {
        super(context, xmlLayoutResId, modeId);
    }
*/
    public SatedaKeyboard(Context context, int layoutTemplateResId, CharSequence characters, int columns, int horizontalPadding) {
        super(context, layoutTemplateResId, characters, columns, horizontalPadding);
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    protected Key createKeyFromXml(Resources res, Row parent, int x, int y,
                                   XmlResourceParser parser) {
        Key key = new Key(res, parent, x, y, parser);

        return key;
    }
}
