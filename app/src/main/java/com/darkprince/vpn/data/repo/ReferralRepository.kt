package com.darkprince.vpn.data.repo

import com.darkprince.vpn.data.api.ApiClient
import com.darkprince.vpn.data.api.dto.ReferralEarningsDto
import com.darkprince.vpn.data.api.dto.ReferralInfoResponse
import com.darkprince.vpn.data.api.dto.ReferralListDto
import com.darkprince.vpn.data.api.dto.ReferralTermsDto
import com.darkprince.vpn.data.api.dto.WithdrawalBalanceDto
import com.darkprince.vpn.data.api.dto.WithdrawalCreateRequest
import com.darkprince.vpn.data.api.dto.WithdrawalDto

/** Реферальная программа целиком: ссылка, условия, приглашённые, начисления и вывод. */
class ReferralRepository(private val client: ApiClient) {
    private val api get() = client.api

    suspend fun info(): ReferralInfoResponse = api.referralInfo()

    suspend fun terms(): ReferralTermsDto? = runCatching { api.referralTerms() }.getOrNull()

    suspend fun invited(): ReferralListDto? = runCatching { api.referralList() }.getOrNull()

    suspend fun earnings(): ReferralEarningsDto? = runCatching { api.referralEarnings() }.getOrNull()

    /** null — вывода в боте нет или он недоступен этому аккаунту. */
    suspend fun withdrawal(): WithdrawalBalanceDto? = runCatching { api.withdrawalBalance() }.getOrNull()

    suspend fun withdrawals(): List<WithdrawalDto> =
        runCatching { api.withdrawalHistory().items }.getOrDefault(emptyList())

    suspend fun requestWithdrawal(amountKopeks: Long, details: String): String? = try {
        api.createWithdrawal(WithdrawalCreateRequest(amountKopeks, details.trim()))
        null
    } catch (e: Exception) {
        e.cabinetMessage(listOf("Too many requests" to "Слишком часто. Подождите минуту."))
    }

    suspend fun cancelWithdrawal(id: Long): String? = try {
        api.cancelWithdrawal(id)
        null
    } catch (e: Exception) {
        e.cabinetMessage()
    }
}
