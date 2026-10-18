# App rules for R8. AGP adds proguard-android-optimize.txt and each library's consumer rules.

-dontwarn sun.misc.Unsafe
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# Parcelables
-keep class * implements android.os.Parcelable{ *; }

# Http
-keep class org.apache.http.** { *; }
-dontwarn org.apache.http.**
-keep class android.net.http.** { *; }
-dontwarn android.net.http.**
-dontwarn com.google.common.**
# jsoup (version catalog) parses pages for the reader and the website details.
-keep public class org.jsoup.** {
    public *;
}
-keepattributes EnclosingMethod

-dontwarn java.lang.invoke.*

-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry

-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings

-keep public class * extends java.lang.Exception

# One-time read of the per-app rules that release 2.1.3 saved with PaperDB (Kryo). Kryo finds
# classes and fields by name, and the stored records name the App class and its five fields.
-keep class io.paperdb.** { *; }
-keep class com.esotericsoftware.** { *; }
-keep class org.objenesis.** { *; }
-keepnames class arun.com.chromer.data.common.App
-keepclassmembernames class arun.com.chromer.data.common.App { <fields>; }
-keepclassmembers class arun.com.chromer.data.common.App { <init>(); }
