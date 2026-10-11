package com.darkprince.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.darkprince.vpn.R
import com.darkprince.vpn.core.model.ProxyProfile
import com.darkprince.vpn.data.log.AppLog
import com.darkprince.vpn.core.xray.XrayConfigBuilder
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.ui.MainActivity
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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

        /**
         * Пауза перед переподключением при смене сети. Переход с Wi-Fi на
         * мобильную даёт несколько событий подряд, и без паузы туннель
         * перезапускался бы по разу на каждое.
         */
        private const val RECONNECT_DELAY_MS = 1500L
        private const val RETRY_BASE_MS = 4_000L
        private const val RETRY_MAX_MS = 120_000L
        private const val MAX_FAILOVER = 3
        private const val TUN_ADDRESS_V6 = "fd66:6470:7276::1"
        /** Отвечает пустым 204 и есть почти везде; его же меряет пинг. */
        private val PROBE_URLS = listOf(
            "https://www.gstatic.com/generate_204",
            "https://cp.cloudflare.com/generate_204",
        )
        private const val PROBE_TIMEOUT_S = 6L
        private const val UNKNOWN_PING = 10_000L
        private const val DEAD_PING = 100_000L

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

        /**
         * Забыть последний профиль — при выходе из аккаунта и отказе от
         * чужой подписки.
         *
         * Плитка в шторке и автоподключение смотрят только на этот файл.
         * Пока он лежал, после «Выйти из аккаунта» одно нажатие на плитку
         * снова поднимало VPN по подписке вышедшего аккаунта.
         */
        fun forgetProfile(context: Context) {
            File(context.filesDir, PROFILE_FILE).delete()
        }

        /**
         * Подменить сохранённый узел свежей версией того же узла из только
         * что скачанной подписки: ключи могли смениться (сброс ссылки), а
         * плитка и автоподключение читают только этот файл.
         */
        fun refreshSavedProfile(context: Context, fresh: List<ProxyProfile>) {
            runCatching {
                val file = File(context.filesDir, PROFILE_FILE)
                if (!file.exists()) return
                val saved = Json.decodeFromString(ProxyProfile.serializer(), file.readText())
                val match = fresh.firstOrNull { it.key == saved.key }
                    ?: fresh.firstOrNull { it.name == saved.name }
                    ?: return
                if (match != saved) {
                    file.writeText(Json.encodeToString(ProxyProfile.serializer(), match))
                }
            }
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
    /** Запуски, которые уже приняты, но ещё ждут своей очереди к vpnMutex.
     *  Пока такой есть, остановка не должна гасить службу целиком. */
    private val pendingStarts = AtomicInteger(0)
    private var activeProfileName: String = ""
    private var lastPingMs: Long? = null

    /** Сколько раз подряд не удалось подняться. Сбрасывается при успехе. */
    private var attempt = 0

    /** Узлы, уже опробованные в текущем заходе перебора. */
    private val tried = mutableSetOf<String>()

    /** Кого просили изначально — чтобы честно сказать о подмене. */
    private var requested: String? = null

    /** Слежение за сетью под туннелем; null — не подписаны. */
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var underlyingNetwork: Network? = null
    /** Все сети с интернетом, кроме VPN. Меняются только в потоке колбэка. */
    private val availableNetworks = LinkedHashSet<Network>()
    /** Рабочая сеть пропала, а другой не было: переподключимся на первой новой. */
    private var networkWasLost = false
    private var reconnectJob: Job? = null

    private val coreCallback = object : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(status: Long, message: String?): Long = 0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        return when (intent?.action) {
            ACTION_STOP -> {
                // Выключили сами — кнопкой, плиткой или из уведомления.
                // Автоподключение не должно поднимать туннель обратно.
                val stopId = startId
                // запись — в фоне: ожидание диска в главном потоке подвешивало
                // приложение, если служба поднималась ради остановки
                scope.launch {
                    runCatching { ServiceLocator.prefs.setUserStoppedBlocking(true) }
                    vpnMutex.withLock { stopVpn(stopId) }
                }
                START_NOT_STICKY
            }
            ACTION_PING -> {
                scope.launch { measurePing() }
                START_STICKY
            }
            ACTION_START -> startFromSaved(startId, bySystem = false)
            // Система поднимает нас сама в двух случаях: «Постоянный VPN» из
            // настроек телефона присылает свою команду, а после гибели
            // процесса START_STICKY приходит вовсе без намерения. Раньше оба
            // молча пропускались: сервис висел без туннеля, а с «блокировать
            // соединения без VPN» человек оставался без интернета.
            VpnService.SERVICE_INTERFACE, null -> startFromSaved(startId, bySystem = true)
            else -> START_NOT_STICKY
        }
    }

    /**
     * Поднимает туннель по сохранённому профилю.
     *
     * [bySystem] — запуск пришёл не от человека, а от системы. Флаг ручного
     * выключения тогда не трогаем: «Постоянный VPN» — это выбор человека в
     * настройках телефона, а перезапуск после гибели процесса означает, что
     * туннель в тот момент работал.
     */
    private fun startFromSaved(startId: Int, bySystem: Boolean): Int {
        // уведомление показываем первым: после startForegroundService
        // система даёт лишь несколько секунд, иначе убивает процесс
        startForeground(
            NOTIFICATION_ID,
            buildNotification(activeProfileName.ifBlank { "Подключение…" })
        )
        val profile = readSavedProfile()
        if (profile == null) {
            AppLog.write(
                if (bySystem) "система запустила VPN, но сервер ещё ни разу не выбирали"
                else "запуск без выбранного сервера"
            )
            VpnStateStore.setState(VpnState.ERROR, "Сервер не выбран")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (bySystem) {
            AppLog.write("VPN поднят системой: постоянный VPN или перезапуск службы")
        } else {
            runCatching { ServiceLocator.prefs.setUserStoppedBlocking(false) }
        }
        activeProfileName = profile.name
        lastPingMs = null
        // Явный запуск начинает новый заход перебора: то, что не
        // поднялось десять минут назад, могло уже подняться.
        tried.clear()
        requested = profile.name
        VpnStateStore.setSwitchedFrom(null)
        updateNotificationForce(profile.name)
        // повторный START на работающем сервисе = смена сервера:
        // старый туннель гасится и сразу поднимается новый
        pendingStarts.incrementAndGet()
        scope.launch {
            // Подписка закончилась — подключаться бессмысленно: сервер не
            // пустит, а туннель без интернета светился бы «Подключено».
            // Только по нажатию человека: «постоянный VPN» не трогаем.
            if (!bySystem && subscriptionExpired()) {
                vpnMutex.withLock {
                    pendingStarts.decrementAndGet()
                    AppLog.write("подписка закончилась — не подключаюсь")
                    VpnStateStore.setState(VpnState.ERROR, "Подписка закончилась — продлите её в «Тарифах»")
                    notifyExpired()
                    stopVpn()
                }
                return@launch
            }
            vpnMutex.withLock {
                pendingStarts.decrementAndGet()
                startVpn(profile)
            }
        }
        return START_STICKY
    }

    /**
     * Истекла ли подписка. Сеть спрашиваем, только если об этом говорит уже
     * сохранённый срок: обычное подключение не должно ждать кабинета, а при
     * белых списках он может и не ответить. Не смогли уточнить — пускаем.
     */
    private suspend fun subscriptionExpired(): Boolean {
        val repo = ServiceLocator.subscriptionRepository
        val expire = runCatching { repo.cachedServers()?.second?.expireUnix }.getOrNull() ?: return false
        if (expire <= 0 || expire * 1000 > System.currentTimeMillis()) return false
        return withTimeoutOrNull(8_000) { runCatching { repo.freshlyExpired() }.getOrNull() } == true
    }

    private fun notifyExpired() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel("subscription", "Подписка", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val intent = Intent(this, com.darkprince.vpn.ui.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(com.darkprince.vpn.ui.MainActivity.EXTRA_OPEN, "plans")
        val pending = PendingIntent.getActivity(
            this, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = "VPN не подключён: подписка закончилась. Продлите её — это минута."
        val notification = Notification.Builder(this, "subscription")
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle("Подписка закончилась")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(7, notification) }
    }

    private fun startVpn(profile: ProxyProfile) {
        VpnStateStore.setState(VpnState.CONNECTING)
        VpnStateStore.setActiveProfile(profile.name)
        // При включённой защите интерфейс переживает пересборку туннеля:
        // establish() ниже заменит его на новый, а до тех пор трафику
        // по-прежнему некуда идти, кроме как в тупик.
        val guarded = killSwitch()
        teardown(keepTun = guarded)
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
                // IPv6 тоже в туннель. Без этого на сетях с IPv6 — а у
                // мобильных операторов он всё чаще — часть трафика шла мимо
                // VPN, с настоящим адресом. Если сервер IPv6 не умеет,
                // приложения просто откатятся на IPv4: так устроены все
                // современные клиенты, и это лучше утечки.
                .addAddress(TUN_ADDRESS_V6, 126)
                .addRoute("::", 0)
            applyAppFilter(builder)
            val fd = builder.establish()
                ?: throw IllegalStateException("Нет разрешения на VPN")
            // Защита оставила старый интерфейс жить до появления нового —
            // теперь он заменён, и его дескриптор надо закрыть, иначе он
            // копится с каждым переподключением.
            tunFd?.takeIf { it !== fd }?.let { runCatching { it.close() } }
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

            // 4. Туннель поднят — но это ещё не значит, что через него идёт
            // трафик: ядро стартует и с мёртвым сервером. Раньше здесь сразу
            // горело «Подключено», а перебор узлов срабатывал только на
            // ошибках запуска, то есть почти никогда.
            if (!internetWorks()) {
                tried += profile.key
                val spare = if (physicalNetworkOnline()) nextCandidate() else null
                if (spare != null) {
                    AppLog.write("${profile.name}: туннель есть, а интернета через него нет — пробую ${spare.name}")
                    updateNotificationForce("${profile.name} не отвечает · пробую ${spare.name}")
                    saveProfile(spare)
                    startVpn(spare)
                    return
                }
                // Запасного нет или нет самой сети под туннелем — тогда
                // сервер не виноват. Туннель не рушим: связь может вернуться
                // через секунду, а выключенный VPN — это утечка.
                AppLog.write("${profile.name}: интернета через туннель пока нет, оставляю подключение")
            }

            VpnStateStore.setState(VpnState.CONNECTED)
            attempt = 0
            tried.clear()
            VpnStateStore.setSwitchedFrom(requested?.takeIf { it != profile.name })
            AppLog.write("подключено: ${profile.name}")
            ServiceLocator.apiClient.onNetworkChanged()
            watchNetwork()
            updateNotification()
            startStatsLoop(profile)
            // первый замер задержки сразу после подключения
            scope.launch { measurePing() }
        } catch (e: Throwable) {
            AppLog.write("подключиться не удалось: ${e.javaClass.simpleName}: ${e.message}")
            VpnStateStore.setState(VpnState.ERROR, e.message)
            tried += profile.key
            val spare = nextCandidate()
            when {
                spare != null -> {
                    // Сначала соседи и только потом отступление: живой сервер
                    // рядом полезнее, чем терпеливое ожидание мёртвого.
                    AppLog.write("${profile.name} не поднялся, пробую ${spare.name}")
                    updateNotificationForce("${profile.name} не отвечает · пробую ${spare.name}")
                    saveProfile(spare)
                    startVpn(spare)
                }

                guarded && tunFd != null -> {
                    // Защита включена и интерфейс жив: наружу ничего не уходит.
                    // Службу не гасим — иначе система снимет интерфейс, и дыра,
                    // от которой мы закрывались, откроется сама.
                    AppLog.write("трафик заблокирован до восстановления")
                    updateNotificationForce("Соединение потеряно · трафик заблокирован")
                    scheduleReconnect()
                }

                else -> stopVpn()
            }
        }
    }

    /**
     * Ходит ли через запущенное ядро трафик на самом деле.
     *
     * Запрос идёт в SOCKS-вход ядра, то есть тем же путём, что и весь
     * трафик телефона, до того же сервера. Приложение исключено из
     * собственного туннеля, поэтому петли тут нет.
     */
    private fun internetWorks(): Boolean {
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", XrayConfigBuilder.SOCKS_PORT)))
            .callTimeout(PROBE_TIMEOUT_S, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
        // Два разных адреса: правила маршрутизации из подписки могут пустить
        // один из них мимо прокси или заблокировать, и тогда «нет связи»
        // было бы ложным — а за ним последовал бы ненужный переход на другой
        // сервер.
        PROBE_URLS.forEachIndexed { attemptNo, url ->
            val ok = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute()
                    .use { it.code in 200..399 }
            }.getOrDefault(false)
            if (ok) return true
            AppLog.write("проверка связи: попытка ${attemptNo + 1} без ответа")
        }
        return false
    }

    /**
     * Есть ли интернет у самой сети под туннелем. Система помечает сеть
     * проверенной, когда сама достучалась до интернета. Если не пометила —
     * виноват Wi-Fi или оператор, и перебирать серверы бессмысленно.
     */
    private fun physicalNetworkOnline(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Настройку читаем каждый раз: её могли переключить между попытками. */
    private fun killSwitch(): Boolean =
        runCatching { ServiceLocator.prefs.killSwitchBlocking() }.getOrDefault(false)

    /**
     * Следующий узел для перебора.
     *
     * Порядок — по последнему измеренному пингу, от ближнего к дальнему;
     * узлы, ответившие «нет», уходят в конец: они уже показали, что мертвы.
     * Мерить заново в момент сбоя нельзя — это минуты там, где человек и так
     * остался без связи, поэтому берём то, что намерили раньше.
     *
     * Перебираем не больше трёх: два десятка подряд — это минута ожидания и
     * разряженный аккумулятор, а причина к тому моменту обычно уже не в
     * серверах.
     */
    private fun nextCandidate(): ProxyProfile? {
        if (!runCatching { ServiceLocator.prefs.failoverBlocking() }.getOrDefault(true)) return null
        if (tried.size >= MAX_FAILOVER) return null

        val servers = runCatching {
            runBlocking { ServiceLocator.subscriptionRepository.cachedServers()?.first }
        }.getOrNull().orEmpty()
        if (servers.size < 2) return null

        val pings = runCatching { ServiceLocator.prefs.pingsBlocking() }.getOrDefault(emptyMap())
        return servers
            .filter { it.key !in tried }
            .minByOrNull { profile ->
                when (val ms = pings[profile.key]) {
                    null -> UNKNOWN_PING     // не мерили — после измеренных живых
                    -1L -> DEAD_PING         // мёртв по последнему замеру
                    else -> ms
                }
            }
    }

    /** Кладём выбранный узел в файл, чтобы переподключение подняло именно его. */
    private fun saveProfile(profile: ProxyProfile) {
        runCatching {
            File(filesDir, PROFILE_FILE)
                .writeText(Json.encodeToString(ProxyProfile.serializer(), profile))
        }
    }

    /**
     * Следит за сетью под туннелем и переподнимает его при смене.
     *
     * Без этого переход с Wi-Fi на мобильную выглядит так: значок VPN горит,
     * а трафик не идёт — ядро осталось привязано к исчезнувшей сети.
     *
     * Подписка — только на сети с NET_CAPABILITY_NOT_VPN, то есть наш
     * собственный туннель сюда не попадает никогда. Была попытка следить за
     * «сетью по умолчанию», но на части прошивок пересоздание туннеля при
     * смене сервера приходило туда как «новая сеть»: служба переподключалась,
     * туннель пересоздавался снова, и так по кругу.
     *
     * Сетей с интернетом может быть несколько сразу — Wi-Fi и мобильная.
     * Появление второй — не повод переподключаться: рабочая никуда не делась.
     * Повод — пропажа той, на которой мы работаем: тогда переходим на
     * оставшуюся, а если не осталось ни одной — ждём первую появившуюся.
     */
    private fun watchNetwork() {
        if (networkCallback != null) return
        val manager = getSystemService(ConnectivityManager::class.java) ?: return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                availableNetworks += network
                if (underlyingNetwork != null) return
                // Приложение исключено из своего туннеля, поэтому его сеть по
                // умолчанию — настоящая. Она и есть рабочая, если есть.
                val current = manager.activeNetwork?.takeIf { it in availableNetworks } ?: network
                useNetwork(current)
                if (networkWasLost) {
                    networkWasLost = false
                    AppLog.write("сеть появилась, переподключаюсь")
                    scheduleReconnect()
                }
            }

            override fun onLost(network: Network) {
                availableNetworks -= network
                if (network != underlyingNetwork) return
                val next = manager.activeNetwork?.takeIf { it in availableNetworks }
                    ?: availableNetworks.firstOrNull()
                if (next != null) {
                    useNetwork(next)
                    AppLog.write("сеть сменилась, переподключаюсь")
                    scheduleReconnect()
                } else {
                    underlyingNetwork = null
                    networkWasLost = true
                    AppLog.write("сеть пропала, жду новую")
                }
            }
        }

        runCatching { manager.registerNetworkCallback(request, callback) }
            .onSuccess { networkCallback = callback }
            .onFailure { AppLog.write("следить за сетью не вышло: ${it.javaClass.simpleName}") }
    }

    /** Запомнить рабочую сеть и сообщить системе, поверх чего идёт VPN. */
    private fun useNetwork(network: Network) {
        underlyingNetwork = network
        runCatching { setUnderlyingNetworks(arrayOf(network)) }
    }

    private fun unwatchNetwork() {
        val callback = networkCallback ?: return
        networkCallback = null
        underlyingNetwork = null
        networkWasLost = false
        availableNetworks.clear()
        val manager = getSystemService(ConnectivityManager::class.java)
        runCatching { manager?.unregisterNetworkCallback(callback) }
    }

    /**
     * Переподключение с задержкой: при переходе между сетями события приходят
     * пачкой, и каждое новое отменяет предыдущее ожидание.
     */
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        // Повтор после сбоя ждёт дольше обычной смены сети и с каждым разом
        // ещё дольше: если сервер лежит, долбиться в него раз в полторы
        // секунды значит греть телефон впустую.
        val failed = VpnStateStore.state.value == VpnState.ERROR
        if (failed) attempt += 1 else attempt = 0
        val delayMs = if (failed) {
            (RETRY_BASE_MS * (1L shl (attempt - 1).coerceAtMost(5))).coerceAtMost(RETRY_MAX_MS)
        } else {
            RECONNECT_DELAY_MS
        }

        reconnectJob = scope.launch {
            delay(delayMs)
            vpnMutex.withLock {
                // пока ждали, человек мог отключиться сам — тогда не лезем
                val state = VpnStateStore.state.value
                val retrying = state == VpnState.ERROR && tunFd != null && killSwitch()
                if (state != VpnState.CONNECTED && state != VpnState.CONNECTING && !retrying) {
                    return@withLock
                }
                val profile = readSavedProfile() ?: return@withLock
                startVpn(profile)
            }
        }
    }

    /** Профиль последнего подключения; null — файла нет или он испорчен. */
    private fun readSavedProfile(): ProxyProfile? = try {
        Json.decodeFromString(
            ProxyProfile.serializer(),
            File(filesDir, PROFILE_FILE).readText(),
        )
    } catch (_: Exception) {
        null
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
    /**
     * Останавливает ядро и мост.
     *
     * [keepTun] оставляет сетевой интерфейс поднятым. Это и есть защита от
     * обрыва: пакеты продолжают уходить в туннель, а на том конце их никто
     * не принимает — значит, они никуда не денутся. Закрыть интерфейс здесь
     * означало бы отдать трафик напрямую в сеть ровно на то время, пока мы
     * поднимаемся заново, — то есть именно тогда, когда человек этого меньше
     * всего ждёт.
     */
    private fun teardown(keepTun: Boolean = false) {
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
        if (keepTun) return
        try {
            tunFd?.close()
        } catch (_: Exception) {
        }
        tunFd = null
    }

    private fun stopVpn(stopId: Int = lastStartId) {
        // Слежение снимаем здесь, а не в teardown: teardown вызывается и при
        // переподключении, а подписку на сеть там терять нельзя.
        unwatchNetwork()
        reconnectJob?.cancel()
        reconnectJob = null
        AppLog.write("отключено")
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
        // Если следом за «выключить» уже нажали «включить», тот запуск ждёт
        // за этим мьютексом. Погасить службу сейчас — значит убить и его.
        if (pendingStarts.get() > 0) {
            AppLog.write("следом ждёт новый запуск — службу не гашу")
            return
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(stopId)
    }

    override fun onRevoke() {
        AppLog.write("разрешение на VPN отозвано системой")
        scope.launch { vpnMutex.withLock { stopVpn() } }
    }

    override fun onDestroy() {
        // Ядро и мост здесь не трогаем: их гасит stopVpn в своём потоке под
        // мьютексом. Повторная остановка из главного потока могла прийтись на
        // момент, когда та ещё идёт, — а двойной вызов в нативный код роняет
        // весь процесс вместе с интерфейсом.
        statsJob?.cancel()
        reconnectJob?.cancel()
        unwatchNetwork()
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
