# 救救我 ProGuard 规则

# 高德 SDK
-keep class com.amap.api.** { *; }
-keep class com.loc.** { *; }
-keep class com.autonavi.** { *; }

# Kotlin
-keepattributes *Annotation*
-keepattributes InnerClasses
-keep class kotlin.Metadata { *; }
