package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Первый запуск: адрес Cabinet API (кабинета) сервиса. */
@Composable
fun SetupScreen(
    initialUrl: String,
    onContinue: (String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Подключение к сервису", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Укажите адрес личного кабинета вашего VPN-сервиса",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("https://cabinet.example.com/api") },
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onContinue(url) },
            modifier = Modifier.fillMaxWidth(),
            enabled = url.startsWith("http"),
        ) {
            Text("Продолжить")
        }
    }
}
