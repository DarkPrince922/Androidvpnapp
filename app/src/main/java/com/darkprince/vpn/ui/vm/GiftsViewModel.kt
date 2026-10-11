package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.api.dto.GiftConfigDto
import com.darkprince.vpn.data.api.dto.GiftPurchaseRequest
import com.darkprince.vpn.data.api.dto.ReceivedGiftDto
import com.darkprince.vpn.data.api.dto.SentGiftDto
import com.darkprince.vpn.data.repo.GiftRepository
import com.darkprince.vpn.data.repo.userMessage
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Чем платить за подарок: с баланса или через кассу (способ и вариант). */
data class GiftPayment(val methodId: String?, val label: String) {
    val fromBalance: Boolean get() = methodId == null
}

/** Подарок куплен — вот код, которым делятся. */
data class GiftReady(val code: String, val title: String)

data class GiftsUiState(
    val loading: Boolean = true,
    val config: GiftConfigDto? = null,
    val sent: List<SentGiftDto> = emptyList(),
    val received: List<ReceivedGiftDto> = emptyList(),
    val busy: Boolean = false,
    val ready: GiftReady? = null,
    /** Оплата в кассе идёт: ждём, пока подарок станет готов. */
    val waitingPayment: Boolean = false,
    val openUrl: String? = null,
    val error: String? = null,
    val info: String? = null,
) {
    /** Способы оплаты: баланс и все варианты всех касс одним списком. */
    val payments: List<GiftPayment>
        get() {
            val list = mutableListOf(GiftPayment(null, "С баланса"))
            config?.paymentMethods.orEmpty().forEach { method ->
                val options = method.subOptions.orEmpty()
                if (options.isEmpty()) {
                    list += GiftPayment(method.methodId, method.displayName)
                } else {
                    options.forEach { option ->
                        // так кабинет склеивает способ и вариант: platega_2
                        list += GiftPayment("${method.methodId}_${option.id}", option.name)
                    }
                }
            }
            return list
        }
}

class GiftsViewModel : ViewModel() {
    private val repo = ServiceLocator.giftRepository

    private val _state = MutableStateFlow(GiftsUiState())
    val state: StateFlow<GiftsUiState> = _state

    private var watch: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            com.darkprince.vpn.data.api.ServiceHealth.backOnline.collect { if (_state.value.config == null) refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val config = async { runCatching { repo.config() } }
            val sent = async { repo.sent() }
            val received = async { repo.received() }
            val configResult = config.await()
            _state.update {
                it.copy(
                    loading = false,
                    config = configResult.getOrNull() ?: it.config,
                    sent = sent.await(),
                    received = received.await(),
                    error = configResult.exceptionOrNull()?.let { e -> (e as? Exception)?.userMessage() },
                )
            }
        }
    }

    fun activate(code: String) {
        if (code.isBlank()) return
        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            repo.activate(code)
                .onSuccess { result ->
                    val what = listOfNotNull(
                        result.tariffName,
                        result.periodDays?.let { "$it дн." },
                    ).joinToString(" · ")
                    _state.update {
                        it.copy(
                            busy = false,
                            info = "Подарок активирован" + (if (what.isNotBlank()) ": $what" else "") +
                                ". Подписка появится на главной",
                        )
                    }
                    refresh()
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun buy(
        tariffId: Long,
        periodDays: Int,
        payment: GiftPayment,
        recipient: String,
        message: String,
    ) {
        val tariff = _state.value.config?.tariffs?.firstOrNull { it.id == tariffId } ?: return
        val to = recipient.trim()
        // кому — по желанию: без получателя подарок просто код, которым делятся
        val recipientType = when {
            to.isBlank() -> null
            '@' in to.drop(1) -> "email"
            else -> "telegram"
        }
        _state.update { it.copy(busy = true, error = null, info = null, ready = null) }
        viewModelScope.launch {
            val request = GiftPurchaseRequest(
                tariffId = tariffId,
                periodDays = periodDays,
                recipientType = recipientType,
                recipientValue = to.takeIf { recipientType != null }?.let {
                    if (recipientType == "telegram") it.removePrefix("@") else it
                },
                giftMessage = message.trim().takeIf { it.isNotBlank() },
                paymentMode = if (payment.fromBalance) "balance" else "gateway",
                paymentMethod = payment.methodId,
            )
            val title = "${tariff.name} · $periodDays дн."
            repo.purchase(request)
                .onSuccess { response ->
                    val warning = if (response.warning == "telegram_unresolvable") {
                        "Бот не знает этот ник — отправьте код другу сами."
                    } else null
                    if (response.paymentUrl != null) {
                        _state.update {
                            it.copy(busy = false, openUrl = response.paymentUrl, waitingPayment = true, info = warning)
                        }
                        waitForPayment(response.purchaseToken, title)
                    } else {
                        _state.update {
                            it.copy(
                                busy = false,
                                ready = GiftReady(GiftRepository.codeOf(response.purchaseToken), title),
                                info = warning,
                            )
                        }
                        refresh()
                    }
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    /** После оплаты в кассе спрашиваем, готов ли подарок: код появляется, когда деньги дошли. */
    private fun waitForPayment(token: String, title: String) {
        watch?.cancel()
        watch = viewModelScope.launch {
            repeat(60) { attempt ->
                delay(if (attempt < 12) 5_000 else 15_000)
                val status = repo.status(token) ?: return@repeat
                if (status.isClaimable || status.status == "delivered") {
                    _state.update {
                        it.copy(
                            waitingPayment = false,
                            ready = GiftReady(GiftRepository.codeOf(status.purchaseToken ?: token), title),
                        )
                    }
                    refresh()
                    return@launch
                }
                if (status.status in setOf("failed", "expired")) {
                    _state.update { it.copy(waitingPayment = false, error = "Оплата подарка не прошла") }
                    return@launch
                }
            }
            _state.update { it.copy(waitingPayment = false) }
        }
    }

    fun dismissReady() {
        _state.update { it.copy(ready = null) }
    }

    fun consumeOpenUrl() {
        _state.update { it.copy(openUrl = null) }
    }
}
