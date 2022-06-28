package com.sateda.keyonekb2;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;

public class KeyoneKb2PluginData {

    @JsonProperty(index=10)
    public ArrayList<SearchPluginData>  SearchPlugins = new ArrayList<>();

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
        public ArrayList<DynamicSearchMethod> DynamicSearchMethod;

        @JsonProperty(index=60)
        public int WaitBeforeSendCharMs;

    }

    public enum DynamicSearchMethodFunction {
        FindAccessibilityNodeInfosByText,
        FindFirstByTextRecursive
    }

    public static class DynamicSearchMethod {
        @JsonProperty(index=10)
        DynamicSearchMethodFunction DynamicSearchMethodFunction;

        @JsonProperty(index=20)
        String ContainsString;
    }
}
