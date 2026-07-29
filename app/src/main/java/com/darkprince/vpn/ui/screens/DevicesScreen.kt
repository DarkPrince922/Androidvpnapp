package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.data.api.dto.DeviceDto
import com.darkprince.vpn.ui.vm.DevicesViewModel

@Composable
fun DevicesScreen(viewModel: DevicesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var confirmDelete by remember { mutableStateOf<DeviceDto?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<DeviceDto?>(null) }
    var subsMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Подключённые устройства", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Отключите лишние устройства, чтобы освободить места в лимите подписки.",
            style = MaterialTheme.typography.bodySmall,
        )

        if (state.subscriptions.size > 1) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { subsMenuOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(state.selectedSubscription?.displayName ?: "Выберите подписку")
            }
            DropdownMenu(expanded = subsMenuOpen, onDismissRequest = { subsMenuOpen = false }) {
                state.subscriptions.forEach { sub ->
                    DropdownMenuItem(
                        text = { Text(sub.displayName) },
                        onClick = {
                            subsMenuOpen = false
                            viewModel.selectSubscription(sub.id)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildString {
                    append("Подключено: ${state.devices.size}")
                    state.deviceLimit?.let { append(" из $it") }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.devices.isNotEmpty()) {
                TextButton(
                    onClick = { confirmDeleteAll = true },
                    enabled = !state.busy,
                ) {
                    Text("Отключить все")
                }
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        state.info?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))

        when {
            state.loading -> CircularProgressIndicator()
            state.devices.isEmpty() -> Column {
                Text(
                    "Устройств пока нет. Панель заводит устройство в момент " +
                        "загрузки подписки — нажмите кнопку ниже, если этот телефон " +
                        "не появился в списке.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.registerThisDevice() },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Зарегистрировать это устройство")
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // если этого телефона нет в списке — панель его не завела
                if (state.devices.none { it.hwid == viewModel.ownHwid }) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "Этого телефона нет в списке",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Устройство регистрируется при загрузке подписки. " +
                                        "Нажмите, чтобы запросить её заново.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.registerThisDevice() },
                                    enabled = !state.busy,
                                ) {
                                    Text("Зарегистрировать")
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "ID этого устройства: ${viewModel.ownHwid.take(13)}…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                items(state.devices, key = { it.hwid }) { device ->
                    val isCurrent = device.hwid == viewModel.ownHwid
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(device.title, style = MaterialTheme.typography.titleMedium)
                                    if (isCurrent) {
                                        Spacer(Modifier.height(0.dp))
                                        Text(
                                            "  · это устройство",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                device.subtitle?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                device.createdAt?.let {
                                    Text(
                                        "Подключено: ${it.take(10)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { renaming = device },
                                enabled = !state.busy,
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Переименовать")
                            }
                            IconButton(
                                onClick = { confirmDelete = device },
                                enabled = !state.busy,
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Отключить",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { device ->
        val isCurrent = device.hwid == viewModel.ownHwid
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Отключить устройство?") },
            text = {
                Text(
                    if (isCurrent) {
                        "Это устройство, с которого вы сейчас пользуетесь VPN. " +
                            "После отключения придётся подключиться заново."
                    } else {
                        "«${device.title}» потеряет доступ к подписке и освободит место в лимите."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDevice(device.hwid)
                    confirmDelete = null
                }) {
                    Text("Отключить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Отмена") }
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Отключить все устройства?") },
            text = {
                Text(
                    "Доступ потеряют все устройства, включая это. " +
                        "Каждому нужно будет подключиться заново."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    confirmDeleteAll = false
                }) {
                    Text("Отключить все", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Отмена") }
            },
        )
    }

    renaming?.let { device ->
        var name by remember(device.hwid) { mutableStateOf(device.localName ?: device.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Название устройства") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Например: телефон жены") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(device.hwid, name)
                    renaming = null
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("Отмена") }
            },
        )
    }
}
