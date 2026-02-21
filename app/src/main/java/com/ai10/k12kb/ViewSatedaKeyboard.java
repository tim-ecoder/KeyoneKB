package com.ai10.k12kb;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;
import android.widget.PopupWindow;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.ai10.k12kb.InputMethodServiceCoreKeyPress.TAG2;

public class ViewSatedaKeyboard extends KeyboardView {

    public static final int SYM_PADDING_X = 84;
    public static final int SYM_PADDING_Y = 108;
    public static final int SYM2_PADDING_X = 56;
    public static final int SYM2_PADDING_Y = 66;

    public static final int NAV_PADDING_X = 37;
    public static final int NAV_PADDING_Y = 39;


    enum SatedaKeyboardMode {
        SwipePanel,
        NavPad,
        SymPad,
        EmojiPad
    }

    private SatedaKeyboardMode mode;

    private SymPadKeyExtraData CurSymPadKeyPopup;

    private String lang = "";
    private String draw_lang = "";
    private int flagResId = 0;
    private boolean modeSwipeSingleShift = false;
    boolean modeSwipeAltMode = false;
    boolean modeSwipeSingleAltMode = false;
    private boolean modeNavFn = false;
    private boolean showFlag = false;

    private K12KbIME mService;

    private OverKeyboardPopupWindow mPopupKeyboard;
    private int mPopupLayout;
    private ViewPopupScreenKeyboard mMiniKeyboard;

    private Context context;
    private AttributeSet attrs;

    private final int screenHeightY;
    private final int screenWidthX;
    private final float scaleHeightY;
    private final float scaleWidthX;


    /***
     * Swipe panel height in pixels. Need to be synchronized with space_empty.xml android:keyHeight value
     */
    public static int BASE_HEIGHT = 70;


    public ViewSatedaKeyboard(Context context, AttributeSet attrs) {
        super(context, attrs);

        mode = SatedaKeyboardMode.SwipePanel;

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

        if(ScanCodeKeyCodeMapping == null)
            LoadMappingFile(context);
    }

    public void setService(K12KbIME listener) {
        mService = listener;
        mMiniKeyboard = new ViewPopupScreenKeyboard(context,attrs);
    }


    public static HashMap<String, Double> ScanCodeKeyCodeMapping;

    private static void LoadMappingFile(Context context) {

        ScanCodeKeyCodeMapping = new HashMap<String, Double>();
        Gson gson;
        gson = new GsonBuilder()
                .enableComplexMapKeySerialization()
                .serializeNulls()
                //.setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
                //.setPrettyPrinting()
                //.setVersion(1.0)
                //.excludeFieldsWithoutExposeAnnotation()
                .create();

        Resources resources = context.getResources();

        try {
            // Open output stream
            InputStream is = resources.openRawResource(R.raw.scan_code_key_code);
            // write integers as separated ascii's

            //mapper.writeValue(stream, Instance.KeyboardLayoutList);
            java.io.Reader w = new InputStreamReader(is);
            //gson.toJson(Instance.ScanCodeKeyCodeMapping, w);
            ScanCodeKeyCodeMapping = gson.fromJson(w, ScanCodeKeyCodeMapping.getClass());
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //String baseFolder = getApplicationContext().getFileStreamPath(fileName).getAbsolutePath();
        //btSave.setText(baseFolder);

    }


    public void setFnNavMode(boolean isEnabled){
        modeNavFn = isEnabled;
        invalidateAllKeys();
    }

    public void setShowFlag(boolean isShow){
        showFlag = isShow;
    }

    boolean resized = false;
    public void setSwipePanelMode(){
        if(!resized)
        {
            double mod = (double)getKeyboard().getHeight() / BASE_HEIGHT;
            ((SatedaKeyboard)getKeyboard()).changeKeyHeight(mod);
            resized = true;
        }
        mode = SatedaKeyboardMode.SwipePanel;
    }



    public void setAltMode(boolean single) {
        modeSwipeSingleShift = false;

        if(single) {
            modeSwipeSingleAltMode = true;
            modeSwipeAltMode = false;
        }
        else {
            modeSwipeAltMode = true;
            modeSwipeSingleAltMode = false;
        }
    }

    public void setShiftFirst(){
        modeSwipeSingleShift = true;
        modeSwipeAltMode = false;
        modeSwipeSingleAltMode = false;
        draw_lang = lang;
    }

    public void setShiftAll(){
        modeSwipeSingleShift = false;
        modeSwipeAltMode = false;
        modeSwipeSingleAltMode = false;
        draw_lang = lang.toUpperCase();
    }

    public void notShift(){
        modeSwipeSingleShift = false;
        modeSwipeAltMode = false;
        modeSwipeSingleAltMode = false;
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


    private static class SymPadKeyExtraData {
        Keyboard.Key Key;
        String Label;
        String LabelAltPopup;
        int LabelX;
        int LabelY;
        String AltPopup;
        String AltPopupLabel;
    }

    private ArrayList<SymPadKeyExtraData> SymPadKeyExtraDataList = new ArrayList<SymPadKeyExtraData>();
    private ArrayList<SymPadKeyExtraData> SymPadAltKeyExtraDataList = new ArrayList<SymPadKeyExtraData>();

    public void prepareSymAltLayer(KeyboardLayout keyboardLayout, KeyboardLayout defaultKeyboardLayout, boolean isAltShift){

        modeNavFn = false;
        mode = SatedaKeyboardMode.SymPad;

        if(!isAltShift) {
            CurSymKeyExtraDataList = SymPadKeyExtraDataList;
            //if(!SymPadKeyExtraDataList.isEmpty())
            //    return;
            SymPadKeyExtraDataList.clear();
            List<Keyboard.Key> keys = getKeyboard().getKeys();
            for(Keyboard.Key key: keys) {
                if (key == null)
                    continue;
                // Skip special keys (like -7 emoji key) so they keep their code
                if (key.codes[0] < 0)
                    continue;
                KeyboardLayout.KeyVariants keyVariants = null;
                Double keyCode = ScanCodeKeyCodeMapping.get(String.format("%d", key.codes[0]));
                if (keyCode == null) {
                    Log.e(TAG2, "SCAN_CODE NOT MAPPED " + key.codes[0]);
                    continue;
                } else {
                    keyVariants = KeyboardLayoutManager.getCurKeyVariants(keyboardLayout, keyCode.intValue());
                }

                SymPadKeyExtraData spk =  new SymPadKeyExtraData();
                spk.Key = key;
                spk.LabelX = key.x + (key.width - scaleX(SYM_PADDING_X));//key:90//pocket:60
                spk.LabelY = key.y + scaleY(SYM_PADDING_Y);//100//55

                KeyboardLayout.KeyVariants defaultKeyVariants = KeyboardLayoutManager.getCurKeyVariants(defaultKeyboardLayout, keyCode.intValue());
                spk.Label = (defaultKeyVariants != null && defaultKeyVariants.SinglePressShiftMode != null)
                        ? defaultKeyVariants.SinglePressShiftMode.toString()
                        : keyVariants.SinglePressShiftMode.toString();

                if (keyVariants.AltMoreVariants != null && !keyVariants.AltMoreVariants.isEmpty()) {
                    spk.LabelAltPopup = Character.toString(keyVariants.AltMoreVariants.charAt(0));
                } else
                    spk.LabelAltPopup = "";

                spk.AltPopupLabel = String.valueOf((char) keyVariants.SinglePressAltMode);
                spk.AltPopup = keyVariants.AltMoreVariants;

                key.codes[0] = keyVariants.SinglePressAltMode;
                key.label = String.valueOf((char) keyVariants.SinglePressAltMode);
                SymPadKeyExtraDataList.add(spk);
            }

        } else {
            CurSymKeyExtraDataList = SymPadAltKeyExtraDataList;

            //if(!SymPadAltKeyExtraDataList.isEmpty())
            //    return;

            SymPadAltKeyExtraDataList.clear();
            List<Keyboard.Key> keys = getKeyboard().getKeys();
            for(Keyboard.Key key: keys) {
                if (key == null)
                    continue;
                // Skip special keys (like -7 emoji key) so they keep their code
                if (key.codes[0] < 0)
                    continue;
                KeyboardLayout.KeyVariants keyVariants = null;
                Double keyCode = ScanCodeKeyCodeMapping.get(String.format("%d", key.codes[0]));
                if (keyCode == null) {
                    Log.e(TAG2, "SCAN_CODE NOT MAPPED " + key.codes[0]);
                    continue;
                } else {
                    keyVariants = KeyboardLayoutManager.getCurKeyVariants(keyboardLayout, keyCode.intValue());
                }

                SymPadKeyExtraData spk =  new SymPadKeyExtraData();
                spk.Key = key;
                spk.LabelX = key.x + (key.width - scaleX(SYM_PADDING_X));//key:90//pocket:60
                spk.LabelY = key.y + scaleY(SYM_PADDING_Y);//100//55

                KeyboardLayout.KeyVariants defaultKeyVariants = KeyboardLayoutManager.getCurKeyVariants(defaultKeyboardLayout, keyCode.intValue());
                spk.Label = (defaultKeyVariants != null && defaultKeyVariants.SinglePressShiftMode != null)
                        ? defaultKeyVariants.SinglePressShiftMode.toString()
                        : keyVariants.SinglePressShiftMode.toString();

                if (keyVariants.AltMoreVariants != null && !keyVariants.AltMoreVariants.isEmpty()) {
                    spk.LabelAltPopup = Character.toString(keyVariants.AltMoreVariants.charAt(0));
                } else
                    spk.LabelAltPopup = "";
                spk.AltPopup = keyVariants.AltMoreVariants;
                spk.AltPopupLabel = String.valueOf((char) keyVariants.SinglePressAltShiftMode);

                key.codes[0] = keyVariants.SinglePressAltShiftMode;
                key.label = String.valueOf((char) keyVariants.SinglePressAltShiftMode);

                SymPadAltKeyExtraDataList.add(spk);
            }
        }
    }

    protected static class NavKeyExtraData {
        Keyboard.Key Key;
        String KeyLabel;
        int KeyLabelX;
        int KeyLabelY;
    }

    private ArrayList<NavKeyExtraData> NavKeyDataList = new ArrayList<NavKeyExtraData>();

    public void prepareNavigationLayer(){

        mode = SatedaKeyboardMode.NavPad;

        if(!NavKeyDataList.isEmpty())
            return;

        List<Keyboard.Key> keys = getKeyboard().getKeys();

        for(Keyboard.Key key: keys) {
            NavKeyExtraData nkd = new NavKeyExtraData();
            nkd.Key = key;
            nkd.KeyLabel = String.valueOf(key.popupCharacters);
            nkd.KeyLabelX = key.x + scaleX(NAV_PADDING_X);
            nkd.KeyLabelY = key.y + scaleY(NAV_PADDING_Y);
            NavKeyDataList.add(nkd);
        }
    }
    
    // --- Emoji Pad ---
    private int emojiPage = 0;
    private HashMap<Integer, String> emojiKeyMap = new HashMap<>();

    public void prepareEmojiLayer(int page) {
        mode = SatedaKeyboardMode.EmojiPad;
        emojiPage = page;
        emojiKeyMap.clear();

        String[] emojis = EmojiData.getPage(page);
        List<Keyboard.Key> keys = getKeyboard().getKeys();
        int emojiIndex = 0;

        for (Keyboard.Key key : keys) {
            if (key.codes == null || key.codes.length == 0) continue;
            int code = key.codes[0];
            if (code == -7 || code == -5 || code == -4) continue;

            if (emojiIndex < emojis.length) {
                emojiKeyMap.put(code, emojis[emojiIndex]);
                key.label = " ";
                emojiIndex++;
            }
        }

        for (Keyboard.Key key : keys) {
            if (key.codes != null && key.codes.length > 0 && key.codes[0] == -7) {
                key.label = (page < EmojiData.getPageCount() - 1) ? "\u25B6" : "SYM";
                key.icon = null;
            }
        }

        invalidateAllKeys();
    }

    public boolean isEmojiMode() {
        return mode == SatedaKeyboardMode.EmojiPad;
    }

    public int getEmojiPage() {
        return emojiPage;
    }

    public String getEmojiForKey(int keyCode) {
        return emojiKeyMap.get(keyCode);
    }

    private int scaleX(int x) {
        return Math.round(scaleWidthX*x);
    }

    private int scaleY(int y) {
        return Math.round(scaleHeightY*y);
    }

    @Override
    public boolean onLongPress(Keyboard.Key popupKey) {
        //super.onLongPress(popupKey);
        Log.d(TAG2, "onLongPress "+popupKey.label);

        if(mode != SatedaKeyboardMode.SymPad)
            return mode == SatedaKeyboardMode.EmojiPad;

        int popupX = 0;

        SymPadKeyExtraData curSpk = null;
        for (SymPadKeyExtraData spk: CurSymKeyExtraDataList) {
            if(spk.AltPopupLabel != null
                && popupKey.label != null
                && spk.AltPopupLabel.equals(popupKey.label))
                curSpk = spk;
        }


        if(curSpk == null || curSpk.AltPopup == null) return false;

        CurSymPadKeyPopup = curSpk;

        Keyboard keyboard;
        keyboard = new Keyboard(getContext(), R.layout.keyboard,
                curSpk.AltPopup, -1, getPaddingLeft() + getPaddingRight());

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
        mPopupKeyboard.setHeight(keyboard.getHeight()+40);

        popupX = popupKey.x+(popupKey.width/2) - keyboard.getMinWidth()/2;
        if(popupX < getWidth()/10)
            popupX = getWidth()/10;
        if(popupX+keyboard.getMinWidth() > getWidth() - (getWidth()/10))
            popupX = getWidth() - (getWidth()/10) - keyboard.getMinWidth();

        mPopupKeyboard.showAtLocation(this, Gravity.NO_GRAVITY, popupX, popupKey.y - scaleY(100));
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
                mService.onKey(CurSymPadKeyPopup.AltPopup.charAt(mMiniKeyboard.getCurrentIndex()), null);
            }
            mPopupKeyboard.dismiss();
            invalidate();
        }
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

    ArrayList<SymPadKeyExtraData> CurSymKeyExtraDataList;

    @Override
    public void onDraw(Canvas canvas) {
        //Log.d(TAG2, "onDraw(Canvas canvas)");
        super.onDraw(canvas);
        mService.LastOnDraw = SystemClock.uptimeMillis();

        float startDrawLine, finishDrawLine;
        int height = getKeyboard().getHeight();



        Paint paint_white = new Paint();
        paint_white.setTextAlign(Paint.Align.CENTER);
        paint_white.setTextSize(Math.round(scaleWidthX*40));
        paint_white.setColor(Color.WHITE);

        Paint paint_gray = new Paint();
        paint_gray.setTextAlign(Paint.Align.CENTER);
        paint_gray.setTextSize(Math.round(scaleWidthX*28));
        paint_gray.setColor(Color.GRAY);

        Paint paint_red = new Paint();
        paint_red.setTextAlign(Paint.Align.CENTER);
        paint_red.setTextSize(Math.round(scaleWidthX*32));
        paint_red.setColor(Color.CYAN);

        Paint paint_blue = new Paint();
        paint_blue.setColor(Color.BLUE);

        List<Keyboard.Key> keys = getKeyboard().getKeys();

        if(mode == SatedaKeyboardMode.SwipePanel) {

            Keyboard.Key key = FindKey(keys, 32);

            //key.height = 30 * (getKeyboard()).getHeight() / BASE_HEIGHT;

            canvas.drawText(draw_lang, key.x + (key.width/2), key.y + (height/2 + scaleY(20)), paint_white);

            float[] measuredWidth = new float[1];
            paint_white.breakText(draw_lang, true, 800, measuredWidth);
            startDrawLine = key.x + (key.width/2)-(measuredWidth[0]/2);
            //finishDrawLine = key.x + (key.width/2)+(measuredWidth[0]/2);
            if(modeSwipeSingleShift || modeSwipeSingleAltMode){
                paint_white.breakText(draw_lang.substring(0,1), true, 800, measuredWidth);
                finishDrawLine = startDrawLine+measuredWidth[0];
                canvas.drawRect(startDrawLine, key.y + (height/2 + scaleY(25)), finishDrawLine, key.y + (height/2 + scaleY(28)), paint_white);
            }


            if(showFlag && !modeSwipeAltMode && !modeSwipeSingleAltMode) {
                // Show flag icon
                try {
                    Drawable langIcon = getResources().getDrawable(flagResId == 0 ? R.drawable.ic_flag_gb_col : flagResId);
                    //canvas.drawBitmap(IconsHelper.drawableToBitmap(langIcon), key.x + (key.width / 2) - scaleX(235), key.y + (height/2 - scaleY(24)), paint_white);

                    canvas.drawBitmap(IconsHelper.drawableToBitmap(langIcon), startDrawLine - scaleX(100), key.y + (height/2 - scaleY(24)), paint_white);

                } catch (Throwable ex) {
                    Log.d(TAG2, "Show flag icon exception: " + ex);
                }
            }
        }
        else if(mode == SatedaKeyboardMode.SymPad) {

            int alt3deltaX = scaleX(SYM2_PADDING_X);
            int alt3deltaY = scaleY(SYM2_PADDING_Y);

            for (SymPadKeyExtraData spk: CurSymKeyExtraDataList) {
                canvas.drawText(spk.Label, spk.LabelX, spk.LabelY, paint_gray);
                if(spk.LabelAltPopup != null
                && !spk.LabelAltPopup.isEmpty()) {
                    canvas.drawText(spk.LabelAltPopup, spk.LabelX + alt3deltaX, spk.LabelY - alt3deltaY, paint_red);
                }
            }


            //если отображено меню сверху - прикрыть панель темной полоской
            if(mPopupKeyboard.isShowing()) {
                Paint paint = new Paint();
                paint.setColor((int) ( 0.5* 0xFF) << 24);
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            }
        }
        else if(mode == SatedaKeyboardMode.NavPad) {

            Keyboard.Key key = FindKey(keys, -7);

            startDrawLine = key.x + (key.width / 3);
            finishDrawLine = key.x + (key.width / 3 * 2);
            int ydelta1 = Math.round(83 * scaleHeightY);
            int ydelta2 = Math.round(88 * scaleHeightY);
            if(modeNavFn) {
                canvas.drawRect(startDrawLine, key.y + ydelta1, finishDrawLine, key.y + ydelta2, paint_blue);
            }else{
                canvas.drawRect(startDrawLine, key.y + ydelta1, finishDrawLine, key.y + ydelta2, paint_gray);
            }

            for (NavKeyExtraData nkd: NavKeyDataList) {
                canvas.drawText(nkd.KeyLabel, nkd.KeyLabelX, nkd.KeyLabelY, paint_gray);
            }
        }
        else if(mode == SatedaKeyboardMode.EmojiPad) {

            Paint emojiPaint = new Paint();
            emojiPaint.setTextAlign(Paint.Align.CENTER);
            emojiPaint.setTextSize(Math.round(scaleWidthX * 50));
            emojiPaint.setAntiAlias(true);

            for (Keyboard.Key key : keys) {
                if (key.codes == null || key.codes.length == 0) continue;
                String emoji = emojiKeyMap.get(key.codes[0]);
                if (emoji != null) {
                    canvas.drawText(emoji, key.x + key.width / 2f,
                            key.y + key.height / 2f + Math.round(scaleWidthX * 18), emojiPaint);
                }
            }

            Paint pagePaint = new Paint();
            pagePaint.setTextAlign(Paint.Align.CENTER);
            pagePaint.setTextSize(Math.round(scaleWidthX * 22));
            pagePaint.setColor(Color.GRAY);

            Keyboard.Key toggleKey = FindKey(keys, -7);
            if (toggleKey != null) {
                String pageText = (emojiPage + 1) + "/" + EmojiData.getPageCount();
                canvas.drawText(pageText, toggleKey.x + toggleKey.width / 2f,
                        toggleKey.y + toggleKey.height - Math.round(scaleWidthX * 4), pagePaint);
            }
        }
        else {
            Log.e(TAG2,"Unknown SatedaKeyboardMode "+mode);
        }
    }

    private Keyboard.Key FindKey(List<Keyboard.Key> keys, int key1) {
        for(Keyboard.Key key: keys) {
            if (key.label == null)
                continue;
            if (key.codes == null || key.codes.length == 0)
                continue;
            if (key.codes[0] == key1)
                return key;
        }
        return null;
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
