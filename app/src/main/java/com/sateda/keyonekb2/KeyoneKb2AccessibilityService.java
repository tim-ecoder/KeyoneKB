package com.sateda.keyonekb2;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;

import static com.sateda.keyonekb2.FileJsonUtils.LogErrorToGui;


public class KeyoneKb2AccessibilityService extends AccessibilityService {

    public static KeyoneKb2AccessibilityService Instance;
    public static String TAG3 = "KeyoneKb2-AS";

    interface NodeClickableConverter {
        AccessibilityNodeInfo getNode(AccessibilityNodeInfo info);
    }

    public static int STD_EVENTS = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_FOCUSED;

    public final ArrayList<SearchClickPlugin> searchClickPlugins = new ArrayList<>();

    public final ArrayList<SearchClickPlugin> clickerPlugins = new ArrayList<>();

    KeyoneKb2Settings keyoneKb2Settings;

    //ExecutorService executorService;

    ArrayList<String> DefaultSearchWords;



    private final ArrayList<String> SearchClickPackages = new ArrayList<>();

    public static final ArrayList<SearchClickPlugin> TEMP_ADDED_SEARCH_CLICK_PLUGINS = new ArrayList<>();

    KeyoneKb2AccServiceOptions keyoneKb2AccServiceOptions;



    public static class KeyoneKb2AccServiceOptions {
        public static final String ResName = "keyonekb2_as_options";

        public static class MetaKeyPlusKey {
            @JsonProperty(index=10)
            public String MetaKeyCode;
            public int MetaKeyCodeInt;

            @JsonProperty(index=20)
            public String KeyKeyCode;
            public int KeyKeyCodeInt;
        }

        public static class DigitsPadHackOptionsAppMarker {
            @JsonProperty(index=10)
            String PackageName;
            @JsonProperty(index=20)
            String DigitsPadMarkerNodeId;
        }

        @JsonProperty(index=10)
        public boolean SearchPluginsEnabled;
        @JsonProperty(index=20)
        public ArrayList<String> RetranslateKeyboardKeyCodes = new ArrayList<>();

        @JsonProperty(index=30)
        public ArrayList<MetaKeyPlusKey> RetranslateKeyboardMetaKeyPlusKeyList = new ArrayList<>();

        @JsonProperty(index=40)
        public boolean DigitsPadPluginEnabled;

        @JsonProperty(index=41)
        public DigitsPadHackOptionsAppMarker[] DigitsPadPluginAppMarkers;

        @JsonProperty(index=50)
        public boolean SelectedNodeClickHack;

        @JsonProperty(index=60)
        public boolean SelectedNodeHighlight;
    }



    @Override
    protected void onServiceConnected() {
        Log.v(TAG3, "onServiceConnected()");
        super.onServiceConnected();

        try {

            keyoneKb2Settings = KeyoneKb2Settings.Get(getSharedPreferences(KeyoneKb2Settings.APP_PREFERENCES, Context.MODE_PRIVATE));
            FileJsonUtils.Initialize(this);
            keyoneKb2AccServiceOptions = FileJsonUtils.DeserializeFromJson(KeyoneKb2AccServiceOptions.ResName, new TypeReference<KeyoneKb2AccServiceOptions>() {}, this);

            LoadRetranslationData();

            if(keyoneKb2AccServiceOptions.DigitsPadPluginEnabled) {
                DigitsPadHackOptionsAppMarkers = keyoneKb2AccServiceOptions.DigitsPadPluginAppMarkers;
            }

            if (!keyoneKb2AccServiceOptions.SearchPluginsEnabled)
                return;
            LoadSearchPluginData();


            AccessibilityServiceInfo info = getServiceInfo();
            if (keyoneKb2AccServiceOptions.SearchPluginsEnabled) {
                //info.packageNames = SearchClickPackages.toArray(new String[SearchClickPackages.size()]);
                info.packageNames = null;
                if (!TEMP_ADDED_SEARCH_CLICK_PLUGINS.isEmpty())
                    info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            } else {
                info.packageNames = null;
                info.flags = 0;
                info.eventTypes = 0;
            }
            setServiceInfo(info);

            } catch(Throwable ex) {
                Log.e(TAG3, "onServiceConnected Exception: "+ex);
                LogErrorToGui("onServiceConnected Exception: "+ex);
                new Thread(() -> {
                    FileJsonUtils.SleepWithWakes(300);
                    StopService();
                }).start();
            }
    }
    @Override
    public synchronized void onDestroy() {
        Log.v(TAG3, "onDestroy()");
        Instance = null;
        super.onDestroy();
    }

    WindowManager _currentWindowManager;
    WindowManager.LayoutParams _layoutParams;

    @Override
    public synchronized void onCreate() {
        Log.v(TAG3, "onCreate()");
        try {
            super.onCreate();
            Instance = this;
            _currentWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            _layoutParams = InitializeLayoutParams();
            //executorService = Executors.newFixedThreadPool(2);
        } catch(Throwable ex) {
            Log.e(TAG3, "onCreate Exception: "+ex);
            new Thread(() -> {
                FileJsonUtils.SleepWithWakes(300);
                StopService();
            }).start();
        }

    }

    public void StopService() {
        disableSelf();
    }

    @Override
    public void onInterrupt() {
        Log.v(TAG3, "onInterrupt()");
    }

    public AccessibilityNodeInfo CurFocus;

    @Override
    public synchronized void onAccessibilityEvent(AccessibilityEvent event) {
        //Log.v(TAG3, "onAccessibilityEvent() eventType: "+event.getEventType() +" "+event.getPackageName());

        try {
            if(KeyoneIME.Instance == null)
                return;



            if (
                    event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                            && event.getEventType() != AccessibilityEvent.TYPE_VIEW_FOCUSED
                            && event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                            && event.getEventType() != AccessibilityEvent.TYPE_VIEW_SCROLLED
                            && event.getEventType() != AccessibilityEvent.TYPE_WINDOWS_CHANGED
            ) {

                Log.d(TAG3, "------------------ NEW_EVENT ------------------");
                LogEventD(event);
                Log.d(TAG3, "------------------ NEW_EVENT ------------------");
                return;
            }



            if((event.getEventType() & AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

                if(event.getContentChangeTypes() == (AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT | AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION)) {
                    //Этот хак нужен потому что Blackberry.Dialer жестко спамит эвентами (вроде как никому эти эвенты больше не нужны)
                    //Log.d(TAG3, "IGNORING TYPE_WINDOW_CONTENT_CHANGED TYPES: " + event.getContentChangeTypes());
                    return;
                }
                //Этот хак нужен чтобы не было никакой работы плагинов, когда пользователь набирает большие тексты, которые генерят как раз эти события
                //if(event.getContentChangeTypes() == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE)
                    if(event.getClassName().toString().equalsIgnoreCase("android.widget.EditText")
                            || event.getClassName().toString().equalsIgnoreCase("android.webkit.WebView")
                            || event.getClassName().toString().equalsIgnoreCase("android.widget.ScrollView")
                    ) {
                        Log.d(TAG3, "IGNORING android.widget.EditText || android.webkit.WebView || android.widget.ScrollView at: " + event.getPackageName());
                        //android.widget.ScrollView
                        return;
                    }
            }

            /** NOTE: Это затормаживает некоторые Web-приложения */
            //AccessibilityNodeInfo root1 = getRootInActiveWindow();
            //if (root1 == null) {
            //    return;
            //}

            if(event.getPackageName() != null && !event.getPackageName().equals(KeyoneIME.Instance._lastPackageName))
                return;
            if(KeyoneIME.Instance.IsInputMode()) {
                if(CurFocus == null || event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                    CurFocus = GetFocusedNode(event.getSource());
                    if (CurFocus == null) {
                        CurFocus = GetFocusedNode(getRootInActiveWindow());
                        Log.v(TAG3, "CurFocus: GetFocusedNode(getRootInActiveWindow())");
                    } else {
                        Log.v(TAG3, "CurFocus: GetFocusedNode(event.getSource())");
                    }
                }
            } else {
                Log.v(TAG3, "CurFocus: CurFocus = null");
                CurFocus = null;
            }


            if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                Log.v(TAG3, "onAccessibilityEvent() eventType: TYPE_WINDOW_STATE_CHANGED");
            if(event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED)
                Log.v(TAG3, "onAccessibilityEvent() eventType: TYPE_VIEW_FOCUSED");
            if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                Log.v(TAG3, "onAccessibilityEvent() eventType: TYPE_WINDOW_CONTENT_CHANGED");
                Log.d(TAG3, "TYPE_WINDOW_CONTENT_CHANGED TYPES: " + event.getContentChangeTypes());
            }
            if(event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED)
                Log.v(TAG3, "onAccessibilityEvent() eventType: TYPE_VIEW_SCROLLED");

            if(event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED)
                Log.v(TAG3, "onAccessibilityEvent() eventType: TYPE_WINDOWS_CHANGED");

            if(event.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT)
                Log.v(TAG3, "onAccessibilityEvent() eventType: TYPE_ANNOUNCEMENT");


            /** NOTE: Это затормаживает некоторые Web-приложения */
            //LogEventD(event);

            if(keyoneKb2AccServiceOptions.DigitsPadPluginEnabled)
                ProcessDigitsPadHack(event);

            if(
                    (KeyoneIME.Instance._modeGestureAtViewMode == InputMethodServiceCoreGesture.GestureAtViewMode.Pointer
                    || KeyoneIME.Instance.IsNavMode())
                    && (
                    keyoneKb2AccServiceOptions.SelectedNodeClickHack
                            || keyoneKb2AccServiceOptions.SelectedNodeHighlight
            ))
                ProcessGesturePointerModeAndNodeSelection(event);

            if(keyoneKb2AccServiceOptions.SearchPluginsEnabled)
                ProcessSearchPlugins(event);

        } catch (Throwable ex) {
            Log.e(TAG3, "onAccessibilityEvent Exception: "+ex);
        }
    }



    /** Наглый (или дерзкий) фокус (если видит что фокус не установлен в хост приложении - ставит его принудительно на первый попавшийс элемент)*/
    public static final boolean BrashFocuser = false;


    //region NODES_SELECTION

    private void ProcessGesturePointerModeAndNodeSelection(AccessibilityEvent event) {

        if(KeyoneIME.Instance != null && KeyoneIME.Instance.IsInputMode()) {
            Log.d(TAG3,"ProcessGesturePointerModeAndNodeSelection:KeyoneIME.Instance.IsInputMode()");
            SetCurrentNodeInfo(null);
            TryRemoveRectangle();
            return;
        }

        if(KeyoneIME.Instance != null && !KeyoneIME.Instance.pref_pointer_mode_rect_and_autofocus) {
            TryRemoveRectangleFast();
        }

        Log.d(TAG3,"ProcessGesturePointerModeAndNodeSelection:LOGIC");

        //TODO: Удалить какой-то атавизм от экспериментов
        if(event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if(keyoneKb2AccServiceOptions.SelectedNodeHighlight) {
                //TryRemoveRectangle();
            }
            //return;
        }

        if(event.getEventType() != AccessibilityEvent.TYPE_VIEW_FOCUSED
                && event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getEventType() != AccessibilityEvent.TYPE_WINDOWS_CHANGED
                && event.getEventType() != AccessibilityEvent.TYPE_VIEW_SCROLLED
        )
            return;

        if(event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED && event.getWindowId() == -1) {
            SetCurrentNodeInfo(null);
            TryRemoveRectangleFast();
            return;
        }

        AccessibilityNodeInfo info;
        if(event.getEventType() != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            info = event.getSource();
            if(
                    info != null
                    && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    && !info.isFocused()
                    && SelectionRectView != null
                    && SelectionRectView.IsSameNodeHash(info)
            ) {
                Log.d(TAG3, "FOCUS MOVED OUT: HASH: "+info.hashCode());
                SetCurrentNodeInfo(null);
                TryRemoveRectangleFast();
                return;
            }

        } else {
            info = null;
        }

        if (KeyoneIME.Instance != null
                && (KeyoneIME.Instance._modeGestureAtViewMode == InputMethodServiceCoreGesture.GestureAtViewMode.Pointer || KeyoneIME.Instance.IsNavMode())
                && !KeyoneIME.Instance.IsInputMode())
        {

            info = GetFocusedNode(info);

            if(KeyoneIME.Instance != null && KeyoneIME.Instance.pref_pointer_mode_rect_and_autofocus && BrashFocuser) {
                if (info == null) {
                    info = GetFocusedNode(getRootInActiveWindow());
                }
                if (info == null) {
                    info = FindFocusableRecurs(GetRoot(getRootInActiveWindow()), 0);
                    if (info != null) {
                        Log.d(TAG3, "FindFocusableRecurs(GetRoot(getRootInActiveWindow()) HASH: " + info.hashCode());
                        boolean clickRes = false;
                        clickRes = info.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                        if (clickRes) {
                            Log.d(TAG3, "info.performAction(AccessibilityNodeInfo.ACTION_FOCUS) OK");
                            return;
                        } else {
                            Log.d(TAG3, "info.performAction(AccessibilityNodeInfo.ACTION_FOCUS) FAIL");
                            info = null;
                        }
                    }
                }
            }

            if (keyoneKb2AccServiceOptions.SelectedNodeClickHack) {
                //Иммитация click в приложениях (Telegram, BB.Hub) где не работает симуляция KEYCODE_ENTER/SPACE
                if (info != null) {
                    PreparePointerClickHack(info);
                } else {
                    SetCurrentNodeInfo(null);
                }
            }
            if (keyoneKb2AccServiceOptions.SelectedNodeHighlight
                    && KeyoneIME.Instance != null && KeyoneIME.Instance.pref_pointer_mode_rect_and_autofocus) {
                if (info != null) {
                    //if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                    ProcessSelectionRectangle(info);
                    //}
                } else {
                    TryRemoveRectangle();
                }

            }


        }
    }

    private AccessibilityNodeInfo FindFocusedRecurs(AccessibilityNodeInfo info) {
        if(info == null)
            return null;
        if(info.isFocused())
            return info;
        for (int i = 0; i < info.getChildCount(); i++)  {
            AccessibilityNodeInfo res = FindFocusedRecurs(info.getChild(i));
            if(res != null)
                return res;
        }
        return null;
    }

    private AccessibilityNodeInfo FindFocusableRecurs(AccessibilityNodeInfo info, int level) {
        if(level > 10)
            return null;
        if(info == null)
            return null;
        if(info.isFocusable())
            return info;
        for (int i = 0; i < Math.min(info.getChildCount(), 10); i++)  {
            AccessibilityNodeInfo res = FindFocusableRecurs(info.getChild(i), level + 1);
            if(res != null)
                return res;
        }
        return null;
    }

    private void FindAllSelectedRecurs(AccessibilityNodeInfo info, ArrayList<AccessibilityNodeInfo> array) {
        if(info == null)
            return;
        if(info.isFocused())
            array.add(info);
        for (int i = 0; i < info.getChildCount(); i++)  {
            FindAllSelectedRecurs(info.getChild(i), array);
        }
        return;
    }

    private AccessibilityNodeInfo GetFocusedNode(AccessibilityNodeInfo info) {
        if(info == null)
            return null;
        if(info.isFocused()) {
            Log.d(TAG3, "GetFocusedNode .isFocused() HASH "+info.hashCode());
            return info;
        }

        AccessibilityNodeInfo info1 = FindFocusedRecurs(info);
        if(info1 != null) {
            Log.d(TAG3, "FindFocusedRecurs FOUND HASH: "+info1.hashCode());
            return info1;
        } else {
            Log.d(TAG3, "FindFocusedRecurs NOT FOUND");
            return null;
        }
    }

    private AccessibilityNodeInfo GetRoot(AccessibilityNodeInfo info) {
        if(info == null)
            return null;
        AccessibilityNodeInfo info1 = info;
        for (int i = 0; i < 20; i++) {
            AccessibilityNodeInfo root = info1.getParent();
            if(root == null)
                return info1;
            info1 = root;
        }
        return null;
    }

     private void ProcessSelectionRectangle(AccessibilityNodeInfo info) {

        if(SelectionRectView == null) {
            SelectionRectView = CreateRectangleView();
            LestSelectionRectView = SelectionRectView;
            Log.d(TAG3, "DRAW [ASYNC] FIRST-TIME HASH: "+info.hashCode());
            SelectionRectView.SetNodeInfo(info);
            SelectionRectView.removed = false;
            SelectionRectView.RemoveRectOnNextDraw = false;
            _currentWindowManager.addView(SelectionRectView, _layoutParams);
            return;
        }

        if(!SelectionRectView.removed && !SelectionRectView.RemoveRectOnNextDraw && SelectionRectView.IsSameNodeAndRect(info)) {
            Log.d(TAG3, "SelectionRectView.IsSameRect(info) HASH: "+info.hashCode());
            return;
        }

        if(!SelectionRectView.removed && !SelectionRectView.RemoveRectOnNextDraw)
            TryRemoveRectangle();
        Log.d(TAG3, "DRAW [ASYNC] REDRAW HASH: "+info.hashCode());
        SelectionRectView.RemoveRectOnNextDraw = false;
        SelectionRectView.SetNodeInfo(info);
        SelectionRectView.setVisibility(View.GONE);
        SelectionRectView.setVisibility(View.VISIBLE);
    }



    RectView SelectionRectView;
    RectView LestSelectionRectView;

    private RectView CreateRectangleView() {
        RectView rectView = new RectView(this);
        rectView.setClickable(false);
        rectView.setFocusable(false);
        rectView.setFocusableInTouchMode(false);
        rectView.setLongClickable(false);
        rectView.setKeepScreenOn(false);
        //rectView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        return rectView;
    }


    class RectView extends View {

        public AccessibilityNodeInfo SelectedNode;
        public Rect SelectedNodeRect;

        public RectView(Context context) {
            super(context);
            int color = 0xFF86888A;
            if(KeyoneIME.Instance != null)
                color = KeyoneIME.Instance.pref_pointer_mode_rect_color;
            paintMainer = new Paint();
            paintMainer.setColor(color);
            paintMainer.setStrokeWidth(3);
            paintMainer.setStyle(Paint.Style.STROKE);

            paintTransparenter = new Paint();
            paintTransparenter.setAlpha(0);
            paintTransparenter.setStrokeWidth(3);
            paintTransparenter.setStyle(Paint.Style.STROKE);
        }

        public void SetNodeInfo(AccessibilityNodeInfo info) {
            SelectedNode = info;
        }

        public boolean IsSameNodeAndRect(AccessibilityNodeInfo info) {
            if(SelectedNode != null && SelectedNode.hashCode() == info.hashCode() && IsSameRect(info))
                return true;
            return false;
        }

        public boolean IsSameNodeHash(AccessibilityNodeInfo info) {
            if(SelectedNode != null && SelectedNode.hashCode() == info.hashCode())
                return true;
            return false;
        }

        private boolean IsSameRect(AccessibilityNodeInfo info) {
            if(SelectedNodeRect == null)
                return true;
            Rect rect = new Rect();
            info.getBoundsInScreen(rect);
            if(rect.top == SelectedNodeRect.top
            && rect.bottom == SelectedNodeRect.bottom
            && rect.left == SelectedNodeRect.left
            && rect.right == SelectedNodeRect.right)
                return true;
            return false;
        }

        public RectView(Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
        }

        public RectView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }

        @Override
        protected void onDraw(Canvas canvas) {

            //Toast.makeText(KeyoneKb2AccessibilityService.Instance, "rectView.isHardwareAccelerated(): "+canvas.isHardwareAccelerated(), Toast.LENGTH_SHORT).show();
            if(SelectedNode == null)
                return;

            if(RemoveRectOnNextDraw) {


                SelectedNodeRect = new Rect();
                SelectedNode.getBoundsInScreen(SelectedNodeRect);

                canvas.drawRect(SelectedNodeRect, paintTransparenter);
                Log.d(TAG3, "OnDraw() HIDE");
                removed = true;
                RemoveRectOnNextDraw = false;
                SetNodeInfo(null);
                return;
            }

            Log.d(TAG3, "OnDraw() SHOW");
            removed = false;
            SelectedNodeRect = new Rect();
            SelectedNode.getBoundsInScreen(SelectedNodeRect);
            /** deltaY нужен чтобы делать смещение рамки на величину пустого черного поля в Unihertz Titan Slim,
             * в Key1-2 delta=0 и все работает и без этого хака*/
            int[] locationOnScreen = new int[2];
            getLocationOnScreen(locationOnScreen);
            int dx = locationOnScreen[0];
            int dy = locationOnScreen[1];
            SelectedNodeRect = new Rect(SelectedNodeRect.left, SelectedNodeRect.top - dy, SelectedNodeRect.right, SelectedNodeRect.bottom - dy);
            canvas.drawRect(SelectedNodeRect, paintMainer);
        }

        public boolean RemoveRectOnNextDraw;

        public boolean removed;

        Paint paintMainer;

        Paint paintTransparenter;
    }






    private static WindowManager.LayoutParams InitializeLayoutParams() {
        WindowManager.LayoutParams lp1 = new WindowManager.LayoutParams();
        lp1.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        lp1.format = PixelFormat.TRANSLUCENT;

        lp1.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;


        lp1.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp1.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp1.gravity = Gravity.FILL;
        return lp1;
    }

    public boolean TryRemoveRectangle() {
        if(SelectionRectView == null)
            return false;
        if(!SelectionRectView.RemoveRectOnNextDraw && !SelectionRectView.removed) {
            Log.d(TAG3, "REMOVE_ASYNC_SIGNAL! TryRemoveRectangle() HASH: "+SelectionRectView.SelectedNode.hashCode());

            SelectionRectView.RemoveRectOnNextDraw = true;
            SelectionRectView.setVisibility(View.GONE);
            SelectionRectView.setVisibility(View.VISIBLE);

            return true;
        }
        return false;
    }

    public boolean TryRemoveRectangleFast() {
        if(SelectionRectView == null)
            return false;
        if(!SelectionRectView.RemoveRectOnNextDraw && !SelectionRectView.removed) {
            Log.d(TAG3, "TryRemoveRectangleFast() HASH: "+SelectionRectView.SelectedNode.hashCode());

            //SelectionRectView.setVisibility(View.INVISIBLE);
            //SelectionRectView.invalidate();

            _currentWindowManager.removeViewImmediate(SelectionRectView);
            SelectionRectView = null;
            return true;
        }
        return false;
    }

    //endregion


    //region GESTURE_POINTER

    private void PreparePointerClickHack(AccessibilityNodeInfo info) {
        Rect rect = new Rect();
        info.getBoundsInScreen(rect);
        float x = rect.centerX();
        float y = rect.centerY();
        SetCurrentNodeInfo((boolean isLongClick) -> {
            Path clickPath = new Path();
            GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
            clickPath.moveTo(x, y);
            if(!isLongClick)
                clickBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 1));
            else {
                int longPressTime = ViewConfiguration.getLongPressTimeout();
                clickBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, longPressTime+longPressTime/2));
            }
            dispatchGesture(clickBuilder.build(), null, null);
        });

    }



    private void SetCurrentNodeInfo(InputMethodServiceCoreCustomizable.AsNodeClicker info) {
        if(KeyoneIME.Instance != null) {
            KeyoneIME.Instance.SetCurrentNodeInfo(info);
        }
    }

    //endregion


    //region SEARCH PLUGIN

    private void ProcessSearchPlugins(AccessibilityEvent event) {

        if(KeyoneIME.Instance != null && KeyoneIME.Instance.IsInputMode()) {
            Log.d(TAG3, "ProcessSearchPlugins:KeyoneIME.Instance.IsInputMode()");
            SetSearchHack(null);
            return;
        }

        CharSequence packageNameCs = event.getPackageName();
        if (packageNameCs == null || packageNameCs.length() == 0)
            return;
        String packageName = packageNameCs.toString();

        if(!SearchClickPackages.contains(packageName)) {
            //LogEventD(event);
            return;
        }
        Log.d(TAG3, "ProcessSearchPlugins:LOGIC");
        AccessibilityNodeInfo root = getRootInActiveWindow();
        for (SearchClickPlugin plugin : searchClickPlugins) {
            if (ProcessSearchField(event.getEventType(), packageName, root, event, plugin)) {
                return;
            }
        }
        for (SearchClickPlugin plugin : clickerPlugins) {
            if (ProcessSearchField(event.getEventType(), packageName, root, event, plugin)) {
                return;
            }
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && IsSearchHackSet(packageName)) {
            SetSearchHack(null);
            //LogEventD(event);
        }
    }


    private void LoadSearchPluginData() throws Exception {
        SearchClickPlugin.SearchClickPluginData data2 = FileJsonUtils.DeserializeFromJson("plugin_data", new TypeReference<SearchClickPlugin.SearchClickPluginData>() {}, getApplicationContext());

        if (data2.DefaultSearchWords != null && !data2.DefaultSearchWords.isEmpty()) {
            DefaultSearchWords = data2.DefaultSearchWords;
        } else {
            DefaultSearchWords = new ArrayList<>();
            DefaultSearchWords.add("Search");
            Log.e(TAG3, "DefaultSearchWords array empty. Need to be customized in plugin_data.json. For now set default: 1. Search");
            FileJsonUtils.LogErrorToGui("DefaultSearchWords array empty. Need to be customized in plugin_data.json. For now set default: 1. Search");
        }
        LoadSearchPlugins(data2.SearchPlugins, searchClickPlugins);
        LoadSearchPlugins(data2.ClickerPlugins, clickerPlugins);

        for(SearchClickPlugin shp2: TEMP_ADDED_SEARCH_CLICK_PLUGINS) {
            if(SearchClickPackages.contains(shp2.getPackageName()))
                continue;
            if(searchClickPlugins.stream().anyMatch((SearchClickPlugin shp3) -> shp3._packageName.equals(shp2._packageName)))
                continue;
            SearchClickPackages.add(shp2.getPackageName());
            searchClickPlugins.add(shp2);
        }



    }

    private void LoadSearchPlugins(ArrayList<SearchClickPlugin.SearchClickPluginData.SearchPluginData> clickPluginData, ArrayList<SearchClickPlugin> _clickPlugins) {
        for (SearchClickPlugin.SearchClickPluginData.SearchPluginData data : clickPluginData) {
            SearchClickPlugin shp = new SearchClickPlugin(data.PackageName);
            SearchClickPackages.add(data.PackageName);
            if (data.SearchFieldId != null && !data.SearchFieldId.isEmpty())
                shp.setId(data.SearchFieldId);
            else if (data.DynamicSearchMethod != null) {
                shp.DynamicSearchMethod = data.DynamicSearchMethod;
            }
            if (data.AdditionalEventTypeTypeWindowContentChanged)
                shp.setEvents(STD_EVENTS | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
            else
                shp.setEvents(STD_EVENTS);
            if (data.CustomClickAdapterClickParent)
                shp.setConverter(AccessibilityNodeInfo::getParent);
            else if(data.CustomClickAdapterClickFirstChild)
                shp.setConverter(new NodeClickableConverter() {
                    @Override
                    public AccessibilityNodeInfo getNode(AccessibilityNodeInfo info) {
                        if(info.getChildCount() == 0)
                            return null;
                        return info.getChild(0);
                    }
                });

            shp.WaitBeforeSendChar = data.WaitBeforeSendCharMs;

            _clickPlugins.add(shp);
        }

        for (SearchClickPlugin plugin : _clickPlugins) {
            if (plugin.getId() == null || plugin.getId().isEmpty()) {
                String value = GetFromSetting(plugin);
                if (value != null && value.length() > 0) {
                    plugin.setId(value);
                }
            }
        }
    }


    private String GetFromSetting(SearchClickPlugin plugin) {
        keyoneKb2Settings.CheckSettingOrSetDefault(plugin.getPreferenceKey(), "");
        return keyoneKb2Settings.GetStringValue(plugin.getPreferenceKey());
    }

    private void SetToSetting(SearchClickPlugin plugin, String value) {
        keyoneKb2Settings.SetStringValue(plugin.getPreferenceKey(), value);
    }

    public void ClearFromSettings(SearchClickPlugin plugin) {
        keyoneKb2Settings.ClearFromSettings(plugin.getPreferenceKey());
    }

    private void LogEventD(AccessibilityEvent event) {
        Log.v(TAG3, "--------------------LogEventD--------------------");
        Log.v(TAG3, "event.getEventType() " + event.getEventType());
        Log.v(TAG3, "event.getPackageName() " + event.getPackageName());
        Log.v(TAG3, "event.getClassName() " + event.getClassName());
        Log.v(TAG3, "event.getWindowId() " + event.getWindowId());
        Log.v(TAG3, "event.getText() " + event.getText());
        Log.v(TAG3, "event.getContentDescription() " + event.getContentDescription());
        AccessibilityNodeInfo info = event.getSource();
        Log.v(TAG3, "event.getSource() HASH " + (info != null ? info.hashCode() : "@NULL"));
        Log.v(TAG3, "getRootInActiveWindow() HASH " + (getRootInActiveWindow() != null ? getRootInActiveWindow().hashCode() : "@NULL"));
        Log.v(TAG3, "--------------------LogEventD--------------------");
    }

    private boolean ProcessSearchField(int eventType, String packageName, AccessibilityNodeInfo root, AccessibilityEvent event, SearchClickPlugin searchClickPlugin) {
        if (!packageName.equals(searchClickPlugin.getPackageName()))
            return false;
        if(!searchClickPlugin.checkEventType(eventType))
            return false;

        if((eventType & AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            Log.d(TAG3, "TYPE_WINDOW_CONTENT_CHANGED TYPES: " +event.getContentChangeTypes());
        }

        AccessibilityNodeInfo info = searchClickPlugin.Convert(FindOrGetFromCache(root, searchClickPlugin));

        if (info != null) {
            if (IsSearchHackSet(searchClickPlugin.getPackageName(), info))
                return true;
            if(info.isFocused() )
                return true;
            Log.d(TAG3, "SetSearchHack=SET package: "+ searchClickPlugin.getPackageName());
            Log.d(TAG3, "SetSearchHack=SET getClassName: " + event.getClassName());
            SearchClickPlugin.SearchPluginLauncher searchPluginLaunchData = new SearchClickPlugin.SearchPluginLauncher(searchClickPlugin.getPackageName(), info, searchClickPlugin.WaitBeforeSendChar);
            SetSearchHack(searchPluginLaunchData);
            return true;
        } else {
            Log.d(TAG3, "SetSearchHack=NULL package: "+ searchClickPlugin.getPackageName());
            Log.d(TAG3, "SetSearchHack=NULL: getClassName: " + event.getClassName());
            SetSearchHack(null);
            return false;
        }
    }

    private AccessibilityNodeInfo FindOrGetFromCache(AccessibilityNodeInfo root, SearchClickPlugin searchClickPlugin) {
        AccessibilityNodeInfo info = null;
        String fieldId = searchClickPlugin.getId();
        if(fieldId != "") {
            List<AccessibilityNodeInfo> infoList  = root.findAccessibilityNodeInfosByViewId(fieldId);
            if (infoList.size() > 0) {
                //Log.d(TAG, "SetSearchHack: production mode: take from cache");
                info = infoList.get(0);
            }
        } else {
            info = searchClickPlugin.findId(root);

            if (info != null) {
                if (info.getViewIdResourceName() != null) {
                    Log.d(TAG3, "SetSearchHack: research mode: field found " + info.getViewIdResourceName());
                    searchClickPlugin.setId(info.getViewIdResourceName());
                    SetToSetting(searchClickPlugin, info.getViewIdResourceName());
                } else {
                    //AccessibilityNodeInfo info2 = info.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                    Log.d(TAG3, "SetSearchHack: getViewIdResourceName() == null " + info.getContentDescription());
                }
            }
        }
        return info;
    }





    private void SetSearchHack(SearchClickPlugin.SearchPluginLauncher searchPluginLaunchData) {
        if (KeyoneIME.Instance != null) {
            KeyoneIME.Instance.SetSearchHack(searchPluginLaunchData);
        }
    }

    private void SetDigitsHack(boolean value) {
        if (KeyoneIME.Instance != null) {
            KeyoneIME.Instance.SetDigitsHack(value);
        }
    }

    private boolean IsSearchHackSet(String packageName, AccessibilityNodeInfo info) {
        if (KeyoneIME.Instance == null)
            return false;
        if(KeyoneIME.Instance.SearchPluginLauncher == null)
            return false;
        return KeyoneIME.Instance.SearchPluginLauncher.IsSameAsMine(packageName, info);
    }

    private boolean IsSearchHackSet(String packageName) {
        if (KeyoneIME.Instance == null)
            return false;
        if(KeyoneIME.Instance.SearchPluginLauncher == null)
            return false;
        return KeyoneIME.Instance.SearchPluginLauncher.IsSameAsMine(packageName);
    }



    //endregion

    //region DIGITS-PAD

    KeyoneKb2AccServiceOptions.DigitsPadHackOptionsAppMarker[] DigitsPadHackOptionsAppMarkers;


    private void ProcessDigitsPadHack(AccessibilityEvent event) {

        if(
                event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                        || event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) {
            if (KeyoneIME.Instance != null && KeyoneIME.Instance.IsInputMode()) {
                Log.d(TAG3, " ProcessDigitsPadHack:KeyoneIME.Instance.IsInputMode()");
                SetDigitsHack(false);
                return;
            }
            Log.d(TAG3, " ProcessDigitsPadHack:LOGIC");
            if (ContainsAllDigitsButtons2()) {
                //ContainsAllDigitsButtons(root);
                SetDigitsHack(true);
            }
        }
    }

    private static AccessibilityNodeInfo FindFirstByTextRecursive(AccessibilityNodeInfo node, String text, int recursLevel) {
        if(recursLevel > 4)
            return null;
        if (node == null)
            return null;
        if(node.getViewIdResourceName() != null)
            Log.d(TAG3, node.getViewIdResourceName());
        if (node.getText() != null) {
            if (node.getText().toString().startsWith(text))
                return node;
            //else Log.d(TAG, "TEXT: "+node.getText());
        }
        for (int i = 0; i < node.getChildCount() && i < 15; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = FindFirstByTextRecursive(child, text, recursLevel+1);
            if (result != null)
                return result;
        }
        return null;
    }

    private boolean ContainsAllDigitsButtons(AccessibilityNodeInfo node) {
        for(int i = 0; i < 10; i++) {
            AccessibilityNodeInfo info3 = FindFirstByTextRecursive(node, Integer.toString(i), 0);
            if(info3 == null)
                return false;
        }
        return true;
    }

    private boolean ContainsAllDigitsButtons2() {
        AccessibilityNodeInfo node = getRootInActiveWindow();
        if(node == null)
            return false;
        for (int i = 0; i < DigitsPadHackOptionsAppMarkers.length; i++) {
            KeyoneKb2AccServiceOptions.DigitsPadHackOptionsAppMarker marker = DigitsPadHackOptionsAppMarkers[i];
            if(!marker.PackageName.equalsIgnoreCase(node.getPackageName().toString()))
                continue;
            if(marker.DigitsPadMarkerNodeId == null)
                return true;
            List<AccessibilityNodeInfo> nodeMarkers = node.findAccessibilityNodeInfosByViewId(marker.DigitsPadMarkerNodeId);
            if(nodeMarkers != null && nodeMarkers.size() > 0 && nodeMarkers.get(0).isVisibleToUser())
                return true;
        }
        return false;
    }

    //endregion

    //region KEY RETRANSLATION
    // ХАК для BB Key2 НЕ_РСТ где кнопку SPEED_KEY переопределить нет возможности

    int[] RetranslateKeyCodes;

    private void LoadRetranslationData() throws NoSuchFieldException, IllegalAccessException {
        if(keyoneKb2AccServiceOptions.RetranslateKeyboardKeyCodes != null && !keyoneKb2AccServiceOptions.RetranslateKeyboardKeyCodes.isEmpty()) {
            RetranslateKeyCodes = new int[keyoneKb2AccServiceOptions.RetranslateKeyboardKeyCodes.size()];
            int i = 0;
            for (String keyCode: keyoneKb2AccServiceOptions.RetranslateKeyboardKeyCodes) {
                int keyCodeInt = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(keyCode);

                //Не перехватываем HOME, BACK если не включена настройка защиты текста
                //Т.к. этот перехват лишний раз может повлиять на отзывчивость этих функций
                if(KeyoneIME.Instance != null && !KeyoneIME.Instance.pref_ensure_entered_text
                    && (keyCodeInt == 3 || keyCodeInt == 4))
                    continue;
                RetranslateKeyCodes[i] = keyCodeInt;
                i++;
            }
        }
        if(keyoneKb2AccServiceOptions.RetranslateKeyboardMetaKeyPlusKeyList != null && !keyoneKb2AccServiceOptions.RetranslateKeyboardMetaKeyPlusKeyList.isEmpty()) {
            for(KeyoneKb2AccServiceOptions.MetaKeyPlusKey pair : keyoneKb2AccServiceOptions.RetranslateKeyboardMetaKeyPlusKeyList) {
                pair.MetaKeyCodeInt = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(pair.MetaKeyCode);
                pair.KeyKeyCodeInt = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(pair.KeyKeyCode);
            }
        }
    }

    @SuppressLint("WrongConstant")
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public synchronized boolean onKeyEvent(KeyEvent event) {
        Log.v(TAG3, "onKeyEvent() "+event);

        if (KeyoneIME.Instance == null)
            return false;
        if(keyoneKb2AccServiceOptions.RetranslateKeyboardKeyCodes == null || keyoneKb2AccServiceOptions.RetranslateKeyboardKeyCodes.isEmpty())
            return false;
        try {
            KeyEvent event1 = GetCopy(event);

            //Это ХАК для BB Key2 НЕ_РСТ где кнопку SPEED_KEY переопределить нет возможности
            //Зажатие speed_key+Буква не передается в сервис клавиатуры

            // Этот блок ХАК-а нужен на К2_не_РСТ иначе при нажатиии speed_key вызывается меню биндинга букв
            if (IsRetranslateKeyCode(event)
                    ) {
                event1.setSource(KeyoneIME.SYNTHETIC_CALL);
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    return KeyoneIME.Instance.onKeyDown(event1.getKeyCode(), event1);
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    return KeyoneIME.Instance.onKeyUp(event1.getKeyCode(), event1);
                }
            }

            if(keyoneKb2AccServiceOptions.RetranslateKeyboardMetaKeyPlusKeyList != null
                    && !keyoneKb2AccServiceOptions.RetranslateKeyboardMetaKeyPlusKeyList.isEmpty()
                    /*&& KeyoneIME.Instance.IsInputMode()*/) {
                for (KeyoneKb2AccServiceOptions.MetaKeyPlusKey pair : keyoneKb2AccServiceOptions.RetranslateKeyboardMetaKeyPlusKeyList) {
                    if(pair.KeyKeyCodeInt != event.getKeyCode())
                        continue;
                    if(!IsMeta(event, pair.MetaKeyCodeInt))
                        continue;
                    event1.setSource(KeyoneIME.SYNTHETIC_CALL_B_PLUS_META);
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        return KeyoneIME.Instance.onKeyDown(event1.getKeyCode(), event1);
                    } else if (event.getAction() == KeyEvent.ACTION_UP) {
                        return KeyoneIME.Instance.onKeyUp(event1.getKeyCode(), event1);
                    }
                }
            }
        } catch(Throwable ex) {
            Log.e(TAG3, "onKeyEvent Exception: "+ex);
        }
        return false;
    }

    private boolean IsRetranslateKeyCode(KeyEvent event) {
        for (int retr : RetranslateKeyCodes) {
            if(retr == event.getKeyCode())
                return true;
        }
        return false;
    }

    private boolean IsMeta(KeyEvent event, int meta) {
        if((event.getMetaState() & meta) == meta)
            return true;
        return false;
    }

    private KeyEvent GetCopyNewTime(KeyEvent keyEvent) {
        long now = SystemClock.uptimeMillis();
        return new KeyEvent(now, now, keyEvent.getAction(), keyEvent.getKeyCode(), keyEvent.getRepeatCount(),keyEvent.getMetaState(),keyEvent.getDeviceId(),keyEvent.getScanCode(),keyEvent.getFlags(),keyEvent.getFlags());
    }

    private KeyEvent GetCopy(KeyEvent keyEvent) {
        return new KeyEvent(keyEvent.getDownTime(), keyEvent.getEventTime(), keyEvent.getAction(), keyEvent.getKeyCode(), keyEvent.getRepeatCount(),keyEvent.getMetaState(),keyEvent.getDeviceId(),keyEvent.getScanCode(),keyEvent.getFlags(),keyEvent.getFlags());
    }

    //endregion


    public void IntentQuickSettings() {
        Log.d(TAG3, "performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)");
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
    }

    public void IntentNotifications() {
        Log.d(TAG3, "performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)");
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

}
