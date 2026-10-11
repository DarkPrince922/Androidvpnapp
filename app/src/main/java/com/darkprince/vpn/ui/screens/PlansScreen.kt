package com.darkprince.vpn.ui.screens

import com.darkprince.vpn.ui.theme.serviceDown
import com.darkprince.vpn.ui.theme.ServiceDownBanner
import com.darkprince.vpn.vpn.VpnStateStore
import com.darkprince.vpn.vpn.VpnState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.data.repo.PeriodPrice
import com.darkprince.vpn.data.repo.TariffOffer
import com.darkprince.vpn.ui.theme.BrandColors
import com.darkprince.vpn.ui.theme.GroupCard
import com.darkprince.vpn.ui.theme.SectionHeader
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
    onNeedTopUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val vpnState by VpnStateStore.state.collectAsStateWithLifecycle()
    val vpnOn = vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING
    // Кабинет лежит — купить, продлить и докупить всё равно не выйдет.
    // Кнопки гасим заранее, а не даём нажать и получить ошибку.
    val down = serviceDown()
    val busy = state.purchasing || down

    // Денег не хватило — сразу на пополнение: недостающая сумма там уже
    // стоит, а выбранная подписка оплатится после зачисления.
    LaunchedEffect(state.needTopUp) {
        if (state.needTopUp) {
            viewModel.consumeTopUp()
            onNeedTopUp()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Text("Тарифы", style = MaterialTheme.typography.headlineSmall)
            ServiceDownBanner(modifier = Modifier.padding(top = 10.dp))
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
                GroupCard {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Пробный период", style = MaterialTheme.typography.titleLarge)
                        PrimaryAction(
                            text = "Попробовать бесплатно",
                            enabled = !busy,
                            onClick = { viewModel.activateTrial() },
                        )
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
                    purchasing = busy,
                    working = card.id == state.workingSubscriptionId,
                    // при одной подписке выбирать нечего — строку не показываем
                    showWorkingRow = state.cards.size > 1,
                    switchLocked = vpnOn,
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
                    purchasing = busy,
                    onBuy = { period -> viewModel.purchase(tariff, period) },
                )
            }
        }

        // при лежащем кабинете ошибка запроса — та же новость, что в полосе сверху
        state.error?.takeIf { !down }?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        state.info?.let { info ->
            item { Text(info, color = MaterialTheme.colorScheme.primary) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    SectionHeader(text, Modifier.padding(top = 6.dp))
}

/**
 * Карточка своей подписки.
 *
 * На виду только главное: что за подписка, сколько осталось, и продление.
 * Устройства и трафик нужны редко — они свёрнуты в строку «Устройства и
 * трафик» и раскрываются по нажатию. Раньше всё было раскрыто сразу, и одна
 * подписка занимала два экрана.
 */
@Composable
private fun MySubscriptionCard(
    card: OwnedSubscription,
    purchasing: Boolean,
    working: Boolean,
    showWorkingRow: Boolean,
    switchLocked: Boolean,
    onMakeWorking: () -> Unit,
    onRenew: (PeriodPrice) -> Unit,
    onBuyDevices: (Int) -> Unit,
    onReduceDevices: (Int) -> Unit,
    onBuyTraffic: (Int) -> Unit,
    onOpenDevices: () -> Unit,
) {
    var selectedPeriod by remember(card.id) { mutableStateOf<PeriodPrice?>(null) }
    val period = selectedPeriod ?: card.periods.firstOrNull()
    var extrasOpen by rememberSaveable(card.id) { mutableStateOf(false) }
    val hasDevices = card.devices != null
    val hasTraffic = !card.unlimitedTraffic && card.trafficPackages.isNotEmpty()

    GroupCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        card.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TermBadge(card)
                }
                Text(
                    cardSummary(card),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (showWorkingRow) {
                    if (working) {
                        Text(
                            "● Подключение идёт по этой подписке",
                            style = MaterialTheme.typography.labelMedium,
                            color = BrandColors.Success,
                        )
                    } else if (card.sub.isActive) {
                        // Не молча при открытии карточки: переключение гасит
                        // поднятый туннель, и делать это за человека нельзя.
                        if (switchLocked) {
                            Text(
                                "Чтобы подключаться по этой, сначала отключите VPN",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            SmallAction("Подключаться по этой", enabled = !purchasing, onClick = onMakeWorking)
                        }
                    }
                }
            }

            if (card.periods.isNotEmpty()) {
                PeriodGrid(
                    periods = card.periods,
                    selected = period,
                    onSelect = { selectedPeriod = it },
                )
                PrimaryAction(
                    text = if (period != null) "Продлить за ${formatKopeks(period.priceKopeks)}" else "Продлить",
                    enabled = !purchasing && period != null,
                    onClick = { period?.let(onRenew) },
                )
            }

            if (hasDevices || hasTraffic) {
                ExpandRow(
                    title = if (hasTraffic) "Устройства и трафик" else "Устройства",
                    summary = devicesSummary(card),
                    warn = devicesOverLimit(card),
                    open = extrasOpen,
                    onToggle = { extrasOpen = !extrasOpen },
                )
                AnimatedVisibility(
                    visible = extrasOpen,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (hasDevices) {
                            DevicesBlock(
                                card = card,
                                purchasing = purchasing,
                                onBuy = onBuyDevices,
                                onReduce = onReduceDevices,
                                onOpenDevices = onOpenDevices,
                            )
                        }
                        if (hasTraffic) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Докупить трафик", style = MaterialTheme.typography.titleSmall)
                                TileGrid(
                                    items = card.trafficPackages,
                                    key = { it.gb },
                                ) { pkg, modifier ->
                                    OptionTile(
                                        title = "+${pkg.gb} ГБ",
                                        subtitle = formatKopeks(pkg.priceKopeks),
                                        selected = false,
                                        enabled = !purchasing,
                                        onClick = { onBuyTraffic(pkg.gb) },
                                        modifier = modifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Срок подписки цветной «таблеткой»: зелёный — время есть, жёлтый — на исходе, красный — вышел. */
@Composable
private fun TermBadge(card: OwnedSubscription) {
    val days = card.daysLeft
    val (text, color) = when {
        !card.sub.isActive -> (card.sub.status ?: "Не активна") to BrandColors.Danger
        days == null -> "Активна" to BrandColors.Success
        days < 0 -> "Истекла" to BrandColors.Danger
        days == 0 -> "Последний день" to BrandColors.Warning
        days <= 7 -> "$days дн." to BrandColors.Warning
        else -> "$days дн." to BrandColors.Success
    }
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
    card.deviceLimit?.let { parts += "до $it устройств" }
    return parts.joinToString(" · ")
}

private fun devicesSummary(card: OwnedSubscription): String? {
    val limit = card.deviceLimit ?: return null
    val used = card.devices?.connectedCount ?: return "лимит $limit"
    return "$used из $limit"
}

private fun devicesOverLimit(card: OwnedSubscription): Boolean {
    val limit = card.deviceLimit ?: return false
    val used = card.devices?.connectedCount ?: return false
    return used > limit
}

/** Строка-раскрывашка: заголовок, сводка справа и стрелка. */
@Composable
private fun ExpandRow(
    title: String,
    summary: String?,
    warn: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        summary?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (warn) BrandColors.Danger else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = if (open) "Свернуть" else "Развернуть",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotation),
        )
    }
}

/**
 * Устройства подписки: счётчик «− N +» и кнопка докупки в одну строку,
 * под ними — уменьшение лимита и список устройств.
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Докупить устройства", style = MaterialTheme.typography.titleSmall)
        if (devices.purchaseAvailable) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Stepper(value = count, onChange = { count = it })
                val price = devices.pricePerDeviceKopeks
                SecondaryAction(
                    text = if (price != null) "+$count за ${formatKopeks(price * count)}" else "Докупить",
                    enabled = !purchasing,
                    onClick = { onBuy(count) },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            // без пояснения кнопка просто исчезала, и было непонятно, это
            // ограничение тарифа или сбой запроса
            val limitReached = devices.maxDeviceLimit != null && limit != null &&
                limit >= devices.maxDeviceLimit
            Text(
                devices.purchaseNote
                    ?: if (limitReached) "Достигнут максимум тарифа: ${devices.maxDeviceLimit}"
                    else "Докупка устройств для этой подписки недоступна",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction("Мои устройства", onClick = onOpenDevices)
            if (devices.reduceAvailable && limit != null && limit > 1) {
                val newLimit = (limit - count).coerceAtLeast(1)
                SmallAction(
                    "Уменьшить до $newLimit",
                    enabled = !purchasing && newLimit < limit,
                    onClick = { onReduce(newLimit) },
                )
            }
        }
    }
}

/** Счётчик «− N +» в одной «таблетке». */
@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int = 1, max: Int = 50) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton("−", enabled = value > min) { onChange(value - 1) }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp),
        )
        StepperButton("+", enabled = value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepperButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}

/** Главная кнопка карточки: залитая, крупная. */
@Composable
private fun PrimaryAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Второстепенная кнопка: рамка акцентного цвета, текст в одну строку. */
@Composable
private fun SecondaryAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Маленькое действие-«таблетка» вместо голого текста-ссылки. */
@Composable
private fun SmallAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (enabled) accent else accent.copy(alpha = 0.4f),
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.12f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
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

    GroupCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tariff.name, style = MaterialTheme.typography.titleLarge)
                tariff.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    offerSummary(tariff),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // единственный срок выбирать не из чего — он виден в кнопке
            if (tariff.periods.size > 1) {
                PeriodGrid(
                    periods = tariff.periods,
                    selected = period,
                    onSelect = { selectedPeriod = it },
                )
            }
            PrimaryAction(
                text = when {
                    period == null -> "Купить"
                    period.daily -> "Подключить · ${formatKopeks(period.priceKopeks)} в день"
                    else -> "Купить за ${formatKopeks(period.priceKopeks)}"
                },
                enabled = !purchasing && period != null,
                onClick = { period?.let(onBuy) },
            )
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
 * Сроки с ценой прямо на плитке.
 *
 * Без цены выбор был вслепую: чтобы сравнить, сколько стоят тридцать дней и
 * девяносто, приходилось тыкать в каждый и смотреть на кнопку внизу.
 */
@Composable
private fun PeriodGrid(
    periods: List<PeriodPrice>,
    selected: PeriodPrice?,
    onSelect: (PeriodPrice) -> Unit,
) {
    TileGrid(items = periods, key = { it.days }) { period, modifier ->
        OptionTile(
            title = if (period.daily) "Посуточно" else daysLabel(period.days),
            subtitle = if (period.daily) "${formatKopeks(period.priceKopeks)} в день"
                else formatKopeks(period.priceKopeks),
            selected = selected?.days == period.days,
            onClick = { onSelect(period) },
            modifier = modifier,
        )
    }
}

private fun daysLabel(days: Int): String {
    val word = when {
        days % 10 == 1 && days % 100 != 11 -> "день"
        days % 10 in 2..4 && days % 100 !in 12..14 -> "дня"
        else -> "дней"
    }
    return "$days $word"
}

/** Плитки по три в ряд — при трёх сроках они встают одной строкой. */
@Composable
private fun <T> TileGrid(
    items: List<T>,
    key: (T) -> Any,
    columns: Int = 3,
    tile: @Composable (T, Modifier) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    androidx.compose.runtime.key(key(item)) {
                        tile(item, Modifier.weight(1f))
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun OptionTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val background by animateColorAsState(
        if (selected) scheme.primary else scheme.onSurface.copy(alpha = 0.04f),
        label = "tile",
    )
    Column(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, if (selected) scheme.primary else scheme.outline.copy(alpha = 0.7f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val content = if (selected) scheme.onPrimary else scheme.onSurface
        Text(title, style = MaterialTheme.typography.titleSmall, color = content, maxLines = 1)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) content.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
