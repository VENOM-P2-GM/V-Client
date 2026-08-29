# Keep JNI entry points (referenced by name from native code / reflection).
-keepclasseswithmembernames class dev.vclient.core.runtime.NativeBridge { native <methods>; }

# Profile / config DTOs are reflectively serialized.
-keep @kotlinx.serialization.Serializable class dev.vclient.** { *; }
-keepclassmembers class dev.vclient.** {
    *** Companion;
}
-keepclasseswithmembers class dev.vclient.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Crash reporting stack traces need readable names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
