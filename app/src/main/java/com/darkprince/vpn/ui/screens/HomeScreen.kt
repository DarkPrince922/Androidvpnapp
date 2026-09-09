package com.darkprince.vpn.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.ui.theme.Appear
import com.darkprince.vpn.ui.theme.bleedHorizontally
import com.darkprince.vpn.ui.theme.LocalPalette
import com.darkprince.vpn.ui.theme.CircleActionButton
import com.darkprince.vpn.ui.theme.ConnectionMap
import com.darkprince.vpn.ui.theme.EmojiTile
import com.darkprince.vpn.ui.theme.GroupCard
import com.darkprince.vpn.ui.theme.QuietMapBackdrop
import com.darkprince.vpn.ui.theme.SectionHeader
import com.darkprince.vpn.ui.theme.TransportBadge
import com.darkprince.vpn.ui.theme.leadingEmoji
import com.darkprince.vpn.ui.theme.nameWithoutEmoji
import com.darkprince.vpn.ui.vm.HomeViewModel
import com.darkprince.vpn.ui.vm.Notice
import com.darkprince.vpn.vpn.VpnState
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

/**
 * Сессия кабинета оборвалась, а подписка осталась.
 *
 * Раньше в этом положении приложение показывало экран входа и всё: VPN,
 * оплаченный и полностью рабочий, оказывался заперт за формой логина. Между
 * тем список узлов лежит в памяти телефона и кабинета не требует — туннель
 * поднимается и без него. Кабинет нужен для другого: продлить, пополнить,
 * написать в поддержку. Поэтому здесь не преграда, а объяснение и кнопка.
 */
@Composable
private fun SessionExpiredCard(onSignIn: () -> Unit) {
    GroupCard(modifier = Modifier.padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Нужно войти заново",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "VPN работает — подписка сохранена. Войдите, чтобы вернуть " +
                    "тарифы, баланс и поддержку.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onSignIn,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("Войти")
            }
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    /** Сессия кабинета оборвалась, но подписка на месте и VPN работает. */
    sessionExpired: Boolean = false,
    onSignIn: () -> Unit = {},
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
      if (sessionExpired) {
          item(key = "session-expired") { SessionExpiredCard(onSignIn = onSignIn) }
      }

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

        AnimatedContent(
            targetState = when (vpnState) {
                VpnState.CONNECTED -> "Подключено"
                VpnState.CONNECTING -> "Подключение…"
                VpnState.ERROR -> vpnError ?: "Ошибка"
                VpnState.DISCONNECTED -> "Нажмите для подключения"
            },
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "status",
        ) { text ->
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = vpnState == VpnState.CONNECTED,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text("↑ ${formatBytes(stats.uplinkBytes)}", style = MaterialTheme.typography.bodyMedium)
                Text("↓ ${formatBytes(stats.downlinkBytes)}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(18.dp))
        }
      }

      item {
        Column(Modifier.fillMaxWidth()) {
            SectionHeader("Текущая подписка")
            Appear {
                SubscriptionCard(
                    state = state,
                    onRefresh = { viewModel.refresh(forceServers = true, notify = true) },
                    onPing = { viewModel.pingAll() },
                    onSelectSubscription = { viewModel.selectSubscription(it) },
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
            SectionHeader(
                if (state.servers.isEmpty()) "Серверы"
                else "Серверы · ${state.servers.size}"
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.pinging) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                } else {
                    IconButton(onClick = { viewModel.pingAll() }) {
                        Icon(Icons.Default.Speed, contentDescription = "Проверить пинг")
                    }
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
        itemsIndexed(state.servers, key = { _, server -> server.key }) { index, server ->
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

/** Большая круглая кнопка с тонким кольцом и «радаром» при подключении. */
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

@Composable
private fun PowerButton(vpnState: VpnState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val targetColor = when (vpnState) {
        VpnState.CONNECTED -> MaterialTheme.colorScheme.primary
        VpnState.CONNECTING -> MaterialTheme.colorScheme.secondary
        VpnState.ERROR -> MaterialTheme.colorScheme.error
        VpnState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val accent by animateColorAsState(targetColor, tween(600), label = "accent")

    val pulse = rememberInfiniteTransition(label = "pulse")
    val ringProgress by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "ring",
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

    Box(
        modifier = modifier.size(268.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (vpnState == VpnState.CONNECTED) {
            Canvas(Modifier.fillMaxSize()) {
                listOf(ringProgress, (ringProgress + 0.5f) % 1f).forEach { progress ->
                    drawCircle(
                        color = accent.copy(alpha = (1f - progress) * 0.3f),
                        radius = size.minDimension / 2f * (0.72f + 0.28f * progress),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }

        // тонкое кольцо вокруг тёмного круга — без заливки акцентом
        Box(
            modifier = Modifier
                .size(230.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(LocalPalette.current.panel.copy(alpha = 0.7f))
                .border(2.dp, accent.copy(alpha = 0.55f), CircleShape)
                .clickable(enabled = vpnState != VpnState.CONNECTING, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (vpnState == VpnState.CONNECTING) {
                CircularProgressIndicator(color = accent, strokeWidth = 3.dp)
            } else {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(88.dp),
                    tint = accent,
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
                            Modifier.clickable { menuOpen = true }
                        } else Modifier
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.subscriptions.size > 1) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = "Сменить подписку",
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

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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

        // срок и трафик одной строкой, как в референсе
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            Spacer(Modifier.width(10.dp))
            LinearProgressIndicator(
                progress = {
                    val used = usedGb ?: 0.0
                    val limit = limitGb ?: 0.0
                    if (limit > 0) (used / limit).coerceIn(0.0, 1.0).toFloat() else 0.06f
                },
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
            Spacer(Modifier.width(10.dp))
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
