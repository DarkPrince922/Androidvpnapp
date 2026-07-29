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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlansUiState(
    val tariffs: List<TariffOffer> = emptyList(),
    val renewalOptions: List<PeriodPrice> = emptyList(),
    val trialAvailable: Boolean = false,
    val devices: DevicesInfo? = null,
    val trafficPackages: List<TrafficPackage> = emptyList(),
    val loading: Boolean = false,
    val purchasing: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    /** Тарифы, которые у пользователя уже куплены — их продлевают, а не покупают. */
    val ownedTariffIds: Set<Long> = emptySet(),
    /** Лимит устройств действующей подписки (может быть больше тарифного из-за докупки). */
    val currentDeviceLimit: Int? = null,
    /** Подписки пользователя и та, для которой сейчас правим устройства. */
    val subscriptions: List<SubscriptionListItem> = emptyList(),
    val deviceSubscriptionId: Long? = null,
    val devicesLoading: Boolean = false,
) {
    val deviceSubscription: SubscriptionListItem?
        get() = subscriptions.firstOrNull { it.id == deviceSubscriptionId }

    /**
     * Действующий лимит устройств по каждому тарифу. Раньше в карточках
     * показывался один общий лимит, из-за чего цифра у тарифа менялась при
     * переключении подписки в блоке устройств.
     */
    val deviceLimitByTariff: Map<Long, Int>
        get() = subscriptions
            .filter { it.isActive }
            .mapNotNull { sub -> sub.tariffId?.let { id -> sub.deviceLimit?.let { id to it } } }
            .toMap()

    fun isOwned(tariffId: Long) = tariffId in ownedTariffIds

    /** Цена продления за период: берём из вариантов продления, иначе тарифную. */
    fun renewalPrice(days: Int): Long? = renewalOptions.firstOrNull { it.days == days }?.priceKopeks
}

class PlansViewModel : ViewModel() {
    private val repo = ServiceLocator.subscriptionRepository

    private val _state = MutableStateFlow(PlansUiState())
    val state: StateFlow<PlansUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            coroutineScope {
                val tariffsDeferred = async {
                    try {
                        repo.tariffs()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                val renewalsDeferred = async {
                    try {
                        repo.renewalOptions()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                val trialDeferred = async {
                    try {
                        val info = repo.trialInfo()
                        info.available ?: info.isAvailable ?: false
                    } catch (_: Exception) {
                        false
                    }
                }
                val deviceSubId = _state.value.deviceSubscriptionId
                val devicesDeferred = async {
                    try {
                        // «пустая» сводка (все поля недоступны) — карточку не показываем
                        repo.devicesInfo(deviceSubId).takeIf {
                            it.deviceLimit != null || it.purchaseAvailable || it.reduceAvailable
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
                val trafficDeferred = async {
                    try {
                        repo.trafficPackages()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                val subsDeferred = async { repo.subscriptions() }
                val tariffs = tariffsDeferred.await()
                val renewals = renewalsDeferred.await()
                val trial = trialDeferred.await()
                val devices = devicesDeferred.await()
                val trafficPackages = trafficDeferred.await()
                val subs = subsDeferred.await()
                val owned = subs.filter { it.isActive }.mapNotNull { it.tariffId }.toSet()
                val deviceLimit = devices?.deviceLimit
                    ?: subs.firstOrNull { it.isActive }?.deviceLimit
                val error = if (tariffs.isEmpty() && renewals.isEmpty() && !trial &&
                    devices == null && trafficPackages.isEmpty()
                ) {
                    "Не удалось загрузить предложения. Проверьте соединение и потяните для обновления."
                } else null
                _state.value = PlansUiState(
                    tariffs = tariffs,
                    renewalOptions = renewals,
                    trialAvailable = trial,
                    devices = devices,
                    trafficPackages = trafficPackages,
                    loading = false,
                    error = error,
                    ownedTariffIds = owned,
                    currentDeviceLimit = deviceLimit,
                    subscriptions = subs,
                    // по умолчанию правим устройства активной подписки
                    deviceSubscriptionId = deviceSubId
                        ?: subs.firstOrNull { it.isActive }?.id
                        ?: subs.firstOrNull()?.id,
                )
            }
        }
    }

    fun purchase(tariff: TariffOffer, period: PeriodPrice) {
        _state.value = _state.value.copy(purchasing = true, error = null, info = null)
        viewModelScope.launch {
            val error = repo.purchaseTariff(tariff.id, period.days)
            if (error == null) {
                _state.value = _state.value.copy(purchasing = false, info = "Подписка оформлена!")
                refresh()
            } else {
                _state.value = _state.value.copy(purchasing = false, error = error)
            }
        }
    }

    fun renew(period: PeriodPrice) {
        _state.value = _state.value.copy(purchasing = true, error = null, info = null)
        viewModelScope.launch {
            val error = repo.renew(period.days)
            if (error == null) {
                _state.value = _state.value.copy(purchasing = false, info = "Подписка продлена!")
                refresh()
            } else {
                _state.value = _state.value.copy(purchasing = false, error = error)
            }
        }
    }

    /** Переключение подписки, для которой управляем устройствами. */
    fun selectDeviceSubscription(id: Long) {
        if (id == _state.value.deviceSubscriptionId) return
        _state.value = _state.value.copy(deviceSubscriptionId = id, devicesLoading = true)
        viewModelScope.launch {
            val devices = try {
                repo.devicesInfo(id)
            } catch (_: Exception) {
                null
            }
            _state.value = _state.value.copy(
                devices = devices,
                currentDeviceLimit = devices?.deviceLimit
                    ?: _state.value.subscriptions.firstOrNull { it.id == id }?.deviceLimit,
                devicesLoading = false,
            )
        }
    }

    fun buyDevices(count: Int) {
        if (count <= 0) return
        _state.value = _state.value.copy(purchasing = true, error = null, info = null)
        viewModelScope.launch {
            val error = repo.buyDevices(count, _state.value.deviceSubscriptionId)
            if (error == null) {
                _state.value = _state.value.copy(purchasing = false, info = "Устройства добавлены!")
                refresh()
            } else {
                _state.value = _state.value.copy(purchasing = false, error = error)
            }
        }
    }

    fun reduceDevices(newLimit: Int) {
        if (newLimit <= 0) return
        _state.value = _state.value.copy(purchasing = true, error = null, info = null)
        viewModelScope.launch {
            val error = repo.reduceDevices(newLimit, _state.value.deviceSubscriptionId)
            if (error == null) {
                _state.value = _state.value.copy(purchasing = false, info = "Лимит устройств уменьшен")
                refresh()
            } else {
                _state.value = _state.value.copy(purchasing = false, error = error)
            }
        }
    }

    fun buyTraffic(gb: Int) {
        _state.value = _state.value.copy(purchasing = true, error = null, info = null)
        viewModelScope.launch {
            val error = repo.buyTraffic(gb)
            if (error == null) {
                _state.value = _state.value.copy(purchasing = false, info = "Трафик добавлен!")
                refresh()
            } else {
                _state.value = _state.value.copy(purchasing = false, error = error)
            }
        }
    }

    fun activateTrial() {
        _state.value = _state.value.copy(purchasing = true, error = null, info = null)
        viewModelScope.launch {
            val ok = repo.activateTrial()
            _state.value = if (ok) {
                _state.value.copy(purchasing = false, info = "Пробный период активирован!")
            } else {
                _state.value.copy(purchasing = false, error = "Не удалось активировать пробный период")
            }
            if (ok) refresh()
        }
    }
}
