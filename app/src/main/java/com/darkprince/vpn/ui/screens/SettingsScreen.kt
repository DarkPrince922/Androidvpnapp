package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkprince.vpn.data.api.dto.UserDto

@Composable
fun SettingsScreen(
    user: UserDto?,
    baseUrl: String,
    guestMode: Boolean,
    onOpenReferral: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenShare: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Аккаунт", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                val identity = when {
                    guestMode -> "Гостевой доступ по подписке"
                    else -> user?.email
                        ?: user?.username?.let { "@$it" }
                        ?: user?.telegramId?.let { "Telegram ID: $it" }
                        ?: "—"
                }
                Text(identity, style = MaterialTheme.typography.titleMedium)
                if (guestMode) {
                    Text(
                        "Оплата и управление подпиской доступны у её владельца",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    user?.firstName?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Адрес кабинета", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(baseUrl.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenApps() },
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Приложения через VPN", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Выберите, какие приложения идут через VPN, а какие в обход",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (!guestMode) {
            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenShare() },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Поделиться подпиской", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "QR-код для близких: они подключатся к вашей подписке без доступа к оплате",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenReferral() },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Пригласить друга", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Делитесь ссылкой и получайте бонусы с платежей приглашённых",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text(if (guestMode) "Отключить гостевой доступ" else "Выйти из аккаунта")
        }
    }
}
