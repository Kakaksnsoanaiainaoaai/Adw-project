# ADW PRODUCTION VDLMS - ProGuard / R8 Rules

# Keep JNI native bridge methods and callbacks used by libadw_vdlms_engine.so
-keep class com.example.audio.NativeAudioBridge {
    *;
}
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Oboe / Android Audio classes
-keep class com.google.oboe.** { *; }
-dontwarn com.google.oboe.**

-keepattributes SourceFile,LineNumberTable
