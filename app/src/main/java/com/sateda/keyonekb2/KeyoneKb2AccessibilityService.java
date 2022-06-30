package com.sateda.keyonekb2;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
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

public class KeyoneKb2AccessibilityService extends AccessibilityService {

    public static KeyoneKb2AccessibilityService Instance;
    private static String TAG3 = "KeyoneKb2-AS";

    interface NodeClickableConverter {
        AccessibilityNodeInfo getNode(AccessibilityNodeInfo info);
    }

    public static int STD_EVENTS = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_FOCUSED;

    static class SearchHackPlugin {

        String _packageName;
        String _id = "";

        int _events = 0;

        NodeClickableConverter _converter;

        public ArrayList<AutoClickPluginData.DynamicSearchMethod> DynamicSearchMethod;

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

            for(AutoClickPluginData.DynamicSearchMethod method : DynamicSearchMethod) {
                if(method.DynamicSearchMethodFunction == AutoClickPluginData.DynamicSearchMethodFunction.FindFirstByTextRecursive) {
                    AccessibilityNodeInfo info = FindFirstByTextRecursive(root, method.ContainsString);
                    if(info != null) {
                        return info;
                    }
                }
                if(method.DynamicSearchMethodFunction == AutoClickPluginData.DynamicSearchMethodFunction.FindAccessibilityNodeInfosByText) {
                    List<AccessibilityNodeInfo> infoList = root.findAccessibilityNodeInfosByText(method.ContainsString);
                    if (infoList.size() > 0) {
                        return infoList.get(0);
                    }
                }
            }

            return null;
        }

        private AccessibilityNodeInfo findIdAll(AccessibilityNodeInfo root) {

            for (String searchWord : Instance.DefaultSearchWords) {

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

    KeyoneKb2Settings keyoneKb2Settings;

    ExecutorService executorService;

    ArrayList<String> DefaultSearchWords;

    @Override
    protected void onServiceConnected() {
        Log.v(TAG3, "onServiceConnected()");
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        info.packageNames = SearchHackPackages.toArray(new String[SearchHackPackages.size()]);
        if(!TempAddedSearchHackPlugins.isEmpty())
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        setServiceInfo(info);
    }

    private final ArrayList<String> SearchHackPackages = new ArrayList<>();

    public static final ArrayList<SearchHackPlugin> TempAddedSearchHackPlugins = new ArrayList<>();

    @Override
    public synchronized void onDestroy() {
        Instance = null;
        super.onDestroy();
    }

    @Override
    public synchronized void onCreate() {
        Log.v(TAG3, "onCreate()");
        try {
            super.onCreate();
            Instance = this;

            keyoneKb2Settings = KeyoneKb2Settings.Get(getSharedPreferences(KeyoneKb2Settings.APP_PREFERENCES, Context.MODE_PRIVATE));
            executorService = Executors.newFixedThreadPool(2);

            AutoClickPluginData data2 = FileJsonUtils.DeserializeFromJson("plugin_data", new TypeReference<AutoClickPluginData>() {
            }, getApplicationContext());
            if (data2 == null)
                return;
            if (data2.DefaultSearchWords != null && !data2.DefaultSearchWords.isEmpty()) {
                DefaultSearchWords = data2.DefaultSearchWords;
            } else {
                DefaultSearchWords = new ArrayList<>();
                DefaultSearchWords.add("Search");
                Log.e(TAG3, "DefaultSearchWords array empty. Need to be customized in plugin_data.json. For now set default: 1. Search");
            }
            for (AutoClickPluginData.SearchPluginData data : data2.SearchPlugins) {
                SearchHackPlugin shp = new SearchHackPlugin(data.PackageName);
                SearchHackPackages.add(data.PackageName);
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

                searchHackPlugins.add(shp);
            }

            for(SearchHackPlugin shp2: TempAddedSearchHackPlugins) {
                SearchHackPackages.add(shp2.getPackageName());
                searchHackPlugins.add(shp2);
            }


            for (SearchHackPlugin plugin : searchHackPlugins) {
                if (plugin.getId() == null || plugin.getId().isEmpty()) {
                    String value = GetFromSetting(plugin);
                    if (value != null && value.length() > 0) {
                        plugin.setId(value);
                    }
                }
            }
        } catch(Throwable ex) {
            Log.e(TAG3, "onCreate Exception: "+ex);
            new Thread(() -> {
                try { Thread.sleep(200); } catch (Throwable ignored) {}
                StopService();
            }).start();
        }

    }

    private String GetFromSetting(SearchHackPlugin plugin) {
        keyoneKb2Settings.CheckSettingOrSetDefault(plugin.getPreferenceKey(), "");
        return keyoneKb2Settings.GetStringValue(plugin.getPreferenceKey());
    }

    private void SetToSetting(SearchHackPlugin plugin, String value) {
        keyoneKb2Settings.SetStringValue(plugin.getPreferenceKey(), value);
    }

    public void ClearFromSettings(SearchHackPlugin plugin) {
        keyoneKb2Settings.ClearFromSettings(plugin.getPreferenceKey());
    }



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
                if(event.getContentChangeTypes() == 6) {
                    //Этот хак нужен потому что Blackberry.Dialer жестко спамит эвентами (вроде как никому эти эвенты больше не нужны)
                    Log.d(TAG3, "IGNORING TYPE_WINDOW_CONTENT_CHANGED TYPES: " + event.getContentChangeTypes());
                    return;
                }
            }
            CharSequence packageNameCs = event.getPackageName();
            if (packageNameCs == null || packageNameCs.length() == 0)
                return;
            String packageName = packageNameCs.toString();

            if(!SearchHackPackages.contains(packageName)) {
                LogEventD(event);
                return;
            }

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null)
                return;

            //TODO: Можно еще фильтровать по чтобы не было лишних срабатываний
            //event.getWindowChanges()
            //event.getContentChangeTypes()
            //event.get

            for (SearchHackPlugin plugin : searchHackPlugins) {
                if (ProcessSearchField(event.getEventType(), packageName, root, event, plugin))
                    return;
            }

            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && IsSearchHackSet(packageName)) {
                SetSearchHack(null, null);
                LogEventD(event);
            }
        } catch (Throwable ex) {
            Log.e(TAG3, "onAccessibilityEvent Exception: "+ex);
        }
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

    private boolean ProcessSearchField(int eventType, String packageName, AccessibilityNodeInfo root, AccessibilityEvent event, SearchHackPlugin searchHackPlugin) {
        if (!packageName.equals(searchHackPlugin.getPackageName()))
            return false;
        if(!searchHackPlugin.checkEventType(eventType))
            return false;

        if((eventType & AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            Log.d(TAG3, "TYPE_WINDOW_CONTENT_CHANGED TYPES: " +event.getContentChangeTypes());
        }

        AccessibilityNodeInfo info = searchHackPlugin.Convert(FindOrGetFromCache(root, searchHackPlugin));

        if (info != null) {
            //if (IsSearchHackSet(searchHackPlugin.getPackageName()))
            //    return true;
            if(info.isFocused() )
                return true;
            Log.d(TAG3, "SetSearchHack=SET package: "+searchHackPlugin.getPackageName());
            Log.d(TAG3, "SetSearchHack=SET getClassName: " + event.getClassName());
            int wait = searchHackPlugin.WaitBeforeSendChar;
            SetSearchHack(() -> {
                boolean answer = info.performAction(AccessibilityNodeInfo.ACTION_CLICK);

                //Для случая уезжающего окна поиска как в Яндекс.Навигаторе плагин хватает поле, которое уже не существует
                if(!answer) {
                    Log.e(TAG3, "info.performAction(AccessibilityNodeInfo.ACTION_CLICK) == false");
                }

                if(wait > 0) {
                    try { Thread.sleep(wait); } catch(Throwable ignore) {}
                }
            }, searchHackPlugin.getPackageName());
        } else {
            Log.d(TAG3, "SetSearchHack=NULL package: "+searchHackPlugin.getPackageName());
            Log.d(TAG3, "SetSearchHack=NULL: getClassName: " + event.getClassName());
            SetSearchHack(null, null);
        }
        return true;

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

            if (info != null) {
                if (info.getViewIdResourceName() != null) {
                    Log.d(TAG3, "SetSearchHack: research mode: field found " + info.getViewIdResourceName());
                    searchHackPlugin.setId(info.getViewIdResourceName());
                    SetToSetting(searchHackPlugin, info.getViewIdResourceName());
                } else {
                    //AccessibilityNodeInfo info2 = info.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                    Log.d(TAG3, "SetSearchHack: getViewIdResourceName() == null " + info.getContentDescription());
                }
            }
        }
        return info;
    }

    private static AccessibilityNodeInfo FindFirstByTextRecursive(AccessibilityNodeInfo node, String text) {
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

    public void StopService() {
        disableSelf();
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
        Log.v(TAG3, "onInterrupt()");
    }


    //region ХАК для BB Key2 НЕ_РСТ где кнопку SPEED_KEY переопределить нет возможности

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public synchronized boolean onKeyEvent(KeyEvent event) {
        Log.v(TAG3, "onKeyEvent()");
        try {
            //Это ХАК для BB Key2 НЕ_РСТ где кнопку SPEED_KEY переопределить нет возможности
            //Зажатие speed_key+Буква не передается в сервис клавиатуры
            //Но зато передается сюда, поэтмоу приходится отсюда туда переправлять
            if (!IsMetaFunctionOrFunction(event))
                return false;
            if (KeyoneIME.Instance == null)
                return false;
            // Этот блок ХАК-а нужен на К2_не_РСТ иначе при нажатиии speed_key вызывается меню биндинга букв
            if (event.getKeyCode() == KeyEvent.KEYCODE_FUNCTION) {
                KeyEvent event1 = GetCopy(event);
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    KeyoneIME.Instance.onKeyDown(event1.getKeyCode(), event1);
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    KeyoneIME.Instance.onKeyUp(event1.getKeyCode(), event1);
                }

                return true;
            }
            if (event.getKeyCode() != KeyEvent.KEYCODE_A
                    && event.getKeyCode() != KeyEvent.KEYCODE_C
                    && event.getKeyCode() != KeyEvent.KEYCODE_X
                    && event.getKeyCode() != KeyEvent.KEYCODE_V
                    && event.getKeyCode() != KeyEvent.KEYCODE_Z
                    && event.getKeyCode() != KeyEvent.KEYCODE_0) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                //KeyoneIME.Instance.onKeyDown(event.getKeyCode(), event);
                executorService.execute(
                        () -> {
                            try {
                                KeyEvent event1 = GetCopy(event);
                                Thread.sleep(100);
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

        } catch(Throwable ex) {
            Log.e(TAG3, "onKeyEvent Exception: "+ex);
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
        return new KeyEvent(keyEvent.getDownTime(), keyEvent.getEventTime(), keyEvent.getAction(), keyEvent.getKeyCode(), keyEvent.getRepeatCount(),keyEvent.getMetaState(),keyEvent.getDeviceId(),keyEvent.getScanCode(),keyEvent.getFlags(),keyEvent.getFlags());
    }

    //endregion

}
