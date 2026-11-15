package com.darkprince.vpn.ui.screens

import com.darkprince.vpn.ui.theme.ServiceDownBanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.ui.theme.Appear
import com.darkprince.vpn.ui.theme.DisplayFont
import com.darkprince.vpn.ui.theme.bleedHorizontally
import com.darkprince.vpn.ui.theme.LocalPalette
import com.darkprince.vpn.ui.theme.CircleActionButton
import com.darkprince.vpn.ui.theme.ConnectionMap
import com.darkprince.vpn.ui.theme.EmojiTile
import com.darkprince.vpn.ui.theme.BrandColors
import com.darkprince.vpn.ui.theme.GroupCard
import com.darkprince.vpn.ui.theme.QuietMapBackdrop
import com.darkprince.vpn.ui.theme.SectionHeader
import com.darkprince.vpn.ui.theme.TransportBadge
import com.darkprince.vpn.ui.theme.leadingEmoji
import com.darkprince.vpn.ui.theme.nameWithoutEmoji
import com.darkprince.vpn.ui.vm.HomeViewModel
import com.darkprince.vpn.ui.vm.Notice
import com.darkprince.vpn.vpn.VpnState
import android.os.SystemClock
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

    // Текст переживает саму полосу: пока она уезжает, ей нужно что-то рисовать.
    var lastNotice by remember { mutableStateOf<Notice?>(null) }
    LaunchedEffect(notice) {
        val shown = notice ?: return@LaunchedEffect
        lastNotice = shown
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

    Box(Modifier.fillMaxSize()) {
      // Тихий фон у нижнего края: без него низ экрана оставался плоской
      // чёрной плитой, и список серверов висел в пустоте. Он лежит под
      // списком и не прокручивается — это фон комнаты, а не часть списка.
      QuietMapBackdrop(
          modifier = Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .height(240.dp),
      )

    // LazyColumn, а не прокручиваемая Column: список серверов теперь живёт
    // прямо здесь, и при полусотне узлов рисовать их все разом незачем.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(4.dp))

        // Карта за кнопкой: пока туннель опущен, огни городов почти погашены,
        // при подключении она разгорается и от региона устройства к стране
        // узла тянется дуга. Кнопка смещена ниже центра полосы — дуга уходит
        // в верхнюю треть, и они не перекрывают друг друга.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(306.dp),
            contentAlignment = Alignment.Center,
        ) {
            ConnectionMap(
                connected = vpnState == VpnState.CONNECTED,
                serverName = state.servers.getOrNull(state.selectedServer)?.name,
                // на всю ширину экрана: с полями списка у карты появлялись
                // боковые обрезы, и она читалась как вставленная картинка
                modifier = Modifier
                    .fillMaxSize()
                    .bleedHorizontally(16.dp),
            )
            PowerButton(
                vpnState = vpnState,
                onClick = { if (vpnState == VpnState.CONNECTED) onDisconnectClick() else onConnectClick() },
                modifier = Modifier.offset(y = 30.dp),
            )
        }

        Spacer(Modifier.height(2.dp))

        // Крупный статус одним словом и строка пояснения под ним: «защищено
        // или нет» человек должен считать за полсекунды, остальное — потом.
        val serverName = state.servers.getOrNull(state.selectedServer)?.name
            ?.let { nameWithoutEmoji(it) }
        AnimatedContent(
            targetState = vpnState,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
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
                    style = MaterialTheme.typography.headlineMedium,
                    color = when (current) {
                        // «Защищено» — цветом акцента, чуть высветленным
                        VpnState.CONNECTED -> lerp(MaterialTheme.colorScheme.primary, Color.White, 0.18f)
                        VpnState.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(6.dp))
                if (current == VpnState.CONNECTED) {
                    ConnectedPill(serverName)
                } else {
                    Text(
                        when (current) {
                            VpnState.CONNECTING -> serverName ?: ""
                            VpnState.ERROR -> vpnError ?: "Не удалось подключиться"
                            else -> "Нажмите, чтобы подключиться"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = vpnState == VpnState.CONNECTED,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            TrafficCapsule(
                received = formatBytes(stats.downlinkBytes),
                sent = formatBytes(stats.uplinkBytes),
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        Spacer(Modifier.height(18.dp))
        }
      }

      item {
        ServiceDownBanner(
            "Обновить подписку, оплатить и продлить можно будет, когда сервис вернётся.",
            Modifier.padding(bottom = 12.dp),
        )
      }

      // напоминания из кабинета — над подпиской: это то, что админ просит
      // сделать сейчас, и ниже их никто не увидит
      items(state.reminders, key = { "reminder-${it.id}" }) { card ->
        ReminderCard(
            card = card,
            onButton = onReminderButton,
            onDismiss = { viewModel.dismissReminder(card.id) },
            modifier = Modifier.padding(bottom = 12.dp),
        )
      }

      item {
        Column(Modifier.fillMaxWidth()) {
            HomeSectionHeader("Подписка", action = "Продлить ›", onAction = onOpenPlans)
            Appear {
                SubscriptionCard(
                    state = state,
                    onRefresh = { viewModel.refresh(forceServers = true, notify = true) },
                    onPing = { viewModel.pingAll() },
                    onSelectSubscription = { viewModel.selectSubscription(it) },
                    // пока VPN поднят, подписку не меняем: туннель идёт по
                    // серверу текущей, и смена на ходу его рвала
                    locked = vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING,
                )
            }
        }
      }

      item {
        // Полоса гаснет сама: держать её до следующего нажатия незачем,
        // а закрывать вручную — лишнее действие ради сообщения на секунду.
        AnimatedVisibility(
            visible = notice != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            // при исчезновении показываем последний непустой текст, иначе
            // строка успела бы опустеть до конца анимации
            lastNotice?.let { NoticeBar(it) }
        }

        // Подмена узла держится, пока человек не переподключится сам: это не
        // мимолётное сообщение, а объяснение, почему он сейчас в другой
        // стране. От неё зависят и скорость, и то, какие сайты откроются.
        switchedFrom?.let { asked ->
            Spacer(Modifier.height(8.dp))
            NoticeBar(
                Notice(
                    "«$asked» не ответил — подключились к другому серверу",
                    ok = false,
                )
            )
        }
      }

      item {
        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }
      }

      // --- Серверы прямо на главной, без отдельного окна ---
      item {
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.servers.isEmpty()) "Серверы" else "Серверы · ${state.servers.size}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.pinging) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                } else {
                    Text(
                        "Проверить пинг",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.pingAll() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                IconButton(onClick = { viewModel.refresh(forceServers = true, notify = true) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить список")
                }
            }
        }
      }

      if (state.servers.isEmpty()) {
        item {
            Text(
                "Список серверов пуст. Проверьте подписку.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
      } else {
        itemsIndexed(
            state.servers,
            // номер в ключе: если в подписке два узла с одинаковым именем и
            // адресом, одинаковые ключи роняли бы список
            key = { index, server -> "${server.key}#$index" },
        ) { index, server ->
            Box(Modifier.padding(bottom = 10.dp)) {
                ServerRow(
                    server = server,
                    selected = index == state.selectedServer,
                    ping = state.pings[server.key],
                    pinging = state.pinging,
                    onClick = { viewModel.selectServer(index) },
                )
            }
        }
      }

      item { Spacer(Modifier.height(24.dp)) }
    }
    }
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

/**
 * Плашка под словом «Защищено»: зелёная точка, сервер и время подключения.
 *
 * Время тикает здесь, а не в модели экрана: раз в секунду меняется одна
 * строка, и перерисовывать ради неё весь экран незачем.
 */
@Composable
private fun ConnectedPill(serverName: String?, modifier: Modifier = Modifier) {
    val since by VpnStateStore.connectedSince.collectAsStateWithLifecycle()
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
        elapsed?.let { formatDuration(it) },
    ).joinToString(" · ")

    val green = BrandColors.Success
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(green.copy(alpha = 0.10f))
            .border(1.dp, green.copy(alpha = 0.30f), shape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(green)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = lerp(green, MaterialTheme.colorScheme.onBackground, 0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Секунды как «00:42:17». */
private fun formatDuration(seconds: Long): String =
    String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)

/**
 * «Получено» и «Отправлено» одной строкой-капсулой.
 *
 * Две плитки занимали столько же места, сколько кнопка, и спорили с ней за
 * внимание. Стрелка вниз и вверх различает числа быстрее подписи.
 */
@Composable
private fun TrafficCapsule(received: String, sent: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline.copy(alpha = 0.5f), shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrafficValue(Icons.Default.ArrowDownward, BrandColors.Success, received)
        Box(
            Modifier
                .padding(vertical = 8.dp)
                .width(1.dp)
                .fillMaxHeight()
                .background(scheme.outline.copy(alpha = 0.5f))
        )
        TrafficValue(Icons.Default.ArrowUpward, scheme.secondary, sent)
    }
}

@Composable
private fun TrafficValue(icon: ImageVector, tint: Color, value: String) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

/** Заголовок раздела главного экрана: название слева, действие справа. */
@Composable
private fun HomeSectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Оставшиеся дни подписки кольцом.
 *
 * Кольцо заполнено по доле от тридцати дней: длину оплаченного периода
 * кабинет не присылает, а месяц — самый частый срок. Больше месяца — кольцо
 * полное. Истекла — пустое и красное.
 */
@Composable
private fun DaysRing(daysLeft: Int?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val expired = daysLeft != null && daysLeft <= 0
    val tint = if (expired) scheme.error else scheme.primary
    val track = scheme.outline.copy(alpha = 0.5f)
    val share = if (daysLeft == null || expired) 0f else (daysLeft / 30f).coerceIn(0.04f, 1f)

    Box(modifier = modifier.size(60.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val arcTopLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (share > 0f) {
                drawArc(
                    color = tint,
                    startAngle = -90f,
                    sweepAngle = 360f * share,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    daysLeft == null -> "—"
                    expired -> "0"
                    daysLeft > 999 -> "999+"
                    else -> daysLeft.toString()
                },
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Bold),
                color = tint,
                maxLines = 1,
            )
            Text(
                "дн.",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Кнопка подключения.
 *
 * Тёмный «стеклянный» диск в кольце. Выключено — кольцо серое. Подключено —
 * кольцо наливается акцентом, светится и медленно вращается, значок на диске
 * загорается тем же цветом, от кнопки расходятся волны. Идёт подключение —
 * кольцо синее. Переход плавный: цвет проявляется, а не вспыхивает.
 */
@Composable
private fun PowerButton(vpnState: VpnState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val gold = scheme.primary
    val connected = vpnState == VpnState.CONNECTED
    val accent by animateColorAsState(
        when (vpnState) {
            VpnState.CONNECTED -> scheme.primary
            VpnState.CONNECTING -> scheme.secondary
            VpnState.ERROR -> scheme.error
            VpnState.DISCONNECTED -> scheme.onSurfaceVariant
        },
        tween(600),
        label = "accent",
    )
    val fill by animateFloatAsState(if (connected) 1f else 0f, tween(600), label = "fill")

    val pulse = rememberInfiniteTransition(label = "pulse")
    val ringProgress by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "ring",
    )
    // оборот кольца: блик на нём плывёт, но в глаза не лезет
    val bezel by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing)),
        label = "bezel",
    )
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (vpnState == VpnState.CONNECTING) 1.04f else 1.015f,
        animationSpec = infiniteRepeatable(
            tween(if (vpnState == VpnState.CONNECTING) 900 else 2600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val panel = LocalPalette.current.panel
    val hairline = scheme.onSurface.copy(alpha = 0.10f)

    // Кольцо — градиент по кругу: светлая сторона, тёмная сторона. Цвета идут
    // от серого (выключено) или цвета состояния к акценту по мере наливания.
    val ringHi = lerp(accent.copy(alpha = 0.55f), lerp(gold, Color.White, 0.40f), fill)
    val ringMid = lerp(accent.copy(alpha = 0.28f), gold, fill)
    val ringLo = lerp(accent.copy(alpha = 0.12f), lerp(gold, Color.Black, 0.50f), fill)

    Box(
        modifier = modifier.size(268.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            if (fill > 0f) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(gold.copy(alpha = 0.34f * fill), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                )
            }
            // внешний пунктир — граница сцены
            drawCircle(
                hairline,
                radius = radius * 0.94f,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx())),
                ),
            )

            val ringRadius = radius * 0.74f
            val ringWidth = 9.dp.toPx()
            // свечение кольца: два широких прозрачных штриха под ним
            if (fill > 0f) {
                drawCircle(gold.copy(alpha = 0.08f * fill), radius = ringRadius, style = Stroke(ringWidth * 3.2f))
                drawCircle(gold.copy(alpha = 0.14f * fill), radius = ringRadius, style = Stroke(ringWidth * 1.9f))
            }
            rotate(degrees = if (connected) bezel else 0f) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(ringHi, ringMid, ringLo, ringMid, ringHi),
                        center = center,
                    ),
                    radius = ringRadius,
                    style = Stroke(ringWidth),
                )
            }
            if (connected) {
                listOf(ringProgress, (ringProgress + 0.5f) % 1f).forEach { progress ->
                    drawCircle(
                        color = accent.copy(alpha = (1f - progress) * 0.35f),
                        radius = radius * (0.78f + 0.22f * progress),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
        }

        val discHi = lerp(panel, Color.White, 0.10f)
        val discLo = lerp(panel, Color.Black, 0.45f)
        Box(
            modifier = Modifier
                .size(164.dp)
                .scale(scale)
                .clip(CircleShape)
                .drawBehind {
                    // стекло: свет сверху, тень снизу
                    drawCircle(
                        Brush.radialGradient(
                            listOf(discHi, discLo),
                            center = Offset(size.width * 0.5f, size.height * 0.22f),
                            radius = size.width * 0.75f,
                        ),
                    )
                    // подключено — диск подсвечен акцентом от краёв
                    if (fill > 0f) {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(Color.Transparent, gold.copy(alpha = 0.26f * fill)),
                                center = center,
                                radius = size.width / 2f,
                            ),
                        )
                    }
                }
                .border(1.dp, lerp(scheme.onSurface.copy(alpha = 0.10f), gold.copy(alpha = 0.40f), fill), CircleShape)
                .clickable(enabled = vpnState != VpnState.CONNECTING, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (vpnState == VpnState.CONNECTING) {
                CircularProgressIndicator(color = accent, strokeWidth = 3.dp)
            } else {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = if (connected) "Отключить VPN" else "Подключить VPN",
                    modifier = Modifier.size(64.dp),
                    tint = lerp(accent, lerp(gold, Color.White, 0.25f), fill),
                )
            }
        }
    }
}

/**
 * Карточка подписки: название с переключателем тарифов, срок и трафик
 * полосой, а под ними — выбранный сервер.
 */
@Composable
private fun SubscriptionCard(
    state: com.darkprince.vpn.ui.vm.HomeUiState,
    onRefresh: () -> Unit,
    onPing: () -> Unit,
    onSelectSubscription: (Long) -> Unit,
    locked: Boolean,
) {
    val accent = MaterialTheme.colorScheme.primary
    val current = state.selectedSubscription
    val userInfo = state.subUserInfo

    // Общий статус кабинета — только когда конкретной подписки нет вовсе
    // (гость, список не пришёл). Раньше он подставлялся в каждое пустое поле,
    // и при нескольких подписках на карточке смешивались две: срок от одной,
    // остаток трафика от другой. Заметнее всего это было с истёкшей — у неё
    // панель половину полей не заполняет.
    val account = state.subscription.takeIf { current == null }

    val title = current?.displayName
        ?: account?.tariffName
        ?: "Подписка"

    // userInfo относится к выбранной подписке: он приходит её же заголовком,
    // поэтому смешения тут нет
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

    var menuOpen by remember { mutableStateOf(false) }
    var lockHint by remember { mutableStateOf(false) }
    LaunchedEffect(lockHint) {
        if (lockHint) {
            delay(3000)
            lockHint = false
        }
    }

    GroupCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (state.subscriptions.size > 1) {
                            Modifier.clickable { if (locked) lockHint = true else menuOpen = true }
                        } else Modifier
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.subscriptions.size > 1) {
                    Icon(
                        if (locked) Icons.Default.Lock else Icons.Default.ExpandMore,
                        contentDescription = if (locked) "Подписку можно сменить после отключения VPN"
                            else "Сменить подписку",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            CircleActionButton(Icons.Default.Refresh, "Обновить", onRefresh)
            Spacer(Modifier.width(8.dp))
            CircleActionButton(Icons.Default.Speed, "Проверить пинг", onPing)
        }

        AnimatedVisibility(visible = lockHint) {
            Text(
                "Отключите VPN, чтобы сменить подписку",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
        }

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

        // срок — кольцом слева, трафик — полосой и строкой справа
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DaysRing(daysLeft)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                // «Осталось 0 дн.» читается как «ещё работает сегодня», хотя подписка
                // уже кончилась — поэтому у истёкшей отдельная надпись и цвет
                val expired = daysLeft != null && daysLeft <= 0
                Text(
                    when {
                        daysLeft == null -> "Срок неизвестен"
                        expired -> "Истекла"
                        else -> "Осталось $daysLeft дн."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (expired) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(7.dp))
                LinearProgressIndicator(
                    progress = {
                        val used = usedGb ?: 0.0
                        val limit = limitGb ?: 0.0
                        if (limit > 0) (used / limit).coerceIn(0.0, 1.0).toFloat() else 0.06f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Трафик",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        buildString {
                            append(String.format(Locale.getDefault(), "%.1f ГБ", usedGb ?: 0.0))
                            append(" / ")
                            // ноль в лимите означает безлимит, а не «нисколько»
                            append(
                                limitGb?.takeIf { it > 0 }
                                    ?.let { String.format(Locale.getDefault(), "%.0f", it) }
                                    ?: "∞"
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        current?.graceUntil?.let { until ->
            Text(
                "Временный доступ до ${cabinetDate(until)} — продлите подписку, чтобы VPN не отключился",
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.Warning,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (current?.isDailyPaused == true) {
            Text(
                "Суточный тариф на паузе — VPN не работает. Возобновить можно в «Тарифах».",
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.Warning,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (current?.requiresTariffSelection == true) {
            Text(
                "Подписка оформлена до появления тарифов. Чтобы продлить её, выберите тариф в «Тарифах».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        // Текущий сервер — просто напоминание, куда подключаемся. Выбирают
        // теперь в списке ниже на этом же экране, поэтому строка не нажимается.
        val selected = state.servers.getOrNull(state.selectedServer)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmojiTile(emoji = selected?.name?.let(::leadingEmoji) ?: "🌐", tint = accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = selected?.name?.let(::nameWithoutEmoji) ?: "Нет доступных серверов",
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    label = "server",
                ) { name ->
                    Text(name, style = MaterialTheme.typography.titleSmall)
                }
                selected?.let {
                    // подпись узла из панели: там её пишут для людей — «1 Гбит»,
                    // «для игр», — и это полезнее, чем набор меток транспорта
                    it.serverDescription?.let { note ->
                        Spacer(Modifier.height(3.dp))
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        it.transportParts.forEach { part -> TransportBadge(part) }
                    }
                }
            }
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
