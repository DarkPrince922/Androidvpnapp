package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.api.dto.DeviceDto
import com.darkprince.vpn.data.api.dto.SubscriptionListItem
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DevicesUiState(
    val devices: List<DeviceDto> = emptyList(),
    val subscriptions: List<SubscriptionListItem> = emptyList(),
    val selectedSubscriptionId: Long? = null,
    val deviceLimit: Int? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val selectedSubscription: SubscriptionListItem?
        get() = subscriptions.firstOrNull { it.id == selectedSubscriptionId }
}

class DevicesViewModel : ViewModel() {
    private val repo = ServiceLocator.subscriptionRepository

    /** HWID этого телефона — его помечаем и не даём удалить случайно. */
    val ownHwid: String get() = repo.ownHwid

    private val _state = MutableStateFlow(DevicesUiState())
    val state: StateFlow<DevicesUiState> = _state

    init {
        load()
    }

    /**
     * Открыть устройства конкретной подписки — с карточки на «Тарифах».
     *
     * Молча ничего не делаем, если она уже открыта: экран грузится сам при
     * создании, и второй такой же запрос был бы лишним.
     */
    fun select(subscriptionId: Long) {
        if (subscriptionId == _state.value.selectedSubscriptionId) return
        load(subscriptionId)
    }

    fun load(subscriptionId: Long? = _state.value.selectedSubscriptionId) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val subs = repo.subscriptions().orEmpty()
            // По умолчанию — рабочая подписка, та же, по которой идёт
            // подключение на главной. Свой переключатель ниже остаётся, но
            // он локальный: смотреть чужие устройства можно, а рабочую
            // подписку этим не меняют — иначе просмотр обрывал бы туннель.
            val targetId = subscriptionId
                ?: ServiceLocator.prefs.selectedSubscriptionFlow.first()
                ?: subs.firstOrNull { it.isActive }?.id
                ?: subs.firstOrNull()?.id
            val devices = repo.devicesList(targetId)
            val limit = try {
                repo.devicesInfo(targetId).deviceLimit
            } catch (_: Exception) {
                null
            }
            _state.value = _state.value.copy(
                devices = devices,
                subscriptions = subs,
                selectedSubscriptionId = targetId,
                deviceLimit = limit,
                loading = false,
            )
        }
    }

    fun selectSubscription(id: Long) {
        if (id == _state.value.selectedSubscriptionId) return
        _state.value = _state.value.copy(selectedSubscriptionId = id, devices = emptyList())
        load(id)
    }

    fun deleteDevice(hwid: String) {
        _state.value = _state.value.copy(busy = true, error = null, info = null)
        viewModelScope.launch {
            val error = repo.deleteDevice(hwid, _state.value.selectedSubscriptionId)
            _state.value = if (error == null) {
                _state.value.copy(
                    busy = false,
                    devices = _state.value.devices.filterNot { it.hwid == hwid },
                    info = "Устройство отключено",
                )
            } else {
                _state.value.copy(busy = false, error = error)
            }
            load()
        }
    }

    fun deleteAll() {
        _state.value = _state.value.copy(busy = true, error = null, info = null)
        viewModelScope.launch {
            val error = repo.deleteAllDevices(_state.value.selectedSubscriptionId)
            _state.value = if (error == null) {
                _state.value.copy(busy = false, devices = emptyList(), info = "Все устройства отключены")
            } else {
                _state.value.copy(busy = false, error = error)
            }
            load()
        }
    }

    /**
     * Перерегистрация: панель заводит устройство в момент скачивания
     * подписки с заголовком x-hwid, а не при подключении VPN. Если список
     * пуст после переустановки — принудительно тянем подписку заново.
     */
    fun registerThisDevice() {
        _state.value = _state.value.copy(busy = true, error = null, info = null)
        viewModelScope.launch {
            val error = try {
                repo.fetchServers(forceRefresh = true)
                null
            } catch (e: Exception) {
                e.message
            }
            _state.value = _state.value.copy(
                busy = false,
                info = if (error == null) "Подписка перезапрошена — обновляем список" else null,
                error = error,
            )
            load()
        }
    }

    fun rename(hwid: String, name: String) {
        if (name.isBlank()) return
        _state.value = _state.value.copy(busy = true, error = null, info = null)
        viewModelScope.launch {
            val error = repo.renameDevice(hwid, name.trim(), _state.value.selectedSubscriptionId)
            _state.value = _state.value.copy(busy = false, error = error)
            load()
        }
    }
}
