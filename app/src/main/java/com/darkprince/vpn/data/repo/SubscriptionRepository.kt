package com.darkprince.vpn.data.repo

import com.darkprince.vpn.core.model.ProxyProfile
import com.darkprince.vpn.core.parser.LinkParser
import com.darkprince.vpn.data.api.ApiClient
import com.darkprince.vpn.data.api.dto.PurchaseTariffRequest
import com.darkprince.vpn.data.api.dto.RenewRequest
import com.darkprince.vpn.data.api.dto.SubscriptionStatusResponse
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

    suspend fun status(): SubscriptionStatusResponse = api.subscription()

    /** Ссылка на подписку Remnawave (через кабинет, с запасным полем из статуса). */
    suspend fun resolveSubscriptionUrl(): String? {
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
            if (!forceRefresh) {
                loadCached()?.let { return@withContext it }
            }
            try {
                fetchFromNetwork()
            } catch (e: Exception) {
                // сеть/сервер недоступны — работаем с сохранённой копией подписки
                loadCached() ?: throw e
            }
        }

    private suspend fun loadCached(): Pair<List<ProxyProfile>, SubscriptionUserInfo?>? {
        val cached = prefs.serversRawFlow.first()
        if (cached.isNullOrBlank()) return null
        val profiles = LinkParser.parseSubscriptionContent(cached)
        if (profiles.isEmpty()) return null
        val storedInfo = prefs.subUserInfoFlow.first()?.let {
            try {
                client.json.decodeFromString(SubscriptionUserInfo.serializer(), it)
            } catch (_: Exception) {
                null
            }
        }
        return profiles to storedInfo
    }

    private suspend fun fetchFromNetwork(): Pair<List<ProxyProfile>, SubscriptionUserInfo?> {
        val url = resolveSubscriptionUrl()
            ?: throw IllegalStateException("Нет активной подписки")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "v2rayNG/1.10.7")
            .header("Accept", "text/plain")
            .build()
        return client.plainOkHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Подписка недоступна (HTTP ${response.code})")
            }
            val body = response.body?.string().orEmpty()
            val userInfo = response.header("subscription-userinfo")?.let(::parseUserInfo)
            val profiles = LinkParser.parseSubscriptionContent(body)
            if (profiles.isNotEmpty()) prefs.setServersRaw(body)
            if (userInfo != null) {
                prefs.setSubUserInfo(
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
}
