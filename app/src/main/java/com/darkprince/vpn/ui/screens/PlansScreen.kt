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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

        if (state.loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
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
            val owned = state.isOwned(tariff.id)
            TariffCard(
                tariff = tariff,
                purchasing = state.purchasing,
                owned = owned,
                // лимит именно этого тарифа, а не выбранного в блоке устройств
                currentDeviceLimit = state.deviceLimitByTariff[tariff.id],
                renewalPriceFor = { days -> state.renewalPrice(days) },
                onAction = { period ->
                    if (owned) viewModel.renew(period) else viewModel.purchase(tariff, period)
                },
            )
        }

        state.devices?.let { devices ->
            item {
                DevicesCard(
                    devices = devices,
                    purchasing = state.purchasing || state.devicesLoading,
                    subscriptions = state.subscriptions,
                    selectedSubscription = state.deviceSubscription,
                    onSelectSubscription = { viewModel.selectDeviceSubscription(it) },
                    onBuy = { viewModel.buyDevices(it) },
                    onReduce = { viewModel.reduceDevices(it) },
                )
            }
        }

        if (state.trafficPackages.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Докупить трафик", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        state.trafficPackages.forEach { pkg ->
                            OutlinedButton(
                                onClick = { viewModel.buyTraffic(pkg.gb) },
                                enabled = !state.purchasing,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("+${pkg.gb} ГБ — ${formatKopeks(pkg.priceKopeks)}")
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
private fun DevicesCard(
    devices: com.darkprince.vpn.data.repo.DevicesInfo,
    purchasing: Boolean,
    subscriptions: List<com.darkprince.vpn.data.api.dto.SubscriptionListItem>,
    selectedSubscription: com.darkprince.vpn.data.api.dto.SubscriptionListItem?,
    onSelectSubscription: (Long) -> Unit,
    onBuy: (Int) -> Unit,
    onReduce: (Int) -> Unit,
) {
    var count by remember { mutableStateOf(1) }
    var menuOpen by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Устройства", style = MaterialTheme.typography.titleMedium)

            // при нескольких подписках выбираем, устройствами какой управляем
            if (subscriptions.size > 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Подписка, для которой меняем устройства:",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(selectedSubscription?.displayName ?: "Выберите подписку")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    subscriptions.forEach { sub ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(sub.displayName)
                                    sub.deviceLimit?.let {
                                        Text(
                                            "Лимит: $it",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                menuOpen = false
                                onSelectSubscription(sub.id)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                devices.deviceLimit?.let {
                    Text("Лимит: $it", style = MaterialTheme.typography.bodyMedium)
                }
                devices.connectedCount?.let {
                    Text("Подключено: $it", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))

            // счётчик количества
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedButton(onClick = { if (count > 1) count-- }) { Text("−") }
                Text("$count", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { count++ }) { Text("+") }
            }
            Spacer(Modifier.height(12.dp))

            if (devices.purchaseAvailable) {
                val price = devices.pricePerDeviceKopeks
                Button(
                    onClick = { onBuy(count) },
                    enabled = !purchasing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val target = selectedSubscription?.displayName
                        ?.takeIf { subscriptions.size > 1 }
                    Text(
                        buildString {
                            append("Докупить $count шт.")
                            if (price != null) append(" за ${formatKopeks(price * count)}")
                            if (target != null) append(" — $target")
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            if (devices.reduceAvailable) {
                val limit = devices.deviceLimit
                val newLimit = limit?.let { (it - count).coerceAtLeast(1) }
                OutlinedButton(
                    onClick = { newLimit?.let(onReduce) },
                    enabled = !purchasing && newLimit != null && limit != null && newLimit < limit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (newLimit != null) "Уменьшить лимит до $newLimit"
                        else "Уменьшить лимит"
                    )
                }
            }
        }
    }
}

@Composable
private fun TariffCard(
    tariff: TariffOffer,
    purchasing: Boolean,
    owned: Boolean,
    currentDeviceLimit: Int?,
    renewalPriceFor: (Int) -> Long?,
    onAction: (com.darkprince.vpn.data.repo.PeriodPrice) -> Unit,
) {
    var selectedPeriod by remember(tariff.id) { mutableStateOf(tariff.periods.firstOrNull()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(tariff.name, style = MaterialTheme.typography.titleMedium)
                if (owned) {
                    Text(
                        "Ваш тариф",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
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
                // у купленного тарифа показываем действующий лимит: он может
                // быть больше тарифного, если устройства докупались
                val devices = currentDeviceLimit ?: tariff.deviceLimit
                devices?.let {
                    val extra = tariff.deviceLimit?.takeIf { base -> it > base }
                    Text(
                        if (extra != null) "Устройств: $it (в тарифе $extra)"
                        else "Устройств: $it",
                        style = MaterialTheme.typography.bodySmall,
                    )
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
            val price = period?.let {
                if (owned) renewalPriceFor(it.days) ?: it.priceKopeks else it.priceKopeks
            }
            Button(
                onClick = { period?.let(onAction) },
                enabled = !purchasing && period != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val verb = if (owned) "Продлить" else "Купить"
                Text(if (price != null) "$verb за ${formatKopeks(price)}" else verb)
            }
        }
    }
}
