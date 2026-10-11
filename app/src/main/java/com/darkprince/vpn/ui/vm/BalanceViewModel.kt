package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.api.dto.PaymentMethodDto
import com.darkprince.vpn.data.api.dto.TransactionDto
import com.darkprince.vpn.data.repo.PendingPurchase
import com.darkprince.vpn.data.repo.PurchaseResult
import com.darkprince.vpn.data.repo.userMessage
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** Покупка, ради которой пополняют: оплатится сама после зачисления. */
    val pendingPurchase: PendingPurchase? = null,
    /** Сейчас оплачиваем отложенную покупку. */
    val completingPurchase: Boolean = false,
    /** URL оплаты, который нужно открыть в браузере. */
    val openUrl: String? = null,
    val error: String? = null,
    val info: String? = null,
    /** Растёт после принятого промокода — экран очищает поле. */
    val promoAccepted: Int = 0,
    /** Код на дни, а подписок несколько — ждём, к какой применить. */
    val promoChoice: com.darkprince.vpn.data.repo.PromoResult.ChooseSubscription? = null,
)

private val BALANCE_EVENTS = setOf("balance.topup", "balance.change", "payment.received")

class BalanceViewModel : ViewModel() {
    private val repo = ServiceLocator.balanceRepository

    private val _state = MutableStateFlow(BalanceUiState())
    val state: StateFlow<BalanceUiState> = _state

    init {
        refresh()
        // Деньги зачислены — сокет кабинета говорит об этом сразу. Опрос
        // после оплаты остаётся: он нужен, если сокет не поднялся.
        // связь вернулась после неудачной загрузки — пробуем снова сами
        viewModelScope.launch {
            com.darkprince.vpn.data.api.ServiceHealth.backOnline.collect {
                if (_state.value.error != null || _state.value.balanceKopeks == null) refresh()
            }
        }
        viewModelScope.launch {
            com.darkprince.vpn.data.api.CabinetEvents.events.collect { event ->
                if (event.type in BALANCE_EVENTS) {
                    watchJob?.cancel()
                    _state.value = _state.value.copy(pendingPayment = null)
                    refresh()
                }
            }
        }
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
                val pending = ServiceLocator.pendingPurchases.load()
                _state.value = _state.value.copy(
                    balanceKopeks = kopeks,
                    methods = methods,
                    transactions = transactions,
                    loading = false,
                    pendingPurchase = pending,
                )
                if (pending != null && kopeks != null) completePurchase(pending, kopeks)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.userMessage())
            }
        }
    }

    fun topup(amountRubles: Long, method: PaymentMethodDto, option: String? = null) {
        if (amountRubles <= 0) {
            _state.value = _state.value.copy(error = "Введите сумму")
            return
        }
        // пределы способа знаем заранее — касса всё равно откажет, но непонятно
        val kopeks = amountRubles * 100
        method.minAmountKopeks?.takeIf { kopeks < it }?.let {
            _state.value = _state.value.copy(error = "Минимум для этого способа — ${com.darkprince.vpn.ui.screens.formatKopeks(it)}")
            return
        }
        method.maxAmountKopeks?.takeIf { it > 0 && kopeks > it }?.let {
            _state.value = _state.value.copy(error = "Максимум для этого способа — ${com.darkprince.vpn.ui.screens.formatKopeks(it)}")
            return
        }
        _state.value = _state.value.copy(creatingPayment = true, error = null, info = null)
        viewModelScope.launch {
            try {
                val response = repo.createTopup(amountRubles * 100, method.effectiveId, option)
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

    /**
     * Денег стало достаточно — оплачиваем то, ради чего пополняли.
     *
     * Сначала смотрим, не сделал ли это уже сам бот: у него есть своя
     * автопокупка после пополнения. Если срок подписки успел измениться,
     * второй раз не платим.
     */
    private suspend fun completePurchase(pending: PendingPurchase, balanceKopeks: Long) {
        if (balanceKopeks < pending.priceKopeks || _state.value.completingPurchase) return
        _state.value = _state.value.copy(completingPurchase = true, error = null)
        val subs = ServiceLocator.subscriptionRepository
        val current = try {
            subs.subscriptions()
        } catch (_: Exception) {
            null
        }
        if (current == null) {
            // не знаем, что с подписками, — платить вслепую нельзя
            _state.value = _state.value.copy(completingPurchase = false)
            return
        }
        if (pending.alreadyDone(current)) {
            ServiceLocator.pendingPurchases.clear()
            _state.value = _state.value.copy(
                completingPurchase = false,
                pendingPurchase = null,
                info = "Готово: «${pending.title}» уже оплачено",
            )
            return
        }
        val result = when (pending.kind) {
            "renew" -> subs.renewChecked(pending.periodDays, pending.subscriptionId)
            "switch" -> subs.switchTariff(pending.subscriptionId ?: 0L, pending.tariffId ?: 0L, pending.periodDays)
            else -> subs.purchaseTariffChecked(pending.tariffId ?: 0L, pending.periodDays)
        }
        when (result) {
            PurchaseResult.Ok -> {
                ServiceLocator.pendingPurchases.clear()
                _state.value = _state.value.copy(
                    completingPurchase = false,
                    pendingPurchase = null,
                    info = "Готово: «${pending.title}» оплачено с баланса",
                )
                refresh()
            }
            is PurchaseResult.NotEnough -> {
                // цена могла вырасти, пока платили, — ждём ещё пополнения
                val updated = pending.copy(missingKopeks = result.missingKopeks ?: pending.missingKopeks)
                ServiceLocator.pendingPurchases.save(updated)
                _state.value = _state.value.copy(completingPurchase = false, pendingPurchase = updated)
            }
            is PurchaseResult.Failed -> {
                ServiceLocator.pendingPurchases.clear()
                _state.value = _state.value.copy(
                    completingPurchase = false,
                    pendingPurchase = null,
                    error = "Деньги зачислены, но оформить «${pending.title}» не вышло: " +
                        "${result.message}. Попробуйте на экране «Тарифы».",
                )
            }
        }
    }

    /** Человек передумал: пополнение останется просто пополнением. */
    fun cancelPendingPurchase() {
        viewModelScope.launch {
            ServiceLocator.pendingPurchases.clear()
            _state.value = _state.value.copy(pendingPurchase = null)
        }
    }

    fun consumeOpenUrl() {
        _state.value = _state.value.copy(openUrl = null)
        // после ухода на страницу оплаты ждём зачисления в фоне
        startPaymentWatch()
    }

    /**
     * Пока висит неоплаченный счёт, приложение само опрашивает статус и
     * обновляет баланс — иначе после оплаты приходится ждать и жать кнопку.
     * Интервал растёт, чтобы не долбить сервер: сначала часто, потом реже.
     */
    private var watchJob: Job? = null

    private fun startPaymentWatch() {
        val pending = _state.value.pendingPayment ?: return
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            val startBalance = _state.value.balanceKopeks
            repeat(40) { attempt ->
                delay(if (attempt < 12) 5_000 else 15_000)
                repo.checkPending(pending.method, pending.paymentId)
                val balance = try {
                    repo.balance()
                } catch (_: Exception) {
                    return@repeat
                }
                val kopeks = balance.balanceKopeks
                    ?: balance.balanceRubles?.let { (it * 100).toLong() }
                if (kopeks != null && kopeks != startBalance) {
                    _state.value = _state.value.copy(
                        balanceKopeks = kopeks,
                        pendingPayment = null,
                        info = "Баланс пополнен",
                    )
                    refresh()
                    return@launch
                }
            }
        }
    }

    /** Вызывается при возвращении на экран — например, из окна оплаты. */
    fun onScreenResumed() {
        refresh()
        val pending = _state.value.pendingPayment ?: return
        viewModelScope.launch {
            repo.checkPending(pending.method, pending.paymentId)
            refresh()
        }
    }

    override fun onCleared() {
        watchJob?.cancel()
        super.onCleared()
    }

    fun activatePromo(code: String, subscriptionId: Long? = null) {
        if (code.isBlank()) return
        _state.value = _state.value.copy(error = null, info = null, promoChoice = null)
        viewModelScope.launch {
            when (val result = repo.activatePromocode(code, subscriptionId)) {
                is com.darkprince.vpn.data.repo.PromoResult.Ok -> {
                    _state.value = _state.value.copy(info = result.message, promoAccepted = _state.value.promoAccepted + 1)
                    refresh()
                }
                is com.darkprince.vpn.data.repo.PromoResult.Failed ->
                    _state.value = _state.value.copy(error = result.message)
                is com.darkprince.vpn.data.repo.PromoResult.ChooseSubscription ->
                    _state.value = _state.value.copy(promoChoice = result)
            }
        }
    }

    fun dismissPromoChoice() {
        _state.value = _state.value.copy(promoChoice = null)
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
