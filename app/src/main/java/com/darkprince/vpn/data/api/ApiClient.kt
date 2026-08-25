package com.darkprince.vpn.data.api

import com.darkprince.vpn.data.api.dto.RefreshRequest
import com.darkprince.vpn.data.prefs.AppPrefs
import com.darkprince.vpn.core.xray.XrayConfigBuilder
import com.darkprince.vpn.vpn.VpnState
import com.darkprince.vpn.vpn.VpnStateStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * HTTP-клиент Cabinet API. Базовый URL берётся из настроек на каждый запрос,
 * поэтому адрес кабинета можно менять без пересоздания клиента.
 */
class ApiClient(private val prefs: AppPrefs) {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /** Подставляет актуальный базовый URL из настроек. */
    private val baseUrlInterceptor = Interceptor { chain ->
        val base = prefs.cachedBaseUrl
        val baseUrl = base.toHttpUrlOrNull()
            ?: throw java.io.IOException("Адрес кабинета не настроен")
        val original = chain.request()
        // placeholder-хост "api.invalid" заменяем на настоящий
        val newUrl = original.url.newBuilder()
            .scheme(baseUrl.scheme)
            .host(baseUrl.host)
            .port(baseUrl.port)
            .encodedPath(baseUrl.encodedPath.trimEnd('/') + original.url.encodedPath)
            .build()
        chain.proceed(original.newBuilder().url(newUrl).build())
    }

    /**
     * Точки входа, которые ходят без токена: вход, регистрация, обновление
     * сессии, восстановление пароля. Токена там либо ещё нет, либо он в теле
     * запроса.
     *
     * Список именно такой — перечисляем безымянные, а не «всё под /auth/,
     * кроме...». Раздел /cabinet/auth/ смешанный: рядом с входом там лежат
     * и /me, и /me/permissions, которым токен обязателен. Правило «под auth
     * токен не нужен» уже один раз молча сломало новый эндпоинт, и с
     * исключениями оно сломается снова — каждый следующий придётся
     * вспоминать вручную.
     */
    private val anonymousPaths = listOf(
        "/cabinet/auth/deeplink/",
        "/cabinet/auth/email/login",
        "/cabinet/auth/email/register",
        "/cabinet/auth/email/verify",
        "/cabinet/auth/password/",
        "/cabinet/auth/refresh",
    )

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath
        val anonymous = anonymousPaths.any { path.contains(it) }
        val token = validAccessToken()
        val newRequest = if (!anonymous && token != null) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else request
        chain.proceed(newRequest)
    }

    private fun validAccessToken(): String? {
        val token = prefs.cachedAccessToken ?: return null
        val expiresAt = prefs.cachedAccessExpiresAt
        if (expiresAt > 0 && System.currentTimeMillis() > expiresAt - 30_000) {
            // истёк — пробуем обновить синхронно (мы в фоновом потоке OkHttp)
            return runBlocking { refreshTokens() }
        }
        return token
    }

    private val refreshMutex = Mutex()
    @Volatile private var lastRefreshAt = 0L

    /**
     * Обновляет пару токенов; возвращает актуальный access-токен или null.
     * Bedolaga ротирует refresh-токен при каждом обновлении, поэтому
     * обновление строго одиночное (Mutex) — параллельные запросы ждут и
     * забирают результат первого, а не затирают сессию друг друга.
     */
    suspend fun refreshTokens(force: Boolean = false): String? = refreshMutex.withLock {
        val current = prefs.cachedAccessToken
        val stillValid = current != null &&
            prefs.cachedAccessExpiresAt > System.currentTimeMillis() + 60_000
        // пока мы ждали Mutex, токен мог обновить другой запрос
        if (current != null && (stillValid && !force || System.currentTimeMillis() - lastRefreshAt < 10_000)) {
            return current
        }
        val refresh = prefs.cachedRefreshToken ?: return null
        try {
            val response = refreshApi.refresh(RefreshRequest(refresh))
            if (response.accessToken != null) {
                prefs.setTokens(response.accessToken, response.refreshToken ?: refresh, response.expiresIn)
                lastRefreshAt = System.currentTimeMillis()
                response.accessToken
            } else null
        } catch (e: retrofit2.HttpException) {
            if (e.code() in 400..499) prefs.setTokens(null, null, null)
            null
        } catch (_: Exception) {
            // сеть/сервер недоступны — сессию не сбрасываем
            prefs.cachedAccessToken
        }
    }

    /** На 401 обновляем токен и повторяем запрос один раз. */
    private val tokenAuthenticator = Authenticator { _, response ->
        if (response.request.header("Authorization") == null) return@Authenticator null
        val attempts = generateSequence(response) { it.priorResponse }.count()
        if (attempts >= 2) return@Authenticator null
        val newToken = runBlocking { refreshTokens(force = true) } ?: return@Authenticator null
        response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    /** Сброс keep-alive соединений после смены сети (поднятие/остановка VPN). */
    fun onNetworkChanged() {
        okHttp.connectionPool.evictAll()
        plainOkHttp.connectionPool.evictAll()
    }

    /**
     * Приложение исключено из VPN (иначе трафик ядра зациклится), поэтому при
     * активном туннеле запросы кабинета идут через локальный SOCKS ядра Xray —
     * то есть через VPN, в обход блокировок оператора. DNS при этом
     * резолвится удалённо. При недоступности SOCKS — fallback напрямую.
     */
    private val vpnAwareProxySelector = object : ProxySelector() {
        private val socks =
            Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", XrayConfigBuilder.SOCKS_PORT))

        override fun select(uri: URI?): MutableList<Proxy> =
            if (VpnStateStore.state.value == VpnState.CONNECTED) {
                mutableListOf(socks, Proxy.NO_PROXY)
            } else {
                mutableListOf(Proxy.NO_PROXY)
            }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: java.io.IOException?) = Unit
    }

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(baseUrlInterceptor)
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .proxySelector(vpnAwareProxySelector)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Отдельный «чистый» клиент для скачивания подписки и служебных запросов. */
    val plainOkHttp: OkHttpClient = OkHttpClient.Builder()
        .proxySelector(vpnAwareProxySelector)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val contentType = "application/json".toMediaType()

    val api: BedolagaApi = Retrofit.Builder()
        .baseUrl("http://api.invalid/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()
        .create(BedolagaApi::class.java)

    /** Клиент без auth-интерцептора — только для refresh, чтобы избежать рекурсии. */
    private val refreshApi: BedolagaApi = Retrofit.Builder()
        .baseUrl("http://api.invalid/")
        .client(
            OkHttpClient.Builder()
                .addInterceptor(baseUrlInterceptor)
                .proxySelector(vpnAwareProxySelector)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()
        .create(BedolagaApi::class.java)
}
