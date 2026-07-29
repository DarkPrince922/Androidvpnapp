package com.darkprince.vpn.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ---------- Auth ----------

@Serializable
data class DeepLinkRequestResponse(
    val token: String,
    @SerialName("bot_username") val botUsername: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 300,
)

@Serializable
data class DeepLinkPollRequest(val token: String)

@Serializable
data class EmailLoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class EmailRegisterRequest(
    val email: String,
    val password: String,
    @SerialName("first_name") val firstName: String? = null,
    val language: String? = null,
    @SerialName("referral_code") val referralCode: String? = null,
)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class LogoutRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class ForgotPasswordRequest(val email: String)

@Serializable
data class UserDto(
    val id: Long? = null,
    @SerialName("telegram_id") val telegramId: Long? = null,
    val email: String? = null,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("balance_kopeks") val balanceKopeks: Long? = null,
    val language: String? = null,
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val user: UserDto? = null,
    // регистрация может вернуть просьбу подтвердить почту вместо токенов
    val message: String? = null,
    @SerialName("requires_email_verification") val requiresEmailVerification: Boolean? = null,
)

// ---------- Subscription ----------

@Serializable
data class SubscriptionStatusResponse(
    val id: Long? = null,
    val status: String? = null,
    @SerialName("is_trial") val isTrial: Boolean? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("days_left") val daysLeft: Int? = null,
    @SerialName("traffic_used_gb") val trafficUsedGb: Double? = null,
    @SerialName("traffic_limit_gb") val trafficLimitGb: Double? = null,
    @SerialName("device_limit") val deviceLimit: Int? = null,
    @SerialName("subscription_url") val subscriptionUrl: String? = null,
    @SerialName("tariff_name") val tariffName: String? = null,
    @SerialName("autopay_enabled") val autopayEnabled: Boolean? = null,
    @SerialName("actual_status") val actualStatus: String? = null,
)

@Serializable
data class SubscriptionListItem(
    val id: Long,
    val status: String? = null,
    @SerialName("tariff_id") val tariffId: Long? = null,
    @SerialName("tariff_name") val tariffName: String? = null,
    @SerialName("traffic_limit_gb") val trafficLimitGb: Double? = null,
    @SerialName("traffic_used_gb") val trafficUsedGb: Double? = null,
    @SerialName("device_limit") val deviceLimit: Int? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("subscription_url") val subscriptionUrl: String? = null,
    @SerialName("is_trial") val isTrial: Boolean? = null,
    @SerialName("autopay_enabled") val autopayEnabled: Boolean? = null,
) {
    val displayName: String
        get() = tariffName?.takeIf { it.isNotBlank() }
            ?: if (isTrial == true) "Пробная подписка" else "Подписка #$id"

    val isActive: Boolean
        get() = status?.lowercase() in setOf("active", "trial", "активна")
}

@Serializable
data class SubscriptionsListResponse(
    val subscriptions: List<SubscriptionListItem> = emptyList(),
    @SerialName("multi_tariff_enabled") val multiTariffEnabled: Boolean = false,
)

@Serializable
data class ConnectionLinkResponse(
    @SerialName("subscription_url") val subscriptionUrl: String? = null,
    @SerialName("happ_scheme_link") val happSchemeLink: String? = null,
    val instructions: JsonElement? = null,
)

@Serializable
data class TrialInfoResponse(
    val available: Boolean? = null,
    @SerialName("is_available") val isAvailable: Boolean? = null,
    @SerialName("duration_days") val durationDays: Int? = null,
    val message: String? = null,
)

@Serializable
data class PurchaseTariffRequest(
    @SerialName("tariff_id") val tariffId: Long,
    @SerialName("period_days") val periodDays: Int,
    @SerialName("traffic_gb") val trafficGb: Int? = null,
)

@Serializable
data class RenewRequest(@SerialName("period_days") val periodDays: Int)

@Serializable
data class DevicesPurchaseRequest(val devices: Int)

@Serializable
data class ReduceDevicesRequest(@SerialName("new_device_limit") val newDeviceLimit: Int)

@Serializable
data class TrafficPurchaseRequest(val gb: Int)

// ---------- Balance / payments ----------

@Serializable
data class BalanceResponse(
    @SerialName("balance_kopeks") val balanceKopeks: Long? = null,
    @SerialName("balance_rubles") val balanceRubles: Double? = null,
    val currency: String? = null,
)

@Serializable
data class PaymentMethodDto(
    val id: String? = null,
    val method: String? = null,
    val name: String? = null,
    val title: String? = null,
    val icon: String? = null,
    val enabled: Boolean? = null,
    @SerialName("is_enabled") val isEnabled: Boolean? = null,
    @SerialName("min_amount_kopeks") val minAmountKopeks: Long? = null,
    @SerialName("max_amount_kopeks") val maxAmountKopeks: Long? = null,
    val options: JsonElement? = null,
) {
    val effectiveId: String get() = method ?: id ?: ""
    val effectiveName: String get() = title ?: name ?: effectiveId
    val effectiveEnabled: Boolean get() = enabled ?: isEnabled ?: true
}

@Serializable
data class TopupRequest(
    @SerialName("amount_kopeks") val amountKopeks: Long,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("payment_option") val paymentOption: String? = null,
    val language: String? = "ru",
)

@Serializable
data class TopupResponse(
    @SerialName("payment_id") val paymentId: String? = null,
    @SerialName("payment_url") val paymentUrl: String? = null,
    @SerialName("amount_kopeks") val amountKopeks: Long? = null,
    val status: String? = null,
    val message: String? = null,
)

@Serializable
data class TransactionDto(
    val id: Long? = null,
    val type: String? = null,
    @SerialName("amount_kopeks") val amountKopeks: Long? = null,
    val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val status: String? = null,
)

@Serializable
data class TransactionsResponse(
    val items: List<TransactionDto>? = null,
    val transactions: List<TransactionDto>? = null,
    val total: Int? = null,
    val page: Int? = null,
) {
    val list: List<TransactionDto> get() = items ?: transactions ?: emptyList()
}

// ---------- Рефералка и промокоды ----------

@Serializable
data class ReferralInfoResponse(
    @SerialName("referral_code") val referralCode: String? = null,
    @SerialName("referral_link") val referralLink: String? = null,
    @SerialName("bot_referral_link") val botReferralLink: String? = null,
    @SerialName("total_referrals") val totalReferrals: Int? = null,
    @SerialName("active_referrals") val activeReferrals: Int? = null,
    @SerialName("total_earnings_kopeks") val totalEarningsKopeks: Long? = null,
    @SerialName("available_balance_kopeks") val availableBalanceKopeks: Long? = null,
    @SerialName("commission_percent") val commissionPercent: Double? = null,
) {
    val shareLink: String? get() = botReferralLink ?: referralLink
}

@Serializable
data class PromoActivateRequest(val code: String)

@Serializable
data class PromoActivateResponse(
    val success: Boolean? = null,
    val message: String? = null,
    @SerialName("bonus_description") val bonusDescription: String? = null,
)

// Ответы неизвестной/переменной формы храним как JsonObject и разбираем адаптивно.
typealias RawJson = JsonObject
