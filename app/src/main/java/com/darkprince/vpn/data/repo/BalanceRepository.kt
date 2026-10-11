package com.darkprince.vpn.data.repo

import com.darkprince.vpn.data.api.ApiClient
import com.darkprince.vpn.data.api.dto.BalanceResponse
import com.darkprince.vpn.data.api.dto.PaymentMethodDto
import com.darkprince.vpn.data.api.dto.TopupRequest
import com.darkprince.vpn.data.api.dto.TopupResponse
import com.darkprince.vpn.data.api.dto.TransactionDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

class BalanceRepository(private val client: ApiClient) {
    private val api get() = client.api

    suspend fun balance(): BalanceResponse = api.balance()

    /** Способы оплаты; ответ может быть массивом или объектом со списком. */
    suspend fun paymentMethods(): List<PaymentMethodDto> {
        val root: JsonElement = api.paymentMethods()
        val array: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> root["methods"]?.jsonArray
                ?: root["items"]?.jsonArray
                ?: root["payment_methods"]?.jsonArray
                ?: return emptyList()
            else -> return emptyList()
        }
        return array.mapNotNull { element ->
            try {
                client.json.decodeFromJsonElement(PaymentMethodDto.serializer(), element)
            } catch (_: Exception) {
                null
            }
        }.filter { it.effectiveEnabled && it.effectiveId.isNotBlank() }
    }

    suspend fun createTopup(amountKopeks: Long, method: String, option: String? = null): TopupResponse =
        api.topup(TopupRequest(amountKopeks, method, option))

    suspend fun transactions(page: Int = 1): List<TransactionDto> =
        api.transactions(page = page).list

    suspend fun checkPending(method: String, paymentId: String): Boolean = try {
        api.checkPendingPayment(method, paymentId)
        true
    } catch (_: Exception) {
        false
    }

    suspend fun referralInfo() = api.referralInfo()

    /**
     * Активация промокода. Код на дни при нескольких подписках бот не
     * применяет сам, а просит выбрать подписку — тогда отдаём список.
     */
    suspend fun activatePromocode(code: String, subscriptionId: Long? = null): PromoResult = try {
        val response = api.activatePromocode(
            com.darkprince.vpn.data.api.dto.PromoActivateRequest(code.trim(), subscriptionId)
        )
        when {
            response.error == "select_subscription" && response.eligibleSubscriptions.isNotEmpty() ->
                PromoResult.ChooseSubscription(code.trim(), response.eligibleSubscriptions)
            response.success == false -> PromoResult.Failed("Не удалось активировать промокод")
            // message у кабинета английский — показываем, что именно дал код
            else -> PromoResult.Ok(
                response.bonusDescription?.takeIf { it.isNotBlank() } ?: "Промокод активирован!"
            )
        }
    } catch (e: Exception) {
        PromoResult.Failed(e.cabinetMessage(PROMO_ERRORS))
    }
}

sealed interface PromoResult {
    data class Ok(val message: String) : PromoResult
    data class Failed(val message: String) : PromoResult
    data class ChooseSubscription(
        val code: String,
        val options: List<com.darkprince.vpn.data.api.dto.PromoSubscriptionOption>,
    ) : PromoResult
}

/**
 * Коды отказа кабинета. Ищем код в кавычках: иначе «used» совпал бы
 * с already_used_by_user, а «not_found» — с subscription_not_found.
 */
private val PROMO_ERRORS = listOf(
    "\"already_used_by_user\"" to "Вы уже активировали этот промокод.",
    "\"not_found\"" to "Такого промокода нет. Проверьте, правильно ли он введён.",
    "\"expired\"" to "Срок действия промокода закончился.",
    "\"not_yet_valid\"" to "Промокод ещё не начал действовать.",
    "\"inactive\"" to "Промокод отключён.",
    "\"used\"" to "Промокод уже использовали максимальное число раз.",
    "\"active_discount_exists\"" to "У вас уже есть активная скидка — сначала используйте её.",
    "\"no_subscription_for_days\"" to "Этот промокод продлевает подписку, а у вас её пока нет. Сначала оформите подписку или пробный период.",
    "\"subscription_not_found\"" to "Подписка не найдена. Обновите экран.",
    "\"not_first_purchase\"" to "Промокод действует только на первую покупку.",
    "\"daily_limit\"" to "Слишком много промокодов за сегодня. Попробуйте завтра.",
    "\"trial_subscription_exists\"" to "Пробный промокод не подходит: у вас уже есть подписка.",
    "\"traffic_not_applicable\"" to "Промокод добавляет трафик, а у вашей подписки он и так безлимитный.",
)
