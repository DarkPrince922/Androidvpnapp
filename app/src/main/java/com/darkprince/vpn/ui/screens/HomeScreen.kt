package com.darkprince.vpn.ui.screens

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.core.model.ProxyProfile
import com.darkprince.vpn.ui.theme.AquaIcon
import com.darkprince.vpn.ui.theme.AquaIconView
import com.darkprince.vpn.ui.theme.BrandColors
import com.darkprince.vpn.ui.theme.GroupCard
import com.darkprince.vpn.ui.theme.ServiceDownBanner
import com.darkprince.vpn.ui.theme.LocalBottomInset
import com.darkprince.vpn.ui.theme.glass
import com.darkprince.vpn.ui.theme.leadingEmoji
import com.darkprince.vpn.ui.theme.ink
import com.darkprince.vpn.ui.theme.nameWithoutEmoji
import com.darkprince.vpn.ui.vm.HomeUiState
import com.darkprince.vpn.ui.vm.HomeViewModel
import com.darkprince.vpn.ui.vm.Notice
import com.darkprince.vpn.vpn.TrafficStats
import com.darkprince.vpn.vpn.VpnState
import com.darkprince.vpn.vpn.VpnStateStore
import kotlinx.coroutines.delay
import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes Б"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f КБ", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f МБ", mb)
    return String.format(Locale.getDefault(), "%.2f ГБ", mb / 1024.0)
}

/** Объём на две части — число и единица, как на плитке: «1,24» и «ГБ». */
private fun splitBytes(bytes: Long): Pair<String, String> {
    val text = formatBytes(bytes)
    val i = text.lastIndexOf(' ')
    return text.substring(0, i) to text.substring(i + 1)
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onReminderButton: (com.darkprince.vpn.data.api.dto.ReminderButtonDto) -> Unit = {},
    /** Открыть «Тарифы»; null — раздела нет (гостевой доступ), ссылку не показываем. */
    onOpenPlans: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val stats by viewModel.vpnStats.collectAsStateWithLifecycle()
    val vpnError by viewModel.vpnError.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val updateBusy by viewModel.updateBusy.collectAsStateWithLifecycle()
    val updateError by viewModel.updateError.collectAsStateWithLifecycle()
    val updateProgress by viewModel.updateProgress.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val switchedFrom by viewModel.switchedFrom.collectAsStateWithLifecycle()
    val connectedSince by VpnStateStore.connectedSince.collectAsStateWithLifecycle()

    LaunchedEffect(notice) {
        val shown = notice ?: return@LaunchedEffect
        // ошибку читать дольше, чем «обновлено»
        delay(if (shown.ok) 2500 else 4500)
        viewModel.consumeNotice()
    }

    // Приложения нет в Google Play, и кроме нас сказать о новой версии
    // некому — поэтому спрашиваем окном, а не строкой где-то внизу.
    update?.let {
        UpdateDialog(
            versionName = it.versionName,
            notes = it.notes,
            busy = updateBusy,
            error = updateError,
            progress = updateProgress,
            onInstall = viewModel::installUpdate,
            onDismiss = viewModel::hideUpdate,
        )
    }

    HomeContent(
        state = state,
        vpnState = vpnState,
        stats = stats,
        vpnError = vpnError,
        notice = notice,
        switchedFrom = switchedFrom,
        connectedSince = connectedSince,
        onPowerClick = { if (vpnState == VpnState.CONNECTED) onDisconnectClick() else onConnectClick() },
        onReminderButton = onReminderButton,
        onDismissReminder = viewModel::dismissReminder,
        onSelectSubscription = viewModel::selectSubscription,
        onOpenPlans = onOpenPlans,
        onSelectServer = viewModel::selectServer,
        onPing = viewModel::pingAll,
        onRefresh = { viewModel.refresh(forceServers = true, notify = true) },
    )
}

/**
 * Главный экран «Аквы» — лента. Вверху компактная кнопка подключения со
 * статусом, ниже подписка и полный список серверов: отдельной вкладки
 * «Серверы» нет. Без привязки к модели: все данные приходят параметрами,
 * поэтому его же рисует отладочный просмотр с поддельными данными.
 */
@Composable
fun HomeContent(
    state: HomeUiState,
    vpnState: VpnState,
    stats: TrafficStats,
    vpnError: String?,
    notice: Notice?,
    switchedFrom: String?,
    connectedSince: Long?,
    onPowerClick: () -> Unit,
    onReminderButton: (com.darkprince.vpn.data.api.dto.ReminderButtonDto) -> Unit = {},
    onDismissReminder: (Long) -> Unit = {},
    onSelectSubscription: (Long) -> Unit = {},
    onOpenPlans: (() -> Unit)? = null,
    onSelectServer: (Int) -> Unit = {},
    onPing: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val connected = vpnState == VpnState.CONNECTED
    val sub = remember(state) { summarizeSubscription(state) }
    val server = state.servers.getOrNull(state.selectedServer)
    val serverName = server?.name?.let(::nameWithoutEmoji)
    val locked = vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING
    // «🇩🇪 Германия · Франкфурт»: флаг рядом с названием, как у крупных клиентов
    val serverLabel = serverName?.let { name ->
        listOfNotNull(server?.name?.let(::leadingEmoji), name).joinToString(" ")
    }

    val scroll = rememberScrollState()
    // фон узнаёт о прокрутке и двигает пятна; уходим с экрана — сбрасываем
    LaunchedEffect(scroll) {
        androidx.compose.runtime.snapshotFlow { scroll.value }.collect {
            com.darkprince.vpn.ui.theme.BackgroundScroll.offset.floatValue = it.toFloat()
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { com.darkprince.vpn.ui.theme.BackgroundScroll.offset.floatValue = 0f }
    }
    val backdrop = com.darkprince.vpn.ui.theme.LocalPalette.current.background

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
    ) {
        // --- шапка: название и, пока подключены, сервер со временем ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "DARKPRINCE",
                fontSize = 15.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.8.sp,
                color = ink(0.8f),
            )
            AnimatedVisibility(visible = connected, enter = fadeIn(), exit = fadeOut()) {
                // в шапке место узкое: флаг, город и время; страну опускаем
                val short = serverName?.substringAfter(" · ")?.let { city ->
                    listOfNotNull(server?.name?.let(::leadingEmoji), city).joinToString(" ")
                }
                ConnectedSubtitle(short ?: serverLabel, connectedSince, Modifier.padding(start = 12.dp), fontSize = 13.sp)
            }
        }

        // --- кнопка слева, статус справа ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PowerDisc(vpnState = vpnState, onClick = onPowerClick, discSize = 88.dp, rings = false)
            Spacer(Modifier.width(18.dp))
            AnimatedContent(
                targetState = vpnState,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "status",
            ) { current ->
                Column {
                    Text(
                        when (current) {
                            VpnState.CONNECTED -> "Защищено"
                            VpnState.CONNECTING -> "Подключение…"
                            VpnState.ERROR -> "Не подключено"
                            VpnState.DISCONNECTED -> "Не защищено"
                        },
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.8).sp,
                        ),
                        color = if (current == VpnState.ERROR) scheme.error else scheme.onBackground,
                        maxLines = 1,
                    )
                    Text(
                        when (current) {
                            VpnState.CONNECTED ->
                                "↓ ${formatBytes(stats.downlinkBytes)} · ↑ ${formatBytes(stats.uplinkBytes)}"
                            VpnState.CONNECTING -> serverLabel ?: ""
                            VpnState.ERROR -> vpnError ?: "Не удалось подключиться"
                            else -> "Нажмите, чтобы подключиться"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (current == VpnState.CONNECTED) scheme.primary else ink(0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            // --- подписка ---
            SubscriptionCard(
                state = state,
                sub = sub,
                locked = locked,
                onSelectSubscription = onSelectSubscription,
                onOpenPlans = onOpenPlans,
                modifier = Modifier.padding(top = 18.dp),
            )

            // --- то, о чём нужно сказать сейчас ---
            ServiceDownBanner(
                "Обновить подписку, оплатить и продлить можно будет, когда сервис вернётся.",
                Modifier.padding(top = 12.dp),
            )

            // напоминания из кабинета: то, что админ просит сделать сейчас
            state.reminders.forEach { card ->
                key(card.id) {
                    ReminderCard(
                        card = card,
                        onButton = onReminderButton,
                        onDismiss = { onDismissReminder(card.id) },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            // Подмена узла держится, пока человек не переподключится сам: от неё
            // зависят и скорость, и то, какие сайты откроются.
            switchedFrom?.let { asked ->
                Spacer(Modifier.height(8.dp))
                NoticeBar(Notice("«$asked» не ответил — подключились к другому серверу", ok = false))
            }

            state.error?.let {
                Text(
                    it,
                    color = scheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }

            sub.current?.graceUntil?.let { until ->
                SubscriptionWarning(
                    "Временный доступ до ${cabinetDate(until)} — продлите подписку, чтобы VPN не отключился",
                    BrandColors.Warning,
                )
            }
            if (sub.current?.isDailyPaused == true) {
                SubscriptionWarning(
                    "Суточный тариф на паузе — VPN не работает. Возобновить можно в «Тарифах».",
                    BrandColors.Warning,
                )
            }
            if (sub.current?.requiresTariffSelection == true) {
                SubscriptionWarning(
                    "Подписка оформлена до появления тарифов. Чтобы продлить её, выберите тариф в «Тарифах».",
                    scheme.onSurfaceVariant,
                )
            }

            // --- серверы: прямо на главной ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (state.servers.isEmpty()) "СЕРВЕРЫ" else "СЕРВЕРЫ · ${state.servers.size}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.6.sp,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                GlassChip(onClick = onPing) {
                    if (state.pinging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.6.dp,
                            color = scheme.primary,
                        )
                    } else {
                        AquaIconView(AquaIcon.Speed, ink(0.8f), size = 15.dp, stroke = 1.6f)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Пинг", fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                GlassChip(onClick = onRefresh) {
                    AquaIconView(AquaIcon.Refresh, ink(0.8f), size = 15.dp, stroke = 1.6f)
                }
            }
            if (state.servers.isEmpty()) {
                Text(
                    "Список серверов пуст. Проверьте подписку.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            } else {
                Column(Modifier.fillMaxWidth().glass()) {
                    state.servers.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = ink(0.07f), thickness = 1.dp)
                        ServerRow(
                            server = item,
                            selected = index == state.selectedServer,
                            ping = state.pings[item.key],
                            pinging = state.pinging,
                            onClick = { onSelectServer(index) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp + LocalBottomInset.current))
        }
    }
    // Мягкое затухание под статус-баром: пока прокрутили, строки не обрезаются
    // жёсткой линией, а растворяются.
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(28.dp)
            .graphicsLayer { alpha = (scroll.value / 120f).coerceIn(0f, 1f) }
            .background(Brush.verticalGradient(listOf(backdrop.copy(alpha = 0.92f), Color.Transparent)))
    )
    // итог обновления всплывает сверху и уходит сам
    NoticeToast(notice, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
    }
}

/** Небольшая стеклянная кнопка-чип: «Пинг», «Обновить». */
@Composable
private fun GlassChip(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .glass(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun SubscriptionWarning(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp),
    )
}

/** «VLESS · Reality»: первое слово — протокол заглавными, остальное с большой буквы. */
private fun protocolText(server: ProxyProfile): String =
    server.transportParts.take(2).mapIndexed { i, part ->
        if (i == 0) part.uppercase() else part.replaceFirstChar { it.uppercase() }
    }.joinToString(" · ").ifBlank { "—" }

// ------------------------------------------------------------------
// Подписка
// ------------------------------------------------------------------

private data class SubSummary(
    val current: com.darkprince.vpn.data.api.dto.SubscriptionListItem?,
    val title: String,
    val daysLeft: Int?,
    val untilShort: String?,
    val usedGb: Double?,
    val limitGb: Double?,
)

private fun summarizeSubscription(state: HomeUiState): SubSummary {
    val current = state.selectedSubscription
    val userInfo = state.subUserInfo

    // Общий статус кабинета — только когда конкретной подписки нет вовсе
    // (гость, список не пришёл). Иначе при нескольких подписках на карточке
    // смешивались бы две: срок от одной, остаток трафика от другой.
    val account = state.subscription.takeIf { current == null }

    val title = current?.displayName ?: account?.tariffName ?: "Подписка"

    val daysLeft = current?.endDate?.let(::daysUntil)
        ?: account?.daysLeft
        ?: userInfo?.expireUnix?.takeIf { it > 0 }?.let {
            ((it * 1000 - System.currentTimeMillis()) / 86_400_000L).toInt()
        }

    val usedGb = current?.trafficUsedGb
        ?: account?.trafficUsedGb
        ?: userInfo?.let { info ->
            val total = (info.uploadBytes ?: 0) + (info.downloadBytes ?: 0)
            if (total > 0 || info.totalBytes != null) total / 1_073_741_824.0 else null
        }
    val limitGb = current?.trafficLimitGb
        ?: account?.trafficLimitGb
        ?: userInfo?.totalBytes?.takeIf { it > 0 }?.let { it / 1_073_741_824.0 }

    return SubSummary(
        current = current,
        title = title,
        daysLeft = daysLeft,
        untilShort = untilShort(current?.endDate, userInfo?.expireUnix),
        usedGb = usedGb,
        limitGb = limitGb,
    )
}

/** Дата окончания как «22.08.2028»; null — даты нет. */
private fun untilShort(raw: String?, expireUnix: Long?): String? {
    val instant = runCatching {
        when {
            raw != null -> runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }
                .getOrElse { java.time.LocalDateTime.parse(raw).toInstant(java.time.ZoneOffset.UTC) }
            expireUnix != null && expireUnix > 0 -> java.time.Instant.ofEpochSecond(expireUnix)
            else -> null
        }
    }.getOrNull() ?: return null
    return java.time.format.DateTimeFormatter
        .ofPattern("dd.MM.yyyy", Locale.forLanguageTag("ru"))
        .format(instant.atZone(java.time.ZoneId.systemDefault()))
}

/**
 * Карточка подписки: тариф, срок и остаток дней крупно. Подписок несколько —
 * нажимается и открывает выбор; пока VPN поднят, менять подписку нельзя
 * (туннель идёт по серверу текущей, и смена на ходу его рвала), поэтому
 * нажатие объясняет это. Одна подписка — нажатие ведёт в «Тарифы».
 */
@Composable
private fun SubscriptionCard(
    state: HomeUiState,
    sub: SubSummary,
    locked: Boolean,
    onSelectSubscription: (Long) -> Unit,
    onOpenPlans: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    var lockHint by remember { mutableStateOf(false) }
    LaunchedEffect(lockHint) {
        if (lockHint) {
            delay(3000)
            lockHint = false
        }
    }
    val multi = state.subscriptions.size > 1
    val expired = sub.daysLeft != null && sub.daysLeft <= 0
    val limited = sub.limitGb != null && sub.limitGb > 0

    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .glass()
                .animateContentSize()
                .clickable {
                    when {
                        multi -> if (locked) lockHint = true else menuOpen = !menuOpen
                        else -> onOpenPlans?.invoke()
                    }
                }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(ink(0.08f)),
                    contentAlignment = Alignment.Center,
                ) { AquaIconView(AquaIcon.Shield, ink(0.8f), size = 18.dp, stroke = 1.5f) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        sub.title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            lockHint -> "Отключите VPN, чтобы сменить"
                            sub.untilShort != null -> "до ${sub.untilShort}"
                            else -> "Срок неизвестен"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (lockHint) BrandColors.Warning else scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(10.dp))
                // остаток — компактной меткой: зелёная, на исходе (до недели) жёлтая,
                // истекла — красная
                if (sub.daysLeft != null) {
                    val tint = when {
                        expired -> scheme.error
                        sub.daysLeft <= 7 -> BrandColors.Warning
                        else -> scheme.primary
                    }
                    Text(
                        if (expired) "Истекла" else "${sub.daysLeft} ${daysWord(sub.daysLeft)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tint,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tint.copy(alpha = 0.14f))
                            .border(1.dp, tint.copy(alpha = 0.32f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                if (multi) {
                    Spacer(Modifier.width(8.dp))
                    AquaIconView(
                        AquaIcon.Chevron,
                        scheme.onSurfaceVariant.copy(alpha = 0.7f),
                        size = 18.dp,
                        stroke = 1.6f,
                        modifier = Modifier.rotate(if (menuOpen && !locked) -90f else 90f),
                    )
                }
            }
            // другие подписки раскрываются здесь же, под текущей
            if (multi && menuOpen && !locked) {
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.HorizontalDivider(color = ink(0.08f))
                state.subscriptions.forEach { item ->
                    SubscriptionOption(
                        item = item,
                        current = item.id == state.selectedSubscription?.id,
                        onClick = {
                            menuOpen = false
                            onSelectSubscription(item.id)
                        },
                    )
                }
            }
            if (limited) {
                Spacer(Modifier.height(12.dp))
                Row {
                    Text("Трафик", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(
                        String.format(Locale.getDefault(), "%.1f / %.0f ГБ", sub.usedGb ?: 0.0, sub.limitGb),
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { ((sub.usedGb ?: 0.0) / (sub.limitGb ?: 1.0)).coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = scheme.primary,
                    trackColor = ink(0.10f),
                )
            }
        }
    }
}

/** Строка в раскрытой карточке: название, срок и остаток цветом; галочка у текущей. */
@Composable
private fun SubscriptionOption(
    item: com.darkprince.vpn.data.api.dto.SubscriptionListItem,
    current: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val days = item.endDate?.let(::daysUntil)
    val (status, tint) = when {
        !item.isActive -> "Неактивна" to scheme.error
        days == null -> "" to scheme.onSurfaceVariant
        days <= 0 -> "Истекла" to scheme.error
        days <= 7 -> "$days ${daysWord(days)}" to BrandColors.Warning
        else -> "$days ${daysWord(days)}" to scheme.primary
    }
    val until = untilShort(item.endDate, null)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (current) scheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (item.isActive) scheme.onBackground else ink(0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (until != null) {
                Text(
                    if (days != null && days <= 0) "закончилась $until" else "до $until",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (status.isNotEmpty()) {
            Text(status, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
        }
        Box(Modifier.width(26.dp), contentAlignment = Alignment.CenterEnd) {
            if (current) AquaIconView(AquaIcon.Check, scheme.primary, size = 17.dp, stroke = 2.2f)
        }
    }
}

/** «день», «дня», «дней» — подпись под числом. */
private fun daysWord(days: Int): String {
    val last2 = days % 100
    val last = days % 10
    return when {
        last2 in 11..14 -> "дней"
        last == 1 -> "день"
        last in 2..4 -> "дня"
        else -> "дней"
    }
}

// ------------------------------------------------------------------
// Кнопка подключения
// ------------------------------------------------------------------

/**
 * Стеклянный диск 176 dp в двух кольцах (244 и 300 dp). Выключено — диск
 * серый и тусклый, подключено — аквамариновый, на нём щит с галочкой.
 * Идёт подключение — вокруг диска бежит дуга. Цвет проявляется плавно.
 */
@Composable
private fun PowerDisc(
    vpnState: VpnState,
    onClick: () -> Unit,
    discSize: androidx.compose.ui.unit.Dp = 180.dp,
    rings: Boolean = true,
) {
    val k = discSize.value / 180f
    val on = vpnState == VpnState.CONNECTED
    val busy = vpnState == VpnState.CONNECTING
    val spec = tween<Color>(500)

    val light = com.darkprince.vpn.ui.theme.LocalPalette.current.isLight
    val accent = MaterialTheme.colorScheme.primary
    // На светлых темах «стекло» диска — белое, а подключённый — акцентный:
    // белые тона светлого диска на светлом фоне не видны вовсе.
    val ringColor by animateColorAsState(
        if (on) accent.copy(alpha = 0.20f) else ink(if (light) 0.10f else 0.08f), spec, label = "ring",
    )
    val borderColor by animateColorAsState(
        if (on) Color.White.copy(alpha = 0.55f) else ink(if (light) 0.14f else 0.18f), spec, label = "border",
    )
    // Выключено — стекло, включено — залитая аквамариновая «кнопка»:
    // состояние читается с вытянутой руки.
    val top by animateColorAsState(
        when {
            on -> Color(0xFF7BF7E8)
            light -> Color.White.copy(alpha = 0.95f)
            else -> ink(0.14f)
        }, spec, label = "top",
    )
    val mid by animateColorAsState(
        when {
            on -> Color(0xFF00BFB0)
            light -> Color.White.copy(alpha = 0.7f)
            else -> ink(0.07f)
        }, spec, label = "mid",
    )
    val bottom by animateColorAsState(
        when {
            on -> Color(0xFF0092A8)
            light -> Color.White.copy(alpha = 0.55f)
            else -> ink(0.04f)
        }, spec, label = "bottom",
    )
    val iconColor by animateColorAsState(
        when {
            on -> Color(0xFF04302D)
            else -> ink(if (light) 0.62f else 0.78f)
        }, spec, label = "icon",
    )
    val glow by animateColorAsState(
        if (on) Color(0xFF00E6D2).copy(alpha = if (light) 0.35f else 0.55f) else Color.Transparent,
        spec, label = "glow",
    )
    val rim = if (on) Color.White.copy(alpha = 0.55f) else if (light) Color.White else ink(0.22f)

    Box(contentAlignment = Alignment.Center) {
        // кольца ничего не зажимают: они крупнее самой полосы
        if (rings) {
            Box(Modifier.requiredSize(300.dp).border(1.dp, ringColor, CircleShape))
            Box(Modifier.requiredSize(244.dp).border(1.dp, ringColor, CircleShape))
        }

        // свечение под включённой кнопкой
        Box(
            Modifier
                .requiredSize(discSize * 1.39f)
                .background(Brush.radialGradient(listOf(glow, Color.Transparent)), CircleShape)
        )

        val shape = CircleShape
        Box(
            modifier = Modifier
                .size(discSize)
                .clip(shape)
                // 160° в CSS — от верхнего левого угла к нижнему правому, почти по диагонали
                .background(Brush.linearGradient(0f to top, 0.7f to mid, 1f to bottom, start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)))
                .border(1.dp, borderColor, shape)
                .drawBehind {
                    // блик по верхней кромке, как inset 0 1px rgba(255,255,255,.25)
                    drawArc(
                        color = rim,
                        startAngle = 205f,
                        sweepAngle = 130f,
                        useCenter = false,
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                        style = Stroke(1.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (on) "Отключить" else "Подключить",
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = on,
                transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) },
                label = "discIcon",
            ) { connected ->
                if (connected) {
                    AquaIconView(AquaIcon.ShieldCheck, iconColor, size = (72 * k).dp, stroke = 1.5f)
                } else {
                    AquaIconView(AquaIcon.Power, iconColor, size = (66 * k).dp, stroke = 1.4f)
                }
            }
        }

        if (busy) ConnectingArc(discSize + 12.dp)
    }
}

/** Бегущая дуга вокруг диска, пока идёт подключение. */
@Composable
private fun ConnectingArc(diameter: androidx.compose.ui.unit.Dp) {
    val accent = MaterialTheme.colorScheme.primary
    val turn by rememberInfiniteTransition(label = "connecting").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "turn",
    )
    androidx.compose.foundation.Canvas(Modifier.requiredSize(diameter)) {
        drawArc(
            color = accent,
            startAngle = turn,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

// ------------------------------------------------------------------
// Время, плитки, строки
// ------------------------------------------------------------------

/**
 * «Германия · 00:42:17». Время тикает здесь, а не в модели экрана: раз в
 * секунду меняется одна строка, и перерисовывать ради неё весь экран незачем.
 */
@Composable
private fun ConnectedSubtitle(
    serverName: String?,
    since: Long?,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
) {
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(since) {
        while (since != null) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val elapsed = since?.let { started -> ((now - started) / 1000).coerceAtLeast(0) }
    val label = listOfNotNull(
        serverName?.takeIf { it.isNotBlank() },
        elapsed?.let(::formatDuration),
    ).joinToString(" · ")
    Text(
        label,
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Секунды как «00:42:17». */
private fun formatDuration(seconds: Long): String =
    String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)

@Composable
private fun TrafficRow(icon: AquaIcon, label: String, value: String, unit: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ink(0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            AquaIconView(icon, ink(0.8f), size = 18.dp, stroke = 1.5f)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 16.sp, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Light, maxLines = 1)
        Text(
            unit,
            fontSize = 12.sp,
            color = ink(0.5f),
            modifier = Modifier.padding(start = 3.dp, top = 3.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun InfoRow(
    icon: AquaIcon,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ink(0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            AquaIconView(icon, ink(0.8f), size = 18.dp, stroke = 1.5f)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 16.sp, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        AnimatedContent(
            targetState = value,
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "infoValue",
        ) { text ->
            Text(
                text,
                fontSize = 14.5.sp,
                color = ink(0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

@Composable
private fun InfoDivider() {
    HorizontalDivider(color = ink(0.07f), thickness = 1.dp)
}

@Composable
private fun UpdateDialog(
    versionName: String,
    notes: String?,
    busy: Boolean,
    error: String?,
    progress: Pair<Long, Long>,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val (downloaded, total) = progress

    AlertDialog(
        // во время закачки закрывать нечему: файл всё равно докачается
        onDismissRequest = { if (!busy) onDismiss() },
        icon = {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = accent)
            }
        },
        title = { Text("Вышла версия $versionName") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = error
                        ?: notes
                        ?: "Приложение скачает обновление само и передаст его установщику.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    // размер известен не всегда — тогда крутим бесконечную
                    if (total > 0) {
                        LinearProgressIndicator(
                            progress = { downloaded.toFloat() / total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${formatBytes(downloaded)} из ${formatBytes(total)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onInstall, enabled = !busy) {
                Text(if (busy) "Качаю…" else if (error != null) "Повторить" else "Обновить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Не сейчас") }
        },
    )
}

/** «день остался», «дня осталось», «дней осталось». */
private fun daysLeftWord(days: Int): String {
    val last2 = days % 100
    val last = days % 10
    return when {
        last2 in 11..14 -> "дней осталось"
        last == 1 -> "день остался"
        last in 2..4 -> "дня осталось"
        else -> "дней осталось"
    }
}

/** Дата окончания словами: «20 августа 2028». null — даты нет. */
private fun untilLabel(raw: String?, expireUnix: Long?): String? {
    val instant = runCatching {
        when {
            raw != null -> runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }
                .getOrElse { java.time.LocalDateTime.parse(raw).toInstant(java.time.ZoneOffset.UTC) }
            expireUnix != null && expireUnix > 0 -> java.time.Instant.ofEpochSecond(expireUnix)
            else -> null
        }
    }.getOrNull() ?: return null
    return java.time.format.DateTimeFormatter
        .ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))
        .format(instant.atZone(java.time.ZoneId.systemDefault()))
}

/**
 * Всплывающее уведомление: компактная таблетка по центру сверху. Появляется
 * поверх экрана и уходит само — вызывающий убирает [notice] через пару
 * секунд. Текст переживает саму таблетку: пока она уезжает, ей нужно что-то
 * рисовать.
 */
@Composable
internal fun NoticeToast(notice: Notice?, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf<Notice?>(null) }
    if (notice != null) last = notice
    AnimatedVisibility(
        visible = notice != null,
        modifier = modifier,
        enter = slideInVertically(tween(260)) { -it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(160)),
    ) {
        val shown = last ?: return@AnimatedVisibility
        val palette = com.darkprince.vpn.ui.theme.LocalPalette.current
        val tint = if (shown.ok) MaterialTheme.colorScheme.primary else Color(0xFFFF8A80)
        val base = if (palette.isLight) Color.White.copy(alpha = 0.96f) else palette.panel.copy(alpha = 0.92f)
        val shape = RoundedCornerShape(50)
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 340.dp)
                .drawBehind {
                    // мягкое свечение вокруг таблетки
                    val grow = 6.dp.toPx()
                    drawRoundRect(
                        color = tint.copy(alpha = 0.06f),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + grow * 2, size.height + grow * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2 + grow),
                    )
                }
                .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
                .clip(shape)
                .background(base)
                .border(1.dp, tint.copy(alpha = 0.32f), shape)
                .padding(start = 12.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                AquaIconView(
                    if (shown.ok) AquaIcon.Check else AquaIcon.Alert,
                    tint,
                    size = 15.dp,
                    stroke = 2.4f,
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                shown.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Итог обновления подписки одной строкой.
 *
 * Обновление чаще всего ничего не меняет на экране, и без такой строки
 * нажатие на кнопку выглядит как «ничего не произошло» — что при ошибке
 * сети неотличимо от успеха.
 */
@Composable
internal fun NoticeBar(notice: Notice) {
    val tint = if (notice.ok) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.error

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (notice.ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            notice.text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
        )
    }
}

/**
 * Сколько дней осталось до даты окончания подписки. Кабинет отдаёт дату
 * строкой ISO — берём из неё только календарный день, время и часовой пояс
 * для «осталось дней» роли не играют.
 */
private fun daysUntil(endDate: String): Int? = try {
    val date = java.time.LocalDate.parse(endDate.take(10))
    // без обрезки по нулю: у истёкшей подписки число уходит в минус, и по
    // нему её видно. Раньше и вчерашняя, и годовой давности показывались
    // одинаково — «осталось 0 дней»
    java.time.temporal.ChronoUnit.DAYS
        .between(java.time.LocalDate.now(), date)
        .toInt()
} catch (_: Exception) {
    null
}

/** Карточка напоминания: заголовок, текст, кнопка из кабинета и «закрыть». */
@Composable
private fun ReminderCard(
    card: com.darkprince.vpn.data.api.dto.ReminderCardDto,
    onButton: (com.darkprince.vpn.data.api.dto.ReminderButtonDto) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupCard(modifier) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(card.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (card.body.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        card.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            androidx.compose.material3.IconButton(onClick = onDismiss) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        val button = card.button?.takeIf { it.text.isNotBlank() && it.target.isNotBlank() }
        if (button != null) {
            androidx.compose.material3.TextButton(
                onClick = { onButton(button) },
                modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
            ) { Text(button.text) }
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}
