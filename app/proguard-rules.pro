# 救救我 ProGuard 规则

# AndroidX AppCompat / Fragment / Biometric
-keep class androidx.appcompat.** { *; }
-keep class androidx.fragment.** { *; }
-keep class androidx.biometric.** { *; }

# 高德 SDK
-keep class com.amap.api.** { *; }
-keep class com.loc.** { *; }
-keep class com.autonavi.** { *; }

# Kotlin
-keepattributes *Annotation*
-keepattributes InnerClasses
-keep class kotlin.Metadata { *; }
