package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.data.repo.PeriodPrice
import com.darkprince.vpn.data.repo.TariffOffer
import com.darkprince.vpn.ui.theme.BrandColors
import com.darkprince.vpn.ui.vm.PlansViewModel
import com.darkprince.vpn.ui.vm.OwnedSubscription

/**
 * Тарифы.
 *
 * Экран отвечает на два разных вопроса, и они разведены: сверху «что у меня
 * есть и что с этим делать», снизу «что ещё можно купить». Раньше и то и
 * другое лежало вперемешку одинаковыми карточками, а блоку устройств
 * приходилось держать собственный переключатель подписки — из-за него на
 * экране и оказывались цифры одной подписки под именем другой.
 *
 * Купленный тариф из магазина уходит: он живёт наверху своей карточкой, и
 * продлевают его там.
 */
@Composable
fun PlansScreen(
    viewModel: PlansViewModel,
    onOpenDevices: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Тарифы", style = MaterialTheme.typography.headlineSmall)
        }

        if (state.loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        if (state.trialAvailable) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Пробный период", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.activateTrial() },
                            enabled = !state.purchasing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Активировать пробный период")
                        }
                    }
                }
            }
        }

        if (state.cards.isNotEmpty()) {
            item { SectionTitle("Ваши подписки") }
            // Ключи с приставкой: ниже вторым списком идут тарифы, и их
            // нумерация своя. Голый номер подписки рано или поздно совпал бы
            // с номером тарифа, а LazyColumn требует ключи, уникальные по
            // всему списку, и на совпадении падает.
            items(state.cards, key = { "sub-${it.id}" }) { card ->
                MySubscriptionCard(
                    card = card,
                    purchasing = state.purchasing,
                    working = card.id == state.workingSubscriptionId,
                    // при одной подписке выбирать нечего — строку не показываем
                    showWorkingRow = state.cards.size > 1,
                    onMakeWorking = { viewModel.makeWorking(card.id) },
                    onRenew = { period -> viewModel.renew(card.id, period) },
                    onBuyDevices = { count -> viewModel.buyDevices(card.id, count) },
                    onReduceDevices = { limit -> viewModel.reduceDevices(card.id, limit) },
                    onBuyTraffic = { gb -> viewModel.buyTraffic(card.id, gb) },
                    onOpenDevices = { onOpenDevices(card.id) },
                )
            }
        }

        if (state.offers.isNotEmpty()) {
            item {
                SectionTitle(if (state.cards.isEmpty()) "Выберите тариф" else "Купить ещё")
            }
            items(state.offers, key = { "offer-${it.id}" }) { tariff ->
                OfferCard(
                    tariff = tariff,
                    purchasing = state.purchasing,
                    onBuy = { period -> viewModel.purchase(tariff, period) },
                )
            }
        }

        state.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        state.info?.let { info ->
            item { Text(info, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * Карточка своей подписки: срок, что в неё входит, и всё, что с ней можно
 * сделать. Устройства и трафик лежат здесь же — им незачем спрашивать, о
 * какой подписке речь, когда они внутри неё.
 */
@Composable
private fun MySubscriptionCard(
    card: OwnedSubscription,
    purchasing: Boolean,
    working: Boolean,
    showWorkingRow: Boolean,
    onMakeWorking: () -> Unit,
    onRenew: (PeriodPrice) -> Unit,
    onBuyDevices: (Int) -> Unit,
    onReduceDevices: (Int) -> Unit,
    onBuyTraffic: (Int) -> Unit,
    onOpenDevices: () -> Unit,
) {
    var selectedPeriod by remember(card.id) { mutableStateOf<PeriodPrice?>(null) }
    val period = selectedPeriod ?: card.periods.firstOrNull()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    card.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TermBadge(card)
            }

            Spacer(Modifier.height(6.dp))
            Text(
                cardSummary(card),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (showWorkingRow) {
                Spacer(Modifier.height(8.dp))
                if (working) {
                    Text(
                        "● Подключение идёт по этой подписке",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrandColors.Success,
                    )
                } else if (card.sub.isActive) {
                    // Не молча при открытии карточки: переключение гасит
                    // поднятый туннель, и делать это за человека нельзя.
                    TextButton(
                        onClick = onMakeWorking,
                        enabled = !purchasing,
                        contentPadding = PaddingValues(0.dp),
                    ) { Text("Подключаться по этой") }
                }
            }

            if (card.periods.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                PeriodRow(
                    periods = card.periods,
                    selected = period,
                    onSelect = { selectedPeriod = it },
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { period?.let(onRenew) },
                    enabled = !purchasing && period != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (period != null) "Продлить за ${formatKopeks(period.priceKopeks)}"
                        else "Продлить"
                    )
                }
            }

            if (card.devices != null) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                DevicesBlock(
                    card = card,
                    purchasing = purchasing,
                    onBuy = onBuyDevices,
                    onReduce = onReduceDevices,
                    onOpenDevices = onOpenDevices,
                )
            }

            if (!card.unlimitedTraffic && card.trafficPackages.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("Докупить трафик", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    card.trafficPackages.forEach { pkg ->
                        OutlinedButton(
                            onClick = { onBuyTraffic(pkg.gb) },
                            enabled = !purchasing,
                        ) {
                            Text("+${pkg.gb} ГБ · ${formatKopeks(pkg.priceKopeks)}")
                        }
                    }
                }
            }
        }
    }
}

/** Срок подписки цветом: зелёный — время есть, жёлтый — на исходе, красный — вышел. */
@Composable
private fun TermBadge(card: OwnedSubscription) {
    val days = card.daysLeft
    val (text, color) = when {
        !card.sub.isActive -> (card.sub.status ?: "Не активна") to BrandColors.Danger
        days == null -> "Активна" to BrandColors.Success
        days < 0 -> "Истекла" to BrandColors.Danger
        days == 0 -> "Сегодня последний день" to BrandColors.Warning
        days <= 7 -> "Осталось $days дн." to BrandColors.Warning
        else -> "Осталось $days дн." to BrandColors.Success
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

/** Что входит в подписку, одной строкой. */
private fun cardSummary(card: OwnedSubscription): String {
    val parts = mutableListOf<String>()
    val limitGb = card.sub.trafficLimitGb
    parts += if (card.unlimitedTraffic) {
        "Безлимитный трафик"
    } else {
        val used = card.sub.trafficUsedGb
        if (used != null && limitGb != null) {
            String.format(java.util.Locale.US, "%.0f из %.0f ГБ", used, limitGb)
        } else {
            String.format(java.util.Locale.US, "%.0f ГБ", limitGb ?: 0.0)
        }
    }
    card.deviceLimit?.let { limit ->
        val extra = card.extraDevices
        parts += if (extra != null) "$limit устройств · $extra докуплены"
        else "$limit устройств"
    }
    return parts.joinToString(" · ")
}

/**
 * Устройства этой подписки.
 *
 * Занято/лимит стоят рядом с действием, а не отдельной строкой сверху: цифра
 * нужна ровно в тот момент, когда человек решает, докупать или отключить
 * лишние.
 */
@Composable
private fun DevicesBlock(
    card: OwnedSubscription,
    purchasing: Boolean,
    onBuy: (Int) -> Unit,
    onReduce: (Int) -> Unit,
    onOpenDevices: () -> Unit,
) {
    val devices = card.devices ?: return
    var count by remember(card.id) { mutableStateOf(1) }
    val limit = card.deviceLimit
    val used = devices.connectedCount

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Устройства", style = MaterialTheme.typography.labelLarge)
        Text(
            when {
                used != null && limit != null -> "Занято $used из $limit"
                limit != null -> "Лимит $limit"
                else -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (used != null && limit != null && used >= limit) BrandColors.Danger
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (devices.purchaseAvailable) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { if (count > 1) count-- },
                enabled = count > 1,
            ) { Text("−") }
            Text(
                "$count",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            OutlinedButton(onClick = { count++ }) { Text("+") }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { onBuy(count) },
                enabled = !purchasing,
            ) {
                val price = devices.pricePerDeviceKopeks
                Text(
                    if (price != null) "Докупить · ${formatKopeks(price * count)}"
                    else "Докупить"
                )
            }
        }
    } else {
        // без пояснения кнопка просто исчезала, и было непонятно, это
        // ограничение тарифа или сбой запроса
        val limitReached = devices.maxDeviceLimit != null && limit != null &&
            limit >= devices.maxDeviceLimit
        Spacer(Modifier.height(6.dp))
        Text(
            devices.purchaseNote
                ?: if (limitReached) "Достигнут максимум тарифа: ${devices.maxDeviceLimit}"
                else "Докупка устройств для этой подписки недоступна",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (devices.reduceAvailable && limit != null) {
            val newLimit = (limit - count).coerceAtLeast(1)
            TextButton(
                onClick = { onReduce(newLimit) },
                enabled = !purchasing && newLimit < limit,
            ) { Text("Уменьшить до $newLimit") }
        }
        TextButton(onClick = onOpenDevices) { Text("Управление") }
    }
}

/** Карточка тарифа в магазине: только то, чего у человека ещё нет. */
@Composable
private fun OfferCard(
    tariff: TariffOffer,
    purchasing: Boolean,
    onBuy: (PeriodPrice) -> Unit,
) {
    var selectedPeriod by remember(tariff.id) { mutableStateOf<PeriodPrice?>(null) }
    val period = selectedPeriod ?: tariff.periods.firstOrNull()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                tariff.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            tariff.description?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                offerSummary(tariff),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            PeriodRow(
                periods = tariff.periods,
                selected = period,
                onSelect = { selectedPeriod = it },
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { period?.let(onBuy) },
                enabled = !purchasing && period != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (period != null) "Купить за ${formatKopeks(period.priceKopeks)}"
                    else "Купить"
                )
            }
        }
    }
}

private fun offerSummary(tariff: TariffOffer): String {
    val parts = mutableListOf<String>()
    tariff.trafficLimitGb?.let {
        parts += if (it <= 0) "Безлимитный трафик" else "$it ГБ"
    }
    tariff.deviceLimit?.let { parts += "$it устройств" }
    return parts.joinToString(" · ")
}

/**
 * Периоды с ценой прямо в чипе.
 *
 * Без цены выбор был вслепую: чтобы сравнить, сколько стоят тридцать дней и
 * девяносто, приходилось тыкать в каждый и смотреть на кнопку внизу.
 */
@Composable
private fun PeriodRow(
    periods: List<PeriodPrice>,
    selected: PeriodPrice?,
    onSelect: (PeriodPrice) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        periods.forEach { period ->
            FilterChip(
                selected = selected?.days == period.days,
                onClick = { onSelect(period) },
                label = { Text("${period.days} дн. · ${formatKopeks(period.priceKopeks)}") },
            )
        }
    }
}
