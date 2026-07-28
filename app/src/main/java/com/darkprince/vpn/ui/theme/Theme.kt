package com.darkprince.vpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4F8EF7),
    onPrimary = Color.White,
    secondary = Color(0xFF7BA7F9),
    background = Color(0xFF0F1420),
    surface = Color(0xFF171E2E),
    onBackground = Color(0xFFE8ECF4),
    onSurface = Color(0xFFE8ECF4),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6FE4),
    onPrimary = Color.White,
    secondary = Color(0xFF4F8EF7),
    background = Color(0xFFF6F8FC),
    surface = Color.White,
    onBackground = Color(0xFF1A2233),
    onSurface = Color(0xFF1A2233),
    error = Color(0xFFD64545),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
