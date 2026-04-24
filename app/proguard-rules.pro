# ProGuard 规则文件 - 两步验证应用

# 保留我们自己的数据模型类
-keep class com.huahao.authenticator.models.** { *; }

# 保留 Compose 相关（最小化）
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**

# 保留 ML Kit 条码扫描
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# 保留 CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# 保留 ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# 保留 DataStore
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# 保留 Kotlinx 序列化
-keep class kotlinx.serialization.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn kotlinx.serialization.**

# 更激进的优化
-optimizationpasses 5
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,code/allocation/variable
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile
-keepattributes LineNumberTable

# 移除调试信息
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# 移除断言
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}
