package com.darkprince.vpn.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.darkprince.vpn.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

        if (VpnStateStore.state.value == VpnState.CONNECTED ||
            VpnStateStore.state.value == VpnState.CONNECTING
        ) {
            XVpnService.stop(applicationContext)
            updateTile(VpnState.DISCONNECTED)
            return
        }

        // Всё, что до запуска сервиса, делаем синхронно и без обращений к
        // диску и сети: система разрешает запустить foreground-сервис лишь
        // в коротком окне сразу после нажатия на плитку.
        if (VpnService.prepare(this) != null) {
            // разрешение на VPN ещё не выдано — только через приложение
            openApp()
            return
        }
        if (!XVpnService.hasSavedProfile(applicationContext)) {
            openApp()
            return
        }

        updateTile(VpnState.CONNECTING)
        try {
            XVpnService.startLast(applicationContext)
        } catch (_: Exception) {
            // система не дала запустить сервис из фона — открываем приложение
            updateTile(VpnState.DISCONNECTED)
            Toast.makeText(this, "Откройте приложение для подключения", Toast.LENGTH_SHORT).show()
            openApp()
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
