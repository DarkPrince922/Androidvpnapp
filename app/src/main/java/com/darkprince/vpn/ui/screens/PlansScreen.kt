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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.data.repo.TariffOffer
import com.darkprince.vpn.ui.vm.PlansViewModel

@Composable
fun PlansScreen(viewModel: PlansViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Тарифы", style = MaterialTheme.typography.headlineSmall)
        }

        if (state.trialAvailable) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Пробный период", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.activateTrial() },
                            enabled = !state.purchasing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Активировать пробный период")
                        }
                    }
                }
            }
        }

        items(state.tariffs) { tariff ->
            TariffCard(tariff, purchasing = state.purchasing) { period ->
                viewModel.purchase(tariff, period)
            }
        }

        if (state.renewalOptions.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Продление подписки", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        state.renewalOptions.forEach { option ->
                            OutlinedButton(
                                onClick = { viewModel.renew(option) },
                                enabled = !state.purchasing,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("${option.days} дн. — ${formatKopeks(option.priceKopeks)}")
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        state.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        state.info?.let { info ->
            item { Text(info, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun TariffCard(
    tariff: TariffOffer,
    purchasing: Boolean,
    onBuy: (com.darkprince.vpn.data.repo.PeriodPrice) -> Unit,
) {
    var selectedPeriod by remember(tariff.id) { mutableStateOf(tariff.periods.firstOrNull()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(tariff.name, style = MaterialTheme.typography.titleMedium)
            tariff.description?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                tariff.trafficLimitGb?.let {
                    Text(
                        if (it <= 0) "Безлимитный трафик" else "$it ГБ",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                tariff.deviceLimit?.let {
                    Text("Устройств: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tariff.periods.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text("${period.days} дн.") },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val period = selectedPeriod
            Button(
                onClick = { period?.let(onBuy) },
                enabled = !purchasing && period != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (period != null) "Купить за ${formatKopeks(period.priceKopeks)}"
                    else "Купить"
                )
            }
        }
    }
}
