package com.sateda.keyonekb2;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

public class KeyoneKb2AccessibilityService extends AccessibilityService {

    public static KeyoneKb2AccessibilityService Instance;
    private static String TAG = "KeyoneKb2As";

    interface NodeClickableConverter {
        AccessibilityNodeInfo getNode(AccessibilityNodeInfo info);
    }

    class SearchHackPlugin {

        String _packageName;
        String _id = "";

        int _events = 0;

        NodeClickableConverter _converter;

        public SearchHackPlugin(String packageName, int events, NodeClickableConverter converter) {
            _events = events;
            _converter = converter;
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
            AccessibilityNodeInfo info = FindFirstByTextRecursive(root, "Найти");
            if(info != null) {
                return info;
            }
            List<AccessibilityNodeInfo> infoList = root.findAccessibilityNodeInfosByText("Поиск");
            if (infoList.size() > 0) {
                return infoList.get(0);
            }
            info = FindFirstByTextRecursive(root, "Поиск");
            if (info != null) {
                return info;
            }
            infoList = root.findAccessibilityNodeInfosByText("Search");
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

    @Override
    public void onCreate() {
        super.onCreate();
        Instance = this;

        kbSettings = KbSettings.Get(getSharedPreferences(KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));

        searchHackPlugins.add(new SearchHackPlugin(
                "com.blackberry.blackberrylauncher",
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED | AccessibilityEvent.TYPE_VIEW_FOCUSED,
                null));
        searchHackPlugins.add(new SearchHackPlugin(
                "org.telegram.messenger",
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_FOCUSED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                null));
        searchHackPlugins.add(new SearchHackPlugin(
                "com.android.dialer",
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_FOCUSED,
                AccessibilityNodeInfo::getParent));
        searchHackPlugins.add(new SearchHackPlugin(
                "com.blackberry.contacts",
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_FOCUSED,
                null));
        searchHackPlugins.add(new SearchHackPlugin(
                "com.oasisfeng.island",
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_FOCUSED,
                null));
        searchHackPlugins.add(new SearchHackPlugin(
                "ru.yandex.yandexmaps",
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_FOCUSED,
                AccessibilityNodeInfo::getParent));
        searchHackPlugins.add(new SearchHackPlugin(
                "com.blackberry.notes",
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_FOCUSED,
                null));
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

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        //https://stackoverflow.com/questions/36067686/how-to-interrupt-an-action-from-being-performed-in-accessibilityservice

        if(event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            && event.getEventType() != AccessibilityEvent.TYPE_VIEW_FOCUSED
            && event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            return;
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
            && IsSearchHackSet()) {
            SetSearchHack(null);
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

            AccessibilityNodeInfo info = searchHackPlugin.Convert(FindOrGetFromCache(root, searchHackPlugin));

            if (info != null) {
                if (IsSearchHackSet())
                    return true;
                if(info.isFocused() )
                    return true;
                Log.d(TAG, "SetSearchHack "+searchHackPlugin.getPackageName());
                SetSearchHack(() -> {
                    info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                });
            } else {
                SetSearchHack(null);
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


    private void SetSearchHack(KeyoneIME.Processable processable) {
        if (KeyoneIME.Instance != null) {
            KeyoneIME.Instance.SetSearchHack(processable);
        }

    }

    private boolean IsSearchHackSet() {
        if (KeyoneIME.Instance != null) {
            return KeyoneIME.Instance.SearchHack != null;
        }
        return false;
    }

    @Override
    public void onInterrupt() {
    }

}
