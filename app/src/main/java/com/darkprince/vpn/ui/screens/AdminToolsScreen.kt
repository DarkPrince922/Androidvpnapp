package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.data.api.dto.AdminPaymentDto
import com.darkprince.vpn.data.api.dto.AdminUserDto
import com.darkprince.vpn.data.api.dto.AdminWithdrawalDto
import com.darkprince.vpn.data.api.dto.BroadcastDto
import com.darkprince.vpn.ui.theme.BrandColors
import com.darkprince.vpn.ui.vm.AdminToolsViewModel
import com.darkprince.vpn.ui.vm.AdminUiState
import com.darkprince.vpn.ui.vm.WithdrawalCheck

// ---------------------------------------------------------------------------
// Выводы
// ---------------------------------------------------------------------------

/**
 * Заявки на вывод реферальных денег.
 *
 * Порядок как в боте: одобрить → перевести деньги самому → отметить
 * «Выплачено». Перед решением видно, что бот думает о риске заявки.
 */
@Composable
fun ColumnScope.WithdrawalsSection(
    tools: AdminToolsViewModel,
    admin: AdminUiState,
    onOpenPerson: (AdminUserDto) -> Unit,
) {
    val state by tools.state.collectAsStateWithLifecycle()
    var decision by remember { mutableStateOf<Pair<AdminWithdrawalDto, Boolean>?>(null) }

    decision?.let { (item, approve) ->
        CommentDialog(
            title = if (approve) "Одобрить вывод ${formatKopeks(item.amountKopeks)}?" else "Отклонить заявку?",
            hint = if (approve) "Комментарий для клиента (необязательно)" else "Причина — её увидит клиент",
            confirm = if (approve) "Одобрить" else "Отклонить",
            presets = if (approve) emptyList() else REJECT_REASONS,
            onConfirm = { comment ->
                decision = null
                if (approve) tools.approveWithdrawal(item.id, comment) else tools.rejectWithdrawal(item.id, comment)
            },
            onDismiss = { decision = null },
        )
    }

    ToolMessages(state.error, state.info)
    Chips(
        listOf("pending" to "Ждут", "approved" to "Одобрены", "completed" to "Выплачены", "rejected" to "Отклонены", null to "Все"),
        selected = state.withdrawalStatus,
        onSelect = tools::setWithdrawalStatus,
    )
    if (state.pendingCount > 0) {
        Text(
            "Ждут решения: ${state.pendingCount} на ${formatKopeks(state.pendingTotalKopeks)}",
            style = MaterialTheme.typography.labelLarge,
            color = BrandColors.Warning,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.withdrawals.isEmpty() && !state.loading) {
            item { Empty("Заявок нет") }
        }
        items(state.withdrawals, key = { it.id }) { item ->
            WithdrawalCard(
                item = item,
                detail = state.withdrawalDetail?.takeIf { it.id == item.id },
                check = state.withdrawalCheck?.takeIf { it.withdrawalId == item.id },
                onOpenPerson = onOpenPerson,
                busy = state.busy,
                canApprove = admin.canApproveWithdrawals,
                canReject = admin.canRejectWithdrawals,
                onToggle = { tools.openWithdrawal(item.id) },
                onApprove = { decision = item to true },
                onReject = { decision = item to false },
                onComplete = { tools.completeWithdrawal(item.id) },
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private fun riskColor(level: String): Color = when (level) {
    "critical", "high" -> BrandColors.Danger
    "medium" -> BrandColors.Warning
    else -> BrandColors.Success
}

private fun riskText(level: String): String = when (level) {
    "critical" -> "риск критический"
    "high" -> "риск высокий"
    "medium" -> "риск средний"
    else -> "риск низкий"
}

private fun withdrawalState(status: String): String = when (status) {
    "pending" -> "ждёт решения"
    "approved" -> "одобрена — переведите деньги"
    "completed" -> "выплачено"
    "rejected" -> "отклонена"
    "cancelled" -> "отменена клиентом"
    else -> status
}

@Composable
private fun WithdrawalCard(
    item: AdminWithdrawalDto,
    detail: com.darkprince.vpn.data.api.dto.AdminWithdrawalDetailDto?,
    check: WithdrawalCheck?,
    onOpenPerson: (AdminUserDto) -> Unit,
    busy: Boolean,
    canApprove: Boolean,
    canReject: Boolean,
    onToggle: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onComplete: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    ToolCard(onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatKopeks(item.amountKopeks), style = MaterialTheme.typography.titleLarge)
                Text(
                    listOfNotNull(item.who, item.createdAt?.let { cabinetDate(it) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Pill(riskText(item.riskLevel), riskColor(item.riskLevel))
        }
        Text(withdrawalState(item.status), style = MaterialTheme.typography.labelMedium)
        item.paymentDetails?.let { details ->
            Text(
                "Реквизиты: $details",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { clipboard.setText(AnnotatedString(details)) },
            )
        }
        item.adminComment?.let {
            Text("Комментарий: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        detail?.let { d ->
            Text(
                "Рефералов ${d.totalReferrals} · заработал ${formatKopeks(d.totalEarningsKopeks)} · баланс ${formatKopeks(d.balanceKopeks)}",
                style = MaterialTheme.typography.bodySmall,
            )
            d.riskAnalysis?.flags?.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            d.riskAnalysis?.recommendationText?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        }
        check?.let { WithdrawalCheckBlock(it, onOpenPerson) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (item.status) {
                "pending" -> {
                    if (canApprove) Action("Одобрить", enabled = !busy, onClick = onApprove)
                    if (canReject) Action("Отклонить", enabled = !busy, danger = true, onClick = onReject)
                }
                "approved" -> if (canApprove) Action("Выплачено", enabled = !busy, onClick = onComplete)
            }
            if (detail == null && item.status == "pending") Action("Проверить", onClick = onToggle)
        }
    }
}

/** Причины отказа по пунктам правил партнёрской программы. */
private val REJECT_REASONS = listOf(
    "Накрутка" to "Приглашённые аккаунты принадлежат вам или оплачены вами — это запрещено п. 3.1 правил партнёрской программы.",
    "Спам" to "Приглашения рассылались как спам — это запрещено п. 3.2 правил партнёрской программы.",
    "Возврат платежа" to "Платёж приглашённого был возвращён или оспорен — вознаграждение аннулировано по п. 4.3 правил.",
)

/**
 * Своя проверка заявки: кого человек пригласил и не похоже ли это на
 * накрутку. Красное — почти наверняка обман, оранжевое — стоит посмотреть.
 */
@Composable
private fun WithdrawalCheckBlock(check: WithdrawalCheck, onOpenPerson: (AdminUserDto) -> Unit) {
    var showAll by remember(check.withdrawalId) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Проверка", style = MaterialTheme.typography.titleSmall)
        when {
            check.loading -> Text(
                "Смотрю приглашённых и устройства…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            check.unavailable != null -> Text(
                check.unavailable,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                if (check.warnings.isEmpty()) {
                    Text("● Подозрительного не найдено", style = MaterialTheme.typography.bodySmall, color = BrandColors.Success)
                }
                check.warnings.forEach { warning ->
                    Text(
                        "● ${warning.text}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (warning.severe) BrandColors.Danger else BrandColors.Warning,
                    )
                }
                if (check.totalFriends > check.devicesChecked && check.devicesChecked > 0) {
                    Text(
                        "Устройства сверены у первых ${check.devicesChecked} из ${check.totalFriends}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (check.friends.isNotEmpty()) {
                    Text(
                        "Пригласил: ${check.totalFriends}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    val shown = if (showAll) check.friends else check.friends.take(FRIENDS_PREVIEW)
                    shown.forEach { friend -> FriendRow(friend) { onOpenPerson(friend) } }
                    if (!showAll && check.friends.size > FRIENDS_PREVIEW) {
                        Text(
                            "Показать всех (${check.friends.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { showAll = true }
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRow(friend: AdminUserDto, onClick: () -> Unit) {
    val bought = when {
        friend.purchaseCount > 0 -> "покупок ${friend.purchaseCount} · ${formatKopeks(friend.totalSpentKopeks)}"
        friend.subscriptionIsTrial -> "только пробная"
        else -> "не покупал"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Text(friend.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text(
            listOfNotNull(
                friend.createdAt?.let { "рег. ${cabinetDate(it)}" },
                bought,
                if (friend.lastActivity == null) "не заходил" else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = if (friend.purchaseCount > 0) MaterialTheme.colorScheme.onSurfaceVariant else BrandColors.Warning,
        )
    }
}

private const val FRIENDS_PREVIEW = 10

// ---------------------------------------------------------------------------
// Платежи
// ---------------------------------------------------------------------------

/**
 * Поиск платежа: по номеру счёта, @нику, Telegram ID или почте. Зависший
 * платёж можно перепроверить у кассы — как кнопка «Проверить» в боте.
 */
@Composable
fun ColumnScope.PaymentsSection(tools: AdminToolsViewModel, admin: AdminUiState) {
    val state by tools.state.collectAsStateWithLifecycle()

    ToolMessages(state.error, state.info)
    OutlinedTextField(
        value = state.paymentQuery,
        onValueChange = tools::setPaymentQuery,
        label = { Text("Счёт, @ник, Telegram ID или почта") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { tools.searchPayments() }),
        trailingIcon = {
            TextButton(onClick = { tools.searchPayments() }) { Text("Найти") }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Chips(
        listOf("all" to "Все", "pending" to "Ждут", "paid" to "Оплачены", "cancelled" to "Отменены"),
        selected = state.paymentStatus,
        onSelect = { tools.setPaymentStatus(it ?: "all") },
    )
    Chips(
        listOf("24h" to "Сутки", "7d" to "Неделя", "30d" to "Месяц", "all" to "Всё время"),
        selected = state.paymentPeriod,
        onSelect = { tools.setPaymentPeriod(it ?: "7d") },
    )
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.payments.isEmpty() && !state.loading) item { Empty("Платежей не найдено") }
        if (state.paymentsTotal > state.payments.size) {
            item {
                Text(
                    "Показаны ${state.payments.size} из ${state.paymentsTotal} — уточните поиск",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.payments, key = { "${it.method}-${it.id}" }) { payment ->
            PaymentCard(payment, busy = state.busy, canCheck = admin.canCheckPayments) { tools.checkPayment(payment) }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun PaymentCard(payment: AdminPaymentDto, busy: Boolean, canCheck: Boolean, onCheck: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    ToolCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatKopeks(payment.amountKopeks), style = MaterialTheme.typography.titleLarge)
                Text(
                    listOfNotNull(
                        payment.methodDisplay.ifBlank { payment.method },
                        payment.createdAt?.let { cabinetDate(it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Pill(
                "${payment.statusEmoji} ${payment.statusText}".trim(),
                if (payment.isPaid) BrandColors.Success else BrandColors.Warning,
            )
        }
        val who = listOfNotNull(payment.userUsername?.let { "@$it" }, payment.userTelegramId?.let { "ID $it" })
        if (who.isNotEmpty()) Text(who.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        if (payment.identifier.isNotBlank()) {
            Text(
                "Счёт: ${payment.identifier}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { clipboard.setText(AnnotatedString(payment.identifier)) },
            )
        }
        if (canCheck && payment.isCheckable && !payment.isPaid) {
            Action("Проверить у кассы", enabled = !busy, onClick = onCheck)
        }
    }
}

// ---------------------------------------------------------------------------
// Рассылки
// ---------------------------------------------------------------------------

/**
 * Рассылка в Telegram с телефона: кому, текст, кнопки под сообщением.
 * Получатели считаются ботом по фильтру — количество видно до отправки.
 */
@Composable
fun ColumnScope.BroadcastsSection(tools: AdminToolsViewModel, admin: AdminUiState) {
    val state by tools.state.collectAsStateWithLifecycle()
    var target by rememberSaveable { mutableStateOf("all") }
    var text by rememberSaveable { mutableStateOf("") }
    var buttons by remember(state.buttons) {
        mutableStateOf(state.buttons.filter { it.default }.map { it.key }.toSet())
    }
    var confirm by remember { mutableStateOf(false) }
    val filter = state.filters.firstOrNull { it.key == target }

    LaunchedEffect(state.broadcastSent) {
        if (state.broadcastSent) {
            text = ""
            tools.consumeBroadcastSent()
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Отправить рассылку?") },
            text = {
                Text(
                    "Получатели: ${filter?.label ?: target}" +
                        (filter?.count?.let { " — $it чел." } ?: "") +
                        ". Отменить отправку можно, но уже доставленные сообщения останутся."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    tools.sendBroadcast(target, text, buttons.toList())
                }) { Text("Отправить") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }

    ToolMessages(state.error, state.info)
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (admin.canSendBroadcasts) {
            item {
                ToolCard {
                    Text("Новая рассылка в Telegram", style = MaterialTheme.typography.titleMedium)
                    Text("Кому", style = MaterialTheme.typography.labelMedium)
                    Chips(
                        state.filters.filter { (it.count ?: 1) > 0 }.map { it.key to "${it.label} · ${it.count ?: "?"}" },
                        selected = target,
                        onSelect = { target = it ?: "all" },
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (it.length <= 4000) text = it },
                        label = { Text("Текст") },
                        supportingText = { Text("${text.length}/4000 · можно HTML: <b>, <i>, <a href>") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.buttons.isNotEmpty()) {
                        Text("Кнопки под сообщением", style = MaterialTheme.typography.labelMedium)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.buttons.forEach { button ->
                                FilterChip(
                                    selected = button.key in buttons,
                                    onClick = {
                                        buttons = if (button.key in buttons) buttons - button.key else buttons + button.key
                                    },
                                    label = { Text(button.label, fontSize = 12.sp) },
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { confirm = true },
                        enabled = !state.busy && text.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Отправить") }
                }
            }
        }
        if (state.broadcasts.isNotEmpty()) {
            item { Text("История", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp)) }
        }
        items(state.broadcasts, key = { it.id }) { broadcast ->
            BroadcastCard(broadcast, state.filters.firstOrNull { it.key == broadcast.targetType }?.label, state.busy) {
                tools.stopBroadcast(broadcast.id)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private fun broadcastStatus(status: String): String = when (status) {
    "queued" -> "в очереди"
    "in_progress" -> "идёт"
    "completed" -> "завершена"
    "partial" -> "частично"
    "failed" -> "ошибка"
    "cancelled" -> "остановлена"
    "cancelling" -> "останавливается"
    else -> status
}

@Composable
private fun BroadcastCard(broadcast: BroadcastDto, targetLabel: String?, busy: Boolean, onStop: () -> Unit) {
    ToolCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(targetLabel ?: broadcast.targetType, broadcast.createdAt?.let { cabinetDate(it) }).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Pill(
                broadcastStatus(broadcast.status),
                if (broadcast.running) BrandColors.Warning else if (broadcast.status == "completed") BrandColors.Success else BrandColors.Danger,
            )
        }
        broadcast.messageText?.let {
            Text(it.take(200), style = MaterialTheme.typography.bodySmall, maxLines = 3)
        }
        if (broadcast.running && broadcast.totalCount > 0) {
            LinearProgressIndicator(
                progress = { (broadcast.progressPercent / 100).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            "Доставлено ${broadcast.sentCount} из ${broadcast.totalCount}" +
                (if (broadcast.blockedCount > 0) " · заблокировали бота ${broadcast.blockedCount}" else "") +
                (if (broadcast.failedCount > 0) " · ошибок ${broadcast.failedCount}" else ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (broadcast.running && broadcast.status != "cancelling") {
            Action("Остановить", enabled = !busy, danger = true, onClick = onStop)
        }
    }
}

// ---------------------------------------------------------------------------
// Общее
// ---------------------------------------------------------------------------

@Composable
private fun ToolMessages(error: String?, info: String?) {
    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
    }
    info?.let {
        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun <T> Chips(items: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { (key, label) ->
            FilterChip(
                selected = key == selected,
                onClick = { onSelect(key) },
                label = { Text(label, fontSize = 12.sp) },
            )
        }
    }
}

@Composable
private fun ToolCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun Pill(text: String, color: Color) {
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

@Composable
private fun Action(text: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
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

@Composable
private fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}

@Composable
private fun CommentDialog(
    title: String,
    hint: String,
    confirm: String,
    presets: List<Pair<String, String>> = emptyList(),
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // готовые причины — по пунктам правил, чтобы не набирать руками
                if (presets.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        presets.forEach { (label, text) ->
                            FilterChip(
                                selected = comment == text,
                                onClick = { comment = text },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { if (it.length <= 2000) comment = it },
                    label = { Text(hint) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(comment) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
