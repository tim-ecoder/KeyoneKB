package com.sateda.keyonekb2;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class KeyoneKb2AccessibilityService extends AccessibilityService {
    private static String TAG = "KeyoneKb2As";

    interface SearchHackPlugin {
        AccessibilityNodeInfo findId(AccessibilityNodeInfo root);
        void setId(String id);
        String getId();
        String getPackageName();
        boolean checkEventType(int eventType);
        AccessibilityNodeInfo getNode(AccessibilityNodeInfo info);
    }

    private SearchHackPlugin bbLauncherAppSearch;
    String bbLauncherAppsSearchFieldId = "";

    @Override
    public void onCreate() {
        super.onCreate();

        bbLauncherAppSearch = new SearchHackPlugin() {
            @Override
            public AccessibilityNodeInfo findId(AccessibilityNodeInfo root) {
                return FindFirstByTextRecursive(root, "Найти");
            }

            @Override
            public void setId(String id) {
                bbLauncherAppsSearchFieldId = id;
            }

            @Override
            public String getId() {
                return bbLauncherAppsSearchFieldId;
            }

            @Override
            public String getPackageName() {
                return "com.blackberry.blackberrylauncher";
            }

            @Override
            public boolean checkEventType(int eventType) {
                if(eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                        && eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED)
                    return false;
                return true;
            }

            @Override
            public AccessibilityNodeInfo getNode(AccessibilityNodeInfo info) {
                return info;
            }

        };
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

        if (ProcessDialerSearchField(event.getEventType(), packageName, root)) return;
        if (ProcessContactsSearchField(event.getEventType(), packageName, root)) return;
        if (ProcessTelegramSearchField(event.getEventType(), packageName, root)) return;
        if (ProcessBbLauncherAppsSearchField(event.getEventType(), packageName, root, event, bbLauncherAppSearch)) return;

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



    private boolean ProcessBbLauncherAppsSearchField(int eventType, String packageName, AccessibilityNodeInfo root, AccessibilityEvent event, SearchHackPlugin searchHackPlugin) {
        if(!searchHackPlugin.checkEventType(eventType))
            return false;
        if (packageName.equals(searchHackPlugin.getPackageName())) {

            AccessibilityNodeInfo info = searchHackPlugin.getNode(FindOrGetFromCache(root, searchHackPlugin));

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
                Log.d(TAG, "event.getPackageName() " + event.getPackageName());
                Log.d(TAG, "event.getEventType() " + event.getEventType());
                Log.d(TAG, "event.getWindowId() " + event.getWindowId());
                Log.d(TAG, "event.getSource() " + event.getSource());
                Log.d(TAG, "event.getClassName() " + event.getClassName());
                Log.d(TAG, "event.getText() " + event.getText());
                Log.d(TAG, "event.getContentDescription() " + event.getContentDescription());


            }
            return true;
        }
        return false;
    }




    private static AccessibilityNodeInfo FindOrGetFromCache(AccessibilityNodeInfo root, SearchHackPlugin specific) {
        AccessibilityNodeInfo info = null;
        String fieldId = specific.getId();
        if(fieldId != "") {
            List<AccessibilityNodeInfo> infoList  = root.findAccessibilityNodeInfosByViewId(fieldId);
            if (infoList.size() > 0) {
                //Log.d(TAG, "SetSearchHack: production mode: take from cache");
                info = infoList.get(0);
            }
        } else {
            //info = FindFirstByTextRecursive(root, "Найти");
            info = specific.findId(root);
            if (info != null) {
                Log.d(TAG, "SetSearchHack: research mode: field found "+info.getViewIdResourceName());
                specific.setId(info.getViewIdResourceName());
            }
        }
        return info;
    }

    private boolean ProcessTelegramSearchField(int eventType, String packageName, AccessibilityNodeInfo root) {
        if(eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        && eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED
        && eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            return false;
        if (packageName.equals("org.telegram.messenger")) {
            List<AccessibilityNodeInfo> info = root.findAccessibilityNodeInfosByText("Search");
            if (info.size() > 0) {
                if(IsSearchHackSet())
                    return true;
                Log.d(TAG, "SetSearchHack Telegram");
                AccessibilityNodeInfo node = info.get(0);
                SetSearchHack(() -> {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                });
            } else {
                SetSearchHack(null);
            }
            return true;
        }
        return false;
    }

    private boolean ProcessContactsSearchField(int eventType, String packageName, AccessibilityNodeInfo root) {
        if(eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED)
            return false;
        if (packageName.equals("com.blackberry.contacts")) {
            List<AccessibilityNodeInfo> info = root.findAccessibilityNodeInfosByText("Поиск");
            if (info.size() > 0) {
                if(IsSearchHackSet())
                    return true;
                AccessibilityNodeInfo node = info.get(0);
                Log.d(TAG, "SetSearchHack Contacts");
                SetSearchHack(() -> {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                });
            } else {
                SetSearchHack(null);
            }
            return true;
        }
        return false;
    }

    private boolean ProcessDialerSearchField(int eventType, String packageName, AccessibilityNodeInfo root) {
        if(eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED)
            return false;
        if (packageName.equals("com.android.dialer")) {
            AccessibilityNodeInfo info = FindFirstByTextRecursive(root, "Поиск");
            if (info != null) {
                if(IsSearchHackSet())
                    return true;
                AccessibilityNodeInfo info2 = info.getParent();
                Log.d(TAG, "SetSearchHack Dialer");
                SetSearchHack(() -> {
                    info2.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                });
            } else {
                SetSearchHack(null);
            }
            return true;
        }
        return false;
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
