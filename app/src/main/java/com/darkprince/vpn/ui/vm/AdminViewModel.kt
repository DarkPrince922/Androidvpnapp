package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.api.dto.AdminPermissionsDto
import com.darkprince.vpn.data.api.dto.AdminTicketDetailDto
import com.darkprince.vpn.data.api.dto.AdminTicketDto
import com.darkprince.vpn.data.repo.AdminRepository
import com.darkprince.vpn.data.repo.adminErrorMessage
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Какие обращения показывать в списке. */
enum class TicketFilter(val api: String?, val title: String) {
    ACTIVE(null, "Все"),
    OPEN("open", "Открытые"),
    ANSWERED("answered", "Отвеченные"),
    CLOSED("closed", "Закрытые"),
}

data class AdminUiState(
    val isAdmin: Boolean = false,
    /** Почему вкладки нет. Показывается только в отладочной сборке. */
    val checkReason: String = "ещё не спрашивали",
    val unlocked: Boolean = false,
    val permissions: AdminPermissionsDto = AdminPermissionsDto(),
    val filter: TicketFilter = TicketFilter.ACTIVE,
    val tickets: List<AdminTicketDto> = emptyList(),
    val openCount: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val ticket: AdminTicketDetailDto? = null,
    val ticketLoading: Boolean = false,
    val sending: Boolean = false,
    val ticketError: String? = null,
    val info: String? = null,
) {
    val canReply: Boolean get() = permissions.allows(AdminRepository.TICKETS_REPLY)
    val canClose: Boolean get() = permissions.allows(AdminRepository.TICKETS_CLOSE)
    val canReadTickets: Boolean get() = permissions.allows(AdminRepository.TICKETS_READ)
}

class AdminViewModel : ViewModel() {
    private val repository = ServiceLocator.adminRepository

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state

    init {
        checkAdmin()
    }

    /**
     * После входа или выхода. Замок тоже сбрасываем: подтверждение личности
     * относится к сессии, а не к устройству, и после смены аккаунта его
     * нужно спрашивать заново.
     */
    fun sessionChanged() {
        _state.value = AdminUiState()
        checkAdmin()
    }

    private fun checkAdmin() {
        viewModelScope.launch {
            val check = repository.isAdmin()
            _state.update { it.copy(isAdmin = check.admin, checkReason = check.reason) }
            if (check.admin) loadCount()
        }
    }

    /**
     * Спросить заново.
     *
     * Первая проверка идёт при запуске и может не удаться на ровном месте:
     * сети ещё нет, токен просрочен. Одной неудачи не должно хватать, чтобы
     * панель пропала до перезапуска приложения, поэтому «Ещё» переспрашивает
     * при каждом открытии.
     */
    fun recheck() {
        if (_state.value.isAdmin) return
        checkAdmin()
    }

    /** Счётчик открытых обращений для точки на вкладке. */
    private fun loadCount() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(openCount = repository.stats().open) }
            } catch (_: Exception) {
                // счётчик — украшение, без него вкладка работает
            }
        }
    }

    /** Личность подтвердили: перечитываем права и открываем список. */
    fun onUnlocked() {
        _state.update { it.copy(unlocked = true) }
        refresh()
    }

    fun lock() {
        _state.update { it.copy(unlocked = false, tickets = emptyList(), ticket = null) }
    }

    fun setFilter(filter: TicketFilter) {
        if (_state.value.filter == filter) return
        _state.update { it.copy(filter = filter) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                // Права спрашиваем заново при каждом открытии вкладки: роль
                // могли снять в панели минуту назад.
                val permissions = repository.permissions()
                if (!permissions.allows(AdminRepository.TICKETS_READ)) {
                    _state.update {
                        it.copy(permissions = permissions, loading = false, tickets = emptyList())
                    }
                    return@launch
                }
                val tickets = repository.tickets(status = _state.value.filter.api)
                _state.update {
                    it.copy(permissions = permissions, tickets = tickets, loading = false)
                }
                loadCount()
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = adminErrorMessage(error)) }
            }
        }
    }

    fun openTicket(id: Long) {
        viewModelScope.launch {
            _state.update { it.copy(ticketLoading = true, ticketError = null, ticket = null) }
            try {
                _state.update { it.copy(ticket = repository.ticket(id), ticketLoading = false) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(ticketLoading = false, ticketError = adminErrorMessage(error))
                }
            }
        }
    }

    fun reply(id: Long, message: String) {
        val text = message.trim()
        if (text.isEmpty() || _state.value.sending) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true, ticketError = null) }
            try {
                repository.reply(id, text)
                // Перечитываем переписку целиком, а не дописываем сообщение
                // локально: сервер мог поменять и статус обращения.
                _state.update { it.copy(ticket = repository.ticket(id), sending = false) }
                refreshQuietly()
            } catch (error: Exception) {
                _state.update {
                    it.copy(sending = false, ticketError = adminErrorMessage(error))
                }
            }
        }
    }

    fun setStatus(id: Long, status: String) {
        viewModelScope.launch {
            _state.update { it.copy(sending = true, ticketError = null) }
            try {
                repository.setStatus(id, status)
                _state.update { it.copy(ticket = repository.ticket(id), sending = false) }
                refreshQuietly()
            } catch (error: Exception) {
                _state.update {
                    it.copy(sending = false, ticketError = adminErrorMessage(error))
                }
            }
        }
    }

    /** Обновить список и счётчик, ничего не показывая на экране обращения. */
    private fun refreshQuietly() {
        viewModelScope.launch {
            try {
                val tickets = repository.tickets(status = _state.value.filter.api)
                _state.update { it.copy(tickets = tickets) }
            } catch (_: Exception) {
            }
            loadCount()
        }
    }

    fun consumeInfo() {
        _state.update { it.copy(info = null) }
    }
}
