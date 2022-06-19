package com.sateda.keyonekb2;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class KeyoneAccessibilityService extends AccessibilityService {
    private String TAG = "KeyoneAccessibilityService";
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        if(event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            && event.getEventType() != AccessibilityEvent.TYPE_VIEW_FOCUSED
            || event.getPackageName() == null)
            return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if(root == null)
            return;

        if (event.getPackageName().equals("org.telegram.messenger")) {
            List<AccessibilityNodeInfo> info = root.findAccessibilityNodeInfosByText("Search");
            if (info.size() > 0) {
                Log.d(TAG, "SetSearchHack Telegram");
                AccessibilityNodeInfo node = info.get(0);
                SetSearchHack(() -> {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                });
            } else {
                Log.d(TAG, "SetSearchHack NULL");
                SetSearchHack(null);
            }
        } else if(event.getPackageName().equals("com.android.dialer")) {
            AccessibilityNodeInfo info = FindFirstByTextRecursive(root,"Поиск");
            if (info != null) {
                AccessibilityNodeInfo info2 = info.getParent();
                Log.d(TAG, "SetSearchHack Dialer");
                SetSearchHack(() -> {
                    info2.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                });
            } else {
                Log.d(TAG, "SetSearchHack NULL");
                SetSearchHack(null);
            }
        } else if(event.getPackageName().equals("com.blackberry.contacts")) {
            List<AccessibilityNodeInfo> info = root.findAccessibilityNodeInfosByText("Поиск");
            if (info.size() > 0) {
                AccessibilityNodeInfo node = info.get(0);
                Log.d(TAG, "SetSearchHack Contacts");
                SetSearchHack(() -> {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                });
            } else {
                Log.d(TAG, "SetSearchHack NULL");
                SetSearchHack(null);
            }
        }
        else {
            SetSearchHack(null);
            //AccessibilityNodeInfo root = getRootInActiveWindow();
            Log.d(TAG, "event.getEventType() "+event.getEventType());
            Log.d(TAG, "event.getSource() "+event.getSource());
            Log.d(TAG, "event.getPackageName() "+event.getPackageName());
            Log.d(TAG, "event.getClassName() "+event.getClassName());
            Log.d(TAG, "event.getText() "+event.getText());
            Log.d(TAG, "event.getContentDescription() "+event.getContentDescription());
            Log.d(TAG, "event.getEventType() "+event.getEventType());
            Log.d(TAG, "event.getWindowId() "+event.getWindowId());
        }


    }


    private AccessibilityNodeInfo FindFirstByTextRecursive(AccessibilityNodeInfo node, String text) {
        if (node == null)
            return null;
        if(node.getViewIdResourceName() != null)
            Log.d(TAG, node.getViewIdResourceName());
        if (node.getText() != null) {
            if (node.getText().toString().contains(text))
                return node;
            else Log.d(TAG, "TEXT: "+node.getText());
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
            KeyoneIME.Instance.SearchHack = processable;
        }
    }

    @Override
    public void onInterrupt() {
    }

}
