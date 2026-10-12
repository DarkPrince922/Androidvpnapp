package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.api.dto.ActiveDiscountDto
import com.darkprince.vpn.data.api.dto.SubscriptionListItem
import com.darkprince.vpn.data.api.dto.TariffSwitchPreviewDto
import com.darkprince.vpn.data.repo.AutopayInfo
import com.darkprince.vpn.data.repo.DevicesInfo
import com.darkprince.vpn.data.repo.PendingPurchase
import com.darkprince.vpn.data.repo.PeriodPrice
import com.darkprince.vpn.data.repo.PurchaseResult
import com.darkprince.vpn.data.repo.TariffOffer
import com.darkprince.vpn.data.repo.TrafficPackage
import com.darkprince.vpn.data.repo.userMessage
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import com.darkprince.vpn.vpn.VpnState
import com.darkprince.vpn.vpn.VpnStateStore
import com.darkprince.vpn.vpn.XVpnService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Одна подписка человека со всем, что к ней относится.
 *
 * Всё, что кабинет считает по подписке — цены продления, устройства, пакеты
 * трафика, — спрашивается с её номером. Раньше эти запросы уходили без
 * номера, кабинет отвечал про подписку, которую сам считает текущей, и на
 * карточке одного тарифа оказывались чужие цифры и чужая цена.
 */
data class OwnedSubscription(
    val sub: SubscriptionListItem,
    /** Тариф подписки — из него берём периоды, если кабинет не дал своих. */
    val tariff: TariffOffer? = null,
    val devices: DevicesInfo? = null,
    val renewalOptions: List<PeriodPrice> = emptyList(),
    val trafficPackages: List<TrafficPackage> = emptyList(),
    /** Автопродление; null — не загружено или подписке не положено. */
    val autopay: AutopayInfo? = null,
    val loading: Boolean = true,
) {
    /** Автопродление бывает только у купленного периодного тарифа. */
    val autopayAllowed: Boolean
        get() = sub.isTrial != true && sub.isDaily != true && sub.tariffId != null
    val id: Long get() = sub.id
    val title: String get() = sub.displayName

    /** Периоды продления: свои у подписки, иначе тарифные. */
    val periods: List<PeriodPrice>
        get() = renewalOptions.ifEmpty { tariff?.periods.orEmpty() }

    val unlimitedTraffic: Boolean get() = (sub.trafficLimitGb ?: 0.0) <= 0.0

    /** Действующий лимит устройств: он может быть больше тарифного из-за докупки. */
    val deviceLimit: Int? get() = devices?.deviceLimit ?: sub.deviceLimit

    /** Сколько из лимита докуплено сверх тарифа. */
    val extraDevices: Int?
        get() {
            val limit = deviceLimit ?: return null
            val base = tariff?.deviceLimit ?: return null
            return (limit - base).takeIf { it > 0 }
        }

    /** Дней до окончания. Отрицательное — подписка уже истекла. */
    val daysLeft: Int? get() = sub.endDate?.let(::daysUntilDate)
}

data class PlansUiState(
    /** Ваши подписки — сверху экрана. */
    val cards: List<OwnedSubscription> = emptyList(),
    /** Всё, что продаётся. */
    val tariffs: List<TariffOffer> = emptyList(),
    val trialAvailable: Boolean = false,
    /**
     * Подписка, по которой идёт подключение.
     *
     * Выбирают её на главной, под кнопкой; остальные экраны за ней следуют.
     * Здесь она нужна, чтобы показать, какая карточка рабочая, и дать
     * переключиться, не возвращаясь на главную.
     */
    val workingSubscriptionId: Long? = null,
    val loading: Boolean = false,
    val purchasing: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    /** Денег не хватило — экрану пора увести человека на пополнение. */
    val needTopUp: Boolean = false,
    /** Страница банка или кассы, которую надо открыть. */
    val openUrl: String? = null,
    /** Смена тарифа: что выбрали и сколько это стоит. */
    val switch: SwitchState? = null,
    /** Ссылку сбросили — главной пора перекачать серверы. */
    val revoked: RevokeDone? = null,
    /** Действующая персональная скидка. */
    val discount: ActiveDiscountDto? = null,
    /** Сколько предложений ждут, чтобы их забрали. */
    val offersWaiting: Int = 0,
) {
    private val ownedTariffIds: Set<Long>
        get() = cards.mapNotNull { it.sub.tariffId }.toSet()

    /**
     * Магазин: только то, чего у человека ещё нет.
     *
     * Купленный тариф из магазина уходит — он живёт наверху своей карточкой,
     * и продлевают его там. Три одинаковые карточки подряд, из которых две
     * «ваши», читались как список, в котором непонятно, что делать.
     */
    val offers: List<TariffOffer>
        get() = tariffs.filter { it.id !in ownedTariffIds }
}

/** Смена тарифа у подписки [subId]: на какой и во что обойдётся. */
data class SwitchState(
    val subId: Long,
    val fromName: String,
    /** Куда можно перейти. */
    val candidates: List<TariffOffer>,
    val target: TariffOffer? = null,
    val preview: TariffSwitchPreviewDto? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Ссылку подписки сбросили. [working] — по ней шло подключение. */
data class RevokeDone(val subId: Long, val working: Boolean)

private const val STALE_MS = 20_000L

class PlansViewModel : ViewModel() {
    private val repo = ServiceLocator.subscriptionRepository

    private val _state = MutableStateFlow(PlansUiState())
    val state: StateFlow<PlansUiState> = _state

    /** Последняя загрузка не удалась — по возвращении связи пробуем снова. */
    private var lastLoadFailed = false
    private var lastLoadedAt = 0L

    init {
        refresh()
        viewModelScope.launch {
            com.darkprince.vpn.data.api.ServiceHealth.backOnline.collect {
                if (lastLoadFailed && !_state.value.purchasing) refresh()
            }
        }
        // продление, автоплатёж, докупка — сразу перечитываем карточки
        viewModelScope.launch {
            com.darkprince.vpn.data.api.CabinetEvents.events.collect { event ->
                val relevant = event.type.startsWith("subscription.") || event.type.startsWith("autopay.")
                if (relevant && !_state.value.purchasing) refresh()
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val (tariffsOrNull, trial, subsOrNull) = coroutineScope {
                val tariffsDeferred = async { runOrNull { repo.tariffs() } }
                val trialDeferred = async {
                    runOrNull {
                        val info = repo.trialInfo()
                        info.available ?: info.isAvailable ?: false
                    } ?: false
                }
                val subsDeferred = async { repo.subscriptions() }
                Triple(tariffsDeferred.await(), trialDeferred.await(), subsDeferred.await())
            }
            // Не ответил кабинет — оставляем то, что уже на экране. Раньше
            // обрыв сети стирал и подписки, и тарифы, а вернуть их можно было
            // только уйдя с экрана и вернувшись.
            lastLoadFailed = tariffsOrNull == null || subsOrNull == null
            lastLoadedAt = System.currentTimeMillis()
            if (subsOrNull == null && tariffsOrNull == null && (_state.value.cards.isNotEmpty() || _state.value.tariffs.isNotEmpty())) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = "Нет связи с сервисом — показаны прежние данные. Обновим, как только связь вернётся.",
                    )
                }
                return@launch
            }
            val tariffs = tariffsOrNull ?: _state.value.tariffs
            val subs = subsOrNull ?: _state.value.cards.map { it.sub }
            val working = ServiceLocator.prefs.selectedSubscriptionFlow.first()

            // Действующие сверху, истёкшие следом: продлить истёкшую тоже надо
            // где-то, но начинать список с неё незачем.
            val ordered = subs.sortedWith(
                compareByDescending<SubscriptionListItem> { it.isActive }
                    .thenBy { it.endDate ?: "" }
            )
            // подробности прежней карточки переносим: пока догружаются новые,
            // а тем более без сети, цены продления и устройства не пропадают
            val previous = _state.value.cards.associateBy { it.id }
            val cards = ordered.map { sub ->
                val prev = previous[sub.id]
                OwnedSubscription(
                    sub = sub,
                    tariff = tariffs.firstOrNull { it.id == sub.tariffId },
                    devices = prev?.devices,
                    renewalOptions = prev?.renewalOptions.orEmpty(),
                    trafficPackages = prev?.trafficPackages.orEmpty(),
                    autopay = prev?.autopay,
                    loading = prev == null,
                )
            }

            val error = if (tariffs.isEmpty() && subs.isEmpty() && !trial) {
                "Не удалось загрузить тарифы. Обновим сами, как только появится связь."
            } else null

            launch { loadPromo() }

            _state.update {
                it.copy(
                    cards = cards,
                    tariffs = tariffs,
                    trialAvailable = trial,
                    workingSubscriptionId = working,
                    loading = false,
                    error = error,
                )
            }

            // Подробности по каждой подписке — параллельно, и каждая ложится в
            // свою карточку, как только пришла. Ждать самую медленную, чтобы
            // показать все разом, незачем: карточки уже на экране.
            coroutineScope {
                cards.map { card -> async { loadDetails(card.id) } }.awaitAll()
            }
        }
    }

    /** Догружает то, что кабинет считает по конкретной подписке. */
    private suspend fun loadDetails(subId: Long) = coroutineScope {
        val devicesDeferred = async {
            runOrNull {
                // «пустая» сводка (все поля недоступны) — блок не показываем
                repo.devicesInfo(subId).takeIf {
                    it.deviceLimit != null || it.purchaseAvailable || it.reduceAvailable
                }
            }
        }
        val renewalsDeferred = async { runOrNull { repo.renewalOptions(subId) } }
        val trafficDeferred = async { runOrNull { repo.trafficPackages(subId) } }
        val card = _state.value.cards.firstOrNull { it.id == subId }
        val autopayDeferred = async {
            if (card?.autopayAllowed == true) runOrNull { repo.autopayInfo(subId) } else null
        }

        val devices = devicesDeferred.await()
        val renewals = renewalsDeferred.await()
        val traffic = trafficDeferred.await()
        val autopay = autopayDeferred.await()

        // не ответило — оставляем прежнее, а не стираем
        updateCard(subId) {
            it.copy(
                devices = devices ?: it.devices,
                renewalOptions = renewals ?: it.renewalOptions,
                trafficPackages = traffic ?: it.trafficPackages,
                autopay = autopay ?: it.autopay,
                loading = false,
            )
        }
    }

    /** Скидка и предложения — для полосы «для вас скидка» над тарифами. */
    private suspend fun loadPromo() {
        val promo = ServiceLocator.promoRepository
        val discount = runOrNull { promo.activeDiscount() }
        val offers = runOrNull { promo.offers() }.orEmpty()
        _state.update { it.copy(discount = discount, offersWaiting = offers.size) }
    }

    // --- Автопродление ---

    fun setAutopay(subId: Long, enabled: Boolean, daysBefore: Int? = null) {
        val card = _state.value.cards.firstOrNull { it.id == subId } ?: return
        val days = daysBefore ?: card.autopay?.daysBefore ?: 3
        // сразу двигаем переключатель — ответ кабинета либо подтвердит, либо вернёт как было
        updateCard(subId) { it.copy(autopay = it.autopay?.copy(enabled = enabled, daysBefore = days)) }
        _state.update { it.copy(error = null, info = null) }
        viewModelScope.launch {
            val error = repo.setAutopay(subId, enabled, days)
            if (error != null) {
                updateCard(subId) { it.copy(autopay = card.autopay) }
                _state.update { it.copy(error = error) }
            } else {
                _state.update {
                    it.copy(
                        info = if (enabled) "Автопродление включено: спишем с баланса за $days ${daysWord(days)} до конца"
                        else "Автопродление выключено",
                    )
                }
                // включение с баланса отменяет автоплатёж СБП — перечитываем
                runOrNull { repo.autopayInfo(subId) }?.let { info -> updateCard(subId) { it.copy(autopay = info) } }
            }
        }
    }

    /** Автоплатёж через СБП: подтверждают его на странице банка. */
    fun enableRecurrent(subId: Long) {
        _state.update { it.copy(purchasing = true, error = null, info = null) }
        viewModelScope.launch {
            repo.enableRecurrent(subId)
                .onSuccess { url ->
                    bankPending = subId
                    _state.update {
                        it.copy(
                            purchasing = false,
                            openUrl = url,
                            info = "Подтвердите автоплатёж в банке и вернитесь в приложение",
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(purchasing = false, error = e.message) } }
        }
    }

    /** Ушли подтверждать автоплатёж в банк: по возвращении перечитать его состояние. */
    private var bankPending: Long? = null

    fun onResumed() {
        // Вкладка живёт между переходами, и без этого «Тарифы» показывали
        // данные часовой давности: оплатили на «Балансе» — а здесь старый срок.
        val stale = System.currentTimeMillis() - lastLoadedAt > STALE_MS
        if ((lastLoadFailed || stale) && !_state.value.purchasing && !_state.value.loading) {
            bankPending = null
            refresh()
            return
        }
        val subId = bankPending ?: return
        bankPending = null
        viewModelScope.launch {
            runOrNull { repo.autopayInfo(subId) }?.let { info -> updateCard(subId) { it.copy(autopay = info) } }
        }
    }

    fun cancelRecurrent(subId: Long) = act("Автоплатёж через СБП отключён") { repo.cancelRecurrent(subId) }

    fun openUrl(url: String, subId: Long? = null) {
        if (subId != null) bankPending = subId
        _state.update { it.copy(openUrl = url) }
    }

    fun consumeOpenUrl() {
        _state.update { it.copy(openUrl = null) }
    }

    // --- Смена тарифа ---

    fun startSwitch(subId: Long) {
        val card = _state.value.cards.firstOrNull { it.id == subId } ?: return
        val candidates = _state.value.tariffs.filter { it.id != card.sub.tariffId }
        if (candidates.isEmpty()) {
            _state.update { it.copy(info = "Других тарифов сейчас нет") }
            return
        }
        _state.update { it.copy(switch = SwitchState(subId, card.title, candidates), error = null, info = null) }
    }

    fun previewSwitch(target: TariffOffer) {
        val current = _state.value.switch ?: return
        _state.update { it.copy(switch = current.copy(target = target, preview = null, loading = true, error = null)) }
        viewModelScope.launch {
            val result = repo.switchPreview(current.subId, target.id, switchPeriod(target))
            _state.update { state ->
                val now = state.switch?.takeIf { it.subId == current.subId && it.target?.id == target.id }
                    ?: return@update state
                state.copy(
                    switch = now.copy(
                        preview = result.getOrNull(),
                        error = result.exceptionOrNull()?.message,
                        loading = false,
                    )
                )
            }
        }
    }

    /** Назад к выбору тарифа из расчёта. */
    fun backToSwitchList() {
        _state.update { s -> s.copy(switch = s.switch?.copy(target = null, preview = null, error = null, loading = false)) }
    }

    fun cancelSwitch() {
        _state.update { it.copy(switch = null) }
    }

    fun confirmSwitch() {
        val switch = _state.value.switch ?: return
        val target = switch.target ?: return
        val preview = switch.preview ?: return
        val period = switchPeriod(target)
        _state.update { it.copy(switch = null) }
        buy(
            pending = PendingPurchase(
                kind = "switch",
                subscriptionId = switch.subId,
                tariffId = target.id,
                periodDays = period,
                priceKopeks = preview.upgradeCostKopeks,
                missingKopeks = preview.missingAmountKopeks.takeIf { it > 0 } ?: preview.upgradeCostKopeks,
                title = "${switch.fromName} → ${target.name}",
            ),
            success = "Тариф сменён на «${target.name}»",
        ) { repo.switchTariff(switch.subId, target.id, period) }
    }

    /** Срок для запроса смены: бот требует его, но считает доплату по оставшимся дням. */
    private fun switchPeriod(target: TariffOffer): Int = target.periods.firstOrNull()?.days ?: 30

    // --- Пауза суточного тарифа ---

    fun toggleDailyPause(subId: Long) {
        _state.update { it.copy(purchasing = true, error = null, info = null) }
        viewModelScope.launch {
            repo.toggleDailyPause(subId)
                .onSuccess { paused ->
                    _state.update {
                        it.copy(
                            purchasing = false,
                            info = if (paused) "Суточный тариф на паузе: списаний нет, VPN по нему не работает"
                            else "Суточный тариф возобновлён",
                        )
                    }
                    refresh()
                }
                .onFailure { e -> _state.update { it.copy(purchasing = false, error = e.message) } }
        }
    }

    // --- Сброс ссылки ---

    fun revoke(subId: Long) {
        val working = subId == _state.value.workingSubscriptionId
        _state.update { it.copy(purchasing = true, error = null, info = null) }
        viewModelScope.launch {
            val error = repo.revoke(subId)
            if (error == null) {
                _state.update {
                    it.copy(
                        purchasing = false,
                        info = "Ссылка сброшена. Старая больше не работает — в других приложениях " +
                            "добавьте подписку заново",
                        revoked = RevokeDone(subId, working),
                    )
                }
                refresh()
            } else {
                _state.update { it.copy(purchasing = false, error = error) }
            }
        }
    }

    fun deleteSubscription(subId: Long) {
        _state.update { it.copy(purchasing = true, error = null, info = null) }
        viewModelScope.launch {
            val error = repo.deleteSubscription(subId)
            if (error == null) {
                _state.update {
                    it.copy(
                        purchasing = false,
                        info = "Подписка удалена",
                        cards = it.cards.filterNot { card -> card.id == subId },
                    )
                }
                refresh()
            } else {
                _state.update { it.copy(purchasing = false, error = error) }
            }
        }
    }

    fun consumeRevoked() {
        _state.update { it.copy(revoked = null) }
    }

    private fun updateCard(subId: Long, block: (OwnedSubscription) -> OwnedSubscription) {
        _state.update { state ->
            state.copy(
                cards = state.cards.map { if (it.id == subId) block(it) else it },
            )
        }
    }

    /**
     * Сделать эту подписку рабочей — той, по которой идёт подключение.
     *
     * Ровно то же, что делает главный экран, и с теми же последствиями:
     * поднятый туннель гасится, серверы перечитываются под новую подписку.
     * Поэтому это отдельное нажатие, а не побочный эффект просмотра карточки:
     * листать «Тарифы» и остаться без связи никто не подписывался.
     */
    fun makeWorking(subId: Long) {
        if (subId == _state.value.workingSubscriptionId) return
        if (VpnStateStore.state.value.let { it == VpnState.CONNECTED || it == VpnState.CONNECTING }) {
            _state.update { it.copy(info = "Отключите VPN, чтобы сменить подписку") }
            return
        }
        val name = _state.value.cards.firstOrNull { it.id == subId }?.title
        _state.update { it.copy(purchasing = true, error = null, info = null) }
        viewModelScope.launch {
            // Сохранённый профиль — сервер прежней подписки. Оставь его, и
            // автоподключение или «постоянный VPN» подняли бы туннель по ней.
            val wasOn = VpnStateStore.state.value.let {
                it == VpnState.CONNECTED || it == VpnState.CONNECTING
            }
            if (wasOn) XVpnService.stop(ServiceLocator.appContext)
            XVpnService.forgetProfile(ServiceLocator.appContext)
            repo.selectSubscription(subId)
            _state.update {
                it.copy(
                    purchasing = false,
                    workingSubscriptionId = subId,
                    info = (if (name != null) "Подключение переключено на «$name»"
                    else "Подключение переключено") +
                        if (wasOn) ". VPN выключен — выберите сервер и подключитесь снова" else "",
                )
            }
        }
    }

    fun purchase(tariff: TariffOffer, period: PeriodPrice) = buy(
        pending = PendingPurchase(
            kind = "buy",
            tariffId = tariff.id,
            periodDays = period.days,
            priceKopeks = period.priceKopeks,
            missingKopeks = period.priceKopeks,
            title = "${tariff.name} · ${periodTitle(period)}",
        ),
        success = "Подписка оформлена!",
    ) { repo.purchaseTariffChecked(tariff.id, period.days) }

    fun renew(subId: Long, period: PeriodPrice) {
        val card = _state.value.cards.firstOrNull { it.id == subId }
        buy(
            pending = PendingPurchase(
                kind = "renew",
                subscriptionId = subId,
                periodDays = period.days,
                priceKopeks = period.priceKopeks,
                missingKopeks = period.priceKopeks,
                title = "${card?.title ?: "Подписка"} · продление на ${periodTitle(period)}",
            ),
            success = "Подписка продлена!",
        ) { repo.renewChecked(period.days, subId) }
    }

    /**
     * Купить или продлить. Не хватило денег — запоминаем выбор и просим
     * экран увести человека на пополнение: там уже стоит недостающая сумма,
     * а после зачисления эта покупка оплатится сама.
     */
    private fun buy(pending: PendingPurchase, success: String, block: suspend () -> PurchaseResult) {
        _state.update { it.copy(purchasing = true, error = null, info = null) }
        viewModelScope.launch {
            when (val result = block()) {
                PurchaseResult.Ok -> {
                    ServiceLocator.pendingPurchases.clear()
                    _state.update { it.copy(purchasing = false, info = success) }
                    refresh()
                }
                is PurchaseResult.NotEnough -> {
                    val snapshot = runOrNull { repo.subscriptions() }.orEmpty()
                        .associate { it.id to it.endDate }
                    ServiceLocator.pendingPurchases.save(
                        pending.copy(
                            missingKopeks = result.missingKopeks ?: pending.priceKopeks,
                            endDatesBefore = snapshot,
                        )
                    )
                    _state.update { it.copy(purchasing = false, needTopUp = true) }
                }
                is PurchaseResult.Failed -> _state.update { it.copy(purchasing = false, error = result.message) }
            }
        }
    }

    /** Экран увёл на пополнение — флаг больше не нужен. */
    fun consumeTopUp() {
        _state.update { it.copy(needTopUp = false) }
    }

    private fun periodTitle(period: PeriodPrice): String =
        if (period.daily) "посуточно" else "${period.days} дн."

    fun buyDevices(subId: Long, count: Int) {
        if (count <= 0) return
        act("Устройства добавлены!") { repo.buyDevices(count, subId) }
    }

    fun reduceDevices(subId: Long, newLimit: Int) {
        if (newLimit <= 0) return
        act("Лимит устройств уменьшен") { repo.reduceDevices(newLimit, subId) }
    }

    fun buyTraffic(subId: Long, gb: Int) = act("Трафик добавлен!") {
        repo.buyTraffic(gb, subId)
    }

    fun activateTrial() = act("Пробный период активирован!") {
        if (repo.activateTrial()) null else "Не удалось активировать пробный период"
    }

    /**
     * Общая обвязка покупок: заблокировать кнопки, сходить, показать итог.
     *
     * Удачная покупка всегда заканчивается перечитыванием: меняются и срок, и
     * лимит устройств, и цены продления, а угадывать новое состояние на
     * клиенте — верный способ показать не то, за что человек заплатил.
     */
    private fun act(success: String, block: suspend () -> String?) {
        _state.update { it.copy(purchasing = true, error = null, info = null) }
        viewModelScope.launch {
            val error = try {
                block()
            } catch (e: Exception) {
                e.userMessage()
            }
            if (error == null) {
                _state.update { it.copy(purchasing = false, info = success) }
                refresh()
            } else {
                _state.update { it.copy(purchasing = false, error = error) }
            }
        }
    }

    /**
     * Запрос, которому позволено не удаться: экран собирается из нескольких, и
     * один упавший не должен уносить остальные.
     *
     * Лямбда именно suspend: внутрь ходят в сеть.
     */
    private suspend fun <T> runOrNull(block: suspend () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }
}

/**
 * Сколько дней осталось до даты окончания. Отрицательное число значит, что
 * срок уже вышел.
 *
 * Кабинет отдаёт время в UTC — иногда с поясом, иногда без. Календарный день
 * считаем по часам телефона: подписка до 21:00 UTC у человека в Москве
 * кончается в полночь следующего дня, и взять день прямо из строки значило бы
 * недосчитать сутки.
 */
private fun daysUntilDate(endDate: String): Int? = try {
    val zone = java.time.ZoneId.systemDefault()
    val end = runCatching { java.time.OffsetDateTime.parse(endDate).toInstant() }
        .recoverCatching {
            java.time.LocalDateTime.parse(endDate).toInstant(java.time.ZoneOffset.UTC)
        }
        .map { it.atZone(zone).toLocalDate() }
        .getOrElse { java.time.LocalDate.parse(endDate.take(10)) }
    java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(zone), end).toInt()
} catch (_: Exception) {
    null
}

private fun daysWord(days: Int): String = when {
    days % 10 == 1 && days % 100 != 11 -> "день"
    days % 10 in 2..4 && days % 100 !in 12..14 -> "дня"
    else -> "дней"
}
