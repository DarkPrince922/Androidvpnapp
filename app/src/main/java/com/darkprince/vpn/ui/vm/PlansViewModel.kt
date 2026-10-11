package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.api.dto.SubscriptionListItem
import com.darkprince.vpn.data.repo.DevicesInfo
import com.darkprince.vpn.data.repo.PendingPurchase
import com.darkprince.vpn.data.repo.PeriodPrice
import com.darkprince.vpn.data.repo.PurchaseResult
import com.darkprince.vpn.data.repo.TariffOffer
import com.darkprince.vpn.data.repo.TrafficPackage
import com.darkprince.vpn.data.repo.userMessage
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import com.darkprince.vpn.vpn.VpnState
import com.darkprince.vpn.vpn.VpnStateStore
import com.darkprince.vpn.vpn.XVpnService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Одна подписка человека со всем, что к ней относится.
 *
 * Всё, что кабинет считает по подписке — цены продления, устройства, пакеты
 * трафика, — спрашивается с её номером. Раньше эти запросы уходили без
 * номера, кабинет отвечал про подписку, которую сам считает текущей, и на
 * карточке одного тарифа оказывались чужие цифры и чужая цена.
 */
data class OwnedSubscription(
    val sub: SubscriptionListItem,
    /** Тариф подписки — из него берём периоды, если кабинет не дал своих. */
    val tariff: TariffOffer? = null,
    val devices: DevicesInfo? = null,
    val renewalOptions: List<PeriodPrice> = emptyList(),
    val trafficPackages: List<TrafficPackage> = emptyList(),
    val loading: Boolean = true,
) {
    val id: Long get() = sub.id
    val title: String get() = sub.displayName

    /** Периоды продления: свои у подписки, иначе тарифные. */
    val periods: List<PeriodPrice>
        get() = renewalOptions.ifEmpty { tariff?.periods.orEmpty() }

    val unlimitedTraffic: Boolean get() = (sub.trafficLimitGb ?: 0.0) <= 0.0

    /** Действующий лимит устройств: он может быть больше тарифного из-за докупки. */
    val deviceLimit: Int? get() = devices?.deviceLimit ?: sub.deviceLimit

    /** Сколько из лимита докуплено сверх тарифа. */
    val extraDevices: Int?
        get() {
            val limit = deviceLimit ?: return null
            val base = tariff?.deviceLimit ?: return null
            return (limit - base).takeIf { it > 0 }
        }

    /** Дней до окончания. Отрицательное — подписка уже истекла. */
    val daysLeft: Int? get() = sub.endDate?.let(::daysUntilDate)
}

data class PlansUiState(
    /** Ваши подписки — сверху экрана. */
    val cards: List<OwnedSubscription> = emptyList(),
    /** Всё, что продаётся. */
    val tariffs: List<TariffOffer> = emptyList(),
    val trialAvailable: Boolean = false,
    /**
     * Подписка, по которой идёт подключение.
     *
     * Выбирают её на главной, под кнопкой; остальные экраны за ней следуют.
     * Здесь она нужна, чтобы показать, какая карточка рабочая, и дать
     * переключиться, не возвращаясь на главную.
     */
    val workingSubscriptionId: Long? = null,
    val loading: Boolean = false,
    val purchasing: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    /** Денег не хватило — экрану пора увести человека на пополнение. */
    val needTopUp: Boolean = false,
) {
    private val ownedTariffIds: Set<Long>
        get() = cards.mapNotNull { it.sub.tariffId }.toSet()

    /**
     * Магазин: только то, чего у человека ещё нет.
     *
     * Купленный тариф из магазина уходит — он живёт наверху своей карточкой,
     * и продлевают его там. Три одинаковые карточки подряд, из которых две
     * «ваши», читались как список, в котором непонятно, что делать.
     */
    val offers: List<TariffOffer>
        get() = tariffs.filter { it.id !in ownedTariffIds }
}

class PlansViewModel : ViewModel() {
    private val repo = ServiceLocator.subscriptionRepository

    private val _state = MutableStateFlow(PlansUiState())
    val state: StateFlow<PlansUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val (tariffs, trial, subs) = coroutineScope {
                val tariffsDeferred = async { runOrNull { repo.tariffs() } ?: emptyList() }
                val trialDeferred = async {
                    runOrNull {
                        val info = repo.trialInfo()
                        info.available ?: info.isAvailable ?: false
                    } ?: false
                }
                val subsDeferred = async { repo.subscriptions().orEmpty() }
                Triple(tariffsDeferred.await(), trialDeferred.await(), subsDeferred.await())
            }
            val working = ServiceLocator.prefs.selectedSubscriptionFlow.first()

            // Действующие сверху, истёкшие следом: продлить истёкшую тоже надо
            // где-то, но начинать список с неё незачем.
            val ordered = subs.sortedWith(
                compareByDescending<SubscriptionListItem> { it.isActive }
                    .thenBy { it.endDate ?: "" }
            )
            val cards = ordered.map { sub ->
                OwnedSubscription(
                    sub = sub,
                    tariff = tariffs.firstOrNull { it.id == sub.tariffId },
                )
            }

            val error = if (tariffs.isEmpty() && subs.isEmpty() && !trial) {
                "Не удалось загрузить предложения. Проверьте соединение и потяните для обновления."
            } else null

            _state.update {
                it.copy(
                    cards = cards,
                    tariffs = tariffs,
                    trialAvailable = trial,
                    workingSubscriptionId = working,
                    loading = false,
                    error = error,
                )
            }

            // Подробности по каждой подписке — параллельно, и каждая ложится в
            // свою карточку, как только пришла. Ждать самую медленную, чтобы
            // показать все разом, незачем: карточки уже на экране.
            coroutineScope {
                cards.map { card -> async { loadDetails(card.id) } }.awaitAll()
            }
        }
    }

    /** Догружает то, что кабинет считает по конкретной подписке. */
    private suspend fun loadDetails(subId: Long) = coroutineScope {
        val devicesDeferred = async {
            runOrNull {
                // «пустая» сводка (все поля недоступны) — блок не показываем
                repo.devicesInfo(subId).takeIf {
                    it.deviceLimit != null || it.purchaseAvailable || it.reduceAvailable
                }
            }
        }
        val renewalsDeferred = async { runOrNull { repo.renewalOptions(subId) } ?: emptyList() }
        val trafficDeferred = async { runOrNull { repo.trafficPackages(subId) } ?: emptyList() }

        val devices = devicesDeferred.await()
        val renewals = renewalsDeferred.await()
        val traffic = trafficDeferred.await()

        updateCard(subId) {
            it.copy(
                devices = devices,
                renewalOptions = renewals,
                trafficPackages = traffic,
                loading = false,
            )
        }
    }

    private fun updateCard(subId: Long, block: (OwnedSubscription) -> OwnedSubscription) {
        _state.update { state ->
            state.copy(
                cards = state.cards.map { if (it.id == subId) block(it) else it },
            )
        }
    }

    /**
     * Сделать эту подписку рабочей — той, по которой идёт подключение.
     *
     * Ровно то же, что делает главный экран, и с теми же последствиями:
     * поднятый туннель гасится, серверы перечитываются под новую подписку.
     * Поэтому это отдельное нажатие, а не побочный эффект просмотра карточки:
     * листать «Тарифы» и остаться без связи никто не подписывался.
     */
    fun makeWorking(subId: Long) {
        if (subId == _state.value.workingSubscriptionId) return
        if (VpnStateStore.state.value.let { it == VpnState.CONNECTED || it == VpnState.CONNECTING }) {
            _state.update { it.copy(info = "Отключите VPN, чтобы сменить подписку") }
            return
        }
        val name = _state.value.cards.firstOrNull { it.id == subId }?.title
        _state.update { it.copy(purchasing = true, error = null, info = null) }
        viewModelScope.launch {
            // Сохранённый профиль — сервер прежней подписки. Оставь его, и
            // автоподключение или «постоянный VPN» подняли бы туннель по ней.
            val wasOn = VpnStateStore.state.value.let {
                it == VpnState.CONNECTED || it == VpnState.CONNECTING
            }
            if (wasOn) XVpnService.stop(ServiceLocator.appContext)
            XVpnService.forgetProfile(ServiceLocator.appContext)
            repo.selectSubscription(subId)
            _state.update {
                it.copy(
                    purchasing = false,
                    workingSubscriptionId = subId,
                    info = (if (name != null) "Подключение переключено на «$name»"
                    else "Подключение переключено") +
                        if (wasOn) ". VPN выключен — выберите сервер и подключитесь снова" else "",
                )
            }
        }
    }

    fun purchase(tariff: TariffOffer, period: PeriodPrice) = buy(
        pending = PendingPurchase(
            kind = "buy",
            tariffId = tariff.id,
            periodDays = period.days,
            priceKopeks = period.priceKopeks,
            missingKopeks = period.priceKopeks,
            title = "${tariff.name} · ${periodTitle(period)}",
        ),
        success = "Подписка оформлена!",
    ) { repo.purchaseTariffChecked(tariff.id, period.days) }

    fun renew(subId: Long, period: PeriodPrice) {
        val card = _state.value.cards.firstOrNull { it.id == subId }
        buy(
            pending = PendingPurchase(
                kind = "renew",
                subscriptionId = subId,
                periodDays = period.days,
                priceKopeks = period.priceKopeks,
                missingKopeks = period.priceKopeks,
                title = "${card?.title ?: "Подписка"} · продление на ${periodTitle(period)}",
            ),
            success = "Подписка продлена!",
        ) { repo.renewChecked(period.days, subId) }
    }

    /**
     * Купить или продлить. Не хватило денег — запоминаем выбор и просим
     * экран увести человека на пополнение: там уже стоит недостающая сумма,
     * а после зачисления эта покупка оплатится сама.
     */
    private fun buy(pending: PendingPurchase, success: String, block: suspend () -> PurchaseResult) {
        _state.update { it.copy(purchasing = true, error = null, info = null) }
        viewModelScope.launch {
            when (val result = block()) {
                PurchaseResult.Ok -> {
                    ServiceLocator.pendingPurchases.clear()
                    _state.update { it.copy(purchasing = false, info = success) }
                    refresh()
                }
                is PurchaseResult.NotEnough -> {
                    val snapshot = runOrNull { repo.subscriptions() }.orEmpty()
                        .associate { it.id to it.endDate }
                    ServiceLocator.pendingPurchases.save(
                        pending.copy(
                            missingKopeks = result.missingKopeks ?: pending.priceKopeks,
                            endDatesBefore = snapshot,
                        )
                    )
                    _state.update { it.copy(purchasing = false, needTopUp = true) }
                }
                is PurchaseResult.Failed -> _state.update { it.copy(purchasing = false, error = result.message) }
            }
        }
    }

    /** Экран увёл на пополнение — флаг больше не нужен. */
    fun consumeTopUp() {
        _state.update { it.copy(needTopUp = false) }
    }

    private fun periodTitle(period: PeriodPrice): String =
        if (period.daily) "посуточно" else "${period.days} дн."

    fun buyDevices(subId: Long, count: Int) {
        if (count <= 0) return
        act("Устройства добавлены!") { repo.buyDevices(count, subId) }
    }

    fun reduceDevices(subId: Long, newLimit: Int) {
        if (newLimit <= 0) return
        act("Лимит устройств уменьшен") { repo.reduceDevices(newLimit, subId) }
    }

    fun buyTraffic(subId: Long, gb: Int) = act("Трафик добавлен!") {
        repo.buyTraffic(gb, subId)
    }

    fun activateTrial() = act("Пробный период активирован!") {
        if (repo.activateTrial()) null else "Не удалось активировать пробный период"
    }

    /**
     * Общая обвязка покупок: заблокировать кнопки, сходить, показать итог.
     *
     * Удачная покупка всегда заканчивается перечитыванием: меняются и срок, и
     * лимит устройств, и цены продления, а угадывать новое состояние на
     * клиенте — верный способ показать не то, за что человек заплатил.
     */
    private fun act(success: String, block: suspend () -> String?) {
        _state.update { it.copy(purchasing = true, error = null, info = null) }
        viewModelScope.launch {
            val error = try {
                block()
            } catch (e: Exception) {
                e.userMessage()
            }
            if (error == null) {
                _state.update { it.copy(purchasing = false, info = success) }
                refresh()
            } else {
                _state.update { it.copy(purchasing = false, error = error) }
            }
        }
    }

    /**
     * Запрос, которому позволено не удаться: экран собирается из нескольких, и
     * один упавший не должен уносить остальные.
     *
     * Лямбда именно suspend: внутрь ходят в сеть.
     */
    private suspend fun <T> runOrNull(block: suspend () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }
}

/**
 * Сколько дней осталось до даты окончания. Отрицательное число значит, что
 * срок уже вышел.
 *
 * Кабинет отдаёт время в UTC — иногда с поясом, иногда без. Календарный день
 * считаем по часам телефона: подписка до 21:00 UTC у человека в Москве
 * кончается в полночь следующего дня, и взять день прямо из строки значило бы
 * недосчитать сутки.
 */
private fun daysUntilDate(endDate: String): Int? = try {
    val zone = java.time.ZoneId.systemDefault()
    val end = runCatching { java.time.OffsetDateTime.parse(endDate).toInstant() }
        .recoverCatching {
            java.time.LocalDateTime.parse(endDate).toInstant(java.time.ZoneOffset.UTC)
        }
        .map { it.atZone(zone).toLocalDate() }
        .getOrElse { java.time.LocalDate.parse(endDate.take(10)) }
    java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(zone), end).toInt()
} catch (_: Exception) {
    null
}
