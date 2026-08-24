package com.darkprince.vpn.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.darkprince.vpn.core.model.ProxyProfile
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class GeoPoint(val lat: Float, val lon: Float)

private val countryPoints = mapOf(
    "DE" to GeoPoint(51.1f, 10.4f), "NL" to GeoPoint(52.1f, 5.3f),
    "US" to GeoPoint(39.8f, -98.6f), "PL" to GeoPoint(52.1f, 19.4f),
    "SE" to GeoPoint(62.0f, 15.0f), "FI" to GeoPoint(64.5f, 26.0f),
    "SG" to GeoPoint(1.35f, 103.8f), "FR" to GeoPoint(46.2f, 2.2f),
    "GB" to GeoPoint(54.5f, -2.5f), "CA" to GeoPoint(56.1f, -106.3f),
    "JP" to GeoPoint(36.2f, 138.2f), "CH" to GeoPoint(46.8f, 8.2f),
    "AT" to GeoPoint(47.5f, 14.5f), "CZ" to GeoPoint(49.8f, 15.5f),
    "RU" to GeoPoint(56.0f, 38.0f), "UA" to GeoPoint(49.0f, 31.0f)
)

private val cityLights = listOf(
    GeoPoint(51.5f,-0.1f), GeoPoint(48.9f,2.35f), GeoPoint(52.5f,13.4f),
    GeoPoint(52.2f,21.0f), GeoPoint(50.1f,14.4f), GeoPoint(48.2f,16.4f),
    GeoPoint(59.3f,18.1f), GeoPoint(60.2f,24.9f), GeoPoint(40.7f,-74.0f),
    GeoPoint(34.0f,-118.2f), GeoPoint(35.7f,139.7f), GeoPoint(1.35f,103.8f)
)

private fun destinationCountry(server: ProxyProfile?): String? {
    val n = server?.name?.lowercase(Locale.ROOT) ?: return null
    return when {
        "poland" in n || "warsaw" in n || "польш" in n -> "PL"
        "germany" in n || "frankfurt" in n || "герман" in n -> "DE"
        "netherlands" in n || "amsterdam" in n || "нидер" in n -> "NL"
        "sweden" in n || "stockholm" in n || "швец" in n -> "SE"
        "finland" in n || "helsinki" in n || "финл" in n -> "FI"
        "singapore" in n || "сингап" in n -> "SG"
        "usa" in n || "united states" in n || "new york" in n || "сша" in n -> "US"
        "france" in n || "paris" in n || "франц" in n -> "FR"
        "uk" in n || "united kingdom" in n || "london" in n || "британ" in n -> "GB"
        "japan" in n || "tokyo" in n || "япон" in n -> "JP"
        else -> null
    }
}

private fun project(p: GeoPoint, w: Float, h: Float): Offset {
    val x = ((p.lon + 180f) / 360f) * w
    val y = ((90f - p.lat) / 180f) * h
    return Offset(x, y)
}

@Composable
fun ConnectionMap(
    connected: Boolean,
    selectedServer: ProxyProfile?,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "routePulse")
    val phase by pulse.animateFloat(
        0f, 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "routePhase"
    )
    val accent = Color(0xFFF2B84B)
    val dim = Color(0xFF1B2635)
    val source = countryPoints[Locale.getDefault().country.uppercase(Locale.ROOT)] ?: GeoPoint(50f, 10f)
    val target = countryPoints[destinationCountry(selectedServer)] ?: GeoPoint(52.1f, 19.4f)

    Canvas(modifier.fillMaxSize()) {
        // Abstract dark world grid: recognisable as a map without collecting GPS.
        for (lon in -150..150 step 30) {
            val x = ((lon + 180f) / 360f) * size.width
            drawLine(dim.copy(alpha = .18f), Offset(x, 0f), Offset(x, size.height), 1f)
        }
        for (lat in -60..60 step 30) {
            val y = ((90f - lat) / 180f) * size.height
            drawLine(dim.copy(alpha = .14f), Offset(0f, y), Offset(size.width, y), 1f)
        }

        // A few subdued continent-like arcs keep the background organic, not cartographic/GPS-precise.
        val coast = Path().apply {
            moveTo(size.width*.10f,size.height*.30f); cubicTo(size.width*.25f,size.height*.18f,size.width*.38f,size.height*.24f,size.width*.46f,size.height*.36f)
            cubicTo(size.width*.54f,size.height*.48f,size.width*.63f,size.height*.30f,size.width*.78f,size.height*.34f)
            cubicTo(size.width*.90f,size.height*.39f,size.width*.84f,size.height*.58f,size.width*.70f,size.height*.62f)
        }
        drawPath(coast, dim.copy(alpha=.48f), style=Stroke(width=2f))

        cityLights.forEachIndexed { i, city ->
            val c = project(city, size.width, size.height)
            val on = connected
            val r = if (on) 1.7f + ((i % 3) * .55f) else .8f
            drawCircle(if (on) accent.copy(alpha=.28f + (i%4)*.10f) else dim.copy(alpha=.20f), r, c)
            if (on && i % 3 == 0) drawCircle(accent.copy(alpha=.08f), r*4f, c)
        }

        if (connected) {
            val a = project(source, size.width, size.height)
            val b = project(target, size.width, size.height)
            val mid = Offset((a.x+b.x)/2f, minOf(a.y,b.y)-size.height*.12f)
            val route = Path().apply { moveTo(a.x,a.y); quadraticBezierTo(mid.x,mid.y,b.x,b.y) }
            drawPath(route, accent.copy(alpha=.30f), style=Stroke(width=7f))
            drawPath(route, accent.copy(alpha=.92f), style=Stroke(width=2.2f))
            drawCircle(accent.copy(alpha=.15f), 15f, b); drawCircle(accent, 5f, b)

            // moving spark on a quadratic Bezier route
            val t = phase
            val u = 1f-t
            val spark = Offset(
                u*u*a.x + 2f*u*t*mid.x + t*t*b.x,
                u*u*a.y + 2f*u*t*mid.y + t*t*b.y
            )
            drawCircle(accent.copy(alpha=.18f), 13f, spark)
            drawCircle(Color.White.copy(alpha=.95f), 3.5f, spark)
        }
    }
}
