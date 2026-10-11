package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.data.api.dto.ReferralEarningDto
import com.darkprince.vpn.data.api.dto.ReferralTermsDto
import com.darkprince.vpn.data.api.dto.WithdrawalBalanceDto
import com.darkprince.vpn.data.api.dto.WithdrawalDto
import com.darkprince.vpn.ui.theme.BrandColors
import com.darkprince.vpn.ui.theme.GroupCard
import com.darkprince.vpn.ui.theme.SectionHeader
import com.darkprince.vpn.ui.vm.ReferralViewModel

/**
 * Реферальная программа: ссылка, условия, кого пригласили, сколько это
 * принесло — и вывод заработанного, если он включён в боте.
 */
@Composable
fun ReferralScreen(viewModel: ReferralViewModel, onShare: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var showInvited by rememberSaveable { mutableStateOf(false) }
    var showEarnings by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Text("Пригласить друга", style = MaterialTheme.typography.headlineSmall)
        }

        val info = state.info
        if (info == null) {
            item {
                if (state.loading) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(state.error ?: "Реферальная программа недоступна", color = MaterialTheme.colorScheme.error)
                }
            }
            return@LazyColumn
        }

        item {
            GroupCard {
                Row(Modifier.fillMaxWidth().padding(18.dp)) {
                    Stat("Приглашено", "${info.totalReferrals ?: 0}", Modifier.weight(1f))
                    Stat("Платят", "${info.activeReferrals ?: 0}", Modifier.weight(1f))
                    Stat("Заработано", formatKopeks(info.totalEarningsKopeks ?: 0), Modifier.weight(1f))
                }
            }
        }

        item { TermsCard(state.terms, info.commissionPercent) }

        info.shareLink?.let { link ->
            item {
                GroupCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Ваша ссылка", style = MaterialTheme.typography.labelMedium)
                        Text(
                            link,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { clipboard.setText(AnnotatedString(link)) },
                        )
                        Button(
                            onClick = { onShare("Попробуй наш VPN — быстрый и надёжный. Подключайся: $link") },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("Поделиться ссылкой") }
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(link)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Скопировать") }
                        info.referralCode?.let {
                            Text(
                                "Код для регистрации по почте: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }

        state.withdrawal?.takeIf { it.isEnabled }?.let { balance ->
            item { SectionHeader("Вывод денег") }
            item {
                WithdrawalCard(
                    balance = balance,
                    busy = state.busy,
                    sent = state.requestSent,
                    onSentShown = viewModel::consumeRequestSent,
                    onRequest = viewModel::requestWithdrawal,
                )
            }
            items(state.withdrawals, key = { "wd-${it.id}" }) { item ->
                WithdrawalRow(item, busy = state.busy, onCancel = { viewModel.cancelWithdrawal(item.id) })
            }
        }

        val invited = state.invited?.items.orEmpty()
        if (invited.isNotEmpty()) {
            item {
                ToggleHeader("Приглашённые", invited.size, showInvited) { showInvited = !showInvited }
            }
            if (showInvited) {
                items(invited, key = { "inv-${it.id}" }) { person ->
                    ListRow(
                        title = person.title,
                        subtitle = listOfNotNull(
                            person.createdAt?.let { "с ${cabinetDate(it)}" },
                            when {
                                person.hasPaid -> "платит"
                                person.hasSubscription -> "с подпиской"
                                else -> "ещё не платил"
                            },
                        ).joinToString(" · "),
                        value = null,
                        positive = person.hasPaid,
                    )
                }
            }
        }

        val earnings = state.earnings?.items.orEmpty()
        if (earnings.isNotEmpty()) {
            item {
                ToggleHeader("Начисления", earnings.size, showEarnings) { showEarnings = !showEarnings }
            }
            if (showEarnings) {
                items(earnings, key = { "earn-${it.id}" }) { earning -> EarningRow(earning) }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun TermsCard(terms: ReferralTermsDto?, fallbackPercent: Double?) {
    val lines = mutableListOf<String>()
    val percent = terms?.commissionPercent ?: fallbackPercent?.toInt()
    val first = terms?.firstPaymentPercent
    if (first != null && first > 0 && first != percent) {
        lines += "$first% с первого пополнения друга"
        if (percent != null && percent > 0) lines += "$percent% со всех следующих"
    } else if (percent != null && percent > 0) {
        lines += "$percent% с каждого пополнения друга — на ваш баланс"
    }
    terms?.inviterBonusKopeks?.takeIf { it > 0 }?.let { lines += "${formatKopeks(it)} вам, когда друг пополнит впервые" }
    terms?.firstTopupBonusKopeks?.takeIf { it > 0 }?.let { lines += "${formatKopeks(it)} другу за первое пополнение" }
    terms?.minimumTopupKopeks?.takeIf { it > 0 }?.let { lines += "Считаются пополнения от ${formatKopeks(it)}" }
    if (lines.isEmpty()) return
    GroupCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Как это работает", style = MaterialTheme.typography.titleMedium)
            lines.forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun WithdrawalCard(
    balance: WithdrawalBalanceDto,
    busy: Boolean,
    sent: Boolean,
    onSentShown: () -> Unit,
    onRequest: (Long, String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(sent) {
        if (sent) {
            open = false
            amount = ""
            onSentShown()
        }
    }
    GroupCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Доступно: ${formatKopeks(balance.availableTotal)}", style = MaterialTheme.typography.titleLarge)
            Text(
                listOfNotNull(
                    balance.minAmountKopeks.takeIf { it > 0 }?.let { "минимум ${formatKopeks(it)}" },
                    balance.pending.takeIf { it > 0 }?.let { "в обработке ${formatKopeks(it)}" },
                    balance.withdrawn.takeIf { it > 0 }?.let { "выведено ${formatKopeks(it)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!balance.canRequest) {
                Text(
                    balance.cannotRequestReason ?: "Сейчас вывести нельзя",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.Warning,
                )
            } else if (!open) {
                Button(
                    onClick = {
                        open = true
                        if (amount.isBlank()) amount = (balance.availableTotal / 100).toString()
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Вывести") }
            } else {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { v -> amount = v.filter(Char::isDigit).take(7) },
                    label = { Text("Сумма, ₽") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 500) details = it },
                    label = { Text("Реквизиты") },
                    placeholder = { Text(balance.requisitesText.ifBlank { "Номер карты или телефон для СБП и банк" }) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { open = false }, modifier = Modifier.weight(1f)) { Text("Отмена") }
                    Button(
                        onClick = { onRequest(amount.toLongOrNull() ?: 0, details) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Отправить") }
                }
            }
        }
    }
}

private fun withdrawalStatus(status: String): Pair<String, Boolean?> = when (status) {
    "pending" -> "на проверке" to null
    "approved" -> "одобрена" to true
    "completed" -> "выплачено" to true
    "rejected" -> "отклонена" to false
    "cancelled" -> "отменена" to false
    else -> status to null
}

@Composable
private fun WithdrawalRow(item: WithdrawalDto, busy: Boolean, onCancel: () -> Unit) {
    val (status, good) = withdrawalStatus(item.status)
    GroupCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(formatKopeks(item.amountKopeks), style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(item.createdAt?.let { cabinetDate(it) }, status, item.adminComment).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (good) {
                        true -> BrandColors.Success
                        false -> BrandColors.Danger
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (item.status == "pending") {
                val accent = MaterialTheme.colorScheme.primary
                Text(
                    "Отменить",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .clickable(enabled = !busy, onClick = onCancel)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ToggleHeader(title: String, count: Int, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader("$title · $count", Modifier.weight(1f))
        Text(
            if (open) "Скрыть" else "Показать",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ListRow(title: String, subtitle: String, value: String?, positive: Boolean) {
    GroupCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (positive) BrandColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            value?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = BrandColors.Success) }
        }
    }
}

@Composable
private fun EarningRow(earning: ReferralEarningDto) {
    val reason = when (earning.reason) {
        "referral_first_topup" -> "Бонус за первое пополнение"
        "referral_commission_topup", "referral_commission" -> "Процент с пополнения"
        "referral_registration_pending" -> "Бонус за регистрацию"
        else -> "Начисление"
    }
    val who = earning.referralFirstName?.takeIf { it.isNotBlank() } ?: earning.referralUsername?.let { "@$it" }
    ListRow(
        title = reason,
        subtitle = listOfNotNull(who, earning.createdAt?.let { cabinetDate(it) }).joinToString(" · "),
        value = "+" + formatKopeks(earning.amountKopeks),
        positive = false,
    )
}
