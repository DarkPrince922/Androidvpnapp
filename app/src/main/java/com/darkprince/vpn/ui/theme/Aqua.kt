package com.darkprince.vpn.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * «Аква»: общие кирпичики — стекло, тонкие линейные значки и нижняя панель.
 */

/**
 * Цвет текста и линий поверх стекла с заданной прозрачностью. На тёмных темах
 * это почти белый, на светлых — тёмно-синий: белый на светлом не читается.
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun ink(alpha: Float): Color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha)

/**
 * Матовое стекло: заливка и контур из темы. Настоящего размытия того, что
 * лежит под карточкой, нет — и не нужно: под ней мягкие пятна фона, а размыть
 * размытое незачем.
 */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(28.dp)): Modifier {
    val palette = LocalPalette.current
    return this
        .clip(shape)
        .background(palette.glassFill)
        .border(1.dp, palette.glassBorder, shape)
}

/**
 * Тонкие линейные значки. Все нарисованы в сетке 24×24 одним контуром и
 * красятся цветом текста; толщину линии задаёт место: на нижней панели она
 * 1,5, на крупном диске подключения — 1,2.
 */
enum class AquaIcon(val d: String) {
    Home("M4 11l8-7 8 7M6 10v9h12v-9"),
    Servers(
        "M6 4H18a2 2 0 0 1 2 2V8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z" +
            "M6 14H18a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V16a2 2 0 0 1 2-2Z" +
            "M8 7h.01M8 17h.01"
    ),
    Cart("M4 5h2l2 11h10l2-8H7M8.5 19.5a1 1 0 1 0 2 0a1 1 0 1 0-2 0M16 19.5a1 1 0 1 0 2 0a1 1 0 1 0-2 0"),
    Wallet("M6 6H18a3 3 0 0 1 3 3V16a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3ZM16 12.5h2M3 10h18"),
    Gear(
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0M12 3v3M12 18v3M3 12h3M18 12h3" +
            "M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"
    ),
    Shield("M12 3l7 3v5c0 5-3 8-7 10-4-2-7-5-7-10V6z"),
    ShieldCheck("M12 3l7 3v5c0 5-3 8-7 10-4-2-7-5-7-10V6zM9 12l2.2 2.2L15.5 10"),
    Power("M18.36 6.64a9 9 0 1 1-12.73 0M12 2v10"),
    Check("M5 12.5l4.5 4.5L19 7.5"),
    Chevron("M9 5l7 7-7 7"),
    Down("M12 4v16M5 13l7 7 7-7"),
    Up("M12 20V4M5 11l7-7 7 7"),
    Bolt("M13 3L5 13.5h6L10 21l8-10.5h-6z"),
    Search("M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0-13 0M16 16l4.5 4.5"),
    Refresh("M20 12a8 8 0 1 1-2.3-5.6M20 4v5h-5"),
    Speed("M4 16a8 8 0 1 1 16 0M12 16l4-5"),
    More("M5 12h.01M12 12h.01M19 12h.01");

    fun vector(stroke: Float): ImageVector =
        cache.getOrPut(this to stroke) {
            ImageVector.Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ).build()
        }

    private companion object {
        val cache = ConcurrentHashMap<Pair<AquaIcon, Float>, ImageVector>()
    }
}

@Composable
fun AquaIconView(
    icon: AquaIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    stroke: Float = 1.5f,
) {
    Icon(
        imageVector = icon.vector(stroke),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}

data class AquaNavItem(val route: String, val label: String, val icon: AquaIcon)

/**
 * Нижняя панель: плавающая стеклянная таблетка. Выбранный раздел лежит на
 * светлой «пузырьковой» подложке, которая плавно переезжает от раздела к
 * разделу; значок и подпись перекрашиваются от того же числа, что и движение.
 * Подключены — выбранный раздел аквамариновый, нет — белый.
 */
@Composable
fun AquaNavBar(
    items: List<AquaNavItem>,
    selectedIndex: Int,
    connected: Boolean,
    onSelect: (AquaNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val scheme = MaterialTheme.colorScheme
    val position by animateFloatAsState(
        targetValue = selectedIndex.coerceAtLeast(0).toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "navBubble",
    )
    val activeColor = if (connected || palette.isLight) scheme.primary else scheme.onBackground
    val bubble = if (palette.isLight) scheme.primary.copy(alpha = 0.12f) else ink(0.12f)
    val inactive = scheme.onSurfaceVariant

    val islandShape = RoundedCornerShape(35.dp)
    // Остров должен читаться как отдельный предмет, а не как полоса внизу:
    // плотная подложка под стеклом, мягкая тень и воздух вокруг.
    val base = if (palette.isLight) Color.White.copy(alpha = 0.94f) else palette.panel.copy(alpha = 0.92f)
    Box(
        modifier = modifier
            // системные кнопки навигации рисуются поверх приложения
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(70.dp)
            .shadow(
                elevation = 18.dp,
                shape = islandShape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            )
            .clip(islandShape)
            .background(base)
            .glass(islandShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp)
                .drawBehind {
                    if (items.isEmpty()) return@drawBehind
                    val cell = size.width / items.size
                    val h = 54.dp.toPx()
                    drawRoundRect(
                        color = bubble,
                        topLeft = Offset(position * cell, (size.height - h) / 2f),
                        size = Size(cell, h),
                        cornerRadius = CornerRadius(h / 2f),
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val near = (1f - abs(position - index)).coerceIn(0f, 1f)
                val color = lerp(inactive, activeColor, near)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        // без волны нажатия: прямоугольная вспышка рядом с
                        // круглой подложкой выглядела бы грубо
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(item) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    AquaIconView(item.icon, color, size = 22.dp, stroke = 1.4f)
                    // Подпись не растёт вслед за системным масштабом шрифта дальше
                    // 1,1: при шести пунктах (у админа) крупная подпись не влезала.
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.1f))
                    ) {
                        Text(
                            item.label,
                            color = color,
                            fontSize = if (items.size >= 6) 9.sp else 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}
