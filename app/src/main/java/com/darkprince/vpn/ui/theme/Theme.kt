package com.darkprince.vpn.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkprince.vpn.R

/**
 * Цвета состояний. Одни и те же во всех темах намеренно: зелёное «хорошо» и
 * красное «не отвечает» — это не оформление, а смысл. Если бы они менялись
 * вместе с темой, человеку пришлось бы заново учиться читать список серверов.
 */
object BrandColors {
    val Success = Color(0xFF3FB68B)
    // «средне»: между Success и Danger, приглушённый — чистый жёлтый рядом с
    // золотым акцентом выглядит грязно
    val Warning = Color(0xFFE0A94A)
    val Danger = Color(0xFFE05B5B)
}

/**
 * Тема оформления целиком.
 *
 * Кроме обычной схемы Material здесь лежит то, чего в ней нет: цвет живого
 * фона и двух световых пятен, которые по нему плывут, и непрозрачная подложка
 * для нижней панели и круга подключения. Схема Material держит `surface`
 * полупрозрачным — сквозь карточки видно фон, — но панель и круг сквозь себя
 * фон пропускать не должны, иначе текст на них плывёт вместе с пятнами.
 */
data class AppPalette(
    val id: String,
    val title: String,
    val subtitle: String,
    val isLight: Boolean,
    val background: Color,
    val glowA: Color,
    val glowB: Color,
    /**
     * Насколько ярко светят пятна. На светлых темах то же значение, что на
     * тёмных, даёт грязь: цветное пятно по светлому фону читается как пятно
     * на бумаге, а не как свечение.
     */
    val glowAlpha: Float,
    val panel: Color,
    val scheme: ColorScheme,
    /** Третье световое пятно фона (внизу слева). */
    val glowC: Color = glowB,
    /**
     * Стекло: заливка и контур карточек и панели. На тёмных темах это белый
     * с малой непрозрачностью, на светлых — почти белый: так сквозь карточку
     * просвечивают пятна фона, а текст остаётся читаемым.
     */
    val glassFill: Color = Color.White.copy(alpha = 0.065f),
    val glassBorder: Color = Color.White.copy(alpha = 0.10f),
)

// --- Ночь: исходная тема под логотип ---------------------------------------

private val NightScheme = darkColorScheme(
    primary = Color(0xFF5FF0DF),
    onPrimary = Color(0xFF04201D),
    primaryContainer = Color(0xFF1F8F86),
    onPrimaryContainer = Color(0xFFE6FFFB),
    secondary = Color(0xFF7C9BFF),
    onSecondary = Color(0xFF0B1433),
    background = Color.Transparent,
    onBackground = Color(0xFFEAF6F6),
    // стекло: белый с малой непрозрачностью, сквозь него видны пятна фона
    surface = Color.White.copy(alpha = 0.065f),
    onSurface = Color(0xFFEAF6F6),
    surfaceVariant = Color(0xFF14222A),
    onSurfaceVariant = Color(0xFF8E9A9E),
    outline = Color.White.copy(alpha = 0.10f),
    outlineVariant = Color.White.copy(alpha = 0.07f),
    error = BrandColors.Danger,
    onError = Color.White,
    // Окна, меню и листы рисуются сплошным цветом: прозрачными им быть нельзя.
    surfaceContainerLowest = Color(0xFF070D12),
    surfaceContainerLow = Color(0xFF0B1319),
    surfaceContainer = Color(0xFF0F1A21),
    surfaceContainerHigh = Color(0xFF14212A),
    surfaceContainerHighest = Color(0xFF192934),
)

/** «Аква»: тёмное стекло и водяные пятна — фирменный вид приложения. */
val NightPalette = AppPalette(
    id = "night",
    title = "Аква",
    subtitle = "Тёмное стекло, аквамариновый акцент",
    isLight = false,
    background = Color(0xFF050A0F),
    glowA = Color(0xFF00D6C4),
    glowB = Color(0xFF466EFF),
    glowC = Color(0xFF00AADC),
    glowAlpha = 0.40f,
    panel = Color(0xFF0B1319),
    scheme = NightScheme,
)

// --- Закат: тёплый градиент по тёмной сливе --------------------------------

private val SunsetScheme = darkColorScheme(
    primary = Color(0xFFFF8A5C),
    onPrimary = Color(0xFF2A1206),
    primaryContainer = Color(0xFFE06B3E),
    onPrimaryContainer = Color(0xFF2A1206),
    secondary = Color(0xFFC77DFF),
    onSecondary = Color(0xFF230B33),
    background = Color.Transparent,
    onBackground = Color(0xFFF7EDF3),
    surface = Color(0xFF2A2033).copy(alpha = 0.82f),
    onSurface = Color(0xFFF7EDF3),
    surfaceVariant = Color(0xFF352840),
    onSurfaceVariant = Color(0xFFBCA6C4),
    outline = Color(0xFF4A3856),
    error = BrandColors.Danger,
    onError = Color.White,
    surfaceContainerLowest = Color(0xFF1F1727),
    surfaceContainerLow = Color(0xFF241B2D),
    surfaceContainer = Color(0xFF281F32),
    surfaceContainerHigh = Color(0xFF2C2236),
    surfaceContainerHighest = Color(0xFF2F2539),
)

val SunsetPalette = AppPalette(
    id = "sunset",
    title = "Закат",
    subtitle = "Тёплый оранжевый и фиолетовый",
    isLight = false,
    background = Color(0xFF1B1422),
    glowA = Color(0xFFFF7A45),
    glowB = Color(0xFFA855F7),
    glowAlpha = 0.20f,
    panel = Color(0xFF2A2033),
    scheme = SunsetScheme,
)

// --- Индиго: светлая, синь с фиолетовым ------------------------------------

private val IndigoScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B34C4),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF1D9BDB),
    onSecondary = Color.White,
    background = Color.Transparent,
    onBackground = Color(0xFF161A38),
    surface = Color.White.copy(alpha = 0.88f),
    onSurface = Color(0xFF161A38),
    surfaceVariant = Color(0xFFE3E8F6),
    onSurfaceVariant = Color(0xFF5B6488),
    outline = Color(0xFFC9D2E8),
    error = Color(0xFFC2413F),
    onError = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FAFE),
    surfaceContainer = Color(0xFFF5F7FC),
    surfaceContainerHigh = Color(0xFFF9FAFE),
    surfaceContainerHighest = Color.White,
)

val IndigoPalette = AppPalette(
    id = "indigo",
    title = "Индиго",
    subtitle = "Светлая, сине-фиолетовая",
    isLight = true,
    background = Color(0xFFEDF1F9),
    glowA = Color(0xFF4F46E5),
    glowB = Color(0xFF1D9BDB),
    glowAlpha = 0.10f,
    panel = Color(0xFFFFFFFF),
    scheme = IndigoScheme,
    glassFill = Color.White.copy(alpha = 0.72f),
    glassBorder = Color(0xFF1B1F3B).copy(alpha = 0.09f),
)

// --- Графит: почти без цвета -----------------------------------------------

private val GraphiteScheme = lightColorScheme(
    primary = Color(0xFF1C1C1E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF37373B),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF5A5A62),
    onSecondary = Color.White,
    background = Color.Transparent,
    onBackground = Color(0xFF121214),
    surface = Color.White.copy(alpha = 0.9f),
    onSurface = Color(0xFF121214),
    surfaceVariant = Color(0xFFE7E7EA),
    onSurfaceVariant = Color(0xFF6A6A72),
    outline = Color(0xFFD3D3D8),
    error = Color(0xFFB23A38),
    onError = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainer = Color(0xFFF6F6F8),
    surfaceContainerHigh = Color(0xFFFAFAFB),
    surfaceContainerHighest = Color.White,
)

val GraphitePalette = AppPalette(
    id = "graphite",
    title = "Графит",
    subtitle = "Светлая, почти без цвета",
    isLight = true,
    background = Color(0xFFF3F3F5),
    glowA = Color(0xFF9A9AA4),
    glowB = Color(0xFF74747E),
    glowAlpha = 0.14f,
    panel = Color(0xFFFFFFFF),
    scheme = GraphiteScheme,
    glassFill = Color.White.copy(alpha = 0.72f),
    glassBorder = Color(0xFF1B1F3B).copy(alpha = 0.09f),
)

/** Все темы в том порядке, в каком они показываются в настройках. */
val AppPalettes = listOf(NightPalette, SunsetPalette, IndigoPalette, GraphitePalette)

/**
 * Тема по сохранённому идентификатору. Неизвестный — это или тема из будущей
 * версии после отката, или порченая настройка; в обоих случаях лучше показать
 * фирменную «Ночь», чем упасть.
 */
fun paletteById(id: String?): AppPalette =
    AppPalettes.firstOrNull { it.id == id } ?: NightPalette

val LocalPalette = staticCompositionLocalOf { NightPalette }

/**
 * Шрифт один — Inter: нейтральный, хорошо читается мелко и красиво
 * выглядит тонким на крупных числах. Начертания: Light для заголовков и
 * цифр, Regular, Medium и SemiBold для текста. Лицензия OFL (licenses/),
 * в приложение положены только нужные начертания и только латиница с
 * кириллицей.
 *
 * `DisplayFont` и `TextFont` — прежние имена для старого кода: теперь они
 * указывают на тот же шрифт. Начертания, которых нет (Bold), подбираются
 * ближайшие.
 */
val InterFont = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val DisplayFont = InterFont
val TextFont = InterFont

private fun TextStyle.text(weight: FontWeight) = copy(fontFamily = InterFont, fontWeight = weight)

/** Заголовки и крупные числа: тонкие и чуть сжатые по трекингу. */
private fun TextStyle.display(size: Int, tracking: Float) = copy(
    fontFamily = InterFont,
    fontWeight = FontWeight.Light,
    fontSize = size.sp,
    lineHeight = (size * 1.15f).sp,
    letterSpacing = tracking.sp,
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.display(52, -1.5f),
        displayMedium = displayMedium.display(44, -1.2f),
        displaySmall = displaySmall.display(36, -1f),
        headlineLarge = headlineLarge.display(34, -0.9f),
        headlineMedium = headlineMedium.display(30, -0.8f),
        headlineSmall = headlineSmall.display(26, -0.6f),
        titleLarge = titleLarge.text(FontWeight.Medium).copy(fontSize = 20.sp, letterSpacing = (-0.3).sp),
        titleMedium = titleMedium.text(FontWeight.Medium).copy(fontSize = 16.sp),
        titleSmall = titleSmall.text(FontWeight.Medium).copy(fontSize = 14.5.sp),
        bodyLarge = bodyLarge.text(FontWeight.Normal),
        bodyMedium = bodyMedium.text(FontWeight.Normal),
        bodySmall = bodySmall.text(FontWeight.Normal),
        labelLarge = labelLarge.text(FontWeight.Medium),
        labelMedium = labelMedium.text(FontWeight.Medium),
        labelSmall = labelSmall.text(FontWeight.Medium),
    )
}

/** Скругления крупные: стеклянные карточки у «Аквы» почти таблетки. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun AppTheme(palette: AppPalette = NightPalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
