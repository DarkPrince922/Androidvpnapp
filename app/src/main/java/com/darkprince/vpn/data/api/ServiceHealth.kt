package com.darkprince.vpn.data.api

import com.darkprince.vpn.data.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Доступен ли кабинет — и если нет, то почему.
 *
 * Бывает, что сервис лежит: технические работы, упал сервер бота. VPN при
 * этом работать не перестаёт — узлы живут сами по себе, а подписка
 * сохранена в приложении. Ломается только то, что требует кабинета:
 * оплата, продление, докупка, устройства, обращения.
 *
 * Но «кабинет не ответил» — не всегда наша беда. У человека может не быть
 * интернета вовсе или оператор мог включить белые списки, и тогда надпись
 * «сервис недоступен» была бы неправдой. Поэтому, когда кабинет не ответил,
 * спрашиваем ещё два адреса: российский сайт из белых списков и зарубежный.
 *
 * | российский | зарубежный | итог                         |
 * |------------|------------|------------------------------|
 * | нет        | нет        | [Status.OFFLINE] — нет сети   |
 * | да         | нет        | [Status.RESTRICTED] — списки  |
 * | да         | да         | [Status.SERVICE_DOWN] — мы    |
 *
 * «Не ответил» — это обрыв соединения или ответ 5xx. Ответы 4xx — это
 * кабинет, который работает и просто говорит «нет», их сюда не считаем.
 * Как только кабинет снова ответил на любой запрос, всё гаснет само.
 */
object ServiceHealth {
    enum class Status { OK, SERVICE_DOWN, RESTRICTED, OFFLINE }

    private val _status = MutableStateFlow(Status.OK)
    val status: StateFlow<Status> = _status

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Кабинет сейчас недоступен — по любой причине. Кнопкам этого хватает. */
    val down: StateFlow<Boolean> = _status
        .map { it != Status.OK }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val isDown: Boolean get() = _status.value != Status.OK

    /** Один таймаут — ещё не сбой: ждём второго подряд. */
    private const val FAILURES_TO_DOWN = 2
    private const val RECHECK_MS = 30_000L

    /** Российский сайт, который открывается и при белых списках. */
    private const val DOMESTIC_URL = "https://ya.ru"
    /** Зарубежный адрес: если он открывается, ограничений у человека нет. */
    private const val FOREIGN_URL = "https://cp.cloudflare.com/generate_204"

    @Volatile private var failures = 0
    private var ping: (suspend () -> Unit)? = null
    private var reach: (suspend (String) -> Boolean)? = null
    private var recheckJob: Job? = null
    private var classifyJob: Job? = null

    /**
     * [ping] — дешёвый запрос к кабинету без входа, им проверяем возвращение;
     * [reach] — открывается ли адрес вообще (ответил хоть чем-то).
     */
    fun init(ping: suspend () -> Unit, reach: suspend (String) -> Boolean) {
        this.ping = ping
        this.reach = reach
    }

    fun reportOk() {
        failures = 0
        if (_status.value != Status.OK) {
            AppLog.write("кабинет снова отвечает")
            _status.value = Status.OK
            recheckJob?.cancel()
            classifyJob?.cancel()
        }
    }

    fun reportFailure() {
        failures += 1
        if (failures >= FAILURES_TO_DOWN && classifyJob?.isActive != true) {
            classifyJob = scope.launch { classify() }
        }
    }

    /** Почему кабинет не отвечает: мы, оператор или сеть. */
    private suspend fun classify() {
        val check = reach ?: return
        val (domestic, foreign) = kotlinx.coroutines.coroutineScope {
            val d = async { runCatching { check(DOMESTIC_URL) }.getOrDefault(false) }
            val f = async { runCatching { check(FOREIGN_URL) }.getOrDefault(false) }
            d.await() to f.await()
        }
        // пока проверяли, кабинет мог ответить — тогда всё уже хорошо
        if (failures == 0) return
        val next = when {
            !domestic && !foreign -> Status.OFFLINE
            !foreign -> Status.RESTRICTED
            else -> Status.SERVICE_DOWN
        }
        if (_status.value != next) {
            AppLog.write(
                when (next) {
                    Status.OFFLINE -> "кабинет не отвечает: интернета нет"
                    Status.RESTRICTED -> "кабинет не отвечает: похоже, у оператора белые списки"
                    else -> "кабинет не отвечает, а интернет есть — сервис недоступен"
                }
            )
            _status.value = next
        }
        startRecheck()
    }

    /** Пока недоступно, раз в полминуты стучимся сами — не ждём, пока человек что-то нажмёт. */
    private fun startRecheck() {
        if (recheckJob?.isActive == true) return
        recheckJob = scope.launch {
            while (isActive && _status.value != Status.OK) {
                delay(RECHECK_MS)
                runCatching { ping?.invoke() }
                // ping сам сообщит об успехе через перехватчик; при неудаче
                // уточняем причину — сеть могла появиться, а кабинет нет
                if (_status.value != Status.OK) classify()
            }
        }
    }
}
