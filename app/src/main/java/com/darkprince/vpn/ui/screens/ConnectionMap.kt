package com.darkprince.vpn.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.darkprince.vpn.core.model.ProxyProfile
import java.util.Locale
import kotlin.math.pow

private data class MapPoint(val x: Float, val y: Float)

@Composable
fun ConnectionMap(
    connected: Boolean,
    selectedServer: ProxyProfile?,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "connectionMap")
    val routeProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "routeProgress",
    )
    val cityPulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "cityPulse",
    )

    val source = sourcePointForLocale(Locale.getDefault().country)
    val target = targetPointForServer(selectedServer)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val mapLine = Color(0xFF5B6472)
        val coast = Color(0xFF737C89)
        val gold = Color(0xFFF2B84B)

        fun p(point: MapPoint) = Offset(point.x * w, point.y * h)

        // Stylised Europe / west-Asia silhouette. It stays deliberately subtle
        // so the power button remains the main visual element.
        val europe = Path().apply {
            moveTo(w * 0.06f, h * 0.50f)
            cubicTo(w * 0.11f, h * 0.36f, w * 0.18f, h * 0.30f, w * 0.26f, h * 0.33f)
            cubicTo(w * 0.31f, h * 0.22f, w * 0.39f, h * 0.24f, w * 0.43f, h * 0.31f)
            cubicTo(w * 0.51f, h * 0.25f, w * 0.63f, h * 0.28f, w * 0.69f, h * 0.37f)
            cubicTo(w * 0.78f, h * 0.34f, w * 0.89f, h * 0.40f, w * 0.95f, h * 0.49f)
            cubicTo(w * 0.88f, h * 0.55f, w * 0.81f, h * 0.55f, w * 0.75f, h * 0.61f)
            cubicTo(w * 0.67f, h * 0.68f, w * 0.58f, h * 0.69f, w * 0.51f, h * 0.64f)
            cubicTo(w * 0.45f, h * 0.72f, w * 0.36f, h * 0.72f, w * 0.30f, h * 0.65f)
            cubicTo(w * 0.22f, h * 0.70f, w * 0.12f, h * 0.63f, w * 0.06f, h * 0.50f)
        }
        drawPath(
            path = europe,
            color = coast.copy(alpha = if (connected) 0.24f else 0.12f),
            style = Stroke(width = 1.2f),
        )

        // Faint latitude/longitude-style lines provide a map feel without a
        // bitmap asset and without any network dependency.
        listOf(0.30f, 0.42f, 0.54f, 0.66f).forEach { y ->
            drawLine(
                color = mapLine.copy(alpha = if (connected) 0.09f else 0.045f),
                start = Offset(w * 0.05f, h * y),
                end = Offset(w * 0.95f, h * y),
                strokeWidth = 1f,
            )
        }
        listOf(0.18f, 0.34f, 0.50f, 0.66f, 0.82f).forEach { x ->
            drawLine(
                color = mapLine.copy(alpha = if (connected) 0.08f else 0.04f),
                start = Offset(w * x, h * 0.24f),
                end = Offset(w * x, h * 0.74f),
                strokeWidth = 1f,
            )
        }

        val cities = listOf(
            MapPoint(0.21f, 0.49f), MapPoint(0.29f, 0.43f), MapPoint(0.37f, 0.51f),
            MapPoint(0.43f, 0.40f), MapPoint(0.49f, 0.48f), MapPoint(0.56f, 0.43f),
            MapPoint(0.61f, 0.53f), MapPoint(0.68f, 0.47f), MapPoint(0.76f, 0.52f),
            MapPoint(0.83f, 0.43f), MapPoint(0.72f, 0.62f), MapPoint(0.53f, 0.61f),
        )
        cities.forEachIndexed { index, city ->
            val pos = p(city)
            if (connected) {
                val alpha = (0.30f + ((index % 4) * 0.10f)) * cityPulse
                drawCircle(gold.copy(alpha = alpha.coerceIn(0f, 0.75f)), radius = 4.8f, center = pos)
                drawCircle(gold.copy(alpha = 0.88f), radius = 1.6f, center = pos)
            } else {
                drawCircle(Color(0xFF657080).copy(alpha = 0.16f), radius = 1.4f, center = pos)
            }
        }

        if (connected && selectedServer != null) {
            val start = p(source)
            val end = p(target)
            val control = Offset(
                x = (start.x + end.x) / 2f,
                y = minOf(start.y, end.y) - h * 0.20f,
            )

            val route = Path().apply {
                moveTo(start.x, start.y)
                quadraticBezierTo(control.x, control.y, end.x, end.y)
            }
            drawPath(route, gold.copy(alpha = 0.18f), style = Stroke(width = 7f))
            drawPath(route, gold.copy(alpha = 0.82f), style = Stroke(width = 2.2f))

            drawCircle(gold.copy(alpha = 0.26f), radius = 11f, center = end)
            drawCircle(gold, radius = 4f, center = end)
            drawCircle(gold.copy(alpha = 0.70f), radius = 3f, center = start)

            val moving = quadraticPoint(start, control, end, routeProgress)
            drawCircle(gold.copy(alpha = 0.20f), radius = 11f, center = moving)
            drawCircle(Color.White.copy(alpha = 0.95f), radius = 3.2f, center = moving)
        }
    }
}

private fun quadraticPoint(start: Offset, control: Offset, end: Offset, t: Float): Offset {
    val oneMinus = 1f - t
    val x = oneMinus.pow(2) * start.x + 2f * oneMinus * t * control.x + t.pow(2) * end.x
    val y = oneMinus.pow(2) * start.y + 2f * oneMinus * t * control.y + t.pow(2) * end.y
    return Offset(x, y)
}

/**
 * Only the coarse device region is used. No GPS/location permission is needed.
 */
private fun sourcePointForLocale(countryCode: String): MapPoint = when (countryCode.uppercase(Locale.ROOT)) {
    "DE" -> MapPoint(0.43f, 0.45f)
    "PL" -> MapPoint(0.50f, 0.43f)
    "FI", "SE", "NO" -> MapPoint(0.48f, 0.32f)
    "NL", "BE" -> MapPoint(0.37f, 0.44f)
    "FR" -> MapPoint(0.34f, 0.52f)
    "GB", "IE" -> MapPoint(0.24f, 0.42f)
    "ES", "PT" -> MapPoint(0.25f, 0.63f)
    "IT" -> MapPoint(0.43f, 0.60f)
    "RU" -> MapPoint(0.76f, 0.38f)
    "UA" -> MapPoint(0.62f, 0.49f)
    "US", "CA" -> MapPoint(0.08f, 0.48f)
    else -> MapPoint(0.45f, 0.50f)
}

private fun targetPointForServer(server: ProxyProfile?): MapPoint {
    if (server == null) return MapPoint(0.57f, 0.47f)
    val text = listOf(server.name, server.serverDescription, server.address)
        .filterNotNull()
        .joinToString(" ")
        .lowercase(Locale.ROOT)

    return when {
        listOf("poland", "polska", "польш").any(text::contains) -> MapPoint(0.50f, 0.43f)
        listOf("germany", "deutsch", "герман").any(text::contains) -> MapPoint(0.43f, 0.45f)
        listOf("finland", "финлян").any(text::contains) -> MapPoint(0.52f, 0.29f)
        listOf("sweden", "швец").any(text::contains) -> MapPoint(0.43f, 0.29f)
        listOf("netherlands", "holland", "нидерланд", "голланд").any(text::contains) -> MapPoint(0.36f, 0.43f)
        listOf("france", "франц").any(text::contains) -> MapPoint(0.34f, 0.52f)
        listOf("england", "united kingdom", "britain", "британ").any(text::contains) -> MapPoint(0.25f, 0.42f)
        listOf("spain", "испан").any(text::contains) -> MapPoint(0.25f, 0.63f)
        listOf("italy", "итал").any(text::contains) -> MapPoint(0.43f, 0.60f)
        listOf("russia", "росси", "moscow", "москв").any(text::contains) -> MapPoint(0.76f, 0.38f)
        listOf("usa", "united states", "america", "сша", "америк").any(text::contains) -> MapPoint(0.08f, 0.48f)
        else -> MapPoint(0.58f, 0.47f)
    }
}
