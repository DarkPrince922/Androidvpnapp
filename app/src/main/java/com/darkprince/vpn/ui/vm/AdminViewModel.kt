package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.api.dto.AdminDashboardDto
import com.darkprince.vpn.data.api.dto.AdminDeviceDto
import com.darkprince.vpn.data.api.dto.AdminPermissionsDto
import com.darkprince.vpn.data.api.dto.AdminTicketDetailDto
import com.darkprince.vpn.data.api.dto.AdminTicketDto
import com.darkprince.vpn.data.api.dto.AdminTransactionDto
import com.darkprince.vpn.data.api.dto.AdminUserDto
import com.darkprince.vpn.data.repo.AdminRepository
import com.darkprince.vpn.data.repo.adminErrorMessage
import com.darkprince.vpn.data.repo.rejectedParameter
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Разделы панели.
 *
 * Переключатель наверху, а не отдельные экраны: обращения открывают чаще
 * всего, и прятать их за лишним касанием ради стройности навигации значит
 * усложнить самое частое действие.
 */
enum class AdminSection(val title: String, val permission: String) {
    SUMMARY("Сводка", AdminRepository.STATS_READ),
    TICKETS("Обращения", AdminRepository.TICKETS_READ),
    PEOPLE("Люди", AdminRepository.USERS_READ),
}

/**
 * Порядок в списке людей.
 *
 * Значения — те, что понимает кабинет; свои придумывать нельзя, сортировка
 * идёт в базе. Направление у каждой задано на сервере и по смыслу очевидно:
 * баланс и траты по убыванию, а дата окончания по возрастанию — «скоро
 * истечёт» имеет смысл только так.
 */
enum class PeopleSort(val api: String?, val title: String) {
    // Пустое значение — умолчание кабинета, дата регистрации по убыванию
    NEW(null, "Новые"),
    EXPIRING("subscription_end_date", "Скоро истекут"),
    BALANCE("balance", "По балансу"),
    SPENT("total_spent", "По тратам"),
    ACTIVITY("last_activity", "По активности");

    /**
     * Тот же порядок своими силами.
     *
     * Нужен, когда панель не знает такой сортировки и отвечает 422: у разных
     * версий бота набор различается. Работает по тем полям, что уже пришли в
     * списке, поэтому «по активности» так не отсортировать — этого поля в
     * выдаче нет, и придумывать его нельзя.
     */
    fun sortLocally(people: List<AdminUserDto>): List<AdminUserDto>? = when (this) {
        NEW -> people
        // Без подписки срока нет — таких в конец, а не в начало с нулём
        EXPIRING -> people.sortedWith(
            compareBy<AdminUserDto> { !it.hasSubscription }.thenBy { it.daysRemaining }
        )
        BALANCE -> people.sortedByDescending { it.balanceKopeks }
        SPENT -> people.sortedByDescending { it.totalSpentKopeks }
        ACTIVITY -> null
    }
}

/** Фильтр по состоянию подписки. */
enum class PeopleFilter(val api: String?, val title: String) {
    ANY(null, "Все"),
    ACTIVE("active", "С подпиской"),
    TRIAL("trial", "Пробные"),
    EXPIRED("expired", "Истекшие"),
}

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
    val section: AdminSection = AdminSection.TICKETS,
    val filter: TicketFilter = TicketFilter.ACTIVE,
    val dashboard: AdminDashboardDto? = null,
    val people: List<AdminUserDto> = emptyList(),
    val peopleTotal: Int = 0,
    val peopleSort: PeopleSort = PeopleSort.NEW,
    val peopleFilter: PeopleFilter = PeopleFilter.ANY,
    val loadingMore: Boolean = false,
    /** Порядок пришлось задать своими силами — страниц в этом режиме нет. */
    val sortedLocally: Boolean = false,
    val search: String = "",
    val tickets: List<AdminTicketDto> = emptyList(),
    val openCount: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val ticket: AdminTicketDetailDto? = null,
    val ticketLoading: Boolean = false,
    val sending: Boolean = false,
    val ticketError: String? = null,
    val info: String? = null,
    // карточка человека
    val person: AdminUserDto? = null,
    val devices: List<AdminDeviceDto> = emptyList(),
    val deviceLimit: Int = 0,
    val transactions: List<AdminTransactionDto> = emptyList(),
    val personLoading: Boolean = false,
) {
    val canReply: Boolean get() = permissions.allows(AdminRepository.TICKETS_REPLY)
    val canClose: Boolean get() = permissions.allows(AdminRepository.TICKETS_CLOSE)
    val canReadTickets: Boolean get() = permissions.allows(AdminRepository.TICKETS_READ)
    val canAddBalance: Boolean get() = permissions.allows(AdminRepository.USERS_BALANCE)
    val canExtend: Boolean get() = permissions.allows(AdminRepository.USERS_SUBSCRIPTION)
    val canDropDevice: Boolean get() = permissions.allows(AdminRepository.USERS_EDIT)
    val canWrite: Boolean get() = permissions.allows(AdminRepository.USERS_SEND_MESSAGE)
    val canRestartNode: Boolean get() = permissions.allows(AdminRepository.REMNAWAVE_MANAGE)
    val canCreatePromo: Boolean get() = permissions.allows(AdminRepository.PROMOCODES_CREATE)

    /** Разделы, которые роль вообще позволяет открыть. */
    /** Есть ли ещё страницы. */
    val hasMorePeople: Boolean get() = !sortedLocally && people.size < peopleTotal

    val sections: List<AdminSection>
        get() = AdminSection.entries.filter { permissions.allows(it.permission) }
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

    fun setSection(section: AdminSection) {
        if (_state.value.section == section) return
        // Итог прошлого действия к новому разделу отношения не имеет.
        _state.update { it.copy(section = section, error = null, info = null) }
        loadSection()
    }

    fun setSearch(query: String) {
        _state.update { it.copy(search = query) }
    }

    fun setPeopleSort(sort: PeopleSort) {
        if (_state.value.peopleSort == sort) return
        _state.update { it.copy(peopleSort = sort) }
        searchPeople()
    }

    fun setPeopleFilter(filter: PeopleFilter) {
        if (_state.value.peopleFilter == filter) return
        _state.update { it.copy(peopleFilter = filter) }
        searchPeople()
    }

    /** Поиск по людям: запускается кнопкой, а не на каждую букву. */
    fun searchPeople() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            loadPeople(offset = 0)
        }
    }

    /**
     * Следующая страница.
     *
     * Дозагружаем к тому, что уже показано, а не перелистываем: на телефоне
     * листать вверх привычнее, чем прыгать по номерам страниц, а место в
     * списке при этом не теряется.
     */
    fun loadMorePeople() {
        val state = _state.value
        if (state.loading || state.loadingMore || !state.hasMorePeople) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true, error = null) }
            loadPeople(offset = state.people.size)
        }
    }

    private suspend fun loadPeople(offset: Int, limit: Int = AdminRepository.PAGE) {
        val state = _state.value
        try {
            val page = repository.users(
                search = state.search,
                sortBy = state.peopleSort.api,
                subscriptionStatus = state.peopleFilter.api,
                offset = offset,
                limit = limit,
            )
            _state.update {
                it.copy(
                    people = if (offset == 0) page.users else it.people + page.users,
                    peopleTotal = page.total,
                    sortedLocally = false,
                    loading = false,
                    loadingMore = false,
                )
            }
        } catch (error: Exception) {
            if (rejectedParameter(error, "sort_by")) {
                sortPeopleLocally(error)
            } else {
                _state.update {
                    it.copy(loading = false, loadingMore = false, error = adminErrorMessage(error))
                }
            }
        }
    }

    /**
     * Запасной путь: панель не знает такой сортировки.
     *
     * Берём одной выдачей столько, сколько кабинет отдаёт за раз, и
     * раскладываем сами. Сортировать одну страницу из тридцати было бы
     * обманом — «скоро истекут» показывало бы ближайших только среди тех
     * тридцати, кто попал в выдачу по другому признаку. Поэтому здесь
     * страниц нет вовсе, и мы честно говорим, что список ограничен.
     */
    private suspend fun sortPeopleLocally(cause: Throwable) {
        val state = _state.value
        val sort = state.peopleSort
        try {
            val page = repository.users(
                search = state.search,
                sortBy = null,
                subscriptionStatus = state.peopleFilter.api,
                offset = 0,
                limit = AdminRepository.MAX_PAGE,
            )
            val sorted = sort.sortLocally(page.users)
            if (sorted == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        error = "Панель не поддерживает сортировку «${sort.title}»",
                    )
                }
                return
            }
            _state.update {
                it.copy(
                    people = sorted,
                    peopleTotal = page.total,
                    sortedLocally = true,
                    loading = false,
                    loadingMore = false,
                    error = null,
                )
            }
        } catch (_: Exception) {
            // запасной путь тоже не прошёл — показываем исходную причину
            _state.update {
                it.copy(loading = false, loadingMore = false, error = adminErrorMessage(cause))
            }
        }
    }

    fun setFilter(filter: TicketFilter) {
        if (_state.value.filter == filter) return
        _state.update { it.copy(filter = filter) }
        loadSection()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                // Права спрашиваем заново при каждом открытии вкладки: роль
                // могли снять в панели минуту назад.
                val permissions = repository.permissions()
                // Раздел, который роль не позволяет, молча заменяем первым
                // доступным: иначе человек с урезанными правами упирался бы
                // в пустой экран того, чего ему не дали.
                val allowed = AdminSection.entries.filter { permissions.allows(it.permission) }
                val section = _state.value.section.takeIf { it in allowed }
                    ?: allowed.firstOrNull()
                    ?: AdminSection.TICKETS
                _state.update { it.copy(permissions = permissions, section = section) }
                loadSection()
                loadCount()
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = adminErrorMessage(error)) }
            }
        }
    }

    /** Данные выбранного раздела. Соседние не трогаем — их спросят при заходе. */
    private fun loadSection() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                when (_state.value.section) {
                    AdminSection.SUMMARY ->
                        _state.update { it.copy(dashboard = repository.dashboard()) }

                    AdminSection.TICKETS -> {
                        val tickets = repository.tickets(status = _state.value.filter.api)
                        _state.update { it.copy(tickets = tickets) }
                    }

                    AdminSection.PEOPLE -> {
                        loadPeople(offset = 0)
                        return@launch
                    }
                }
                _state.update { it.copy(loading = false) }
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = adminErrorMessage(error)) }
            }
        }
    }

    /**
     * Начислить на баланс. Сумма в копейках, отрицательная списывает.
     * Возвращаем текст для подтверждения: человеку важно увидеть, что
     * получилось, а не только что «успешно».
     */
    /**
     * Перечитать ровно то, что человек уже пролистал.
     *
     * После правки баланса или продления список должен показать новое
     * значение, но сбрасывать его на первую страницу нельзя: догруженные
     * страницы пропали бы, и место в списке вместе с ними.
     */
    private fun refreshPeopleInPlace() {
        viewModelScope.launch {
            val shown = _state.value.people.size
            loadPeople(offset = 0, limit = shown.coerceAtLeast(AdminRepository.PAGE))
        }
    }

    fun addBalance(userId: Long, amountKopeks: Long) {
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            try {
                val result = repository.addBalance(userId, amountKopeks)
                _state.update {
                    it.copy(
                        sending = false,
                        info = "Баланс: %.2f ₽ → %.2f ₽".format(
                            result.oldBalanceKopeks / 100.0,
                            result.newBalanceKopeks / 100.0,
                        ),
                    )
                }
                refreshPeopleInPlace()
            } catch (error: Exception) {
                _state.update { it.copy(sending = false, error = adminErrorMessage(error)) }
            }
        }
    }

    fun extendSubscription(userId: Long, days: Int) {
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            try {
                repository.extendSubscription(userId, days)
                _state.update { it.copy(sending = false, info = "Подписка продлена на $days дн.") }
                refreshPeopleInPlace()
            } catch (error: Exception) {
                _state.update { it.copy(sending = false, error = adminErrorMessage(error)) }
            }
        }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
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

    // ---------- карточка человека ----------

    /**
     * Открыть карточку. Устройства и платежи спрашиваем сразу обоими
     * запросами: по отдельности карточка собиралась бы рывками.
     */
    fun openPerson(person: AdminUserDto) {
        _state.update {
            it.copy(
                person = person,
                devices = emptyList(),
                transactions = emptyList(),
                personLoading = true,
                error = null,
                info = null,
            )
        }
        viewModelScope.launch {
            val devices = runCatching { repository.devices(person.id) }.getOrNull()
            val transactions = runCatching { repository.transactions(person.id) }.getOrNull()
            _state.update {
                it.copy(
                    devices = devices?.devices.orEmpty(),
                    deviceLimit = devices?.deviceLimit ?: person.deviceLimit,
                    transactions = transactions.orEmpty(),
                    personLoading = false,
                )
            }
        }
    }

    fun closePerson() {
        _state.update {
            it.copy(person = null, devices = emptyList(), transactions = emptyList(), info = null)
        }
    }

    fun removeDevice(hwid: String) {
        val person = _state.value.person ?: return
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            try {
                repository.removeDevice(person.id, hwid)
                val devices = repository.devices(person.id)
                _state.update {
                    it.copy(
                        sending = false,
                        devices = devices.devices,
                        deviceLimit = devices.deviceLimit,
                        info = "Устройство отключено",
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(sending = false, error = adminErrorMessage(error)) }
            }
        }
    }

    fun sendMessage(text: String) {
        val person = _state.value.person ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            try {
                repository.sendMessage(person.id, body)
                _state.update { it.copy(sending = false, info = "Сообщение отправлено") }
            } catch (error: Exception) {
                _state.update { it.copy(sending = false, error = adminErrorMessage(error)) }
            }
        }
    }

    // ---------- узлы и промокоды ----------

    fun restartNode(uuid: String, name: String) {
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            try {
                repository.restartNode(uuid)
                _state.update {
                    it.copy(sending = false, info = "Узел «$name» перезапускается")
                }
            } catch (error: Exception) {
                _state.update { it.copy(sending = false, error = adminErrorMessage(error)) }
            }
        }
    }

    /**
     * Промокод. Код возвращаем в info, чтобы его можно было скопировать и
     * вставить в ответ на обращение — ради этого он обычно и создаётся.
     */
    fun createPromo(code: String, days: Int?, rubles: Int?, maxUses: Int) {
        val normalized = code.trim().uppercase()
        if (normalized.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            try {
                repository.createPromo(normalized, days, rubles, maxUses)
                _state.update { it.copy(sending = false, info = "Промокод $normalized создан") }
            } catch (error: Exception) {
                _state.update { it.copy(sending = false, error = adminErrorMessage(error)) }
            }
        }
    }

    fun consumeInfo() {
        _state.update { it.copy(info = null) }
    }
}
