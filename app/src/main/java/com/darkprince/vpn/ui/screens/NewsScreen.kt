package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.data.api.dto.NewsItemDto
import com.darkprince.vpn.ui.vm.NewsViewModel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Лента новостей сервиса.
 *
 * Новости приходят из админки бота, поэтому и оформление берём оттуда же:
 * у каждой рубрики свой цвет, заданный редактором. Изобретать свою палитру
 * поверх чужой значило бы получить два несогласованных набора цветов.
 */
@Composable
fun NewsScreen(viewModel: NewsViewModel, onOpen: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Заходя в ленту, человек видит всё, что в ней лежит: точку на кнопке
    // гасим сразу, а не после того, как он откроет каждую статью.
    LaunchedEffect(state.items) { viewModel.markSeen() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Новости", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Что нового в сервисе: обновления, работы на серверах, акции.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        when {
            !state.loggedIn -> NewsNotice(
                "Новости доступны в аккаунте. Гостевой доступ по чужой ссылке " +
                    "их не показывает — аккаунта, которому их адресовать, нет."
            )

            state.loading && state.items.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null && state.items.isEmpty() -> NewsNotice(state.error!!)

            state.items.isEmpty() -> NewsNotice("Пока новостей нет. Заглядывайте позже.")

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.items, key = { it.id }) { item ->
                    NewsCard(item) { onOpen(item.slug) }
                }
            }
        }
    }
}

@Composable
private fun NewsNotice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NewsCard(item: NewsItemDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.category?.takeIf { it.isNotBlank() }?.let {
                    CategoryChip(it, item.categoryColor)
                }
                Spacer(Modifier.weight(1f))
                newsDate(item.publishedAt)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            item.excerpt?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Рубрика цветом редактора.
 *
 * Цвет приходит строкой из админки и вполне может оказаться мусором —
 * тогда берём обычный акцент вместо того, чтобы падать на разборе.
 */
@Composable
private fun CategoryChip(text: String, hex: String?) {
    val tint = parseHexColor(hex) ?: MaterialTheme.colorScheme.primary
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = tint,
        modifier = Modifier
            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(5.dp))
            .border(1.dp, tint.copy(alpha = 0.42f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** `#RGB`, `#RRGGBB` и `#RRGGBBAA` — форматы, которые допускает кабинет. */
internal fun parseHexColor(hex: String?): Color? {
    val raw = hex?.trim()?.removePrefix("#")?.takeIf { it.isNotEmpty() } ?: return null
    val expanded = when (raw.length) {
        3 -> raw.map { "$it$it" }.joinToString("")
        4 -> raw.take(3).map { "$it$it" }.joinToString("") + raw[3].let { "$it$it" }
        6, 8 -> raw
        else -> return null
    }
    val value = expanded.toLongOrNull(16) ?: return null
    return if (expanded.length == 8) {
        // из RRGGBBAA в ARGB, которого ждёт Color
        val alpha = value and 0xFF
        Color((alpha shl 24 or (value ushr 8)).toInt())
    } else {
        Color(value.toInt() or 0xFF000000.toInt())
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))

/** Дата публикации; кабинет отдаёт ISO, но пустое и кривое тоже переживаем. */
internal fun newsDate(iso: String?): String? {
    val text = iso?.takeIf { it.isNotBlank() } ?: return null
    return try {
        OffsetDateTime.parse(text).format(dateFormat)
    } catch (_: Exception) {
        try {
            java.time.LocalDateTime.parse(text).format(dateFormat)
        } catch (_: Exception) {
            null
        }
    }
}

/** Карточка одной новости. Тело статьи — HTML, разметку разбирает Compose. */
@Composable
fun NewsArticleScreen(viewModel: NewsViewModel, slug: String) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(slug) { viewModel.openArticle(slug) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        when {
            state.articleLoading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.articleError != null -> NewsNotice(state.articleError!!)

            else -> state.article?.let { article ->
                LazyColumn {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            article.category?.takeIf { it.isNotBlank() }?.let {
                                CategoryChip(it, article.categoryColor)
                            }
                            Spacer(Modifier.weight(1f))
                            newsDate(article.publishedAt)?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(article.title, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            newsBody(article.content, article.excerpt),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

/**
 * Текст статьи.
 *
 * Кабинет отдаёт HTML — тот же, что показывает веб-кабинет. Compose умеет
 * разобрать его сам, без WebView: ради ленты новостей тянуть в приложение
 * целый браузер было бы перебором. Если тела нет, показываем хотя бы
 * анонс — пустой экран хуже короткого.
 */
private fun newsBody(content: String, excerpt: String?): AnnotatedString {
    val html = content.takeIf { it.isNotBlank() }
        ?: return AnnotatedString(excerpt.orEmpty())
    return AnnotatedString.fromHtml(html)
}
