package com.sateda.keyonekb2;

import android.content.Context;
import android.graphics.Canvas;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;


import android.util.AttributeSet;
import android.util.Log;

import java.util.List;

import static com.sateda.keyonekb2.InputMethodServiceCoreKeyPress.TAG2;


public class ViewPopupScreenKeyboard extends KeyboardView {


    private int countKeys;
    private int indexPopupPressed;
    private float touchX;
    private float leftX;
    private int keyWidth;
    private int totalWidth;



    public ViewPopupScreenKeyboard(Context context, AttributeSet attrs) {
        super(context, attrs);
        keyWidth = 0;
        totalWidth = 0;

    }

    public ViewPopupScreenKeyboard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    public void startXindex(float x, int totalWidth, int keyboardWidth, float leftX) {
        List<Keyboard.Key> keys = getKeyboard().getKeys();
        countKeys = keys.size();
        touchX = x;
        this.totalWidth = totalWidth;
        this.leftX = leftX;
        keyWidth = keyboardWidth / countKeys;
        int startX = (totalWidth-keyboardWidth)/2;
        if((int)touchX <= (startX + keyWidth)) {
            indexPopupPressed = 0;
        } else if((int)touchX >= (startX + keyboardWidth- keyWidth)) {
            indexPopupPressed = countKeys-1;
        } else{
            indexPopupPressed = ((int)touchX-(int)leftX)/ keyWidth;
        }
        Log.d(TAG2, "startXindex keys "+countKeys);
    }

    public int getCurrentIndex() {
        return indexPopupPressed;
    }

    public void coordsToIndexKey(float x) {

        if(keyWidth <= 0) return;
        //Log.d(TAG, "coordsToIndexKey leftX "+leftX+" totalWidht "+totalWidht);

        if(indexPopupPressed != ((int)x-(int)leftX)/ keyWidth){
            indexPopupPressed = ((int)x-(int)leftX)/ keyWidth;
            if(indexPopupPressed > countKeys-1) indexPopupPressed = countKeys-1;
            if(indexPopupPressed < 0) indexPopupPressed = 0;
            invalidateAllKeys();
            Log.d(TAG2, "coordsToIndexKey indexPopupPressed "+indexPopupPressed);
        }
    }

    @Override
    public void onDraw(Canvas canvas) {

        int n = 0;
        List<Keyboard.Key> keys = getKeyboard().getKeys();
        for(Keyboard.Key key: keys) {
            if(n == indexPopupPressed){
                key.pressed = true;
            }else{
                key.pressed = false;
            }
            n++;
        }

        super.onDraw(canvas);

    }
}
