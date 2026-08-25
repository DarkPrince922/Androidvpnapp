package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import java.util.Locale
import com.darkprince.vpn.data.api.dto.AdminTicketDto
import com.darkprince.vpn.data.api.dto.AdminUserDto
import com.darkprince.vpn.data.api.dto.SupportMessageDto
import com.darkprince.vpn.ui.vm.AdminSection
import com.darkprince.vpn.ui.vm.AdminUiState
import com.darkprince.vpn.ui.vm.AdminViewModel
import com.darkprince.vpn.ui.vm.PeopleFilter
import com.darkprince.vpn.ui.vm.PeopleSort
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
                // Заголовок называет открытый раздел, а не всегда обращения:
                // подпись, которая спорит с выбранной кнопкой, читается как
                // ошибка приложения.
                Text(state.section.title, style = MaterialTheme.typography.headlineSmall)
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

        if (state.sections.isEmpty()) {
            // Права спрашиваются при входе во вкладку, и до ответа разделов
            // тоже нет. Показывать в эту секунду «роль не даёт доступа»
            // значит обвинить человека в том, чего ещё не знаем.
            if (state.loading) Loader() else AdminNotice(
                "Ваша роль не даёт доступа ни к одному разделу панели. Права " +
                    "выдаются в панели бота — попросите владельца сервиса."
            )
            return@Column
        }

        // Разделы, которых роль не даёт, не показываем вовсе: переключатель
        // с недоступными кнопками обещал бы то, чего нет.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.sections.forEach { section ->
                FilterChip(
                    selected = state.section == section,
                    onClick = { viewModel.setSection(section) },
                    label = { Text(section.title, fontSize = 12.sp) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        state.error?.let {
            AdminNotice(it)
            Spacer(Modifier.height(8.dp))
        }
        state.info?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }

        when (state.section) {
            AdminSection.SUMMARY -> SummarySection(state)
            AdminSection.TICKETS -> TicketsSection(viewModel, state, onOpenTicket)
            AdminSection.PEOPLE -> PeopleSection(viewModel, state)
        }
    }
}

@Composable
private fun TicketsSection(
    viewModel: AdminViewModel,
    state: AdminUiState,
    onOpenTicket: (Long) -> Unit,
) {
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

    when {
        state.loading && state.tickets.isEmpty() -> Loader()

        state.tickets.isEmpty() && state.error == null ->
            AdminNotice("Обращений в этой выборке нет.")

        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.tickets, key = { it.id }) { ticket ->
                AdminTicketRow(ticket) { onOpenTicket(ticket.id) }
            }
        }
    }
}

@Composable
private fun Loader() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Деньги в панели считаются в копейках — переводим у самого края.
 *
 * Формат собираем на английской локали и разделители расставляем сами.
 * На русской «%,.2f» ставит запятую дробным разделителем, и попытка
 * заменить разделитель разрядов пробелом испортила бы копейки.
 */
private fun rubles(kopeks: Long): String =
    String.format(Locale.US, "%,.2f", kopeks / 100.0)
        .replace(',', ' ')
        .replace('.', ',') + " ₽"

@Composable
private fun SummarySection(state: AdminUiState) {
    val dashboard = state.dashboard
    when {
        state.loading && dashboard == null -> Loader()
        dashboard == null -> AdminNotice("Сводка не загрузилась.")
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SummaryCard("Деньги") {
                    SummaryLine("Сегодня", rubles(dashboard.financial.incomeTodayKopeks))
                    SummaryLine("За месяц", rubles(dashboard.financial.incomeMonthKopeks))
                    SummaryLine("Всего", rubles(dashboard.financial.incomeTotalKopeks))
                }
            }
            item {
                SummaryCard("Подписки") {
                    SummaryLine("Активных", dashboard.subscriptions.active.toString())
                    SummaryLine("Пробных", dashboard.subscriptions.trial.toString())
                    SummaryLine("Истекших", dashboard.subscriptions.expired.toString())
                    SummaryLine("Куплено сегодня", dashboard.subscriptions.purchasedToday.toString())
                    SummaryLine("Куплено за месяц", dashboard.subscriptions.purchasedMonth.toString())
                }
            }
            item {
                val nodes = dashboard.nodes
                SummaryCard("Узлы") {
                    SummaryLine("Онлайн", "${nodes.online} из ${nodes.total}")
                    SummaryLine("Пользователей сейчас", nodes.totalUsersOnline.toString())
                    // Упавшие называем поимённо: число «офлайн: 2» ничего не
                    // говорит, а имя узла говорит, куда идти.
                    nodes.nodes.filter { it.isDown }.forEach { node ->
                        SummaryLine(
                            node.name.ifBlank { node.uuid },
                            "не отвечает",
                            alarm = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, alarm: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (alarm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PeopleSection(viewModel: AdminViewModel, state: AdminUiState) {
    var target by remember { mutableStateOf<AdminUserDto?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.search,
            onValueChange = viewModel::setSearch,
            placeholder = { Text("Имя, @ник или Telegram ID") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = viewModel::searchPeople, enabled = !state.loading) {
            Icon(Icons.Default.Search, contentDescription = "Искать")
        }
    }

    Spacer(Modifier.height(8.dp))

    // Два ряда: сперва кого показывать, потом в каком порядке. Оба
    // прокручиваются вбок — на узком экране они не помещаются, а прятать
    // их в меню значит спрятать сортировку, ради которой сюда и заходят.
    ChipRow {
        PeopleFilter.entries.forEach { filter ->
            FilterChip(
                selected = state.peopleFilter == filter,
                onClick = { viewModel.setPeopleFilter(filter) },
                label = { Text(filter.title, fontSize = 12.sp) },
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    ChipRow {
        PeopleSort.entries.forEach { sort ->
            FilterChip(
                selected = state.peopleSort == sort,
                onClick = { viewModel.setPeopleSort(sort) },
                label = { Text(sort.title, fontSize = 12.sp) },
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    if (state.peopleTotal > 0) {
        Text(
            if (state.sortedLocally) {
                "Показано ${state.people.size} из ${state.peopleTotal} — " +
                    "панель не умеет так сортировать, порядок задан на телефоне"
            } else {
                "Показано ${state.people.size} из ${state.peopleTotal}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
    }

    when {
        state.loading && state.people.isEmpty() -> Loader()
        state.people.isEmpty() -> AdminNotice("Никого не нашлось. Попробуйте другой запрос.")
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.people, key = { it.id }) { person ->
                PersonRow(person) { target = person }
            }
            if (state.hasMorePeople) {
                item {
                    // Кнопкой, а не по достижению конца списка: подгрузка
                    // «сама собой» на длинном списке легко уносит человека
                    // дальше, чем он собирался, и обратно он уже не найдёт.
                    OutlinedButton(
                        onClick = viewModel::loadMorePeople,
                        enabled = !state.loadingMore,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.loadingMore) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Показать ещё")
                        }
                    }
                }
            }
        }
    }

    target?.let { person ->
        PersonActionsDialog(
            person = person,
            state = state,
            onDismiss = { target = null },
            onAddBalance = { kopeks ->
                viewModel.addBalance(person.id, kopeks)
                target = null
            },
            onExtend = { days ->
                viewModel.extendSubscription(person.id, days)
                target = null
            },
        )
    }
}

/** Ряд чипов, который прокручивается вбок, если не помещается. */
@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun PersonRow(person: AdminUserDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    person.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    rubles(person.balanceKopeks),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                subscriptionSummary(person),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Одна строка про подписку: то, что чаще всего и спрашивают в поддержке. */
private fun subscriptionSummary(person: AdminUserDto): String {
    if (!person.hasSubscription) return "Подписки нет"
    val kind = when {
        person.subscriptionIsTrial -> "пробная"
        person.tariffName != null -> person.tariffName
        else -> "платная"
    }
    val left = when {
        person.daysRemaining > 0 -> "${person.daysRemaining} дн."
        else -> "истекла"
    }
    val traffic = if (person.trafficLimitGb > 0) {
        " · %.1f из %d ГБ".format(person.trafficUsedGb, person.trafficLimitGb)
    } else {
        ""
    }
    return "$kind · $left$traffic"
}

/**
 * Действия над человеком.
 *
 * Начисление и продление спрашивают подтверждение суммой и сроком: промах
 * пальцем здесь стоит чужих денег, а откатывать это придётся руками через
 * веб-панель.
 */
@Composable
private fun PersonActionsDialog(
    person: AdminUserDto,
    state: AdminUiState,
    onDismiss: () -> Unit,
    onAddBalance: (Long) -> Unit,
    onExtend: (Int) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(person.displayName) },
        text = {
            Column {
                Text(
                    "Баланс: ${rubles(person.balanceKopeks)} · ${subscriptionSummary(person)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.canAddBalance) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { text ->
                            // минус разрешаем: списание — то же действие
                            if (text.all { it.isDigit() || it == '-' }) amount = text
                        },
                        label = { Text("Начислить, ₽") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val rub = amount.toLongOrNull()
                    if (rub != null && rub != 0L) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Станет: ${rubles(person.balanceKopeks + rub * 100)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { onAddBalance(rub * 100) },
                            enabled = !state.sending,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (rub > 0) "Начислить $rub ₽" else "Списать ${-rub} ₽") }
                    }
                }

                if (state.canExtend) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = days,
                        onValueChange = { text ->
                            if (text.all { it.isDigit() } && text.length <= 4) days = text
                        },
                        label = { Text("Продлить, дней") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val count = days.toIntOrNull()
                    if (count != null && count > 0) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { onExtend(count) },
                            enabled = !state.sending,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Продлить на $count дн.") }
                    }
                }

                if (!state.canAddBalance && !state.canExtend) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Ваша роль позволяет смотреть, но не менять.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
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
