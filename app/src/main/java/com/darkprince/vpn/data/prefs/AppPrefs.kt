package com.darkprince.vpn.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "app_prefs")

/**
 * Хранилище настроек и токенов. Токены дополнительно кэшируются в памяти,
 * чтобы сетевые интерцепторы могли читать их синхронно.
 */
class AppPrefs(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val ACCESS_EXPIRES_AT = longPreferencesKey("access_expires_at")
        val USER_JSON = stringPreferencesKey("user_json")
        val SUB_URL = stringPreferencesKey("subscription_url")
        val SERVERS_RAW = stringPreferencesKey("servers_raw")
        val SUB_USERINFO = stringPreferencesKey("sub_userinfo")
        val SELECTED_SERVER = intPreferencesKey("selected_server")
        val LAST_EXPIRY_NOTIFY_DAY = stringPreferencesKey("last_expiry_notify_day")
    }

    @Volatile var cachedBaseUrl: String = ""
        private set
    @Volatile var cachedAccessToken: String? = null
        private set
    @Volatile var cachedRefreshToken: String? = null
        private set
    @Volatile var cachedAccessExpiresAt: Long = 0L
        private set

    fun warmUp() = runBlocking {
        val p = context.dataStore.data.first()
        // если адрес ещё не сохранён — берём вшитый в сборку адрес кабинета
        cachedBaseUrl = p[Keys.BASE_URL]
            ?: com.darkprince.vpn.BuildConfig.DEFAULT_API_BASE_URL.trimEnd('/')
        cachedAccessToken = p[Keys.ACCESS_TOKEN]
        cachedRefreshToken = p[Keys.REFRESH_TOKEN]
        cachedAccessExpiresAt = p[Keys.ACCESS_EXPIRES_AT] ?: 0L
    }

    val baseUrlFlow: Flow<String> = context.dataStore.data.map {
        it[Keys.BASE_URL] ?: com.darkprince.vpn.BuildConfig.DEFAULT_API_BASE_URL.trimEnd('/')
    }
    val accessTokenFlow: Flow<String?> = context.dataStore.data.map { it[Keys.ACCESS_TOKEN] }
    val userJsonFlow: Flow<String?> = context.dataStore.data.map { it[Keys.USER_JSON] }
    val subscriptionUrlFlow: Flow<String?> = context.dataStore.data.map { it[Keys.SUB_URL] }
    val serversRawFlow: Flow<String?> = context.dataStore.data.map { it[Keys.SERVERS_RAW] }
    val subUserInfoFlow: Flow<String?> = context.dataStore.data.map { it[Keys.SUB_USERINFO] }
    val selectedServerFlow: Flow<Int> = context.dataStore.data.map { it[Keys.SELECTED_SERVER] ?: 0 }

    suspend fun setBaseUrl(url: String) {
        val normalized = url.trim().trimEnd('/')
        cachedBaseUrl = normalized
        context.dataStore.edit { it[Keys.BASE_URL] = normalized }
    }

    suspend fun setTokens(access: String?, refresh: String?, expiresInSeconds: Long?) {
        val expiresAt = if (access != null && expiresInSeconds != null) {
            System.currentTimeMillis() + expiresInSeconds * 1000L
        } else 0L
        cachedAccessToken = access
        cachedRefreshToken = refresh
        cachedAccessExpiresAt = expiresAt
        context.dataStore.edit { p: androidx.datastore.preferences.core.MutablePreferences ->
            if (access == null) p.remove(Keys.ACCESS_TOKEN) else p[Keys.ACCESS_TOKEN] = access
            if (refresh == null) p.remove(Keys.REFRESH_TOKEN) else p[Keys.REFRESH_TOKEN] = refresh
            p[Keys.ACCESS_EXPIRES_AT] = expiresAt
        }
    }

    suspend fun setUserJson(json: String?) {
        context.dataStore.edit { p ->
            if (json == null) p.remove(Keys.USER_JSON) else p[Keys.USER_JSON] = json
        }
    }

    suspend fun setSubscriptionUrl(url: String?) {
        context.dataStore.edit { p ->
            if (url == null) p.remove(Keys.SUB_URL) else p[Keys.SUB_URL] = url
        }
    }

    suspend fun setServersRaw(raw: String?) {
        context.dataStore.edit { p ->
            if (raw == null) p.remove(Keys.SERVERS_RAW) else p[Keys.SERVERS_RAW] = raw
        }
    }

    val lastExpiryNotifyDayFlow: Flow<String?> =
        context.dataStore.data.map { it[Keys.LAST_EXPIRY_NOTIFY_DAY] }

    suspend fun setLastExpiryNotifyDay(day: String) {
        context.dataStore.edit { it[Keys.LAST_EXPIRY_NOTIFY_DAY] = day }
    }

    suspend fun setSubUserInfo(json: String?) {
        context.dataStore.edit { p ->
            if (json == null) p.remove(Keys.SUB_USERINFO) else p[Keys.SUB_USERINFO] = json
        }
    }

    suspend fun setSelectedServer(index: Int) {
        context.dataStore.edit { it[Keys.SELECTED_SERVER] = index }
    }

    suspend fun clearSession() {
        setTokens(null, null, null)
        context.dataStore.edit { p: androidx.datastore.preferences.core.MutablePreferences ->
            p.remove(Keys.USER_JSON)
            p.remove(Keys.SUB_URL)
            p.remove(Keys.SERVERS_RAW)
        }
    }
}
