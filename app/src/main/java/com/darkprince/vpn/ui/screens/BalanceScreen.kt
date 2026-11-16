package com.darkprince.vpn.ui.screens

import com.darkprince.vpn.ui.theme.serviceDown
import com.darkprince.vpn.ui.theme.ServiceDownBanner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.remember
import com.darkprince.vpn.ui.theme.LocalBottomInset
import com.darkprince.vpn.ui.theme.LocalNavHidden
import com.darkprince.vpn.ui.theme.LocalPalette
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.data.api.dto.TransactionDto
import com.darkprince.vpn.ui.theme.AquaIcon
import com.darkprince.vpn.ui.theme.AquaIconView
import com.darkprince.vpn.ui.theme.BrandColors
import com.darkprince.vpn.ui.theme.glass
import com.darkprince.vpn.ui.theme.ink
import com.darkprince.vpn.ui.theme.GroupCard
import com.darkprince.vpn.ui.theme.RowDivider
import com.darkprince.vpn.ui.theme.SectionHeader
import com.darkprince.vpn.ui.vm.BalanceViewModel
import java.util.Locale
import kotlin.math.abs

/**
 * Деньги по-человечески: «285 ₽», «1 322,50 ₽», «−63 ₽».
 *
 * Копейки показываем, только когда они есть: «285,00 ₽» на каждой кнопке
 * читается как бухгалтерская выписка. Тысячи отбиваются пробелом.
 */
fun formatKopeks(kopeks: Long): String {
    val sign = if (kopeks < 0) "−" else ""
    val whole = String.format(Locale("ru"), "%,d", abs(kopeks / 100))
    val rest = abs(kopeks % 100)
    return if (rest == 0L) "$sign$whole ₽"
    else "$sign$whole," + String.format(Locale.US, "%02d", rest) + " ₽"
}

/** Частые суммы пополнения — одним нажатием, без клавиатуры. */
private val QuickAmounts = listOf(100, 300, 500, 1000)

/** Пределы суммы пополнения: меньше десяти рублей касса не принимает. */
private const val MIN_TOPUP = 10L
private const val MAX_TOPUP = 100_000L
private const val TOPUP_STEP = 50L

/** «1 000», «300»: тысячи через неразрывный пробел, без копеек. */
private fun formatAmount(rubles: Long): String = String.format(Locale("ru"), "%,d", rubles)

/** Круглая кнопка «−» / «+» рядом с суммой. */
@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(ink(0.05f))
            .border(1.dp, ink(0.12f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else ink(0.3f),
        )
    }
}

/**
 * Своя цифровая панель вместо системной клавиатуры: кнопка «Пополнить»
 * остаётся над цифрами и сразу показывает введённую сумму. Пока панель
 * открыта, нижняя навигация спрятана. Первая нажатая цифра заменяет
 * прежнюю сумму, дальше цифры дописываются.
 */
@Composable
private fun AmountKeypad(
    amount: String,
    canPay: Boolean,
    payLabel: String,
    onChange: (String) -> Unit,
    onPay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hidden = LocalNavHidden.current
    DisposableEffect(Unit) {
        hidden?.value = true
        onDispose { hidden?.value = false }
    }
    androidx.activity.compose.BackHandler(onBack = onDismiss)

    var fresh by remember { mutableStateOf(true) }
    fun press(digits: String) {
        val base = if (fresh) "" else amount
        val next = (base + digits).trimStart('0')
        fresh = false
        if (next.length <= 6 && (next.toLongOrNull() ?: 0) <= MAX_TOPUP) onChange(next)
    }

    Box(Modifier.fillMaxSize()) {
        // тап мимо панели закрывает её
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(LocalPalette.current.background.copy(alpha = 0.96f))
                .border(
                    1.dp, ink(0.10f),
                    RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                )
                .clickable(enabled = false) {}
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 14.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ink(0.2f))
            )
            Button(
                onClick = onPay,
                enabled = canPay,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp).height(54.dp),
                shape = RoundedCornerShape(27.dp),
            ) { Text(payLabel, style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.height(10.dp))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { d -> Key(Modifier.weight(1f), onClick = { press(d) }) { KeyText(d) } }
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Key(Modifier.weight(1f), plain = true, onClick = { press("00") }) { KeyText("00") }
                Key(Modifier.weight(1f), onClick = { press("0") }) { KeyText("0") }
                Key(Modifier.weight(1f), plain = true, onClick = {
                    fresh = false
                    onChange(amount.dropLast(1))
                }) {
                    AquaIconView(AquaIcon.Backspace, MaterialTheme.colorScheme.onBackground, size = 26.dp, stroke = 1.4f)
                }
            }
        }
    }
}

@Composable
private fun KeyText(text: String) {
    Text(text, fontSize = 26.sp, fontWeight = FontWeight.Light)
}

@Composable
private fun Key(
    modifier: Modifier,
    plain: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (plain) Color.Transparent else ink(0.06f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Сколько операций видно сразу; остальные — по кнопке. */
private const val HISTORY_PREVIEW = 5

@Composable
fun BalanceScreen(viewModel: BalanceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // возвращаемся из окна оплаты — сразу подтягиваем баланс
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onScreenResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var amount by rememberSaveable { mutableStateOf("300") }
    var keypadOpen by rememberSaveable { mutableStateOf(false) }
    val down = serviceDown()

    // Пришли сюда за недостающей суммой — подставляем её: ровно столько,
    // сколько не хватает, округлив вверх до рубля (и не меньше 10 ₽ — меньше
    // касса не принимает).
    val pendingPurchase = state.pendingPurchase
    LaunchedEffect(pendingPurchase?.createdAt, pendingPurchase?.missingKopeks) {
        val missing = pendingPurchase?.missingKopeks ?: return@LaunchedEffect
        amount = ((missing + 99) / 100).coerceAtLeast(10).toString()
    }
    var selectedMethod by rememberSaveable { mutableStateOf<String?>(null) }
    var promo by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.promoAccepted) { if (state.promoAccepted > 0) promo = "" }
    var showAllHistory by rememberSaveable { mutableStateOf(false) }

    // Все варианты всех способов одним рядом: человеку неважно, что СБП и
    // карта — это одна касса, ему важно, чем платить.
    val choices = state.methods.flatMap { it.choices }
    val choice = choices.firstOrNull { it.key == selectedMethod } ?: choices.firstOrNull()

    val history = if (showAllHistory) state.transactions
        else state.transactions.take(HISTORY_PREVIEW)

    // код на дни, а подписок несколько — бот спрашивает, к какой применить
    state.promoChoice?.let { choice ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPromoChoice,
            title = { Text("К какой подписке применить?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    choice.options.forEach { option ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.activatePromo(choice.code, option.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                option.tariffName?.takeIf { it.isNotBlank() } ?: "Подписка #${option.id}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            option.daysLeft?.let { days ->
                                Text(
                                    if (days > 0) "осталось $days дн." else "истекла",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::dismissPromoChoice) { Text("Отмена") } },
        )
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(start = 4.dp, top = 12.dp)) {
                Text("Баланс", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Пополнение, промокоды и история операций",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // Сумма на счёте — главное на экране: крупная, как ценник — рубли
        // большие, копейки и знак валюты мелким индексом справа сверху.
        item {
            val balance = state.balanceKopeks
            val lastTopUp = state.transactions.firstOrNull {
                it.type == "deposit" && (it.amountKopeks ?: 0) > 0
            }
            Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp)) {
                Text(
                    "На счёте",
                    fontSize = 11.sp,
                    letterSpacing = 0.9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (balance == null) {
                        Text("—", fontSize = 60.sp, fontWeight = FontWeight.Light)
                    } else {
                        val sign = if (balance < 0) "−" else ""
                        val whole = String.format(Locale("ru"), "%,d", abs(balance / 100))
                        val kopeks = String.format(Locale.US, "%02d", abs(balance % 100))
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                "$sign$whole",
                                fontSize = 60.sp,
                                lineHeight = 62.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = (-2.5).sp,
                                maxLines = 1,
                            )
                            Column(Modifier.padding(start = 4.dp, top = 6.dp)) {
                                Text(kopeks, fontSize = 24.sp, lineHeight = 26.sp)
                                Text(
                                    "₽",
                                    fontSize = 17.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier.size(46.dp).glass(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        AquaIconView(AquaIcon.Wallet, ink(0.8f), size = 20.dp, stroke = 1.5f)
                    }
                }
                if (lastTopUp != null) {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
                        color = ink(0.10f),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "Последнее пополнение",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "+" + formatKopeks(lastTopUp.amountKopeks ?: 0) +
                                (lastTopUp.createdAt?.let { formatTxDate(it) }?.let { " · $it" } ?: ""),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        item {
            ServiceDownBanner("Пополнить баланс и применить промокод можно будет, когда сервис вернётся.")
        }

        pendingPurchase?.let { pending ->
            item { PendingPurchaseBanner(pending, state.completingPurchase, viewModel::cancelPendingPurchase) }
        }

        item {
            val rubles = amount.toLongOrNull() ?: 0
            val valid = rubles in MIN_TOPUP..MAX_TOPUP
            Column(
                modifier = Modifier.fillMaxWidth().glass().padding(horizontal = 16.dp, vertical = 18.dp),
            ) {
                Text(
                    "Сумма пополнения",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    StepButton("−", enabled = rubles > MIN_TOPUP) {
                        amount = (rubles - TOPUP_STEP).coerceAtLeast(MIN_TOPUP).toString()
                    }
                    Row(
                        modifier = Modifier
                            .widthIn(min = 150.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { keypadOpen = true }
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            formatAmount(rubles),
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-1.5).sp,
                            maxLines = 1,
                        )
                        Text(
                            " ₽",
                            fontSize = 30.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    StepButton("+", enabled = rubles < MAX_TOPUP) {
                        amount = (rubles + TOPUP_STEP).coerceAtMost(MAX_TOPUP).toString()
                    }
                }
                Text(
                    if (valid || rubles == 0L) "нажмите на сумму, чтобы изменить"
                    else "от ${formatAmount(MIN_TOPUP)} ₽ до ${formatAmount(MAX_TOPUP)} ₽",
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 12.sp,
                    color = if (valid || rubles == 0L) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
                )

                // быстрые суммы — со знаком рубля у каждой
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    QuickAmounts.forEach { rub ->
                        Pill(
                            text = "${formatAmount(rub.toLong())} ₽",
                            selected = amount == rub.toString(),
                            onClick = { amount = rub.toString() },
                            modifier = Modifier.height(36.dp),
                        )
                    }
                }

                // Выбор способа показываем, только когда выбирать есть из
                // чего: один-единственный чип выглядел кнопкой, которую
                // зачем-то надо нажать.
                if (choices.size > 1) {
                    Row(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        choices.forEach { option ->
                            Pill(
                                text = option.label,
                                selected = option.key == choice?.key,
                                onClick = { selectedMethod = option.key },
                            )
                        }
                    }
                } else if (state.methods.isEmpty() && !state.loading) {
                    Text(
                        "Способы оплаты сейчас недоступны",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                Button(
                    onClick = {
                        choice?.let { viewModel.topup(rubles, it.method, it.optionId) }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    enabled = !state.creatingPayment && state.methods.isNotEmpty() && valid && !down,
                ) {
                    Text(
                        when {
                            state.creatingPayment -> "Создаём платёж…"
                            valid -> "Пополнить на ${formatKopeks(rubles * 100)}"
                            else -> "Пополнить"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (choices.size == 1) {
                    Text(
                        "Оплата: ${choices.first().label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                    )
                }
                if (state.pendingPayment != null) {
                    OutlinedButton(
                        onClick = { viewModel.checkPending() },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("Я оплатил — проверить")
                    }
                }
            }
        }

        // Промокод — одной строкой: поле и кнопка рядом. Отдельная карточка
        // с заголовком и кнопкой во всю ширину занимала пол-экрана ради
        // действия, которое делают раз в год.
        item {
            GroupCard {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.LocalOffer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    TextField(
                        value = promo,
                        onValueChange = { promo = it.trim() },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Промокод") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    TextButton(
                        onClick = { viewModel.activatePromo(promo) },
                        enabled = promo.isNotBlank() && !down,
                    ) { Text("Применить") }
                }
            }
        }

        state.error?.takeIf { !down }?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
        state.info?.let { info ->
            item { Text(info, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
        }

        item {
            Spacer(Modifier.height(4.dp))
            SectionHeader("История")
            if (state.transactions.isEmpty()) {
                Text(
                    "Операций пока нет",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        // Вся история — одна карточка со строками, а не стопка отдельных
        // плашек: так это читается как выписка, а не как лента.
        if (history.isNotEmpty()) {
            item {
                GroupCard {
                    history.forEachIndexed { index, tx ->
                        if (index > 0) RowDivider()
                        TransactionRow(tx)
                    }
                }
            }
        }

        if (!showAllHistory && state.transactions.size > HISTORY_PREVIEW) {
            item {
                TextButton(
                    onClick = { showAllHistory = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Показать все · ${state.transactions.size}") }
            }
        }

        item { Spacer(Modifier.height(16.dp + LocalBottomInset.current)) }
    }

    if (keypadOpen) {
        AmountKeypad(
            amount = amount,
            canPay = (amount.toLongOrNull() ?: 0) in MIN_TOPUP..MAX_TOPUP && !state.creatingPayment &&
                state.methods.isNotEmpty() && !down,
            payLabel = (amount.toLongOrNull() ?: 0).let {
                if (it in MIN_TOPUP..MAX_TOPUP) "Пополнить на ${formatKopeks(it * 100)}" else "Пополнить"
            },
            onChange = { amount = it },
            onPay = {
                keypadOpen = false
                choice?.let { viewModel.topup(amount.toLongOrNull() ?: 0, it.method, it.optionId) }
            },
            onDismiss = { keypadOpen = false },
        )
    }
    }
}

/**
 * Зачем пополняем: «Не хватает 182 ₽ на „Стандарт · 30 дн.“». После
 * зачисления покупка оплатится сама — об этом и говорим, чтобы человек не
 * шёл потом на «Тарифы» платить второй раз.
 */
@Composable
private fun PendingPurchaseBanner(
    pending: com.darkprince.vpn.data.repo.PendingPurchase,
    completing: Boolean,
    onCancel: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.5f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Не хватает ${formatKopeks(pending.missingKopeks)}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "на «${pending.title}» за ${formatKopeks(pending.priceKopeks)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            if (completing) "Оплачиваем подписку…"
            else "Пополните баланс — подписка оплатится сама, сразу после зачисления.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!completing) {
            TextButton(
                onClick = onCancel,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text("Не оплачивать автоматически") }
        }
    }
}

/** Пилюля-переключатель: одна строка, текст по центру, выбранная залита. */
@Composable
private fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .background(if (selected) scheme.primary.copy(alpha = 0.16f) else ink(0.05f))
            .border(1.dp, if (selected) scheme.primary else ink(0.12f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.primary else scheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun TransactionRow(tx: TransactionDto) {
    val amount = tx.amountKopeks
    val income = (amount ?: 0) > 0
    val tint = if (income) BrandColors.Success else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            AquaIconView(
                if (income) AquaIcon.Down else AquaIcon.Up,
                tint,
                size = 18.dp,
                stroke = 1.6f,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                transactionTitle(tx),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            tx.createdAt?.let { formatTxDate(it) }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        amount?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                if (income) "+" + formatKopeks(it) else formatKopeks(it),
                style = MaterialTheme.typography.titleSmall,
                color = if (income) BrandColors.Success else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/**
 * Подпись операции. Бот пишет часть описаний по-английски — служебные
 * переводим, остальное показываем как есть.
 */
private fun transactionTitle(tx: TransactionDto): String {
    val text = tx.description?.trim().orEmpty()
    return when {
        text.equals("Admin balance adjustment", ignoreCase = true) -> "Начисление от администрации"
        text.isNotEmpty() -> text
        tx.type == "deposit" -> "Пополнение"
        tx.type == "subscription_payment" -> "Оплата подписки"
        else -> "Операция"
    }
}

/**
 * «26 авг., 06:26» вместо «2026-08-26 06:26:09». Год — только если не
 * текущий. Время сервер отдаёт в UTC, показываем по часам телефона.
 */
private fun formatTxDate(raw: String): String? = runCatching {
    val zone = java.time.ZoneId.systemDefault()
    val instant = runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }
        .getOrElse {
            java.time.LocalDateTime.parse(raw.take(19)).toInstant(java.time.ZoneOffset.UTC)
        }
    val local = instant.atZone(zone)
    val pattern = if (local.year == java.time.LocalDate.now(zone).year) "d MMM, HH:mm" else "d MMM yyyy, HH:mm"
    java.time.format.DateTimeFormatter.ofPattern(pattern, Locale("ru")).format(local)
}.getOrNull()
