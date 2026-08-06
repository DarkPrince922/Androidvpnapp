package com.darkprince.vpn.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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
)

// --- Ночь: исходная тема под логотип ---------------------------------------

private val NightScheme = darkColorScheme(
    primary = Color(0xFFCFAA62),
    onPrimary = Color(0xFF14100A),
    primaryContainer = Color(0xFFB8924C),
    onPrimaryContainer = Color(0xFF14100A),
    secondary = Color(0xFF5E8BFF),
    onSecondary = Color.White,
    background = Color.Transparent,
    onBackground = Color(0xFFEDF0F7),
    surface = Color(0xFF121A29).copy(alpha = 0.82f),
    onSurface = Color(0xFFEDF0F7),
    surfaceVariant = Color(0xFF1A2438),
    onSurfaceVariant = Color(0xFF9AA6BD),
    outline = Color(0xFF2A3650),
    error = BrandColors.Danger,
    onError = Color.White,
)

val NightPalette = AppPalette(
    id = "night",
    title = "Ночь",
    subtitle = "Тёмно-синяя, золотой акцент",
    isLight = false,
    background = Color(0xFF0A0E17),
    glowA = Color(0xFFCFAA62),
    glowB = Color(0xFF5E8BFF),
    glowAlpha = 0.15f,
    panel = Color(0xFF121A29),
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

@Composable
fun AppTheme(palette: AppPalette = NightPalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.scheme,
            content = content,
        )
    }
}
