package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.data.api.dto.SupportMessageDto
import com.darkprince.vpn.data.api.dto.SupportTicketDto
import com.darkprince.vpn.ui.theme.BrandColors
import com.darkprince.vpn.ui.theme.GroupCard
import com.darkprince.vpn.ui.vm.SupportViewModel

@Composable
fun SupportScreen(
    viewModel: SupportViewModel,
    onBack: () -> Unit,
    onOpenTicket: (Long) -> Unit,
    onOpenContact: (String) -> Unit,
    onPickAttachment: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    LaunchedEffect(state.navigateToTicketId) {
        state.navigateToTicketId?.let { ticketId ->
            viewModel.consumeTicketNavigation()
            showCreateDialog = false
            title = ""
            message = ""
            onOpenTicket(ticketId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        SupportHeader(
            title = "Техподдержка",
            onBack = onBack,
            action = {
                IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                }
            },
        )

        Text(
            "Напишите нам — ответы сохраняются здесь и приходят без перезапуска приложения.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        state.error?.let { SupportNotice(it, error = true) }
        state.info?.let { SupportNotice(it, error = false) }

        when {
            state.loading && state.tickets.isEmpty() -> {
                Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            !state.loggedIn || !state.ticketsEnabled -> {
                ContactSupportCard(
                    guest = !state.loggedIn,
                    onOpenContact = { onOpenContact(state.contactUrl) },
                )
            }

            else -> {
                val activeTicket = state.tickets.firstOrNull { !it.isClosed }
                if (state.tickets.isEmpty()) {
                    GroupCard {
                        Column(Modifier.padding(18.dp)) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Обращений пока нет", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Опишите проблему — новое обращение сразу появится у администратора в Telegram.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                } else {
                    Text(
                        "ВАШИ ОБРАЩЕНИЯ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.tickets, key = { it.id }) { ticket ->
                        TicketRow(ticket = ticket, onClick = { onOpenTicket(ticket.id) })
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                Button(
                    onClick = {
                        if (activeTicket != null) onOpenTicket(activeTicket.id)
                        else showCreateDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (activeTicket == null) "Новое обращение" else "Открыть текущее обращение")
                }
                if (state.config?.supportType.equals("both", ignoreCase = true)) {
                    TextButton(
                        onClick = { onOpenContact(state.contactUrl) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("Написать напрямую в Telegram")
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!state.sending) {
                    showCreateDialog = false
                    viewModel.removeAttachment()
                }
            },
            title = { Text("Новое обращение") },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 120) title = it },
                        label = { Text("Тема") },
                        singleLine = true,
                        enabled = !state.sending,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { if (it.length <= 4_000) message = it },
                        label = { Text("Что случилось?") },
                        minLines = 4,
                        maxLines = 7,
                        enabled = !state.sending,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    AttachmentRow(
                        name = state.pendingAttachment?.name,
                        enabled = !state.sending,
                        onPick = onPickAttachment,
                        onAttachLog = viewModel::attachLog,
                        onRemove = viewModel::removeAttachment,
                    )
                    state.error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.createTicket(title, message) },
                    enabled = !state.sending,
                ) {
                    if (state.sending) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Отправить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        viewModel.removeAttachment()
                    },
                    enabled = !state.sending,
                ) { Text("Отмена") }
            },
        )
    }
}

@Composable
fun SupportTicketScreen(
    viewModel: SupportViewModel,
    ticketId: Long,
    onBack: () -> Unit,
    onOpenAttachment: (String) -> Unit,
    onPickAttachment: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ticket = state.activeTicket
    var reply by remember(ticketId) { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(ticketId) {
        viewModel.openTicket(ticketId)
        onDispose { viewModel.leaveTicket() }
    }
    LaunchedEffect(ticket?.messages?.size) {
        val last = ticket?.messages?.lastIndex ?: -1
        if (last >= 0) listState.animateScrollToItem(last)
    }
    LaunchedEffect(state.replySentVersion) {
        if (state.replySentVersion > 0) reply = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 12.dp),
    ) {
        SupportHeader(
            title = ticket?.title ?: "Обращение #$ticketId",
            onBack = onBack,
            subtitle = if (state.socketConnected) "онлайн" else null,
        )

        state.error?.let { SupportNotice(it, error = true) }

        when {
            state.loading && ticket == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            ticket == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Не удалось открыть обращение")
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ticket.messages.sortedBy { it.id }, key = { it.id }) { item ->
                        SupportMessageBubble(
                            message = item,
                            onOpenAttachment = {
                                viewModel.attachmentUrl(item.id)?.let(onOpenAttachment)
                            },
                        )
                    }
                }

                if (ticket.isClosed || ticket.isReplyBlocked) {
                    Text(
                        if (ticket.isClosed) "Обращение закрыто" else "Отправка сообщений ограничена",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(12.dp),
                    )
                } else {
                    AttachmentRow(
                        name = state.pendingAttachment?.name,
                        enabled = !state.sending,
                        onPick = onPickAttachment,
                        onAttachLog = viewModel::attachLog,
                        onRemove = viewModel::removeAttachment,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        OutlinedTextField(
                            value = reply,
                            onValueChange = { if (it.length <= 4_000) reply = it },
                            placeholder = { Text("Сообщение") },
                            minLines = 1,
                            maxLines = 4,
                            enabled = !state.sending,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                viewModel.sendReply(reply)
                            },
                            enabled = !state.sending &&
                                (reply.isNotBlank() || state.pendingAttachment != null),
                        ) {
                            if (state.sending) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Send, contentDescription = "Отправить")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportHeader(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = BrandColors.Success)
            }
        }
        action?.invoke()
    }
}

@Composable
private fun TicketRow(ticket: SupportTicketDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ticket.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    ticketStatus(ticket.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ticket.isClosed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        BrandColors.Success
                    },
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                ticket.lastMessage?.messageText?.takeIf { it.isNotBlank() }
                    ?: if (ticket.lastMessage?.hasMedia == true) "Вложение" else "Без сообщений",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "#${ticket.id} · ${formatSupportTime(ticket.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContactSupportCard(guest: Boolean, onOpenContact: () -> Unit) {
    GroupCard {
        Column(Modifier.padding(18.dp)) {
            Text(
                if (guest) "Для тикета нужен аккаунт" else "Тикеты временно недоступны",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (guest) {
                    "Вы вошли по ссылке чужой подписки. Напишите нам в Telegram — мы всё равно поможем."
                } else {
                    "Свяжитесь с нами напрямую через Telegram."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onOpenContact, modifier = Modifier.fillMaxWidth()) {
                Text("Написать в Telegram")
            }
        }
    }
}

@Composable
private fun SupportMessageBubble(
    message: SupportMessageDto,
    onOpenAttachment: () -> Unit,
) {
    val fromAdmin = message.isFromAdmin
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromAdmin) Arrangement.Start else Arrangement.End,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (fromAdmin) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            ),
            border = if (fromAdmin) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            } else null,
        ) {
            Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                if (fromAdmin) {
                    Text(
                        "Поддержка",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                }
                message.messageText.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                if (message.hasMedia || message.mediaFileId != null || !message.mediaItems.isNullOrEmpty()) {
                    OutlinedButton(onClick = onOpenAttachment) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Открыть вложение")
                    }
                }
                Text(
                    formatSupportTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    name: String?,
    enabled: Boolean,
    onPick: () -> Unit,
    onAttachLog: () -> Unit,
    onRemove: () -> Unit,
) {
    if (name == null) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPick, enabled = enabled) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Файл (до 10 МБ)")
                }
                TextButton(onClick = onAttachLog, enabled = enabled) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Журнал")
                }
            }
            // Человек должен понимать, что именно отправляет.
            Text(
                "Журнал — что происходило с подключением. Ссылки и токены из него вырезаны.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(Icons.Default.Close, contentDescription = "Убрать вложение")
            }
        }
    }
}

@Composable
private fun SupportNotice(text: String, error: Boolean) {
    Text(
        text,
        color = if (error) MaterialTheme.colorScheme.error else BrandColors.Success,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

private fun ticketStatus(status: String): String = when (status.lowercase()) {
    "open", "new" -> "Открыт"
    "pending", "waiting", "awaiting_user" -> "Ожидает ответа"
    "closed", "resolved" -> "Закрыт"
    else -> status
}

private fun formatSupportTime(raw: String): String {
    val normalized = raw.replace('T', ' ').removeSuffix("Z")
    return normalized.take(16).ifBlank { raw }
}
