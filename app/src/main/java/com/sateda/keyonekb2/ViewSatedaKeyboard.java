package com.sateda.keyonekb2;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
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
    private int alt3deltaX;
    private int alt3deltaY;

    public ViewSatedaKeyboard(Context context, AttributeSet attrs) {
        super(context, attrs);

        altPopup = new String[MAX_KEY_COUNT];
        altPopupLabel = new String[MAX_KEY_COUNT];

        DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
        screenHeightY = dm.heightPixels;
        screenWidthX = dm.widthPixels;
        scaleHeightY = screenHeightY / 1620f;
        scaleWidthX = screenWidthX / 1080f;
        alt3deltaX = Math.round(65*scaleWidthX);
        alt3deltaY = Math.round(65*scaleHeightY);

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

    public void setLang(String lang){
        this.lang = lang;
        draw_lang = lang;
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
    int[] alt_KeyLabel_x;
    int[] alt_KeyLabel_y;
    String[] alt_KeyLabel;
    String[] alt_KeyLabelAltPopup;

    boolean modeAlt = false;

    boolean modeSym = false;

    public void setAltLayer(KeyboardLayout keyboardLayout, boolean isAltShift){

        max_keys = 27;
        if(isAltShift) {
            modeSym = true;
            modeAlt = false;
            modeNav = false;

            if(isSymLoaded) {
                return;
            }
            sym_KeyLabel = new String[max_keys];
            sym_KeyLabel_x = new int[max_keys];
            sym_KeyLabel_y = new int[max_keys];
            sym_KeyLabelAltPopup = new String[max_keys];

            LoadExtraKeyScreenData(keyboardLayout, isAltShift, sym_KeyLabel_x, sym_KeyLabel_y, sym_KeyLabel, sym_KeyLabelAltPopup);
            isSymLoaded = true;
        } else {
            modeSym = false;
            modeAlt = true;
            modeNav = false;

            if(isAltLoaded) {
                return;
            }

            alt_KeyLabel = new String[max_keys];
            alt_KeyLabel_x = new int[max_keys];
            alt_KeyLabel_y = new int[max_keys];
            alt_KeyLabelAltPopup = new String[max_keys];

            LoadExtraKeyScreenData(keyboardLayout, isAltShift, alt_KeyLabel_x, alt_KeyLabel_y, alt_KeyLabel, alt_KeyLabelAltPopup);
            isAltLoaded = true;
        }
    }

    private void LoadExtraKeyScreenData(KeyboardLayout keyboardLayout, boolean isAltShift, int[] _KeyLabel_x, int[] _KeyLabel_y, String[] _KeyLabel, String[] _KeyLabelAltPopup) {
        alt = true;
        showSymbol = true;
        fnSymbol = false;

        List<Keyboard.Key> keys = getKeyboard().getKeys();
        int arr_inc = 0;
        int i = 0;
        // TODO: Это все требует рефакторинга чтобы не перерисовывать каждый раз клавиатуру
        for(KeyboardLayout.KeyVariants keyVariants : keyboardLayout.KeyMapping){
            _KeyLabel[i] = "";
            _KeyLabel_x[i] = 0;
            _KeyLabel_y[i] = 0;
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

            if((key.label.equals(" "))
                    && _KeyLabel != null
                    && isKeyboard(keyVariants.KeyCode)
                    && keyVariants.SinglePressShiftMode != null
            ) {

                _KeyLabel_x[arr_inc] = key.x + (key.width - Math.round(90*scaleWidthX));//key:90//pocket:60
                _KeyLabel_y[arr_inc] = key.y + Math.round(100*scaleHeightY);//100//55
                _KeyLabel[arr_inc] = keyVariants.SinglePressShiftMode.toString();

                if (keyVariants.AltMoreVariants != null && !keyVariants.AltMoreVariants.isEmpty()) {
                    _KeyLabelAltPopup[arr_inc] = Character.toString(keyVariants.AltMoreVariants.charAt(0));
                } else
                    _KeyLabelAltPopup[arr_inc] = "";

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

    boolean navLayerLoaded = false;
    boolean modeNav = false;

    public void setNavigationLayer(){
        max_keys = 12;

        modeSym = false;
        modeAlt = false;
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
            if (key.codes[0] == 122) { nav_KeyLabel[arr_inc] = "W/Y"; } //HOME
            if (key.codes[0] == 19)  { nav_KeyLabel[arr_inc] = "E/U"; } //Arrow Up
            if (key.codes[0] == 123) { nav_KeyLabel[arr_inc] = "R/I"; } //END
            if (key.codes[0] == 92)  { nav_KeyLabel[arr_inc] = "T/O"; } //Page Up
            if (key.codes[0] == -7)  { nav_KeyLabel[arr_inc] = "P"; } //FN

            if (key.codes[0] == 61)  { nav_KeyLabel[arr_inc] = "A"; } //TAB
            if (key.codes[0] == 21)  { nav_KeyLabel[arr_inc] = "S/H"; } //Arrow Left
            if (key.codes[0] == 20)  { nav_KeyLabel[arr_inc] = "D/J"; } //Arrow Down
            if (key.codes[0] == 22)  { nav_KeyLabel[arr_inc] = "F/K"; } //Arrow Right
            if (key.codes[0] == 93)  { nav_KeyLabel[arr_inc] = "G/L"; } //Page Down

            nav_KeyLabel_x[arr_inc] = key.x + (key.width - 25);
            nav_KeyLabel_y[arr_inc] = key.y + 40;
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
            if(modeNav)
                for(int i = 0; i < max_keys; i++){
                    canvas.drawText(nav_KeyLabel[i], nav_KeyLabel_x[i], nav_KeyLabel_y[i], paint_gray);
                }
            else if(modeAlt)
                for(int i = 0; i < max_keys; i++){
                    canvas.drawText(alt_KeyLabel[i], alt_KeyLabel_x[i], alt_KeyLabel_y[i], paint_gray);
                    if(alt_KeyLabelAltPopup[i] != null && alt_KeyLabelAltPopup[i] != "")
                        canvas.drawText(alt_KeyLabelAltPopup[i], alt_KeyLabel_x[i]+ alt3deltaX, alt_KeyLabel_y[i]- alt3deltaY, paint_red);
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
