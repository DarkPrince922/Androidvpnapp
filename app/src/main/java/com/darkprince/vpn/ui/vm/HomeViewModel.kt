package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.core.model.ProxyProfile
import com.darkprince.vpn.core.xray.XrayConfigBuilder
import com.darkprince.vpn.data.api.dto.SubscriptionListItem
import com.darkprince.vpn.data.api.dto.SubscriptionStatusResponse
import com.darkprince.vpn.data.repo.SubscriptionUserInfo
import com.darkprince.vpn.data.repo.userMessage
import com.darkprince.vpn.data.update.AppUpdater
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.vpn.CoreEnv
import com.darkprince.vpn.vpn.TrafficStats
import com.darkprince.vpn.vpn.VpnState
import com.darkprince.vpn.vpn.VpnStateStore
import com.darkprince.vpn.vpn.XVpnService
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
    /** Задержки по устойчивому имени узла, а не по номеру в списке. */
    val pings: Map<String, Long> = emptyMap(),
    val pinging: Boolean = false,
    /** Подписки пользователя; переключатель показываем, когда их больше одной. */
    val subscriptions: List<SubscriptionListItem> = emptyList(),
    val selectedSubscriptionId: Long? = null,
) {
    /**
     * Выбранная подписка, а если её в списке уже нет — активная.
     *
     * Раньше запасным вариантом был просто первый элемент, то есть тот, что
     * панель отдала первым. У человека с истёкшей и действующей подпиской
     * первой запросто оказывалась истёкшая, и карточка показывала её: ноль
     * дней и чужой остаток трафика — при живой второй подписке.
     */
    val selectedSubscription: SubscriptionListItem?
        get() = subscriptions.firstOrNull { it.id == selectedSubscriptionId }
            ?: subscriptions.firstOrNull { it.isActive }
            ?: subscriptions.firstOrNull()
}

/**
 * Короткое сообщение об итоге ручного обновления.
 *
 * Раньше нажатие на «обновить» заканчивалось молча: если данные не менялись,
 * человек не мог отличить успешное обновление от того, что кнопка не сработала.
 */
data class Notice(val text: String, val ok: Boolean)

class HomeViewModel : ViewModel() {
    private val subRepo = ServiceLocator.subscriptionRepository
    private val prefs = ServiceLocator.prefs
    private val updater = ServiceLocator.appUpdater

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    val vpnState: StateFlow<VpnState> = VpnStateStore.state
    val vpnStats: StateFlow<TrafficStats> = VpnStateStore.stats
    val vpnError: StateFlow<String?> = VpnStateStore.lastError

    /** Имя узла, который просили, если подключились не к нему. */
    val switchedFrom: StateFlow<String?> = VpnStateStore.switchedFrom

    /** Обновление, о котором стоит сказать. null — говорить нечего. */
    private val _update = MutableStateFlow<AppUpdater.Update?>(null)
    val update: StateFlow<AppUpdater.Update?> = _update

    /** Идёт закачка: кнопку нажимать второй раз незачем. */
    private val _updateBusy = MutableStateFlow(false)
    val updateBusy: StateFlow<Boolean> = _updateBusy

    /** Не получилось скачать. Отдельно от ошибок подписки: это про другое. */
    private val _updateError = MutableStateFlow<String?>(null)
    val updateError: StateFlow<String?> = _updateError

    /** Скачано и всего байт. Ноль во втором — размер неизвестен. */
    private val _updateProgress = MutableStateFlow(0L to 0L)
    val updateProgress: StateFlow<Pair<Long, Long>> = _updateProgress

    /** Итог последнего обновления по кнопке. null — показывать нечего. */
    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice

    fun consumeNotice() {
        _notice.value = null
    }

    init {
        checkUpdate()
        refresh(forceServers = true)
    }

    /**
     * @param notify показать итог сообщением. Нужно, когда обновление запросил
     * человек кнопкой: автоматическое обновление при открытии экрана молчит,
     * иначе сообщение всплывало бы при каждом входе.
     */
    fun refresh(forceServers: Boolean = false, notify: Boolean = false) {
        _state.value = _state.value.copy(loading = true, error = null)
        if (notify) _notice.value = null
        viewModelScope.launch {
            // 1) мгновенно показываем сохранённую подписку (работает офлайн)
            val cachedId = prefs.selectedSubscriptionFlow.first()
            // до первого чтения: забрать наследство старого формата, иначе
            // подписке подсунется общий кэш от другой
            prefs.adoptLegacySelection(cachedId)
            subRepo.cachedServersFor(cachedId)?.let { (cachedServers, cachedInfo) ->
                val selected = resolveSelected(cachedId, cachedServers)
                _state.value = _state.value.copy(
                    servers = cachedServers,
                    subUserInfo = cachedInfo ?: _state.value.subUserInfo,
                    selectedServer = selected,
                )
            }

            // 2) фоном обновляем из сети
            var error: String? = null
            // null — список спросить не удалось; оставляем прежний, иначе
            // подписки пропадут с экрана из-за одной сетевой заминки
            val subs = subRepo.subscriptions() ?: _state.value.subscriptions
            var selectedId = prefs.selectedSubscriptionFlow.first()
            if (subs.isNotEmpty() && subs.none { it.id == selectedId }) {
                // выбранной подписки больше нет — берём активную, иначе первую
                selectedId = (subs.firstOrNull { it.isActive } ?: subs.first()).id
                prefs.setSelectedSubscription(selectedId)
                // номер узнали только сейчас: переносим под него то, что
                // человек успел выбрать, пока номера не было
                prefs.adoptLegacySelection(selectedId)
            }
            _state.value = _state.value.copy(subscriptions = subs, selectedSubscriptionId = selectedId)

            // без аккаунта кабинет недоступен — только подписка по ссылке
            val guest = !ServiceLocator.authRepository.isLoggedIn
            val sub = if (guest) null else try {
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
            // Итог считаем до того, как ошибку погасит кэш: для полосы «ошибка
            // или нет» важно, дошли ли мы до сервера, а не осталось ли что
            // показать на экране.
            if (notify) {
                _notice.value = if (fresh != null) {
                    Notice("Подписка обновлена", ok = true)
                } else {
                    Notice(error ?: "Не удалось обновить подписку", ok = false)
                }
            }
            // если данные в итоге есть — сетевую ошибку не показываем
            if (servers.isNotEmpty()) error = null
            val selected = resolveSelected(selectedId, servers)
            _state.value = _state.value.copy(
                subscription = sub,
                subUserInfo = userInfo,
                servers = servers,
                selectedServer = selected,
                loading = false,
                error = error,
                subscriptions = subs,
                selectedSubscriptionId = selectedId,
            )

            // остальные подписки догружаем фоном, чтобы переключение было
            // мгновенным и работало без сети
            if (subs.size > 1) {
                launch { subRepo.prefetchAllSubscriptions() }
            }
        }
    }

    /** Переключение между подписками: серверы перезагружаются под выбранную. */
    fun selectSubscription(id: Long) {
        if (id == _state.value.selectedSubscriptionId) return
        viewModelScope.launch {
            if (VpnStateStore.state.value == VpnState.CONNECTED) {
                XVpnService.stop(ServiceLocator.appContext)
            }
            subRepo.selectSubscription(id)
            // серверы этой подписки уже могут лежать в кэше — показываем сразу
            val cached = subRepo.cachedServersFor(id)
            val cachedServers = cached?.first ?: emptyList()
            _state.value = _state.value.copy(
                selectedSubscriptionId = id,
                servers = cachedServers,
                subUserInfo = cached?.second,
                selectedServer = resolveSelected(id, cachedServers),
                pings = emptyMap(),
            )
            refresh(forceServers = true)
        }
    }

    /**
     * Номер выбранного узла в текущем списке.
     *
     * Хранится имя, а не номер: в панели узлы переставляют, и номер после
     * этого указывал бы на другую страну. Если сохранённого имени в списке
     * нет — узел убрали или переименовали, берём первый.
     *
     * Заодно переносит старый выбор, сохранённый номером, на имя — один раз
     * при первом запуске после обновления.
     */
    /**
     * Какой узел показать выбранным после обновления списка.
     *
     * Ключ узла — «имя|адрес:порт», и адрес панель меняет чаще, чем кажется:
     * достаточно переехать за другой домен, и точного совпадения уже нет,
     * хотя узел тот же самый. Поэтому если по ключу не нашли — ищем по имени,
     * и только потом сдаёмся.
     *
     * Что нашли, то и записываем обратно. Раньше при промахе просто
     * возвращался ноль, ключ оставался старым, и выбор слетал на первый узел
     * при каждом обновлении подписки, а не один раз.
     */
    private suspend fun resolveSelected(subId: Long?, servers: List<ProxyProfile>): Int {
        if (servers.isEmpty()) return 0
        val savedKey = prefs.selectedServerKeyFor(subId)
        if (savedKey != null) {
            val exact = servers.indexOfFirst { it.key == savedKey }
            if (exact >= 0) return exact
            // адрес мог смениться — имя узла переживает переезд
            val savedName = savedKey.substringBefore('|')
            val byName = servers.indexOfFirst { it.name == savedName }
            if (byName >= 0) {
                prefs.setSelectedServerKeyFor(subId, servers[byName].key)
                return byName
            }
        }
        val legacy = prefs.selectedServerFor(subId).coerceIn(0, servers.size - 1)
        prefs.setSelectedServerKeyFor(subId, servers[legacy].key)
        return legacy
    }

    fun selectServer(index: Int) {
        viewModelScope.launch {
            // Пока список подписок не пришёл, номера в состоянии нет, а
            // выбор надо где-то сохранить. Берём его из хранилища, иначе
            // запись уйдёт под «default» и потеряется, как только номер
            // появится.
            val subId = _state.value.selectedSubscriptionId
                ?: prefs.selectedSubscriptionFlow.first()
            val chosen = _state.value.servers.getOrNull(index)
            prefs.setSelectedServerFor(subId, index)
            if (chosen != null) prefs.setSelectedServerKeyFor(subId, chosen.key)
            _state.value = _state.value.copy(selectedServer = index)
            // при активном VPN сразу переключаемся на выбранный сервер
            val profile = chosen
            val vpnActive = VpnStateStore.state.value == VpnState.CONNECTED ||
                VpnStateStore.state.value == VpnState.CONNECTING
            if (profile != null && vpnActive) {
                XVpnService.start(ServiceLocator.appContext, profile)
            }
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
            val results = ConcurrentHashMap<String, Long>()
            val semaphore = Semaphore(3)
            coroutineScope {
                servers.forEach { profile ->
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
                            results[profile.key] = ms
                            _state.value = _state.value.copy(pings = results.toMap())
                            // Складываем замеры на диск: службе они понадобятся
                            // при переборе запасных узлов, а мерить заново в
                            // момент сбоя — это минуты без связи.
                            ServiceLocator.prefs.savePings(results.toMap())
                        }
                    }
                }
            }
            _state.value = _state.value.copy(pinging = false)
        }
    }

    /**
     * Спрашивает манифест обновлений.
     *
     * Ошибки глотаются внутри AppUpdater: проверка обновлений не должна мешать
     * пользоваться приложением, а показать всё равно нечего.
     */
    fun checkUpdate() {
        viewModelScope.launch {
            val found = updater.check() ?: return@launch
            // полосу про эту версию уже закрывали крестиком
            if (found.versionCode <= prefs.hiddenUpdateCode()) return@launch
            _update.value = found
        }
    }

    /**
     * Качает APK и отдаёт системному установщику.
     *
     * Если разрешение на установку ещё не выдано, сначала уводим в системные
     * настройки: оно выдаётся отдельно каждому приложению, и то, что человек
     * когда-то разрешил браузеру, на нас не распространяется.
     */
    fun installUpdate() {
        val found = _update.value ?: return
        if (!updater.canInstall()) {
            updater.requestInstallPermission()
            return
        }
        if (_updateBusy.value) return

        viewModelScope.launch {
            _updateBusy.value = true
            try {
                _updateError.value = null
                _updateProgress.value = 0L to 0L
                val apk = updater.download(found) { downloaded, total ->
                    // total = -1, когда сервер не сказал размер заранее
                    _updateProgress.value = downloaded to total.coerceAtLeast(0L)
                }
                if (apk == null) {
                    _updateError.value = "Не удалось скачать обновление"
                    return@launch
                }
                updater.install(apk)
            } finally {
                _updateBusy.value = false
            }
        }
    }

    /** Больше не показывать полосу про эту версию. */
    fun hideUpdate() {
        val found = _update.value ?: return
        _update.value = null
        viewModelScope.launch { prefs.setHiddenUpdateCode(found.versionCode) }
    }
}
