package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.repo.DeepLinkAuthEvent
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val baseUrl: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    /** Ссылки для открытия Telegram (deep-link авторизация запущена). */
    val telegramUri: String? = null,
    val telegramWebUri: String? = null,
    val waitingTelegram: Boolean = false,
    val loggedIn: Boolean = false,
    /** Вход по ссылке подписки без аккаунта: VPN работает, кабинет недоступен. */
    val guestMode: Boolean = false,
    /**
     * Сейчас используется подписка, которой поделились. Гость может завести
     * свой аккаунт, не теряя доступ: чужая подписка работает, пока не появится
     * своя.
     */
    val usingSharedSubscription: Boolean = false,
    /**
     * Прошлая сессия оборвалась сама, и вот почему. Показываем на экране
     * входа: без этой строки внезапный выход из аккаунта выглядит как
     * «приложение просто забыло», и человеку нечего сказать в поддержку.
     */
    val sessionEnded: String? = null,
)

class AuthViewModel : ViewModel() {
    private val auth = ServiceLocator.authRepository
    private val prefs = ServiceLocator.prefs

    private val _state = MutableStateFlow(
        AuthUiState(
            baseUrl = prefs.cachedBaseUrl,
            loggedIn = auth.isLoggedIn,
            guestMode = prefs.cachedGuestSubUrl != null && !auth.isLoggedIn,
            usingSharedSubscription = prefs.cachedGuestSubUrl != null,
            sessionEnded = prefs.cachedSessionEnd.takeIf { !auth.isLoggedIn },
        )
    )
    val state: StateFlow<AuthUiState> = _state

    private var telegramJob: Job? = null

    init {
        // чужая подписка отпускается сама, как только заработает своя, —
        // следим за этим, чтобы экраны сразу перестроились
        viewModelScope.launch {
            prefs.guestSubUrlFlow.collect { url ->
                _state.value = _state.value.copy(
                    usingSharedSubscription = url != null,
                    guestMode = url != null && !auth.isLoggedIn,
                )
            }
        }
    }

    fun setBaseUrl(url: String, onDone: () -> Unit) {
        viewModelScope.launch {
            prefs.setBaseUrl(url)
            _state.value = _state.value.copy(baseUrl = prefs.cachedBaseUrl, error = null)
            onDone()
        }
    }

    fun startTelegramAuth() {
        telegramJob?.cancel()
        _state.value = _state.value.copy(loading = true, error = null, info = null, waitingTelegram = false)
        telegramJob = viewModelScope.launch {
            auth.telegramDeepLinkAuth { event ->
                when (event) {
                    is DeepLinkAuthEvent.OpenTelegram -> _state.value = _state.value.copy(
                        loading = false,
                        telegramUri = event.telegramUri,
                        telegramWebUri = event.webUri,
                        waitingTelegram = true,
                    )
                    is DeepLinkAuthEvent.Waiting -> Unit
                    is DeepLinkAuthEvent.Success -> _state.value = _state.value.copy(
                        loading = false,
                        waitingTelegram = false,
                        telegramUri = null,
                        telegramWebUri = null,
                        loggedIn = true,
                        guestMode = false,
                        sessionEnded = null,
                    )
                    is DeepLinkAuthEvent.Failed -> _state.value = _state.value.copy(
                        loading = false,
                        waitingTelegram = false,
                        telegramUri = null,
                        telegramWebUri = null,
                        error = event.message,
                    )
                }
            }
        }
    }

    fun cancelTelegramAuth() {
        telegramJob?.cancel()
        _state.value = _state.value.copy(loading = false, waitingTelegram = false, telegramUri = null)
    }

    fun consumeTelegramUri() {
        _state.value = _state.value.copy(telegramUri = null, telegramWebUri = null)
    }

    fun emailLogin(email: String, password: String) {
        _state.value = _state.value.copy(loading = true, error = null, info = null)
        viewModelScope.launch {
            val error = auth.emailLogin(email, password)
            _state.value = if (error == null) {
                _state.value.copy(loading = false, loggedIn = true, guestMode = false, sessionEnded = null)
            } else {
                _state.value.copy(loading = false, error = error)
            }
        }
    }

    fun emailRegister(email: String, password: String, referralCode: String? = null) {
        _state.value = _state.value.copy(loading = true, error = null, info = null)
        viewModelScope.launch {
            val (success, message) = auth.emailRegister(email, password, referralCode)
            _state.value = when {
                success && auth.isLoggedIn ->
                    _state.value.copy(loading = false, loggedIn = true, guestMode = false, sessionEnded = null)
                success -> _state.value.copy(loading = false, info = message)
                else -> _state.value.copy(loading = false, error = message)
            }
        }
    }

    fun onLoggedOut() {
        _state.value = _state.value.copy(
            loggedIn = false,
            guestMode = false,
            usingSharedSubscription = false,
            error = null,
            info = null,
        )
    }

    fun showQrImageError() {
        _state.value = _state.value.copy(
            loading = false,
            error = "QR-код на картинке не распознан. Попробуйте изображение покрупнее " +
                "или отсканируйте код камерой.",
        )
    }

    /** Вход по QR/ссылке подписки, полученной от владельца. */
    fun loginWithSubscriptionLink(rawLink: String) {
        _state.value = _state.value.copy(loading = true, error = null, info = null)
        viewModelScope.launch {
            val error = ServiceLocator.subscriptionRepository.activateGuestSubscription(rawLink)
            _state.value = if (error == null) {
                _state.value.copy(
                    loading = false,
                    guestMode = !auth.isLoggedIn,
                    usingSharedSubscription = true,
                )
            } else {
                _state.value.copy(loading = false, error = error)
            }
        }
    }

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            auth.forgotPassword(email)
            _state.value = _state.value.copy(
                info = "Если такой e-mail зарегистрирован, мы отправили письмо для сброса пароля."
            )
        }
    }
}
