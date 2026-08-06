package com.darkprince.vpn.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Выбор темы: карточки-образцы, по одной на тему.
 *
 * Названий недостаточно — «Индиго» и «Графит» ни о чём не говорят, пока не
 * увидишь. Поэтому каждая карточка нарисована своими же цветами: фон темы,
 * на нём кружки акцентов и полоски вместо текста. Человек выбирает глазами,
 * а не читает список.
 */
@Composable
fun ThemePicker(
    selectedId: String,
    onSelect: (AppPalette) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppPalettes.forEach { palette ->
            ThemeSwatch(
                palette = palette,
                selected = palette.id == selectedId,
                onClick = { onSelect(palette) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeSwatch(
    palette: AppPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
        animationSpec = tween(220),
        label = "swatchBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = tween(220),
        label = "swatchBorderWidth",
    )

    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.background)
                .border(borderWidth, borderColor, RoundedCornerShape(14.dp)),
        ) {
            // мини-версия экрана: два акцентных пятна и строки-заглушки
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(palette.scheme.primary),
                )
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(palette.scheme.secondary),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 8.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(palette.scheme.onSurface.copy(alpha = 0.75f)),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(palette.scheme.onSurfaceVariant.copy(alpha = 0.6f)),
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = 6.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Выбрана",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            palette.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
