package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.ui.theme.BrandColors
import com.darkprince.vpn.ui.vm.Check
import com.darkprince.vpn.ui.vm.CheckResult
import com.darkprince.vpn.ui.vm.DiagnosticsViewModel

/**
 * «Почему не работает».
 *
 * Экран отвечает на единственный вопрос, с которым люди приходят в
 * поддержку. Поэтому наверху не список проверок, а вывод: что именно
 * мешает и что с этим делать. Проверки ниже — для тех, кому нужны
 * подробности, и для журнала, если писать в поддержку всё-таки придётся.
 */
@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Проверка подключения", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Смотрим по порядку то, из-за чего VPN обычно показывает " +
                "«подключено», а интернета нет.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.running) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    state.verdict,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.checks) { check -> CheckRow(check) }

            item {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = viewModel::run,
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Проверить ещё раз") }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Если здесь всё в порядке, а интернета всё равно нет — " +
                        "напишите в поддержку из раздела «Ещё». К обращению " +
                        "приложится журнал, по нему причина обычно видна сразу.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CheckRow(check: Check) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, tint(check.result).copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    check.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    check.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = tint(check.result),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            // Совет показываем только там, где есть что делать: под каждой
            // зелёной строкой он превратился бы в шум и перестал читаться.
            check.advice?.takeIf { check.result != CheckResult.OK }?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun tint(result: CheckResult): Color = when (result) {
    CheckResult.OK -> BrandColors.Success
    CheckResult.WARN -> BrandColors.Warning
    CheckResult.FAIL -> BrandColors.Danger
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
