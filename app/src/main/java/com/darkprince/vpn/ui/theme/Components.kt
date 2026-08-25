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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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

/**
 * Растягивает содержимое за горизонтальные поля родителя.
 *
 * Списку экрана поля нужны — карточки не должны лепиться к краю. А карте,
 * наоборот, нужен весь экран: с полями у неё появлялись боковые обрезы, и
 * фон читался как вставленная картинка, а не как фон.
 *
 * Проще было бы снять поля со списка и раздать их каждому пункту, но тогда
 * про них пришлось бы помнить в каждом новом пункте.
 */
fun Modifier.bleedHorizontally(margin: Dp): Modifier = layout { measurable, constraints ->
    val extra = margin.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra,
        )
    )
    layout(placeable.width - extra, placeable.height) {
        placeable.place(-margin.roundToPx(), 0)
    }
}

/**
 * Цвет метки транспорта по её тексту.
 *
 * Цвет закреплён за словом, а не за местом в строке: reality всегда сиреневая,
 * grpc всегда коралловая. Тогда взгляд находит нужное, не читая — а если у
 * двух узлов совпал протокол, разницу видно по цвету второй метки.
 *
 * Цвета одни на все темы: это не оформление, а признак. Если менять их вместе
 * с темой, список серверов пришлось бы учить заново.
 */
private fun badgeTint(text: String, fallback: Color): Color = when (text.lowercase()) {
    "vless", "vmess", "trojan", "ss" -> Color(0xFFD9A94E)
    "hysteria2", "tuic" -> Color(0xFF35BFB8)
    "wireguard" -> Color(0xFF7C9AE0)
    "reality" -> Color(0xFF9B6BE0)
    "tls" -> Color(0xFF4E93D9)
    "grpc" -> Color(0xFFDE6A78)
    "xhttp" -> Color(0xFF3FB3C4)
    "websocket" -> Color(0xFF49B183)
    "httpupgrade" -> Color(0xFF6FA36B)
    "quic" -> Color(0xFFB07AD6)
    "mkcp" -> Color(0xFFC08A5A)
    "tcp" -> Color(0xFF7E8CA6)
    else -> fallback
}

/**
 * Мелкая метка транспорта: тонкая рамка, приглушённая подложка, буквы в
 * половину обычных.
 *
 * Намеренно тише соседних меток: этих значков в строке до трёх, и в полный
 * голос они забивали бы название узла, ради которого строка и существует.
 */
@Composable
fun TransportBadge(text: String, modifier: Modifier = Modifier) {
    val tint = badgeTint(text, MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        text = text.uppercase(),
        fontSize = 8.5.sp,
        lineHeight = 10.sp,
        letterSpacing = 0.4.sp,
        color = tint,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(tint.copy(alpha = 0.09f))
            .border(1.dp, tint.copy(alpha = 0.42f), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
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
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 4.dp)
            // ширина под «1000 мс»: без неё строки списка дёргались бы,
            // когда у соседних серверов разное число цифр
            .widthIn(min = 58.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            listOf(4.dp, 6.5.dp, 9.dp).forEachIndexed { index, barHeight ->
                Box(
                    Modifier
                        .width(2.5.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index < bars) tint else tint.copy(alpha = 0.25f)),
                )
            }
        }
        Text(
            text = if (unreachable) "нет" else "$millis мс",
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Место под задержку, пока идёт замер.
 *
 * Занимает ровно столько же, сколько готовый чип: иначе строки списка
 * прыгали бы по мере того, как узлы отвечают один за другим.
 */
@Composable
fun PingPendingChip(modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 7.dp, vertical = 4.dp)
            .widthIn(min = 58.dp)
            .height(17.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(11.dp),
            strokeWidth = 1.5.dp,
            color = tint,
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
    /** Непрочитанное: рисуем счётчиком у стрелки. Ноль — ничего не рисуем. */
    badge: Int = 0,
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
        if (badge > 0) {
            UnreadBadge(badge)
            Spacer(Modifier.width(8.dp))
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

/**
 * Кружок с числом непрочитанного.
 *
 * Подписи «Новых ответов: 2» под заголовком мало: её читают, только когда уже
 * смотрят на строку, а нужно, чтобы взгляд цеплялся сам. Однозначные числа
 * рисуем ровным кругом, двузначные и больше — вытянутой пилюлей, иначе цифры
 * упираются в края.
 */
@Composable
fun UnreadBadge(count: Int) {
    val text = if (count > 99) "99+" else count.toString()
    Box(
        modifier = Modifier
            .heightIn(min = 20.dp)
            .widthIn(min = 20.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondary)
            .padding(horizontal = if (text.length > 1) 6.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondary,
        )
    }
}
