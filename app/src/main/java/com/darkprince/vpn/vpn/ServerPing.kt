package com.darkprince.vpn.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.darkprince.vpn.core.model.Protocol
import com.darkprince.vpn.core.model.ProxyProfile
import com.darkprince.vpn.core.xray.XrayConfigBuilder
import libv2ray.Libv2ray
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Пинг до сервера — так, как если бы VPN был выключен.
 *
 * Раньше «пинг» был временем целого запроса через сервер: соединение,
 * рукопожатие протокола, запрос до Google и ответ обратно. Это несколько
 * кругов туда-обратно, и узел в 40 мс от телефона показывал 300–400. Число
 * говорило не о том, близко ли сервер, а о том, сколько шагов в протоколе.
 *
 * Теперь меряется время одного TCP-соединения до сервера — один круг, то же,
 * что показывает компьютерная версия. Соединение открывается на настоящей
 * сети телефона (Wi-Fi или мобильной), явно мимо VPN: включён он или нет,
 * число одно и то же.
 *
 * Узлы поверх UDP (Hysteria2, TUIC, WireGuard) по TCP не отвечают вовсе — им
 * оставлен прежний замер через ядро, иначе рабочий сервер выглядел бы мёртвым.
 */
object ServerPing {
    private const val CONNECT_TIMEOUT_MS = 3_000
    /** Два соединения, берём быстрее: первое иногда платит за пробуждение радио. */
    private const val ATTEMPTS = 2
    private const val REAL_DELAY_URL = "https://www.gstatic.com/generate_204"

    /** Задержка в миллисекундах; -1 — сервер не ответил. Блокирующая. */
    fun measure(context: Context, profile: ProxyProfile): Long {
        val host = profile.address.trim()
        if (overUdp(profile) || host.isEmpty() || host == "-" || profile.port !in 1..65535) {
            return realDelay(context, profile)
        }
        return tcpDelay(context, host, profile.port)
    }

    /** До такого узла TCP-соединение не установить — меряем через ядро. */
    private fun overUdp(profile: ProxyProfile): Boolean {
        val network = profile.network.lowercase()
        return profile.protocol == Protocol.HYSTERIA2 ||
            profile.protocol == Protocol.TUIC ||
            profile.protocol == Protocol.WIREGUARD ||
            // незнакомый протокол: чем он ходит, мы не знаем
            profile.protocol == Protocol.OTHER ||
            network in setOf("kcp", "mkcp", "quic", "hysteria")
    }

    private fun tcpDelay(context: Context, host: String, port: Int): Long {
        val network = physicalNetwork(context)
        // Имя разрешаем заранее и на той же сети: время DNS — не задержка сервера.
        val addresses = runCatching {
            (network?.getAllByName(host) ?: InetAddress.getAllByName(host)).toList()
        }.getOrDefault(emptyList())

        for (address in addresses.take(2)) {
            var best = -1L
            for (attempt in 0 until ATTEMPTS) {
                val ms = connectTime(network, InetSocketAddress(address, port))
                // не ответил — второй раз ждать те же секунды незачем
                if (ms < 0) break
                best = if (best < 0) ms else minOf(best, ms)
            }
            if (best >= 0) return best
        }
        return -1L
    }

    private fun connectTime(network: Network?, target: InetSocketAddress): Long {
        val socket = runCatching { network?.socketFactory?.createSocket() }.getOrNull() ?: Socket()
        return try {
            val start = SystemClock.elapsedRealtimeNanos()
            socket.connect(target, CONNECT_TIMEOUT_MS)
            (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
        } catch (_: Exception) {
            -1L
        } finally {
            runCatching { socket.close() }
        }
    }

    /**
     * Настоящая сеть телефона — не VPN. Приложение и так исключено из своего
     * туннеля, но полагаться на это здесь незачем: сокет, открытый на
     * конкретной сети, в туннель не попадёт при любых настройках.
     */
    private fun physicalNetwork(context: Context): Network? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val isPhysical = { network: Network? ->
            val caps = network?.let { runCatching { manager.getNetworkCapabilities(it) }.getOrNull() }
            caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
        val active = runCatching { manager.activeNetwork }.getOrNull()
        if (isPhysical(active)) return active
        @Suppress("DEPRECATION")
        return runCatching { manager.allNetworks.firstOrNull { isPhysical(it) } }.getOrNull()
    }

    private fun realDelay(context: Context, profile: ProxyProfile): Long = try {
        CoreEnv.ensure(context)
        Libv2ray.measureOutboundDelay(XrayConfigBuilder.build(profile), REAL_DELAY_URL)
    } catch (_: Throwable) {
        -1L
    }
}
