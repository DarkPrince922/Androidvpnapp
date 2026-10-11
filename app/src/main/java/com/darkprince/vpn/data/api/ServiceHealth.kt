package com.darkprince.vpn.data.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.darkprince.vpn.data.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Доступен ли кабинет.
 *
 * Бывает, что сервис лежит: технические работы, упал сервер бота. VPN при
 * этом работать не перестаёт — узлы живут сами по себе, а подписка
 * сохранена в приложении. Ломается только то, что требует кабинета:
 * оплата, продление, докупка, устройства, обращения.
 *
 * Здесь копится одно знание — «кабинет не отвечает». Экраны по нему
 * показывают спокойную полосу вместо россыпи сетевых ошибок и выключают
 * то, что всё равно не сработало бы. Как только кабинет снова ответил на
 * любой запрос, полоса гаснет сама.
 *
 * «Не отвечает» — это обрыв соединения или ответ 5xx. Ответы 4xx — это
 * кабинет, который работает и просто говорит «нет», их сюда не считаем. И
 * обрыв при отсутствии интернета у самого телефона — тоже не сбой
 * сервиса: тогда виноват Wi-Fi, а не мы.
 */
object ServiceHealth {
    private val _down = MutableStateFlow(false)
    val down: StateFlow<Boolean> = _down
    val isDown: Boolean get() = _down.value

    /** Один таймаут — ещё не сбой: ждём второго подряд. */
    private const val FAILURES_TO_DOWN = 2
    private const val RECHECK_MS = 30_000L

    @Volatile private var failures = 0
    private var context: Context? = null
    private var probe: (suspend () -> Unit)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recheckJob: Job? = null

    /** [ping] — дешёвый запрос к кабинету без входа, им проверяем возвращение. */
    fun init(context: Context, ping: suspend () -> Unit) {
        this.context = context.applicationContext
        probe = ping
    }

    fun reportOk() {
        failures = 0
        if (_down.value) {
            AppLog.write("кабинет снова отвечает")
            _down.value = false
            recheckJob?.cancel()
        }
    }

    fun reportFailure() {
        if (!deviceOnline()) return
        failures += 1
        if (failures >= FAILURES_TO_DOWN && !_down.value) {
            AppLog.write("кабинет не отвечает — показываем, что сервис недоступен")
            _down.value = true
            startRecheck()
        }
    }

    /** Пока сервис лежит, раз в полминуты стучимся сами — не ждём, пока человек что-то нажмёт. */
    private fun startRecheck() {
        recheckJob?.cancel()
        recheckJob = scope.launch {
            while (isActive && _down.value) {
                delay(RECHECK_MS)
                runCatching { probe?.invoke() }
            }
        }
    }

    private fun deviceOnline(): Boolean {
        val manager = context?.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
