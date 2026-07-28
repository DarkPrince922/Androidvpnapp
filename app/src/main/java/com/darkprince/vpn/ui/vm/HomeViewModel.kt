package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.core.model.ProxyProfile
import com.darkprince.vpn.core.xray.XrayConfigBuilder
import com.darkprince.vpn.data.api.dto.SubscriptionStatusResponse
import com.darkprince.vpn.data.repo.SubscriptionUserInfo
import com.darkprince.vpn.data.repo.userMessage
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.vpn.CoreEnv
import com.darkprince.vpn.vpn.TrafficStats
import com.darkprince.vpn.vpn.VpnState
import com.darkprince.vpn.vpn.VpnStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import libv2ray.Libv2ray
import java.util.concurrent.ConcurrentHashMap

/** Результат замера: >=0 — задержка в мс, -1 — сервер недоступен. */
data class HomeUiState(
    val subscription: SubscriptionStatusResponse? = null,
    val subUserInfo: SubscriptionUserInfo? = null,
    val servers: List<ProxyProfile> = emptyList(),
    val selectedServer: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val pings: Map<Int, Long> = emptyMap(),
    val pinging: Boolean = false,
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
        refresh(forceServers = true)
    }

    fun refresh(forceServers: Boolean = false) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            // 1) мгновенно показываем сохранённую подписку (работает офлайн)
            subRepo.cachedServers()?.let { (cachedServers, cachedInfo) ->
                val selected = prefs.selectedServerFlow.first()
                    .coerceIn(0, (cachedServers.size - 1).coerceAtLeast(0))
                _state.value = _state.value.copy(
                    servers = cachedServers,
                    subUserInfo = cachedInfo ?: _state.value.subUserInfo,
                    selectedServer = selected,
                )
            }

            // 2) фоном обновляем из сети
            var error: String? = null
            val sub = try {
                subRepo.status()
            } catch (e: Exception) {
                error = e.userMessage()
                null
            }
            val fresh = try {
                subRepo.fetchServers(forceRefresh = forceServers)
            } catch (e: Exception) {
                if (error == null) error = e.userMessage()
                null
            }
            val servers = fresh?.first ?: _state.value.servers
            val userInfo = fresh?.second ?: _state.value.subUserInfo
            // если данные в итоге есть — сетевую ошибку не показываем
            if (servers.isNotEmpty()) error = null
            val selected = prefs.selectedServerFlow.first()
                .coerceIn(0, (servers.size - 1).coerceAtLeast(0))
            _state.value = _state.value.copy(
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

    /**
     * Замер реальной задержки через ядро Xray (запрос через прокси-протокол,
     * как real ping в v2rayNG), до 3 серверов одновременно.
     */
    fun pingAll() {
        if (_state.value.pinging) return
        val servers = _state.value.servers
        if (servers.isEmpty()) return
        _state.value = _state.value.copy(pinging = true, pings = emptyMap())
        viewModelScope.launch(Dispatchers.IO) {
            CoreEnv.ensure(ServiceLocator.appContext)
            val results = ConcurrentHashMap<Int, Long>()
            val semaphore = Semaphore(3)
            coroutineScope {
                servers.forEachIndexed { index, profile ->
                    launch {
                        semaphore.withPermit {
                            val ms = try {
                                Libv2ray.measureOutboundDelay(
                                    XrayConfigBuilder.build(profile),
                                    "https://www.gstatic.com/generate_204",
                                )
                            } catch (_: Throwable) {
                                -1L
                            }
                            results[index] = ms
                            _state.value = _state.value.copy(pings = results.toMap())
                        }
                    }
                }
            }
            _state.value = _state.value.copy(pinging = false)
        }
    }
}
