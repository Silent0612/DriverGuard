# General Android & Flutter
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.**  { *; }
-keep class io.flutter.util.**  { *; }
-keep class io.flutter.view.**  { *; }
-keep class io.flutter.**  { *; }
-keep class io.flutter.plugins.**  { *; }

# Keep our own code completely
-keep class com.example.flutter_application_1.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# MediaPipe & Protobuf (Critical for Release Crash)
-keep class com.google.mediapipe.** { *; }
-keep interface com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-keep interface com.google.protobuf.** { *; }
-keep class com.google.research.xeno.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**

# Attributes (Important for Reflection/JNI)
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# Native Methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# AndroidX & Architecture Components
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**
-keep class androidx.lifecycle.** { *; }
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**

# Java Standard Libs (Suppress R8 errors)
-dontwarn java.awt.**
-dontwarn javax.annotation.**
-dontwarn sun.misc.**
-dontwarn java.nio.**
-dontwarn javax.lang.**
-dontwarn javax.lang.model.**
-dontwarn com.squareup.javapoet.**
-dontwarn com.google.auto.value.**
-dontwarn org.checkerframework.**
