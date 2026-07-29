package com.darkprince.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.darkprince.vpn.R
import com.darkprince.vpn.core.model.ProxyProfile
import com.darkprince.vpn.core.xray.XrayConfigBuilder
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

class XVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.darkprince.vpn.START"
        const val ACTION_STOP = "com.darkprince.vpn.STOP"
        const val ACTION_PING = "com.darkprince.vpn.PING"
        private const val PROFILE_FILE = "active_profile.json"

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "vpn_state"

        fun start(context: Context, profile: ProxyProfile) {
            // профиль (возможно, с большим raw-конфигом) передаём через файл,
            // а не через Intent — у экстра есть жёсткий лимит размера
            File(context.filesDir, PROFILE_FILE)
                .writeText(Json.encodeToString(ProxyProfile.serializer(), profile))
            val intent = Intent(context, XVpnService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, XVpnService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        /** Есть ли сохранённый профиль для быстрого запуска (плитка, автозапуск). */
        fun hasSavedProfile(context: Context): Boolean =
            File(context.filesDir, PROFILE_FILE).let { it.exists() && it.length() > 0 }

        /**
         * Запуск по ранее сохранённому профилю. Ничего не читает заранее —
         * важно для плитки: система даёт очень короткое окно на запуск
         * foreground-сервиса после нажатия.
         */
        fun startLast(context: Context) {
            val intent = Intent(context, XVpnService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vpnMutex = Mutex()
    private var statsJob: Job? = null
    private var tunFd: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null
    /** Последний startId: останавливаемся только им, иначе новый запуск,
     *  пришедший сразу после остановки, погибнет вместе со старым. */
    private var lastStartId = 0
    private var activeProfileName: String = ""
    private var lastPingMs: Long? = null

    private val coreCallback = object : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(status: Long, message: String?): Long = 0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch { vpnMutex.withLock { stopVpn() } }
                return START_NOT_STICKY
            }
            ACTION_PING -> {
                scope.launch { measurePing() }
                return START_STICKY
            }
            ACTION_START -> {
                // уведомление показываем первым: после startForegroundService
                // система даёт лишь несколько секунд, иначе убивает процесс
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(activeProfileName.ifBlank { "Подключение…" })
                )
                val profile = try {
                    val profileJson = File(filesDir, PROFILE_FILE).readText()
                    Json.decodeFromString(ProxyProfile.serializer(), profileJson)
                } catch (_: Exception) {
                    VpnStateStore.setState(VpnState.ERROR, "Сервер не выбран")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                activeProfileName = profile.name
                lastPingMs = null
                updateNotificationForce(profile.name)
                // повторный START на работающем сервисе = смена сервера:
                // старый туннель гасится и сразу поднимается новый
                scope.launch { vpnMutex.withLock { startVpn(profile) } }
            }
        }
        return START_STICKY
    }

    private fun startVpn(profile: ProxyProfile) {
        VpnStateStore.setState(VpnState.CONNECTING)
        VpnStateStore.setActiveProfile(profile.name)
        teardown()
        try {
            CoreEnv.ensure(this)

            // 1. Запускаем ядро Xray с SOCKS-инбаундом
            val config = XrayConfigBuilder.build(profile)
            val controller = Libv2ray.newCoreController(coreCallback)
            controller.startLoop(config, -1)
            coreController = controller

            // 2. Поднимаем TUN-интерфейс
            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(8500)
                .addAddress("10.10.14.1", 30)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
            applyAppFilter(builder)
            val fd = builder.establish()
                ?: throw IllegalStateException("Нет разрешения на VPN")
            tunFd = fd

            // 3. Мост TUN → SOCKS (hev-socks5-tunnel)
            val yaml = """
                tunnel:
                  mtu: 8500
                socks5:
                  address: 127.0.0.1
                  port: ${XrayConfigBuilder.SOCKS_PORT}
                  udp: udp
                misc:
                  task-stack-size: 20480
                  log-level: error
            """.trimIndent()
            val configFile = File(filesDir, "hev-socks5-tunnel.yaml")
            configFile.writeText(yaml)
            TProxyService.TProxyStartService(configFile.absolutePath, fd.fd)

            VpnStateStore.setState(VpnState.CONNECTED)
            ServiceLocator.apiClient.onNetworkChanged()
            updateNotification()
            startStatsLoop(profile)
            // первый замер задержки сразу после подключения
            scope.launch { measurePing() }
        } catch (e: Throwable) {
            VpnStateStore.setState(VpnState.ERROR, e.message)
            stopVpn()
        }
    }

    private fun startStatsLoop(profile: ProxyProfile) {
        statsJob?.cancel()
        val tags = XrayConfigBuilder.statsTags(profile)
        statsJob = scope.launch {
            var up = 0L
            var down = 0L
            while (isActive) {
                val controller = coreController ?: break
                try {
                    for (tag in tags) {
                        up += controller.queryStats(tag, "uplink")
                        down += controller.queryStats(tag, "downlink")
                    }
                    VpnStateStore.setStats(TrafficStats(uplinkBytes = up, downlinkBytes = down))
                } catch (_: Exception) {
                }
                delay(2000)
            }
        }
    }

    /**
     * Раздельное туннелирование. Android разрешает использовать либо список
     * разрешённых приложений, либо список исключённых, но не оба сразу.
     * Само приложение всегда вне туннеля, иначе трафик ядра зациклится.
     */
    private fun applyAppFilter(builder: Builder) {
        val prefs = ServiceLocator.prefs
        val mode = runBlocking { prefs.splitMode() }
        val apps = runBlocking { prefs.splitApps() } - packageName

        when (mode) {
            "ONLY_SELECTED" -> {
                if (apps.isEmpty()) {
                    // пустой список означал бы «в туннель не идёт никто»
                    disallowSelf(builder)
                    return
                }
                for (pkg in apps) {
                    try {
                        builder.addAllowedApplication(pkg)
                    } catch (_: PackageManager.NameNotFoundException) {
                        // приложение удалили — пропускаем
                    }
                }
            }
            "EXCEPT_SELECTED" -> {
                disallowSelf(builder)
                for (pkg in apps) {
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (_: PackageManager.NameNotFoundException) {
                    }
                }
            }
            else -> disallowSelf(builder)
        }
    }

    private fun disallowSelf(builder: Builder) {
        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: Exception) {
        }
    }

    /** Гасит ядро и туннель, не трогая состояние сервиса (для смены сервера). */
    private fun teardown() {
        statsJob?.cancel()
        statsJob = null
        if (coreController == null && tunFd == null) return
        try {
            TProxyService.TProxyStopService()
        } catch (_: Throwable) {
        }
        try {
            coreController?.stopLoop()
        } catch (_: Throwable) {
        }
        coreController = null
        try {
            tunFd?.close()
        } catch (_: Exception) {
        }
        tunFd = null
    }

    private fun stopVpn() {
        teardown()
        if (VpnStateStore.state.value != VpnState.ERROR) {
            VpnStateStore.setState(VpnState.DISCONNECTED)
        }
        VpnStateStore.setActiveProfile(null)
        VpnStateStore.setStats(TrafficStats())
        lastPingMs = null
        try {
            ServiceLocator.apiClient.onNetworkChanged()
        } catch (_: Exception) {
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    override fun onRevoke() {
        scope.launch { vpnMutex.withLock { stopVpn() } }
    }

    override fun onDestroy() {
        statsJob?.cancel()
        super.onDestroy()
    }

    /**
     * Уведомление с названием сервера, задержкой и кнопками управления —
     * чтобы отключать VPN и мерить пинг прямо из шторки.
     */
    private fun buildNotification(profileName: String): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_vpn),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, XVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pingIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, XVpnService::class.java).setAction(ACTION_PING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = when (val ping = lastPingMs) {
            null -> "Подключено"
            in 0..Long.MAX_VALUE -> "Пинг: $ping мс"
            else -> "Сервер не отвечает"
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(profileName)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(
                Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Пинг", pingIntent).build()
            )
            .addAction(
                Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Остановить", stopIntent).build()
            )
            .build()
    }

    private fun updateNotification() {
        if (VpnStateStore.state.value != VpnState.CONNECTED) return
        updateNotificationForce(activeProfileName)
    }

    private fun updateNotificationForce(title: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(NOTIFICATION_ID, buildNotification(title))
        } catch (_: Exception) {
        }
    }

    /** Замер задержки текущего сервера — по кнопке в уведомлении. */
    private suspend fun measurePing() {
        val profileJson = try {
            File(filesDir, PROFILE_FILE).readText()
        } catch (_: Exception) {
            return
        }
        val profile = try {
            Json.decodeFromString(ProxyProfile.serializer(), profileJson)
        } catch (_: Exception) {
            return
        }
        lastPingMs = try {
            CoreEnv.ensure(this)
            Libv2ray.measureOutboundDelay(
                XrayConfigBuilder.build(profile),
                "https://www.gstatic.com/generate_204",
            )
        } catch (_: Throwable) {
            -1L
        }
        updateNotification()
    }
}
