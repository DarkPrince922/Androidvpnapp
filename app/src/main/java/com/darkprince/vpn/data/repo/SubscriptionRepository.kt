package com.darkprince.vpn.data.repo

import com.darkprince.vpn.core.model.ProxyProfile
import com.darkprince.vpn.core.parser.LinkParser
import com.darkprince.vpn.data.api.ApiClient
import com.darkprince.vpn.data.api.dto.DeviceDto
import com.darkprince.vpn.data.api.dto.DevicesPurchaseRequest
import com.darkprince.vpn.data.api.dto.RenameDeviceRequest
import com.darkprince.vpn.data.api.dto.PurchaseTariffRequest
import com.darkprince.vpn.data.api.dto.ReduceDevicesRequest
import com.darkprince.vpn.data.api.dto.RenewRequest
import com.darkprince.vpn.data.api.dto.SubscriptionListItem
import com.darkprince.vpn.data.api.dto.SubscriptionStatusResponse
import com.darkprince.vpn.data.api.dto.TrafficPurchaseRequest
import com.darkprince.vpn.data.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Request

/** Тариф, извлечённый из purchase-options (форма ответа может отличаться между версиями бота). */
data class TariffOffer(
    val id: Long,
    val name: String,
    val description: String?,
    val periods: List<PeriodPrice>,
    val trafficLimitGb: Int?,
    val deviceLimit: Int?,
)

data class PeriodPrice(val days: Int, val priceKopeks: Long)

@kotlinx.serialization.Serializable
data class SubscriptionUserInfo(
    val uploadBytes: Long? = null,
    val downloadBytes: Long? = null,
    val totalBytes: Long? = null,
    val expireUnix: Long? = null,
)

class SubscriptionRepository(
    private val client: ApiClient,
    private val prefs: AppPrefs,
) {
    private val api get() = client.api

    /** Есть аккаунт кабинета. Гость без аккаунта живёт только по чужой ссылке. */
    private val isLoggedIn: Boolean get() = prefs.cachedRefreshToken != null

    suspend fun status(): SubscriptionStatusResponse = api.subscription()

    /** Список подписок пользователя; пусто без аккаунта и без мультитарифа. */
    suspend fun subscriptions(): List<SubscriptionListItem> {
        if (!isLoggedIn) return emptyList()
        return try {
            api.subscriptions().subscriptions
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Разбирает ссылку, полученную от владельца подписки: принимаем и
     * прямую ссылку Remnawave, и наш deep link darkprincevpn://sub?url=…
     */
    fun parseSharedSubscription(raw: String): String? {
        val text = raw.trim()
        if (text.isBlank()) return null
        val url = when {
            text.startsWith("darkprincevpn://", true) -> {
                android.net.Uri.parse(text).getQueryParameter("url")
                    ?: text.substringAfter("://sub/", "").takeIf { it.isNotBlank() }
            }
            text.startsWith("http://", true) || text.startsWith("https://", true) -> text
            else -> null
        } ?: return null
        return url.takeIf { it.startsWith("http", true) }
    }

    /** Включает гостевой режим по ссылке подписки и сразу проверяет её. */
    suspend fun activateGuestSubscription(rawLink: String): String? {
        val url = parseSharedSubscription(rawLink)
            ?: return "Ссылка не распознана. Нужна ссылка на подписку или QR из приложения владельца."
        return try {
            prefs.setGuestSubUrl(url)
            val (servers, _) = withContext(Dispatchers.IO) { downloadSubscription(null, url) }
            if (servers.isEmpty()) {
                prefs.setGuestSubUrl(null)
                "По ссылке не нашлось серверов. Проверьте, что подписка активна."
            } else null
        } catch (e: Exception) {
            prefs.setGuestSubUrl(null)
            "Не удалось загрузить подписку: ${e.userMessage()}"
        }
    }

    suspend fun exitGuestMode() {
        prefs.setGuestSubUrl(null)
        prefs.setServersRawFor(null, null)
    }

    /**
     * Переключение подписки: данные каждой подписки лежат отдельно, поэтому
     * ничего не удаляем — серверы и выбранный узел прошлой подписки остаются
     * на месте и доступны сразу при возврате к ней.
     */
    suspend fun selectSubscription(id: Long?) {
        prefs.setSelectedSubscription(id)
    }

    /**
     * Догружает серверы для всех подписок, чтобы переключение было мгновенным
     * и работало без сети. Ошибки по отдельной подписке не прерывают остальные.
     */
    suspend fun prefetchAllSubscriptions() = withContext(Dispatchers.IO) {
        for (sub in subscriptions()) {
            val url = sub.subscriptionUrl ?: continue
            try {
                downloadSubscription(sub.id, url)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Ссылка на подписку Remnawave. При нескольких подписках берём ссылку
     * выбранной (она приходит прямо в списке), иначе — через кабинет.
     *
     * Гостевая ссылка — запасной вариант: пока у зарегистрировавшегося гостя
     * нет своей подписки, он продолжает пользоваться той, которой с ним
     * поделились, и не остаётся без VPN во время оплаты.
     */
    suspend fun resolveSubscriptionUrl(): String? {
        // гость без аккаунта: кабинета нет, работаем только по чужой ссылке
        if (!isLoggedIn) return prefs.cachedGuestSubUrl
        return ownSubscriptionUrl() ?: prefs.cachedGuestSubUrl
    }

    private suspend fun ownSubscriptionUrl(): String? {
        val selectedId = prefs.selectedSubscriptionFlow.first()
        if (selectedId != null) {
            val fromList = subscriptions().firstOrNull { it.id == selectedId }?.subscriptionUrl
            if (fromList != null) {
                prefs.setSubUrlFor(selectedId, fromList)
                return fromList
            }
            prefs.subUrlFor(selectedId)?.let { return it }
        }
        val fromLink = try {
            api.connectionLink().subscriptionUrl
        } catch (_: Exception) {
            null
        }
        val url = fromLink ?: try {
            api.subscription().subscriptionUrl
        } catch (_: Exception) {
            null
        } ?: prefs.subscriptionUrlFlow.first()
        if (url != null) prefs.setSubscriptionUrl(url)
        return url
    }

    /**
     * Скачивает подписку Remnawave и парсит серверы (vless/vmess/trojan/ss).
     * User-Agent v2rayNG заставляет Remnawave отдать base64-список ссылок.
     */
    suspend fun fetchServers(forceRefresh: Boolean = false): Pair<List<ProxyProfile>, SubscriptionUserInfo?> =
        withContext(Dispatchers.IO) {
            val subId = prefs.selectedSubscriptionFlow.first()
            if (!forceRefresh) {
                cachedServersFor(subId)?.let { return@withContext it }
            }
            try {
                val url = resolveSubscriptionUrl()
                    ?: throw IllegalStateException("Нет активной подписки")
                val result = downloadSubscription(subId, url)
                // своя подписка заработала — чужую отпускаем, чтобы не занимать
                // место в лимите устройств владельца
                val guestUrl = prefs.cachedGuestSubUrl
                if (guestUrl != null && url != guestUrl && result.first.isNotEmpty()) {
                    prefs.setGuestSubUrl(null)
                }
                result
            } catch (e: Exception) {
                // сеть/сервер недоступны — работаем с сохранённой копией подписки
                cachedServersFor(subId) ?: throw e
            }
        }

    /** Сохранённая копия текущей подписки (без сети). */
    suspend fun cachedServers(): Pair<List<ProxyProfile>, SubscriptionUserInfo?>? =
        cachedServersFor(prefs.selectedSubscriptionFlow.first())

    /** Сохранённая копия конкретной подписки (без сети). */
    suspend fun cachedServersFor(subId: Long?): Pair<List<ProxyProfile>, SubscriptionUserInfo?>? {
        val cached = prefs.serversRawFor(subId)
        if (cached.isNullOrBlank()) return null
        val profiles = LinkParser.parseSubscriptionContent(cached)
        if (profiles.isEmpty()) return null
        val storedInfo = prefs.userInfoFor(subId)?.let {
            try {
                client.json.decodeFromString(SubscriptionUserInfo.serializer(), it)
            } catch (_: Exception) {
                null
            }
        }
        return profiles to storedInfo
    }

    /** Скачивает подписку и складывает результат в кэш конкретной подписки. */
    private suspend fun downloadSubscription(
        subId: Long?,
        url: String,
    ): Pair<List<ProxyProfile>, SubscriptionUserInfo?> {
        // HWID-заголовки нужны Remnawave, чтобы считать устройства и
        // применять лимит из тарифа; без x-hwid панель с включённым лимитом
        // отдаёт 404.
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "v2rayNG/1.10.7")
            .header("Accept", "text/plain")
            .header("x-hwid", prefs.cachedHwid)
            .header("x-device-os", "Android")
            .header("x-ver-os", android.os.Build.VERSION.RELEASE ?: "")
            .header("x-device-model", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim())
            .build()
        return client.plainOkHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Подписка недоступна (HTTP ${response.code})")
            }
            val body = response.body?.string().orEmpty()
            val userInfo = response.header("subscription-userinfo")?.let(::parseUserInfo)
            val profiles = LinkParser.parseSubscriptionContent(body)
            if (profiles.isNotEmpty()) prefs.setServersRawFor(subId, body)
            if (userInfo != null) {
                prefs.setUserInfoFor(
                    subId,
                    client.json.encodeToString(SubscriptionUserInfo.serializer(), userInfo)
                )
            }
            profiles to userInfo
        }
    }

    private fun parseUserInfo(header: String): SubscriptionUserInfo {
        val map = header.split(';')
            .mapNotNull {
                val kv = it.trim().split('=', limit = 2)
                if (kv.size == 2) kv[0] to kv[1].toLongOrNull() else null
            }
            .toMap()
        return SubscriptionUserInfo(
            uploadBytes = map["upload"],
            downloadBytes = map["download"],
            totalBytes = map["total"],
            expireUnix = map["expire"],
        )
    }

    // --- Покупка/продление ---

    suspend fun trialInfo() = api.trialInfo()

    suspend fun activateTrial(): Boolean = try {
        api.activateTrial(buildJsonObject { })
        true
    } catch (_: Exception) {
        false
    }

    /** Адаптивно разбирает purchase-options: ищет массив тарифов в известных полях. */
    suspend fun tariffs(): List<TariffOffer> {
        val root = api.purchaseOptions()
        val arrays = mutableListOf<JsonArray>()
        fun collect(element: JsonElement, depth: Int) {
            if (depth > 3) return
            when (element) {
                is JsonArray -> arrays.add(element)
                is JsonObject -> {
                    for (key in listOf("tariffs", "items", "options", "plans")) {
                        element[key]?.let { collect(it, depth + 1) }
                    }
                }
                else -> Unit
            }
        }
        collect(root, 0)
        val offers = mutableListOf<TariffOffer>()
        for (array in arrays) {
            for (item in array) {
                val obj = item as? JsonObject ?: continue
                val id = obj["id"]?.jsonPrimitive?.longOrNull ?: continue
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "Тариф $id"
                val periods = parsePeriods(obj)
                if (periods.isEmpty()) continue
                offers.add(
                    TariffOffer(
                        id = id,
                        name = name,
                        description = obj["description"]?.jsonPrimitive?.contentOrNull,
                        periods = periods,
                        trafficLimitGb = obj["traffic_limit_gb"]?.jsonPrimitive?.intOrNull,
                        deviceLimit = obj["device_limit"]?.jsonPrimitive?.intOrNull,
                    )
                )
            }
        }
        return offers.distinctBy { it.id }
    }

    private fun parsePeriods(obj: JsonObject): List<PeriodPrice> {
        val result = mutableListOf<PeriodPrice>()
        val periodsElement = obj["period_prices"] ?: obj["periods"] ?: obj["prices"]
        if (periodsElement is JsonArray) {
            for (p in periodsElement) {
                val po = p as? JsonObject ?: continue
                val days = po["days"]?.jsonPrimitive?.intOrNull
                    ?: po["period_days"]?.jsonPrimitive?.intOrNull ?: continue
                val price = po["price_kopeks"]?.jsonPrimitive?.longOrNull
                    ?: po["price"]?.jsonPrimitive?.longOrNull ?: continue
                result.add(PeriodPrice(days, price))
            }
        }
        return result.sortedBy { it.days }
    }

    suspend fun purchaseTariff(tariffId: Long, periodDays: Int): String? = try {
        api.purchaseTariff(PurchaseTariffRequest(tariffId, periodDays))
        null
    } catch (e: Exception) {
        e.userMessage()
    }

    /** Варианты продления: список {period_days, price_kopeks}. */
    suspend fun renewalOptions(): List<PeriodPrice> {
        val root = api.renewalOptions()
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root["options"]?.jsonArray
                ?: root["items"]?.jsonArray
                ?: return emptyList()
            else -> return emptyList()
        }
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val days = obj["period_days"]?.jsonPrimitive?.intOrNull
                ?: obj["days"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val price = obj["price_kopeks"]?.jsonPrimitive?.longOrNull
                ?: obj["price"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            PeriodPrice(days, price)
        }.sortedBy { it.days }
    }

    suspend fun renew(periodDays: Int): String? = try {
        api.renew(RenewRequest(periodDays))
        null
    } catch (e: Exception) {
        e.userMessage()
    }

    // --- Устройства ---

    /** Сводка по устройствам конкретной подписки (null — текущая). */
    suspend fun devicesInfo(subscriptionId: Long? = null): DevicesInfo {
        var limit: Int? = null
        var connected: Int? = null
        try {
            val devices = api.devices(subscriptionId)
            limit = devices.intOf("device_limit")
            connected = devices.intOf("total") ?: (devices["devices"] as? JsonArray)?.size
        } catch (_: Exception) {
        }
        var pricePerDevice: Long? = null
        var maxLimit: Int? = null
        var purchaseAvailable = false
        try {
            val price = api.devicePrice(subscriptionId = subscriptionId)
            purchaseAvailable = price.boolOf("available") ?: true
            pricePerDevice = price.longOf("price_per_device_kopeks")
            maxLimit = price.intOf("max_device_limit")
            if (limit == null) limit = price.intOf("current_device_limit")
        } catch (_: Exception) {
        }
        var reduceAvailable = false
        try {
            val reduction = api.deviceReductionInfo(subscriptionId)
            reduceAvailable = reduction.boolOf("available") ?: false
            if (limit == null) limit = reduction.intOf("current_device_limit")
            if (connected == null) connected = reduction.intOf("connected_devices_count")
        } catch (_: Exception) {
        }
        return DevicesInfo(
            deviceLimit = limit,
            connectedCount = connected,
            pricePerDeviceKopeks = pricePerDevice,
            maxDeviceLimit = maxLimit,
            purchaseAvailable = purchaseAvailable && pricePerDevice != null,
            reduceAvailable = reduceAvailable,
        )
    }

    /** Подключённые устройства подписки. */
    suspend fun devicesList(subscriptionId: Long? = null): List<DeviceDto> = try {
        api.devicesList(subscriptionId).devices
    } catch (_: Exception) {
        emptyList()
    }

    suspend fun deleteDevice(hwid: String, subscriptionId: Long? = null): String? = try {
        api.deleteDevice(hwid, subscriptionId)
        null
    } catch (e: Exception) {
        e.userMessage()
    }

    suspend fun deleteAllDevices(subscriptionId: Long? = null): String? = try {
        api.deleteAllDevices(subscriptionId)
        null
    } catch (e: Exception) {
        e.userMessage()
    }

    suspend fun renameDevice(hwid: String, name: String, subscriptionId: Long? = null): String? = try {
        api.renameDevice(hwid, RenameDeviceRequest(name), subscriptionId)
        null
    } catch (e: Exception) {
        e.userMessage()
    }

    /** HWID этого телефона — чтобы не удалить его по ошибке. */
    val ownHwid: String get() = prefs.cachedHwid

    suspend fun buyDevices(count: Int, subscriptionId: Long? = null): String? = try {
        api.purchaseDevices(DevicesPurchaseRequest(count), subscriptionId)
        null
    } catch (e: Exception) {
        e.userMessage()
    }

    suspend fun reduceDevices(newLimit: Int, subscriptionId: Long? = null): String? = try {
        api.reduceDevices(ReduceDevicesRequest(newLimit), subscriptionId)
        null
    } catch (e: Exception) {
        e.userMessage()
    }

    // --- Трафик ---

    /** Пакеты докупки трафика: [{gb, price_kopeks}]. */
    suspend fun trafficPackages(): List<TrafficPackage> {
        val root = api.trafficPackages()
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root["packages"]?.jsonArray
                ?: root["items"]?.jsonArray
                ?: root["traffic_packages"]?.jsonArray
                ?: return emptyList()
            else -> return emptyList()
        }
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val gb = obj.intOf("gb") ?: obj.intOf("traffic_gb") ?: return@mapNotNull null
            val price = obj.longOf("price_kopeks") ?: obj.longOf("price") ?: return@mapNotNull null
            TrafficPackage(gb, price)
        }.sortedBy { it.gb }
    }

    suspend fun buyTraffic(gb: Int): String? = try {
        api.purchaseTraffic(TrafficPurchaseRequest(gb))
        null
    } catch (e: Exception) {
        e.userMessage()
    }
}

data class DevicesInfo(
    val deviceLimit: Int?,
    val connectedCount: Int?,
    val pricePerDeviceKopeks: Long?,
    val maxDeviceLimit: Int?,
    val purchaseAvailable: Boolean,
    val reduceAvailable: Boolean,
)

data class TrafficPackage(val gb: Int, val priceKopeks: Long)

private fun JsonObject.intOf(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
private fun JsonObject.longOf(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
private fun JsonObject.boolOf(key: String): Boolean? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull
