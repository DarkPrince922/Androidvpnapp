package com.darkprince.vpn.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
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

@Composable
fun ServersScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        Spacer(Modifier.height(8.dp))

        if (state.servers.isEmpty()) {
            Text(
                "Список серверов пуст. Проверьте подписку.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.servers) { index, server ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectServer(index) },
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(server.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${server.protocol.name.lowercase()} · ${server.address}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            state.pings[index]?.let { ping ->
                                Text(
                                    text = if (ping < 0) "нет ответа" else "$ping мс",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        ping < 0 -> MaterialTheme.colorScheme.error
                                        ping < 300 -> Color(0xFF34A853)
                                        ping < 700 -> Color(0xFFF9A825)
                                        else -> MaterialTheme.colorScheme.error
                                    },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                            if (index == state.selectedServer) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Выбран",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
