package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.core.xray.XrayConfigBuilder
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.vpn.CoreEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libv2ray.Libv2ray
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Итог одной проверки. */
enum class CheckResult { RUNNING, OK, WARN, FAIL, SKIPPED }

data class Check(
    val title: String,
    val result: CheckResult = CheckResult.RUNNING,
    val detail: String = "",
    /** Что делать человеку. Пусто — делать нечего, всё в порядке. */
    val advice: String? = null,
)

data class DiagnosticsUiState(
    val checks: List<Check> = emptyList(),
    val running: Boolean = false,
) {
    /** Первое найденное препятствие: с него и надо начинать. */
    val verdict: String
        get() {
            if (running) return "Проверяю…"
            val bad = checks.firstOrNull { it.result == CheckResult.FAIL }
                ?: checks.firstOrNull { it.result == CheckResult.WARN }
            return bad?.advice ?: "Ничего подозрительного не нашлось"
        }
}

/**
 * Самопроверка подключения.
 *
 * Порядок проверок повторяет порядок причин в справке — от самой частой к
 * редкой. Это не про полноту: смысл в том, чтобы человек за пять секунд
 * узнал то, ради чего иначе писал бы в поддержку и ждал ответа.
 *
 * Каждая проверка обязана сказать, что делать. Диагноз без совета оставляет
 * человека там же, где он был, только теперь с непонятным словом.
 */
class DiagnosticsViewModel : ViewModel() {
    private val subscriptions = ServiceLocator.subscriptionRepository

    /** Ответ кабинета: он же источник цифр по трафику. */
    private var status: com.darkprince.vpn.data.api.dto.SubscriptionStatusResponse? = null

    /**
     * Подписка, про которую идёт весь разбор.
     *
     * Выбирается один раз в первой проверке, и дальше каждая следующая
     * спрашивает кабинет именно про неё. Без этого получалась чепуха: в
     * заголовке одна подписка, а лимит устройств от другой — той, которую
     * кабинет считает текущей, когда его не спросили прямо.
     */
    private var subject: com.darkprince.vpn.data.api.dto.SubscriptionListItem? = null

    private val _state = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = _state

    init {
        run()
    }

    fun run() {
        if (_state.value.running) return
        _state.value = DiagnosticsUiState(running = true, checks = emptyList())
        // Повторная проверка начинается с чистого листа: вчерашние ответы
        // тут хуже, чем их отсутствие.
        status = null
        subject = null
        viewModelScope.launch {
            subscription()
            traffic()
            devices()
            server()
            _state.update { it.copy(running = false) }
        }
    }

    private fun add(check: Check) = _state.update { it.copy(checks = it.checks + check) }

    /**
     * Срок подписки.
     *
     * Сколько осталось, панель может сказать тремя способами, и ни один не
     * обязателен: числом дней, датой окончания или отметкой времени в
     * заголовке самой подписки. Берём первое, что нашлось.
     *
     * Отдельно ловим случай с несколькими подписками: человек может сидеть
     * на истёкшей, когда рядом есть живая. Приложение при этом честно
     * показывает «подключено», а трафик не идёт — и понять, почему, без
     * подсказки невозможно.
     */
    private suspend fun subscription() {
        val response = runCatching { subscriptions.status() }
        val failure = response.exceptionOrNull()
        if (failure != null) {
            add(
                Check(
                    "Подписка",
                    CheckResult.SKIPPED,
                    reason(failure),
                    "Проверьте, есть ли интернет без VPN.",
                )
            )
            return
        }

        status = response.getOrNull()
        val list = runCatching { subscriptions.subscriptions() }.getOrNull().orEmpty()
        val selectedId = ServiceLocator.prefs.selectedSubscriptionFlow.first()
        // Тот же порядок, что и на главной, иначе проверка говорила бы про
        // одну подписку, а человек смотрел бы на другую.
        val current = list.firstOrNull { it.id == selectedId }
            ?: list.firstOrNull { it.isActive }
            ?: list.firstOrNull()
        subject = current
        val title = current?.let { "Подписка «${it.displayName}»" } ?: "Подписка"

        if (current != null && !current.isActive) {
            val alive = list.firstOrNull { it.isActive }
            add(
                Check(
                    title,
                    CheckResult.FAIL,
                    current.status ?: "Не активна",
                    if (alive != null) {
                        "Выбрана неактивная подписка, а «${alive.displayName}» работает. " +
                            "Переключите её в карточке подписки на главной — трафик " +
                            "пойдёт сразу."
                    } else {
                        "Подписка не активна: туннель поднимется, но сервер не " +
                            "пропустит трафик. Продлите на вкладке «Тарифы»."
                    },
                )
            )
            return
        }

        val days = status?.daysLeft
            ?: status?.endDate?.let(::daysUntil)
            ?: current?.endDate?.let(::daysUntil)
            ?: cachedExpiryDays()

        if (days == null) {
            val active = status?.isActive == true || current?.isActive == true
            add(
                Check(
                    title,
                    if (active) CheckResult.OK else CheckResult.SKIPPED,
                    if (active) "Активна" else "Срок не указан",
                    if (active) {
                        null
                    } else {
                        "Панель не сообщает дату окончания — посмотрите её в " +
                            "карточке подписки на главной."
                    },
                )
            )
            return
        }

        add(
            when {
                days < 0 -> Check(
                    title,
                    CheckResult.FAIL,
                    "Истекла",
                    "Подписка закончилась. Туннель поднимается, но сервер не " +
                        "пропускает трафик — продлите на вкладке «Тарифы».",
                )

                days == 0 -> Check(
                    title,
                    CheckResult.WARN,
                    "Заканчивается сегодня",
                    "Подписка заканчивается сегодня — продлите, чтобы не остаться " +
                        "без связи посреди дня.",
                )

                else -> Check(title, CheckResult.OK, "Осталось $days дн.")
            }
        )
    }

    /**
     * Трафик.
     *
     * Первым делом смотрим на саму подписку из списка: она названа в
     * заголовке, и её цифры относятся именно к ней. Ответ кабинета про
     * «текущую» подписку идёт следом, а кеш — последним: он заведён по
     * выбранной подписке и вполне может быть пуст, если человек переключился
     * на ту, которую ещё ни разу не скачивали.
     */
    private suspend fun traffic() {
        val gb = 1024.0 * 1024 * 1024
        val plan = subject
        val usedGb = plan?.trafficUsedGb ?: status?.trafficUsedGb
        val limitGb = plan?.trafficLimitGb ?: status?.trafficLimitGb
        val header = runCatching {
            if (plan != null) subscriptions.cachedServersFor(plan.id)?.second
            else subscriptions.cachedServers()?.second
        }.getOrNull()

        val used = usedGb ?: header?.let { ((it.uploadBytes ?: 0) + (it.downloadBytes ?: 0)) / gb }
        val limit = limitGb ?: header?.totalBytes?.let { it / gb }

        add(
            when {
                used == null || limit == null ->
                    Check("Трафик", CheckResult.SKIPPED, "Нет данных о тарифе")

                // Ноль в лимите у панели значит «без ограничения»
                limit <= 0 -> Check("Трафик", CheckResult.OK, "Без ограничения")

                used >= limit -> Check(
                    "Трафик",
                    CheckResult.FAIL,
                    "Израсходован весь объём",
                    "Трафик по тарифу закончился. Выглядит это как «подключено, а " +
                        "ничего не грузится» — докупите пакет или дождитесь обновления " +
                        "лимита.",
                )

                used > limit * 0.9 -> Check(
                    "Трафик",
                    CheckResult.WARN,
                    "Осталось меньше десятой части",
                    "Трафик почти закончился — скоро всё перестанет открываться, " +
                        "хотя VPN будет показывать «подключено».",
                )

                else -> Check("Трафик", CheckResult.OK, "%.1f из %.1f ГБ".format(used, limit))
            }
        )
    }

    /**
     * Лимит устройств.
     *
     * Номер подписки обязателен. Без него кабинет отвечает про ту, которую
     * сам считает текущей, и на экране оказывались устройства одной подписки
     * под именем другой — «занято 22 из 16» там, где на самом деле занято
     * одно из пяти.
     */
    private suspend fun devices() {
        val info = runCatching { subscriptions.devicesInfo(subject?.id) }.getOrNull()
        val limit = info?.deviceLimit ?: subject?.deviceLimit ?: 0
        val used = info?.connectedCount ?: 0
        add(
            when {
                info == null -> Check("Устройства", CheckResult.SKIPPED, "Нет данных")
                limit > 0 && used >= limit -> Check(
                    "Устройства",
                    CheckResult.FAIL,
                    "Занято $used из $limit",
                    "Лимит устройств выбран. Новое подключается, но трафик не идёт — " +
                        "отключите лишние в «Ещё» → «Устройства». Переустановленное " +
                        "приложение считается новым устройством.",
                )

                limit > 0 -> Check(
                    "Устройства",
                    CheckResult.OK,
                    "Занято $used из $limit",
                )

                else -> Check("Устройства", CheckResult.OK, "Без ограничения")
            }
        )
    }

    /**
     * Отвечает ли выбранный узел.
     *
     * Замер идёт мимо туннеля, напрямую — так же, как кнопка проверки пинга.
     * Поэтому «нет ответа» здесь означает либо мёртвый узел, либо что до него
     * не пускает сеть; различить это с телефона нельзя, и совет покрывает оба.
     */
    private suspend fun server() {
        val profile = runCatching {
            // Пустой кеш — не повод сдаваться: диагностика затем и нужна,
            // чтобы сходить и посмотреть, а не пересказать вчерашнее.
            val prefs = ServiceLocator.prefs
            val subId = subject?.id ?: prefs.selectedSubscriptionFlow.first()
            val (servers, _) = subscriptions.cachedServersFor(subId)
                ?: subscriptions.fetchServers(forceRefresh = true).takeIf { it.first.isNotEmpty() }
                ?: return@runCatching null
            // Ключ надёжнее номера: сервер могли переставить в панели, и
            // тогда под старым номером окажется чужой узел.
            val key = prefs.selectedServerKeyFor(subId)
            servers.firstOrNull { it.key == key }
                ?: servers.getOrNull(prefs.selectedServerFor(subId))
                ?: servers.firstOrNull()
        }.getOrNull()

        if (profile == null) {
            add(Check("Сервер", CheckResult.SKIPPED, "Список серверов пуст",
                "Обновите подписку кнопкой со стрелками в карточке подписки."))
            return
        }

        val ms = withContext(Dispatchers.IO) {
            runCatching {
                CoreEnv.ensure(ServiceLocator.appContext)
                Libv2ray.measureOutboundDelay(
                    XrayConfigBuilder.build(profile),
                    "https://www.gstatic.com/generate_204",
                )
            }.getOrDefault(-1L)
        }

        add(
            if (ms < 0) {
                Check(
                    "Сервер «${profile.name}»",
                    CheckResult.FAIL,
                    "Не отвечает",
                    "Выбранный узел не отвечает. Выберите другой в списке серверов — " +
                        "или подключитесь, приложение само возьмёт живой, если " +
                        "включён запасной сервер.",
                )
            } else {
                Check("Сервер «${profile.name}»", CheckResult.OK, "$ms мс")
            }
        )
    }

    /** Дней до даты вида «2026-09-01T12:00:00Z». Не разобралась — null. */
    private fun daysUntil(iso: String): Int? = try {
        val end = runCatching { OffsetDateTime.parse(iso).toInstant() }
            .getOrElse { LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC) }
        ChronoUnit.DAYS.between(Instant.now(), end).toInt()
    } catch (_: Exception) {
        null
    }

    /** Запасной источник срока: отметка времени из заголовка подписки. */
    private suspend fun cachedExpiryDays(): Int? {
        val expire = runCatching { subscriptions.cachedServers()?.second?.expireUnix }
            .getOrNull()
            ?.takeIf { it > 0 }
            ?: return null
        return ((expire * 1000 - System.currentTimeMillis()) / 86_400_000L).toInt()
    }

    /** Короткая причина, почему запрос не прошёл. */
    private fun reason(error: Throwable): String = when {
        error is IOException -> "Нет соединения с сервером"
        error is HttpException && error.code() == 401 -> "Сессия истекла"
        error is HttpException -> "Сервер ответил ${error.code()}"
        else -> "Не удалось спросить сервер"
    }
}
