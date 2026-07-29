package com.darkprince.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Премиальная тёмная палитра под логотип: глубокий тёмно-синий фон,
// золотистый металлик (окантовка щита) как основной акцент,
// холодное голубое свечение — вторичный.
object BrandColors {
    val Background = Color(0xFF0A0E17)
    val Surface = Color(0xFF121A29)
    val SurfaceHigh = Color(0xFF1A2438)
    val Gold = Color(0xFFCFAA62)
    val GoldDeep = Color(0xFFB8924C)
    val Glow = Color(0xFF5E8BFF)
    val TextPrimary = Color(0xFFEDF0F7)
    val TextSecondary = Color(0xFF9AA6BD)
    val Success = Color(0xFF3FB68B)
    val Danger = Color(0xFFE05B5B)
}

private val PremiumDark = darkColorScheme(
    primary = BrandColors.Gold,
    onPrimary = Color(0xFF14100A),
    primaryContainer = BrandColors.GoldDeep,
    onPrimaryContainer = Color(0xFF14100A),
    secondary = BrandColors.Glow,
    onSecondary = Color.White,
    background = Color.Transparent,
    onBackground = BrandColors.TextPrimary,
    // карточки слегка прозрачные — сквозь них видно живой фон
    surface = BrandColors.Surface.copy(alpha = 0.82f),
    onSurface = BrandColors.TextPrimary,
    surfaceVariant = BrandColors.SurfaceHigh,
    onSurfaceVariant = BrandColors.TextSecondary,
    outline = Color(0xFF2A3650),
    error = BrandColors.Danger,
    onError = Color.White,
)

/** Фирменная тема всегда тёмная — как логотип. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PremiumDark,
        content = content,
    )
}
