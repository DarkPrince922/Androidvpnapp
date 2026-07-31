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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.ui.theme.Appear
import com.darkprince.vpn.ui.vm.HomeViewModel
import com.darkprince.vpn.vpn.VpnState
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
    onOpenServers: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val stats by viewModel.vpnStats.collectAsStateWithLifecycle()
    val vpnError by viewModel.vpnError.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            // при нескольких подписках добавляется карточка-переключатель и
            // блок со сроком и трафиком уезжает за нижний край экрана
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Главная", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { viewModel.refresh(forceServers = true) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Обновить")
            }
        }

        Spacer(Modifier.height(24.dp))

        // Кнопка подключения с живой индикацией состояния
        val targetColor = when (vpnState) {
            VpnState.CONNECTED -> MaterialTheme.colorScheme.primary
            VpnState.CONNECTING -> MaterialTheme.colorScheme.secondary
            VpnState.ERROR -> MaterialTheme.colorScheme.error
            VpnState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
        }
        val statusText = when (vpnState) {
            VpnState.CONNECTED -> "Подключено"
            VpnState.CONNECTING -> "Подключение…"
            VpnState.ERROR -> vpnError ?: "Ошибка"
            VpnState.DISCONNECTED -> "Отключено"
        }
        val buttonColor by animateColorAsState(
            targetValue = targetColor,
            animationSpec = tween(600),
            label = "buttonColor",
        )

        val pulse = rememberInfiniteTransition(label = "pulse")
        // «радар» вокруг кнопки, когда туннель поднят
        val ringProgress by pulse.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
            label = "ring",
        )
        // мягкое дыхание кнопки в покое и при подключении
        val scale by pulse.animateFloat(
            initialValue = 1f,
            targetValue = if (vpnState == VpnState.CONNECTING) 1.06f else 1.02f,
            animationSpec = infiniteRepeatable(
                tween(if (vpnState == VpnState.CONNECTING) 900 else 2600, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "scale",
        )

        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (vpnState == VpnState.CONNECTED) {
                Canvas(Modifier.fillMaxSize()) {
                    // две расходящиеся волны со сдвигом по фазе
                    listOf(ringProgress, (ringProgress + 0.5f) % 1f).forEach { progress ->
                        drawCircle(
                            color = buttonColor.copy(alpha = (1f - progress) * 0.35f),
                            radius = size.minDimension / 2f * (0.55f + 0.45f * progress),
                            style = Stroke(width = 3.dp.toPx()),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(scale)
                    .background(buttonColor, CircleShape)
                    .clickable(enabled = vpnState != VpnState.CONNECTING) {
                        if (vpnState == VpnState.CONNECTED) onDisconnectClick() else onConnectClick()
                    },
                contentAlignment = Alignment.Center,
            ) {
                // на золотой кнопке тёмная иконка читается лучше белой
                val iconTint = if (vpnState == VpnState.CONNECTED) Color(0xFF14100A) else Color.White
                if (vpnState == VpnState.CONNECTING) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Icon(
                        Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = iconTint,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        AnimatedContent(
            targetState = statusText,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "status",
        ) { text ->
            Text(text, style = MaterialTheme.typography.titleMedium)
        }

        AnimatedVisibility(
            visible = vpnState == VpnState.CONNECTED,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                "↑ ${formatBytes(stats.uplinkBytes)}   ↓ ${formatBytes(stats.downlinkBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        // Переключатель подписок — только когда их несколько
        if (state.subscriptions.size > 1) {
            var menuOpen by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { menuOpen = true },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Подписка", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.selectedSubscription?.displayName ?: "Выберите подписку",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Icon(Icons.Default.ExpandMore, contentDescription = "Сменить подписку")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    state.subscriptions.forEach { sub ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(sub.displayName)
                                    val details = listOfNotNull(
                                        sub.endDate?.take(10),
                                        if (sub.isActive) null else "неактивна",
                                    ).joinToString(" · ")
                                    if (details.isNotBlank()) {
                                        Text(details, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            },
                            onClick = {
                                menuOpen = false
                                viewModel.selectSubscription(sub.id)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Выбранный сервер
        val selected = state.servers.getOrNull(state.selectedServer)
        Appear(delayMillis = 60) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenServers() },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Сервер", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(
                        targetState = selected?.name ?: "Нет доступных серверов",
                        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                        label = "server",
                    ) { name ->
                        Text(name, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Статус подписки: поля кабинета, при их отсутствии — данные из
        // заголовка subscription-userinfo самой подписки Remnawave
        val sub = state.subscription
        val userInfo = state.subUserInfo
        // при нескольких подписках общий статус кабинета относится не к той,
        // что выбрана, — сведения берём из самой выбранной подписки
        val current = state.selectedSubscription.takeIf { state.subscriptions.size > 1 }
        Appear(delayMillis = 140) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Подписка", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                var shownAnything = false
                (current?.tariffName ?: sub?.tariffName)?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium)
                    shownAnything = true
                }
                val daysLeft = current?.endDate?.let(::daysUntil)
                    ?: sub?.daysLeft
                    ?: userInfo?.expireUnix?.takeIf { it > 0 }?.let {
                        ((it * 1000 - System.currentTimeMillis()) / 86_400_000L).toInt().coerceAtLeast(0)
                    }
                daysLeft?.let {
                    Text("Осталось дней: $it")
                    shownAnything = true
                }
                val usedGb = current?.trafficUsedGb
                    ?: sub?.trafficUsedGb
                    ?: userInfo?.let { info ->
                        val total = (info.uploadBytes ?: 0) + (info.downloadBytes ?: 0)
                        if (total > 0 || info.totalBytes != null) total / 1_073_741_824.0 else null
                    }
                val limitGb = current?.trafficLimitGb
                    ?: sub?.trafficLimitGb
                    ?: userInfo?.totalBytes?.takeIf { it > 0 }?.let { it / 1_073_741_824.0 }
                if (usedGb != null) {
                    val usedText = String.format(Locale.getDefault(), "%.1f ГБ", usedGb)
                    if (limitGb != null && limitGb > 0) {
                        Text(
                            "Трафик: $usedText из ${
                                String.format(Locale.getDefault(), "%.0f ГБ", limitGb)
                            }"
                        )
                    } else {
                        Text("Трафик: $usedText (безлимит)")
                    }
                    shownAnything = true
                }
                if (!shownAnything) {
                    when {
                        state.loading -> Text("Загрузка…")
                        state.servers.isEmpty() -> Text("Нет активной подписки")
                        else -> Text("Подписка активна")
                    }
                }
            }
        }
        }

        state.error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Сколько дней осталось до даты окончания подписки. Кабинет отдаёт дату
 * строкой ISO — берём из неё только календарный день, время и часовой пояс
 * для «осталось дней» роли не играют.
 */
private fun daysUntil(endDate: String): Int? = try {
    val date = java.time.LocalDate.parse(endDate.take(10))
    java.time.temporal.ChronoUnit.DAYS
        .between(java.time.LocalDate.now(), date)
        .toInt()
        .coerceAtLeast(0)
} catch (_: Exception) {
    null
}
