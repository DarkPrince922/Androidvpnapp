package com.darkprince.vpn.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Появление блока с лёгким сдвигом снизу. Задержка позволяет выстроить
 * карточки «лесенкой», чтобы экран собирался плавно, а не возникал разом.
 */
@Composable
fun Appear(
    delayMillis: Int = 0,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 420, delayMillis = delayMillis)) +
            slideInVertically(tween(durationMillis = 420, delayMillis = delayMillis)) { it / 6 },
    ) {
        content()
    }
}
