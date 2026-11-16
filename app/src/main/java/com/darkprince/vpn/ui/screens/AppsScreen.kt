package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.ui.theme.BrandColors
import com.darkprince.vpn.ui.theme.GlassSearchField
import com.darkprince.vpn.ui.theme.LocalPalette
import com.darkprince.vpn.ui.theme.glass
import com.darkprince.vpn.ui.theme.ink
import com.darkprince.vpn.ui.vm.AppsViewModel
import com.darkprince.vpn.ui.vm.SplitMode

@Composable
fun AppsScreen(viewModel: AppsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val glassFill = LocalPalette.current.glassFill

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(start = 4.dp, top = 12.dp)) {
            Text("Приложения", style = MaterialTheme.typography.displaySmall)
            Text(
                "Какие идут через VPN, а какие в обход",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        // режим — одной капсулой на три части
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(20.dp))
                .padding(4.dp),
        ) {
            SplitMode.entries.forEach { mode ->
                val selected = state.mode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) scheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) scheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                            RoundedCornerShape(16.dp),
                        )
                        .clickable { viewModel.setMode(mode) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when (mode) {
                            SplitMode.ALL -> "Все"
                            SplitMode.ONLY_SELECTED -> "Выбранные"
                            SplitMode.EXCEPT_SELECTED -> "Кроме"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        color = if (selected) scheme.primary else ink(0.6f),
                    )
                }
            }
        }
        Text(
            state.mode.hint,
            fontSize = 12.5.sp,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, top = 10.dp),
        )

        if (state.mode != SplitMode.ALL) {
            Spacer(Modifier.height(12.dp))
            GlassSearchField(
                value = state.query,
                onValueChange = { viewModel.setQuery(it) },
                placeholder = "Поиск приложения",
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Выбрано: ${state.selected.size}",
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Сбросить",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.clearSelection() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // Пустой выбор в режиме «только выбранные» — не ошибка, а предупреждение
            if (state.mode == SplitMode.ONLY_SELECTED && state.selected.isEmpty()) {
                Text(
                    "Отмеченных нет — через VPN пойдёт весь трафик",
                    fontSize = 12.5.sp,
                    color = BrandColors.Warning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BrandColors.Warning.copy(alpha = 0.10f))
                        .border(1.dp, BrandColors.Warning.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            if (state.loading) {
                // серые «скелеты» строк вместо пустого крутящегося круга
                Column(Modifier.fillMaxWidth().glass()) {
                    repeat(6) { index ->
                        if (index > 0) androidx.compose.material3.HorizontalDivider(color = ink(0.07f))
                        SkeletonAppRow()
                    }
                }
            } else {
                val apps = state.visibleApps
                LazyColumn(modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                    items(apps.size, key = { apps[it].packageName }) { index ->
                        val app = apps[index]
                        val icon = remember(app.packageName) {
                            app.icon?.let { drawable ->
                                try {
                                    drawable.toBitmap(96, 96).asImageBitmap()
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        }
                        // одна «плита»: у первой строки скруглён верх, у последней — низ
                        val shape = RoundedCornerShape(
                            topStart = if (index == 0) 24.dp else 0.dp,
                            topEnd = if (index == 0) 24.dp else 0.dp,
                            bottomStart = if (index == apps.lastIndex) 24.dp else 0.dp,
                            bottomEnd = if (index == apps.lastIndex) 24.dp else 0.dp,
                        )
                        Column(Modifier.clip(shape).background(glassFill)) {
                            if (index > 0) androidx.compose.material3.HorizontalDivider(color = ink(0.07f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggle(app.packageName) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (icon != null) {
                                    Image(
                                        bitmap = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
                                    )
                                } else {
                                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ink(0.08f)))
                                }
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        app.label,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        app.packageName,
                                        fontSize = 11.sp,
                                        color = ink(0.4f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Switch(
                                    checked = app.packageName in state.selected,
                                    onCheckedChange = { viewModel.toggle(app.packageName) },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = scheme.primary,
                                        checkedThumbColor = scheme.onPrimary,
                                        checkedBorderColor = Color.Transparent,
                                        uncheckedTrackColor = ink(0.14f),
                                        uncheckedThumbColor = ink(0.7f),
                                        uncheckedBorderColor = Color.Transparent,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonAppRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ink(0.07f)))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.5f).height(13.dp).clip(RoundedCornerShape(7.dp)).background(ink(0.09f)))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.7f).height(10.dp).clip(RoundedCornerShape(6.dp)).background(ink(0.06f)))
        }
    }
}
