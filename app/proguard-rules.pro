# 救救我 ProGuard 规则

# ===== 项目核心类 —— 防止反射/序列化失败 =====
-keep class com.shouhu.guardian.ui.MainActivity { *; }
-keep class com.shouhu.guardian.service.WakeWordService { *; }
-keep class com.shouhu.guardian.service.EmergencyService { *; }
-keep class com.shouhu.guardian.service.ShakeService { *; }
-keep class com.shouhu.guardian.receiver.BootReceiver { *; }

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
