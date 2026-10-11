package com.darkprince.vpn.data.repo

import com.darkprince.vpn.data.api.ApiClient
import com.darkprince.vpn.data.api.dto.GiftActivateRequest
import com.darkprince.vpn.data.api.dto.GiftActivateResponse
import com.darkprince.vpn.data.api.dto.GiftConfigDto
import com.darkprince.vpn.data.api.dto.GiftPurchaseRequest
import com.darkprince.vpn.data.api.dto.GiftPurchaseResponse
import com.darkprince.vpn.data.api.dto.GiftPurchaseStatusDto
import com.darkprince.vpn.data.api.dto.ReceivedGiftDto
import com.darkprince.vpn.data.api.dto.SentGiftDto

/**
 * Подарочные подписки.
 *
 * Купленный подарок — это код: кабинет показывает первые двенадцать знаков
 * токена, и по ним же его активирует. Ссылку на бота сделать нельзя — в
 * deep link бот требует не меньше 48 знаков, а их кабинет не отдаёт. Поэтому
 * делимся кодом «GIFT-…», а друг вводит его в приложении или на сайте.
 */
class GiftRepository(private val client: ApiClient) {
    private val api get() = client.api

    suspend fun config(): GiftConfigDto = api.giftConfig()

    suspend fun sent(): List<SentGiftDto> = runCatching { api.giftsSent() }.getOrDefault(emptyList())

    suspend fun received(): List<ReceivedGiftDto> = runCatching { api.giftsReceived() }.getOrDefault(emptyList())

    suspend fun purchase(request: GiftPurchaseRequest): Result<GiftPurchaseResponse> = try {
        Result.success(api.giftPurchase(request))
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.cabinetMessage(GIFT_ERRORS)))
    }

    suspend fun status(token: String): GiftPurchaseStatusDto? =
        runCatching { api.giftPurchaseStatus(token) }.getOrNull()

    suspend fun activate(code: String): Result<GiftActivateResponse> = try {
        Result.success(api.giftActivate(GiftActivateRequest(code.trim())))
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.cabinetMessage(GIFT_ERRORS)))
    }

    companion object {
        /** Код, которым делятся: как его показывает кабинет. */
        fun codeOf(token: String): String = "GIFT-$token"
    }
}

private val GIFT_ERRORS = listOf(
    "Gift feature is not enabled" to "Подарки отключены в боте.",
    "Insufficient balance" to "На балансе не хватает денег — выберите оплату картой или СБП.",
    "restricted for this account" to "Покупки для этого аккаунта ограничены.",
    "Invalid email format" to "Проверьте почту получателя.",
    "Invalid Telegram username" to "Проверьте ник в Telegram: латиница, цифры и _, от 5 символов.",
    "Cannot gift to yourself" to "Себе подарок не отправить.",
    "Cannot activate your own gift" to "Свой подарок активировать нельзя — отправьте код другу.",
    "Price is not configured" to "Для этого срока нет цены. Выберите другой.",
    "Tariff not found" to "Этот тариф больше не дарят.",
    "Payment provider" to "Касса не ответила. Попробуйте позже или другим способом.",
    "Code too short" to "Код слишком короткий. Скопируйте его целиком.",
    "Gift not found" to "Подарок не найден. Проверьте код.",
    "cannot be activated" to "Этот подарок уже активирован или срок его действия вышел.",
    "Too many requests" to "Слишком часто. Подождите минуту.",
)
