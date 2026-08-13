# OffGrid AI — ProGuard Rules

# Keep ONNX Runtime classes (required for native AI inference)
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Keep Qualcomm SNPE classes (required for JNI native calls)
-keep class com.qualcomm.qti.snpe.** { *; }
-dontwarn com.qualcomm.qti.snpe.**

# Keep Coil image loading
-keep class coil.** { *; }

# Keep Compose navigation
-keep class androidx.navigation.** { *; }

# Keep our AI engine package
-keep class com.offgridai.app.ai.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Android media APIs
-keep class android.media.MediaCodec { *; }
-keep class android.media.MediaExtractor { *; }
-keep class android.media.MediaMuxer { *; }

# Remove all logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
