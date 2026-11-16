package com.darkprince.vpn.ui.screens

import com.darkprince.vpn.ui.theme.serviceDown
import com.darkprince.vpn.ui.theme.ServiceDownBanner
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.darkprince.vpn.ui.theme.AquaIcon
import com.darkprince.vpn.ui.theme.AquaIconView
import com.darkprince.vpn.ui.theme.glass
import com.darkprince.vpn.ui.theme.ink
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
    // без кабинета отключить устройство нельзя — кнопки гасим заранее
    val down = serviceDown()

    var confirmDelete by remember { mutableStateOf<DeviceDto?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<DeviceDto?>(null) }
    var subsMenuOpen by remember { mutableStateOf(false) }

    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(start = 4.dp, top = 12.dp)) {
            Text("Устройства", style = MaterialTheme.typography.displaySmall)
            Text(
                "Отключите лишние, чтобы освободить место в лимите",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        ServiceDownBanner(
            "Отключать и переименовывать устройства можно будет, когда сервис вернётся.",
            Modifier.padding(top = 12.dp),
        )

        // --- сколько занято: число, шкала, подписка ---
        val used = state.devices.size
        val limit = state.deviceLimit
        Column(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .glass()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    used.toString(),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1).sp,
                )
                Text(
                    if (limit != null) "из $limit" else "подключено",
                    fontSize = 15.sp,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 5.dp),
                )
                Spacer(Modifier.weight(1f))
                if (state.subscriptions.size > 1) {
                    Box {
                        Text(
                            state.selectedSubscription?.displayName ?: "Подписка",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary,
                            maxLines = 1,
                            modifier = Modifier
                                .widthIn(max = 150.dp)
                                .clip(RoundedCornerShape(50))
                                .background(scheme.primary.copy(alpha = 0.12f))
                                .border(1.dp, scheme.primary.copy(alpha = 0.3f), RoundedCornerShape(50))
                                .clickable { subsMenuOpen = true }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        )
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
                } else {
                    state.selectedSubscription?.displayName?.let {
                        Text(
                            it,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary,
                            maxLines = 1,
                            modifier = Modifier
                                .widthIn(max = 150.dp)
                                .clip(RoundedCornerShape(50))
                                .background(scheme.primary.copy(alpha = 0.12f))
                                .border(1.dp, scheme.primary.copy(alpha = 0.3f), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            if (limit != null && limit > 0) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (used.toFloat() / limit).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (used > limit) scheme.error else scheme.primary,
                    trackColor = ink(0.10f),
                )
            }
        }

        state.error?.let {
            Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
        }
        state.info?.let {
            Text(it, color = scheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
        }

        if (state.devices.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    "Отключить все",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (!state.busy && !down) scheme.error else scheme.error.copy(alpha = 0.4f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !state.busy && !down) { confirmDeleteAll = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }

        when {
            state.loading -> com.darkprince.vpn.ui.theme.LoadingCapsule("Загружаем устройства")
            state.devices.isEmpty() -> Column(Modifier.fillMaxWidth().glass().padding(18.dp)) {
                Text(
                    "Устройств пока нет. Панель заводит устройство в момент " +
                        "загрузки подписки — нажмите кнопку ниже, если этот телефон " +
                        "не появился в списке.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.registerThisDevice() },
                    enabled = !state.busy && !down,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text("Зарегистрировать это устройство")
                }
            }
            else -> {
                // если этого телефона нет в списке — панель его не завела
                if (state.devices.none { it.hwid == viewModel.ownHwid }) {
                    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                        Text("Этого телефона нет в списке", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Устройство регистрируется при загрузке подписки. " +
                                "Нажмите, чтобы запросить её заново.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.registerThisDevice() },
                            enabled = !state.busy && !down,
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text("Зарегистрировать")
                        }
                        Text(
                            "ID этого устройства: ${viewModel.ownHwid.take(13)}…",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                // все устройства — одной стеклянной плитой
                Column(Modifier.fillMaxWidth().glass()) {
                    state.devices.forEachIndexed { index, device ->
                        if (index > 0) androidx.compose.material3.HorizontalDivider(color = ink(0.07f))
                        DeviceRow(
                            device = device,
                            isCurrent = device.hwid == viewModel.ownHwid,
                            enabled = !state.busy && !down,
                            onRename = { renaming = device },
                            onDelete = { confirmDelete = device },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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

/** «16.11.2026» из «2026-11-16T…»; не разобрали — как пришло. */
private fun deviceDate(raw: String): String {
    val d = raw.take(10).split("-")
    return if (d.size == 3) "${d[2]}.${d[1]}.${d[0]}" else raw.take(10)
}

@Composable
private fun DeviceRow(
    device: DeviceDto,
    isCurrent: Boolean,
    enabled: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    val desktop = listOfNotNull(device.platform, device.deviceModel)
        .any { it.contains("windows", true) || it.contains("mac", true) || it.contains("linux", true) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(ink(0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            AquaIconView(if (desktop) AquaIcon.Servers else AquaIcon.Phone, ink(0.85f), size = 20.dp, stroke = 1.5f)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isCurrent) {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "это устройство",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.primary.copy(alpha = 0.13f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                listOfNotNull(device.subtitle, device.createdAt?.let(::deviceDate)).joinToString(" · "),
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled) { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                AquaIconView(AquaIcon.More, scheme.onSurfaceVariant, size = 20.dp, stroke = 2.4f)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Переименовать") },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Отключить", color = scheme.error) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}
