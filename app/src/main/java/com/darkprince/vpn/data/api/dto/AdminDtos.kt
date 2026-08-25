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
