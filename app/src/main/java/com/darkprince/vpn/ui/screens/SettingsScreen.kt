package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import com.darkprince.vpn.data.api.dto.UserDto
import com.darkprince.vpn.data.prefs.AppPrefs
import com.darkprince.vpn.ui.theme.AppPalette
import com.darkprince.vpn.ui.theme.GroupCard
import com.darkprince.vpn.ui.theme.RowDivider
import com.darkprince.vpn.ui.theme.SectionHeader
import com.darkprince.vpn.ui.theme.SettingsRow
import com.darkprince.vpn.ui.theme.ThemePicker

@Composable
fun SettingsScreen(
    user: UserDto?,
    guestMode: Boolean,
    usingSharedSubscription: Boolean,
    onOpenReferral: () -> Unit,
    onOpenApps: () -> Unit,
    autoConnectMode: String,
    onSelectAutoConnect: (String) -> Unit,
    failover: Boolean,
    onToggleFailover: (Boolean) -> Unit,
    killSwitch: Boolean,
    onToggleKillSwitch: (Boolean) -> Unit,
    onOpenVpnSettings: () -> Unit,
    onOpenShare: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenNews: () -> Unit,
    newsUnreadCount: Int = 0,
    supportUnreadCount: Int,
    onCreateAccount: () -> Unit,
    onDropSharedSubscription: () -> Unit,
    onLogout: () -> Unit,
    selectedThemeId: String,
    onSelectTheme: (AppPalette) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))

        // --- Аккаунт ---
        SectionHeader("Аккаунт")
        GroupCard {
            val identity = when {
                guestMode -> "Гостевой доступ по подписке"
                else -> user?.email
                    ?: user?.username?.let { "@$it" }
                    ?: user?.telegramId?.let { "Telegram ID: $it" }
                    ?: "—"
            }
            val hint = when {
                guestMode -> "Оплата и управление подпиской — у владельца"
                usingSharedSubscription ->
                    "Работает чужая подписка. Купите свою — переключится сама"
                else -> user?.firstName
            }
            SettingsRow(
                icon = Icons.Default.VerifiedUser,
                title = identity,
                subtitle = hint,
                showChevron = false,
            )

            if (guestMode) {
                RowDivider()
                SettingsRow(
                    icon = Icons.Default.ShoppingBag,
                    title = "Своя подписка",
                    subtitle = "Зарегистрируйтесь и купите собственную",
                    onClick = onCreateAccount,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // --- Помощь ---
        SectionHeader("Помощь")
        GroupCard {
            // Стоит перед поддержкой намеренно: половина обращений — это то,
            // что человек может выяснить сам за пять секунд.
            SettingsRow(
                icon = Icons.Default.Troubleshoot,
                title = "Почему не работает",
                subtitle = "Проверить подписку, трафик, устройства и сервер",
                onClick = onOpenDiagnostics,
            )

            RowDivider()

            SettingsRow(
                icon = Icons.Default.SupportAgent,
                title = "Техподдержка",
                subtitle = when {
                    supportUnreadCount > 0 -> "Новых ответов: $supportUnreadCount"
                    guestMode -> "Написать нам в Telegram"
                    else -> "Обращения и переписка с поддержкой"
                },
                tint = if (supportUnreadCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                onClick = onOpenSupport,
            )

            // Гостю по чужой ссылке новости не показываем: кабинет отдаёт их
            // только аккаунту, и строка вела бы на объяснение, почему пусто.
            if (!guestMode) {
                RowDivider()
                SettingsRow(
                    icon = Icons.Default.Campaign,
                    title = "Новости",
                    subtitle = if (newsUnreadCount > 0) {
                        "Новых: $newsUnreadCount"
                    } else {
                        "Обновления, работы на серверах, акции"
                    },
                    tint = if (newsUnreadCount > 0) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    onClick = onOpenNews,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // --- Оформление ---
        SectionHeader("Оформление")
        GroupCard {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Тема",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Меняется сразу, перезапускать не нужно",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                ThemePicker(selectedId = selectedThemeId, onSelect = onSelectTheme)
            }
        }

        Spacer(Modifier.height(22.dp))

        // --- Подключение ---
        SectionHeader("Подключение")
        GroupCard {
            SettingsRow(
                icon = Icons.Default.Apps,
                title = "Приложения через VPN",
                subtitle = "Какие идут через VPN, а какие в обход",
                onClick = onOpenApps,
            )

            RowDivider()

            var autoOpen by remember { mutableStateOf(false) }
            SettingsRow(
                icon = Icons.Default.PlayCircle,
                title = "Автоподключение",
                subtitle = autoConnectTitle(autoConnectMode),
                onClick = { autoOpen = true },
            )
            if (autoOpen) {
                AutoConnectDialog(
                    selected = autoConnectMode,
                    onSelect = {
                        onSelectAutoConnect(it)
                        autoOpen = false
                    },
                    onDismiss = { autoOpen = false },
                )
            }

            RowDivider()

            SettingsRow(
                icon = Icons.Default.SwapHoriz,
                title = "Запасной сервер при сбое",
                subtitle = if (failover) {
                    "Если выбранный не отвечает, подключимся к ближайшему живому"
                } else {
                    "Подключаемся только к выбранному серверу"
                },
                showChevron = false,
                onClick = { onToggleFailover(!failover) },
                trailing = {
                    Switch(checked = failover, onCheckedChange = onToggleFailover)
                },
            )

            RowDivider()

            SettingsRow(
                icon = Icons.Default.Shield,
                title = "Блокировать трафик при обрыве",
                subtitle = if (killSwitch) {
                    "Пока туннель поднимается заново, сеть не работает"
                } else {
                    "Сейчас при обрыве трафик идёт напрямую"
                },
                showChevron = false,
                onClick = { onToggleKillSwitch(!killSwitch) },
                trailing = {
                    Switch(checked = killSwitch, onCheckedChange = onToggleKillSwitch)
                },
            )

            if (killSwitch) {
                RowDivider()
                SettingsRow(
                    icon = Icons.Default.Lock,
                    title = "Запретить сеть без VPN",
                    subtitle = "Системная настройка: постоянный VPN и блокировка " +
                        "соединений без него. Включается только вручную",
                    onClick = onOpenVpnSettings,
                )
            }
        }

        if (!guestMode) {
            Spacer(Modifier.height(22.dp))

            // --- Подписка ---
            SectionHeader("Подписка")
            GroupCard {
                SettingsRow(
                    icon = Icons.Default.Devices,
                    title = "Подключённые устройства",
                    subtitle = "Отключите лишние, чтобы освободить лимит",
                    onClick = onOpenDevices,
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Default.QrCode2,
                    title = "Поделиться подпиской",
                    subtitle = "QR-код для близких, без доступа к оплате",
                    onClick = onOpenShare,
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Default.PersonAdd,
                    title = "Пригласить друга",
                    subtitle = "Бонусы с платежей приглашённых",
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = onOpenReferral,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // --- Выход ---
        GroupCard {
            if (!guestMode && usingSharedSubscription) {
                SettingsRow(
                    icon = Icons.Default.QrCode2,
                    title = "Отключить гостевой доступ",
                    subtitle = "Перестать пользоваться чужой подпиской",
                    showChevron = false,
                    onClick = onDropSharedSubscription,
                )
                RowDivider()
            }
            SettingsRow(
                icon = Icons.Default.Logout,
                title = if (guestMode) "Отключить гостевой доступ" else "Выйти из аккаунта",
                tint = MaterialTheme.colorScheme.error,
                showChevron = false,
                onClick = onLogout,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Как назвать выбранный режим одной строкой. */
private fun autoConnectTitle(mode: String): String = when (mode) {
    AppPrefs.AUTO_BOOT -> "При включении телефона"
    AppPrefs.AUTO_NETWORK -> "При включении телефона и появлении сети"
    else -> "Выключено"
}

/**
 * Выбор повода для автоподключения.
 *
 * Поводов ровно два, и второй включает первый: «при появлении сети» без
 * подключения после перезагрузки был бы странным набором. Различать домашнюю
 * сеть и чужую мы не умеем — для этого нужно читать имя сети, а его Android
 * отдаёт только вместе с доступом к местоположению. Просить у людей
 * геолокацию ради этого мы не будем, поэтому и не обещаем.
 */
@Composable
private fun AutoConnectDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        AppPrefs.AUTO_OFF to "Подключаться только вручную",
        AppPrefs.AUTO_BOOT to "После включения телефона",
        AppPrefs.AUTO_NETWORK to "После включения и когда появляется сеть",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Автоподключение") },
        text = {
            Column {
                options.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Приложение не будет спрашивать разрешение на VPN само — " +
                        "если оно ещё не выдано, подключитесь один раз вручную.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
