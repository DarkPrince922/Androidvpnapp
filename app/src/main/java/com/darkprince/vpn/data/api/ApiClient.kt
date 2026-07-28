package com.darkprince.vpn.data.api

import com.darkprince.vpn.data.api.dto.RefreshRequest
import com.darkprince.vpn.data.prefs.AppPrefs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
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

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val isAuthPath = request.url.encodedPath.contains("/cabinet/auth/") &&
            !request.url.encodedPath.endsWith("/cabinet/auth/me") &&
            !request.url.encodedPath.contains("/cabinet/auth/logout")
        val token = validAccessToken()
        val newRequest = if (!isAuthPath && token != null) {
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

    /** Обновляет пару токенов; возвращает новый access-токен или null. */
    suspend fun refreshTokens(): String? {
        val refresh = prefs.cachedRefreshToken ?: return null
        return try {
            val response = refreshApi.refresh(RefreshRequest(refresh))
            if (response.accessToken != null) {
                prefs.setTokens(response.accessToken, response.refreshToken ?: refresh, response.expiresIn)
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

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(baseUrlInterceptor)
        .addInterceptor(authInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Отдельный «чистый» клиент для скачивания подписки и служебных запросов. */
    val plainOkHttp: OkHttpClient = OkHttpClient.Builder()
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
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()
        .create(BedolagaApi::class.java)
}
