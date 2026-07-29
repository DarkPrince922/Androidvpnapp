package com.darkprince.vpn.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.ui.vm.AppsViewModel
import com.darkprince.vpn.ui.vm.SplitMode

@Composable
fun AppsScreen(viewModel: AppsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Приложения", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SplitMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { viewModel.setMode(mode) },
                    label = {
                        Text(
                            when (mode) {
                                SplitMode.ALL -> "Все"
                                SplitMode.ONLY_SELECTED -> "Только выбранные"
                                SplitMode.EXCEPT_SELECTED -> "Кроме выбранных"
                            }
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(state.mode.hint, style = MaterialTheme.typography.bodySmall)

        if (state.mode != SplitMode.ALL) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.setQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Поиск приложения") },
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Выбрано: ${state.selected.size}",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { viewModel.clearSelection() }) { Text("Сбросить") }
            }

            if (state.mode == SplitMode.ONLY_SELECTED && state.selected.isEmpty()) {
                Text(
                    "Пока ничего не выбрано — через VPN пойдёт весь трафик.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))
            if (state.loading) {
                CircularProgressIndicator()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.visibleApps, key = { it.packageName }) { app ->
                        val icon = remember(app.packageName) {
                            app.icon?.let { drawable ->
                                try {
                                    drawable.toBitmap(96, 96).asImageBitmap()
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggle(app.packageName) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (icon != null) {
                                Image(
                                    bitmap = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                )
                            } else {
                                Spacer(Modifier.size(40.dp))
                            }
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Checkbox(
                                checked = app.packageName in state.selected,
                                onCheckedChange = { viewModel.toggle(app.packageName) },
                            )
                        }
                    }
                }
            }
        }
    }
}
