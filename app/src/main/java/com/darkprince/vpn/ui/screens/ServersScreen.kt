package com.darkprince.vpn.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.core.model.ProxyProfile
import com.darkprince.vpn.ui.theme.EmojiTile
import com.darkprince.vpn.ui.theme.PingChip
import com.darkprince.vpn.ui.theme.SectionHeader
import com.darkprince.vpn.ui.theme.TagChip
import com.darkprince.vpn.ui.theme.leadingEmoji
import com.darkprince.vpn.ui.theme.nameWithoutEmoji
import com.darkprince.vpn.ui.vm.HomeViewModel

@Composable
fun ServersScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Серверы", style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.pinging) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = { viewModel.pingAll() }) {
                        Icon(Icons.Default.Speed, contentDescription = "Проверить пинг")
                    }
                }
                IconButton(onClick = { viewModel.refresh(forceServers = true) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить список")
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        if (state.servers.isEmpty()) {
            Text(
                "Список серверов пуст. Проверьте подписку.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            SectionHeader("Доступные узлы · ${state.servers.size}")
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(state.servers) { index, server ->
                    ServerRow(
                        server = server,
                        selected = index == state.selectedServer,
                        ping = state.pings[server.key],
                        onClick = { viewModel.selectServer(index) },
                    )
                }
            }
        }
    }
}

/**
 * Строка сервера: слева плитка с флагом из названия, справа — метки
 * транспорта и формата конфига. Выбранный узел обведён акцентной рамкой.
 */
@Composable
internal fun ServerRow(
    server: ProxyProfile,
    selected: Boolean,
    ping: Long?,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        animationSpec = tween(250),
        label = "serverBorder",
    )
    val background by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surface,
        animationSpec = tween(250),
        label = "serverBackground",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiTile(emoji = leadingEmoji(server.name) ?: "🌐", tint = accent)
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                nameWithoutEmoji(server.name),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TagChip(server.transportLabel, tint = MaterialTheme.colorScheme.secondary)
                if (server.rawConfig != null) {
                    TagChip("JSON", tint = accent)
                }
            }
        }

        ping?.let {
            Spacer(Modifier.width(8.dp))
            PingChip(it)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Выбран",
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
