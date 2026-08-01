package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkprince.vpn.data.api.dto.UserDto
import com.darkprince.vpn.ui.theme.BrandColors
import com.darkprince.vpn.ui.theme.GroupCard
import com.darkprince.vpn.ui.theme.RowDivider
import com.darkprince.vpn.ui.theme.SectionHeader
import com.darkprince.vpn.ui.theme.SettingsRow

@Composable
fun SettingsScreen(
    user: UserDto?,
    guestMode: Boolean,
    usingSharedSubscription: Boolean,
    onOpenReferral: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenShare: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenSupport: () -> Unit,
    supportUnreadCount: Int,
    onCreateAccount: () -> Unit,
    onDropSharedSubscription: () -> Unit,
    onLogout: () -> Unit,
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
            SettingsRow(
                icon = Icons.Default.SupportAgent,
                title = "Техподдержка",
                subtitle = when {
                    supportUnreadCount > 0 -> "Новых ответов: $supportUnreadCount"
                    guestMode -> "Написать нам в Telegram"
                    else -> "Обращения и переписка с поддержкой"
                },
                tint = if (supportUnreadCount > 0) BrandColors.Glow else MaterialTheme.colorScheme.primary,
                onClick = onOpenSupport,
            )
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
                    tint = BrandColors.Glow,
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
