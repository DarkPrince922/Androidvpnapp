package com.darkprince.vpn.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class TrafficStats(
    val uplinkBytes: Long = 0,
    val downlinkBytes: Long = 0,
)

/** Общее состояние VPN между сервисом и UI (один процесс). */
object VpnStateStore {
    private val _state = MutableStateFlow(VpnState.DISCONNECTED)
    val state: StateFlow<VpnState> = _state

    private val _stats = MutableStateFlow(TrafficStats())
    val stats: StateFlow<TrafficStats> = _stats

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val _activeProfileName = MutableStateFlow<String?>(null)
    val activeProfileName: StateFlow<String?> = _activeProfileName

    fun setState(state: VpnState, error: String? = null) {
        _state.value = state
        _lastError.value = error
    }

    fun setStats(stats: TrafficStats) {
        _stats.value = stats
    }

    fun setActiveProfile(name: String?) {
        _activeProfileName.value = name
    }
}
