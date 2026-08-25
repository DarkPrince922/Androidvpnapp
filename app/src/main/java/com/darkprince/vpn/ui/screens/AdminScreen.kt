package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.data.api.dto.AdminTicketDto
import com.darkprince.vpn.data.api.dto.SupportMessageDto
import com.darkprince.vpn.ui.vm.AdminViewModel
import com.darkprince.vpn.ui.vm.TicketFilter

/**
 * Обращения глазами админа.
 *
 * Здесь показывается только то, что позволяет роль: список требует
 * `tickets:read`, поле ответа — `tickets:reply`, закрытие — `tickets:close`.
 * Это удобство, а не защита: решает всё равно сервер, и кнопка без права
 * просто получила бы 403. Но показывать человеку то, чего он не может, —
 * значит обещать впустую.
 */
@Composable
fun AdminScreen(viewModel: AdminViewModel, onOpenTicket: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Обращения", style = MaterialTheme.typography.headlineSmall)
                val roles = state.permissions.roles.joinToString(", ").takeIf { it.isNotBlank() }
                Text(
                    roles?.let { "Роль: $it" } ?: "Панель управления",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (!state.canReadTickets) {
            AdminNotice(
                "Ваша роль не даёт доступа к обращениям. Права выдаются в панели " +
                    "бота — попросите владельца сервиса."
            )
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TicketFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(filter.title, fontSize = 12.sp) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        state.error?.let {
            AdminNotice(it)
            Spacer(Modifier.height(8.dp))
        }

        when {
            state.loading && state.tickets.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.tickets.isEmpty() && state.error == null ->
                AdminNotice("Обращений в этой выборке нет.")

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.tickets, key = { it.id }) { ticket ->
                    AdminTicketRow(ticket) { onOpenTicket(ticket.id) }
                }
            }
        }
    }
}

@Composable
private fun AdminNotice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AdminTicketRow(ticket: AdminTicketDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            // Ждущее ответа обращение обводим акцентом: в списке из тридцати
            // строк важно не «сколько всего», а «где меня ждут».
            if (ticket.needsReply) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            },
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ticket.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(ticket.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                ticket.user?.displayName ?: "Пользователь удалён",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ticket.lastMessage?.messageText?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Статус обращения цветом: открытое зовёт, закрытое молчит. */
@Composable
private fun StatusChip(status: String) {
    val (label, tint) = when (status.lowercase()) {
        "open" -> "Открыто" to MaterialTheme.colorScheme.primary
        "answered" -> "Отвечено" to MaterialTheme.colorScheme.secondary
        "pending" -> "Ожидает" to MaterialTheme.colorScheme.tertiary
        "closed" -> "Закрыто" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> status to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
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

/** Переписка по одному обращению и ответ от лица поддержки. */
@Composable
fun AdminTicketScreen(viewModel: AdminViewModel, ticketId: Long, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ticket = state.ticket
    var reply by remember(ticketId) { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(ticketId) { viewModel.openTicket(ticketId) }
    LaunchedEffect(ticket?.messages?.size) {
        val last = ticket?.messages?.lastIndex ?: -1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    ticket?.title ?: "Обращение #$ticketId",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ticket?.user?.let {
                    Text(
                        it.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (ticket != null && !ticket.isClosed && state.canClose) {
                OutlinedButton(
                    onClick = { viewModel.setStatus(ticketId, "closed") },
                    enabled = !state.sending,
                ) { Text("Закрыть", fontSize = 12.sp) }
            }
        }

        state.ticketError?.let {
            AdminNotice(it)
            Spacer(Modifier.height(8.dp))
        }

        when {
            state.ticketLoading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            ticket == null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { AdminNotice("Не удалось открыть обращение") }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ticket.messages.sortedBy { it.id }, key = { it.id }) { message ->
                        AdminMessageBubble(message)
                    }
                }

                when {
                    ticket.isClosed -> Text(
                        "Обращение закрыто",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(12.dp),
                    )

                    !state.canReply -> Text(
                        "Ваша роль позволяет читать обращения, но не отвечать",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(12.dp),
                    )

                    else -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        OutlinedTextField(
                            value = reply,
                            onValueChange = { if (it.length <= 4_000) reply = it },
                            placeholder = { Text("Ответ") },
                            minLines = 1,
                            maxLines = 4,
                            enabled = !state.sending,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                viewModel.reply(ticketId, reply)
                                reply = ""
                            },
                            enabled = !state.sending && reply.isNotBlank(),
                        ) {
                            if (state.sending) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Отправить",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Сообщение в переписке.
 *
 * Стороны здесь зеркальны пользовательскому экрану: то, что человеку видно
 * слева как «Поддержка», для админа своё и стоит справа. Иначе, отвечая, он
 * читал бы собственные слова как чужие.
 */
@Composable
private fun AdminMessageBubble(message: SupportMessageDto) {
    val mine = message.isFromAdmin
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (mine) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ),
            border = if (mine) {
                null
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            },
        ) {
            Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                if (!mine) {
                    Text(
                        "Пользователь",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    message.messageText.ifBlank { "(вложение)" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (message.hasMedia) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Есть вложение — откройте в веб-кабинете",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
