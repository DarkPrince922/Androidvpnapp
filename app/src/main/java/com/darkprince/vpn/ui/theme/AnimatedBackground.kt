package com.darkprince.vpn.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.darkprince.vpn.vpn.VpnState
import com.darkprince.vpn.vpn.VpnStateStore

/**
 * Насколько пролистан главный экран, в пикселях. Фон читает это значение
 * только при рисовании, поэтому прокрутка не перекомпонует ничего, кроме
 * самой картинки.
 */
object BackgroundScroll {
    val offset = androidx.compose.runtime.mutableFloatStateOf(0f)
}

/**
 * Фон «Аквы»: три мягких цветных пятна на тёмной основе, как водяные
 * разводы. Цвет живёт вместе с VPN: пока он выключен, пятна холодные и
 * серые, при подключении оживают и становятся аквамариновыми и синими;
 * переход плавный.
 *
 * Пятно — большой круг, размытый на 56 dp. Размытие здесь не вычисляется:
 * оно заменено радиальным градиентом с теми же значениями на расстояниях,
 * которые дало бы размытие, — выходит тот же мягкий край и ноль нагрузки.
 */
@Composable
fun AnimatedBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val vpnState by VpnStateStore.state.collectAsState()
    val live by animateFloatAsState(
        targetValue = if (vpnState == VpnState.CONNECTED) 1f else 0f,
        animationSpec = tween(900),
        label = "backgroundLive",
    )

    // Без подключения пятна серо-голубые; на светлых темах — те же цвета,
    // что и с подключением, но вдвое бледнее: серый на светлом выглядит грязью.
    val offColors = listOf(Color(0xFF6E8CA0), Color(0xFF5A6E96), Color(0xFF5A8296))
    val onColors = listOf(palette.glowA, palette.glowB, palette.glowC)
    val onAlphas = listOf(1f, 0.8f, 0.6f).map { it * palette.glowAlpha }
    val offAlphas = if (palette.isLight) onAlphas.map { it * 0.5f } else listOf(0.20f, 0.16f, 0.14f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(palette.background)
                val w = size.width
                val h = size.height
                // Параллакс: пятна едут вверх вслед за прокруткой, но медленнее
                // самого экрана; верхнее тает, нижнее чуть крепнет — свет
                // «стекает» к списку.
                val scroll = BackgroundScroll.offset.floatValue
                val progress = (scroll / (h * 0.8f)).coerceIn(0f, 1f)
                val blobs = listOf(
                    Triple(Offset(50.dp.toPx(), h * 0.100f - scroll * 0.20f), 150.dp.toPx(), 0),
                    Triple(Offset(w - 35.dp.toPx(), h * 0.519f - scroll * 0.12f), 165.dp.toPx(), 1),
                    Triple(Offset(80.dp.toPx(), h * 0.963f - scroll * 0.06f), 140.dp.toPx(), 2),
                )
                val weight = floatArrayOf(1f - 0.65f * progress, 1f - 0.2f * progress, 1f + 0.35f * progress)
                for ((center, core, i) in blobs) {
                    val color = if (palette.isLight) onColors[i] else lerp(offColors[i], onColors[i], live)
                    val alpha = (offAlphas[i] + (onAlphas[i] - offAlphas[i]) * live) * weight[i]
                    blob(center, core, color, alpha)
                }
            },
        content = content,
    )
}

/**
 * Пятно: круг радиуса [core], размытый с σ = 56 dp. У такого размытия край
 * круга светит вполовину, у центра — почти полностью, а за двумя σ от края
 * гаснет; пять опорных точек градиента повторяют эту кривую.
 */
private fun DrawScope.blob(center: Offset, core: Float, color: Color, alpha: Float) {
    val sigma = 56.dp.toPx()
    val reach = core + 2f * sigma
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = alpha * 0.97f),
            (core * 0.78f) / reach to color.copy(alpha = alpha * 0.92f),
            core / reach to color.copy(alpha = alpha * 0.50f),
            (core + sigma) / reach to color.copy(alpha = alpha * 0.16f),
            1f to Color.Transparent,
            center = center,
            radius = reach,
        ),
        radius = reach,
        center = center,
    )
}
