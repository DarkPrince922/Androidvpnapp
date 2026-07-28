# Ядро Xray (gomobile-биндинги) — не обфусцировать
-keep class libv2ray.** { *; }
-keep class go.** { *; }

# JNI-класс hev-socks5-tunnel: имена нативных методов должны сохраниться
-keep class com.darkprince.vpn.vpn.TProxyService { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.darkprince.vpn.** {
    *** Companion;
}
-keepclasseswithmembers class com.darkprince.vpn.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn javax.annotation.**
-keepattributes Signature, Exceptions
