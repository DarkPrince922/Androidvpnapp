package com.darkprince.vpn.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

        // Кнопка подключения
        val (buttonColor, statusText) = when (vpnState) {
            VpnState.CONNECTED -> MaterialTheme.colorScheme.primary to "Подключено"
            VpnState.CONNECTING -> MaterialTheme.colorScheme.secondary to "Подключение…"
            VpnState.ERROR -> MaterialTheme.colorScheme.error to (vpnError ?: "Ошибка")
            VpnState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant to "Отключено"
        }
        Box(
            modifier = Modifier
                .size(180.dp)
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
        Spacer(Modifier.height(12.dp))
        Text(statusText, style = MaterialTheme.typography.titleMedium)

        if (vpnState == VpnState.CONNECTED) {
            Spacer(Modifier.height(4.dp))
            Text(
                "↑ ${formatBytes(stats.uplinkBytes)}   ↓ ${formatBytes(stats.downlinkBytes)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(24.dp))

        // Выбранный сервер
        val selected = state.servers.getOrNull(state.selectedServer)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenServers() },
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Сервер", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    selected?.name ?: "Нет доступных серверов",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Статус подписки: поля кабинета, при их отсутствии — данные из
        // заголовка subscription-userinfo самой подписки Remnawave
        val sub = state.subscription
        val userInfo = state.subUserInfo
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Подписка", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                var shownAnything = false
                sub?.tariffName?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium)
                    shownAnything = true
                }
                val daysLeft = sub?.daysLeft
                    ?: userInfo?.expireUnix?.takeIf { it > 0 }?.let {
                        ((it * 1000 - System.currentTimeMillis()) / 86_400_000L).toInt().coerceAtLeast(0)
                    }
                daysLeft?.let {
                    Text("Осталось дней: $it")
                    shownAnything = true
                }
                val usedGb = sub?.trafficUsedGb
                    ?: userInfo?.let { info ->
                        val total = (info.uploadBytes ?: 0) + (info.downloadBytes ?: 0)
                        if (total > 0 || info.totalBytes != null) total / 1_073_741_824.0 else null
                    }
                val limitGb = sub?.trafficLimitGb
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

        state.error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
