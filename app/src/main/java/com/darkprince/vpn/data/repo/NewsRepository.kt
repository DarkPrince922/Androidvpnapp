package com.darkprince.vpn.data.repo

import com.darkprince.vpn.data.api.ApiClient
import com.darkprince.vpn.data.api.dto.NewsArticleDto
import com.darkprince.vpn.data.api.dto.NewsItemDto
import com.darkprince.vpn.data.prefs.AppPrefs
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.IOException

/**
 * Лента новостей из кабинета.
 *
 * Новости отдаются только авторизованным: гость по чужой ссылке подписки
 * аккаунта не имеет, и спрашивать за него нечего — поэтому лента ему просто
 * не показывается, а не показывается пустой с ошибкой.
 */
class NewsRepository(
    private val client: ApiClient,
    private val prefs: AppPrefs,
) {
    private val api get() = client.api

    val isLoggedIn: Boolean get() = prefs.cachedRefreshToken != null

    suspend fun list(limit: Int = NEWS_PAGE, offset: Int = 0): List<NewsItemDto> =
        api.news(limit = limit, offset = offset).items

    suspend fun article(slug: String): NewsArticleDto = api.newsArticle(slug)

    /**
     * Непрочитанное считаем сами: у кабинета счётчика для новостей нет, а
     * заводить его на своей стороне ради точки на кнопке — избыточно.
     * Запоминаем наибольший показанный id и сравниваем с наибольшим пришедшим.
     * Идентификаторы растут, поэтому «больше» здесь надёжнее даты: у статьи
     * можно поправить published_at задним числом, а id остаётся прежним.
     */
    suspend fun unreadCount(items: List<NewsItemDto>): Int {
        if (items.isEmpty()) return 0
        // Ленту ещё не открывали: помечать новичку непрочитанным весь архив
        // незачем — точка на кнопке должна значить «появилось новое», а не
        // «вы поставили приложение».
        val lastSeen = prefs.newsLastSeenIdFlow.first() ?: return 0
        return items.count { it.id > lastSeen }
    }

    /** Вызывать, когда человек открыл ленту: всё, что в ней было, он увидел. */
    suspend fun markSeen(items: List<NewsItemDto>) {
        val newest = items.maxOfOrNull { it.id } ?: return
        val lastSeen = prefs.newsLastSeenIdFlow.first()
        if (lastSeen == null || newest > lastSeen) prefs.setNewsLastSeenId(newest)
    }

    /** Ленту ещё ни разу не открывали и не помечали. */
    suspend fun firstRun(): Boolean = prefs.newsLastSeenIdFlow.first() == null

    companion object {
        const val NEWS_PAGE = 20
    }
}

/**
 * Человеческий текст вместо кода ответа.
 *
 * Новости — не то, ради чего человек открыл приложение, поэтому ошибка здесь
 * не должна выглядеть поломкой: «не загрузились» и всё, без разбирательств.
 */
fun newsErrorMessage(error: Throwable): String = when {
    error is IOException -> "Нет соединения"
    error is HttpException && error.code() == 401 -> "Сессия истекла. Войдите ещё раз"
    error is HttpException && error.code() == 404 -> "Новость не найдена — возможно, её убрали"
    else -> "Не удалось загрузить новости"
}
