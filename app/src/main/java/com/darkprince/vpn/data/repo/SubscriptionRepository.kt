package com.darkprince.vpn.data.repo

import com.darkprince.vpn.core.model.ProxyProfile
import com.darkprince.vpn.core.parser.LinkParser
import com.darkprince.vpn.data.api.ApiClient
import com.darkprince.vpn.data.api.dto.AutopayRequest
import com.darkprince.vpn.data.api.dto.DeviceDto
import com.darkprince.vpn.data.api.dto.RecurrentStatusDto
import com.darkprince.vpn.data.api.dto.TariffSwitchPreviewDto
import com.darkprince.vpn.data.api.dto.TariffSwitchRequest
import com.darkprince.vpn.data.api.dto.DevicesPurchaseRequest
import com.darkprince.vpn.data.api.dto.RenameDeviceRequest
import com.darkprince.vpn.data.api.dto.PurchaseTariffRequest
import com.darkprince.vpn.data.api.dto.ReduceDevicesRequest
import com.darkprince.vpn.data.api.dto.RenewRequest
import com.darkprince.vpn.data.api.dto.SubscriptionListItem
import com.darkprince.vpn.data.api.dto.SubscriptionStatusResponse
import com.darkprince.vpn.data.api.dto.TrafficPurchaseRequest
import com.darkprince.vpn.data.prefs.AppPrefs
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
    /** Админ отметил тариф как выгодный — на карточке значок. */
    val highlighted: Boolean = false,
)

/**
 * Срок и цена. [daily] — суточный тариф: у него нет сроков, только цена за
 * день, а срок в один день сервер ставит при покупке сам.
 */
data class PeriodPrice(
    val days: Int,
    val priceKopeks: Long,
    val daily: Boolean = false,
    /** Самый выгодный срок тарифа — по отметке админа. */
    val highlighted: Boolean = false,
)

private fun JsonObject.flag(key: String): Boolean =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull == true

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

    /**
     * Список подписок пользователя. Пустой — подписок правда нет; null —
     * спросить не удалось.
     *
     * Разница принципиальна: раньше сетевая заминка возвращала пустой список,
     * и подписки пропадали с экрана у человека, у которого они есть. Теперь
     * при неудаче экран оставляет прежний список.
     */
    suspend fun subscriptions(): List<SubscriptionListItem>? {
        if (!isLoggedIn) return emptyList()
        return try {
            api.subscriptions().subscriptions
        } catch (_: Exception) {
            null
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
        for (sub in subscriptions().orEmpty()) {
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
            val fromList = subscriptions().orEmpty().firstOrNull { it.id == selectedId }?.subscriptionUrl
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

    /**
     * Истекла ли выбранная подписка — по свежим данным, без кэша.
     *
     * null — узнать не удалось (нет сети, кабинет недоступен): тогда не
     * мешаем подключаться. Аккаунту отвечает кабинет, гостю — заголовок
     * подписки.
     */
    suspend fun freshlyExpired(): Boolean? = withContext(Dispatchers.IO) {
        if (isLoggedIn) {
            val subs = subscriptions() ?: return@withContext null
            val selected = prefs.selectedSubscriptionFlow.first()
            val sub = subs.firstOrNull { it.id == selected }
                ?: subs.singleOrNull()
                ?: return@withContext null
            sub.status.equals("expired", ignoreCase = true)
        } else {
            val url = prefs.cachedGuestSubUrl ?: return@withContext null
            val expire = runCatching { downloadSubscription(null, url).second?.expireUnix }.getOrNull()
                ?: return@withContext null
            expire in 1 until System.currentTimeMillis() / 1000
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
            // Плитка и автоподключение поднимают VPN по сохранённому узлу. Без
            // этого после сброса ссылки или правки узла в панели они
            // подключались бы со старыми ключами, пока не откроешь приложение.
            if (profiles.isNotEmpty() && subId == prefs.selectedSubscriptionFlow.first()) {
                com.darkprince.vpn.vpn.XVpnService.refreshSavedProfile(ServiceLocator.appContext, profiles)
            }
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
                // Суточный тариф приходит без сроков, с ценой за день. Раньше
                // такие тарифы отбрасывались вместе с действительно пустыми, и
                // суточного в магазине не было вовсе.
                val periods = parsePeriods(obj).ifEmpty {
                    val daily = (obj["is_daily"] as? kotlinx.serialization.json.JsonPrimitive)
                        ?.booleanOrNull == true
                    val perDay = obj["daily_price_kopeks"]?.jsonPrimitive?.longOrNull ?: 0L
                    if (daily && perDay > 0) listOf(PeriodPrice(1, perDay, daily = true)) else emptyList()
                }
                if (periods.isEmpty()) continue
                offers.add(
                    TariffOffer(
                        id = id,
                        name = name,
                        description = obj["description"]?.jsonPrimitive?.contentOrNull,
                        periods = periods,
                        trafficLimitGb = obj["traffic_limit_gb"]?.jsonPrimitive?.intOrNull,
                        deviceLimit = obj["device_limit"]?.jsonPrimitive?.intOrNull,
                        highlighted = obj.flag("is_highlighted"),
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
                result.add(PeriodPrice(days, price, highlighted = po.flag("is_highlighted")))
            }
        }
        return result.sortedBy { it.days }
    }

    /**
     * Пауза суточного тарифа: на паузе списаний нет и VPN не работает.
     * Снятие с паузы сразу списывает сутки — на это нужны деньги на балансе.
     * Успех — новое состояние (true — на паузе).
     */
    suspend fun toggleDailyPause(subscriptionId: Long): Result<Boolean> = try {
        val body = api.toggleDailyPause(subscriptionId)
        Result.success((body["is_paused"] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull == true)
    } catch (e: retrofit2.HttpException) {
        val message = if (e.code() == 402) {
            val detail = runCatching {
                val raw = e.response()?.errorBody()?.string().orEmpty()
                kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject["detail"]?.jsonObject
            }.getOrNull()
            val required = detail?.get("required")?.jsonPrimitive?.longOrNull
            val balance = detail?.get("balance")?.jsonPrimitive?.longOrNull
            if (required != null && balance != null && required > balance) {
                "Чтобы возобновить, на балансе нужно ${formatRubles(required)} — не хватает ${formatRubles(required - balance)}"
            } else {
                "Чтобы возобновить, пополните баланс: сутки списываются сразу"
            }
        } else {
            e.cabinetMessage(
                listOf(
                    "only available for daily" to "Пауза есть только у суточного тарифа.",
                    "has no tariff" to "У подписки нет тарифа.",
                )
            )
        }
        Result.failure(IllegalStateException(message))
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.userMessage()))
    }

    /** То же, что [purchaseTariff], но нехватку денег отличает от прочих ошибок. */
    suspend fun purchaseTariffChecked(tariffId: Long, periodDays: Int): PurchaseResult =
        attemptPurchase { api.purchaseTariff(PurchaseTariffRequest(tariffId, periodDays)) }

    /** То же, что [renew], но нехватку денег отличает от прочих ошибок. */
    suspend fun renewChecked(periodDays: Int, subscriptionId: Long?): PurchaseResult =
        attemptPurchase { api.renew(RenewRequest(periodDays), subscriptionId) }

    suspend fun purchaseTariff(tariffId: Long, periodDays: Int): String? = try {
        api.purchaseTariff(PurchaseTariffRequest(tariffId, periodDays))
        null
    } catch (e: Exception) {
        e.userMessage()
    }

    /** Варианты продления: список {period_days, price_kopeks}. */
    suspend fun renewalOptions(subscriptionId: Long? = null): List<PeriodPrice> {
        val root = api.renewalOptions(subscriptionId)
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
            PeriodPrice(days, price, highlighted = obj.flag("is_highlighted"))
        }.sortedBy { it.days }
    }

    suspend fun renew(periodDays: Int, subscriptionId: Long? = null): String? = try {
        api.renew(RenewRequest(periodDays), subscriptionId)
        null
    } catch (e: Exception) {
        e.userMessage()
    }

    // --- Автопродление ---

    /**
     * Автопродление подписки: с баланса и через СБП.
     *
     * За сколько дней до конца списывать, в списке подписок не приходит —
     * спрашиваем саму подписку. СБП-автоплатёж бывает выключен в боте
     * целиком: тогда [AutopayInfo.recurrent] пустой и блок не показываем.
     */
    suspend fun autopayInfo(subscriptionId: Long): AutopayInfo = kotlinx.coroutines.coroutineScope {
        val settings = async { runCatching { api.subscriptionAutopay(subscriptionId) }.getOrNull() }
        val recurrent = async { recurrentStatus(subscriptionId) }
        val s = settings.await()
        AutopayInfo(
            enabled = s?.enabled,
            daysBefore = s?.daysBefore ?: 3,
            recurrent = recurrent.await(),
        )
    }

    /** null — СБП-автоплатёж в боте выключен или узнать не удалось. */
    private suspend fun recurrentStatus(subscriptionId: Long): RecurrentStatusDto? = try {
        api.recurrentStatus(subscriptionId)
    } catch (_: Exception) {
        null
    }

    suspend fun setAutopay(subscriptionId: Long, enabled: Boolean, daysBefore: Int?): String? = try {
        api.updateAutopay(AutopayRequest(enabled, daysBefore), subscriptionId)
        null
    } catch (e: Exception) {
        e.cabinetMessage(AUTOPAY_ERRORS)
    }

    /** Ссылка на страницу банка, где человек подтверждает автоплатёж. */
    suspend fun enableRecurrent(subscriptionId: Long): Result<String> = try {
        val url = api.enableRecurrent(subscriptionId).redirectUrl
        if (url.isNullOrBlank()) Result.failure(IllegalStateException("Касса не вернула ссылку на подтверждение"))
        else Result.success(url)
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.cabinetMessage(AUTOPAY_ERRORS)))
    }

    suspend fun cancelRecurrent(subscriptionId: Long): String? = try {
        api.cancelRecurrent(subscriptionId)
        null
    } catch (e: Exception) {
        e.cabinetMessage(AUTOPAY_ERRORS)
    }

    // --- Смена тарифа ---

    /** Сколько будет стоить переход: доплата за оставшиеся дни или бесплатно. */
    suspend fun switchPreview(subscriptionId: Long, tariffId: Long, periodDays: Int): Result<TariffSwitchPreviewDto> = try {
        Result.success(api.tariffSwitchPreview(TariffSwitchRequest(tariffId, periodDays), subscriptionId))
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.cabinetMessage(SWITCH_ERRORS)))
    }

    /** Нехватку денег отличаем от прочих ошибок — как при покупке. */
    suspend fun switchTariff(subscriptionId: Long, tariffId: Long, periodDays: Int): PurchaseResult = try {
        api.tariffSwitch(TariffSwitchRequest(tariffId, periodDays), subscriptionId)
        PurchaseResult.Ok
    } catch (e: retrofit2.HttpException) {
        if (e.code() == 402) PurchaseResult.NotEnough(e.missingAmountKopeks())
        else PurchaseResult.Failed(e.cabinetMessage(SWITCH_ERRORS))
    } catch (e: Exception) {
        PurchaseResult.Failed(e.userMessage())
    }

    // --- Перевыпуск ссылки ---

    /**
     * Новая ссылка подписки вместо старой. Старая перестаёт работать везде,
     * куда её добавили руками, — поэтому сохранённые серверы этой подписки
     * больше не годятся и скачиваются заново.
     */
    suspend fun revoke(subscriptionId: Long): String? = try {
        api.revokeSubscription(subscriptionId)
        prefs.setServersRawFor(subscriptionId, null)
        null
    } catch (e: Exception) {
        val http = e as? retrofit2.HttpException
        if (http?.code() == 429) {
            val wait = runCatching { http.response()?.errorBody()?.string() }.getOrNull()?.secondsToWait()
            val minutes = ((wait ?: 60) + 59) / 60
            "Ссылку уже сбрасывали недавно. Повторить можно через ${minutesText(minutes)}."
        } else {
            e.cabinetMessage(
                listOf(
                    "reissue is not available" to "Сброс ссылки отключён в боте.",
                    "Only active subscriptions" to "Сбросить можно только у действующей подписки.",
                    "Failed to reissue" to "Панель не смогла выпустить новую ссылку. Попробуйте позже.",
                )
            )
        }
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
        // почему докупка недоступна — иначе кнопка просто исчезает и
        // непонятно, это ограничение тарифа или сбой запроса
        var purchaseNote: String? = null
        try {
            val price = api.devicePrice(subscriptionId = subscriptionId)
            purchaseAvailable = price.boolOf("available") ?: true
            pricePerDevice = price.longOf("price_per_device_kopeks")
            maxLimit = price.intOf("max_device_limit")
            if (limit == null) limit = price.intOf("current_device_limit")
            if (!purchaseAvailable || pricePerDevice == null) {
                purchaseNote = price.textOf("message")
                    ?: price.textOf("reason")
                    ?: price.textOf("detail")
            }
        } catch (e: Exception) {
            purchaseNote = "Не удалось узнать цену: ${e.userMessage()}"
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
            purchaseNote = purchaseNote,
        )
    }

    /** Подключённые устройства подписки. */
    /** null — спросить не удалось: это не то же самое, что «устройств нет». */
    suspend fun devicesList(subscriptionId: Long? = null): List<DeviceDto>? = try {
        api.devicesList(subscriptionId).devices
    } catch (_: Exception) {
        null
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
    suspend fun trafficPackages(subscriptionId: Long? = null): List<TrafficPackage> {
        val root = api.trafficPackages(subscriptionId)
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

    suspend fun buyTraffic(gb: Int, subscriptionId: Long? = null): String? = try {
        api.purchaseTraffic(TrafficPurchaseRequest(gb), subscriptionId)
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
    /** Пояснение, почему докупка недоступна. */
    val purchaseNote: String? = null,
)

data class TrafficPackage(val gb: Int, val priceKopeks: Long)

/** Автопродление подписки. [enabled] null — кабинет не ответил. */
data class AutopayInfo(
    val enabled: Boolean?,
    val daysBefore: Int,
    val recurrent: RecurrentStatusDto?,
) {
    /** СБП-автоплатёж подключён (или ждёт подтверждения в банке). */
    val recurrentOn: Boolean
        get() = recurrent != null && recurrent.status.lowercase() !in setOf("none", "cancelled", "canceled", "failed", "expired")
}

private val AUTOPAY_ERRORS = listOf(
    "classic subscriptions" to "Автопродление работает только для тарифов. Купите тариф.",
    "trial subscriptions" to "У пробной подписки автопродления нет.",
    "Trial subscriptions" to "У пробной подписки автопродления нет.",
    "daily subscriptions" to "Суточный тариф и так продлевается сам каждый день.",
    "recurrent disabled" to "Автоплатёж через СБП отключён в боте.",
    "has no tariff" to "У подписки нет тарифа — автоплатёж не подключить.",
    "Could not create Platega" to "Касса не смогла оформить автоплатёж. Попробуйте позже.",
    "No subscription found" to "Подписка не найдена. Обновите экран.",
)

private val SWITCH_ERRORS = listOf(
    "subscription_expired" to "Подписка истекла — сменить её нельзя, купите нужный тариф.",
    "trial_cannot_switch" to "Пробную подписку сменить нельзя — купите тариф.",
    "free_tariff_cannot_switch" to "С бесплатного тарифа не переходят — купите нужный.",
    "subscription_not_active" to "Подписка не активна — сменить тариф нельзя.",
    "Already on this tariff" to "Вы уже на этом тарифе.",
    "already have an active subscription for the target tariff" to "У вас уже есть подписка на этот тариф.",
    "promo group" to "Этот тариф вам недоступен.",
    "Tariff not available" to "Этот тариф вам недоступен.",
    "not found or inactive" to "Тариф больше не продаётся.",
    "Tariffs mode is not enabled" to "Смена тарифа отключена в боте.",
    "No active subscription with tariff" to "У подписки нет тарифа — сменить нельзя.",
)

private fun JsonObject.intOf(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
private fun JsonObject.textOf(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
private fun JsonObject.longOf(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
private fun JsonObject.boolOf(key: String): Boolean? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull

private fun formatRubles(kopeks: Long): String =
    if (kopeks % 100 == 0L) "${kopeks / 100} ₽" else String.format(java.util.Locale.US, "%.2f ₽", kopeks / 100.0)
