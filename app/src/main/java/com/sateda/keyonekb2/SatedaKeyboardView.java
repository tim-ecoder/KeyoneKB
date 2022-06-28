package com.sateda.keyonekb2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;

import java.util.List;

import static com.sateda.keyonekb2.KeyboardCoreKeyPress.TAG2;

public class SatedaKeyboardView extends KeyboardView {

    private static final int MAX_KEY_COUNT = 50;

    private static final int KEY_Q = 16;
    private static final int KEY_W = 17;
    private static final int KEY_E = 18;
    private static final int KEY_R = 19;
    private static final int KEY_T = 20;
    private static final int KEY_Y = 21;
    private static final int KEY_U = 22;
    private static final int KEY_I = 23;
    private static final int KEY_O = 24;
    private static final int KEY_P = 25;

    private static final int KEY_A = 30;
    private static final int KEY_S = 31;
    private static final int KEY_D = 32;
    private static final int KEY_F = 33;
    private static final int KEY_G = 34;
    private static final int KEY_H = 35;
    private static final int KEY_J = 36;
    private static final int KEY_K = 37;
    private static final int KEY_L = 38;

    private static final int KEY_Z = 44;
    private static final int KEY_X = 45;
    private static final int KEY_C = 46;
    private static final int KEY_V = 47;
    private static final int KEY_B = 48;
    private static final int KEY_N = 49;
    private static final int KEY_M = 50;
    private static final int KEY_DOLLAR = 5;

    private int[] KeyLabel_x;
    private int[] KeyLabel_y;
    private String[] KeyLabel;
    private String[] altPopup;
    private String[] altPopupLabel;
    private int indexAltPopup;
    private int max_keys = 0;

    private boolean popupOnCenter = true;

    private String lang = "";
    private String draw_lang = "";
    private boolean alt = false;
    private boolean shiftFirst = false;
    private boolean showSymbol = false;
    private boolean fnSymbol = false;

    private boolean pref_flag = false;

    private KeyoneIME mService;

    private OverKeyboardPopupWindow mPopupKeyboard;
    private int mPopupLayout;
    private MoreKeysKeyboardView mMiniKeyboard;

    private Context context;
    private AttributeSet attrs;

    public SatedaKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        KeyLabel = new String[MAX_KEY_COUNT];
        KeyLabel_x = new int[MAX_KEY_COUNT];
        KeyLabel_y = new int[MAX_KEY_COUNT];
        altPopup = new String[MAX_KEY_COUNT];
        altPopupLabel = new String[MAX_KEY_COUNT];

        this.context = context;
        this.attrs = attrs;

        mPopupLayout = R.layout.keyboard;

        mPopupKeyboard = new OverKeyboardPopupWindow(context, this) {
            @NonNull
            @Override
            public View onCreateView(LayoutInflater inflater) {
                return null;
            }

            @Override
            public void onViewCreated(View view) {

            }
        };
        mPopupKeyboard.setBackgroundDrawable(null);
    }

    public void setService(KeyoneIME listener) {
        mService = listener;
        mMiniKeyboard = new MoreKeysKeyboardView(context,attrs);
    }

    public void showFlag(boolean isShow){
        pref_flag = isShow;
    }

    public void setLetterKB(){alt = false; showSymbol = false; }

    public void setAlt(){ alt = true; showSymbol = false; }

    public void setShiftFirst(){
        shiftFirst = true;
        draw_lang = lang;
    }

    public void setShiftAll(){
        shiftFirst = false;
        draw_lang = lang.toUpperCase();
    }

    public void notShift(){
        shiftFirst = false;
        draw_lang = lang;
    }

    public void setLang(String lang){
        this.lang = lang;
        draw_lang = lang;
    }

    private boolean isKeyboard(int keyCode1) {
        for(int keyCode2 : KeyboardCoreKeyPress.KEY2_LATIN_ALPHABET_KEYS_CODES) {
            if(keyCode1 == keyCode2)
                return true;
        }
        return false;
    }

    public void setAltLayer(KeyboardLayout keybordLayout, boolean isAltShift){
        alt = true;
        showSymbol = true;
        fnSymbol = false;
        List<Keyboard.Key> keys = getKeyboard().getKeys();
        int arr_inc = 0;
        int i = 0;
        // TODO: Это все требуется рефакторинга чтобы не перерисовывать каждый раз клавиатуру
        for(KeyboardLayout.KeyVariants keyVariants : keybordLayout.KeyMapping){
            KeyLabel[i] = "";
            KeyLabel_x[i] = 0;
            KeyLabel_y[i] = 0;
            if(!isAltShift) {
                altPopup[i] = keyVariants.AltMoreVariants;
                altPopupLabel[i] = String.valueOf((char) keyVariants.SinglePressAltMode);
            }
            else {
                altPopup[i] = keyVariants.AltShiftMoreVariants;
                altPopupLabel[i] = String.valueOf((char) keyVariants.SinglePressAltShiftMode);
            }
            i++;
        }

        for(Keyboard.Key key: keys) {
            if(key == null)
                continue;
            KeyboardLayout.KeyVariants keyVariants = null;
            Double keyCode = FileJsonUtils.ScanCodeKeyCodeMapping.get(String.format("%d",key.codes[0]));
            if(keyCode == null) {
                Log.e(TAG2, "SCAN_CODE NOT MAPPED "+key.codes[0]);
                continue;
            } else {
                keyVariants = KeyboardLayoutManager.getCurKeyVariants(keybordLayout, keyCode.intValue());
            }

            if(key.label.equals(" ")
                    && isKeyboard(keyVariants.KeyCode)){

                KeyLabel_x[arr_inc] = key.x + (key.width - 25);
                KeyLabel_y[arr_inc] = key.y + 40;

                if (key.codes[0] == KEY_Q) { KeyLabel[arr_inc] = "Q"; }
                else if (key.codes[0] == KEY_W) { KeyLabel[arr_inc] = "W"; }
                else if (key.codes[0] == KEY_E) { KeyLabel[arr_inc] = "E"; }
                else if (key.codes[0] == KEY_R) { KeyLabel[arr_inc] = "R"; }
                else if (key.codes[0] == KEY_T) { KeyLabel[arr_inc] = "T"; }
                else if (key.codes[0] == KEY_Y) { KeyLabel[arr_inc] = "Y"; }
                else if (key.codes[0] == KEY_U) { KeyLabel[arr_inc] = "U"; }
                else if (key.codes[0] == KEY_I) { KeyLabel[arr_inc] = "I"; }
                else if (key.codes[0] == KEY_O) { KeyLabel[arr_inc] = "O"; }
                else if (key.codes[0] == KEY_P) { KeyLabel[arr_inc] = "P"; }
                else if (key.codes[0] == KEY_A) { KeyLabel[arr_inc] = "A"; }
                else if (key.codes[0] == KEY_S) { KeyLabel[arr_inc] = "S"; }
                else if (key.codes[0] == KEY_D) { KeyLabel[arr_inc] = "D"; }
                else if (key.codes[0] == KEY_F) { KeyLabel[arr_inc] = "F"; }
                else if (key.codes[0] == KEY_G) { KeyLabel[arr_inc] = "G"; }
                else if (key.codes[0] == KEY_H) { KeyLabel[arr_inc] = "H"; }
                else if (key.codes[0] == KEY_J) { KeyLabel[arr_inc] = "J"; }
                else if (key.codes[0] == KEY_K) { KeyLabel[arr_inc] = "K"; }
                else if (key.codes[0] == KEY_L) { KeyLabel[arr_inc] = "L"; }
                else if (key.codes[0] == KEY_Z) { KeyLabel[arr_inc] = "Z"; }
                else if (key.codes[0] == KEY_X) { KeyLabel[arr_inc] = "X"; }
                else if (key.codes[0] == KEY_C) { KeyLabel[arr_inc] = "C"; }
                else if (key.codes[0] == KEY_V) { KeyLabel[arr_inc] = "V"; }
                else if (key.codes[0] == KEY_B) { KeyLabel[arr_inc] = "B"; }
                else if (key.codes[0] == KEY_N) { KeyLabel[arr_inc] = "N"; }
                else if (key.codes[0] == KEY_M) { KeyLabel[arr_inc] = "M"; }
                else if (key.codes[0] == KEY_DOLLAR) { KeyLabel[arr_inc] = "$"; }

                if(!isAltShift) {
                    key.codes[0] = keyVariants.SinglePressAltMode;
                    key.label = String.valueOf((char) keyVariants.SinglePressAltMode);
                }
                else {
                    key.codes[0] = keyVariants.SinglePressAltShiftMode;
                    key.label = String.valueOf((char) keyVariants.SinglePressAltShiftMode);
                }

                arr_inc++;

            }
        }
    }

    public void SetFnKeyboardMode(boolean isEnabled){
        fnSymbol = isEnabled;
        invalidateAllKeys();
    }

    public void setNavigationLayer(){
        alt = true;
        showSymbol = true;
        List<Keyboard.Key> keys = getKeyboard().getKeys();
        max_keys = 11;
        int arr_inc = 0;
        for(int i = 0; i < MAX_KEY_COUNT; i++){
            KeyLabel[i] = "";
            KeyLabel_x[i] = 0;
            KeyLabel_y[i] = 0;
        }

        for(Keyboard.Key key: keys) {

            if (key.codes[0] == 111) { KeyLabel[arr_inc] = "Q"; } //ESC
            if (key.codes[0] == 122) { KeyLabel[arr_inc] = "W/Y"; } //HOME
            if (key.codes[0] == 19)  { KeyLabel[arr_inc] = "E/U"; } //Arrow Up
            if (key.codes[0] == 123) { KeyLabel[arr_inc] = "R/I"; } //END
            if (key.codes[0] == 92)  { KeyLabel[arr_inc] = "T/O"; } //Page Up
            if (key.codes[0] == -7)  { KeyLabel[arr_inc] = "P"; } //FN

            if (key.codes[0] == 61)  { KeyLabel[arr_inc] = "A"; } //TAB
            if (key.codes[0] == 21)  { KeyLabel[arr_inc] = "S/H"; } //Arrow Left
            if (key.codes[0] == 20)  { KeyLabel[arr_inc] = "D/J"; } //Arrow Down
            if (key.codes[0] == 22)  { KeyLabel[arr_inc] = "F/K"; } //Arrow Right
            if (key.codes[0] == 93)  { KeyLabel[arr_inc] = "G/L"; } //Page Down

            KeyLabel_x[arr_inc] = key.x + (key.width - 25);
            KeyLabel_y[arr_inc] = key.y + 40;
            arr_inc++;
        }
    }

    @Override
    public boolean onLongPress(Keyboard.Key popupKey) {
        //super.onLongPress(popupKey);
        Log.d(TAG2, "onLongPress "+popupKey.label);

        if(!showSymbol) return false;

        int popupX = 0;

        for(int i = 0; i < altPopupLabel.length; i++){
            if(altPopupLabel[i].equals(popupKey.label)){
                indexAltPopup = i;
                break;
            }
        }

        if(altPopup[indexAltPopup] == null || altPopup[indexAltPopup].equals("")) return false;

        Keyboard keyboard;
        keyboard = new Keyboard(getContext(), R.layout.keyboard,
                altPopup[indexAltPopup], -1, getPaddingLeft() + getPaddingRight());

        mMiniKeyboard.setKeyboard(keyboard);

        mMiniKeyboard.setOnKeyboardActionListener(new OnKeyboardActionListener() {
            public void onKey(int primaryCode, int[] keyCodes) {  }
            public void onText(CharSequence text) { }
            public void swipeLeft() { }
            public void swipeRight() { }
            public void swipeUp() { }
            public void swipeDown() { }
            public void onPress(int primaryCode) {
                Log.d(TAG2, "onPress primaryCode "+primaryCode);
            }
            public void onRelease(int primaryCode) {
            }
        });

        mPopupKeyboard.setContentView(mMiniKeyboard);
        mPopupKeyboard.setWidth(keyboard.getMinWidth());
        mPopupKeyboard.setHeight(keyboard.getHeight());

        popupX = popupKey.x+(popupKey.width/2) - keyboard.getMinWidth()/2;
        if(popupX < getWidth()/10) popupX = getWidth()/10;
        if(popupX+keyboard.getMinWidth() > getWidth() - (getWidth()/10)) popupX = getWidth() - (getWidth()/10) - keyboard.getMinWidth();

        mPopupKeyboard.showAtLocation(this, Gravity.NO_GRAVITY, popupX, 0);
        mMiniKeyboard.startXindex(popupKey.x+(popupKey.width/2), this.getWidth(), keyboard.getMinWidth(), popupX);

        invalidate();

        return true;
    }

    public void coordsToIndexKey(float x) {
        mMiniKeyboard.coordsToIndexKey(x);
    }

    public void hidePopup(boolean returnKey) {
        if(mPopupKeyboard.isShowing()){
            if(returnKey) {
                mService.onKey(altPopup[indexAltPopup].charAt(mMiniKeyboard.getCurrentIndex()), null);
            }
            mPopupKeyboard.dismiss();
            invalidate();
        }
    }

    @Override
    public void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        float startDrawLine, finishDrawLine;
        int height = getKeyboard().getHeight();

        Paint paint_white = new Paint();
        paint_white.setTextAlign(Paint.Align.CENTER);
        paint_white.setTextSize(40);
        paint_white.setColor(Color.WHITE);

        Paint paint_gray = new Paint();
        paint_gray.setTextAlign(Paint.Align.CENTER);
        paint_gray.setTextSize(28);
        paint_gray.setColor(Color.GRAY);

        Paint paint_blue = new Paint();
        paint_blue.setColor(Color.BLUE);


        // название текущего языка
        List<Keyboard.Key> keys = getKeyboard().getKeys();
        for(Keyboard.Key key: keys) {
            if(key.label != null) {
                if (!alt && key.codes[0] == 32){
                    canvas.drawText(draw_lang, key.x + (key.width/2), key.y + (height/2 + 20), paint_white);

                    float[] measuredWidth = new float[1];
                    paint_white.breakText(draw_lang, true, 800, measuredWidth);
                    startDrawLine = key.x + (key.width/2)-(measuredWidth[0]/2);
                    finishDrawLine = key.x + (key.width/2)+(measuredWidth[0]/2);
                    if(shiftFirst){
                        paint_white.breakText(draw_lang.substring(0,1), true, 800, measuredWidth);
                        finishDrawLine = startDrawLine+measuredWidth[0];
                    }

                    if(shiftFirst) canvas.drawRect(startDrawLine, key.y + (height/2 + 25), finishDrawLine, key.y + (height/2 + 28), paint_white);

                    if(pref_flag) {
                        // Show flag icon
                        try {
                            /// TODO Use enums for current language
                            Drawable langIcon = getResources().getDrawable(lang.compareTo("English") == 0
                                    ? R.drawable.ic_flag_gb_col
                                    : lang.compareTo("Русский") == 0
                                    ? R.drawable.ic_flag_russia_col
                                    : R.drawable.ic_flag_ukraine_col);
                            //Log.d("Tlt", "lang: " + lang + "; draw_lang: " + draw_lang);
                            canvas.drawBitmap(IconsHelper.drawableToBitmap(langIcon), key.x + (key.width / 2) - 210, key.y + (height/2 - 28), paint_white);
                        } catch (Exception ex) {
                            Log.d("Tlt", "!ex: " + ex);
                        }
                    }
                }else if (alt && key.codes[0] == 32){
                    canvas.drawText(lang, key.x + (key.width/2), key.y + (height/2 + 20), paint_white);
                }
                if(showSymbol && key.codes[0] == -7){
                    startDrawLine = key.x + (key.width / 3);
                    finishDrawLine = key.x + (key.width / 3 * 2);
                    if(fnSymbol) {
                        canvas.drawRect(startDrawLine, key.y + 83, finishDrawLine, key.y + 88, paint_blue);
                    }else{
                        canvas.drawRect(startDrawLine, key.y + 83, finishDrawLine, key.y + 88, paint_gray);
                    }
                }
            }
        }

        // отображение подписи букв, эквивалентных кнопкам
        if (showSymbol){
            for(int i = 0; i < max_keys; i++){
                canvas.drawText(KeyLabel[i], KeyLabel_x[i], KeyLabel_y[i], paint_gray);
            }
        }

        //если отображено меню сверху - прикрыть панель темной полоской
        if(mPopupKeyboard.isShowing()) {
            Paint paint = new Paint();
            paint.setColor((int) ( 0.5* 0xFF) << 24);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
    }
}
