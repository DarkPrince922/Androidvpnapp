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

    private val _state = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = _state

    init {
        run()
    }

    fun run() {
        if (_state.value.running) return
        _state.value = DiagnosticsUiState(running = true, checks = emptyList())
        viewModelScope.launch {
            subscription()
            traffic()
            devices()
            server()
            _state.update { it.copy(running = false) }
        }
    }

    private fun add(check: Check) = _state.update { it.copy(checks = it.checks + check) }

    private suspend fun subscription() {
        val days = runCatching { subscriptions.status().daysLeft }.getOrNull()
        add(
            when {
                days == null -> Check(
                    "Подписка",
                    CheckResult.SKIPPED,
                    "Не удалось спросить сервер",
                    "Проверьте, есть ли интернет без VPN.",
                )

                days < 0 -> Check(
                    "Подписка",
                    CheckResult.FAIL,
                    "Истекла",
                    "Подписка закончилась. Туннель поднимается, но сервер не " +
                        "пропускает трафик — продлите на вкладке «Тарифы».",
                )

                days == 0 -> Check(
                    "Подписка",
                    CheckResult.WARN,
                    "Заканчивается сегодня",
                    "Подписка заканчивается сегодня — продлите, чтобы не остаться " +
                        "без связи посреди дня.",
                )

                else -> Check("Подписка", CheckResult.OK, "Осталось $days дн.")
            }
        )
    }

    private suspend fun traffic() {
        val info = runCatching { subscriptions.cachedServers()?.second }.getOrNull()
        val limit = info?.totalBytes ?: 0
        val used = (info?.uploadBytes ?: 0) + (info?.downloadBytes ?: 0)
        add(
            when {
                info == null -> Check("Трафик", CheckResult.SKIPPED, "Нет данных о тарифе")
                // Ноль в лимите у панели значит «без ограничения», а не «ничего нельзя»
                limit <= 0 -> Check("Трафик", CheckResult.OK, "Без ограничения")
                used >= limit -> Check(
                    "Трафик",
                    CheckResult.FAIL,
                    "Израсходован весь объём",
                    "Трафик по тарифу закончился. Выглядит это как «подключено, а " +
                        "ничего не грузится» — докупите пакет или дождитесь обновления " +
                        "лимита.",
                )

                used > limit * 9 / 10 -> Check(
                    "Трафик",
                    CheckResult.WARN,
                    "Осталось меньше десятой части",
                    "Трафик почти закончился — скоро всё перестанет открываться, " +
                        "хотя VPN будет показывать «подключено».",
                )

                else -> Check("Трафик", CheckResult.OK, gigabytes(used, limit))
            }
        )
    }

    private suspend fun devices() {
        val info = runCatching { subscriptions.devicesInfo() }.getOrNull()
        val limit = info?.deviceLimit ?: 0
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
            val (servers, _) = subscriptions.cachedServers() ?: return@runCatching null
            val prefs = ServiceLocator.prefs
            val subId = prefs.selectedSubscriptionFlow.first()
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

    private fun gigabytes(used: Long, limit: Long): String {
        val gb = 1024.0 * 1024 * 1024
        return "%.1f из %.1f ГБ".format(used / gb, limit / gb)
    }
}
