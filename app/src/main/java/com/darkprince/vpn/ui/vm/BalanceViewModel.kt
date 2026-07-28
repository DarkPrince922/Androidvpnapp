package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.api.dto.PaymentMethodDto
import com.darkprince.vpn.data.api.dto.TransactionDto
import com.darkprince.vpn.data.repo.userMessage
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PendingPayment(val method: String, val paymentId: String, val url: String?)

data class BalanceUiState(
    val balanceKopeks: Long? = null,
    val methods: List<PaymentMethodDto> = emptyList(),
    val transactions: List<TransactionDto> = emptyList(),
    val loading: Boolean = false,
    val creatingPayment: Boolean = false,
    val pendingPayment: PendingPayment? = null,
    /** URL оплаты, который нужно открыть в браузере. */
    val openUrl: String? = null,
    val error: String? = null,
    val info: String? = null,
)

class BalanceViewModel : ViewModel() {
    private val repo = ServiceLocator.balanceRepository

    private val _state = MutableStateFlow(BalanceUiState())
    val state: StateFlow<BalanceUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val balance = repo.balance()
                val methods = repo.paymentMethods()
                val transactions = try {
                    repo.transactions()
                } catch (_: Exception) {
                    emptyList()
                }
                val kopeks = balance.balanceKopeks
                    ?: balance.balanceRubles?.let { (it * 100).toLong() }
                _state.value = _state.value.copy(
                    balanceKopeks = kopeks,
                    methods = methods,
                    transactions = transactions,
                    loading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.userMessage())
            }
        }
    }

    fun topup(amountRubles: Long, method: PaymentMethodDto) {
        if (amountRubles <= 0) {
            _state.value = _state.value.copy(error = "Введите сумму")
            return
        }
        _state.value = _state.value.copy(creatingPayment = true, error = null, info = null)
        viewModelScope.launch {
            try {
                val response = repo.createTopup(amountRubles * 100, method.effectiveId)
                val url = response.paymentUrl
                _state.value = _state.value.copy(
                    creatingPayment = false,
                    pendingPayment = response.paymentId?.let {
                        PendingPayment(method.effectiveId, it, url)
                    },
                    openUrl = url,
                    info = if (url == null) (response.message ?: "Платёж создан") else null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(creatingPayment = false, error = e.userMessage())
            }
        }
    }

    fun consumeOpenUrl() {
        _state.value = _state.value.copy(openUrl = null)
    }

    fun checkPending() {
        val pending = _state.value.pendingPayment ?: return
        viewModelScope.launch {
            repo.checkPending(pending.method, pending.paymentId)
            refresh()
            _state.value = _state.value.copy(info = "Статус платежа обновлён")
        }
    }
}
