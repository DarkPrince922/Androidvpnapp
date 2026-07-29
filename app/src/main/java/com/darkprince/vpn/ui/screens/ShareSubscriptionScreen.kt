package com.darkprince.vpn.ui.screens

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.darkprince.vpn.di.ServiceLocator
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** QR-код из ссылки подписки — для передачи доступа второму устройству. */
private fun qrBitmap(text: String, size: Int = 720): Bitmap? = try {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until size) {
            for (y in 0 until size) {
                setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
    }
} catch (_: Exception) {
    null
}

@Composable
fun ShareSubscriptionScreen(onShare: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current

    val link by produceState<String?>(initialValue = null) {
        value = try {
            ServiceLocator.subscriptionRepository.resolveSubscriptionUrl()
        } catch (_: Exception) {
            null
        }
    }

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
            "Покажите этот QR-код тому, кому даёте доступ: он отсканирует его при " +
                "входе в приложение и сможет пользоваться VPN. Баланс, оплата и ваш " +
                "аккаунт останутся недоступны.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))

        val url = link
        when {
            url == null -> CircularProgressIndicator()
            else -> {
                val bitmap = qrBitmap(url)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR-код подписки",
                        modifier = Modifier.size(260.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Ссылка подписки", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(url, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onShare(url) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Отправить ссылку")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(url)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Скопировать")
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Каждое устройство считается отдельно и занимает место в лимите " +
                        "устройств вашего тарифа.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
