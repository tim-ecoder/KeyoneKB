package com.sateda.keyonekb2;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

import static com.sateda.keyonekb2.KeyoneKb2AccessibilityService.TAG3;

public class SearchClickPlugin {

    String _packageName;
    String _id = "";

    int _events = 0;

    KeyoneKb2AccessibilityService.NodeClickableConverter _converter;

    public ArrayList<SearchClickPluginData.DynamicSearchMethod> DynamicSearchMethod;

    public int WaitBeforeSendChar;

    public void setConverter(KeyoneKb2AccessibilityService.NodeClickableConverter converter) {
        _converter = converter;
    }

    public void setEvents(int events) {
        _events = events;
    }

    public SearchClickPlugin(String packageName) {
        _packageName = packageName;
    }

    public void setId(String id) {
        _id = id;
    }

    public String getId() {
        return _id;
    }

    public String getPreferenceKey() {
        return "" + _packageName;
    }

    public AccessibilityNodeInfo findId(AccessibilityNodeInfo root) {

        if (DynamicSearchMethod == null || DynamicSearchMethod.isEmpty()) {
            return findIdAll(root);
        }

        for (SearchClickPluginData.DynamicSearchMethod method : DynamicSearchMethod) {
            if (method.DynamicSearchMethodFunction == SearchClickPluginData.DynamicSearchMethodFunction.FindFirstByTextRecursive) {
                AccessibilityNodeInfo info = FindFirstByTextRecursive(root, method.ContainsString);
                if (info != null) {
                    return info;
                }
            }
            if (method.DynamicSearchMethodFunction == SearchClickPluginData.DynamicSearchMethodFunction.FindAccessibilityNodeInfosByText) {
                List<AccessibilityNodeInfo> infoList = root.findAccessibilityNodeInfosByText(method.ContainsString);
                if (infoList.size() > 0) {
                    return infoList.get(0);
                }
            }
        }

        return null;
    }

    public static AccessibilityNodeInfo FindFirstByTextRecursive(AccessibilityNodeInfo node, String text) {
        if (node == null)
            return null;
        if(node.getViewIdResourceName() != null)
            Log.d(TAG3, node.getViewIdResourceName());
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

    private AccessibilityNodeInfo findIdAll(AccessibilityNodeInfo root) {

        for (String searchWord : KeyoneKb2AccessibilityService.Instance.DefaultSearchWords) {

            AccessibilityNodeInfo info = FindFirstByTextRecursive(root, searchWord);
            if (info != null) {
                return info;
            }
            List<AccessibilityNodeInfo> infoList = root.findAccessibilityNodeInfosByText(searchWord);
            if (infoList.size() > 0) {
                return infoList.get(0);
            }
        }
        return null;
    }

    public String getPackageName() {
        return _packageName;
    }

    public boolean checkEventType(int eventType) {
        if ((eventType & _events) == 0)
            return false;

        return true;
    }

    public AccessibilityNodeInfo Convert(AccessibilityNodeInfo info) {
        if (_converter != null && info != null)
            return _converter.getNode(info);
        return info;
    }

    public static class SearchPluginLauncher {

        public SearchPluginLauncher(String packageName, AccessibilityNodeInfo info, int wait) {
            PackageName = packageName;
            _info = info;
            _wait = wait;
        }

        public String PackageName;
        public String ViewIdResourceName;
        AccessibilityNodeInfo _info;
        int _wait = 0;

        public void FirePluginAction() {
            boolean answer = _info.performAction(AccessibilityNodeInfo.ACTION_CLICK);

            //Для случая уезжающего окна поиска как в Яндекс.Навигаторе плагин хватает поле, которое уже не существует
            if (!answer) {
                Log.e(TAG3, "info.performAction(AccessibilityNodeInfo.ACTION_CLICK) == false");
            }

            if (_wait > 0) {
                try {
                    Thread.sleep(_wait);
                } catch (Throwable ignore) {
                }
            }
        }

        public boolean Equals(SearchPluginLauncher other) {
            if (other == null)
                return false;
            if (!this.PackageName.equals(other.PackageName))
                return false;
            if (this.ViewIdResourceName != null && !this.ViewIdResourceName.isEmpty())
                return this.ViewIdResourceName.equals(other.ViewIdResourceName);
            return this._info.equals(other._info);
        }

        public boolean IsSameAsMine(String packageName, AccessibilityNodeInfo info) {
            if (!PackageName.equals(packageName))
                return false;
            if (this.ViewIdResourceName != null && !this.ViewIdResourceName.isEmpty())
                return this.ViewIdResourceName.equals(info.getViewIdResourceName());
            return this._info.equals(info);
        }

        public boolean IsSameAsMine(String packageName) {
            if (!PackageName.equals(packageName))
                return false;
            return true;
        }

    }

    public static class SearchClickPluginData {

        @JsonProperty(index=5)
        public ArrayList<String>  DefaultSearchWords = new ArrayList<>();
        @JsonProperty(index=10)
        public ArrayList<SearchPluginData>  SearchPlugins = new ArrayList<>();

        @JsonProperty(index=20)
        public ArrayList<SearchPluginData>  ClickerPlugins = new ArrayList<>();

        public static class SearchPluginData {

            @JsonProperty(index=10)
            public String PackageName;

            @JsonProperty(index=20)
            public boolean AdditionalEventTypeTypeWindowContentChanged = false;

            @JsonProperty(index=30)
            public boolean CustomClickAdapterClickParent = false;

            @JsonProperty(index=40)
            public String SearchFieldId;

            @JsonProperty(index=50)
            public ArrayList<SearchClickPluginData.DynamicSearchMethod> DynamicSearchMethod;

            @JsonProperty(index=60)
            public int WaitBeforeSendCharMs;

        }

        public enum DynamicSearchMethodFunction {
            FindAccessibilityNodeInfosByText,
            FindFirstByTextRecursive
        }

        public static class DynamicSearchMethod {
            @JsonProperty(index=10)
            SearchClickPluginData.DynamicSearchMethodFunction DynamicSearchMethodFunction;

            @JsonProperty(index=20)
            String ContainsString;
        }
    }
}
