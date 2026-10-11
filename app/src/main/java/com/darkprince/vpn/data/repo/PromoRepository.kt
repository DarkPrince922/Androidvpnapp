package com.darkprince.vpn.data.repo

import com.darkprince.vpn.data.api.ApiClient
import com.darkprince.vpn.data.api.dto.ActiveDiscountDto
import com.darkprince.vpn.data.api.dto.ClaimOfferRequest
import com.darkprince.vpn.data.api.dto.ClaimOfferResponse
import com.darkprince.vpn.data.api.dto.GroupDiscountsDto
import com.darkprince.vpn.data.api.dto.LoyaltyTiersDto
import com.darkprince.vpn.data.api.dto.PromoOfferDto

/** Персональные предложения, действующая скидка и уровни лояльности. */
class PromoRepository(private val client: ApiClient) {
    private val api get() = client.api

    /** Предложения, которые ещё можно забрать. */
    suspend fun offers(): List<PromoOfferDto> =
        runCatching { api.promoOffers() }.getOrDefault(emptyList())
            .filter { it.isActive && !it.isClaimed }

    suspend fun activeDiscount(): ActiveDiscountDto? =
        runCatching { api.activeDiscount() }.getOrNull()?.takeIf { it.isActive && it.discountPercent > 0 }

    suspend fun groupDiscounts(): GroupDiscountsDto? = runCatching { api.groupDiscounts() }.getOrNull()

    suspend fun loyalty(): LoyaltyTiersDto? = runCatching { api.loyaltyTiers() }.getOrNull()

    suspend fun claim(offerId: Long): Result<ClaimOfferResponse> = try {
        Result.success(api.claimOffer(ClaimOfferRequest(offerId)))
    } catch (e: Exception) {
        Result.failure(
            IllegalStateException(
                e.cabinetMessage(
                    listOf(
                        "already been claimed" to "Это предложение уже активировано.",
                        "has expired" to "Срок предложения вышел.",
                        "Offer not found" to "Предложение не найдено. Обновите экран.",
                        "Active subscription required" to "Нужна действующая подписка.",
                        "already connected" to "Эти серверы у вас уже есть.",
                        "Failed to connect servers" to "Не получилось подключить серверы. Попробуйте позже.",
                    )
                )
            )
        )
    }
}
