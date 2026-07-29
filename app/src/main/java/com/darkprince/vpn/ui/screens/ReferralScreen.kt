package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.darkprince.vpn.data.api.dto.ReferralInfoResponse
import com.darkprince.vpn.di.ServiceLocator

@Composable
fun ReferralScreen(onShare: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current

    val info by produceState<Result<ReferralInfoResponse>?>(initialValue = null) {
        value = try {
            Result.success(ServiceLocator.balanceRepository.referralInfo())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Пригласить друга", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        when {
            info == null -> CircularProgressIndicator()
            info?.isFailure == true -> Text(
                "Реферальная программа недоступна",
                color = MaterialTheme.colorScheme.error,
            )
            else -> {
                val data = info!!.getOrThrow()
                data.commissionPercent?.let {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Приглашайте друзей и получайте ${it.toInt()}% с их платежей на баланс",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Приглашено", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "${data.totalReferrals ?: 0}",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("С подпиской", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "${data.activeReferrals ?: 0}",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Заработано", style = MaterialTheme.typography.labelMedium)
                            Text(
                                formatKopeks(data.totalEarningsKopeks ?: 0),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                val link = data.shareLink
                if (link != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Ваша ссылка", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(link, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            onShare(
                                "Попробуй наш VPN — быстрый и надёжный. Подключайся: $link"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Поделиться ссылкой")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(link)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Скопировать")
                    }
                } else {
                    Text("Ссылка недоступна", color = MaterialTheme.colorScheme.error)
                }

                data.referralCode?.let { code ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Код для регистрации по почте: $code",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
