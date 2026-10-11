package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.api.dto.MergePreviewDto
import com.darkprince.vpn.data.repo.EmailLinkResult
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Шаги привязки почты. */
sealed interface EmailLinkStep {
    data object Form : EmailLinkStep
    /** Письмо ушло; [linkedNow] — подтверждение в боте выключено, уже готово. */
    data class Sent(val email: String, val linkedNow: Boolean) : EmailLinkStep
    /** Почта занята другим аккаунтом — ждём код из письма. */
    data class Code(val email: String) : EmailLinkStep
    /** Код верный — показываем оба аккаунта и спрашиваем, чью подписку оставить. */
    data class Choose(val token: String, val preview: MergePreviewDto) : EmailLinkStep
    data object Merged : EmailLinkStep
}

data class EmailLinkUiState(
    val step: EmailLinkStep = EmailLinkStep.Form,
    val busy: Boolean = false,
    val error: String? = null,
)

class EmailLinkViewModel : ViewModel() {
    private val auth = ServiceLocator.authRepository

    private val _state = MutableStateFlow(EmailLinkUiState())
    val state: StateFlow<EmailLinkUiState> = _state

    fun submit(email: String, password: String, repeat: String) {
        val trimmed = email.trim()
        val problem = when {
            !trimmed.contains('@') || !trimmed.substringAfter('@').contains('.') -> "Проверьте адрес почты"
            password.length < 8 -> "Пароль — не короче 8 символов"
            password != repeat -> "Пароли не совпадают"
            else -> null
        }
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }
        run {
            when (val result = auth.linkEmail(trimmed, password)) {
                is EmailLinkResult.Sent -> _state.update {
                    it.copy(step = EmailLinkStep.Sent(result.email, result.linkedNow))
                }
                is EmailLinkResult.NeedMergeCode -> _state.update {
                    it.copy(step = EmailLinkStep.Code(result.email))
                }
                is EmailLinkResult.Failed -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun confirmCode(code: String) {
        if (!code.trim().matches(Regex("\\d{6}"))) {
            _state.update { it.copy(error = "Код — 6 цифр из письма") }
            return
        }
        run {
            val (token, error) = auth.confirmMergeCode(code)
            if (token == null) {
                _state.update { it.copy(error = error) }
                return@run
            }
            val preview = runCatching { auth.mergePreview(token) }.getOrNull()
            if (preview == null) {
                _state.update { it.copy(error = "Не удалось показать аккаунты. Начните заново.") }
            } else {
                _state.update { it.copy(step = EmailLinkStep.Choose(token, preview)) }
            }
        }
    }

    fun merge(keepSubscriptionFrom: Long) {
        val step = _state.value.step as? EmailLinkStep.Choose ?: return
        run {
            val error = auth.executeMerge(step.token, keepSubscriptionFrom)
            _state.update {
                if (error == null) it.copy(step = EmailLinkStep.Merged) else it.copy(error = error)
            }
        }
    }

    fun restart() {
        _state.value = EmailLinkUiState()
    }

    private fun run(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
