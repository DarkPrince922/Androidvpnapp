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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.data.api.dto.TransactionDto
import com.darkprince.vpn.ui.theme.BrandColors
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
    var showAllHistory by rememberSaveable { mutableStateOf(false) }

    // Все варианты всех способов одним рядом: человеку неважно, что СБП и
    // карта — это одна касса, ему важно, чем платить.
    val choices = state.methods.flatMap { it.choices }
    val choice = choices.firstOrNull { it.key == selectedMethod } ?: choices.firstOrNull()

    val history = if (showAllHistory) state.transactions
        else state.transactions.take(HISTORY_PREVIEW)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Text("Баланс", style = MaterialTheme.typography.headlineSmall)
        }

        // Сумма на счёте — главное на экране: крупно и по центру, без рамки.
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "На счёте",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    state.balanceKopeks?.let { formatKopeks(it) } ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                )
            }
        }

        item {
            ServiceDownBanner("Пополнить баланс и применить промокод можно будет, когда сервис вернётся.")
        }

        pendingPurchase?.let { pending ->
            item { PendingPurchaseBanner(pending, state.completingPurchase, viewModel::cancelPendingPurchase) }
        }

        item {
            GroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Пополнить", style = MaterialTheme.typography.titleMedium)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickAmounts.forEach { rub ->
                            Pill(
                                text = "$rub ₽",
                                selected = amount == rub.toString(),
                                onClick = { amount = rub.toString() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter(Char::isDigit).take(7) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Своя сумма, ₽") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    // Выбор способа показываем, только когда выбирать есть из
                    // чего: один-единственный чип выглядел кнопкой, которую
                    // зачем-то надо нажать.
                    if (choices.size > 1) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
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
                        )
                    }

                    val rubles = amount.toLongOrNull() ?: 0
                    Button(
                        onClick = {
                            choice?.let { viewModel.topup(rubles, it.method, it.optionId) }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        enabled = !state.creatingPayment && state.methods.isNotEmpty() && rubles > 0 &&
                            !down,
                    ) {
                        Text(
                            when {
                                state.creatingPayment -> "Создаём платёж…"
                                rubles > 0 -> "Пополнить на ${formatKopeks(rubles * 100)}"
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
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    if (state.pendingPayment != null) {
                        OutlinedButton(
                            onClick = { viewModel.checkPending() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text("Я оплатил — проверить")
                        }
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

        item { Spacer(Modifier.height(16.dp)) }
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
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (selected) scheme.onSurface else Color.Transparent)
            .border(1.dp, if (selected) scheme.onSurface else scheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.surfaceContainerLowest else scheme.onSurface,
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
            Icon(
                if (income) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
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
