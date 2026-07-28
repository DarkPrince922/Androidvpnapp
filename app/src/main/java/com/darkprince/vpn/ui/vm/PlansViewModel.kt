package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.repo.DevicesInfo
import com.darkprince.vpn.data.repo.PeriodPrice
import com.darkprince.vpn.data.repo.TariffOffer
import com.darkprince.vpn.data.repo.TrafficPackage
import com.darkprince.vpn.data.repo.userMessage
import com.darkprince.vpn.di.ServiceLocator
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
)

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
            val tariffs = try {
                repo.tariffs()
            } catch (_: Exception) {
                emptyList()
            }
            val renewals = try {
                repo.renewalOptions()
            } catch (_: Exception) {
                emptyList()
            }
            val trial = try {
                val info = repo.trialInfo()
                info.available ?: info.isAvailable ?: false
            } catch (_: Exception) {
                false
            }
            val devices = try {
                repo.devicesInfo()
            } catch (_: Exception) {
                null
            }
            val trafficPackages = try {
                repo.trafficPackages()
            } catch (_: Exception) {
                emptyList()
            }
            val error = if (tariffs.isEmpty() && renewals.isEmpty() && !trial &&
                devices == null && trafficPackages.isEmpty()
            ) {
                "Нет доступных предложений. Возможно, покупка через кабинет отключена."
            } else null
            _state.value = PlansUiState(
                tariffs = tariffs,
                renewalOptions = renewals,
                trialAvailable = trial,
                devices = devices,
                trafficPackages = trafficPackages,
                loading = false,
                error = error,
            )
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

    fun buyDevices(count: Int) {
        if (count <= 0) return
        _state.value = _state.value.copy(purchasing = true, error = null, info = null)
        viewModelScope.launch {
            val error = repo.buyDevices(count)
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
            val error = repo.reduceDevices(newLimit)
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
