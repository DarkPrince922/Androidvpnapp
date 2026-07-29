# --- Ядро Xray (gomobile-биндинги) ---
-keep class libv2ray.** { *; }
-keep class go.** { *; }

# JNI-класс hev-socks5-tunnel: имена нативных методов должны сохраниться
-keep class com.darkprince.vpn.vpn.TProxyService { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Retrofit ---
# Для suspend-функций R8 обязан сохранить сигнатуры и Continuation,
# иначе любой сетевой вызов падает в release-сборке.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep,allowobfuscation interface com.darkprince.vpn.data.api.BedolagaApi
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn retrofit2.**

# --- kotlinx.serialization ---
-keepattributes *Annotation*
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# DTO и модели приложения сериализуются по имени полей — сохраняем целиком
-keep class com.darkprince.vpn.data.api.dto.** { *; }
-keep class com.darkprince.vpn.core.model.** { *; }
-keep class com.darkprince.vpn.data.repo.SubscriptionUserInfo { *; }
-keepclassmembers class com.darkprince.vpn.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- WorkManager ---
# Воркеры создаются рефлексией по имени класса
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.darkprince.vpn.work.** { *; }

# --- Сервисы, объявленные в манифесте ---
-keep class com.darkprince.vpn.vpn.XVpnService { *; }
-keep class com.darkprince.vpn.vpn.VpnTileService { *; }

# Enum-ы (используются в сериализации)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
