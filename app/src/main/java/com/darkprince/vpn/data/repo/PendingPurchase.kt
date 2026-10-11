package com.darkprince.vpn.data.repo

import com.darkprince.vpn.data.api.dto.SubscriptionListItem
import com.darkprince.vpn.data.prefs.AppPrefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import retrofit2.HttpException

/**
 * Покупка, на которую не хватило денег.
 *
 * Человек выбрал подписку, нажал «Купить» — а на балансе меньше нужного. Мы
 * запоминаем выбор и ведём его на пополнение с недостающей суммой. Как
 * только деньги зачислятся, оплачиваем именно эту подписку — и только её:
 * в любом другом случае пополнение остаётся просто пополнением.
 *
 * Хранится на диске, а не в памяти: оплата идёт в браузере, и за это время
 * система вполне может выгрузить приложение.
 */
@Serializable
data class PendingPurchase(
    /** "renew" — продлить свою подписку, "buy" — купить тариф. */
    val kind: String,
    val subscriptionId: Long? = null,
    val tariffId: Long? = null,
    val periodDays: Int,
    val priceKopeks: Long,
    val missingKopeks: Long,
    /** Что показать человеку: «Стандарт · 30 дней». */
    val title: String,
    /**
     * Сроки подписок на момент нехватки. Бот умеет и сам оплатить корзину
     * после пополнения (если это включено в его настройках). Если срок
     * успел измениться — значит, он это уже сделал, и платить второй раз
     * нельзя.
     */
    val endDatesBefore: Map<Long, String?> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isStale: Boolean get() = System.currentTimeMillis() - createdAt > TTL_MS

    /** Видно ли по свежему списку подписок, что покупка уже состоялась. */
    fun alreadyDone(subscriptions: List<SubscriptionListItem>): Boolean {
        val relevant = when (kind) {
            "renew" -> subscriptions.filter { it.id == subscriptionId }
            else -> subscriptions.filter { it.tariffId == tariffId }
        }
        return relevant.any { sub ->
            !endDatesBefore.containsKey(sub.id) || endDatesBefore[sub.id] != sub.endDate
        }
    }

    companion object {
        /** Через три часа это уже не «докинуть и купить», а забытая затея. */
        const val TTL_MS = 3 * 60 * 60 * 1000L
    }
}

class PendingPurchaseStore(private val prefs: AppPrefs) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): PendingPurchase? {
        val raw = prefs.pendingPurchaseJson() ?: return null
        val pending = runCatching { json.decodeFromString(PendingPurchase.serializer(), raw) }.getOrNull()
        if (pending == null || pending.isStale) {
            clear()
            return null
        }
        return pending
    }

    suspend fun save(pending: PendingPurchase) {
        prefs.setPendingPurchaseJson(json.encodeToString(PendingPurchase.serializer(), pending))
    }

    suspend fun clear() = prefs.setPendingPurchaseJson(null)
}

/** Чем закончилась попытка купить или продлить. */
sealed interface PurchaseResult {
    data object Ok : PurchaseResult
    /** Не хватило денег; сколько именно — если бот сказал. */
    data class NotEnough(val missingKopeks: Long?) : PurchaseResult
    data class Failed(val message: String) : PurchaseResult
}

/**
 * Бот отвечает на нехватку денег кодом 402 и телом
 * `{"detail": {"code": "insufficient_funds", "missing_amount": <копейки>}}`.
 */
internal suspend fun attemptPurchase(block: suspend () -> Unit): PurchaseResult = try {
    block()
    PurchaseResult.Ok
} catch (e: HttpException) {
    if (e.code() == 402) {
        PurchaseResult.NotEnough(e.missingAmountKopeks())
    } else {
        PurchaseResult.Failed(e.userMessage())
    }
} catch (e: Exception) {
    PurchaseResult.Failed(e.userMessage())
}

private fun HttpException.missingAmountKopeks(): Long? = runCatching {
    val body = response()?.errorBody()?.string() ?: return null
    val detail = Json.parseToJsonElement(body) as? JsonObject ?: return null
    val inner = detail["detail"] as? JsonObject ?: return null
    inner["missing_amount"]?.jsonPrimitive?.longOrNull
}.getOrNull()
