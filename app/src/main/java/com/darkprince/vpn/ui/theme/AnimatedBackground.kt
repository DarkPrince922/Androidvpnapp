package com.darkprince.vpn.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * Живой фон приложения: холодное сияние сверху и второе, слабее, у правого
 * края. Оба чуть дышат и покачиваются. Рисуется на Canvas, поэтому не создаёт
 * дополнительных слоёв композиции и почти не нагружает отрисовку.
 *
 * Раньше по экрану плыли два пятна — золотое и синее. Золото, размазанное по
 * тёмно-синему фону, давало бурую муть по краям. Теперь фон почти чёрный,
 * холодный свет едва заметен, а золото осталось там, где оно что-то значит:
 * в кольце кнопки подключения и её отсвете.
 */
@Composable
fun AnimatedBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "background")

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val breath by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(palette.background)

                val w = size.width
                val h = size.height
                // верхнее сияние — широкое, центр чуть выше края экрана
                val topRadius = maxOf(w, h) * 0.62f * breath
                val top = Offset(x = w * (0.5f + 0.08f * cos(phase)), y = -h * 0.04f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.glowB.copy(alpha = palette.glowAlpha * 0.8f), Color.Transparent),
                        center = top,
                        radius = topRadius,
                    ),
                    radius = topRadius,
                    center = top,
                )

                // боковое — слабее, у правого края ниже середины
                val sideRadius = maxOf(w, h) * 0.42f
                val side = Offset(x = w * 1.02f, y = h * (0.62f + 0.05f * sin(phase)))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.glowB.copy(alpha = palette.glowAlpha * 0.4f), Color.Transparent),
                        center = side,
                        radius = sideRadius,
                    ),
                    radius = sideRadius,
                    center = side,
                )
            },
        content = content,
    )
}
