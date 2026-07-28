package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.core.model.ProxyProfile
import com.darkprince.vpn.data.api.dto.SubscriptionStatusResponse
import com.darkprince.vpn.data.repo.SubscriptionUserInfo
import com.darkprince.vpn.data.repo.userMessage
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.vpn.TrafficStats
import com.darkprince.vpn.vpn.VpnState
import com.darkprince.vpn.vpn.VpnStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeUiState(
    val subscription: SubscriptionStatusResponse? = null,
    val subUserInfo: SubscriptionUserInfo? = null,
    val servers: List<ProxyProfile> = emptyList(),
    val selectedServer: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
)

class HomeViewModel : ViewModel() {
    private val subRepo = ServiceLocator.subscriptionRepository
    private val prefs = ServiceLocator.prefs

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    val vpnState: StateFlow<VpnState> = VpnStateStore.state
    val vpnStats: StateFlow<TrafficStats> = VpnStateStore.stats
    val vpnError: StateFlow<String?> = VpnStateStore.lastError

    init {
        // при запуске сразу тянем свежую подписку; без сети fetchServers
        // сам откатится на сохранённую копию
        refresh(forceServers = true)
    }

    fun refresh(forceServers: Boolean = false) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            var error: String? = null
            val sub = try {
                subRepo.status()
            } catch (e: Exception) {
                error = e.userMessage()
                null
            }
            val (servers, userInfo) = try {
                subRepo.fetchServers(forceRefresh = forceServers)
            } catch (e: Exception) {
                if (sub != null && error == null) error = e.userMessage()
                emptyList<ProxyProfile>() to null
            }
            val selected = prefs.selectedServerFlow.first().coerceIn(0, (servers.size - 1).coerceAtLeast(0))
            _state.value = HomeUiState(
                subscription = sub,
                subUserInfo = userInfo,
                servers = servers,
                selectedServer = selected,
                loading = false,
                error = error,
            )
        }
    }

    fun selectServer(index: Int) {
        viewModelScope.launch {
            prefs.setSelectedServer(index)
            _state.value = _state.value.copy(selectedServer = index)
        }
    }

    fun selectedProfile(): ProxyProfile? =
        _state.value.servers.getOrNull(_state.value.selectedServer)
}
