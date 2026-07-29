package com.darkprince.vpn.ui.vm

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SplitMode(val key: String, val title: String, val hint: String) {
    ALL("ALL", "Все приложения", "Через VPN идёт весь трафик устройства"),
    ONLY_SELECTED("ONLY_SELECTED", "Только выбранные", "Через VPN пойдут только отмеченные приложения"),
    EXCEPT_SELECTED("EXCEPT_SELECTED", "Кроме выбранных", "Отмеченные приложения пойдут в обход VPN"),
}

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean,
)

data class AppsUiState(
    val apps: List<InstalledApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val mode: SplitMode = SplitMode.ALL,
    val loading: Boolean = true,
    val query: String = "",
) {
    val visibleApps: List<InstalledApp>
        get() = if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
}

class AppsViewModel : ViewModel() {
    private val prefs = ServiceLocator.prefs

    private val _state = MutableStateFlow(AppsUiState())
    val state: StateFlow<AppsUiState> = _state

    init {
        // режим и отметки читаем потоком из настроек: список приложений
        // грузится долго, и раньше его результат затирал только что
        // выбранный режим значением, прочитанным до начала загрузки
        viewModelScope.launch {
            prefs.splitModeFlow.collect { key ->
                val mode = SplitMode.entries.firstOrNull { it.key == key } ?: SplitMode.ALL
                _state.value = _state.value.copy(mode = mode)
            }
        }
        viewModelScope.launch {
            prefs.splitAppsFlow.collect { packages ->
                _state.value = _state.value.copy(selected = packages)
            }
        }
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            val selectedNow = prefs.splitApps()
            val apps = withContext(Dispatchers.IO) {
                val pm = ServiceLocator.appContext.packageManager
                val self = ServiceLocator.appContext.packageName
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .asSequence()
                    // только приложения с доступом в интернет — остальным VPN не нужен
                    .filter { info ->
                        info.packageName != self &&
                            pm.checkPermission(
                                android.Manifest.permission.INTERNET,
                                info.packageName
                            ) == PackageManager.PERMISSION_GRANTED
                    }
                    .map { info ->
                        InstalledApp(
                            packageName = info.packageName,
                            label = pm.getApplicationLabel(info).toString(),
                            icon = try {
                                pm.getApplicationIcon(info)
                            } catch (_: Exception) {
                                null
                            },
                            isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        )
                    }
                    // выбранные и пользовательские — выше системных
                    .sortedWith(
                        compareByDescending<InstalledApp> { it.packageName in selectedNow }
                            .thenBy { it.isSystem }
                            .thenBy { it.label.lowercase() }
                    )
                    .toList()
            }
            // обновляем только список: режим и отметки живут своим потоком
            _state.value = _state.value.copy(apps = apps, loading = false)
        }
    }

    fun setMode(mode: SplitMode) {
        _state.value = _state.value.copy(mode = mode)
        viewModelScope.launch { prefs.setSplitMode(mode.key) }
    }

    fun toggle(packageName: String) {
        val updated = _state.value.selected.toMutableSet().apply {
            if (!add(packageName)) remove(packageName)
        }
        _state.value = _state.value.copy(selected = updated)
        viewModelScope.launch { prefs.setSplitApps(updated) }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selected = emptySet())
        viewModelScope.launch { prefs.setSplitApps(emptySet()) }
    }
}
