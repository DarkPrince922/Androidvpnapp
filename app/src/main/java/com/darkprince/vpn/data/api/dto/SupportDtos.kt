package com.darkprince.vpn.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupportConfigDto(
    @SerialName("tickets_enabled") val ticketsEnabled: Boolean = false,
    @SerialName("support_type") val supportType: String = "tickets",
    @SerialName("support_url") val supportUrl: String? = null,
    @SerialName("support_username") val supportUsername: String? = null,
    @SerialName("contact_is_telegram") val contactIsTelegram: Boolean = false,
)

@Serializable
data class SupportTicketListDto(
    val items: List<SupportTicketDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 20,
    val pages: Int = 1,
)

@Serializable
data class SupportTicketDto(
    val id: Long,
    val title: String,
    val status: String,
    val priority: String = "normal",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("messages_count") val messagesCount: Int = 0,
    @SerialName("last_message") val lastMessage: SupportMessageDto? = null,
) {
    val isClosed: Boolean get() = status.equals("closed", ignoreCase = true)
}

@Serializable
data class SupportTicketDetailDto(
    val id: Long,
    val title: String,
    val status: String,
    val priority: String = "normal",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("is_reply_blocked") val isReplyBlocked: Boolean = false,
    val messages: List<SupportMessageDto> = emptyList(),
) {
    val isClosed: Boolean get() = status.equals("closed", ignoreCase = true)
}

@Serializable
data class SupportMessageDto(
    val id: Long,
    @SerialName("message_text") val messageText: String = "",
    @SerialName("is_from_admin") val isFromAdmin: Boolean = false,
    @SerialName("has_media") val hasMedia: Boolean = false,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("media_file_id") val mediaFileId: String? = null,
    @SerialName("media_token") val mediaToken: String? = null,
    @SerialName("media_caption") val mediaCaption: String? = null,
    @SerialName("media_items") val mediaItems: List<SupportMediaItemDto>? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class SupportMediaItemDto(
    val type: String,
    @SerialName("file_id") val fileId: String,
    val caption: String? = null,
    val token: String? = null,
)

@Serializable
data class SupportTicketCreateRequest(
    val title: String,
    val message: String,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("media_file_id") val mediaFileId: String? = null,
    @SerialName("media_caption") val mediaCaption: String? = null,
)

@Serializable
data class SupportMessageCreateRequest(
    val message: String,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("media_file_id") val mediaFileId: String? = null,
    @SerialName("media_caption") val mediaCaption: String? = null,
)

@Serializable
data class SupportMediaUploadDto(
    @SerialName("media_type") val mediaType: String,
    @SerialName("file_id") val fileId: String,
    @SerialName("file_unique_id") val fileUniqueId: String? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
)

@Serializable
data class SupportUnreadCountDto(
    @SerialName("unread_count") val unreadCount: Int = 0,
)
