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
-keep class com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-keep class org.vosk.Model {
    <init>(...);
    *;
}

# It might be necessary to also keep related Vosk classes used via reflection
-keep class org.vosk.LibVosk {
    *;
}

-keep class org.vosk.Recognizer {
    *;
}

-keep class org.vosk.SpeakerModel {
    *;
}

# If using JNA, which Vosk relies on, ensure its native methods and classes are kept
-keep class com.sun.jna.** { *; }
-keep interface com.sun.jna.** { *; }
-keep class net.java.dev.jna.** { *; }
-keep interface net.java.dev.jna.** { *; }
