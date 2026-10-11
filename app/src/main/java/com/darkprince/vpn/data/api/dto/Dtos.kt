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
    @SerialName("email_verified") val emailVerified: Boolean? = null,
)

// ---------- Привязка почты к аккаунту ----------

@Serializable
data class EmailLinkRequest(val email: String, val password: String)

/**
 * Ответ на привязку. Обычно — «письмо отправлено». Если почта уже занята
 * другим аккаунтом, бот присылает на неё код и просит подтвердить
 * объединение: `merge_required`.
 */
@Serializable
data class EmailLinkResponse(
    val message: String? = null,
    val email: String? = null,
    @SerialName("merge_required") val mergeRequired: Boolean? = null,
    @SerialName("merge_token") val mergeToken: String? = null,
)

@Serializable
data class MergeCodeRequest(val code: String)

@Serializable
data class MergePreviewSubscriptionDto(
    val status: String? = null,
    @SerialName("tariff_name") val tariffName: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("device_limit") val deviceLimit: Int? = null,
    @SerialName("traffic_limit_gb") val trafficLimitGb: Double? = null,
    @SerialName("is_trial") val isTrial: Boolean? = null,
)

@Serializable
data class MergePreviewUserDto(
    val id: Long,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    val email: String? = null,
    @SerialName("auth_methods") val authMethods: List<String> = emptyList(),
    @SerialName("balance_kopeks") val balanceKopeks: Long = 0,
    val subscription: MergePreviewSubscriptionDto? = null,
)

@Serializable
data class MergePreviewDto(
    val primary: MergePreviewUserDto,
    val secondary: MergePreviewUserDto,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int = 0,
)

@Serializable
data class MergeExecuteRequest(
    @SerialName("keep_subscription_from") val keepSubscriptionFrom: Long,
)

@Serializable
data class MergeResultDto(
    val success: Boolean = false,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: UserDto? = null,
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
data class DeviceDto(
    val hwid: String,
    val platform: String? = null,
    @SerialName("device_model") val deviceModel: String? = null,
    @SerialName("local_name") val localName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val title: String
        get() = localName?.takeIf { it.isNotBlank() }
            ?: deviceModel?.takeIf { it.isNotBlank() }
            ?: platform?.takeIf { it.isNotBlank() }
            ?: "Устройство"

    val subtitle: String?
        get() = listOfNotNull(
            platform?.takeIf { it.isNotBlank() && it != title },
            deviceModel?.takeIf { it.isNotBlank() && it != title },
        ).joinToString(" · ").takeIf { it.isNotBlank() }
}

@Serializable
data class DevicesListResponse(
    val devices: List<DeviceDto> = emptyList(),
    val total: Int? = null,
    @SerialName("device_limit") val deviceLimit: Int? = null,
)

@Serializable
data class RenameDeviceRequest(val name: String)

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

    /**
     * Чем именно можно заплатить этим способом.
     *
     * У части касс внутри одного способа несколько вариантов — у Platega,
     * например, СБП и карта. Бот присылает их в `options`, и выбранный нужно
     * передать в `payment_option`. Раньше варианты не читались, бот брал
     * первый — и оплатить можно было только по СБП.
     */
    val choices: List<PaymentChoice>
        get() {
            val list = (options as? kotlinx.serialization.json.JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .mapNotNull { option ->
                    val optionId = option.string("id") ?: return@mapNotNull null
                    val label = option.string("name")?.takeIf { it.isNotBlank() }
                        ?: option.string("description")?.takeIf { it.isNotBlank() }
                        ?: optionId
                    PaymentChoice(this, optionId, label)
                }
            return list.ifEmpty { listOf(PaymentChoice(this, null, effectiveName)) }
        }
}

private fun JsonObject.string(key: String): String? =
    (get(key) as? kotlinx.serialization.json.JsonPrimitive)
        ?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content

/** Один вариант оплаты: способ и, если у него есть варианты, выбранный. */
data class PaymentChoice(
    val method: PaymentMethodDto,
    val optionId: String?,
    val label: String,
) {
    val key: String get() = method.effectiveId + "/" + (optionId ?: "")
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

// ---------- Новости ----------

/**
 * Статья в списке. Тела статьи здесь нет — кабинет отдаёт его только в
 * карточке, и это правильно: в ленте оно никому не нужно, а весит больше
 * всего остального вместе взятого.
 */
@Serializable
data class NewsItemDto(
    val id: Long,
    val title: String,
    val slug: String,
    val excerpt: String? = null,
    val category: String? = null,
    @SerialName("category_color") val categoryColor: String? = null,
    val tag: String? = null,
    @SerialName("featured_image_url") val featuredImageUrl: String? = null,
    @SerialName("is_featured") val isFeatured: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("read_time_minutes") val readTimeMinutes: Int = 1,
    @SerialName("views_count") val viewsCount: Int = 0,
)

@Serializable
data class NewsListResponse(
    val items: List<NewsItemDto> = emptyList(),
    val total: Int = 0,
    val categories: List<String> = emptyList(),
)

/** Статья целиком. [content] — размеченный HTML, кабинет чистит его у себя. */
@Serializable
data class NewsArticleDto(
    val id: Long,
    val title: String,
    val slug: String,
    val content: String = "",
    val excerpt: String? = null,
    val category: String? = null,
    @SerialName("category_color") val categoryColor: String? = null,
    val tag: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("read_time_minutes") val readTimeMinutes: Int = 1,
    @SerialName("views_count") val viewsCount: Int = 0,
    @SerialName("author_name") val authorName: String? = null,
)
