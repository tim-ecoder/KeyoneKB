# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# This will strip `Log.v`, `Log.d`, and `Log.i` statements and will leave `Log.w` and `Log.e` statements intact.

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}


#TODO: Переделать исключение конкретно для этого случая, чтобы остальное все-таки зачищать в релизе
#-dontshrink

#Без этого вырезает XmlBlock$Parser
-dontwarn org.xmlpull.v1.**
-dontnote org.xmlpull.v1.**
-dontwarn android.content.res.**
-dontnote android.content.res.**
-keep class org.xmlpull.** { *; }
-keep class com.android.internal.telephony.** { *; }
-keep class com.sateda.keyonekb2.KeyboardLayout { *; }
-keep class com.sateda.keyonekb2.KeyboardLayout$* { *; }
-keep class com.sateda.keyonekb2.SearchClickPlugin$SearchClickPluginData { *; }
-keep class com.sateda.keyonekb2.SearchClickPlugin$SearchClickPluginData$* { *; }
-keep class com.sateda.keyonekb2.KeyoneKb2Settings$CoreKeyboardSettings { *; }
-keep class com.sateda.keyonekb2.InputMethodServiceCodeCustomizable$KeyboardMechanics { *; }
-keep class com.sateda.keyonekb2.InputMethodServiceCodeCustomizable$KeyboardMechanics$* { *; }