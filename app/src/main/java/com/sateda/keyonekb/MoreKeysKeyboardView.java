package com.sateda.keyonekb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.support.annotation.NonNull;
import android.text.Spanned;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import java.util.List;

import static android.content.ContentValues.TAG;

public class MoreKeysKeyboardView extends KeyboardView {


    private int countKeys;
    private int indexPopupPressed;
    private float touchX;
    private float leftX;
    private int keyWidht;
    private int totalWidht;



    public MoreKeysKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        keyWidht = 0;
        totalWidht = 0;

    }

    public MoreKeysKeyboardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    public void startXindex(float x, int totalWidht, int keyboardWidth, float leftX) {
        List<Keyboard.Key> keys = getKeyboard().getKeys();
        countKeys = keys.size();
        touchX = x;
        this.totalWidht = totalWidht;
        this.leftX = leftX;
        keyWidht = keyboardWidth / countKeys;
        int startX = (totalWidht-keyboardWidth)/2;
        if((int)touchX <= (startX + keyWidht)){
            indexPopupPressed = 0;
        }else if((int)touchX >= (startX + keyboardWidth-keyWidht)){
            indexPopupPressed = countKeys-1;
        }else{
            indexPopupPressed = ((int)touchX-(int)leftX)/keyWidht;
        }
        Log.d(TAG, "startXindex keys "+countKeys);
    }

    public int getCurrentIndex() {
        return indexPopupPressed;
    }

    public void coordsToIndexKey(float x) {

        if(keyWidht <= 0) return;
        //Log.d(TAG, "coordsToIndexKey leftX "+leftX+" totalWidht "+totalWidht);

        if(indexPopupPressed != ((int)x-(int)leftX)/keyWidht){
            indexPopupPressed = ((int)x-(int)leftX)/keyWidht;
            if(indexPopupPressed > countKeys-1) indexPopupPressed = countKeys-1;
            if(indexPopupPressed < 0) indexPopupPressed = 0;
            invalidateAllKeys();
            Log.d(TAG, "coordsToIndexKey indexPopupPressed "+indexPopupPressed);
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
