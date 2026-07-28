package com.darkprince.vpn.data.repo

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
    suspend fun emailRegister(email: String, password: String): Pair<Boolean, String?> {
        return try {
            val auth = api.emailRegister(
                EmailRegisterRequest(email.trim(), password, language = "ru")
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

    suspend fun me(): UserDto? = try {
        val user = api.me()
        prefs.setUserJson(client.json.encodeToString(user))
        user
    } catch (_: Exception) {
        null
    }

    suspend fun logout() {
        val refresh = prefs.cachedRefreshToken
        try {
            if (refresh != null) api.logout(LogoutRequest(refresh))
        } catch (_: Exception) {
        }
        prefs.clearSession()
    }
}

fun Exception.userMessage(): String = when (this) {
    is retrofit2.HttpException -> when (code()) {
        400, 422 -> "Неверные данные. Проверьте введённые значения."
        401 -> "Неверный логин или пароль."
        403 -> "Доступ запрещён."
        404 -> "Сервис не найден. Проверьте адрес кабинета."
        429 -> "Слишком много попыток. Подождите немного."
        in 500..599 -> "Сервер временно недоступен."
        else -> "Ошибка сервера (${code()})."
    }
    is java.io.IOException -> "Нет соединения с сервером. Проверьте интернет и адрес кабинета."
    else -> message ?: "Неизвестная ошибка."
}
