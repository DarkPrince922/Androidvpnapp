package com.darkprince.vpn.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Автопродление ----------

@Serializable
data class AutopayRequest(
    val enabled: Boolean,
    @SerialName("days_before") val daysBefore: Int? = null,
)

@Serializable
data class AutopayResponse(
    @SerialName("autopay_enabled") val enabled: Boolean = false,
    @SerialName("autopay_days_before") val daysBefore: Int? = null,
)

/** Настройки подписки, которых нет в списке: за сколько дней списывать. */
@Serializable
data class SubscriptionAutopayDto(
    @SerialName("autopay_enabled") val enabled: Boolean? = null,
    @SerialName("autopay_days_before") val daysBefore: Int? = null,
)

/** Автоплатёж через СБП (Platega): банк сам списывает за следующий период. */
@Serializable
data class RecurrentStatusDto(
    /** none — не подключён; active/pending/… — как отдаёт касса. */
    val status: String = "none",
    val interval: String? = null,
    @SerialName("amount_kopeks") val amountKopeks: Long? = null,
    @SerialName("next_charge_at") val nextChargeAt: String? = null,
    @SerialName("redirect_url") val redirectUrl: String? = null,
)

@Serializable
data class RecurrentEnableResponse(
    val status: String? = null,
    @SerialName("redirect_url") val redirectUrl: String? = null,
)

// ---------- Смена тарифа ----------

@Serializable
data class TariffSwitchRequest(
    @SerialName("tariff_id") val tariffId: Long,
    @SerialName("period_days") val periodDays: Int,
)

@Serializable
data class TariffSwitchPreviewDto(
    @SerialName("can_switch") val canSwitch: Boolean = false,
    @SerialName("current_tariff_name") val currentTariffName: String? = null,
    @SerialName("new_tariff_name") val newTariffName: String? = null,
    @SerialName("remaining_days") val remainingDays: Int = 0,
    @SerialName("upgrade_cost_kopeks") val upgradeCostKopeks: Long = 0,
    @SerialName("balance_kopeks") val balanceKopeks: Long = 0,
    @SerialName("missing_amount_kopeks") val missingAmountKopeks: Long = 0,
    @SerialName("is_upgrade") val isUpgrade: Boolean = false,
    @SerialName("discount_percent") val discountPercent: Int? = null,
)

// ---------- Перевыпуск ссылки ----------

@Serializable
data class RevokeResponse(
    val success: Boolean = false,
    @SerialName("cooldown_seconds") val cooldownSeconds: Int? = null,
)

// ---------- Подарки ----------

@Serializable
data class GiftPeriodDto(
    val days: Int,
    @SerialName("price_kopeks") val priceKopeks: Long,
    @SerialName("original_price_kopeks") val originalPriceKopeks: Long? = null,
    @SerialName("discount_percent") val discountPercent: Int? = null,
)

@Serializable
data class GiftTariffDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    @SerialName("traffic_limit_gb") val trafficLimitGb: Int = 0,
    @SerialName("device_limit") val deviceLimit: Int = 1,
    val periods: List<GiftPeriodDto> = emptyList(),
)

@Serializable
data class GiftSubOptionDto(val id: String, val name: String)

@Serializable
data class GiftPaymentMethodDto(
    @SerialName("method_id") val methodId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("min_amount_kopeks") val minAmountKopeks: Long? = null,
    @SerialName("max_amount_kopeks") val maxAmountKopeks: Long? = null,
    @SerialName("sub_options") val subOptions: List<GiftSubOptionDto>? = null,
)

@Serializable
data class GiftConfigDto(
    @SerialName("is_enabled") val isEnabled: Boolean = false,
    val tariffs: List<GiftTariffDto> = emptyList(),
    @SerialName("payment_methods") val paymentMethods: List<GiftPaymentMethodDto> = emptyList(),
    @SerialName("balance_kopeks") val balanceKopeks: Long = 0,
)

@Serializable
data class GiftPurchaseRequest(
    @SerialName("tariff_id") val tariffId: Long,
    @SerialName("period_days") val periodDays: Int,
    @SerialName("recipient_type") val recipientType: String? = null,
    @SerialName("recipient_value") val recipientValue: String? = null,
    @SerialName("gift_message") val giftMessage: String? = null,
    /** balance — с баланса, gateway — через кассу. */
    @SerialName("payment_mode") val paymentMode: String,
    @SerialName("payment_method") val paymentMethod: String? = null,
)

@Serializable
data class GiftPurchaseResponse(
    val status: String,
    @SerialName("purchase_token") val purchaseToken: String,
    @SerialName("payment_url") val paymentUrl: String? = null,
    val warning: String? = null,
)

@Serializable
data class GiftPurchaseStatusDto(
    val status: String,
    @SerialName("is_claimable") val isClaimable: Boolean = false,
    @SerialName("purchase_token") val purchaseToken: String? = null,
    @SerialName("tariff_name") val tariffName: String? = null,
    @SerialName("period_days") val periodDays: Int? = null,
)

@Serializable
data class SentGiftDto(
    val token: String,
    @SerialName("tariff_name") val tariffName: String? = null,
    @SerialName("period_days") val periodDays: Int = 0,
    val status: String,
    @SerialName("gift_recipient_value") val recipient: String? = null,
    @SerialName("gift_message") val message: String? = null,
    @SerialName("activated_by_username") val activatedBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ReceivedGiftDto(
    val token: String,
    @SerialName("tariff_name") val tariffName: String? = null,
    @SerialName("period_days") val periodDays: Int = 0,
    val status: String,
    @SerialName("sender_display") val sender: String? = null,
    @SerialName("gift_message") val message: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class GiftActivateRequest(val code: String)

@Serializable
data class GiftActivateResponse(
    val status: String? = null,
    @SerialName("tariff_name") val tariffName: String? = null,
    @SerialName("period_days") val periodDays: Int? = null,
)

// ---------- Скидки и уровни ----------

@Serializable
data class PromoOfferDto(
    val id: Long,
    @SerialName("notification_type") val notificationType: String = "",
    @SerialName("discount_percent") val discountPercent: Int? = null,
    @SerialName("effect_type") val effectType: String = "percent_discount",
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("is_claimed") val isClaimed: Boolean = false,
)

@Serializable
data class ActiveDiscountDto(
    @SerialName("discount_percent") val discountPercent: Int = 0,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("is_active") val isActive: Boolean = false,
)

@Serializable
data class ClaimOfferRequest(@SerialName("offer_id") val offerId: Long)

@Serializable
data class ClaimOfferResponse(
    val success: Boolean = false,
    val message: String? = null,
    @SerialName("discount_percent") val discountPercent: Int? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class GroupDiscountsDto(
    @SerialName("group_name") val groupName: String? = null,
    @SerialName("server_discount_percent") val serverPercent: Int = 0,
    @SerialName("traffic_discount_percent") val trafficPercent: Int = 0,
    @SerialName("device_discount_percent") val devicePercent: Int = 0,
    @SerialName("period_discounts") val periodDiscounts: Map<String, Int> = emptyMap(),
)

@Serializable
data class LoyaltyTierDto(
    val id: Long,
    val name: String,
    @SerialName("threshold_rubles") val thresholdRubles: Double = 0.0,
    @SerialName("server_discount_percent") val serverPercent: Int = 0,
    @SerialName("traffic_discount_percent") val trafficPercent: Int = 0,
    @SerialName("device_discount_percent") val devicePercent: Int = 0,
    @SerialName("period_discounts") val periodDiscounts: Map<String, Int> = emptyMap(),
    @SerialName("is_current") val isCurrent: Boolean = false,
    @SerialName("is_achieved") val isAchieved: Boolean = false,
)

@Serializable
data class LoyaltyTiersDto(
    val tiers: List<LoyaltyTierDto> = emptyList(),
    @SerialName("current_spent_rubles") val spentRubles: Double = 0.0,
    @SerialName("current_tier_name") val currentTierName: String? = null,
    @SerialName("next_tier_name") val nextTierName: String? = null,
    @SerialName("next_tier_threshold_rubles") val nextTierThresholdRubles: Double? = null,
    @SerialName("progress_percent") val progressPercent: Double = 0.0,
)

// ---------- Рефералка ----------

@Serializable
data class ReferralTermsDto(
    @SerialName("is_enabled") val isEnabled: Boolean = true,
    @SerialName("commission_percent") val commissionPercent: Int = 0,
    @SerialName("first_payment_commission_percent") val firstPaymentPercent: Int? = null,
    @SerialName("minimum_topup_kopeks") val minimumTopupKopeks: Long = 0,
    @SerialName("first_topup_bonus_kopeks") val firstTopupBonusKopeks: Long = 0,
    @SerialName("inviter_bonus_kopeks") val inviterBonusKopeks: Long = 0,
)

@Serializable
data class ReferralPersonDto(
    val id: Long,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("has_subscription") val hasSubscription: Boolean = false,
    @SerialName("has_paid") val hasPaid: Boolean = false,
) {
    val title: String
        get() = firstName?.takeIf { it.isNotBlank() }
            ?: username?.let { "@$it" }
            ?: "Друг #$id"
}

@Serializable
data class ReferralListDto(
    val items: List<ReferralPersonDto> = emptyList(),
    val total: Int = 0,
    val pages: Int = 1,
)

@Serializable
data class ReferralEarningDto(
    val id: Long,
    @SerialName("amount_kopeks") val amountKopeks: Long = 0,
    val reason: String = "",
    @SerialName("referral_username") val referralUsername: String? = null,
    @SerialName("referral_first_name") val referralFirstName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ReferralEarningsDto(
    val items: List<ReferralEarningDto> = emptyList(),
    val total: Int = 0,
    @SerialName("total_amount_kopeks") val totalAmountKopeks: Long = 0,
)

@Serializable
data class WithdrawalBalanceDto(
    @SerialName("total_earned") val totalEarned: Long = 0,
    val withdrawn: Long = 0,
    val pending: Long = 0,
    @SerialName("available_total") val availableTotal: Long = 0,
    @SerialName("min_amount_kopeks") val minAmountKopeks: Long = 0,
    @SerialName("is_withdrawal_enabled") val isEnabled: Boolean = false,
    @SerialName("can_request") val canRequest: Boolean = false,
    @SerialName("cannot_request_reason") val cannotRequestReason: String? = null,
    @SerialName("requisites_text") val requisitesText: String = "",
)

@Serializable
data class WithdrawalCreateRequest(
    @SerialName("amount_kopeks") val amountKopeks: Long,
    @SerialName("payment_details") val paymentDetails: String,
)

@Serializable
data class WithdrawalDto(
    val id: Long,
    @SerialName("amount_kopeks") val amountKopeks: Long = 0,
    val status: String = "",
    @SerialName("payment_details") val paymentDetails: String? = null,
    @SerialName("admin_comment") val adminComment: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class WithdrawalListDto(val items: List<WithdrawalDto> = emptyList(), val total: Int = 0)
