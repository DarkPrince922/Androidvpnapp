package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.api.dto.AdminCommentRequest
import com.darkprince.vpn.data.api.dto.AdminPaymentDto
import com.darkprince.vpn.data.api.dto.AdminUserDto
import com.darkprince.vpn.data.api.dto.AdminWithdrawalDetailDto
import com.darkprince.vpn.data.api.dto.AdminWithdrawalDto
import com.darkprince.vpn.data.api.dto.BroadcastButtonDto
import com.darkprince.vpn.data.api.dto.BroadcastCreateRequest
import com.darkprince.vpn.data.api.dto.BroadcastDto
import com.darkprince.vpn.data.api.dto.BroadcastFilterDto
import com.darkprince.vpn.data.repo.adminErrorMessage
import com.darkprince.vpn.data.repo.cabinetMessage
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminToolsUiState(
    val loading: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,

    // выводы
    /** null — все; иначе статус заявки. */
    val withdrawalStatus: String? = "pending",
    val withdrawals: List<AdminWithdrawalDto> = emptyList(),
    val pendingCount: Int = 0,
    val pendingTotalKopeks: Long = 0,
    /** Разбор риска по открытой заявке. */
    val withdrawalDetail: AdminWithdrawalDetailDto? = null,
    /** Своя проверка открытой заявки: друзья, устройства, реквизиты. */
    val withdrawalCheck: WithdrawalCheck? = null,

    // платежи
    val paymentQuery: String = "",
    val paymentStatus: String = "all",
    val paymentPeriod: String = "7d",
    val payments: List<AdminPaymentDto> = emptyList(),
    val paymentsTotal: Int = 0,

    // рассылки
    val filters: List<BroadcastFilterDto> = emptyList(),
    val buttons: List<BroadcastButtonDto> = emptyList(),
    val broadcasts: List<BroadcastDto> = emptyList(),
    /** Рассылка ушла — форму можно очистить. */
    val broadcastSent: Boolean = false,
)

/**
 * Выводы, платежи и рассылки в панели админа. Отдельно от [AdminViewModel],
 * чтобы не раздувать его: у этих разделов своё состояние и свои запросы.
 */
class AdminToolsViewModel : ViewModel() {
    private val api get() = ServiceLocator.apiClient.api

    private val _state = MutableStateFlow(AdminToolsUiState())
    val state: StateFlow<AdminToolsUiState> = _state

    private var progressJob: Job? = null

    fun load(section: AdminSection) {
        when (section) {
            AdminSection.WITHDRAWALS -> loadWithdrawals()
            AdminSection.PAYMENTS -> searchPayments()
            AdminSection.BROADCASTS -> loadBroadcasts()
            else -> Unit
        }
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                block()
                _state.update { it.copy(loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = adminErrorMessage(e)) }
            }
        }
    }

    // --- Выводы ---

    fun setWithdrawalStatus(status: String?) {
        _state.update { it.copy(withdrawalStatus = status, withdrawalDetail = null, withdrawalCheck = null) }
        loadWithdrawals()
    }

    private fun loadWithdrawals() = run {
        val list = api.adminWithdrawals(status = _state.value.withdrawalStatus)
        _state.update {
            it.copy(
                withdrawals = list.items,
                pendingCount = list.pendingCount,
                pendingTotalKopeks = list.pendingTotalKopeks,
            )
        }
    }

    /**
     * Флаги риска — бот считает их сам; показываем перед решением. Рядом
     * своя проверка: то, что админ иначе смотрел бы руками по карточкам.
     */
    fun openWithdrawal(id: Long) {
        if (_state.value.withdrawalDetail?.id == id) {
            _state.update { it.copy(withdrawalDetail = null, withdrawalCheck = null) }
            return
        }
        val item = _state.value.withdrawals.firstOrNull { it.id == id }
        _state.update {
            it.copy(withdrawalCheck = item?.let { w -> WithdrawalCheck(withdrawalId = w.id, loading = true) })
        }
        viewModelScope.launch {
            runCatching { api.adminWithdrawal(id) }
                .onSuccess { detail -> _state.update { it.copy(withdrawalDetail = detail) } }
                .onFailure { e -> _state.update { it.copy(error = adminErrorMessage(e)) } }
        }
        if (item != null) viewModelScope.launch {
            val check = checkWithdrawal(item)
            _state.update { if (it.withdrawalCheck?.withdrawalId == id) it.copy(withdrawalCheck = check) else it }
        }
    }

    /**
     * Устройства человека по всем его подпискам: у каждой подписки свой
     * список в панели, и запрос без номера подписки видит только один.
     */
    private suspend fun allHwids(userId: Long): Set<String> = coroutineScope {
        val subs = api.adminUser(userId).allSubscriptions
        val lists = if (subs.isEmpty()) {
            listOf(api.adminUserDevices(userId).devices)
        } else {
            subs.map { sub ->
                async { runCatching { api.adminUserDevices(userId, sub.id).devices }.getOrNull().orEmpty() }
            }.awaitAll()
        }
        lists.flatten().map { it.hwid }.filter { it.isNotBlank() }.toSet()
    }

    private suspend fun checkWithdrawal(item: AdminWithdrawalDto): WithdrawalCheck = coroutineScope {
        val friendsCall = async { runCatching { api.adminUserReferrals(item.userId) } }
        val ownDevicesCall = async { runCatching { allHwids(item.userId) } }
        val othersCall = async { runCatching { api.adminWithdrawals(status = null, limit = 100).items } }

        val friendsList = friendsCall.await().getOrElse { e ->
            return@coroutineScope WithdrawalCheck(
                withdrawalId = item.id,
                unavailable = if (e is retrofit2.HttpException && e.code() == 403) {
                    "Нет права смотреть людей — проверка недоступна"
                } else {
                    "Не удалось загрузить приглашённых: ${adminErrorMessage(e)}"
                },
            )
        }
        val friends = friendsList.users
        val warnings = mutableListOf<CheckWarning>()

        // общие устройства: «друг» сидит с того же телефона, что и пригласивший
        val ownHwids = ownDevicesCall.await().getOrNull().orEmpty()
        val checked = friends.take(DEVICE_CHECK_LIMIT)
        if (ownHwids.isNotEmpty() && checked.isNotEmpty()) {
            val shared = checked.chunked(6).flatMap { chunk ->
                chunk.map { friend ->
                    async {
                        val hwids = runCatching { allHwids(friend.id) }.getOrNull().orEmpty()
                        friend.takeIf { hwids.any { it in ownHwids } }
                    }
                }.awaitAll()
            }.filterNotNull()
            if (shared.isNotEmpty()) {
                warnings += CheckWarning(
                    "Общие устройства с пригласившим: ${shared.joinToString { it.displayName }}",
                    severe = true,
                )
            }
        }

        // те же реквизиты у другого человека — один получатель на несколько аккаунтов
        val details = item.paymentDetails?.let(::normalizeDetails)
        if (!details.isNullOrEmpty()) {
            val others = othersCall.await().getOrNull().orEmpty()
                .filter { it.userId != item.userId && it.paymentDetails?.let(::normalizeDetails) == details }
                .distinctBy { it.userId }
            if (others.isNotEmpty()) {
                warnings += CheckWarning(
                    "Эти реквизиты есть в заявках: ${others.joinToString { it.who }}",
                    severe = true,
                )
            }
        }

        if (friends.size >= 3) {
            val (day, sameDay) = friends.mapNotNull { it.createdAt?.take(10) }
                .groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.toPair() ?: ("" to 0)
            if (sameDay >= 3 && sameDay * 2 >= friends.size) {
                warnings += CheckWarning("$sameDay из ${friends.size} зарегистрированы в один день ($day)")
            }
            val noPurchase = friends.count { it.purchaseCount == 0 }
            if (noPurchase * 2 >= friends.size) {
                warnings += CheckWarning("$noPurchase из ${friends.size} ничего не купили")
            }
            val idle = friends.count { it.lastActivity == null }
            if (idle * 2 >= friends.size) {
                warnings += CheckWarning("$idle из ${friends.size} не заходили после регистрации")
            }
        }

        WithdrawalCheck(
            withdrawalId = item.id,
            friends = friends,
            totalFriends = friendsList.total,
            devicesChecked = if (ownHwids.isEmpty()) 0 else checked.size,
            warnings = warnings,
        )
    }

    /** Карта с пробелами и без, кошелёк в разном регистре — один и тот же получатель. */
    private fun normalizeDetails(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }

    fun approveWithdrawal(id: Long, comment: String) =
        act("Заявка одобрена. Переведите деньги и отметьте «Выплачено»") {
            api.adminApproveWithdrawal(id, AdminCommentRequest(comment.trim().ifBlank { null }))
        }

    fun rejectWithdrawal(id: Long, comment: String) = act("Заявка отклонена, деньги вернулись на реферальный баланс") {
        api.adminRejectWithdrawal(id, AdminCommentRequest(comment.trim().ifBlank { null }))
    }

    fun completeWithdrawal(id: Long) = act("Отмечено: деньги выплачены") {
        api.adminCompleteWithdrawal(id)
    }

    private fun act(success: String, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(busy = false, info = success, withdrawalDetail = null, withdrawalCheck = null) }
                loadWithdrawals()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.cabinetMessage()) }
            }
        }
    }

    // --- Платежи ---

    fun setPaymentQuery(query: String) {
        _state.update { it.copy(paymentQuery = query) }
    }

    fun setPaymentStatus(status: String) {
        _state.update { it.copy(paymentStatus = status) }
        searchPayments()
    }

    fun setPaymentPeriod(period: String) {
        _state.update { it.copy(paymentPeriod = period) }
        searchPayments()
    }

    /** Поиск сразу по клиенту — из его карточки. Период — за всё время. */
    fun searchFor(query: String) {
        _state.update { it.copy(paymentQuery = query, paymentStatus = "all", paymentPeriod = "all") }
        searchPayments()
    }

    fun searchPayments() = run {
        val s = _state.value
        val list = api.adminSearchPayments(
            search = s.paymentQuery.trim().ifBlank { null },
            status = s.paymentStatus,
            period = s.paymentPeriod,
        )
        _state.update { it.copy(payments = list.items, paymentsTotal = list.total) }
    }

    fun checkPayment(payment: AdminPaymentDto) {
        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                val result = api.adminCheckPayment(payment.method, payment.id)
                _state.update { state ->
                    state.copy(
                        busy = false,
                        info = result.message.ifBlank { "Проверено" },
                        payments = state.payments.map { p ->
                            if (p.id == payment.id && p.method == payment.method) result.payment ?: p else p
                        },
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.cabinetMessage()) }
            }
        }
    }

    // --- Рассылки ---

    private fun loadBroadcasts() = run {
        kotlinx.coroutines.coroutineScope {
            val filters = async { api.adminBroadcastFilters() }
            val buttons = async { runCatching { api.adminBroadcastButtons().buttons }.getOrDefault(emptyList()) }
            val list = async { api.adminBroadcasts().items }
            val f = filters.await()
            _state.update {
                it.copy(
                    filters = f.filters + f.tariffFilters + f.customFilters,
                    buttons = buttons.await(),
                    broadcasts = list.await(),
                )
            }
        }
        watchProgress()
    }

    fun sendBroadcast(target: String, text: String, buttons: List<String>) {
        if (text.isBlank()) {
            _state.update { it.copy(error = "Напишите текст рассылки") }
            return
        }
        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                val created = api.adminCreateBroadcast(BroadcastCreateRequest(target, text.trim(), buttons))
                _state.update {
                    it.copy(
                        busy = false,
                        broadcastSent = true,
                        info = "Рассылка запущена: ${created.totalCount.takeIf { c -> c > 0 }?.let { c -> "$c получателей" } ?: "считаем получателей"}",
                        broadcasts = listOf(created) + it.broadcasts.filter { b -> b.id != created.id },
                    )
                }
                watchProgress()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.cabinetMessage()) }
            }
        }
    }

    fun consumeBroadcastSent() {
        _state.update { it.copy(broadcastSent = false) }
    }

    fun stopBroadcast(id: Long) {
        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                val stopped = api.adminStopBroadcast(id)
                _state.update { s ->
                    s.copy(busy = false, info = "Рассылка останавливается", broadcasts = s.broadcasts.map { if (it.id == id) stopped else it })
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.cabinetMessage()) }
            }
        }
    }

    /** Пока рассылка идёт — раз в несколько секунд обновляем её счётчики. */
    private fun watchProgress() {
        if (_state.value.broadcasts.none { it.running }) return
        if (progressJob?.isActive == true) return
        progressJob = viewModelScope.launch {
            while (true) {
                delay(4_000)
                val list = runCatching { api.adminBroadcasts().items }.getOrNull() ?: continue
                _state.update { it.copy(broadcasts = list) }
                if (list.none { it.running }) break
            }
        }
    }

    fun consumeInfo() {
        _state.update { it.copy(info = null, error = null) }
    }
}

/** Предупреждение проверки; [severe] — почти наверняка обман. */
data class CheckWarning(val text: String, val severe: Boolean = false)

data class WithdrawalCheck(
    val withdrawalId: Long,
    val loading: Boolean = false,
    /** Проверку провести нельзя — почему. */
    val unavailable: String? = null,
    val friends: List<AdminUserDto> = emptyList(),
    val totalFriends: Int = 0,
    /** Скольких друзей сверили по устройствам. */
    val devicesChecked: Int = 0,
    val warnings: List<CheckWarning> = emptyList(),
)

/** Устройства — отдельный запрос на каждого друга; больше не ждём. */
private const val DEVICE_CHECK_LIMIT = 30
