package com.sateda.keyonekb2;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;
import android.widget.PopupWindow;

import java.util.List;

import static com.sateda.keyonekb2.InputMethodServiceCoreKeyPress.TAG2;

public class ViewSatedaKeyboard extends KeyboardView {

    private static final int MAX_KEY_COUNT = 50;

    private String[] altPopup;
    private String[] altPopupLabel;
    private int indexAltPopup;
    private int max_keys = 0;

    private boolean popupOnCenter = true;

    private String lang = "";
    private String draw_lang = "";

    private int flagResId = 0;
    private boolean alt = false;
    private boolean shiftFirst = false;
    private boolean showSymbol = false;
    private boolean fnSymbol = false;

    private boolean pref_flag = false;

    private KeyoneIME mService;

    private OverKeyboardPopupWindow mPopupKeyboard;
    private int mPopupLayout;
    private ViewPopupScreenKeyboard mMiniKeyboard;

    private Context context;
    private AttributeSet attrs;

    private int[] nav_KeyLabel_x;
    private int[] nav_KeyLabel_y;
    private String[] nav_KeyLabel;
    private final int screenHeightY;
    private final int screenWidthX;
    private final float scaleHeightY;
    private final float scaleWidthX;

    public ViewSatedaKeyboard(Context context, AttributeSet attrs) {
        super(context, attrs);

        DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
        screenHeightY = dm.heightPixels;
        screenWidthX = dm.widthPixels;
        scaleHeightY = screenHeightY / 1620f;
        scaleWidthX = screenWidthX / 1080f;


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
        mMiniKeyboard = new ViewPopupScreenKeyboard(context,attrs);
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

    public void setLang(String lang, int flagResId){
        this.lang = lang;
        draw_lang = lang;
        this.flagResId = flagResId;
    }

    private boolean isKeyboard(int keyCode1) {
        for(int keyCode2 : InputMethodServiceCoreKeyPress.KEY2_LATIN_ALPHABET_KEYS_CODES) {
            if(keyCode1 == keyCode2)
                return true;
        }
        return false;
    }

    boolean isSymLoaded = false;
    boolean isAltLoaded = false;

    int[] sym_KeyLabel_x;
    int[] sym_KeyLabel_y;
    String[] sym_KeyLabel;
    String[] sym_KeyLabelAltPopup;

    boolean modeSym = false;

    public void loadAltLayer(KeyboardLayout keyboardLayout, boolean isAltShift){

        max_keys = 27;
        alt = true;
        showSymbol = true;
        fnSymbol = false;
        modeSym = true;
        modeNav = false;

        /** кешировать не будем пока т.к. клавиатура дает возможность делать разные альт символы в привязке к разной раскладке
         * если закешировать то запомнится только первая раскладка и ее альт-символы
         * Если уж делать кеширование какое-то то в рамках полного переписывания кода SatedaKeyboard */

        //if(isSymLoaded) {
        //    return;
        //}
        sym_KeyLabel = new String[max_keys];
        sym_KeyLabel_x = new int[max_keys];
        sym_KeyLabel_y = new int[max_keys];
        sym_KeyLabelAltPopup = new String[max_keys];
        altPopup = new String[max_keys];
        altPopupLabel = new String[max_keys];


        List<Keyboard.Key> keys = getKeyboard().getKeys();
        int arr_inc = 0;
        int i = 0;

        for(KeyboardLayout.KeyVariants keyVariants : keyboardLayout.KeyMapping){
            sym_KeyLabel[i] = "";
            sym_KeyLabel_x[i] = 0;
            sym_KeyLabel_y[i] = 0;
            altPopup[i] = keyVariants.AltMoreVariants;

            if(!isAltShift) {
                altPopupLabel[i] = String.valueOf((char) keyVariants.SinglePressAltMode);
            }
            else if (altPopupLabel != null) {
                altPopupLabel[i] = String.valueOf((char) keyVariants.SinglePressAltShiftMode);
            }
            i++;
        }

        for(Keyboard.Key key: keys) {
            if(key == null)
                continue;
            KeyboardLayout.KeyVariants keyVariants = null;
            //TODO: Особенно вот эта дичь
            //Double keyCode = FileJsonUtils.ScanCodeKeyCodeMapping.get(String.format("%d",key.codes[0]));
            Double keyCode = FileJsonUtils.ScanCodeKeyCodeMapping.get(String.format("%d",key.codes[0]));
            if(keyCode == null) {
                Log.e(TAG2, "SCAN_CODE NOT MAPPED "+key.codes[0]);
                continue;
            } else {
                keyVariants = KeyboardLayoutManager.getCurKeyVariants(keyboardLayout, keyCode.intValue());
            }

            if(key.label.equals(" ")
                    && sym_KeyLabel != null
                    && isKeyboard(keyVariants.KeyCodeInt)
                    && keyVariants.SinglePressShiftMode != null
            ) {

                sym_KeyLabel_x[arr_inc] = key.x + (key.width - scaleX(90));//key:90//pocket:60
                sym_KeyLabel_y[arr_inc] = key.y + scaleY(100);//100//55
                sym_KeyLabel[arr_inc] = keyVariants.SinglePressShiftMode.toString();

                if (keyVariants.AltMoreVariants != null && !keyVariants.AltMoreVariants.isEmpty()) {
                    sym_KeyLabelAltPopup[arr_inc] = Character.toString(keyVariants.AltMoreVariants.charAt(0));
                } else
                    sym_KeyLabelAltPopup[arr_inc] = "";

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
        //isSymLoaded = true;

    }

    public void SetFnKeyboardMode(boolean isEnabled){
        fnSymbol = isEnabled;
        invalidateAllKeys();
    }

    boolean navLayerLoaded = false;
    boolean modeNav = false;

    public void setNavigationLayer(){
        max_keys = 12;

        modeSym = false;
        modeNav = true;

        if(navLayerLoaded)
            return;

        nav_KeyLabel = new String[max_keys];
        nav_KeyLabel_x = new int[max_keys];
        nav_KeyLabel_y = new int[max_keys];

        alt = true;
        showSymbol = true;
        List<Keyboard.Key> keys = getKeyboard().getKeys();

        int arr_inc = 0;
        for(int i = 0; i < max_keys; i++){
            nav_KeyLabel[i] = "";
            nav_KeyLabel_x[i] = 0;
            nav_KeyLabel_y[i] = 0;
        }

        for(Keyboard.Key key: keys) {

            if (key.codes[0] == 111) { nav_KeyLabel[arr_inc] = "Q"; } //ESC
            if (key.codes[0] == 122) { nav_KeyLabel[arr_inc] = "W|Y"; } //HOME
            if (key.codes[0] == 19)  { nav_KeyLabel[arr_inc] = "E|U"; } //Arrow Up
            if (key.codes[0] == 123) { nav_KeyLabel[arr_inc] = "R|I"; } //END
            if (key.codes[0] == 92)  { nav_KeyLabel[arr_inc] = "T|O"; } //Page Up
            if (key.codes[0] == -7)  { nav_KeyLabel[arr_inc] = "P"; } //FN

            if (key.codes[0] == 61)  { nav_KeyLabel[arr_inc] = "A"; } //TAB
            if (key.codes[0] == 21)  { nav_KeyLabel[arr_inc] = "S|H"; } //Arrow Left
            if (key.codes[0] == 20)  { nav_KeyLabel[arr_inc] = "D|J"; } //Arrow Down
            if (key.codes[0] == 22)  { nav_KeyLabel[arr_inc] = "F|K"; } //Arrow Right
            if (key.codes[0] == 93)  { nav_KeyLabel[arr_inc] = "G|L"; } //Page Down

            nav_KeyLabel_x[arr_inc] = key.x + scaleX(32);
            nav_KeyLabel_y[arr_inc] = key.y + scaleY(32);
            arr_inc++;
        }
    }
    
    private int scaleX(int x) {
        return Math.round(x*scaleHeightY);
    }
    
    private int scaleY(int y) {
        return Math.round(y * scaleHeightY);
    }

    @Override
    public boolean onLongPress(Keyboard.Key popupKey) {
        //super.onLongPress(popupKey);
        Log.d(TAG2, "onLongPress "+popupKey.label);

        //Не работает TODO: ВЕСЬ КЛАСС ПОДЛЕЖИТ РЕФАКТОРИНГУ
        if(!showSymbol) return false;
        //Работает
        if(!modeSym) return false;

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
    public void onMeasure(int widthMeasureSpec,
                          int heightMeasureSpec) {
        Log.d(TAG2, "onMeasure()");
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void onSizeChanged (int w,
                               int h,
                               int oldw,
                               int oldh) {
        Log.d(TAG2, "onSizeChanged()");
        super.onSizeChanged(w,h,oldw, oldh);
    }

    @Override
    public void onDetachedFromWindow () {
        Log.d(TAG2, "onDetachedFromWindow()");
        super.onDetachedFromWindow();
    }

    @Override
    public void onDraw(Canvas canvas) {
        Log.d(TAG2, "onDraw(Canvas canvas)");
        super.onDraw(canvas);
        mService.LastOnDraw = SystemClock.uptimeMillis();

        float startDrawLine, finishDrawLine;
        int height = getKeyboard().getHeight();



        Paint paint_white = new Paint();
        paint_white.setTextAlign(Paint.Align.CENTER);
        paint_white.setTextSize(Math.round(scaleHeightY*40));
        paint_white.setColor(Color.WHITE);

        Paint paint_gray = new Paint();
        paint_gray.setTextAlign(Paint.Align.CENTER);
        paint_gray.setTextSize(Math.round(scaleHeightY*28));
        paint_gray.setColor(Color.GRAY);

        Paint paint_red = new Paint();
        paint_red.setTextAlign(Paint.Align.CENTER);
        paint_red.setTextSize(Math.round(scaleHeightY*32));
        paint_red.setColor(Color.CYAN);

        Paint paint_blue = new Paint();
        paint_blue.setColor(Color.BLUE);


        // TODO: Это все требует рефакторинга чтобы не перерисовывать каждый раз клавиатуру
        // название текущего языка
        List<Keyboard.Key> keys = getKeyboard().getKeys();
        for(Keyboard.Key key: keys) {
            if(key.label != null) {
                if (!alt && key.codes[0] == 32){
                    canvas.drawText(draw_lang, key.x + (key.width/2), key.y + (height/2 + scaleY(20)), paint_white);

                    float[] measuredWidth = new float[1];
                    paint_white.breakText(draw_lang, true, 800, measuredWidth);
                    startDrawLine = key.x + (key.width/2)-(measuredWidth[0]/2);
                    finishDrawLine = key.x + (key.width/2)+(measuredWidth[0]/2);
                    if(shiftFirst){
                        paint_white.breakText(draw_lang.substring(0,1), true, 800, measuredWidth);
                        finishDrawLine = startDrawLine+measuredWidth[0];
                    }

                    if(shiftFirst) canvas.drawRect(startDrawLine, key.y + (height/2 + scaleY(25)), finishDrawLine, key.y + (height/2 + scaleY(28)), paint_white);

                    if(pref_flag) {
                        // Show flag icon
                        try {

                            Drawable langIcon = getResources().getDrawable(flagResId == 0 ? R.drawable.ic_flag_gb_col : flagResId);
                            canvas.drawBitmap(IconsHelper.drawableToBitmap(langIcon), key.x + (key.width / 2) - scaleX(210), key.y + (height/2 - scaleY(28)), paint_white);
                        } catch (Throwable ex) {
                            Log.d("Tlt", "!ex: " + ex);
                        }
                    }
                }else if (alt && key.codes[0] == 32){
                    canvas.drawText(lang, key.x + (key.width/2), key.y + (height/2 + scaleY(20)), paint_white);
                }
                if(showSymbol && key.codes[0] == -7){
                    startDrawLine = key.x + (key.width / 3);
                    finishDrawLine = key.x + (key.width / 3 * 2);
                    int ydelta1 = Math.round(83 * scaleHeightY);
                    int ydelta2 = Math.round(88 * scaleHeightY);
                    if(fnSymbol) {
                        canvas.drawRect(startDrawLine, key.y + ydelta1, finishDrawLine, key.y + ydelta2, paint_blue);
                    }else{
                        canvas.drawRect(startDrawLine, key.y + ydelta1, finishDrawLine, key.y + ydelta2, paint_gray);
                    }
                }
            }
        }



        // отображение подписи букв, эквивалентных кнопкам
        if (showSymbol){
            int alt3deltaX = scaleX(65);
            int alt3deltaY = scaleY(65);
            if(modeNav)
                for(int i = 0; i < max_keys; i++){
                    canvas.drawText(nav_KeyLabel[i], nav_KeyLabel_x[i], nav_KeyLabel_y[i], paint_gray);
                }
            else if(modeSym)
                for(int i = 0; i < max_keys; i++){
                    canvas.drawText(sym_KeyLabel[i], sym_KeyLabel_x[i], sym_KeyLabel_y[i], paint_gray);
                    if(sym_KeyLabelAltPopup != null
                            && sym_KeyLabelAltPopup[i] != null
                            && sym_KeyLabelAltPopup[i] != "")
                        canvas.drawText(sym_KeyLabelAltPopup[i], sym_KeyLabel_x[i]+ alt3deltaX, sym_KeyLabel_y[i]- alt3deltaY, paint_red);
                }
        }



        //если отображено меню сверху - прикрыть панель темной полоской
        if(mPopupKeyboard.isShowing()) {
            Paint paint = new Paint();
            paint.setColor((int) ( 0.5* 0xFF) << 24);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
    }

    public static class IconsHelper {

        public static Bitmap drawableToBitmap (Drawable drawable) {
            Bitmap bitmap = null;

            if (drawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                if(bitmapDrawable.getBitmap() != null) {
                    return bitmapDrawable.getBitmap();
                }
            }

            if(drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
                bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
            } else {
                bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            }

            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        }
    }

    /**
     * Base class to create popup window that appears over software keyboard.
     */
    public abstract static class OverKeyboardPopupWindow extends PopupWindow
            implements ViewTreeObserver.OnGlobalLayoutListener {

        public interface OnKeyboardHideListener {

            void onKeyboardHide();

        }


        @SuppressWarnings("unchecked")
        public static <S> S getSystemService(final Context context, final String serviceName) {
            return (S) context.getSystemService(serviceName);
        }

        private int mKeyboardHeight = 0;
        private boolean mPendingOpen = false;
        private boolean mKeyboardOpen = false;

        private Context mContext;
        private View mRootView;

        private OnKeyboardHideListener mKeyboardHideListener;

        public OverKeyboardPopupWindow(final Context context, @NonNull final View rootView) {
            super(context);
            mContext = context;
            mRootView = rootView;

            setBackgroundDrawable(null);


            final View view = onCreateView(LayoutInflater.from(context));
            onViewCreated(view);
            setContentView(view);
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

            // Default size
            setSize(mContext.getResources().getDimensionPixelSize(R.dimen.candidate_vertical_padding),
                    WindowManager.LayoutParams.MATCH_PARENT);
            setSizeForSoftKeyboard();
        }

        public void setKeyboardHideListener(final OnKeyboardHideListener keyboardHideListener) {
            mKeyboardHideListener = keyboardHideListener;
        }

        @NonNull
        public Context getContext() {
            return mContext;
        }

        /**
         * Manually set the popup window size
         *
         * @param width  Width of the popup
         * @param height Height of the popup
         */
        public void setSize(final int width, final int height) {
            setWidth(width);
            setHeight(height);
        }

        /**
         * Call this function to resize the emoji popup according to your soft keyboard size
         */
        public void setSizeForSoftKeyboard() {
            final ViewTreeObserver viewTreeObserver = mRootView.getViewTreeObserver();
            viewTreeObserver.addOnGlobalLayoutListener(this);
        }

        @Override
        public void onGlobalLayout() {
            final Rect r = new Rect();
            mRootView.getWindowVisibleDisplayFrame(r);

            final int screenHeight = calculateScreenHeight();
            int heightDifference = screenHeight - (r.bottom - r.top);

            final Resources resources = mContext.getResources();
            final int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                heightDifference -= resources.getDimensionPixelSize(resourceId);
            }

            if (heightDifference > 100) {
                mKeyboardHeight = heightDifference;
                setSize(WindowManager.LayoutParams.MATCH_PARENT, mKeyboardHeight);

                mKeyboardOpen = true;
                if (mPendingOpen) {
                    showAtBottom();
                    mPendingOpen = false;
                }
            } else {
                if (mKeyboardOpen && mKeyboardHideListener != null) {
                    mKeyboardHideListener.onKeyboardHide();
                }
                mKeyboardOpen = false;
            }
        }

        private int calculateScreenHeight() {
            final WindowManager wm = getSystemService(mContext, Context.WINDOW_SERVICE);
            final Display display = wm.getDefaultDisplay();
            final Point size = new Point();
            display.getSize(size);
            return size.y;
        }

        /**
         * Use this function to show the popup.
         * NOTE: Since, the soft keyboard sizes are variable on different android devices, the
         * library needs you to open the soft keyboard at least once before calling this function.
         * If that is not possible see showAtBottomPending() function.
         */
        public void showAtBottom() {
            showAtLocation(mRootView, Gravity.BOTTOM, 0, 0);
        }

        /**
         * Use this function when the soft keyboard has not been opened yet. This
         * will show the popup after the keyboard is up next time.
         * Generally, you will be calling InputMethodManager.showSoftInput function after
         * calling this function.
         */
        public void showAtBottomPending() {
            if (isKeyboardOpen()) {
                showAtBottom();
            } else {
                mPendingOpen = true;
            }
        }

        /**
         * @return Returns true if the soft keyboard is open, false otherwise.
         */
        public boolean isKeyboardOpen() {
            return mKeyboardOpen;
        }

        /**
         * @return keyboard height in pixels
         */
        public int getKeyboardHeight() {
            return mKeyboardHeight;
        }

        @NonNull
        public abstract View onCreateView(final LayoutInflater inflater);

        public abstract void onViewCreated(final View view);


    }
}
