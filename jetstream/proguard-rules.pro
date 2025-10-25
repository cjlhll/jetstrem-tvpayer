# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
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

# ========== Android Framework & XML ==========
-dontwarn org.xmlpull.v1.**
-dontnote org.xmlpull.v1.**
-keep,allowobfuscation class org.xmlpull.v1.** { *; }
-keep interface org.xmlpull.v1.** { *; }
-dontwarn org.kxml2.io.**
-dontnote org.kxml2.io.**
-keep class org.kxml2.io.** { *; }

# 解决 R8 XmlResourceParser 警告
-dontwarn android.content.res.XmlResourceParser

# ========== Media3 (ExoPlayer) ==========
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep ExoPlayer classes
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.decoder.** { *; }
-keep class androidx.media3.ui.** { *; }

# Keep custom views
-keep public class * extends android.view.View {
    *** get*();
    void set*(***);
    public <init>(android.content.Context);
    public <init>(android.content.Context, java.lang.Boolean);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ========== Jetpack Compose 优化 ==========
-keepclassmembers class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-dontwarn androidx.compose.**

# 保持 Compose 相关的反射
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ========== Hilt / Dagger ==========
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.** { *; }
-dontwarn dagger.**

# ========== Kotlin Coroutines ==========
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ========== Kotlin Serialization ==========
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.google.jetstream.**$$serializer { *; }
-keepclassmembers class com.google.jetstream.** {
    *** Companion;
}
-keepclasseswithmembers class com.google.jetstream.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ========== OkHttp ==========
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ========== Room Database ==========
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ========== Coil 图片加载 ==========
-keep class coil.** { *; }
-dontwarn coil.**

# ========== 移除日志以提升性能 (Release) ==========
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ========== 通用优化 ==========
# 启用优化
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose

# 保持行号用于崩溃分析
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile