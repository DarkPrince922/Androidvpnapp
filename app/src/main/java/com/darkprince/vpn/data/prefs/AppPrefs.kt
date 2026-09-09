package com.darkprince.vpn.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
        // о скольких непрочитанных обращениях человек уже оповещён: чтобы одно
        // и то же сообщение не всплывало каждый час
        val SUPPORT_NOTIFIED = intPreferencesKey("support_notified_count")
        val ADMIN_TICKETS_NOTIFIED = intPreferencesKey("admin_tickets_notified_count")
        val NEWS_LAST_SEEN_ID = longPreferencesKey("news_last_seen_id")
        val HWID = stringPreferencesKey("hwid")
        val SELECTED_SUBSCRIPTION = longPreferencesKey("selected_subscription")
        val SPLIT_MODE = stringPreferencesKey("split_mode")
        val SPLIT_APPS = stringSetPreferencesKey("split_apps")
        val GUEST_SUB_URL = stringPreferencesKey("guest_sub_url")
        val HIDDEN_UPDATE = intPreferencesKey("hidden_update_code")
        val THEME = stringPreferencesKey("theme")
        val AUTOCONNECT = stringPreferencesKey("autoconnect_mode")
        val KILL_SWITCH = booleanPreferencesKey("kill_switch")
        val FAILOVER = booleanPreferencesKey("failover")
        val PINGS = stringPreferencesKey("last_pings")
        // почему в прошлый раз оборвалась сессия: человек видит это на экране
        // входа, а мы — в обращении в поддержку
        val SESSION_END = stringPreferencesKey("session_end_reason")
    }

    @Volatile var cachedBaseUrl: String = ""
        private set
    @Volatile var cachedHwid: String = ""
        private set

    /** Гостевой режим: подписка получена по ссылке, аккаунта кабинета нет. */
    @Volatile var cachedGuestSubUrl: String? = null
        private set

    /**
     * Почему прошлая сессия оборвалась сама. Пусто, если человек вышел сам
     * или ещё не выходил ни разу.
     *
     * Внезапный выход из аккаунта иначе неотличим от «приложение забыло»:
     * человек видит экран входа наутро и не знает, что случилось ночью, —
     * и мы не знаем тоже, потому что журнал живёт в памяти и умирает вместе
     * с процессом.
     */
    @Volatile var cachedSessionEnd: String? = null
        private set

    /**
     * В памяти телефона лежит рабочая подписка: список узлов и ссылка, по
     * которой его обновляют.
     *
     * Читается на старте, потому что от него зависит, куда открыть
     * приложение. Сессия кабинета может оборваться — по нашей ошибке, по
     * ошибке сервера, — и запирать человека без VPN на экране входа за это
     * нельзя: подписка оплачена и лежит рядом, работать она может и без
     * кабинета.
     */
    @Volatile var cachedHasSubscription: Boolean = false
        private set
    @Volatile var cachedAccessToken: String? = null
        private set
    @Volatile var cachedRefreshToken: String? = null
        private set
    @Volatile var cachedAccessExpiresAt: Long = 0L
        private set

    /** Идентификатор выбранной темы, прочитанный на старте. */
    @Volatile var cachedTheme: String? = null
        private set

    companion object {
        const val AUTO_OFF = "off"
        const val AUTO_BOOT = "boot"
        const val AUTO_NETWORK = "network"
    }

    fun warmUp() = runBlocking {
        val p = context.dataStore.data.first()
        // если адрес ещё не сохранён — берём вшитый в сборку адрес кабинета
        cachedBaseUrl = p[Keys.BASE_URL]
            ?: com.darkprince.vpn.BuildConfig.DEFAULT_API_BASE_URL.trimEnd('/')
        cachedAccessToken = p[Keys.ACCESS_TOKEN]
        cachedRefreshToken = p[Keys.REFRESH_TOKEN]
        cachedAccessExpiresAt = p[Keys.ACCESS_EXPIRES_AT] ?: 0L
        cachedGuestSubUrl = p[Keys.GUEST_SUB_URL]
        cachedSessionEnd = p[Keys.SESSION_END]
        // список узлов хранится по подпискам, под ключами с её номером в
        // имени, поэтому ищем по префиксу, а не по одному known-ключу
        cachedHasSubscription = p.asMap().any { (key, value) ->
            (key.name == Keys.SERVERS_RAW.name || key.name.startsWith("servers_raw_")) &&
                (value as? String).isNullOrBlank().not()
        }
        // тема нужна синхронно: окно красится до первой отрисовки Compose,
        // иначе при светлой теме запуск начинается с тёмной вспышки
        cachedTheme = p[Keys.THEME]
        // идентификатор устройства для учёта в панели: один на установку
        cachedHwid = p[Keys.HWID] ?: java.util.UUID.randomUUID().toString().also { generated ->
            context.dataStore.edit { it[Keys.HWID] = generated }
        }
    }

    /**
     * Автоподключение и защита от обрыва.
     *
     * Оба читаются и из служб, которые поднимаются без интерфейса — при
     * загрузке телефона Compose ещё нет, а решать, подключаться ли, уже надо.
     * Поэтому рядом с потоками лежат и синхронные чтения.
     */
    val autoConnectFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.AUTOCONNECT] ?: AUTO_OFF }

    suspend fun setAutoConnect(mode: String) {
        context.dataStore.edit { it[Keys.AUTOCONNECT] = mode }
    }

    fun autoConnectBlocking(): String = runBlocking {
        context.dataStore.data.first()[Keys.AUTOCONNECT] ?: AUTO_OFF
    }

    val killSwitchFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.KILL_SWITCH] ?: false }

    suspend fun setKillSwitch(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KILL_SWITCH] = enabled }
    }

    fun killSwitchBlocking(): Boolean = runBlocking {
        context.dataStore.data.first()[Keys.KILL_SWITCH] ?: false
    }

    /**
     * Перебор запасных узлов. По умолчанию включён: сидеть без связи, когда
     * рядом есть живой сервер, — не то, что кто-то выбрал бы сознательно.
     */
    val failoverFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.FAILOVER] ?: true }

    suspend fun setFailover(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FAILOVER] = enabled }
    }

    fun failoverBlocking(): Boolean = runBlocking {
        context.dataStore.data.first()[Keys.FAILOVER] ?: true
    }

    /**
     * Последние измеренные задержки: «ключ узла=миллисекунды» через точку с
     * запятой. Нужны службе, чтобы при переборе начинать с ближайшего, а не
     * с первого попавшегося. Мерить их заново в момент сбоя нельзя — это
     * минуты ожидания там, где человек и так остался без связи.
     */
    fun savePings(pings: Map<String, Long>) = runBlocking {
        val packed = pings.entries.joinToString(";") { "${it.key.replace(';', ' ')}=${it.value}" }
        context.dataStore.edit { it[Keys.PINGS] = packed }
    }

    fun pingsBlocking(): Map<String, Long> = runBlocking {
        val packed = context.dataStore.data.first()[Keys.PINGS] ?: return@runBlocking emptyMap()
        packed.split(";").mapNotNull { pair ->
            val at = pair.lastIndexOf('=')
            if (at <= 0) return@mapNotNull null
            val ms = pair.substring(at + 1).toLongOrNull() ?: return@mapNotNull null
            pair.substring(0, at) to ms
        }.toMap()
    }

    val guestSubUrlFlow: Flow<String?> = context.dataStore.data.map { it[Keys.GUEST_SUB_URL] }

    suspend fun setGuestSubUrl(url: String?) {
        cachedGuestSubUrl = url
        context.dataStore.edit { p ->
            if (url == null) p.remove(Keys.GUEST_SUB_URL) else p[Keys.GUEST_SUB_URL] = url
        }
    }

    val selectedSubscriptionFlow: Flow<Long?> =
        context.dataStore.data.map { it[Keys.SELECTED_SUBSCRIPTION] }

    /** Режим раздельного туннелирования: ALL / ONLY_SELECTED / EXCEPT_SELECTED. */
    /**
     * Выбранная тема оформления. Пусто — значит человек её не трогал: тогда
     * тему подбирает сам экран, а не хранилище.
     */
    val themeFlow: Flow<String?> = context.dataStore.data.map { it[Keys.THEME] }

    suspend fun setTheme(id: String) {
        cachedTheme = id
        context.dataStore.edit { it[Keys.THEME] = id }
    }

    val splitModeFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.SPLIT_MODE] ?: "ALL" }

    val splitAppsFlow: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.SPLIT_APPS] ?: emptySet() }

    suspend fun splitMode(): String = context.dataStore.data.first()[Keys.SPLIT_MODE] ?: "ALL"

    suspend fun splitApps(): Set<String> = context.dataStore.data.first()[Keys.SPLIT_APPS] ?: emptySet()

    suspend fun setSplitMode(mode: String) {
        context.dataStore.edit { it[Keys.SPLIT_MODE] = mode }
    }

    suspend fun setSplitApps(packages: Set<String>) {
        context.dataStore.edit { it[Keys.SPLIT_APPS] = packages }
    }

    suspend fun setSelectedSubscription(id: Long?) {
        context.dataStore.edit { p ->
            if (id == null) p.remove(Keys.SELECTED_SUBSCRIPTION) else p[Keys.SELECTED_SUBSCRIPTION] = id
        }
    }

    val baseUrlFlow: Flow<String> = context.dataStore.data.map {
        it[Keys.BASE_URL] ?: com.darkprince.vpn.BuildConfig.DEFAULT_API_BASE_URL.trimEnd('/')
    }
    val accessTokenFlow: Flow<String?> = context.dataStore.data.map { it[Keys.ACCESS_TOKEN] }
    val userJsonFlow: Flow<String?> = context.dataStore.data.map { it[Keys.USER_JSON] }

    /**
     * Есть ли ещё сессия кабинета. Нужен именно поток: токен может пропасть,
     * пока приложение открыто, — сервер ответил «не принимаю», — и экраны
     * должны узнать об этом сразу, а не при следующем запуске.
     */
    val hasSessionFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.REFRESH_TOKEN] != null }
    val subscriptionUrlFlow: Flow<String?> = context.dataStore.data.map { it[Keys.SUB_URL] }
    val serversRawFlow: Flow<String?> = context.dataStore.data.map { it[Keys.SERVERS_RAW] }
    val subUserInfoFlow: Flow<String?> = context.dataStore.data.map { it[Keys.SUB_USERINFO] }

    // --- Данные по конкретной подписке ---
    // Каждая подписка держит свой список серверов, ссылку и выбранный сервер,
    // поэтому переключение между подписками ничего не теряет.

    private fun serversKey(subId: Long?) = stringPreferencesKey("servers_raw_${subId ?: "default"}")
    private fun subUrlKey(subId: Long?) = stringPreferencesKey("sub_url_${subId ?: "default"}")
    private fun userInfoKey(subId: Long?) = stringPreferencesKey("sub_userinfo_${subId ?: "default"}")
    private fun serverIndexKey(subId: Long?) = intPreferencesKey("selected_server_${subId ?: "default"}")
    private fun serverKeyKey(subId: Long?) = stringPreferencesKey("selected_server_key_${subId ?: "default"}")

    suspend fun serversRawFor(subId: Long?): String? =
        context.dataStore.data.first()[serversKey(subId)]

    /**
     * Разовый переезд со старого формата, когда кэш и выбор сервера были
     * общими на всё приложение.
     *
     * Раньше это был откат при чтении: нет кэша у подписки — берём общий. Но
     * писать в общий давно перестали, и он навсегда остался с серверами той
     * подписки, которая была активна до разделения. В итоге любая подписка,
     * у которой своего кэша ещё нет, получала чужой список — и выбранный
     * сервер, разумеется, в нём не находился.
     *
     * Сюда же переносим выбор, сохранённый под «default»: экран сохраняет его
     * под номером подписки, а номер известен не сразу — до ответа кабинета он
     * null, и первый же выбор человека уходил не в тот ящик.
     *
     * Переносим только в пустое место: то, что подписка уже знает про себя,
     * всегда вернее старых общих данных.
     */
    suspend fun adoptLegacySelection(subId: Long?) {
        context.dataStore.edit { p ->
            val legacyRaw = p[Keys.SERVERS_RAW]
            if (legacyRaw != null) {
                if (p[serversKey(subId)] == null) p[serversKey(subId)] = legacyRaw
                p.remove(Keys.SERVERS_RAW)
            }
            val legacyInfo = p[Keys.SUB_USERINFO]
            if (legacyInfo != null) {
                if (p[userInfoKey(subId)] == null) p[userInfoKey(subId)] = legacyInfo
                p.remove(Keys.SUB_USERINFO)
            }
            if (subId != null) {
                val defaultKey = p[serverKeyKey(null)]
                if (defaultKey != null) {
                    if (p[serverKeyKey(subId)] == null) p[serverKeyKey(subId)] = defaultKey
                    p.remove(serverKeyKey(null))
                }
                val defaultIndex = p[serverIndexKey(null)]
                if (defaultIndex != null) {
                    if (p[serverIndexKey(subId)] == null) p[serverIndexKey(subId)] = defaultIndex
                    p.remove(serverIndexKey(null))
                }
            }
        }
    }

    suspend fun setServersRawFor(subId: Long?, raw: String?) {
        if (!raw.isNullOrBlank()) cachedHasSubscription = true
        context.dataStore.edit { p ->
            if (raw == null) p.remove(serversKey(subId)) else p[serversKey(subId)] = raw
        }
    }

    suspend fun subUrlFor(subId: Long?): String? = context.dataStore.data.first()[subUrlKey(subId)]

    suspend fun setSubUrlFor(subId: Long?, url: String?) {
        context.dataStore.edit { p ->
            if (url == null) p.remove(subUrlKey(subId)) else p[subUrlKey(subId)] = url
        }
    }

    suspend fun userInfoFor(subId: Long?): String? = context.dataStore.data.first()[userInfoKey(subId)]

    suspend fun setUserInfoFor(subId: Long?, json: String?) {
        context.dataStore.edit { p ->
            if (json == null) p.remove(userInfoKey(subId)) else p[userInfoKey(subId)] = json
        }
    }

    /**
     * Номер сборки обновления, полосу про которое закрыли крестиком.
     * Переживает перезапуск: иначе полоса возвращалась бы при каждом запуске.
     */
    suspend fun hiddenUpdateCode(): Int =
        context.dataStore.data.first()[Keys.HIDDEN_UPDATE] ?: 0

    suspend fun setHiddenUpdateCode(code: Int) {
        context.dataStore.edit { it[Keys.HIDDEN_UPDATE] = code }
    }

    suspend fun selectedServerFor(subId: Long?): Int =
        context.dataStore.data.first()[serverIndexKey(subId)] ?: 0

    suspend fun setSelectedServerFor(subId: Long?, index: Int) {
        context.dataStore.edit { it[serverIndexKey(subId)] = index }
    }

    /** Выбранный узел по имени. null — выбор ещё не переносили со старого формата. */
    suspend fun selectedServerKeyFor(subId: Long?): String? =
        context.dataStore.data.first()[serverKeyKey(subId)]

    suspend fun setSelectedServerKeyFor(subId: Long?, key: String) {
        context.dataStore.edit { it[serverKeyKey(subId)] = key }
    }
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

    /**
     * Записывает, почему сессия оборвалась. Текст сервера обрезаем: в теле
     * ошибки может приехать целая HTML-страница промежуточного узла.
     */
    suspend fun noteSessionEnded(code: Int, detail: String?) {
        val at = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.US)
            .format(java.util.Date())
        val tail = detail?.trim()?.take(120)?.takeIf { it.isNotEmpty() }
        val text = if (tail == null) "$at — сервер ответил $code" else "$at — сервер ответил $code: $tail"
        cachedSessionEnd = text
        context.dataStore.edit { p -> p[Keys.SESSION_END] = text }
    }

    /**
     * Токенов нет, а сервер об этом ничего не говорил.
     *
     * Второй путь потери сессии, и внешне он неотличим от первого: человек
     * видит экран входа. Разница принципиальная — в первом случае виноват
     * ответ кабинета, во втором приложение потеряло сохранённое само, и
     * искать надо в разных местах. Поэтому случай подписываем отдельно.
     */
    suspend fun noteSessionVanished() {
        val at = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.US)
            .format(java.util.Date())
        val text = "$at — вход пропал без ответа сервера"
        cachedSessionEnd = text
        context.dataStore.edit { p -> p[Keys.SESSION_END] = text }
    }

    /** Вход удался — прошлая причина больше не новость. */
    suspend fun clearSessionEndReason() {
        cachedSessionEnd = null
        context.dataStore.edit { p -> p.remove(Keys.SESSION_END) }
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

    /**
     * Наибольший id новости, которую человек уже видел в ленте. Пусто значит
     * «ленту ещё не открывали» — тогда точку не рисуем вовсе: новичку помечать
     * непрочитанным весь архив бессмысленно.
     */
    val newsLastSeenIdFlow: Flow<Long?> =
        context.dataStore.data.map { it[Keys.NEWS_LAST_SEEN_ID] }

    suspend fun setNewsLastSeenId(id: Long) {
        context.dataStore.edit { it[Keys.NEWS_LAST_SEEN_ID] = id }
    }

    val lastExpiryNotifyDayFlow: Flow<String?> =
        context.dataStore.data.map { it[Keys.LAST_EXPIRY_NOTIFY_DAY] }

    suspend fun setLastExpiryNotifyDay(day: String) {
        context.dataStore.edit { it[Keys.LAST_EXPIRY_NOTIFY_DAY] = day }
    }

    val supportNotifiedFlow: Flow<Int> =
        context.dataStore.data.map { it[Keys.SUPPORT_NOTIFIED] ?: 0 }

    suspend fun setSupportNotified(count: Int) {
        context.dataStore.edit { it[Keys.SUPPORT_NOTIFIED] = count }
    }

    val adminTicketsNotifiedFlow: Flow<Int> =
        context.dataStore.data.map { it[Keys.ADMIN_TICKETS_NOTIFIED] ?: 0 }

    suspend fun setAdminTicketsNotified(count: Int) {
        context.dataStore.edit { it[Keys.ADMIN_TICKETS_NOTIFIED] = count }
    }

    suspend fun setSubUserInfo(json: String?) {
        context.dataStore.edit { p ->
            if (json == null) p.remove(Keys.SUB_USERINFO) else p[Keys.SUB_USERINFO] = json
        }
    }

    suspend fun setSelectedServer(index: Int) {
        context.dataStore.edit { it[Keys.SELECTED_SERVER] = index }
    }

    /**
     * Полностью забывает всё, что относилось к прошлому аккаунту.
     *
     * Данные подписок лежат под ключами, в имя которых входит её номер, —
     * перечислить их заранее нельзя, поэтому идём по префиксам. Без этого
     * человек выходил из аккаунта, заходил в другой и видел чужую подписку:
     * остаток трафика, срок и список серверов оставались от предыдущего.
     */
    /** Кто был в аккаунте в прошлый раз — по нему решаем, чужая ли подписка. */
    suspend fun cachedUserJson(): String? = context.dataStore.data.first()[Keys.USER_JSON]

    suspend fun clearSession() {
        setTokens(null, null, null)
        cachedHasSubscription = false
        context.dataStore.edit { p: androidx.datastore.preferences.core.MutablePreferences ->
            p.remove(Keys.USER_JSON)
            p.remove(Keys.SUB_URL)
            p.remove(Keys.SERVERS_RAW)
            p.remove(Keys.SUB_USERINFO)
            p.remove(Keys.SELECTED_SUBSCRIPTION)
            p.remove(Keys.SELECTED_SERVER)
            val prefixes = listOf(
                "servers_raw_",
                "sub_url_",
                "sub_userinfo_",
                "selected_server_",
                "selected_server_key_",
            )
            val stale = p.asMap().keys.filter { key -> prefixes.any { key.name.startsWith(it) } }
            stale.forEach { p.remove(it) }
        }
    }
}
