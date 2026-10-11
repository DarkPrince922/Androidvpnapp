package com.darkprince.vpn.data.repo

import kotlinx.coroutines.launch
import com.darkprince.vpn.data.api.dto.MergeExecuteRequest
import com.darkprince.vpn.data.api.dto.MergePreviewDto
import com.darkprince.vpn.data.api.dto.MergeCodeRequest
import com.darkprince.vpn.data.api.dto.EmailLinkRequest
import com.darkprince.vpn.data.api.ApiClient
import com.darkprince.vpn.data.api.dto.AuthResponse
import com.darkprince.vpn.data.api.dto.DeepLinkPollRequest
import com.darkprince.vpn.data.api.dto.EmailLoginRequest
import com.darkprince.vpn.data.api.dto.EmailRegisterRequest
import com.darkprince.vpn.data.api.dto.ForgotPasswordRequest
import com.darkprince.vpn.data.api.dto.LogoutRequest
import com.darkprince.vpn.data.api.dto.UserDto
import com.darkprince.vpn.data.prefs.AppPrefs
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString

sealed interface DeepLinkAuthEvent {
    /** Ссылку нужно открыть в Telegram. */
    data class OpenTelegram(val telegramUri: String, val webUri: String) : DeepLinkAuthEvent
    data object Waiting : DeepLinkAuthEvent
    data class Success(val user: UserDto?) : DeepLinkAuthEvent
    data class Failed(val message: String) : DeepLinkAuthEvent
}

class AuthRepository(
    private val client: ApiClient,
    private val prefs: AppPrefs,
) {
    private val api get() = client.api

    val isLoggedIn: Boolean get() = prefs.cachedRefreshToken != null

    private suspend fun saveSession(auth: AuthResponse) {
        if (auth.accessToken != null) {
            prefs.setTokens(auth.accessToken, auth.refreshToken, auth.expiresIn)
            // вошли — причина прошлого обрыва больше не новость
            prefs.clearSessionEndReason()
        }
        auth.user?.let { prefs.setUserJson(client.json.encodeToString(it)) }
    }

    /**
     * Авторизация через Telegram: запрашиваем одноразовый токен, отдаём ссылку
     * на бота (t.me/<bot>?start=webauth_<token>) и опрашиваем сервер до
     * подтверждения в боте.
     */
    suspend fun telegramDeepLinkAuth(onEvent: suspend (DeepLinkAuthEvent) -> Unit) {
        val request = try {
            api.deepLinkRequest()
        } catch (e: Exception) {
            onEvent(DeepLinkAuthEvent.Failed(e.userMessage()))
            return
        }
        val bot = request.botUsername ?: run {
            onEvent(DeepLinkAuthEvent.Failed("Сервер не вернул имя бота"))
            return
        }
        val startParam = "webauth_${request.token}"
        onEvent(
            DeepLinkAuthEvent.OpenTelegram(
                telegramUri = "tg://resolve?domain=$bot&start=$startParam",
                webUri = "https://t.me/$bot?start=$startParam",
            )
        )

        val deadline = System.currentTimeMillis() + request.expiresIn * 1000L
        while (System.currentTimeMillis() < deadline) {
            try {
                val response = api.deepLinkPoll(DeepLinkPollRequest(request.token))
                when (response.code()) {
                    200 -> {
                        val auth = response.body()
                        if (auth?.accessToken != null) {
                            saveSession(auth)
                            onEvent(DeepLinkAuthEvent.Success(auth.user))
                        } else {
                            onEvent(DeepLinkAuthEvent.Failed("Пустой ответ сервера"))
                        }
                        return
                    }
                    202 -> onEvent(DeepLinkAuthEvent.Waiting)
                    410 -> {
                        onEvent(DeepLinkAuthEvent.Failed("Время авторизации истекло, попробуйте ещё раз"))
                        return
                    }
                    else -> onEvent(DeepLinkAuthEvent.Waiting)
                }
            } catch (_: Exception) {
                // временная сетевая ошибка — продолжаем опрос
            }
            delay(2000)
        }
        onEvent(DeepLinkAuthEvent.Failed("Время авторизации истекло, попробуйте ещё раз"))
    }

    /** Вход по e-mail. Возвращает null при успехе, иначе текст ошибки. */
    suspend fun emailLogin(email: String, password: String): String? {
        return try {
            val auth = api.emailLogin(EmailLoginRequest(email.trim(), password))
            if (auth.accessToken != null) {
                saveSession(auth)
                null
            } else auth.message ?: "Не удалось войти"
        } catch (e: Exception) {
            e.userMessage()
        }
    }

    /**
     * Регистрация по e-mail. Возвращает Pair(успех, сообщение). При успехе без
     * токенов сервер прислал письмо для подтверждения почты.
     */
    suspend fun emailRegister(
        email: String,
        password: String,
        referralCode: String? = null,
    ): Pair<Boolean, String?> {
        return try {
            val auth = api.emailRegister(
                EmailRegisterRequest(
                    email.trim(),
                    password,
                    language = "ru",
                    referralCode = referralCode?.trim()?.takeIf { it.isNotBlank() },
                )
            )
            if (auth.accessToken != null) {
                saveSession(auth)
                true to null
            } else {
                true to (auth.message ?: "Подтвердите e-mail по ссылке из письма, затем войдите.")
            }
        } catch (e: Exception) {
            false to e.userMessage()
        }
    }

    suspend fun forgotPassword(email: String): Boolean = try {
        api.forgotPassword(ForgotPasswordRequest(email.trim()))
        true
    } catch (_: Exception) {
        false
    }

    // ---------- привязка почты ----------

    /**
     * Привязать почту к вошедшему аккаунту (обычно — к вошедшему через
     * Telegram). Бот шлёт письмо со ссылкой подтверждения; после неё в тот же
     * аккаунт можно входить и по почте.
     *
     * Если почта уже принадлежит другому аккаунту, бот предлагает их
     * объединить и шлёт на эту почту код — [EmailLinkResult.NeedMergeCode].
     */
    suspend fun linkEmail(email: String, password: String): EmailLinkResult = try {
        val response = api.linkEmail(EmailLinkRequest(email.trim(), password))
        when {
            response.mergeRequired == true -> EmailLinkResult.NeedMergeCode(email.trim())
            else -> {
                me()
                EmailLinkResult.Sent(
                    email = response.email ?: email.trim(),
                    linkedNow = response.message?.contains("linked", ignoreCase = true) == true,
                )
            }
        }
    } catch (e: Exception) {
        EmailLinkResult.Failed(e.linkMessage())
    }

    /** Код из письма на занятую почту → токен объединения, или текст ошибки. */
    suspend fun confirmMergeCode(code: String): Pair<String?, String?> = try {
        val token = api.verifyEmailMerge(MergeCodeRequest(code.trim())).mergeToken
        if (token != null) token to null else null to "Бот не прислал подтверждение объединения"
    } catch (e: Exception) {
        null to e.linkMessage()
    }

    suspend fun mergePreview(token: String): MergePreviewDto = api.mergePreview(token)

    /**
     * Объединить аккаунты. [keepSubscriptionFrom] — чья подписка останется.
     * Бот выдаёт новую сессию — сохраняем её, старая больше не годится.
     */
    suspend fun executeMerge(token: String, keepSubscriptionFrom: Long): String? = try {
        val result = api.executeMerge(token, MergeExecuteRequest(keepSubscriptionFrom))
        if (result.accessToken != null) {
            saveSession(AuthResponse(accessToken = result.accessToken, refreshToken = result.refreshToken, user = result.user))
        }
        me()
        if (result.success) null else "Объединить не удалось"
    } catch (e: Exception) {
        e.linkMessage()
    }

    suspend fun me(): UserDto? = try {
        val user = api.me()
        prefs.setUserJson(client.json.encodeToString(user))
        user
    } catch (_: Exception) {
        null
    }

    /**
     * Выход: сначала сразу и локально, потом — сообщить серверу.
     *
     * Раньше ждали ответа сервера, и если в этот момент гас VPN, запрос
     * висел до тайм-аута — полминуты, в течение которых экран не отвечал, а
     * повторное нажатие запускало второй выход поверх первого. Теперь
     * сессия стирается сразу, а отзыв токена на сервере уходит фоном и
     * ограничен по времени: не дошёл — токен истечёт сам.
     */
    suspend fun logout() {
        val refresh = prefs.cachedRefreshToken
        prefs.clearSession()
        if (refresh != null) {
            com.darkprince.vpn.di.ServiceLocator.appScope.launch {
                kotlinx.coroutines.withTimeoutOrNull(8_000) {
                    runCatching { api.logout(LogoutRequest(refresh)) }
                }
            }
        }
    }
}

fun Exception.userMessage(): String = when (this) {
    is retrofit2.HttpException -> serverExplanation() ?: when (code()) {
        400, 422 -> "Неверные данные. Проверьте введённые значения."
        401 -> "Неверный логин или пароль."
        402 -> "Не хватает денег на балансе. Пополните баланс и попробуйте снова."
        403 -> "Доступ запрещён."
        404 -> "Сервис не найден. Проверьте адрес кабинета."
        409 -> "Это уже сделано — обновите экран."
        429 -> "Слишком много попыток. Подождите немного."
        in 500..599 -> "Сервер временно недоступен."
        else -> "Ошибка сервера (${code()})."
    }
    is java.io.IOException -> "Нет соединения с сервером. Проверьте интернет и адрес кабинета."
    else -> message ?: "Неизвестная ошибка."
}

/**
 * Объяснение сервера — но только русское.
 *
 * Кабинет часто знает точную причину отказа: «промокод не найден», «тариф
 * недоступен», сколько не хватает на балансе. Раньше всё это выбрасывалось,
 * и любой отказ сводился к «Неверные данные» или «Ошибка сервера (402)».
 *
 * Показываем текст как есть, только если он по-русски: часть ответов
 * кабинета написана по-английски, и такую строку человеку лучше заменить
 * понятной общей фразой по коду. Причина приезжает строкой, объектом
 * {"message": …} или, у ошибок разбора, списком [{"msg": …}].
 *
 * Тело ответа можно прочитать только раз, поэтому разбор живёт здесь, в
 * одном месте, а не размазан по экранам.
 */
private fun retrofit2.HttpException.serverExplanation(): String? {
    val raw = runCatching { response()?.errorBody()?.string() }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: return null
    val text = runCatching {
        val root = org.json.JSONObject(raw)
        when (val detail = root.opt("detail") ?: root.opt("message")) {
            is String -> detail
            is org.json.JSONObject -> detail.optString("message")
            is org.json.JSONArray -> detail.optJSONObject(0)?.optString("msg")
            else -> null
        }
    }.getOrNull()?.trim() ?: return null
    val russian = text.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
    return text.takeIf { russian && it.length <= 300 }
}

sealed interface EmailLinkResult {
    /** Письмо отправлено; [linkedNow] — подтверждение в боте выключено, почта уже привязана. */
    data class Sent(val email: String, val linkedNow: Boolean) : EmailLinkResult
    /** Почта занята другим аккаунтом: на неё ушёл код для объединения. */
    data class NeedMergeCode(val email: String) : EmailLinkResult
    data class Failed(val message: String) : EmailLinkResult
}

/**
 * Ошибки привязки и объединения бот пишет по-английски. Те, что человек
 * реально встретит, переводим; остальное — общим текстом по коду ответа.
 */
private fun Exception.linkMessage(): String {
    val detail = (this as? retrofit2.HttpException)?.let { http ->
        runCatching { http.response()?.errorBody()?.string() }.getOrNull()
    }.orEmpty()
    val known = listOf(
        "already linked to your account" to "Эта почта уже привязана к вашему аккаунту.",
        "cannot be linked" to "Эту почту привязать нельзя.",
        "not configured" to "Бот сейчас не может отправлять письма. Попробуйте позже.",
        "Invalid confirmation code" to "Неверный код. Проверьте письмо и попробуйте снова.",
        "Too many invalid attempts" to "Слишком много неверных попыток. Начните заново.",
        "No pending account merge" to "Время на подтверждение вышло. Начните заново.",
        "invalid or expired" to "Время на подтверждение вышло. Начните заново.",
        "temporarily blocked" to "Объединение пока недоступно. Попробуйте позже.",
        "Too many requests" to "Слишком часто. Подождите минуту и попробуйте снова.",
        "Email auth" to "Вход по почте в боте выключен.",
    )
    known.firstOrNull { (marker, _) -> detail.contains(marker, ignoreCase = true) }?.let { return it.second }
    val code = (this as? retrofit2.HttpException)?.code()
    return when (code) {
        422 -> "Проверьте почту и пароль: пароль — от 8 символов."
        null -> userMessage()
        else -> "Не получилось (ошибка $code). Попробуйте позже."
    }
}
