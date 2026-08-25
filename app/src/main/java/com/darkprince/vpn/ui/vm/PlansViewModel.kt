package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.api.dto.SubscriptionListItem
import com.darkprince.vpn.data.repo.DevicesInfo
import com.darkprince.vpn.data.repo.PeriodPrice
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
        val name = _state.value.cards.firstOrNull { it.id == subId }?.title
        _state.update { it.copy(purchasing = true, error = null, info = null) }
        viewModelScope.launch {
            if (VpnStateStore.state.value == VpnState.CONNECTED) {
                XVpnService.stop(ServiceLocator.appContext)
            }
            repo.selectSubscription(subId)
            _state.update {
                it.copy(
                    purchasing = false,
                    workingSubscriptionId = subId,
                    info = if (name != null) "Подключение переключено на «$name»"
                    else "Подключение переключено",
                )
            }
        }
    }

    fun purchase(tariff: TariffOffer, period: PeriodPrice) = act("Подписка оформлена!") {
        repo.purchaseTariff(tariff.id, period.days)
    }

    fun renew(subId: Long, period: PeriodPrice) = act("Подписка продлена!") {
        repo.renew(period.days, subId)
    }

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
 * Сколько дней осталось до даты окончания. Кабинет отдаёт дату строкой ISO —
 * берём из неё только календарный день: время и часовой пояс для «осталось
 * дней» роли не играют. Отрицательное число значит, что срок уже вышел.
 */
private fun daysUntilDate(endDate: String): Int? = try {
    java.time.temporal.ChronoUnit.DAYS
        .between(java.time.LocalDate.now(), java.time.LocalDate.parse(endDate.take(10)))
        .toInt()
} catch (_: Exception) {
    null
}
