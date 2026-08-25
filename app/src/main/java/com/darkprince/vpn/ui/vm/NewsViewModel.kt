package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.api.dto.NewsArticleDto
import com.darkprince.vpn.data.api.dto.NewsItemDto
import com.darkprince.vpn.data.repo.newsErrorMessage
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewsUiState(
    val loggedIn: Boolean = false,
    val items: List<NewsItemDto> = emptyList(),
    val unread: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val article: NewsArticleDto? = null,
    val articleLoading: Boolean = false,
    val articleError: String? = null,
)

/**
 * Лента новостей.
 *
 * Живёт на уровне активности, а не экрана: точку «есть новое» показывает
 * кнопка в «Ещё», и считать её нужно до того, как человек туда зайдёт.
 */
class NewsViewModel : ViewModel() {
    private val repository = ServiceLocator.newsRepository

    private val _state = MutableStateFlow(NewsUiState())
    val state: StateFlow<NewsUiState> = _state

    init {
        refresh()
    }

    /** После входа или выхода: гостю чужая лента не нужна. */
    fun sessionChanged() {
        _state.value = NewsUiState()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val loggedIn = repository.isLoggedIn
            if (!loggedIn) {
                _state.value = NewsUiState(loggedIn = false)
                return@launch
            }
            _state.update { it.copy(loggedIn = true, loading = true, error = null) }
            try {
                val items = repository.list()
                _state.update {
                    it.copy(
                        items = items,
                        unread = repository.unreadCount(items),
                        loading = false,
                    )
                }
                // Первый успешный ответ задаёт точку отсчёта: всё, что было до
                // установки приложения, новостью для человека не является.
                if (repository.firstRun()) repository.markSeen(items)
            } catch (error: Exception) {
                _state.update {
                    it.copy(loading = false, error = newsErrorMessage(error))
                }
            }
        }
    }

    /** Человек открыл ленту — значит увидел всё, что в ней лежит. */
    fun markSeen() {
        val items = _state.value.items
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.markSeen(items)
            _state.update { it.copy(unread = 0) }
        }
    }

    fun openArticle(slug: String) {
        viewModelScope.launch {
            _state.update { it.copy(articleLoading = true, articleError = null, article = null) }
            try {
                val article = repository.article(slug)
                _state.update { it.copy(article = article, articleLoading = false) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(articleLoading = false, articleError = newsErrorMessage(error))
                }
            }
        }
    }

    fun closeArticle() {
        _state.update { it.copy(article = null, articleError = null, articleLoading = false) }
    }
}
