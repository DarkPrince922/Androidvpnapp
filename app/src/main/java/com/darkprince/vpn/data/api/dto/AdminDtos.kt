package com.darkprince.vpn.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Кто я в панели ----------

@Serializable
data class IsAdminDto(
    @SerialName("is_admin") val isAdmin: Boolean = false,
)

/**
 * Права из RBAC кабинета.
 *
 * `permissions` — список вида `tickets:reply`. У владельца сервиса, заведённого
 * в конфиге бота, приходит одна строка `*:*` — это «всё», и разбирать её как
 * обычное право нельзя.
 */
@Serializable
data class AdminPermissionsDto(
    val permissions: List<String> = emptyList(),
    val roles: List<String> = emptyList(),
    @SerialName("role_level") val roleLevel: Int = 0,
) {
    fun allows(permission: String): Boolean {
        if (permissions.any { it == "*:*" }) return true
        if (permissions.contains(permission)) return true
        // право вида `tickets:*` покрывает всю свою область
        val area = permission.substringBefore(':')
        return permissions.any { it == "$area:*" }
    }
}

// ---------- Тикеты глазами админа ----------

/** Автор обращения. Почты может не быть у входа через Telegram, и наоборот. */
@Serializable
data class AdminTicketUserDto(
    val id: Long,
    @SerialName("telegram_id") val telegramId: Long? = null,
    val email: String? = null,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
) {
    /** Как назвать человека в списке: что есть, то и показываем. */
    val displayName: String
        get() = listOfNotNull(firstName, lastName)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
            ?: username?.let { "@$it" }
            ?: email
            ?: telegramId?.let { "Telegram $it" }
            ?: "Пользователь $id"
}

@Serializable
data class AdminTicketDto(
    val id: Long,
    val title: String,
    val status: String,
    val priority: String = "normal",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("messages_count") val messagesCount: Int = 0,
    val user: AdminTicketUserDto? = null,
    @SerialName("last_message") val lastMessage: SupportMessageDto? = null,
) {
    val isClosed: Boolean get() = status.equals("closed", ignoreCase = true)

    /** Ждёт ответа: человек написал, а мы ещё нет. */
    val needsReply: Boolean
        get() = !isClosed && lastMessage?.isFromAdmin == false
}

@Serializable
data class AdminTicketListDto(
    val items: List<AdminTicketDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 20,
    val pages: Int = 1,
)

@Serializable
data class AdminTicketDetailDto(
    val id: Long,
    val title: String,
    val status: String,
    val priority: String = "normal",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("is_reply_blocked") val isReplyBlocked: Boolean = false,
    val user: AdminTicketUserDto? = null,
    val messages: List<SupportMessageDto> = emptyList(),
) {
    val isClosed: Boolean get() = status.equals("closed", ignoreCase = true)
}

@Serializable
data class AdminTicketStatsDto(
    val total: Int = 0,
    val open: Int = 0,
    val pending: Int = 0,
    val answered: Int = 0,
    val closed: Int = 0,
)

@Serializable
data class AdminReplyRequest(val message: String)

@Serializable
data class AdminStatusRequest(val status: String)

// ---------- Сводка ----------

/** Один узел панели. Из всего, что отдаёт Remnawave, нам важно живой ли он. */
@Serializable
data class AdminNodeDto(
    val uuid: String = "",
    val name: String = "",
    @SerialName("is_connected") val isConnected: Boolean = false,
    @SerialName("is_disabled") val isDisabled: Boolean = false,
    @SerialName("users_online") val usersOnline: Int = 0,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("last_status_message") val lastStatusMessage: String? = null,
) {
    /** Отключённый вручную узел не «упал» — его выключили, и это не тревога. */
    val isDown: Boolean get() = !isConnected && !isDisabled
}

@Serializable
data class AdminNodesDto(
    val total: Int = 0,
    val online: Int = 0,
    val offline: Int = 0,
    val disabled: Int = 0,
    @SerialName("total_users_online") val totalUsersOnline: Int = 0,
    val nodes: List<AdminNodeDto> = emptyList(),
)

@Serializable
data class AdminSubStatsDto(
    val total: Int = 0,
    val active: Int = 0,
    val trial: Int = 0,
    val paid: Int = 0,
    val expired: Int = 0,
    @SerialName("purchased_today") val purchasedToday: Int = 0,
    @SerialName("purchased_week") val purchasedWeek: Int = 0,
    @SerialName("purchased_month") val purchasedMonth: Int = 0,
)

@Serializable
data class AdminFinancialDto(
    @SerialName("income_today_kopeks") val incomeTodayKopeks: Long = 0,
    @SerialName("income_month_kopeks") val incomeMonthKopeks: Long = 0,
    @SerialName("income_total_kopeks") val incomeTotalKopeks: Long = 0,
)

@Serializable
data class AdminDashboardDto(
    val nodes: AdminNodesDto = AdminNodesDto(),
    val subscriptions: AdminSubStatsDto = AdminSubStatsDto(),
    val financial: AdminFinancialDto = AdminFinancialDto(),
)

// ---------- Люди ----------

@Serializable
data class AdminUserDto(
    val id: Long,
    @SerialName("telegram_id") val telegramId: Long? = null,
    val username: String? = null,
    @SerialName("full_name") val fullName: String = "",
    val status: String = "active",
    @SerialName("balance_kopeks") val balanceKopeks: Long = 0,
    @SerialName("has_subscription") val hasSubscription: Boolean = false,
    @SerialName("subscription_status") val subscriptionStatus: String? = null,
    @SerialName("subscription_is_trial") val subscriptionIsTrial: Boolean = false,
    @SerialName("tariff_name") val tariffName: String? = null,
    @SerialName("traffic_used_gb") val trafficUsedGb: Double = 0.0,
    @SerialName("traffic_limit_gb") val trafficLimitGb: Int = 0,
    @SerialName("device_limit") val deviceLimit: Int = 0,
    @SerialName("days_remaining") val daysRemaining: Int = 0,
    @SerialName("total_spent_kopeks") val totalSpentKopeks: Long = 0,
) {
    val displayName: String
        get() = fullName.takeIf { it.isNotBlank() }
            ?: username?.let { "@$it" }
            ?: telegramId?.let { "Telegram $it" }
            ?: "Пользователь $id"

    val isBlocked: Boolean get() = status.equals("blocked", ignoreCase = true)
}

@Serializable
data class AdminUsersListDto(
    val users: List<AdminUserDto> = emptyList(),
    val total: Int = 0,
)

/** Правка баланса. Отрицательная сумма списывает. */
@Serializable
data class AdminBalanceRequest(
    @SerialName("amount_kopeks") val amountKopeks: Long,
    val description: String = "Правка из приложения",
)

@Serializable
data class AdminBalanceResponse(
    val success: Boolean = false,
    @SerialName("old_balance_kopeks") val oldBalanceKopeks: Long = 0,
    @SerialName("new_balance_kopeks") val newBalanceKopeks: Long = 0,
    val message: String? = null,
)

/** Продление подписки. Других действий из приложения не даём. */
@Serializable
data class AdminExtendRequest(
    val action: String = "extend",
    val days: Int,
    @SerialName("subscription_id") val subscriptionId: Long? = null,
)

/**
 * Подписка в карточке человека. Бот отдаёт и старое поле `subscription`, и
 * список `subscriptions` (несколько тарифов сразу) — берём что есть.
 */
@Serializable
data class AdminSubscriptionInfoDto(
    val id: Long,
    val status: String = "",
    @SerialName("is_trial") val isTrial: Boolean = false,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("traffic_limit_gb") val trafficLimitGb: Int = 0,
    @SerialName("traffic_used_gb") val trafficUsedGb: Double = 0.0,
    @SerialName("device_limit") val deviceLimit: Int = 1,
    @SerialName("tariff_id") val tariffId: Long? = null,
    @SerialName("tariff_name") val tariffName: String? = null,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("days_remaining") val daysRemaining: Int = 0,
)

/** Карточка человека целиком: GET /cabinet/admin/users/{id}. */
@Serializable
data class AdminUserDetailDto(
    val id: Long,
    @SerialName("telegram_id") val telegramId: Long? = null,
    val username: String? = null,
    @SerialName("full_name") val fullName: String = "",
    val status: String = "active",
    @SerialName("balance_kopeks") val balanceKopeks: Long = 0,
    val email: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_activity") val lastActivity: String? = null,
    val subscription: AdminSubscriptionInfoDto? = null,
    val subscriptions: List<AdminSubscriptionInfoDto> = emptyList(),
    @SerialName("total_spent_kopeks") val totalSpentKopeks: Long = 0,
) {
    val allSubscriptions: List<AdminSubscriptionInfoDto>
        get() = subscriptions.ifEmpty { listOfNotNull(subscription) }

    val isBlocked: Boolean get() = status.equals("blocked", ignoreCase = true)
}

/**
 * Любое действие с подпиской: POST /cabinet/admin/users/{id}/subscription.
 * Какое — в [action]: extend, shorten, set_device_limit, add_traffic,
 * set_traffic, change_tariff, activate, cancel. Нужные поля — по действию.
 */
@Serializable
data class AdminSubscriptionAction(
    val action: String,
    @SerialName("subscription_id") val subscriptionId: Long? = null,
    val days: Int? = null,
    @SerialName("device_limit") val deviceLimit: Int? = null,
    @SerialName("traffic_limit_gb") val trafficLimitGb: Int? = null,
    @SerialName("traffic_gb") val trafficGb: Int? = null,
    @SerialName("traffic_used_gb") val trafficUsedGb: Double? = null,
    @SerialName("tariff_id") val tariffId: Long? = null,
)

@Serializable
data class AdminTariffOptionDto(
    val id: Long,
    val name: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("traffic_limit_gb") val trafficLimitGb: Int = 0,
    @SerialName("device_limit") val deviceLimit: Int = 1,
)

@Serializable
data class AdminAvailableTariffsDto(
    val tariffs: List<AdminTariffOptionDto> = emptyList(),
)

// ---------- Устройства, платежи, сообщения ----------

@Serializable
data class AdminDeviceDto(
    val hwid: String = "",
    val platform: String = "",
    @SerialName("device_model") val deviceModel: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("local_name") val localName: String? = null,
) {
    /** Имя, данное человеком, важнее модели: он по нему и узнаёт устройство. */
    val displayName: String
        get() = localName?.takeIf { it.isNotBlank() }
            ?: deviceModel.takeIf { it.isNotBlank() }
            ?: platform.takeIf { it.isNotBlank() }
            ?: hwid.take(8)
}

@Serializable
data class AdminDevicesDto(
    val devices: List<AdminDeviceDto> = emptyList(),
    val total: Int = 0,
    @SerialName("device_limit") val deviceLimit: Int = 0,
)

@Serializable
data class AdminTransactionDto(
    val id: Long = 0,
    val type: String = "",
    @SerialName("amount_kopeks") val amountKopeks: Long = 0,
    val description: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("is_completed") val isCompleted: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AdminTransactionsDto(
    val transactions: List<AdminTransactionDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class AdminMessageRequest(val text: String)

/**
 * Промокод.
 *
 * Из шести типов, которые знает бот, в приложении даём два: бонус на баланс
 * и дни подписки. Остальные — скидки, промогруппы, привязка к тарифу —
 * требуют выбора из справочников, которых на телефоне под рукой нет.
 */
@Serializable
data class AdminPromoRequest(
    val code: String,
    val type: String,
    @SerialName("balance_bonus_kopeks") val balanceBonusKopeks: Int = 0,
    @SerialName("subscription_days") val subscriptionDays: Int = 0,
    @SerialName("max_uses") val maxUses: Int = 1,
    @SerialName("is_active") val isActive: Boolean = true,
)

/**
 * Уведомления о тикетах для админа: новые обращения и ответы людей.
 *
 * Кабинет ведёт их сам, отдельной таблицей, — считать «что нового» на
 * телефоне, сравнивая списки тикетов, не нужно.
 */
@Serializable
data class AdminTicketNotificationDto(
    val id: Long = 0,
    @SerialName("ticket_id") val ticketId: Long = 0,
    @SerialName("notification_type") val type: String = "",
    /** Готовый текст вида «Новый тикет #123: тема». */
    val message: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
)

@Serializable
data class AdminTicketNotificationsDto(
    val items: List<AdminTicketNotificationDto> = emptyList(),
    @SerialName("unread_count") val unreadCount: Int = 0,
)

// ---------- Заявки на вывод ----------

@Serializable
data class AdminWithdrawalDto(
    val id: Long,
    @SerialName("user_id") val userId: Long,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("telegram_id") val telegramId: Long? = null,
    @SerialName("amount_kopeks") val amountKopeks: Long = 0,
    val status: String = "",
    @SerialName("risk_score") val riskScore: Int = 0,
    @SerialName("risk_level") val riskLevel: String = "low",
    @SerialName("payment_details") val paymentDetails: String? = null,
    @SerialName("admin_comment") val adminComment: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val who: String
        get() = firstName?.takeIf { it.isNotBlank() }
            ?: username?.let { "@$it" }
            ?: telegramId?.let { "ID $it" }
            ?: "Пользователь #$userId"
}

@Serializable
data class AdminWithdrawalListDto(
    val items: List<AdminWithdrawalDto> = emptyList(),
    val total: Int = 0,
    @SerialName("pending_count") val pendingCount: Int = 0,
    @SerialName("pending_total_kopeks") val pendingTotalKopeks: Long = 0,
)

/** Разбор риска, который бот делает сам: флаги и рекомендация. */
@Serializable
data class WithdrawalRiskDto(
    val flags: List<String> = emptyList(),
    @SerialName("recommendation_text") val recommendationText: String? = null,
)

@Serializable
data class AdminWithdrawalDetailDto(
    val id: Long,
    @SerialName("risk_analysis") val riskAnalysis: WithdrawalRiskDto? = null,
    @SerialName("balance_kopeks") val balanceKopeks: Long = 0,
    @SerialName("total_referrals") val totalReferrals: Int = 0,
    @SerialName("total_earnings_kopeks") val totalEarningsKopeks: Long = 0,
)

@Serializable
data class AdminCommentRequest(val comment: String? = null)

// ---------- Платежи ----------

@Serializable
data class AdminPaymentDto(
    val id: Long,
    val method: String,
    @SerialName("method_display") val methodDisplay: String = "",
    val identifier: String = "",
    @SerialName("amount_kopeks") val amountKopeks: Long = 0,
    val status: String = "",
    @SerialName("status_emoji") val statusEmoji: String = "",
    @SerialName("status_text") val statusText: String = "",
    @SerialName("is_paid") val isPaid: Boolean = false,
    @SerialName("is_checkable") val isCheckable: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("user_telegram_id") val userTelegramId: Long? = null,
    @SerialName("user_username") val userUsername: String? = null,
)

@Serializable
data class AdminPaymentListDto(
    val items: List<AdminPaymentDto> = emptyList(),
    val total: Int = 0,
    val pages: Int = 1,
)

@Serializable
data class AdminPaymentCheckDto(
    val success: Boolean = false,
    val message: String = "",
    val payment: AdminPaymentDto? = null,
    @SerialName("status_changed") val statusChanged: Boolean = false,
)

// ---------- Рассылки ----------

@Serializable
data class BroadcastFilterDto(
    val key: String,
    val label: String,
    val count: Int? = null,
    val group: String? = null,
)

@Serializable
data class BroadcastFiltersDto(
    val filters: List<BroadcastFilterDto> = emptyList(),
    @SerialName("tariff_filters") val tariffFilters: List<BroadcastFilterDto> = emptyList(),
    @SerialName("custom_filters") val customFilters: List<BroadcastFilterDto> = emptyList(),
)

@Serializable
data class BroadcastButtonDto(val key: String, val label: String, val default: Boolean = false)

@Serializable
data class BroadcastButtonsDto(val buttons: List<BroadcastButtonDto> = emptyList())

@Serializable
data class BroadcastCreateRequest(
    val target: String,
    @SerialName("message_text") val messageText: String,
    @SerialName("selected_buttons") val selectedButtons: List<String>,
    val category: String = "system",
)

@Serializable
data class BroadcastDto(
    val id: Long,
    @SerialName("target_type") val targetType: String = "",
    @SerialName("message_text") val messageText: String? = null,
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("sent_count") val sentCount: Int = 0,
    @SerialName("failed_count") val failedCount: Int = 0,
    @SerialName("blocked_count") val blockedCount: Int = 0,
    val status: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("progress_percent") val progressPercent: Double = 0.0,
    val channel: String = "telegram",
) {
    val running: Boolean get() = status in setOf("queued", "in_progress", "cancelling")
}

@Serializable
data class BroadcastListDto(val items: List<BroadcastDto> = emptyList(), val total: Int = 0)
