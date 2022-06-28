package com.sateda.keyonekb2;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.sateda.keyonekb2.BuildConfig.DEBUG;

public class KeyoneKb2AccessibilityService extends AccessibilityService {

    public static String SEARCH_CONST_FIND1 = "Найти";
    public static String SEARCH_CONST_FIND2 = "Поиск";
    public static String SEARCH_CONST_FIND3 = "Search";
    public static KeyoneKb2AccessibilityService Instance;
    private static String TAG = "KeyoneKb2-AS";

    interface NodeClickableConverter {
        AccessibilityNodeInfo getNode(AccessibilityNodeInfo info);
    }

    public int STD_EVENTS = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_FOCUSED;

    class SearchHackPlugin {

        String _packageName;
        String _id = "";

        int _events = 0;

        NodeClickableConverter _converter;

        public ArrayList<KeyoneKb2PluginData.DynamicSearchMethod> DynamicSearchMethod;

        public int WaitBeforeSendChar;

        public void setConverter(NodeClickableConverter converter) {
            _converter = converter;
        }

        public void setEvents(int events) {
            _events = events;
        }

        public SearchHackPlugin(String packageName) {
            _packageName = packageName;
        }
        public void setId(String id) {
            _id = id;
        }
        public String getId() {
            return _id;
        }

        public String getPreferenceKey() {
            return ""+_packageName;
        }

        private AccessibilityNodeInfo findId(AccessibilityNodeInfo root) {

            if(DynamicSearchMethod == null || DynamicSearchMethod.isEmpty()) {
                return findIdAll(root);
            }

            for(KeyoneKb2PluginData.DynamicSearchMethod method : DynamicSearchMethod) {
                if(method.DynamicSearchMethodFunction == KeyoneKb2PluginData.DynamicSearchMethodFunction.FindFirstByTextRecursive) {
                    AccessibilityNodeInfo info = FindFirstByTextRecursive(root, method.ContainsString);
                    if(info != null) {
                        return info;
                    }
                }
                if(method.DynamicSearchMethodFunction == KeyoneKb2PluginData.DynamicSearchMethodFunction.FindAccessibilityNodeInfosByText) {
                    List<AccessibilityNodeInfo> infoList = root.findAccessibilityNodeInfosByText(method.ContainsString);
                    if (infoList.size() > 0) {
                        return infoList.get(0);
                    }
                }
            }

            return null;
        }

        private AccessibilityNodeInfo findIdAll(AccessibilityNodeInfo root) {

            AccessibilityNodeInfo info = FindFirstByTextRecursive(root, SEARCH_CONST_FIND1);
            if(info != null) {
                return info;
            }
            List<AccessibilityNodeInfo> infoList = root.findAccessibilityNodeInfosByText(SEARCH_CONST_FIND2);
            if (infoList.size() > 0) {
                return infoList.get(0);
            }
            info = FindFirstByTextRecursive(root, SEARCH_CONST_FIND2);
            if (info != null) {
                return info;
            }
            infoList = root.findAccessibilityNodeInfosByText(SEARCH_CONST_FIND3);
            if (infoList.size() > 0) {
                return infoList.get(0);
            }
            return null;
        }

        public String getPackageName() {
            return _packageName;
        }
        public boolean checkEventType(int eventType) {
            if((eventType & _events) == 0)
                return false;
            return true;
        }

        public AccessibilityNodeInfo Convert(AccessibilityNodeInfo info) {
            if (_converter != null && info != null)
                return _converter.getNode(info);
            return info;
        }
    }

    public final ArrayList<SearchHackPlugin> searchHackPlugins = new ArrayList<>();

    KbSettings kbSettings;

    ExecutorService executorService;

    @Override
    public void onCreate() {
        super.onCreate();
        Instance = this;

        kbSettings = KbSettings.Get(getSharedPreferences(KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));
        executorService = Executors.newFixedThreadPool(2);

        KeyoneKb2PluginData data2 = FileJsonUtils.DeserializeFromJson("plugin_data", new TypeReference<KeyoneKb2PluginData>() {}, getApplicationContext());
        if(data2 == null)
            return;
        for (KeyoneKb2PluginData.SearchPluginData data : data2.SearchPlugins) {
            SearchHackPlugin shp = new SearchHackPlugin(data.PackageName);
            if(data.SearchFieldId != null && !data.SearchFieldId.isEmpty())
                shp.setId(data.SearchFieldId);
            else if(data.DynamicSearchMethod != null) {
                shp.DynamicSearchMethod = data.DynamicSearchMethod;
            }
            if(data.AdditionalEventTypeTypeWindowContentChanged)
                shp.setEvents(STD_EVENTS | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
            else
                shp.setEvents(STD_EVENTS);
            if(data.CustomClickAdapterClickParent)
                shp.setConverter(AccessibilityNodeInfo::getParent);

            shp.WaitBeforeSendChar = data.WaitBeforeSendCharMs;

            searchHackPlugins.add(shp);
        }


        for (SearchHackPlugin plugin : searchHackPlugins) {
            String value = GetFromSetting(plugin);
            if (value != null && value.length() > 0) {
                plugin.setId(value);
            }
        }

    }

    private String GetFromSetting(SearchHackPlugin plugin) {
        kbSettings.CheckSettingOrSetDefault(plugin.getPreferenceKey(), "");
        return kbSettings.GetStringValue(plugin.getPreferenceKey());
    }

    private void SetToSetting(SearchHackPlugin plugin, String value) {
        kbSettings.SetStringValue(plugin.getPreferenceKey(), value);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public boolean onKeyEvent(KeyEvent event) {

        //Это ХАК для BB Key2 НЕ_РСТ где кнопку SPEED_KEY переопределить нет возможности
        //Зажатие speed_key+Буква не передается в сервис клавиатуры
        //Но зато передается сюда, поэтмоу приходится отсюда туда переправлять
        if(!IsMetaFunctionOrFunction(event))
            return false;
        if(KeyoneIME.Instance == null)
            return false;
        // Этот блок ХАК-а нужен на К2_не_РСТ иначе при нажатиии speed_key вызывается меню биндинга букв
        if(event.getKeyCode() == KeyEvent.KEYCODE_FUNCTION) {
            KeyEvent event1 = GetCopy(event);
            if(event.getAction() == KeyEvent.ACTION_DOWN) {
                KeyoneIME.Instance.onKeyDown(event1.getKeyCode(), event1);
            }
            if(event.getAction() == KeyEvent.ACTION_UP) {
                KeyoneIME.Instance.onKeyUp(event1.getKeyCode(), event1);
            }

            return true;
        }
        if(     event.getKeyCode() != KeyEvent.KEYCODE_A
                && event.getKeyCode() != KeyEvent.KEYCODE_C
                && event.getKeyCode() != KeyEvent.KEYCODE_X
                && event.getKeyCode() != KeyEvent.KEYCODE_V
                && event.getKeyCode() != KeyEvent.KEYCODE_Z
                && event.getKeyCode() != KeyEvent.KEYCODE_0) {
            return false;
        }
        if(event.getAction() == KeyEvent.ACTION_DOWN) {
            //KeyoneIME.Instance.onKeyDown(event.getKeyCode(), event);
            executorService.execute(
                    () -> {
                        try {
                            KeyEvent event1 = GetCopy(event);
                            Thread.sleep(100);
                            KeyoneIME.Instance.onKeyDown(event1.getKeyCode(), event1);
                        }catch(Throwable ignored) {}
                    });
            return true;
        } else if(event.getAction() == KeyEvent.ACTION_UP) {
            //executorService.execute(() -> {KeyoneIME.Instance.onKeyUp(event.getKeyCode(), event);});
            KeyEvent event1 = GetCopy(event);
            KeyoneIME.Instance.onKeyUp(event1.getKeyCode(), event1);
            return true;
        }
        return false;
    }

    private boolean IsMetaFunctionOrFunction(KeyEvent event) {
        if(event.getKeyCode() == KeyEvent.KEYCODE_FUNCTION)
            return true;
        if((event.getMetaState() & KeyEvent.META_FUNCTION_ON) == KeyEvent.META_FUNCTION_ON)
            return true;
        return false;
    }

    private KeyEvent GetCopyNewTime(KeyEvent keyEvent) {
        long now = SystemClock.uptimeMillis();
        return new KeyEvent(now, now, keyEvent.getAction(), keyEvent.getKeyCode(), keyEvent.getRepeatCount(),keyEvent.getMetaState(),keyEvent.getDeviceId(),keyEvent.getScanCode(),keyEvent.getFlags(),keyEvent.getFlags());
    }

    private KeyEvent GetCopy(KeyEvent keyEvent) {
        long now = SystemClock.uptimeMillis();
        return new KeyEvent(keyEvent.getDownTime(), keyEvent.getEventTime(), keyEvent.getAction(), keyEvent.getKeyCode(), keyEvent.getRepeatCount(),keyEvent.getMetaState(),keyEvent.getDeviceId(),keyEvent.getScanCode(),keyEvent.getFlags(),keyEvent.getFlags());
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        if(DEBUG && event.getPackageName() != null && event.getPackageName().toString().equals("com.android.systemui"))
            return;
        //https://stackoverflow.com/questions/36067686/how-to-interrupt-an-action-from-being-performed-in-accessibilityservice

        if(event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            && event.getEventType() != AccessibilityEvent.TYPE_VIEW_FOCUSED
            && event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            Log.d(TAG, "event.getPackageName() " + event.getPackageName());
            Log.d(TAG, "event.getEventType() " + event.getEventType());
            Log.d(TAG, "event.getWindowId() " + event.getWindowId());
            Log.d(TAG, "event.getSource() " + event.getSource());
            Log.d(TAG, "event.getClassName() " + event.getClassName());
            Log.d(TAG, "event.getText() " + event.getText());
            Log.d(TAG, "event.getContentDescription() " + event.getContentDescription());
            return;
        }
        CharSequence packageNameCs = event.getPackageName();
        if(packageNameCs == null || packageNameCs.length() == 0)
            return;
        String packageName = packageNameCs.toString();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if(root == null)
            return;

        for(SearchHackPlugin plugin : searchHackPlugins) {
            if (ProcessSearchField(event.getEventType(), packageName, root, event, plugin))
                return;
        }

        if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            && IsSearchHackSet(packageName)) {
            SetSearchHack(null, null);
            Log.d(TAG, "event.getPackageName() " + event.getPackageName());
            Log.d(TAG, "event.getEventType() " + event.getEventType());
            Log.d(TAG, "event.getWindowId() " + event.getWindowId());
            Log.d(TAG, "event.getSource() " + event.getSource());
            Log.d(TAG, "event.getClassName() " + event.getClassName());
            Log.d(TAG, "event.getText() " + event.getText());
            Log.d(TAG, "event.getContentDescription() " + event.getContentDescription());
        }
    }

    private boolean ProcessSearchField(int eventType, String packageName, AccessibilityNodeInfo root, AccessibilityEvent event, SearchHackPlugin searchHackPlugin) {
        if(!searchHackPlugin.checkEventType(eventType))
            return false;
        if (packageName.equals(searchHackPlugin.getPackageName())) {
            if (IsSearchHackSet(searchHackPlugin.getPackageName()))
                return true;

            AccessibilityNodeInfo info = searchHackPlugin.Convert(FindOrGetFromCache(root, searchHackPlugin));

            if (info != null) {
                if(info.isFocused() )
                    return true;
                Log.d(TAG, "SetSearchHack "+searchHackPlugin.getPackageName());
                int wait = searchHackPlugin.WaitBeforeSendChar;
                SetSearchHack(() -> {
                    info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    if(wait > 0) {
                        try { Thread.sleep(wait); } catch(Throwable ignore) {}
                    }
                }, searchHackPlugin.getPackageName());
            } else {
                SetSearchHack(null, null);
            }
            return true;
        }
        return false;
    }

    private AccessibilityNodeInfo FindOrGetFromCache(AccessibilityNodeInfo root, SearchHackPlugin searchHackPlugin) {
        AccessibilityNodeInfo info = null;
        String fieldId = searchHackPlugin.getId();
        if(fieldId != "") {
            List<AccessibilityNodeInfo> infoList  = root.findAccessibilityNodeInfosByViewId(fieldId);
            if (infoList.size() > 0) {
                //Log.d(TAG, "SetSearchHack: production mode: take from cache");
                info = infoList.get(0);
            }
        } else {
            info = searchHackPlugin.findId(root);
            if (info != null && info.getViewIdResourceName() != null) {
                Log.d(TAG, "SetSearchHack: research mode: field found "+info.getViewIdResourceName());
                searchHackPlugin.setId(info.getViewIdResourceName());
                SetToSetting(searchHackPlugin, info.getViewIdResourceName());
            }
        }
        return info;
    }

    private AccessibilityNodeInfo FindFirstByTextRecursive(AccessibilityNodeInfo node, String text) {
        if (node == null)
            return null;
        if(node.getViewIdResourceName() != null)
            Log.d(TAG, node.getViewIdResourceName());
        if (node.getText() != null) {
            if (node.getText().toString().contains(text))
                return node;
            //else Log.d(TAG, "TEXT: "+node.getText());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = FindFirstByTextRecursive(child, text);
            if (result != null)
                return result;
        }
        return null;
    }


    private void SetSearchHack(KeyoneIME.Processable processable, String packageName) {
        if (KeyoneIME.Instance != null) {
            KeyoneIME.Instance.SetSearchHack(processable, packageName);
        }

    }

    private boolean IsSearchHackSet(String packageName) {
        if (KeyoneIME.Instance != null) {
            if(KeyoneIME.Instance.SearchHackPackage != null && KeyoneIME.Instance.SearchHackPackage.equals(packageName))
                return KeyoneIME.Instance.SearchHack != null;
        }
        return false;
    }

    @Override
    public void onInterrupt() {
    }

}
