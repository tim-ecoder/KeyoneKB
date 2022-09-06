package com.sateda.keyonekb2;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.*;
import android.os.Build;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;


public class KeyoneKb2AccessibilityService extends AccessibilityService {

    public static KeyoneKb2AccessibilityService Instance;
    public static String TAG3 = "KeyoneKb2-AS";

    interface NodeClickableConverter {
        AccessibilityNodeInfo getNode(AccessibilityNodeInfo info);
    }

    public static int STD_EVENTS = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_FOCUSED;

    public final ArrayList<SearchClickPlugin> searchClickPlugins = new ArrayList<>();

    KeyoneKb2Settings keyoneKb2Settings;

    ExecutorService executorService;

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

        @JsonProperty(index=10)
        public boolean SearchPluginsEnabled;
        @JsonProperty(index=20)
        public ArrayList<String> RetranslateKeyboardKeyCodes = new ArrayList<>();

        @JsonProperty(index=30)
        public ArrayList<MetaKeyPlusKey> RetranslateKeyboardMetaKeyPlusKeyList = new ArrayList<>();

        @JsonProperty(index=40)
        public boolean DigitsPadPluginEnabled;

        @JsonProperty(index=50)
        public boolean SelectedNodeClickHack;

        @JsonProperty(index=60)
        public boolean SelectedNodeHighlight;
    }

    public boolean TryRemoveRectangle() {
         if(selectionRectangle != null) {
             wm.removeView(selectionRectangle);
             selectionRectangle = null;
             return true;
         }
         return false;
    }

    @Override
    protected void onServiceConnected() {
        Log.v(TAG3, "onServiceConnected()");
        super.onServiceConnected();

        AccessibilityServiceInfo info = getServiceInfo();
        if(keyoneKb2AccServiceOptions.SearchPluginsEnabled) {
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
    }
    @Override
    public synchronized void onDestroy() {
        Instance = null;
        super.onDestroy();
    }

    WindowManager wm;

    @Override
    public synchronized void onCreate() {
        Log.v(TAG3, "onCreate()");
        try {
            super.onCreate();
            Instance = this;
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);

            keyoneKb2Settings = KeyoneKb2Settings.Get(getSharedPreferences(KeyoneKb2Settings.APP_PREFERENCES, Context.MODE_PRIVATE));
            keyoneKb2AccServiceOptions = FileJsonUtils.DeserializeFromJson(KeyoneKb2AccServiceOptions.ResName, new TypeReference<KeyoneKb2AccServiceOptions>() {}, getApplicationContext());

            LoadRetranslationData();
            executorService = Executors.newFixedThreadPool(2);

            if(!keyoneKb2AccServiceOptions.SearchPluginsEnabled)
                return;
            LoadSearchPluginData();
        } catch(Throwable ex) {
            Log.e(TAG3, "onCreate Exception: "+ex);
            new Thread(() -> {
                try { Thread.sleep(200); } catch (Throwable ignored) {}
                StopService();
            }).start();
        }

    }

    @Override
    public void onInterrupt() {
        Log.v(TAG3, "onInterrupt()");
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    public synchronized void onAccessibilityEvent(AccessibilityEvent event) {
        Log.v(TAG3, "onAccessibilityEvent() eventType: "+event.getEventType());

        try {
            if(KeyoneIME.Instance == null)
                return;

            if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && event.getEventType() != AccessibilityEvent.TYPE_VIEW_FOCUSED
                    && event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

                LogEventD(event);
                return;
            }
            if((event.getEventType() & AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

                if(event.getContentChangeTypes() == (AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT | AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION)) {
                    //Этот хак нужен потому что Blackberry.Dialer жестко спамит эвентами (вроде как никому эти эвенты больше не нужны)
                    Log.d(TAG3, "IGNORING TYPE_WINDOW_CONTENT_CHANGED TYPES: " + event.getContentChangeTypes());
                    return;
                }
            }

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null)
                return;

            if(keyoneKb2AccServiceOptions.DigitsPadPluginEnabled)
            if(event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && ContainsAllDigitsButtons(root)) {
                SetDigitsHack(true);
                return;
            } else {
                //SetDigitsHack(false);
            }

            if(keyoneKb2AccServiceOptions.SelectedNodeClickHack || keyoneKb2AccServiceOptions.SelectedNodeHighlight) {
                if (KeyoneIME.Instance != null
                        && (KeyoneIME.Instance.GesturePointerMode || KeyoneIME.Instance.IsNavMode())
                        && !KeyoneIME.Instance.IsInputMode()
                        && event.getSource() != null) {
                    if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED
                            || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                        if (keyoneKb2AccServiceOptions.SelectedNodeClickHack) {
                            //Иммитация click в приложениях (Telegram, BB.Hub) где не работает симуляция KEYCODE_ENTER/SPACE
                            PreparePointerClickHack(event);
                        }
                        if (keyoneKb2AccServiceOptions.SelectedNodeHighlight) {
                            ProcessSelectionRectangle(event);
                        }
                    }
                }
            }
            //if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if(KeyoneIME.Instance != null) {
                    KeyoneIME.Instance.ActiveNode = getRootInActiveWindow();
                }
            //}

            if(!keyoneKb2AccServiceOptions.SearchPluginsEnabled)
                return;

            CharSequence packageNameCs = event.getPackageName();
            if (packageNameCs == null || packageNameCs.length() == 0)
                return;
            String packageName = packageNameCs.toString();

            if(!SearchClickPackages.contains(packageName)) {
                //LogEventD(event);
                return;
            }

            for (SearchClickPlugin plugin : searchClickPlugins) {
                if (ProcessSearchField(event.getEventType(), packageName, root, event, plugin)) {
                    return;
                }
            }

            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && IsSearchHackSet(packageName)) {
                SetSearchHack(null);
                LogEventD(event);
            }
        } catch (Throwable ex) {
            Log.e(TAG3, "onAccessibilityEvent Exception: "+ex);
        }
    }

    //region DIGITS-PAD

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

    //endregion

    //region GESTURE_POINTER

    private void PreparePointerClickHack(AccessibilityEvent event) {
        Rect rect = new Rect();
        event.getSource().getBoundsInScreen(rect);
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

    private void ProcessSelectionRectangle(AccessibilityEvent event) {

        TryRemoveRectangle();

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        lp.format = PixelFormat.TRANSLUCENT;

        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;


        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.gravity = Gravity.FILL;

        selectionRectangle = new RectView(this, event.getSource());

        selectionRectangle.setClickable(false);
        selectionRectangle.setFocusable(false);
        selectionRectangle.setFocusableInTouchMode(false);
        selectionRectangle.setLongClickable(false);
        selectionRectangle.setKeepScreenOn(false);

        wm.addView(selectionRectangle, lp);
        selectionRectangle.setVisibility(View.VISIBLE);
    }

    class RectView extends View {

        AccessibilityNodeInfo _info;
        public RectView(Context context) {
            super(context);
        }
        public RectView(Context context, AccessibilityNodeInfo info) {
            super(context);
            _info = info;
        }

        public RectView(Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
        }

        public RectView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Rect rect = new Rect();
            _info.getBoundsInScreen(rect);
            Paint paint = new Paint();
            paint.setColor(Color.GRAY);
            paint.setStrokeWidth(3);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(rect, paint);
        }
    }

    View selectionRectangle;

    private void SetCurrentNodeInfo(InputMethodServiceCodeCustomizable.AsNodeClicker info) {
        if(KeyoneIME.Instance != null) {
            KeyoneIME.Instance.SetCurrentNodeInfo(info);
        }
    }

    //endregion

    //region SEARCH PLUGIN


    private void LoadSearchPluginData() {
        SearchClickPlugin.SearchClickPluginData data2 = FileJsonUtils.DeserializeFromJson("plugin_data", new TypeReference<SearchClickPlugin.SearchClickPluginData>() {}, getApplicationContext());
        if (data2 == null)
            return;
        if (data2.DefaultSearchWords != null && !data2.DefaultSearchWords.isEmpty()) {
            DefaultSearchWords = data2.DefaultSearchWords;
        } else {
            DefaultSearchWords = new ArrayList<>();
            DefaultSearchWords.add("Search");
            Log.e(TAG3, "DefaultSearchWords array empty. Need to be customized in plugin_data.json. For now set default: 1. Search");
        }
        for (SearchClickPlugin.SearchClickPluginData.SearchPluginData data : data2.SearchPlugins) {
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

            shp.WaitBeforeSendChar = data.WaitBeforeSendCharMs;

            searchClickPlugins.add(shp);
        }

        for(SearchClickPlugin shp2: TEMP_ADDED_SEARCH_CLICK_PLUGINS) {
            if(SearchClickPackages.contains(shp2.getPackageName()))
                continue;
            if(searchClickPlugins.stream().anyMatch((SearchClickPlugin shp3) -> shp3._packageName.equals(shp2._packageName)))
                continue;
            SearchClickPackages.add(shp2.getPackageName());
            searchClickPlugins.add(shp2);
        }


        for (SearchClickPlugin plugin : searchClickPlugins) {
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
        Log.v(TAG3, "event.getEventType() " + event.getEventType());
        Log.v(TAG3, "event.getPackageName() " + event.getPackageName());
        Log.v(TAG3, "event.getClassName() " + event.getClassName());
        Log.v(TAG3, "event.getWindowId() " + event.getWindowId());
        Log.v(TAG3, "event.getSource() " + event.getSource());
        Log.v(TAG3, "event.getText() " + event.getText());
        Log.v(TAG3, "event.getContentDescription() " + event.getContentDescription());
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
        } else {
            Log.d(TAG3, "SetSearchHack=NULL package: "+ searchClickPlugin.getPackageName());
            Log.d(TAG3, "SetSearchHack=NULL: getClassName: " + event.getClassName());
            SetSearchHack(null);
        }
        return true;

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



    public void StopService() {
        disableSelf();
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

    //region KEY RETRANSLATION
    // ХАК для BB Key2 НЕ_РСТ где кнопку SPEED_KEY переопределить нет возможности

    int[] RetranslateKeyCodes;

    private void LoadRetranslationData() throws NoSuchFieldException, IllegalAccessException {
        if(keyoneKb2AccServiceOptions.RetranslateKeyboardKeyCodes != null && !keyoneKb2AccServiceOptions.RetranslateKeyboardKeyCodes.isEmpty()) {
            RetranslateKeyCodes = new int[keyoneKb2AccServiceOptions.RetranslateKeyboardKeyCodes.size()];
            int i = 0;
            for (String keyCode: keyoneKb2AccServiceOptions.RetranslateKeyboardKeyCodes) {
                RetranslateKeyCodes[i] = FileJsonUtils.GetKeyCodeIntFromKeyEventOrInt(keyCode);
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
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public synchronized boolean onKeyEvent(KeyEvent event) {
        Log.v(TAG3, "onKeyEvent()");
        if (KeyoneIME.Instance == null)
            return false;
        if(keyoneKb2AccServiceOptions.RetranslateKeyboardKeyCodes == null || keyoneKb2AccServiceOptions.RetranslateKeyboardKeyCodes.isEmpty())
            return false;
        try {
            //Это ХАК для BB Key2 НЕ_РСТ где кнопку SPEED_KEY переопределить нет возможности
            //Зажатие speed_key+Буква не передается в сервис клавиатуры

            // Этот блок ХАК-а нужен на К2_не_РСТ иначе при нажатиии speed_key вызывается меню биндинга букв
            if (IsRetranslateKeyCode(event)) {
                KeyEvent event1 = GetCopy(event);
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    return KeyoneIME.Instance.onKeyDown(event1.getKeyCode(), event1);
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    return KeyoneIME.Instance.onKeyUp(event1.getKeyCode(), event1);
                }
            }

            if(keyoneKb2AccServiceOptions.RetranslateKeyboardMetaKeyPlusKeyList != null && !keyoneKb2AccServiceOptions.RetranslateKeyboardMetaKeyPlusKeyList.isEmpty()) {
                for (KeyoneKb2AccServiceOptions.MetaKeyPlusKey pair : keyoneKb2AccServiceOptions.RetranslateKeyboardMetaKeyPlusKeyList) {
                    if(pair.KeyKeyCodeInt != event.getKeyCode())
                        continue;
                    if(!IsMeta(event, pair.MetaKeyCodeInt))
                        continue;
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        //KeyoneIME.Instance.onKeyDown(event.getKeyCode(), event);
                        executorService.execute(
                                () -> {
                                    try {
                                        KeyEvent event1 = GetCopy(event);
                                        //Было sleep 100 из-за задержки в KeyoneIME относительно AS
                                        Thread.sleep(1);
                                        KeyoneIME.Instance.onKeyDown(event1.getKeyCode(), event1);
                                    } catch (Throwable ignored) {
                                    }
                                });
                        return true;
                    } else if (event.getAction() == KeyEvent.ACTION_UP) {
                        //executorService.execute(() -> {KeyoneIME.Instance.onKeyUp(event.getKeyCode(), event);});
                        KeyEvent event1 = GetCopy(event);
                        KeyoneIME.Instance.onKeyUp(event1.getKeyCode(), event1);
                        return true;
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

}
