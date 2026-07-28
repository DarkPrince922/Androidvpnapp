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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.ui.vm.BalanceViewModel
import java.util.Locale

fun formatKopeks(kopeks: Long): String =
    String.format(Locale.getDefault(), "%.2f ₽", kopeks / 100.0)

@Composable
fun BalanceScreen(viewModel: BalanceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var amount by rememberSaveable { mutableStateOf("300") }
    var selectedMethod by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Баланс и оплата", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Баланс", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.balanceKopeks?.let { formatKopeks(it) } ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Пополнить", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Сумма, ₽") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(12.dp))
                    if (state.methods.isEmpty() && !state.loading) {
                        Text(
                            "Способы оплаты недоступны",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.methods.take(4).forEach { method ->
                            FilterChip(
                                selected = selectedMethod == method.effectiveId,
                                onClick = { selectedMethod = method.effectiveId },
                                label = { Text(method.effectiveName) },
                            )
                        }
                    }
                    if (state.methods.size > 4) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.methods.drop(4).forEach { method ->
                                FilterChip(
                                    selected = selectedMethod == method.effectiveId,
                                    onClick = { selectedMethod = method.effectiveId },
                                    label = { Text(method.effectiveName) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val method = state.methods.firstOrNull { it.effectiveId == selectedMethod }
                                ?: state.methods.firstOrNull()
                            val rubles = amount.toLongOrNull() ?: 0
                            if (method != null) viewModel.topup(rubles, method)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.creatingPayment && state.methods.isNotEmpty() &&
                            (amount.toLongOrNull() ?: 0) > 0,
                    ) {
                        Text(if (state.creatingPayment) "Создание платежа…" else "Перейти к оплате")
                    }
                    if (state.pendingPayment != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.checkPending() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Проверить оплату")
                        }
                    }
                }
            }
        }
        state.error?.let { error ->
            item {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
        state.info?.let { info ->
            item {
                Text(info, color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            Text("История операций", style = MaterialTheme.typography.titleMedium)
        }
        if (state.transactions.isEmpty()) {
            item {
                Text("Операций пока нет", style = MaterialTheme.typography.bodySmall)
            }
        }
        items(state.transactions) { tx ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            tx.description ?: tx.type ?: "Операция",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        tx.createdAt?.let {
                            Text(it.take(19).replace('T', ' '), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    tx.amountKopeks?.let {
                        Text(formatKopeks(it), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
