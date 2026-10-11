package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.api.dto.FaqPageDto
import com.darkprince.vpn.data.api.dto.InfoVisibilityDto
import com.darkprince.vpn.data.repo.userMessage
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Документ сервиса: что показать в списке и как его достать. */
enum class DocKind(val title: String) {
    RULES("Правила сервиса"),
    OFFER("Публичная оферта"),
    PRIVACY("Политика конфиденциальности"),
    RECURRENT("Условия автоплатежей"),
}

data class DocsUiState(
    val loading: Boolean = true,
    val visibility: InfoVisibilityDto = InfoVisibilityDto(),
    val faq: List<FaqPageDto> = emptyList(),
    /** Открытый документ: заголовок и текст. */
    val openTitle: String? = null,
    val openContent: String? = null,
    val openLoading: Boolean = false,
    val error: String? = null,
) {
    val docs: List<DocKind>
        get() = DocKind.entries.filter {
            when (it) {
                DocKind.RULES -> visibility.rules
                DocKind.OFFER -> visibility.offer
                DocKind.PRIVACY -> visibility.privacy
                DocKind.RECURRENT -> visibility.recurrent
            }
        }
}

/** Вопросы и ответы, правила, оферта и политика — то, что настроено в боте. */
class DocsViewModel : ViewModel() {
    private val api get() = ServiceLocator.apiClient.api

    private val _state = MutableStateFlow(DocsUiState())
    val state: StateFlow<DocsUiState> = _state

    init {
        viewModelScope.launch {
            val visibility = async { runCatching { api.infoVisibility() }.getOrNull() }
            val faq = async { runCatching { api.faq() }.getOrDefault(emptyList()) }
            val v = visibility.await()
            _state.update {
                it.copy(
                    loading = false,
                    visibility = v ?: it.visibility,
                    faq = faq.await().sortedBy { page -> page.order },
                    error = if (v == null) "Не удалось загрузить список документов" else null,
                )
            }
        }
    }

    fun openDoc(kind: DocKind) {
        _state.update { it.copy(openTitle = kind.title, openContent = null, openLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val doc = when (kind) {
                    DocKind.RULES -> api.rules()
                    DocKind.OFFER -> api.publicOffer()
                    DocKind.PRIVACY -> api.privacyPolicy()
                    DocKind.RECURRENT -> api.recurrentTerms()
                }
                _state.update { it.copy(openContent = doc.content, openLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(openLoading = false, error = e.userMessage()) }
            }
        }
    }

    fun openFaq(page: FaqPageDto) {
        _state.update { it.copy(openTitle = page.title, openContent = page.content, openLoading = false, error = null) }
    }

    fun close() {
        _state.update { it.copy(openTitle = null, openContent = null, openLoading = false) }
    }
}
