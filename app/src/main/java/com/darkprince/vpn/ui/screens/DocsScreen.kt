package com.darkprince.vpn.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.ui.theme.GroupCard
import com.darkprince.vpn.ui.theme.RowDivider
import com.darkprince.vpn.ui.theme.SectionHeader
import com.darkprince.vpn.ui.theme.SettingsRow
import com.darkprince.vpn.ui.vm.DocsViewModel

/** Вопросы и ответы и документы сервиса — из бота, как в веб-кабинете. */
@Composable
fun DocsScreen(viewModel: DocsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val title = state.openTitle
    if (title != null) {
        BackHandler(onBack = viewModel::close)
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                IconButton(onClick = viewModel::close) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            when {
                state.openLoading -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                else -> Text(
                    docText(state.openContent.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                )
            }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Вопросы и документы", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))
        if (state.loading) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (state.faq.isNotEmpty()) {
            SectionHeader("Частые вопросы")
            GroupCard {
                state.faq.forEachIndexed { index, page ->
                    if (index > 0) RowDivider()
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        title = page.title,
                        onClick = { viewModel.openFaq(page) },
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        if (state.docs.isNotEmpty()) {
            SectionHeader("Документы")
            GroupCard {
                state.docs.forEachIndexed { index, kind ->
                    if (index > 0) RowDivider()
                    SettingsRow(
                        icon = Icons.Default.Description,
                        title = kind.title,
                        onClick = { viewModel.openDoc(kind) },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Текст документа. В боте его пишут кто как: разметкой Telegram (HTML) или
 * Markdown — у документов по умолчанию заголовки «# …». Оба вида приводим к
 * размеченному тексту; голые решётки и звёздочки читать неприятно.
 */
internal fun docText(content: String): AnnotatedString {
    val looksHtml = Regex("<(b|i|u|s|a|p|br|strong|em|code|pre|h[1-6]|ul|ol|li)[\\s>/]", RegexOption.IGNORE_CASE)
        .containsMatchIn(content)
    val html = if (looksHtml) {
        content.replace("\n", "<br>")
    } else {
        content
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .lines()
            .joinToString("<br>") { line ->
                val heading = Regex("^#{1,6}\\s+(.*)").find(line)
                when {
                    heading != null -> "<b>${heading.groupValues[1]}</b>"
                    line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") ->
                        "• " + line.trimStart().drop(2)
                    else -> line
                }
            }
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
    }
    return AnnotatedString.fromHtml(html)
}
