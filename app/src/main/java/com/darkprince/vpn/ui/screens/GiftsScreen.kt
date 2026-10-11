package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.data.api.dto.GiftTariffDto
import com.darkprince.vpn.data.repo.GiftRepository
import com.darkprince.vpn.ui.theme.BrandColors
import com.darkprince.vpn.ui.theme.GroupCard
import com.darkprince.vpn.ui.theme.SectionHeader
import com.darkprince.vpn.ui.theme.ServiceDownBanner
import com.darkprince.vpn.ui.theme.serviceDown
import com.darkprince.vpn.ui.vm.GiftReady
import com.darkprince.vpn.ui.vm.GiftsViewModel

/**
 * Подарки: подарить подписку другу и активировать подаренную.
 *
 * Подарок — это код. Купили — получили «GIFT-…», отправили другу любым
 * мессенджером; друг вводит его здесь или на сайте.
 */
@Composable
fun GiftsScreen(viewModel: GiftsViewModel, onShare: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val down = serviceDown()
    val busy = state.busy || down
    var code by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Text("Подарки", style = MaterialTheme.typography.headlineSmall)
            ServiceDownBanner(modifier = Modifier.padding(top = 10.dp))
        }

        state.ready?.let { ready ->
            item { ReadyCard(ready, onShare = onShare, onDone = viewModel::dismissReady) }
        }
        if (state.waitingPayment) {
            item {
                GroupCard {
                    Row(
                        Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.height(24.dp))
                        Text("Ждём оплату. Код подарка появится здесь, как только деньги дойдут")
                    }
                }
            }
        }

        item { SectionHeader("Мне подарили код") }
        item {
            GroupCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.trim() },
                        label = { Text("Код подарка") },
                        placeholder = { Text("GIFT-…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { viewModel.activate(code) },
                        enabled = !busy && code.length >= 8,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Активировать") }
                }
            }
        }

        state.error?.takeIf { !down }?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        state.info?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }

        val config = state.config
        if (state.loading && config == null) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (config != null && config.isEnabled && config.tariffs.isNotEmpty()) {
            item { SectionHeader("Подарить подписку") }
            item {
                GiftForm(
                    tariffs = config.tariffs,
                    balanceKopeks = config.balanceKopeks,
                    payments = state.payments,
                    busy = busy,
                    onBuy = viewModel::buy,
                )
            }
        }

        if (state.sent.isNotEmpty()) {
            item { SectionHeader("Я подарил") }
            items(state.sent, key = { "sent-${it.token}" }) { gift ->
                val claimable = gift.status in setOf("paid", "pending_activation")
                GiftRow(
                    title = listOfNotNull(gift.tariffName, "${gift.periodDays} дн.").joinToString(" · "),
                    subtitle = listOfNotNull(
                        giftStatus(gift.status),
                        gift.activatedBy?.let { "активировал $it" },
                        gift.recipient?.let { "для $it" },
                    ).joinToString(" · "),
                    action = if (claimable) "Отправить код" else null,
                    onAction = {
                        onShare(shareText(GiftRepository.codeOf(gift.token), gift.tariffName, gift.periodDays))
                    },
                )
            }
        }
        if (state.received.isNotEmpty()) {
            item { SectionHeader("Мне подарили") }
            items(state.received, key = { "got-${it.token}" }) { gift ->
                GiftRow(
                    title = listOfNotNull(gift.tariffName, "${gift.periodDays} дн.").joinToString(" · "),
                    subtitle = listOfNotNull(giftStatus(gift.status), gift.sender?.let { "от $it" }, gift.message)
                        .joinToString(" · "),
                    action = null,
                    onAction = {},
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun giftStatus(status: String): String = when (status) {
    "delivered" -> "активирован"
    "paid", "pending_activation" -> "ждёт активации"
    "pending" -> "ждёт оплаты"
    "failed" -> "оплата не прошла"
    "expired" -> "истёк"
    else -> status
}

private fun shareText(code: String, tariff: String?, days: Int?): String =
    "Дарю тебе VPN" + (tariff?.let { " «$it»" } ?: "") + (days?.let { " на $it дн." } ?: "") +
        "!\nКод подарка: $code\nВведи его в приложении DarkPrince VPN (Ещё → Подарки) или в личном кабинете."

@Composable
private fun ReadyCard(ready: GiftReady, onShare: (String) -> Unit, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    GroupCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🎁 Подарок готов", style = MaterialTheme.typography.titleLarge)
            Text(ready.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                ready.code,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .clickable { clipboard.setText(AnnotatedString(ready.code)) }
                    .padding(14.dp),
            )
            Text(
                "Отправьте код другу. Активировать его может любой, у кого он окажется, — делитесь лично.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onShare(shareText(ready.code, ready.title, null)) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Отправить другу") }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
        }
    }
}

@Composable
private fun GiftForm(
    tariffs: List<GiftTariffDto>,
    balanceKopeks: Long,
    payments: List<com.darkprince.vpn.ui.vm.GiftPayment>,
    busy: Boolean,
    onBuy: (Long, Int, com.darkprince.vpn.ui.vm.GiftPayment, String, String) -> Unit,
) {
    var tariffId by rememberSaveable { mutableStateOf(tariffs.first().id) }
    val tariff = tariffs.firstOrNull { it.id == tariffId } ?: tariffs.first()
    var days by rememberSaveable(tariff.id) { mutableStateOf(tariff.periods.first().days) }
    val period = tariff.periods.firstOrNull { it.days == days } ?: tariff.periods.first()
    var paymentKey by rememberSaveable { mutableStateOf<String?>(null) }
    // хватает на балансе — предлагаем его, нет — первую кассу
    val defaultPayment = payments.firstOrNull { it.fromBalance && balanceKopeks >= period.priceKopeks }
        ?: payments.firstOrNull { !it.fromBalance } ?: payments.first()
    val payment = payments.firstOrNull { (it.methodId ?: "balance") == paymentKey } ?: defaultPayment
    var recipient by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }

    GroupCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Choices(
                items = tariffs.map { it.id.toString() to it.name },
                selected = tariff.id.toString(),
                onSelect = { tariffId = it.toLong() },
            )
            Choices(
                items = tariff.periods.map {
                    it.days.toString() to "${it.days} дн. · ${formatKopeks(it.priceKopeks)}" +
                        (it.discountPercent?.let { d -> " −$d%" } ?: "")
                },
                selected = period.days.toString(),
                onSelect = { days = it.toInt() },
            )
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                label = { Text("Кому (необязательно)") },
                placeholder = { Text("@ник в Telegram или почта") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = message,
                onValueChange = { if (it.length <= 300) message = it },
                label = { Text("Пожелание (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Оплата", style = MaterialTheme.typography.titleSmall)
            Choices(
                items = payments.map {
                    (it.methodId ?: "balance") to
                        if (it.fromBalance) "С баланса · ${formatKopeks(balanceKopeks)}" else it.label
                },
                selected = payment.methodId ?: "balance",
                onSelect = { paymentKey = it },
            )
            if (payment.fromBalance && balanceKopeks < period.priceKopeks) {
                Text(
                    "На балансе не хватает ${formatKopeks(period.priceKopeks - balanceKopeks)} — выберите карту или СБП",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.Warning,
                )
            }
            Button(
                onClick = { onBuy(tariff.id, period.days, payment, recipient, message) },
                enabled = !busy && !(payment.fromBalance && balanceKopeks < period.priceKopeks),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("Подарить за ${formatKopeks(period.priceKopeks)}") }
        }
    }
}

/** Выбор одного из нескольких — «таблетками», переносятся по строкам. */
@Composable
private fun Choices(items: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (key, label) ->
                    val on = key == selected
                    val shape = RoundedCornerShape(14.dp)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) scheme.onPrimary else scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (on) scheme.primary else scheme.onSurface.copy(alpha = 0.04f))
                            .border(1.dp, if (on) scheme.primary else scheme.outline.copy(alpha = 0.7f), shape)
                            .clickable { onSelect(key) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GiftRow(title: String, subtitle: String, action: String?, onAction: () -> Unit) {
    GroupCard {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            action?.let {
                val accent = MaterialTheme.colorScheme.primary
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
