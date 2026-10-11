package com.darkprince.vpn.data.api

import com.darkprince.vpn.data.log.AppLog
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Событие кабинета: «пришли деньги», «подписка продлена», «новое обращение». */
data class CabinetEvent(val type: String, val data: JSONObject) {
    fun long(key: String): Long? = if (data.has(key) && !data.isNull(key)) data.optLong(key) else null
    fun text(key: String): String? = data.optString(key).takeIf { it.isNotBlank() && it != "null" }
}

/**
 * Живые события кабинета — общий сокет /cabinet/ws.
 *
 * Без него приложение узнаёт об оплате, продлении или новом обращении только
 * опросом: раз в несколько секунд, пока открыт экран, и никак — пока он
 * закрыт. Сокет говорит сразу. Опрос при этом остаётся: он страхует на
 * случай, если сокет не поднялся (старый бот, прокси без websocket, http).
 *
 * Держим соединение, пока приложение на экране: [start] и [stop] зовёт
 * активность. Обрыв — переподключаемся с растущей паузой.
 */
object CabinetEvents {
    private val _events = MutableSharedFlow<CabinetEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<CabinetEvent> = _events

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    @Volatile private var socket: WebSocket? = null

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch { runLoop() }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        socket?.close(1000, "app in background")
        socket = null
    }

    /** Вход или выход: соединение с прежним токеном больше не годится. */
    fun sessionChanged() {
        stop()
        start()
    }

    private suspend fun runLoop() {
        var failures = 0
        while (true) {
            val url = socketUrl()
            val token = if (url != null) ServiceLocator.apiClient.accessToken() else null
            if (url == null || token == null) {
                // гость, http-адрес или нет входа — сокета нет, хватает опроса
                delay(60_000)
                continue
            }
            val outcome = connect("$url?token=${android.net.Uri.encode(token)}")
            when {
                outcome.opened -> failures = 0
                outcome.code == 1008 -> {
                    // токен отвергнут: обновляем и пробуем снова
                    ServiceLocator.apiClient.refreshTokens(force = true)
                    failures++
                }
                else -> failures++
            }
            delay(BACKOFF_MS[failures.coerceAtMost(BACKOFF_MS.lastIndex)])
        }
    }

    /**
     * Только wss: токен идёт в строке адреса — так его ждёт бот, — и по
     * открытому ws он ушёл бы в сеть как есть.
     */
    private fun socketUrl(): String? {
        val base = ServiceLocator.prefs.cachedBaseUrl.trimEnd('/')
        if (!base.startsWith("https://")) return null
        return "wss://${base.removePrefix("https://")}/cabinet/ws"
    }

    private class Outcome(val opened: Boolean, val code: Int?)

    /** Держит одно соединение до его закрытия. */
    private suspend fun connect(url: String): Outcome {
        val done = CompletableDeferred<Outcome>()
        var opened = false
        val client = ServiceLocator.apiClient.plainOkHttp.newBuilder()
            .pingInterval(25, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val ws = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened = true
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                    val type = json.optString("type")
                    if (type.isBlank() || type == "pong" || type == "connected") return
                    _events.tryEmit(CabinetEvent(type, json))
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                    done.complete(Outcome(opened, code))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    done.complete(Outcome(opened, code))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!opened) AppLog.write("сокет кабинета: ${t.javaClass.simpleName} ${response?.code ?: ""}")
                    done.complete(Outcome(opened, null))
                }
            },
        )
        socket = ws
        // Сервер отвечает только на текстовый ping: им же держим соединение
        // живым через прокси, которые рвут «молчащие» сокеты.
        val keepAlive = scope.launch {
            while (isActive) {
                delay(30_000)
                ws.send("{\"type\":\"ping\"}")
            }
        }
        return try {
            done.await()
        } finally {
            keepAlive.cancel()
            ws.cancel()
            if (socket === ws) socket = null
        }
    }

    private val BACKOFF_MS = longArrayOf(1_000, 3_000, 10_000, 30_000, 60_000, 120_000)
}
