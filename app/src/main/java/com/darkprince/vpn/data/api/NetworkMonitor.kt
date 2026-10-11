package com.darkprince.vpn.data.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock

/**
 * Следит, когда у телефона пропадает и снова появляется интернет.
 *
 * Вернулся после пропажи — сообщаем [ServiceHealth.networkReturned]: экраны
 * с ошибкой перезагружаются, сокет событий переподключается сразу, а не
 * через минуты ожидания.
 *
 * Короткие «пропажи» не в счёт: при включении и выключении VPN сеть по
 * умолчанию меняется за доли секунды, и это не обрыв.
 */
object NetworkMonitor {
    @Volatile private var lostAt = 0L
    @Volatile private var online = true

    fun start(context: Context) {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (validated && !online) {
                        online = true
                        if (SystemClock.elapsedRealtime() - lostAt >= MIN_OUTAGE_MS) {
                            ServiceHealth.networkReturned()
                            CabinetEvents.kick()
                        }
                    }
                }

                override fun onLost(network: Network) {
                    if (online) lostAt = SystemClock.elapsedRealtime()
                    online = false
                }
            })
        }
    }

    private const val MIN_OUTAGE_MS = 3_000L
}
