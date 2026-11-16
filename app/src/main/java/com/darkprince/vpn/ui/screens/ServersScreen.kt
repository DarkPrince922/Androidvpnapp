package com.darkprince.vpn.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.core.model.ProxyProfile
import com.darkprince.vpn.ui.theme.AquaIcon
import com.darkprince.vpn.ui.theme.AquaIconView
import com.darkprince.vpn.ui.theme.glass
import com.darkprince.vpn.ui.theme.ink
import com.darkprince.vpn.ui.theme.leadingEmoji
import com.darkprince.vpn.ui.theme.nameWithoutEmoji
import com.darkprince.vpn.ui.vm.HomeUiState
import com.darkprince.vpn.ui.vm.HomeViewModel
import kotlinx.coroutines.delay

@Composable
fun ServersScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    LaunchedEffect(notice) {
        val shown = notice ?: return@LaunchedEffect
        delay(if (shown.ok) 2500 else 4500)
        viewModel.consumeNotice()
    }

    ServersContent(
        state = state,
        notice = notice,
        onSelect = viewModel::selectServer,
        onPing = viewModel::pingAll,
        onRefresh = { viewModel.refresh(forceServers = true, notify = true) },
    )
}

/** Экран без зависимости от модели: его же рисует отладочный просмотр. */
@Composable
fun ServersContent(
    state: HomeUiState,
    notice: com.darkprince.vpn.ui.vm.Notice?,
    onSelect: (Int) -> Unit,
    onPing: () -> Unit,
    onRefresh: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 14.dp, top = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Серверы", style = MaterialTheme.typography.displaySmall, color = scheme.onBackground)
                Text(
                    "Пинг напрямую, без VPN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(
                    onClick = onPing,
                    description = "Проверить пинг",
                ) {
                    if (state.pinging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 1.6.dp,
                            color = scheme.primary,
                        )
                    } else {
                        AquaIconView(AquaIcon.Speed, scheme.onBackground.copy(alpha = 0.8f), size = 20.dp, stroke = 1.5f)
                    }
                }
                Spacer(Modifier.width(8.dp))
                GlassIconButton(onClick = onRefresh, description = "Обновить список") {
                    AquaIconView(AquaIcon.Refresh, scheme.onBackground.copy(alpha = 0.8f), size = 20.dp, stroke = 1.5f)
                }
            }
        }

        if (state.servers.isEmpty()) {
            Text(
                "Список серверов пуст. Проверьте подписку.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp, top = 18.dp,
                    bottom = 16.dp + com.darkprince.vpn.ui.theme.LocalBottomInset.current,
                ),
            ) {
                // Все узлы — одной стеклянной группой, как в референсе; ключ с
                // номером: два узла с одним именем и адресом не должны ронять список.
                item {
                    Column(Modifier.fillMaxWidth().glass()) {
                        state.servers.forEachIndexed { index, server ->
                            if (index > 0) {
                                HorizontalDivider(color = ink(0.07f), thickness = 1.dp)
                            }
                            ServerRow(
                                server = server,
                                selected = index == state.selectedServer,
                                ping = state.pings[server.key],
                                pinging = state.pinging,
                                onClick = { onSelect(index) },
                            )
                        }
                    }
                }
            }
        }
    }
    // тот же итог обновления, что и на главной: всплывает сверху
    NoticeToast(notice, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
    }
}

@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    description: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .glass(RoundedCornerShape(22.dp))
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Строка сервера в стеклянной группе: плитка с флагом, название, подпись
 * из меток транспорта и задержка справа. Выбранный узел подсвечен аквой
 * слева направо и полоской у края, цифра у него акцентная.
 */
@Composable
internal fun ServerRow(
    server: ProxyProfile,
    selected: Boolean,
    ping: Long?,
    onClick: () -> Unit,
    pinging: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    val glow by animateColorAsState(
        targetValue = if (selected) Color(0xFF00D6C4) else Color.Transparent,
        animationSpec = tween(250),
        label = "serverGlow",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(glow.copy(alpha = glow.alpha * 0.16f), glow.copy(alpha = glow.alpha * 0.03f))
                )
            )
            .drawBehind {
                if (selected) drawRect(accent, size = Size(3.dp.toPx(), size.height), topLeft = Offset.Zero)
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ink(0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(leadingEmoji(server.name) ?: "🌐", fontSize = 22.sp)
        }
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                nameWithoutEmoji(server.name),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.5.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // подпись узла из панели Remnawave, если её там задали, иначе — метки
            val tags = server.serverDescription ?: server.transportParts.joinToString(" · ").uppercase()
            Text(
                tags,
                fontSize = 11.sp,
                letterSpacing = 0.44.sp,
                color = scheme.onBackground.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))

        val unit = scheme.onBackground.copy(alpha = 0.5f)
        when {
            ping != null && ping < 0 -> Text(
                "нет",
                fontSize = 17.sp,
                fontWeight = FontWeight.Light,
                color = scheme.error,
                maxLines = 1,
            )
            ping != null -> Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    ping.toString(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Light,
                    color = if (selected) accent else scheme.onBackground,
                    maxLines = 1,
                )
                Text(
                    " мс",
                    fontSize = 11.sp,
                    color = if (selected) accent.copy(alpha = 0.7f) else unit,
                    modifier = Modifier.padding(bottom = 2.dp),
                    maxLines = 1,
                )
            }
            pinging -> CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = unit,
            )
        }
    }
}
