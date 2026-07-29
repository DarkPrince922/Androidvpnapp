package com.darkprince.vpn.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Плитка в шторке: включение/выключение VPN без открытия приложения. */
class VpnTileService : TileService() {

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = newScope
        newScope.launch {
            VpnStateStore.state.collect { updateTile(it) }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    private fun updateTile(state: VpnState) {
        val tile = qsTile ?: return
        tile.state = when (state) {
            VpnState.CONNECTED, VpnState.CONNECTING -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (state) {
                VpnState.CONNECTED -> "Подключено"
                VpnState.CONNECTING -> "Подключение…"
                else -> "Отключено"
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        when (VpnStateStore.state.value) {
            VpnState.CONNECTED, VpnState.CONNECTING -> {
                XVpnService.stop(this)
                updateTile(VpnState.DISCONNECTED)
            }
            else -> {
                ServiceLocator.init(applicationContext)
                if (VpnService.prepare(this) != null || !ServiceLocator.authRepository.isLoggedIn) {
                    // нет разрешения на VPN или входа — открываем приложение
                    openApp()
                    return
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val profile = try {
                        val (servers, _) = ServiceLocator.subscriptionRepository.fetchServers()
                        val index = ServiceLocator.prefs.selectedServerFlow.first()
                            .coerceIn(0, (servers.size - 1).coerceAtLeast(0))
                        servers.getOrNull(index)
                    } catch (_: Exception) {
                        null
                    }
                    if (profile != null) {
                        // сервис мог ещё завершать прошлую сессию — небольшая
                        // пауза, иначе новый запуск погибнет вместе с ней
                        delay(300)
                        XVpnService.start(this@VpnTileService, profile)
                    } else {
                        launch(Dispatchers.Main) { openApp() }
                    }
                }
            }
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
