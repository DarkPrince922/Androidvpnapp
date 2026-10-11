package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.api.dto.ReferralEarningsDto
import com.darkprince.vpn.data.api.dto.ReferralInfoResponse
import com.darkprince.vpn.data.api.dto.ReferralListDto
import com.darkprince.vpn.data.api.dto.ReferralTermsDto
import com.darkprince.vpn.data.api.dto.WithdrawalBalanceDto
import com.darkprince.vpn.data.api.dto.WithdrawalDto
import com.darkprince.vpn.data.repo.userMessage
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReferralUiState(
    val loading: Boolean = true,
    val info: ReferralInfoResponse? = null,
    val terms: ReferralTermsDto? = null,
    val invited: ReferralListDto? = null,
    val earnings: ReferralEarningsDto? = null,
    val withdrawal: WithdrawalBalanceDto? = null,
    val withdrawals: List<WithdrawalDto> = emptyList(),
    val busy: Boolean = false,
    /** Заявка ушла — форму можно закрыть. */
    val requestSent: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class ReferralViewModel : ViewModel() {
    private val repo = ServiceLocator.referralRepository

    private val _state = MutableStateFlow(ReferralUiState())
    val state: StateFlow<ReferralUiState> = _state

    init {
        refresh()
        viewModelScope.launch {
            com.darkprince.vpn.data.api.ServiceHealth.backOnline.collect { if (_state.value.info == null || _state.value.error != null) refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val info = async { runCatching { repo.info() } }
            val terms = async { repo.terms() }
            val invited = async { repo.invited() }
            val earnings = async { repo.earnings() }
            val withdrawal = async { repo.withdrawal() }
            val withdrawals = async { repo.withdrawals() }
            val infoResult = info.await()
            _state.update {
                it.copy(
                    loading = false,
                    info = infoResult.getOrNull() ?: it.info,
                    terms = terms.await(),
                    invited = invited.await(),
                    earnings = earnings.await(),
                    withdrawal = withdrawal.await(),
                    withdrawals = withdrawals.await(),
                    error = if (infoResult.isFailure && it.info == null) {
                        (infoResult.exceptionOrNull() as? Exception)?.userMessage()
                    } else null,
                )
            }
        }
    }

    fun requestWithdrawal(rubles: Long, details: String) {
        val balance = _state.value.withdrawal ?: return
        val kopeks = rubles * 100
        val error = when {
            rubles <= 0 -> "Введите сумму"
            kopeks < balance.minAmountKopeks ->
                "Минимум для вывода — ${com.darkprince.vpn.ui.screens.formatKopeks(balance.minAmountKopeks)}"
            kopeks > balance.availableTotal ->
                "Доступно не больше ${com.darkprince.vpn.ui.screens.formatKopeks(balance.availableTotal)}"
            details.trim().length < 5 -> "Укажите реквизиты: карту или телефон для СБП и банк"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error, message = null) }
            return
        }
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            val failure = repo.requestWithdrawal(kopeks, details)
            _state.update {
                if (failure == null) {
                    it.copy(busy = false, requestSent = true, message = "Заявка отправлена. Деньги придут после проверки")
                } else {
                    it.copy(busy = false, error = failure)
                }
            }
            if (failure == null) refresh()
        }
    }

    fun consumeRequestSent() {
        _state.update { it.copy(requestSent = false) }
    }

    fun cancelWithdrawal(id: Long) {
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            val failure = repo.cancelWithdrawal(id)
            _state.update {
                if (failure == null) it.copy(busy = false, message = "Заявка отменена")
                else it.copy(busy = false, error = failure)
            }
            if (failure == null) refresh()
        }
    }
}
