package com.darkprince.vpn.ui.screens

import com.darkprince.vpn.ui.theme.BrandColors
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.data.api.dto.AdminUserDetailDto
import com.darkprince.vpn.data.api.dto.AdminTicketUserDto
import com.darkprince.vpn.data.api.dto.AdminTariffOptionDto
import com.darkprince.vpn.data.api.dto.AdminSubscriptionInfoDto
import com.darkprince.vpn.data.api.dto.AdminSubscriptionAction
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
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
fun AdminScreen(
    viewModel: AdminViewModel,
    tools: com.darkprince.vpn.ui.vm.AdminToolsViewModel,
    onOpenTicket: (Long) -> Unit,
    onOpenPerson: (AdminUserDto) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // выводы, платежи и рассылки грузятся своей моделью — при входе в
    // раздел и по кнопке «Обновить»
    androidx.compose.runtime.LaunchedEffect(state.section, state.refreshTick) {
        tools.load(state.section)
    }

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
        // разделов бывает до шести — ряд прокручивается, а не сжимается
        Row(
            modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
            AdminSection.SUMMARY -> SummarySection(viewModel, state)
            AdminSection.TICKETS -> TicketsSection(viewModel, state, onOpenTicket)
            AdminSection.PEOPLE -> PeopleSection(viewModel, state, onOpenPerson)
            AdminSection.WITHDRAWALS -> WithdrawalsSection(tools, state, onOpenPerson)
            AdminSection.PAYMENTS -> PaymentsSection(tools, state)
            AdminSection.BROADCASTS -> BroadcastsSection(tools, state)
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
private fun SummarySection(viewModel: AdminViewModel, state: AdminUiState) {
    val dashboard = state.dashboard
    var promoOpen by remember { mutableStateOf(false) }
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                node.name.ifBlank { node.uuid },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            // Кнопка стоит там же, где имя упавшего узла:
                            // узнать и поднять — одно действие, а не два.
                            if (state.canRestartNode) {
                                TextButton(
                                    onClick = {
                                        viewModel.restartNode(node.uuid, node.name)
                                    },
                                    enabled = !state.sending,
                                ) { Text("Перезапустить", fontSize = 12.sp) }
                            } else {
                                Text(
                                    "не отвечает",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
            if (state.canCreatePromo) {
                item {
                    OutlinedButton(
                        onClick = { promoOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Создать промокод") }
                }
            }
        }
    }

    if (promoOpen) {
        PromoDialog(
            sending = state.sending,
            onDismiss = { promoOpen = false },
            onCreate = { code, days, rub, uses ->
                viewModel.createPromo(code, days, rub, uses)
                promoOpen = false
            },
        )
    }
}

/**
 * Промокод на дни или на баланс.
 *
 * Из шести типов, которые знает бот, здесь два. Остальные — скидки,
 * промогруппы, привязка к тарифу — требуют выбора из справочников, которых
 * на телефоне под рукой нет, и собирать их вслепую значит наплодить кодов,
 * которые не сработают.
 *
 * Код предлагаем готовый, но даём переписать: чаще всего он идёт в ответ на
 * обращение, и человеку приятнее получить «SORRY30», чем случайные буквы.
 */
@Composable
private fun PromoDialog(
    sending: Boolean,
    onDismiss: () -> Unit,
    onCreate: (code: String, days: Int?, rubles: Int?, uses: Int) -> Unit,
) {
    var code by remember { mutableStateOf(suggestPromoCode()) }
    var byDays by remember { mutableStateOf(true) }
    var value by remember { mutableStateOf("30") }
    var uses by remember { mutableStateOf("1") }

    val amount = value.toIntOrNull()
    val usesCount = uses.toIntOrNull()
    val valid = code.isNotBlank() && amount != null && amount > 0 &&
        usesCount != null && usesCount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый промокод") },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { text ->
                        if (text.length <= 50) code = text.uppercase()
                    },
                    label = { Text("Код") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                ChipRow {
                    FilterChip(
                        selected = byDays,
                        onClick = { byDays = true },
                        label = { Text("Дни подписки", fontSize = 12.sp) },
                    )
                    FilterChip(
                        selected = !byDays,
                        onClick = { byDays = false },
                        label = { Text("Бонус на баланс", fontSize = 12.sp) },
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { text ->
                        if (text.all { it.isDigit() } && text.length <= 6) value = text
                    },
                    label = { Text(if (byDays) "Дней" else "Рублей") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = uses,
                    onValueChange = { text ->
                        if (text.all { it.isDigit() } && text.length <= 5) uses = text
                    },
                    label = { Text("Сколько раз можно применить") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        code,
                        if (byDays) amount else null,
                        if (byDays) null else amount,
                        usesCount ?: 1,
                    )
                },
                enabled = valid && !sending,
            ) { Text("Создать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/** Готовый код из букв и цифр — набирать вручную на телефоне мучительно. */
private fun suggestPromoCode(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..8).map { alphabet.random() }.joinToString("")
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
private fun PeopleSection(
    viewModel: AdminViewModel,
    state: AdminUiState,
    onOpenPerson: (AdminUserDto) -> Unit,
) {
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
                PersonRow(person) { onOpenPerson(person) }
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
fun AdminTicketScreen(
    viewModel: AdminViewModel,
    ticketId: Long,
    onBack: () -> Unit,
    onOpenPerson: (AdminTicketUserDto) -> Unit,
    onOpenAttachment: (String) -> Unit,
) {
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
                // Имя — ссылка на карточку: прочитав жалобу, сразу видно
                // подписку человека, и править её можно не уходя искать его.
                ticket?.user?.let { user ->
                    Text(
                        "${user.displayName} · карточка ›",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenPerson(user) }
                            .padding(vertical = 2.dp),
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
                        AdminMessageBubble(message, onOpenAttachment)
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
private fun AdminMessageBubble(message: SupportMessageDto, onOpenAttachment: (String) -> Unit) {
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
                val media = remember(message) { ServiceLocator.supportRepository.mediaLinks(message) }
                if (message.messageText.isNotBlank() || media.isEmpty()) {
                    Text(
                        message.messageText.ifBlank { "(вложение)" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                media.forEach { item ->
                    Spacer(Modifier.height(6.dp))
                    if (item.type == "photo") {
                        AttachmentImage(item.fileId, item.url, onOpenAttachment)
                    } else {
                        OutlinedButton(onClick = { onOpenAttachment(item.url) }) {
                            Text(if (item.type == "video") "Открыть видео" else "Открыть вложение")
                        }
                    }
                }
                if (message.hasMedia && media.isEmpty()) {
                    Text(
                        "Вложение не загрузилось — обновите обращение",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Карточка человека.
 *
 * Отдельный экран, а не окно поверх списка: здесь и подписки, и устройства,
 * и платежи, и переписка. Открывается и из списка людей, и прямо из
 * обращения — чтобы, прочитав «не работает», сразу посмотреть подписку и
 * поправить её, не отправляясь на поиски человека.
 *
 * Всё, что меняет подписку или доступ, спрашивает подтверждение: кнопки
 * мелкие, а промах по «−7 дней» или «Заблокировать» человек заметит раньше
 * админа.
 */
@Composable
fun AdminPersonScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit,
    /** Платежи клиента — поиск в разделе «Платежи» по его ID или нику. */
    onOpenPayments: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val person = state.person

    if (person == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AdminNotice("Карточка закрыта")
        }
        return
    }
    val detail = state.personDetail

    var amount by remember(person.id) { mutableStateOf("") }
    var message by remember(person.id) { mutableStateOf("") }
    // какое устройство и из какой подписки удаляем
    var dropping by remember(person.id) { mutableStateOf<Pair<String, Long?>?>(null) }
    var confirm by remember(person.id) { mutableStateOf<PendingAdminAction?>(null) }

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
                    person.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    contactLine(person, detail),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(6.dp))
        }
        state.info?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // --- Профиль: баланс, статус, сколько потратил ---
            item {
                SummaryCard("Профиль") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Баланс",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(rubles(person.balanceKopeks), style = MaterialTheme.typography.headlineSmall)
                        }
                        StatusPill(
                            if (person.isBlocked) "Заблокирован" else "Активен",
                            if (person.isBlocked) MaterialTheme.colorScheme.error else BrandColors.Success,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    SummaryLine("Потратил всего", rubles(person.totalSpentKopeks))
                    detail?.createdAt?.let { SummaryLine("С нами с", shortDate(it)) }
                    detail?.lastActivity?.let { SummaryLine("Был в сети", shortDate(it)) }
                    val paymentsQuery = person.telegramId?.toString() ?: person.username?.let { "@$it" }
                    if (state.canReadPayments && paymentsQuery != null) {
                        Spacer(Modifier.height(8.dp))
                        ActionChip("Платежи клиента") { onOpenPayments(paymentsQuery) }
                    }
                    if (state.canBlock) {
                        Spacer(Modifier.height(8.dp))
                        ActionChip(
                            if (person.isBlocked) "Разблокировать" else "Заблокировать",
                            enabled = !state.sending,
                            danger = !person.isBlocked,
                        ) {
                            confirm = PendingAdminAction(
                                title = if (person.isBlocked) "Снять блокировку?" else "Заблокировать?",
                                text = if (person.isBlocked) {
                                    "${person.displayName} снова сможет пользоваться VPN и ботом."
                                } else {
                                    "${person.displayName} потеряет доступ к VPN: пользователь в панели " +
                                        "будет отключён. Снять блокировку можно здесь же."
                                },
                                confirmLabel = if (person.isBlocked) "Разблокировать" else "Заблокировать",
                                run = { viewModel.setBlocked(!person.isBlocked) },
                            )
                        }
                    }
                }
            }

            // --- Баланс: быстрые суммы и своя ---
            if (state.canAddBalance) {
                item {
                    SummaryCard("Начислить или списать") {
                        ChipRow {
                            listOf(100, 300, 500, 1000).forEach { rub ->
                                ActionChip("+$rub ₽", enabled = !state.sending) {
                                    confirm = PendingAdminAction(
                                        title = "Начислить $rub ₽?",
                                        text = "Станет: ${rubles(person.balanceKopeks + rub * 100L)}",
                                        confirmLabel = "Начислить",
                                        run = { viewModel.addBalance(person.id, rub * 100L) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { text ->
                                // минус разрешаем: списание — то же действие
                                if (text.all { it.isDigit() || it == '-' } && text.length <= 7) amount = text
                            },
                            label = { Text("Своя сумма, ₽ (минус — списать)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val rub = amount.toLongOrNull()
                        if (rub != null && rub != 0L) {
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    confirm = PendingAdminAction(
                                        title = if (rub > 0) "Начислить $rub ₽?" else "Списать ${-rub} ₽?",
                                        text = "Станет: ${rubles(person.balanceKopeks + rub * 100)}",
                                        confirmLabel = if (rub > 0) "Начислить" else "Списать",
                                        run = {
                                            viewModel.addBalance(person.id, rub * 100)
                                            amount = ""
                                        },
                                    )
                                },
                                enabled = !state.sending,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (rub > 0) "Начислить $rub ₽" else "Списать ${-rub} ₽")
                            }
                        }
                    }
                }
            }

            // --- Подписки ---
            val subscriptions = detail?.allSubscriptions.orEmpty()
            when {
                state.personLoading && detail == null -> item {
                    SummaryCard("Подписка") { AdminNotice("Загружаю…") }
                }
                detail != null && subscriptions.isEmpty() -> item {
                    SummaryCard("Подписка") { AdminNotice("Подписки нет") }
                }
                else -> items(subscriptions, key = { "sub-${it.id}" }) { sub ->
                    AdminSubscriptionCard(
                        sub = sub,
                        canEdit = state.canExtend,
                        sending = state.sending,
                        tariffs = state.tariffs,
                        onAsk = { confirm = it },
                        onAction = viewModel::subscriptionAction,
                    )
                }
            }

            // устройства — отдельной карточкой на каждую подписку: у каждой свой лимит
            val manyGroups = state.deviceGroups.size > 1
            if (state.personLoading && state.deviceGroups.isEmpty()) {
                item { SummaryCard("Устройства") { AdminNotice("Загружаю…") } }
            }
            items(state.deviceGroups, key = { "dev-${it.subscriptionId}" }) { group ->
                val title = buildString {
                    append("Устройства")
                    if (manyGroups) append(" · ${group.title?.takeIf { it.isNotBlank() } ?: "подписка #${group.subscriptionId}"}")
                    if (group.limit > 0) append(" · ${group.devices.size} из ${group.limit}")
                }
                SummaryCard(title) {
                    when {
                        group.failed -> AdminNotice("Не удалось загрузить")
                        group.devices.isEmpty() -> AdminNotice("Ни одного подключения")
                        else -> group.devices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(device.displayName, style = MaterialTheme.typography.bodyMedium)
                                    device.platform.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (state.canDropDevice) {
                                    TextButton(
                                        onClick = { dropping = device.hwid to group.subscriptionId },
                                        enabled = !state.sending,
                                    ) { Text("Удалить", fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            }

            item {
                SummaryCard("Последние платежи") {
                    when {
                        state.personLoading -> AdminNotice("Загружаю…")
                        state.transactions.isEmpty() -> AdminNotice("Операций нет")
                        else -> state.transactions.take(10).forEach { transaction ->
                            SummaryLine(
                                transaction.description?.takeIf { it.isNotBlank() }
                                    ?: transaction.type,
                                rubles(transaction.amountKopeks),
                                // незавершённый платёж — то самое «я оплатил,
                                // а не зачлось», ради чего сюда и смотрят
                                alarm = !transaction.isCompleted,
                            )
                        }
                    }
                }
            }

            if (state.canWrite) {
                item {
                    SummaryCard("Написать в Telegram") {
                        OutlinedTextField(
                            value = message,
                            onValueChange = { if (it.length <= 4_000) message = it },
                            label = { Text("Сообщение") },
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.sendMessage(message)
                                message = ""
                            },
                            enabled = !state.sending && message.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Отправить") }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    confirm?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(pending.title) },
            text = { Text(pending.text) },
            confirmButton = {
                TextButton(onClick = {
                    pending.run()
                    confirm = null
                }) { Text(pending.confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Отмена") }
            },
        )
    }

    dropping?.let { (hwid, subscriptionId) ->
        val device = state.deviceGroups
            .firstOrNull { it.subscriptionId == subscriptionId }
            ?.devices?.firstOrNull { it.hwid == hwid }
        AlertDialog(
            onDismissRequest = { dropping = null },
            title = { Text("Удалить устройство") },
            text = {
                Text(
                    "«${device?.displayName ?: hwid}» уйдёт из списка, место в лимите " +
                        "освободится сразу. Это не блокировка: при следующем обновлении " +
                        "подписки устройство вернётся, если место свободно.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeDevice(hwid, subscriptionId)
                    dropping = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { dropping = null }) { Text("Отмена") }
            },
        )
    }
}

/** Действие, ждущее подтверждения. */
private class PendingAdminAction(
    val title: String,
    val text: String,
    val confirmLabel: String,
    val run: () -> Unit,
)

/**
 * Одна подписка в карточке: что в ней сейчас и быстрые правки — срок,
 * устройства, трафик, тариф.
 */
@Composable
private fun AdminSubscriptionCard(
    sub: AdminSubscriptionInfoDto,
    canEdit: Boolean,
    sending: Boolean,
    tariffs: List<AdminTariffOptionDto>,
    onAsk: (PendingAdminAction) -> Unit,
    onAction: (AdminSubscriptionAction, String) -> Unit,
) {
    val name = sub.tariffName ?: "Подписка #${sub.id}"
    fun ask(title: String, text: String, action: AdminSubscriptionAction, done: String) {
        onAsk(PendingAdminAction(title, text, "Да", run = { onAction(action, done) }))
    }

    SummaryCard(if (sub.isTrial) "$name · пробная" else name) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                sub.endDate?.let {
                    Text("до ${shortDate(it)}", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    buildString {
                        append(
                            if (sub.trafficLimitGb <= 0) "безлимит"
                            else String.format(Locale.US, "%.1f из %d ГБ", sub.trafficUsedGb, sub.trafficLimitGb)
                        )
                        append(" · устройств: ${sub.deviceLimit}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val (label, color) = when {
                !sub.isActive -> (sub.status.ifBlank { "не активна" }) to MaterialTheme.colorScheme.error
                sub.daysRemaining <= 3 -> "${sub.daysRemaining} дн." to BrandColors.Warning
                else -> "${sub.daysRemaining} дн." to BrandColors.Success
            }
            StatusPill(label, color)
        }

        if (!canEdit) return@SummaryCard

        val subId = sub.id
        Spacer(Modifier.height(12.dp))
        Text("Срок", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        ChipRow {
            listOf(7, 30, 90).forEach { days ->
                ActionChip("+$days дн.", enabled = !sending) {
                    ask(
                        "Продлить «$name» на $days дн.?",
                        "Бесплатно для человека, с баланса ничего не спишется.",
                        AdminSubscriptionAction("extend", subId, days = days),
                        "Продлено на $days дн.",
                    )
                }
            }
            ActionChip("−7 дн.", enabled = !sending, danger = true) {
                ask(
                    "Сократить «$name» на 7 дн.?",
                    "Срок подписки уменьшится на неделю.",
                    AdminSubscriptionAction("shorten", subId, days = 7),
                    "Срок сокращён на 7 дн.",
                )
            }
            if (!sub.isActive) {
                ActionChip("Активировать", enabled = !sending) {
                    ask(
                        "Активировать «$name»?",
                        "Подписка снова заработает. Если срок уже вышел, бот сам продлит её на 30 дней.",
                        AdminSubscriptionAction("activate", subId),
                        "Подписка активирована",
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Устройства", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        var limit by remember(sub.id, sub.deviceLimit) { mutableStateOf(sub.deviceLimit) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { if (limit > 1) limit-- },
                enabled = limit > 1,
            ) { Text("−") }
            Text(
                "$limit",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            OutlinedButton(onClick = { if (limit < 100) limit++ }) { Text("+") }
            Spacer(Modifier.weight(1f))
            if (limit != sub.deviceLimit) {
                TextButton(
                    onClick = {
                        ask(
                            "Лимит устройств: ${sub.deviceLimit} → $limit?",
                            "Изменится сразу и в панели.",
                            AdminSubscriptionAction("set_device_limit", subId, deviceLimit = limit),
                            "Лимит устройств: $limit",
                        )
                    },
                    enabled = !sending,
                ) { Text("Сохранить") }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Трафик", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        ChipRow {
            if (sub.trafficLimitGb > 0) {
                listOf(50, 100).forEach { gb ->
                    ActionChip("+$gb ГБ", enabled = !sending) {
                        ask(
                            "Добавить $gb ГБ?",
                            "Лимит станет ${sub.trafficLimitGb + gb} ГБ.",
                            AdminSubscriptionAction("add_traffic", subId, trafficGb = gb),
                            "Добавлено $gb ГБ",
                        )
                    }
                }
                ActionChip("Безлимит", enabled = !sending) {
                    ask(
                        "Сделать трафик безлимитным?",
                        "Ограничение по трафику для этой подписки снимется.",
                        AdminSubscriptionAction("set_traffic", subId, trafficLimitGb = 0),
                        "Трафик безлимитный",
                    )
                }
            }
            ActionChip("Сбросить счётчик", enabled = !sending) {
                ask(
                    "Обнулить использованный трафик?",
                    "Счётчик «использовано» начнётся с нуля, лимит не изменится.",
                    AdminSubscriptionAction("set_traffic", subId, trafficUsedGb = 0.0),
                    "Счётчик трафика сброшен",
                )
            }
        }

        val others = tariffs.filter { it.id != sub.tariffId }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            var menu by remember(sub.id) { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { menu = true },
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Сменить тариф") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    others.forEach { tariff ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(tariff.name)
                                    Text(
                                        (if (tariff.trafficLimitGb <= 0) "безлимит" else "${tariff.trafficLimitGb} ГБ") +
                                            " · устройств: ${tariff.deviceLimit}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                menu = false
                                ask(
                                    "Перевести на «${tariff.name}»?",
                                    "Срок сохранится, лимиты трафика и устройств станут как у нового тарифа.",
                                    AdminSubscriptionAction("change_tariff", subId, tariffId = tariff.id),
                                    "Тариф: ${tariff.name}",
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Как связаться: почта, ник или номер в Telegram — что есть. */
private fun contactLine(person: AdminUserDto, detail: AdminUserDetailDto?): String =
    listOfNotNull(
        detail?.email,
        person.username?.let { "@$it" },
        person.telegramId?.let { "TG $it" },
    ).joinToString(" · ").ifEmpty { "ID ${person.id}" }

/** «03.11.2026» из ISO-строки бота; не разобрали — показываем как есть. */
private fun shortDate(raw: String): String = runCatching {
    java.time.LocalDate.parse(raw.take(10))
        .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}.getOrDefault(raw)

@Composable
private fun StatusPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Мелкая кнопка-«таблетка» для быстрых правок. */
@Composable
private fun ActionChip(
    text: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) color else color.copy(alpha = 0.4f),
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}
