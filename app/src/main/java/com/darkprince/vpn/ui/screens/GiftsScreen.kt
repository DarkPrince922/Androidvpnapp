package com.darkprince.vpn.ui.screens

import com.darkprince.vpn.ui.theme.LoadingDots
import com.darkprince.vpn.ui.theme.LoadingCapsule
import androidx.compose.foundation.background
import com.darkprince.vpn.ui.theme.ink
import com.darkprince.vpn.ui.theme.glass
import com.darkprince.vpn.ui.theme.AquaIconView
import com.darkprince.vpn.ui.theme.AquaIcon
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.animateContentSize
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
            Column(Modifier.padding(start = 4.dp, top = 12.dp)) {
                Text("Подарки", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Подарить подписку другу или активировать код",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
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
                        LoadingDots()
                        Text("Ждём оплату. Код подарка появится здесь, как только деньги дойдут")
                    }
                }
            }
        }

        // Код, который подарили, — строкой; нажали — раскрывается поле
        item {
            var codeOpen by rememberSaveable { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().glass().animateContentSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { codeOpen = !codeOpen }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(ink(0.08f)),
                        contentAlignment = Alignment.Center,
                    ) { AquaIconView(AquaIcon.Bolt, ink(0.85f), size = 18.dp, stroke = 1.5f) }
                    Spacer(Modifier.width(12.dp))
                    Text("Мне подарили код", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    AquaIconView(
                        AquaIcon.Chevron,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        size = 18.dp,
                        stroke = 1.6f,
                        modifier = Modifier.rotate(if (codeOpen) -90f else 90f),
                    )
                }
                if (codeOpen) {
                    Column(
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.trim() },
                            placeholder = { Text("GIFT-…") },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = giftFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { viewModel.activate(code) },
                            enabled = !busy && code.length >= 8,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                        ) { Text("Активировать", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        state.error?.takeIf { !down }?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        state.info?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }

        val config = state.config
        if (state.loading && config == null) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    LoadingCapsule("Загружаем подарки")
                }
            }
        } else if (config != null && config.isEnabled && config.tariffs.isNotEmpty()) {
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
private fun giftFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = ink(0.14f),
    focusedBorderColor = MaterialTheme.colorScheme.primary,
)

/**
 * Подарок мастером: Тариф → Срок → Кому и оплата. Один шаг на экран, внизу
 * «Назад» и «Далее»; на последнем шаге «Далее» становится «Подарить за …».
 */
@Composable
private fun GiftForm(
    tariffs: List<GiftTariffDto>,
    balanceKopeks: Long,
    payments: List<com.darkprince.vpn.ui.vm.GiftPayment>,
    busy: Boolean,
    onBuy: (Long, Int, com.darkprince.vpn.ui.vm.GiftPayment, String, String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var step by rememberSaveable { mutableStateOf(1) }
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
    val notEnough = payment.fromBalance && balanceKopeks < period.priceKopeks

    Column(Modifier.fillMaxWidth()) {
        // --- шаги ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf("Тариф", "Срок", "Кому").forEachIndexed { index, label ->
                val n = index + 1
                val done = n < step
                val active = n == step
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                active -> scheme.primary
                                done -> scheme.primary.copy(alpha = 0.2f)
                                else -> ink(0.08f)
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) AquaIconView(AquaIcon.Check, scheme.primary, size = 14.dp, stroke = 2.6f)
                    else Text(
                        n.toString(),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) scheme.onPrimary else ink(0.5f),
                    )
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) scheme.onBackground else ink(0.5f),
                )
                if (index < 2) {
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(1.dp)
                            .background(ink(0.15f))
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        when (step) {
            1 -> {
                Text("Какой тариф дарим?", fontSize = 13.5.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(10.dp))
                GiftRadioGroup {
                    tariffs.forEachIndexed { index, t ->
                        if (index > 0) HorizontalDivider(color = ink(0.07f))
                        GiftRadioRow(
                            title = t.name,
                            trailing = t.periods.minOfOrNull { it.priceKopeks }?.let { "от ${formatKopeks(it)}" },
                            selected = t.id == tariff.id,
                            onClick = { tariffId = t.id },
                        )
                    }
                }
            }
            2 -> {
                Text(
                    "Тариф «${tariff.name}». На сколько дарим?",
                    fontSize = 13.5.sp,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                GiftRadioGroup {
                    tariff.periods.forEachIndexed { index, p ->
                        if (index > 0) HorizontalDivider(color = ink(0.07f))
                        GiftRadioRow(
                            title = "${p.days} дней",
                            badge = p.discountPercent?.let { "−$it%" },
                            trailing = formatKopeks(p.priceKopeks),
                            selected = p.days == period.days,
                            onClick = { days = p.days },
                        )
                    }
                }
            }
            else -> {
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    placeholder = { Text("Кому: @ник в Telegram или почта") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = giftFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 300) message = it },
                    placeholder = { Text("Пожелание (необязательно)") },
                    shape = RoundedCornerShape(18.dp),
                    colors = giftFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "ОПЛАТА",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 8.dp),
                )
                GiftRadioGroup {
                    payments.forEachIndexed { index, pay ->
                        if (index > 0) HorizontalDivider(color = ink(0.07f))
                        val key = pay.methodId ?: "balance"
                        GiftRadioRow(
                            title = if (pay.fromBalance) "С баланса" else pay.label,
                            trailing = if (pay.fromBalance) formatKopeks(balanceKopeks) else null,
                            selected = key == (payment.methodId ?: "balance"),
                            onClick = { paymentKey = key },
                        )
                    }
                }
                if (notEnough) {
                    Text(
                        "На балансе не хватает ${formatKopeks(period.priceKopeks - balanceKopeks)} — выберите карту или СБП",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.Warning,
                        modifier = Modifier.padding(start = 6.dp, top = 8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step > 1) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .glass(CircleShape)
                        .clickable { step -= 1 },
                    contentAlignment = Alignment.Center,
                ) {
                    AquaIconView(
                        AquaIcon.Chevron,
                        ink(0.85f),
                        size = 20.dp,
                        stroke = 1.8f,
                        modifier = Modifier.rotate(180f),
                    )
                }
                Spacer(Modifier.width(10.dp))
            }
            Button(
                onClick = {
                    if (step < 3) step += 1
                    else onBuy(tariff.id, period.days, payment, recipient, message)
                },
                enabled = step < 3 || (!busy && !notEnough),
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(27.dp),
            ) {
                Text(
                    if (step < 3) "Далее" else "Подарить за ${formatKopeks(period.priceKopeks)}",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Рамка группы вариантов: одна стеклянная плита. */
@Composable
private fun GiftRadioGroup(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp))) { content() }
}

/** Строка выбора с радио-кнопкой: название, отметка, цена справа. */
@Composable
private fun GiftRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: String? = null,
    badge: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) scheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(2.dp, if (selected) scheme.primary else ink(0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(scheme.primary))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            fontSize = 14.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Text(badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.primary, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
