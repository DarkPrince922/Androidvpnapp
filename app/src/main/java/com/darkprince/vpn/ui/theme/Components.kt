package com.darkprince.vpn.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Общие элементы интерфейса: заголовки секций, сгруппированные карточки,
 * строки настроек, плитки-иконки и чипы.
 */

/** Подпись над группой: капсом, с разрядкой, приглушённым цветом. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.4.sp,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/** Карточка-группа: внутри идут строки, разделённые тонкой линией. */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                RoundedCornerShape(22.dp),
            ),
        content = content,
    )
}

/** Разделитель между строками группы — с отступом под плитку-иконку. */
@Composable
fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
    )
}

/** Квадратная плитка с иконкой: подложка и рамка в цвет акцента. */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** То же, но вместо иконки — символ: флаг страны из названия сервера. */
@Composable
fun EmojiTile(
    emoji: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
    }
}

/** Мелкая метка под названием: транспорт, формат подписки и т.п. */
@Composable
fun TagChip(
    text: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.13f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * Задержка до сервера — «таблеткой» со шкалой качества.
 *
 * Голое число рядом с названием читалось как случайная цифра: чтобы понять,
 * хороший это сервер или плохой, приходилось сравнивать строки глазами.
 * Здесь то же значение, но сразу с ответом: три палочки показывают качество,
 * подложка — цветом. Пороги те же, что были у текста.
 *
 * Отрицательное значение означает, что узел не ответил.
 */
@Composable
fun PingChip(millis: Long, modifier: Modifier = Modifier) {
    val unreachable = millis < 0
    val tint = when {
        unreachable -> MaterialTheme.colorScheme.error
        millis < 300 -> BrandColors.Success
        millis < 700 -> BrandColors.Warning
        else -> MaterialTheme.colorScheme.error
    }
    // сколько палочек горит: чем меньше задержка, тем выше шкала
    val bars = when {
        unreachable -> 0
        millis < 300 -> 3
        millis < 700 -> 2
        else -> 1
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(tint.copy(alpha = 0.13f))
            .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
            // ширина под «1000 мс»: без неё строки списка дёргались бы,
            // когда у соседних серверов разное число цифр
            .widthIn(min = 68.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            listOf(5.dp, 8.dp, 11.dp).forEachIndexed { index, barHeight ->
                Box(
                    Modifier
                        .width(3.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index < bars) tint else tint.copy(alpha = 0.25f)),
                )
            }
        }
        Text(
            text = if (unreachable) "нет" else "$millis мс",
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Строка группы: плитка-иконка, заголовок с пояснением, значение справа
 * и стрелка. Вместо значения можно подставить свой элемент — например
 * переключатель.
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = icon, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            value?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showChevron) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Круглая кнопка-действие рядом с заголовком карточки. */
@Composable
fun CircleActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** Пункт плавающей нижней навигации: у активного — «таблетка» под ним. */
@Composable
fun NavPillItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val background by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(220),
        label = "navBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "navContent",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/**
 * Ведущий символ названия — флаг страны. Панель обычно ставит его в начало
 * имени узла; в списке он уезжает в отдельную плитку, а из заголовка
 * убирается, чтобы не дублироваться.
 */
fun leadingEmoji(name: String): String? {
    val trimmed = name.trimStart()
    if (trimmed.isEmpty()) return null
    val first = trimmed.codePointAt(0)
    // пара региональных индикаторов = флаг страны
    if (first in 0x1F1E6..0x1F1FF) {
        val firstChars = Character.charCount(first)
        if (trimmed.length > firstChars) {
            val second = trimmed.codePointAt(firstChars)
            if (second in 0x1F1E6..0x1F1FF) {
                return trimmed.substring(0, firstChars + Character.charCount(second))
            }
        }
        return trimmed.substring(0, firstChars)
    }
    // прочие символы вне основной плоскости: эмодзи вроде 🌍 или ⚡
    if (first > 0x2000 && !Character.isLetterOrDigit(first)) {
        return trimmed.substring(0, Character.charCount(first))
    }
    return null
}

/** Название без ведущего флага. */
fun nameWithoutEmoji(name: String): String {
    val emoji = leadingEmoji(name) ?: return name.trim()
    return name.trimStart().removePrefix(emoji).trim().ifBlank { name.trim() }
}
