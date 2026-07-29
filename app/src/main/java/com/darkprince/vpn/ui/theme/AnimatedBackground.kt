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
 * Живой фон приложения: несколько мягких световых пятен в цветах логотипа
 * медленно плывут по экрану. Рисуется на Canvas, поэтому не создаёт
 * дополнительных слоёв композиции и почти не нагружает отрисовку.
 */
@Composable
fun AnimatedBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
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
                drawRect(BrandColors.Background)

                val w = size.width
                val h = size.height
                val radius = maxOf(w, h) * 0.55f * breath

                // золотое пятно — движется по широкой дуге сверху
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(BrandColors.Gold.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(
                            x = w * (0.5f + 0.28f * cos(phase)),
                            y = h * (0.22f + 0.10f * sin(phase)),
                        ),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(
                        x = w * (0.5f + 0.28f * cos(phase)),
                        y = h * (0.22f + 0.10f * sin(phase)),
                    ),
                )

                // холодное свечение — в противофазе, ближе к низу
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(BrandColors.Glow.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(
                            x = w * (0.5f - 0.32f * cos(phase * 0.7f)),
                            y = h * (0.78f + 0.09f * sin(phase * 0.9f)),
                        ),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(
                        x = w * (0.5f - 0.32f * cos(phase * 0.7f)),
                        y = h * (0.78f + 0.09f * sin(phase * 0.9f)),
                    ),
                )
            },
        content = content,
    )
}
