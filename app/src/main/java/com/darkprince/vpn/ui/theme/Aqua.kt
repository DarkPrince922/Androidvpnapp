package com.darkprince.vpn.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
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
    More("M5 12h.01M12 12h.01M19 12h.01"),
    Alert("M12 6v7M12 17.5h.01"),
    Phone("M8 3h8a1.5 1.5 0 0 1 1.5 1.5v15A1.5 1.5 0 0 1 16 21H8a1.5 1.5 0 0 1-1.5-1.5v-15A1.5 1.5 0 0 1 8 3ZM11 18h2"),
    Backspace("M20 6H9l-6 6 6 6h11a1 1 0 0 0 1-1V7a1 1 0 0 0-1-1ZM15 10l-4 4M11 10l4 4");

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

/** Сколько места внизу экрана занимает плавающая панель: списки оставляют его пустым в конце. */
val LocalBottomInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** Экран может спрятать панель (своя клавиатура и т. п.), выставив значение в true. */
val LocalNavHidden = androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.MutableState<Boolean>?> { null }

data class AquaNavItem(val route: String, val label: String, val icon: AquaIcon)

/** Насколько пункт «выбран» при текущей позиции: 1 — на месте, 0 — дальше чем в шаге. */
private fun nearOf(position: Float, index: Int): Float = (1f - abs(position - index)).coerceIn(0f, 1f)

/**
 * Нижняя панель: плавающий остров на плотной подложке. Выбранный пункт
 * раскрывается: рядом со значком появляется подпись, а пункт лежит на
 * прямоугольнике с мягким скруглением и тонкой рамкой акцента. Остальные
 * пункты — только значки, поэтому не тесно и при шести разделах (у админа).
 *
 * Движение ведёт одно число — позиция выбранного пункта, — и читается оно
 * только в фазах раскладки и рисования: подписи постоянно на месте и меняют
 * лишь ширину и прозрачность, поэтому на каждом кадре ничего не
 * пересоздаётся и не перекомпоновывается. Подключены — акцент
 * аквамариновый, нет — белый.
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
    val position = animateFloatAsState(
        targetValue = selectedIndex.coerceAtLeast(0).toFloat(),
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "navPosition",
    )
    val activeColor = if (connected || palette.isLight) scheme.primary else scheme.onBackground
    val accent = scheme.primary
    val inactive = scheme.onSurfaceVariant

    val islandShape = RoundedCornerShape(28.dp)
    // Остров должен читаться как отдельный предмет, а не как полоса внизу:
    // плотная подложка, тонкая рамка и мягкая тень.
    val base = if (palette.isLight) Color.White.copy(alpha = 0.96f) else palette.panel.copy(alpha = 0.96f)
    val border = if (palette.isLight) ink(0.09f) else accent.copy(alpha = 0.20f)
    Box(
        modifier = modifier
            // системные кнопки навигации рисуются поверх приложения
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(68.dp)
            .shadow(
                elevation = 18.dp,
                shape = islandShape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            )
            .clip(islandShape)
            .background(base)
            .border(1.dp, border, islandShape),
    ) {
        Layout(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            content = {
                items.forEachIndexed { index, item ->
                    NavCell(item, index, position, inactive, activeColor, accent) { onSelect(item) }
                }
            },
        ) { measurables, constraints ->
            // ширина пункта = доля от общей, выбранный в 2,6 раза шире остальных
            val pos = position.value
            val weights = measurables.indices.map { 1f + 1.6f * nearOf(pos, it) }
            val sum = weights.sum()
            val total = constraints.maxWidth
            val cellHeight = 54.dp.roundToPx().coerceAtMost(constraints.maxHeight)
            var used = 0
            val placeables = measurables.mapIndexed { i, m ->
                val w = if (i == measurables.lastIndex) total - used
                else (total * weights[i] / sum).roundToInt()
                used += w
                m.measure(Constraints.fixed(w.coerceAtLeast(0), cellHeight))
            }
            layout(total, constraints.maxHeight) {
                var x = 0
                val y = (constraints.maxHeight - cellHeight) / 2
                placeables.forEach { p ->
                    p.placeRelative(x, y)
                    x += p.width
                }
            }
        }
    }
}

@Composable
private fun NavCell(
    item: AquaNavItem,
    index: Int,
    position: State<Float>,
    inactive: Color,
    active: Color,
    accent: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(21.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .drawBehind {
                // подсветка и рамка растут вместе с «выбранностью»
                val near = nearOf(position.value, index)
                val radius = CornerRadius(21.dp.toPx())
                drawRoundRect(accent.copy(alpha = 0.14f * near), cornerRadius = radius)
                drawRoundRect(
                    accent.copy(alpha = 0.45f * near),
                    cornerRadius = radius,
                    style = Stroke(1.dp.toPx()),
                )
            }
            // без волны нажатия: прямоугольная вспышка рядом с подсветкой
            // выглядела бы грубо
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = item.label,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.wrapContentSize(Alignment.Center, unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // значок в двух цветах: один гаснет, другой проявляется
            Box {
                AquaIconView(
                    item.icon, inactive, size = 23.dp, stroke = 1.4f,
                    modifier = Modifier.graphicsLayer { alpha = 1f - nearOf(position.value, index) },
                )
                AquaIconView(
                    item.icon, active, size = 23.dp, stroke = 1.4f,
                    modifier = Modifier.graphicsLayer { alpha = nearOf(position.value, index) },
                )
            }
            // Подпись не растёт вслед за системным масштабом шрифта дальше
            // 1,1: раскрытый пункт и так занимает почти треть панели.
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.1f))
            ) {
                Text(
                    item.label,
                    color = active,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        // место под подпись растёт вместе с пунктом, чтобы значок
                        // у закрытых пунктов оставался по центру
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                            val near = nearOf(position.value, index)
                            val shown = (placeable.width * near).roundToInt()
                            val gap = (7.dp.toPx() * near).roundToInt()
                            layout(shown + gap, placeable.height) { placeable.placeRelative(gap, 0) }
                        }
                        .graphicsLayer { alpha = ((nearOf(position.value, index) - 0.35f) / 0.65f).coerceIn(0f, 1f) },
                )
            }
        }
    }
}


/** Стеклянное поле поиска: значок слева, подсказка, клавиша «Поиск» на клавиатуре. */
@Composable
fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .glass(RoundedCornerShape(23.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AquaIconView(AquaIcon.Search, ink(0.6f), size = 19.dp, stroke = 1.6f)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = ink(0.5f))
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


/**
 * Загрузка: стеклянная капсула по центру с тремя пульсирующими точками и
 * подписью, что именно грузится. Заменяет голый крутящийся круг.
 */
@Composable
fun LoadingCapsule(text: String, modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "loading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "loadingPhase",
    )
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            modifier = Modifier
                // мягкое свечение вокруг капсулы
                .drawBehind {
                    val grow = 7.dp.toPx()
                    drawRoundRect(
                        color = accent.copy(alpha = 0.06f),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + grow * 2, size.height + grow * 2),
                        cornerRadius = CornerRadius(size.height / 2 + grow),
                    )
                }
                .glass(RoundedCornerShape(50))
                .padding(start = 20.dp, end = 22.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { i ->
                    val x = (phase + i / 3f) % 1f
                    val a = 0.3f + 0.7f * (if (x < 0.5f) x * 2f else (1f - x) * 2f)
                    Box(Modifier.size(9.dp).clip(CircleShape).background(accent.copy(alpha = a)))
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}


/**
 * Фильтр одной капсулой: части равной ширины (до четырёх) или, если их
 * больше, по размеру подписи с прокруткой вбок. Выбранная — акцентом.
 */
@Composable
fun <T> GlassSegments(
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val scrolls = items.size > 4
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp))
            .then(if (scrolls) Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()) else Modifier)
            .padding(4.dp),
    ) {
        items.forEach { (key, label) ->
            val isSelected = key == selected
            Box(
                modifier = Modifier
                    .then(if (scrolls) Modifier else Modifier.weight(1f))
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) scheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) scheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable { onSelect(key) }
                    .padding(vertical = 8.dp, horizontal = if (scrolls) 14.dp else 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    color = if (isSelected) scheme.primary else ink(0.6f),
                )
            }
        }
    }
}
