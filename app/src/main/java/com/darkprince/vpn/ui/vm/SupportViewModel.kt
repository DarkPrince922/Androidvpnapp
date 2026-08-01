package com.darkprince.vpn.ui.vm

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.api.dto.SupportConfigDto
import com.darkprince.vpn.data.api.dto.SupportTicketDetailDto
import com.darkprince.vpn.data.api.dto.SupportTicketDto
import com.darkprince.vpn.data.repo.PendingSupportAttachment
import com.darkprince.vpn.data.repo.SupportRepository
import com.darkprince.vpn.data.repo.supportErrorMessage
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.WebSocket

data class SupportUiState(
    val loggedIn: Boolean = false,
    val config: SupportConfigDto? = null,
    val tickets: List<SupportTicketDto> = emptyList(),
    val activeTicket: SupportTicketDetailDto? = null,
    val unreadCount: Int = 0,
    val loading: Boolean = false,
    val sending: Boolean = false,
    val socketConnected: Boolean = false,
    val pendingAttachment: PendingSupportAttachment? = null,
    val error: String? = null,
    val info: String? = null,
    val navigateToTicketId: Long? = null,
    val replySentVersion: Long = 0,
) {
    val ticketsEnabled: Boolean get() = config?.ticketsEnabled == true
    val contactUrl: String
        get() = config?.supportUrl?.takeIf { it.startsWith("https://") || it.startsWith("tg://") }
            ?: SupportRepository.DEFAULT_SUPPORT_URL
}

class SupportViewModel : ViewModel() {
    private val repository = ServiceLocator.supportRepository

    private val _state = MutableStateFlow(SupportUiState())
    val state: StateFlow<SupportUiState> = _state

    private var socket: WebSocket? = null
    private var pollingJob: Job? = null

    init {
        refresh()
    }

    /** Вызывать после входа/выхода, чтобы гостю не остались чужие тикеты. */
    fun sessionChanged() {
        stopWatching()
        _state.value = SupportUiState()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val loggedIn = repository.isLoggedIn
            _state.update { it.copy(loggedIn = loggedIn, loading = true, error = null) }

            val fallback = SupportConfigDto(
                ticketsEnabled = loggedIn,
                supportType = "both",
                supportUrl = SupportRepository.DEFAULT_SUPPORT_URL,
                supportUsername = "@skzfeee",
                contactIsTelegram = true,
            )
            val config = try {
                repository.config()
            } catch (_: Exception) {
                _state.value.config ?: fallback
            }

            if (!loggedIn || !config.ticketsEnabled) {
                _state.update {
                    it.copy(
                        loggedIn = loggedIn,
                        config = config,
                        tickets = emptyList(),
                        activeTicket = null,
                        unreadCount = 0,
                        loading = false,
                    )
                }
                return@launch
            }

            try {
                val tickets = repository.tickets()
                val unread = runCatching { repository.unreadCount() }.getOrDefault(0)
                _state.update {
                    it.copy(
                        config = config,
                        tickets = tickets,
                        unreadCount = unread,
                        loading = false,
                        error = null,
                    )
                }
                if (pollingJob == null) startWatching(ticketId = null)
            } catch (error: Exception) {
                _state.update {
                    it.copy(config = config, loading = false, error = supportErrorMessage(error))
                }
            }
        }
    }

    fun refreshUnread() {
        if (!repository.isLoggedIn) return
        viewModelScope.launch {
            runCatching { repository.unreadCount() }
                .onSuccess { count -> _state.update { it.copy(unreadCount = count) } }
        }
    }

    fun chooseAttachment(uri: Uri) {
        viewModelScope.launch {
            try {
                val attachment = repository.describeAttachment(uri)
                _state.update { it.copy(pendingAttachment = attachment, error = null) }
            } catch (error: Exception) {
                _state.update { it.copy(error = supportErrorMessage(error)) }
            }
        }
    }

    fun removeAttachment() {
        _state.update { it.copy(pendingAttachment = null) }
    }

    fun createTicket(title: String, message: String) {
        val normalizedTitle = title.trim()
        val normalizedMessage = message.trim()
        val attachment = _state.value.pendingAttachment
        if (normalizedTitle.length < 3) {
            _state.update { it.copy(error = "Заголовок должен содержать минимум 3 символа") }
            return
        }
        if (normalizedMessage.isBlank() && attachment == null) {
            _state.update { it.copy(error = "Опишите проблему или добавьте вложение") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null, info = null) }
            try {
                val media = attachment?.let { repository.upload(it) }
                val ticket = repository.createTicket(normalizedTitle, normalizedMessage, media)
                _state.update {
                    it.copy(
                        sending = false,
                        pendingAttachment = null,
                        navigateToTicketId = ticket.id,
                        info = "Обращение отправлено в поддержку",
                    )
                }
                refreshTicketListSilently()
            } catch (error: Exception) {
                _state.update { it.copy(sending = false, error = supportErrorMessage(error)) }
            }
        }
    }

    fun consumeTicketNavigation() {
        _state.update { it.copy(navigateToTicketId = null) }
    }

    fun openTicket(ticketId: Long) {
        stopWatching()
        _state.update {
            it.copy(
                activeTicket = null,
                pendingAttachment = null,
                loading = true,
                error = null,
                info = null,
            )
        }
        viewModelScope.launch {
            try {
                val ticket = repository.ticket(ticketId)
                _state.update { it.copy(activeTicket = ticket, loading = false) }
                runCatching { repository.markRead(ticketId) }
                refreshUnread()
                startWatching(ticketId)
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = supportErrorMessage(error)) }
            }
        }
    }

    fun sendReply(message: String) {
        val ticket = _state.value.activeTicket ?: return
        val normalized = message.trim()
        val attachment = _state.value.pendingAttachment
        if (normalized.isBlank() && attachment == null) return

        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            try {
                val media = attachment?.let { repository.upload(it) }
                repository.reply(ticket.id, normalized, media)
                _state.update {
                    it.copy(
                        sending = false,
                        pendingAttachment = null,
                        replySentVersion = it.replySentVersion + 1,
                    )
                }
                refreshActiveTicket(ticket.id)
                refreshTicketListSilently()
            } catch (error: Exception) {
                _state.update { it.copy(sending = false, error = supportErrorMessage(error)) }
            }
        }
    }

    fun attachmentUrl(messageId: Long): String? {
        val message = _state.value.activeTicket?.messages?.firstOrNull { it.id == messageId } ?: return null
        return repository.mediaUrl(message)
    }

    fun leaveTicket() {
        stopWatching()
        _state.update {
            it.copy(activeTicket = null, pendingAttachment = null, socketConnected = false, error = null)
        }
        refreshTicketListSilently()
        if (repository.isLoggedIn && _state.value.ticketsEnabled) {
            startWatching(ticketId = null)
        }
    }

    private fun startWatching(ticketId: Long?) {
        socket = repository.openEventSocket(
            onConnected = { connected ->
                _state.update { it.copy(socketConnected = connected) }
            },
            onTicketChanged = { changedId ->
                viewModelScope.launch {
                    if (ticketId != null && (changedId == null || changedId == ticketId)) {
                        refreshActiveTicket(ticketId)
                    }
                    refreshTicketListSilently()
                    refreshUnread()
                }
            },
        )
        // WebSocket ускоряет обновление, polling гарантирует доставку после
        // смены сети, истечения access-токена или старой версии Bedolaga.
        // Вне чата реже обновляем только список и счётчик новых ответов.
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(if (ticketId == null) 30_000 else 8_000)
                if (ticketId == null) {
                    refreshTicketListSilently()
                    refreshUnread()
                } else {
                    refreshActiveTicket(ticketId)
                }
            }
        }
    }

    private suspend fun refreshActiveTicket(ticketId: Long) {
        if (_state.value.activeTicket?.id != ticketId) return
        try {
            val ticket = repository.ticket(ticketId)
            _state.update { it.copy(activeTicket = ticket, error = null) }
            runCatching { repository.markRead(ticketId) }
        } catch (_: Exception) {
            // Фоновое обновление не должно перекрывать переписку ошибкой.
        }
    }

    private fun refreshTicketListSilently() {
        if (!repository.isLoggedIn) return
        viewModelScope.launch {
            runCatching { repository.tickets() }
                .onSuccess { tickets -> _state.update { it.copy(tickets = tickets) } }
        }
    }

    private fun stopWatching() {
        pollingJob?.cancel()
        pollingJob = null
        socket?.close(1000, "screen closed")
        socket = null
        _state.update { it.copy(socketConnected = false) }
    }

    override fun onCleared() {
        stopWatching()
        super.onCleared()
    }
}
