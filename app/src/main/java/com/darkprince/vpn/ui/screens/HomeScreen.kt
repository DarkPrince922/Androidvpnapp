package com.darkprince.vpn.ui.screens

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import com.darkprince.vpn.ui.theme.glass
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
    )
}

/**
 * Главный экран «Аквы» без привязки к модели: все данные приходят параметрами,
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
) {
    val scheme = MaterialTheme.colorScheme
    val connected = vpnState == VpnState.CONNECTED
    val sub = remember(state) { summarizeSubscription(state) }
    val server = state.servers.getOrNull(state.selectedServer)
    val serverName = server?.name?.let(::nameWithoutEmoji)
    val locked = vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING

    // Текст переживает саму полосу: пока она уезжает, ей нужно что-то рисовать.
    var lastNotice by remember { mutableStateOf<Notice?>(null) }
    if (notice != null) lastNotice = notice

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // --- шапка: название и подписка ---
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
            SubscriptionPill(
                state = state,
                sub = sub,
                locked = locked,
                onSelectSubscription = onSelectSubscription,
            )
        }

        // --- кнопка подключения ---
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(290.dp),
            contentAlignment = Alignment.Center,
        ) {
            PowerDisc(vpnState = vpnState, onClick = onPowerClick)
        }

        // --- статус одним словом и строка под ним ---
        AnimatedContent(
            targetState = vpnState,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            modifier = Modifier.padding(top = 2.dp),
            label = "status",
        ) { current ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (current) {
                        VpnState.CONNECTED -> "Защищено"
                        VpnState.CONNECTING -> "Подключение…"
                        VpnState.ERROR -> "Не подключено"
                        VpnState.DISCONNECTED -> "Не защищено"
                    },
                    style = MaterialTheme.typography.displaySmall,
                    color = if (current == VpnState.ERROR) scheme.error else scheme.onBackground,
                )
                if (current == VpnState.CONNECTED) {
                    ConnectedSubtitle(serverName, connectedSince)
                } else {
                    Text(
                        when (current) {
                            VpnState.CONNECTING -> serverName ?: ""
                            VpnState.ERROR -> vpnError ?: "Не удалось подключиться"
                            else -> "Нажмите, чтобы подключиться"
                        },
                        fontSize = 14.sp,
                        color = ink(0.55f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }

        // --- нижняя часть: плитки и группа строк ---
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            if (connected) {
                Row(
                    modifier = Modifier.padding(top = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val (down, downUnit) = splitBytes(stats.downlinkBytes)
                    val (up, upUnit) = splitBytes(stats.uplinkBytes)
                    TrafficTile("Получено", AquaIcon.Down, down, downUnit, Modifier.weight(1f))
                    TrafficTile("Отправлено", AquaIcon.Up, up, upUnit, Modifier.weight(1f))
                }
                Column(Modifier.padding(top = 12.dp).fillMaxWidth().glass()) {
                    val ping = server?.let { state.pings[it.key] }?.takeIf { it >= 0 }
                    InfoRow(
                        AquaIcon.Servers,
                        "Сервер",
                        listOfNotNull(serverName?.substringAfter(" · ") ?: "Нет серверов", ping?.let { "$it мс" }).joinToString(" · "),
                    )
                }
            } else {
                Column(Modifier.padding(top = 24.dp).fillMaxWidth().glass()) {
                    InfoRow(AquaIcon.Servers, "Сервер", serverName ?: "Нет серверов")
                    InfoDivider()
                    InfoRow(AquaIcon.Bolt, "Протокол", server?.let(::protocolText) ?: "—")
                    InfoDivider()
                    InfoRow(
                        AquaIcon.Shield,
                        "Подписка",
                        sub.untilShort?.let { "до $it" } ?: "Срок неизвестен",
                        onClick = onOpenPlans,
                    )
                    if (sub.limitGb != null && sub.limitGb > 0) {
                        InfoDivider()
                        InfoRow(
                            AquaIcon.Speed,
                            "Трафик",
                            String.format(Locale.getDefault(), "%.1f / %.0f ГБ", sub.usedGb ?: 0.0, sub.limitGb),
                        )
                    }
                }
            }

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

            // Полоса гаснет сама: держать её до следующего нажатия незачем.
            AnimatedVisibility(
                visible = notice != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                lastNotice?.let { NoticeBar(it) }
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
            Spacer(Modifier.height(24.dp))
        }
    }
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
 * Пилюля в шапке: «Премиум · 644 дн.». Подписок несколько — нажимается и
 * открывает выбор; пока VPN поднят, менять подписку нельзя (туннель идёт по
 * серверу текущей, и смена на ходу его рвала), поэтому нажатие объясняет это.
 */
@Composable
private fun SubscriptionPill(
    state: HomeUiState,
    sub: SubSummary,
    locked: Boolean,
    onSelectSubscription: (Long) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var lockHint by remember { mutableStateOf(false) }
    LaunchedEffect(lockHint) {
        if (lockHint) {
            delay(3000)
            lockHint = false
        }
    }
    val multi = state.subscriptions.size > 1
    val label = when {
        sub.daysLeft == null -> sub.title
        sub.daysLeft <= 0 -> "${sub.title} · истекла"
        else -> "${sub.title} · ${sub.daysLeft} дн."
    }

    Box {
        Text(
            if (lockHint) "Отключите VPN, чтобы сменить" else label,
            fontSize = 12.sp,
            color = if (sub.daysLeft != null && sub.daysLeft <= 0) MaterialTheme.colorScheme.error
                else ink(0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 220.dp)
                .glass(RoundedCornerShape(50))
                .then(
                    if (multi) Modifier.clickable { if (locked) lockHint = true else menuOpen = true }
                    else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        DropdownMenu(expanded = menuOpen && !locked, onDismissRequest = { menuOpen = false }) {
            state.subscriptions.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(item.displayName)
                            val details = listOfNotNull(
                                item.endDate?.take(10),
                                if (item.isActive) null else "неактивна",
                            ).joinToString(" · ")
                            if (details.isNotBlank()) {
                                Text(details, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    onClick = {
                        menuOpen = false
                        onSelectSubscription(item.id)
                    },
                )
            }
        }
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
private fun PowerDisc(vpnState: VpnState, onClick: () -> Unit) {
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
        when {
            on -> accent.copy(alpha = if (light) 0.55f else 0.45f)
            else -> ink(if (light) 0.14f else 0.16f)
        }, spec, label = "border",
    )
    val top by animateColorAsState(
        when {
            on -> accent.copy(alpha = if (light) 0.26f else 0.28f)
            light -> Color.White.copy(alpha = 0.95f)
            else -> ink(0.12f)
        }, spec, label = "top",
    )
    val bottom by animateColorAsState(
        when {
            on -> if (light) accent.copy(alpha = 0.08f) else Color(0xFF0096C8).copy(alpha = 0.08f)
            light -> Color.White.copy(alpha = 0.55f)
            else -> ink(0.03f)
        }, spec, label = "bottom",
    )
    val iconColor by animateColorAsState(
        when {
            on -> if (light) accent else Color(0xFF8FF5E8)
            else -> ink(if (light) 0.62f else 0.78f)
        }, spec, label = "icon",
    )
    val rim = if (light) Color.White else ink(0.25f)

    Box(contentAlignment = Alignment.Center) {
        // кольца ничего не зажимают: они крупнее самой полосы
        Box(Modifier.requiredSize(300.dp).border(1.dp, ringColor, CircleShape))
        Box(Modifier.requiredSize(244.dp).border(1.dp, ringColor, CircleShape))

        val shape = CircleShape
        Box(
            modifier = Modifier
                .size(176.dp)
                .clip(shape)
                // 160° в CSS — от верхнего левого угла к нижнему правому, почти по диагонали
                .background(Brush.linearGradient(listOf(top, bottom), start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)))
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
                    AquaIconView(AquaIcon.ShieldCheck, iconColor, size = 64.dp, stroke = 1.2f)
                } else {
                    AquaIconView(AquaIcon.Power, iconColor, size = 60.dp, stroke = 1.2f)
                }
            }
        }

        if (busy) ConnectingArc()
    }
}

/** Бегущая дуга вокруг диска, пока идёт подключение. */
@Composable
private fun ConnectingArc() {
    val accent = MaterialTheme.colorScheme.primary
    val turn by rememberInfiniteTransition(label = "connecting").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "turn",
    )
    androidx.compose.foundation.Canvas(Modifier.requiredSize(190.dp)) {
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
private fun ConnectedSubtitle(serverName: String?, since: Long?) {
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
        fontSize = 14.sp,
        color = ink(0.55f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

/** Секунды как «00:42:17». */
private fun formatDuration(seconds: Long): String =
    String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)

@Composable
private fun TrafficTile(
    label: String,
    icon: AquaIcon,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val dim = ink(0.55f)
    Column(modifier.glass().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AquaIconView(icon, dim, size = 14.dp, stroke = 1.6f)
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, color = dim, maxLines = 1)
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-1).sp,
                maxLines = 1,
            )
            Text(
                unit,
                fontSize = 13.sp,
                color = dim,
                modifier = Modifier.padding(start = 3.dp, bottom = 5.dp),
                maxLines = 1,
            )
        }
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
