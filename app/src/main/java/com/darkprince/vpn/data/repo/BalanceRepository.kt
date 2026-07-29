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

    /** Активация промокода. Возвращает Pair(успех, сообщение). */
    suspend fun activatePromocode(code: String): Pair<Boolean, String> = try {
        val response = api.activatePromocode(
            com.darkprince.vpn.data.api.dto.PromoActivateRequest(code.trim())
        )
        val ok = response.success ?: true
        val message = listOfNotNull(response.message, response.bonusDescription)
            .joinToString(" ")
            .ifBlank { if (ok) "Промокод активирован!" else "Не удалось активировать промокод" }
        ok to message
    } catch (e: Exception) {
        false to e.userMessage()
    }
}
