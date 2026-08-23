package com.darkprince.vpn.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.darkprince.vpn.core.qr.QrUtils
import com.darkprince.vpn.data.api.dto.SubscriptionListItem
import com.darkprince.vpn.di.ServiceLocator

private data class ShareTarget(
    val label: String,
    val url: String,
)

@Composable
fun ShareSubscriptionScreen(
    onShareLink: (String) -> Unit,
    onShareImage: (Bitmap, String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    // варианты для шаринга: каждая подписка со своей ссылкой
    val targets by produceState<List<ShareTarget>?>(initialValue = null) {
        val repo = ServiceLocator.subscriptionRepository
        // не смогли спросить — тот же путь, что и «подписок нет»: ниже есть
        // запасной вариант с единственной ссылкой
        val subs: List<SubscriptionListItem> = repo.subscriptions().orEmpty()
        value = if (subs.isNotEmpty()) {
            subs.filter { !it.subscriptionUrl.isNullOrBlank() }
                .map { ShareTarget(it.displayName, it.subscriptionUrl!!) }
        } else {
            // мультитариф выключен — делимся единственной подпиской
            repo.resolveSubscriptionUrl()?.let { listOf(ShareTarget("Моя подписка", it)) }
                ?: emptyList()
        }
    }

    var selectedIndex by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Поделиться подпиской", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Покажите QR-код тому, кому даёте доступ, или отправьте картинку. " +
                "Он сможет пользоваться VPN, но не увидит баланс, оплату и ваш аккаунт.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))

        val list = targets
        when {
            list == null -> CircularProgressIndicator()
            list.isEmpty() -> Text(
                "Нет активной подписки, которой можно поделиться",
                color = MaterialTheme.colorScheme.error,
            )
            else -> {
                val target = list.getOrElse(selectedIndex) { list.first() }

                // выбор подписки, если их несколько
                if (list.size > 1) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { menuOpen = true },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Какой подпиской делимся", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(target.label, style = MaterialTheme.typography.titleMedium)
                            }
                            Icon(Icons.Default.ExpandMore, contentDescription = "Выбрать подписку")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            list.forEachIndexed { index, item ->
                                DropdownMenuItem(
                                    text = { Text(item.label) },
                                    onClick = {
                                        selectedIndex = index
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                val qr = remember(target.url) { QrUtils.encode(target.url) }
                if (qr != null) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = "QR-код подписки",
                        modifier = Modifier.size(260.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { onShareImage(qr, target.label) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Отправить картинкой")
                    }
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedButton(
                    onClick = { onShareLink(target.url) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Отправить ссылкой")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(target.url)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Скопировать ссылку")
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Каждое устройство занимает место в лимите устройств вашего тарифа.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
