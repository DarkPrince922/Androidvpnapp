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
