package com.darkprince.vpn.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.darkprince.vpn.data.api.ApiClient
import com.darkprince.vpn.data.api.dto.SupportConfigDto
import com.darkprince.vpn.data.api.dto.SupportMediaUploadDto
import com.darkprince.vpn.data.api.dto.SupportMessageCreateRequest
import com.darkprince.vpn.data.api.dto.SupportMessageDto
import com.darkprince.vpn.data.api.dto.SupportTicketCreateRequest
import com.darkprince.vpn.data.api.dto.SupportTicketDetailDto
import com.darkprince.vpn.data.api.dto.SupportTicketDto
import com.darkprince.vpn.data.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.IOException

data class PendingSupportAttachment(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long?,
)

/** Тикеты Bedolaga: REST для истории/отправки и WebSocket для живых событий. */
class SupportRepository(
    private val apiClient: ApiClient,
    private val prefs: AppPrefs,
    private val context: Context,
) {
    private val api get() = apiClient.api

    val isLoggedIn: Boolean get() = prefs.cachedRefreshToken != null

    suspend fun config(): SupportConfigDto = api.supportConfig()

    suspend fun tickets(): List<SupportTicketDto> =
        api.supportTickets(perPage = 100).items

    suspend fun ticket(id: Long): SupportTicketDetailDto = api.supportTicket(id)

    suspend fun unreadCount(): Int = api.supportUnreadCount().unreadCount

    suspend fun markRead(ticketId: Long) {
        api.markSupportTicketRead(ticketId)
    }

    /**
     * Bedolaga разрешает только один незакрытый тикет. Если два устройства
     * одновременно попробовали создать новый, открываем уже существующий.
     */
    suspend fun createTicket(
        title: String,
        message: String,
        media: SupportMediaUploadDto? = null,
    ): SupportTicketDetailDto {
        return try {
            api.createSupportTicket(
                SupportTicketCreateRequest(
                    title = title,
                    message = message,
                    mediaType = media?.mediaType,
                    mediaFileId = media?.fileId,
                    mediaCaption = message.takeIf { media != null && it.isNotBlank() },
                )
            )
        } catch (error: HttpException) {
            if (error.code() != 409) throw error
            val active = tickets().firstOrNull { !it.isClosed } ?: throw error
            ticket(active.id)
        }
    }

    suspend fun reply(
        ticketId: Long,
        message: String,
        media: SupportMediaUploadDto? = null,
    ): SupportMessageDto = api.addSupportMessage(
        ticketId,
        SupportMessageCreateRequest(
            message = message,
            mediaType = media?.mediaType,
            mediaFileId = media?.fileId,
            mediaCaption = message.takeIf { media != null && it.isNotBlank() },
        )
    )

    suspend fun describeAttachment(uri: Uri): PendingSupportAttachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "Вложение"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameColumn >= 0) name = cursor.getString(nameColumn) ?: name
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn)
                }
            }
        size?.let {
            if (it > MAX_FILE_SIZE) throw IllegalArgumentException("Файл больше 10 МБ")
        }
        PendingSupportAttachment(
            uri = uri,
            name = name.take(128),
            mimeType = resolver.getType(uri) ?: "application/octet-stream",
            size = size,
        )
    }

    suspend fun upload(attachment: PendingSupportAttachment): SupportMediaUploadDto =
        withContext(Dispatchers.IO) {
            val bytes = readBounded(attachment.uri)
            if (bytes.isEmpty()) throw IllegalArgumentException("Файл пуст")
            val mediaType = when {
                attachment.mimeType in SAFE_IMAGE_TYPES -> "photo"
                attachment.mimeType.startsWith("video/") -> "video"
                else -> "document"
            }
            val body = bytes.toRequestBody(attachment.mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", attachment.name, body)
            val kind = mediaType.toRequestBody("text/plain".toMediaTypeOrNull())
            api.uploadSupportMedia(part, kind)
        }

    private fun readBounded(uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Не удалось открыть файл")
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                if (out.size() + read > MAX_FILE_SIZE) {
                    throw IllegalArgumentException("Файл больше 10 МБ")
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    fun mediaUrl(message: SupportMessageDto): String? {
        val item = message.mediaItems?.firstOrNull()
        val fileId = item?.fileId ?: message.mediaFileId ?: return null
        val token = item?.token ?: message.mediaToken ?: return null
        val base = prefs.cachedBaseUrl.trimEnd('/')
        return "$base/cabinet/media/${Uri.encode(fileId)}?token=${Uri.encode(token)}"
    }

    /**
     * Сокет нужен только как сигнал «данные изменились»: каноническую историю
     * после события снова читаем REST-запросом. Так переживаем повторные события,
     * смену версии протокола и разрыв соединения без дубликатов сообщений.
     */
    fun openEventSocket(
        onConnected: (Boolean) -> Unit,
        onTicketChanged: (Long?) -> Unit,
    ): WebSocket? {
        val token = prefs.cachedAccessToken ?: return null
        val base = prefs.cachedBaseUrl.trimEnd('/')
        // Только wss. По ws токен доступа ушёл бы в заголовке открытым
        // текстом, а переписка с поддержкой — открытой всему пути. Если база
        // задана по http, сокета просто нет: обновления в этом случае
        // приносит опрос, он идёт параллельно и ничего не теряет.
        val wsBase = if (base.startsWith("https://")) {
            "wss://${base.removePrefix("https://")}"
        } else {
            return null
        }
        val request = Request.Builder()
            .url("$wsBase/cabinet/ws/support/v1")
            .header("Authorization", "Bearer $token")
            .header("Sec-WebSocket-Protocol", SUPPORT_SUBPROTOCOL)
            .build()

        return apiClient.plainOkHttp.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onConnected(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    return
                }
                if (event.optString("type") != "event") return
                val eventName = event.optString("event")
                if (eventName == "connection.ready" || eventName.startsWith("auth.")) return
                val payload = event.optJSONObject("payload")
                val rawId = payload?.opt("ticketId") ?: payload?.opt("ticket_id")
                val ticketId = when (rawId) {
                    is Number -> rawId.toLong()
                    is String -> rawId.toLongOrNull()
                    else -> null
                }
                onTicketChanged(ticketId)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onConnected(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onConnected(false)
            }
        })
    }

    companion object {
        const val DEFAULT_SUPPORT_URL = "https://t.me/skzfeee"
        private const val SUPPORT_SUBPROTOCOL = "bedolaga.support.mobile.v1"
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024
        private val SAFE_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp")
    }
}

fun supportErrorMessage(error: Throwable): String {
    if (error is IllegalArgumentException) return error.message ?: "Некорректное вложение"
    if (error is IOException) return "Нет соединения с поддержкой"
    if (error !is HttpException) return error.message ?: "Не удалось связаться с поддержкой"

    val detail = try {
        val raw = error.response()?.errorBody()?.string().orEmpty()
        JSONObject(raw).optString("detail").takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
    return when (error.code()) {
        400 -> if (detail?.contains("closed", ignoreCase = true) == true) {
            "Тикет уже закрыт"
        } else "Проверьте текст сообщения и вложение"
        401 -> "Сессия истекла. Войдите в аккаунт ещё раз"
        403 -> when {
            detail?.contains("disabled", ignoreCase = true) == true -> "Тикеты временно отключены"
            detail?.contains("blocked", ignoreCase = true) == true -> "Обращения в поддержку для аккаунта ограничены"
            else -> "Поддержка недоступна для этого аккаунта"
        }
        404 -> "Тикет не найден"
        409 -> "У вас уже есть открытый тикет"
        413 -> "Файл слишком большой"
        422 -> "Проверьте заголовок, сообщение и вложение"
        429 -> "Слишком много сообщений. Попробуйте немного позже"
        in 500..599 -> "Поддержка временно недоступна"
        else -> detail ?: "Ошибка поддержки (${error.code()})"
    }
}
