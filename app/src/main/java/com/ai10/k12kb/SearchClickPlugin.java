package com.ai10.k12kb;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

import static com.ai10.k12kb.K12KbAccessibilityService.TAG3;

public class SearchClickPlugin {

    String _packageName;
    String _id = "";

    int _events = 0;

    K12KbAccessibilityService.NodeClickableConverter _converter;

    public ArrayList<SearchClickPluginData.DynamicSearchMethod> DynamicSearchMethod;

    public int WaitBeforeSendChar;

    public void setConverter(K12KbAccessibilityService.NodeClickableConverter converter) {
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
                AccessibilityNodeInfo info = FindFirstByText(root, method.ContainsString);
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

    /**
     * Узел с текстом, содержащим {@code text}, — то же, что возвращает
     * {@link #FindFirstByTextRecursive}, но без ручного обхода дерева.
     *
     * getChild() — это обращение к процессу приложения, поэтому рекурсия стоит
     * порядка одной транзакции на узел: для глубокого дерева (браузер, лента)
     * счёт идёт на сотни, и всё это в UI-потоке приложения-источника.
     * findAccessibilityNodeInfosByText() делает тот же обход на стороне системы
     * за один запрос.
     *
     * Прямая замена изменила бы выбор узла: системный поиск регистронезависим и
     * смотрит ещё и на contentDescription, то есть в одну сторону находит больше.
     * Поэтому среди его кандидатов берём первый, подходящий под строгое условие
     * рекурсии — getText() содержит подстроку с учётом регистра.
     *
     * В другую сторону он находит меньше: под капотом это View.findViewsWithText,
     * который работает по настоящей иерархии View и не видит виртуальные узлы,
     * отдаваемые через AccessibilityNodeProvider — содержимое WebView и Compose.
     * Обход через getChild() их видит. Поэтому при отсутствии строгого совпадения
     * рекурсия всё равно запускается: для гибридных приложений это единственный
     * способ найти поле.
     */
    public static AccessibilityNodeInfo FindFirstByText(AccessibilityNodeInfo root, String text) {
        if (root == null || text == null)
            return null;

        AccessibilityNodeInfo strict = FirstWithText(FindByTextSafe(root, text), text);
        if (strict != null)
            return strict;

        return FindFirstByTextRecursive(root, text);
    }

    private static List<AccessibilityNodeInfo> FindByTextSafe(AccessibilityNodeInfo root, String text) {
        try {
            return root.findAccessibilityNodeInfosByText(text);
        } catch (Throwable ex) {
            Log.w(TAG3, "findAccessibilityNodeInfosByText failed: " + ex);
            return null;
        }
    }

    /** Первый узел, у которого именно getText() содержит подстроку (с учётом регистра). */
    private static AccessibilityNodeInfo FirstWithText(List<AccessibilityNodeInfo> nodes, String text) {
        if (nodes == null) return null;
        for (int i = 0; i < nodes.size(); i++) {
            AccessibilityNodeInfo n = nodes.get(i);
            if (n == null) continue;
            CharSequence t = n.getText();
            if (t != null && t.toString().contains(text))
                return n;
        }
        return null;
    }

    public static AccessibilityNodeInfo FindFirstByTextRecursive(AccessibilityNodeInfo node, String text) {
        if (node == null)
            return null;
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
        if (root == null)
            return null;

        for (String searchWord : K12KbAccessibilityService.Instance.DefaultSearchWords) {

            // Один системный запрос вместо обхода всего дерева через getChild().
            List<AccessibilityNodeInfo> candidates = FindByTextSafe(root, searchWord);

            // Приоритет прежний: сначала то, что нашла бы рекурсия (строгое
            // совпадение по getText()), и только потом любой кандидат — это то,
            // что раньше давала вторая ветка. Рекурсия остаётся между ними: она
            // видит виртуальные узлы WebView и Compose, до которых системный
            // поиск не доходит, и раньше шла первой.
            AccessibilityNodeInfo strict = FirstWithText(candidates, searchWord);
            if (strict != null)
                return strict;

            AccessibilityNodeInfo info = FindFirstByTextRecursive(root, searchWord);
            if (info != null)
                return info;

            if (candidates != null && !candidates.isEmpty())
                return candidates.get(0);
        }
        return null;
    }

    public String getPartiallyPackageName() {
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

            FileJsonUtils.SleepWithWakes(_wait);
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
            @JsonProperty(index=31)
            public boolean CustomClickAdapterClickFirstChild = false;

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
