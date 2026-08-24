package com.darkprince.vpn.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.darkprince.vpn.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Карта соединения за кнопкой подключения.
 *
 * Снимок Земли ночью, приближенный к маршруту: от примерного региона
 * устройства к стране выбранного узла. Когда туннель опущен, огни городов
 * почти погашены; при подключении карта разгорается, и по дуге бежит искра.
 *
 * Геолокацию приложение не спрашивает и не может. Начало маршрута — регион из
 * настроек системы, то есть страна целиком, а не координата; конец — страна
 * выбранного сервера. Ни то, ни другое никуда не отправляется: карта целиком
 * рисуется на устройстве.
 *
 * Подложка — NASA «Earth's City Lights» (общественное достояние),
 * перекрашенная под фирменное золото заранее, при сборке ассета: делать это на
 * телефоне значило бы гонять два мегапикселя на каждом кадре.
 */

/**
 * Центры стран по коду ISO 3166-1 alpha-2: «код широта долгота» через запятую.
 *
 * Строкой, а не картой на две сотни записей: разбирается один раз при первом
 * показе карты, зато файл остаётся обозримым. Координата — центр крупнейшего
 * контура страны, поэтому Франция попадает во Францию, а не в середину между
 * метрополией и заморскими территориями.
 */
private val COUNTRY_CENTERS: String =
        "AD42.5 1.5,AE24.2 54.2,AF34.8 67.8,AL41.3 20.1,AM39.9 45.3," +
        "AO-10.9 17.4,AQ-73.0 -2.7,AR-37.0 -65.1,AT47.6 13.5,AU-23.8 133.2," +
        "AZ40.4 47.4,BA44.2 17.9,BD23.4 90.6,BE50.7 4.4,BF12.1 -1.9," +
        "BG42.9 24.7,BH26.0 50.5,BI-3.2 30.0,BJ9.8 2.3,BN4.7 115.0," +
        "BO-16.3 -64.1,BR-9.7 -56.9,BS24.5 -77.9,BT27.5 90.6,BW-22.5 24.5," +
        "BY53.3 28.3,BZ17.4 -88.6,CA56.4 -91.1,CD-3.8 23.1,CF6.3 20.7," +
        "CG-0.9 14.9,CH46.8 8.3,CI7.9 -6.3,CL-38.1 -71.4,CM6.9 13.2," +
        "CN37.3 105.7,CO4.5 -72.7,CR9.8 -84.2,CU21.7 -79.7,CY35.1 33.4," +
        "CZ49.8 15.8,DE51.0 10.7,DJ11.8 42.5,DK56.3 9.6,DO18.7 -70.5," +
        "DZ29.7 3.0,EC-1.6 -78.7,EE58.7 26.1,EG28.2 31.7,EH24.8 -12.0," +
        "ER14.7 39.6,ES40.2 -4.5,ET9.0 39.2,FI65.2 25.9,FJ-17.7 178.0," +
        "FK-51.7 -59.6,FR47.0 3.3,GA-0.3 11.9,GB53.9 -3.1,GE42.3 43.4," +
        "GH8.3 -1.0,GL74.1 -41.0,GM13.5 -15.3,GN10.4 -11.0,GQ1.6 10.1," +
        "GR39.8 23.1,GT15.5 -90.3,GW12.0 -15.2,GY4.5 -58.9,HK22.3 114.2," +
        "HN14.7 -86.3,HR44.8 16.4,HT18.8 -72.7,HU47.4 19.2,ID0.1 114.0," +
        "IE53.5 -7.7,IL32.0 35.1,IN24.0 83.6,IQ33.4 44.4,IR33.4 53.2," +
        "IS65.3 -19.3,IT43.1 12.6,JM18.2 -77.3,JO31.1 36.7,JP35.7 136.0," +
        "KE1.1 37.8,KG41.4 74.0,KH12.6 104.9,KP39.9 127.4,KR36.7 127.5," +
        "KW29.3 47.7,KZ47.3 65.3,LA18.3 103.7,LB33.8 35.9,LI47.2 9.5," +
        "LK7.6 80.9,LR6.8 -9.1,LS-29.6 28.4,LT55.2 24.0,LU49.8 6.1," +
        "LV56.9 24.9,LY28.5 16.6,MA28.8 -9.4,MC43.7 7.4,MD47.0 28.5," +
        "ME42.7 19.4,MG-18.0 47.0,MK41.7 21.6,ML14.1 -5.8,MM20.0 97.2," +
        "MN47.2 104.6,MR18.8 -12.4,MT35.9 14.4,MU-20.3 57.6,MW-12.9 34.1," +
        "MX23.9 -103.1,MY3.7 114.8,MZ-17.7 35.0,NA-21.6 17.9,NC-21.2 165.5," +
        "NE15.5 8.7,NG9.7 8.3,NI13.1 -85.0,NL52.1 5.5,NO66.2 18.1," +
        "NP28.2 84.6,NZ-43.5 171.0,OM20.9 56.6,PA8.5 -80.3,PE-7.8 -74.1," +
        "PG-7.8 146.2,PH15.4 121.8,PK30.8 69.4,PL51.7 19.5,PR18.3 -66.4," +
        "PS31.8 35.2,PT39.8 -8.0,PY-23.3 -57.6,QA25.3 51.2,RO45.9 25.2," +
        "RS43.9 20.9,RU59.1 90.9,RW-2.0 29.9,SA24.0 43.6,SB-7.9 159.1," +
        "SD12.8 29.3,SE62.7 16.6,SG1.4 103.8,SI46.1 15.0,SK48.8 19.4," +
        "SL8.6 -11.7,SM43.9 12.5,SN13.9 -14.6,SO6.6 46.9,SR3.7 -55.9," +
        "SS8.0 30.1,SV13.9 -88.9,SY35.1 38.0,SZ-26.4 31.4,TD13.0 18.0," +
        "TF-49.1 69.5,TG8.9 0.9,TH13.2 100.7,TJ38.5 70.8,TL-8.7 125.9," +
        "TM39.3 58.5,TN34.0 9.8,TR38.4 36.9,TT10.5 -61.5,TW23.9 121.1," +
        "TZ-6.5 34.3,UA48.7 30.5,UG1.3 32.0,US38.3 -90.4,UY-32.7 -56.1," +
        "UZ41.2 65.3,VE7.2 -66.9,VN16.7 105.9,VU-15.4 166.9,XK42.5 21.0," +
        "YE15.5 46.5,ZA-28.6 24.7,ZM-12.8 28.0,ZW-18.9 29.7,"

/**
 * Центры расселения для стран, у которых геометрический центр никуда не годится.
 *
 * Центр России по контуру — середина Сибири, и маршрут для человека из Москвы
 * начинался бы за три тысячи километров от него. Здесь центр расселения: тоже
 * не координата человека, а «примерно эта часть страны», но хотя бы та самая.
 */
private val POPULATED_CENTER = mapOf(
    "RU" to (55.5f to 42.0f),
    "US" to (39.5f to -86.0f),
    "CA" to (45.4f to -78.0f),
    "BR" to (-19.0f to -45.0f),
    "AU" to (-33.0f to 147.0f),
    "CN" to (33.0f to 113.0f),
    "KZ" to (49.0f to 72.0f),
    "ID" to (-7.0f to 110.0f),
)

/** Разобранная таблица центров. Ленивая: до первого показа карты не нужна. */
private val centers: Map<String, Pair<Float, Float>> by lazy {
    COUNTRY_CENTERS.split(",")
        .filter { it.length > 4 }
        .mapNotNull { entry ->
            val parts = entry.drop(2).split(" ")
            val lat = parts.getOrNull(0)?.toFloatOrNull()
            val lon = parts.getOrNull(1)?.toFloatOrNull()
            if (lat == null || lon == null) null else entry.take(2) to (lat to lon)
        }
        .toMap()
}

/**
 * Код страны из флага в начале названия узла.
 *
 * Флаг в Unicode — пара региональных индикаторов, и она буквально содержит код
 * страны: 🇵🇱 это U+1F1F5 U+1F1F1, то есть «PL». Поэтому список стран вести не
 * нужно: узел с флагом опознаётся сам, как бы его ни назвали в панели.
 */
internal fun countryCodeFromName(name: String?): String? {
    val trimmed = name?.trimStart() ?: return null
    if (trimmed.isEmpty()) return null
    val first = trimmed.codePointAt(0)
    if (first !in 0x1F1E6..0x1F1FF) return null
    if (trimmed.length <= Character.charCount(first)) return null
    val second = trimmed.codePointAt(Character.charCount(first))
    if (second !in 0x1F1E6..0x1F1FF) return null
    return String(charArrayOf('A' + (first - 0x1F1E6), 'A' + (second - 0x1F1E6)))
}

/** Название страны на языке телефона: «Польша», а не «PL». */
internal fun countryTitle(code: String): String =
    Locale("", code).getDisplayCountry(Locale.getDefault()).ifBlank { code }

private fun centerOf(code: String?): Pair<Float, Float>? {
    val key = code?.uppercase(Locale.ROOT) ?: return null
    return POPULATED_CENTER[key] ?: centers[key]
}

/**
 * @param connected туннель поднят: зажигаем огни и ведём дугу
 * @param serverName имя выбранного узла — из его флага берётся страна
 */
@Composable
fun ConnectionMap(
    connected: Boolean,
    serverName: String?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val accent = palette.scheme.primary
    val measurer = rememberTextMeasurer()
    val earth = ImageBitmap.imageResource(R.drawable.earth_night)

    // Переход тянем плавно: мгновенная смена яркости всей карты читается как
    // мигание экрана, а не как отклик на нажатие.
    val glow by animateFloatAsState(
        targetValue = if (connected) 1f else 0f,
        animationSpec = tween(900),
        label = "mapGlow",
    )

    val travel = rememberInfiniteTransition(label = "mapTravel")
    val spark by travel.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "mapSpark",
    )

    val code = countryCodeFromName(serverName)
    val target = centerOf(code)
    val source = centerOf(Locale.getDefault().country)

    val label = remember(code) {
        code?.let { "${flagOf(it)} ${countryTitle(it)}" }
    }

    Canvas(modifier.fillMaxSize()) {
        val frame = frameFor(source, target, size)

        drawEarth(earth, frame, glow)
        drawFade(palette.background)

        val to = target?.let { frame.project(it.first, it.second) } ?: return@Canvas

        if (source != null && glow > 0.01f) {
            drawRoute(frame.project(source.first, source.second), to, accent, glow, spark)
        }
        drawEndpoint(to, accent, glow)
        if (label != null && glow > 0.05f) {
            drawLabel(measurer, label, to, accent, glow, palette.panel, palette.scheme.onSurface)
        }
    }
}

/** Флаг-эмодзи из кода страны — обратная сборка того, что читает countryCodeFromName. */
private fun flagOf(code: String): String =
    String(Character.toChars(0x1F1E6 + (code[0] - 'A'))) +
        String(Character.toChars(0x1F1E6 + (code[1] - 'A')))

// --- кадр -------------------------------------------------------------------

/**
 * Окно карты: какой кусок мира показываем и как он ложится на холст.
 *
 * Мир целиком не годится — дуга Москва → Варшава на ширине телефона выродилась
 * бы в точку. Кадр строится по концам маршрута с полями, но не уже [MIN_SPAN]:
 * снимок всего 2048 точек в ширину, и при большем приближении он поплывёт.
 */
private class MapFrame(
    val lonLeft: Float,
    val lonSpan: Float,
    val latTop: Float,
    val latSpan: Float,
    val size: Size,
) {
    fun project(lat: Float, lon: Float): Offset = Offset(
        x = (lon - lonLeft) / lonSpan * size.width,
        y = (latTop - lat) / latSpan * size.height,
    )
}

private const val MIN_SPAN = 72f

/** Доля высоты кадра над маршрутом: 0.5 — по центру, меньше — выше. */
private const val ROUTE_TOP_BIAS = 0.26f

/** Полюса в кадр не пускаем: у краёв равнопромежуточной проекции всё растянуто. */
private const val VIEW_LAT_TOP = 80f
private const val VIEW_LAT_BOTTOM = -70f

private fun frameFor(source: Pair<Float, Float>?, target: Pair<Float, Float>?, size: Size): MapFrame {
    // без узла показываем Европу: там стоит большинство наших серверов
    val a = source ?: (52f to 15f)
    val b = target ?: a

    val aspect = if (size.height > 0f) size.width / size.height else 1.6f

    // Ширину задаёт сам маршрут плюс поля, чтобы концы дуги не упирались в край.
    val needLon = abs(a.second - b.second) * 1.45f
    val needLat = abs(a.first - b.first) * 1.9f
    val lonSpan = max(max(needLon, needLat * aspect), MIN_SPAN).coerceAtMost(360f)
    val latSpan = min(lonSpan / aspect, VIEW_LAT_TOP - VIEW_LAT_BOTTOM)

    // Маршрут держим в верхней трети кадра, а не по центру: в центре стоит
    // кнопка подключения, и дуга уходила бы прямо за неё.
    //
    // Кадр при этом упирается в границы, а не выходит за них: за краем снимка
    // была бы пустая полоса. Сдвигаем, а не сжимаем — масштаб маршрута важнее
    // того, какой именно кусок мира попал в кадр.
    val latTop = ((a.first + b.first) / 2f + latSpan * ROUTE_TOP_BIAS)
        .coerceIn(VIEW_LAT_BOTTOM + latSpan, VIEW_LAT_TOP)
    val lonLeft = ((a.second + b.second) / 2f - lonSpan / 2f)
        .coerceIn(-180f, 180f - lonSpan)

    return MapFrame(lonLeft, lonSpan, latTop, latSpan, size)
}

// --- отрисовка --------------------------------------------------------------

/**
 * Снимок Земли, вырезанный по кадру.
 *
 * Яркость ведёт glow: при опущенном туннеле огни почти погашены, но контуры
 * материков видны — иначе экран выглядел бы просто чёрным и было бы непонятно,
 * что там вообще карта.
 */
private fun DrawScope.drawEarth(earth: ImageBitmap, frame: MapFrame, glow: Float) {
    val sx = ((frame.lonLeft + 180f) / 360f * earth.width).roundToInt()
    val sy = ((90f - frame.latTop) / 180f * earth.height).roundToInt()
    val sw = (frame.lonSpan / 360f * earth.width).roundToInt()
    val sh = (frame.latSpan / 180f * earth.height).roundToInt()

    drawImage(
        image = earth,
        srcOffset = IntOffset(sx.coerceIn(0, earth.width - 1), sy.coerceIn(0, earth.height - 1)),
        srcSize = IntSize(
            sw.coerceIn(1, earth.width - sx.coerceIn(0, earth.width - 1)),
            sh.coerceIn(1, earth.height - sy.coerceIn(0, earth.height - 1)),
        ),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = 0.34f + 0.66f * glow,
        // снимок растягивается в несколько раз: без сглаживания вылезли бы пиксели
        filterQuality = FilterQuality.High,
    )
}

/** Растворение краёв в фон, чтобы карта не обрывалась прямоугольником. */
private fun DrawScope.drawFade(background: Color) {
    drawRect(
        brush = Brush.verticalGradient(
            0f to background,
            0.18f to background.copy(alpha = 0f),
            0.72f to background.copy(alpha = 0f),
            1f to background,
        ),
    )
    drawRect(
        brush = Brush.horizontalGradient(
            0f to background,
            0.12f to background.copy(alpha = 0f),
            0.88f to background.copy(alpha = 0f),
            1f to background,
        ),
    )
}

/**
 * Дуга маршрута с бегущей искрой.
 *
 * Сегментами, а не одним Path: так линия разгорается к концу — ближе к стране
 * назначения ярче и толще, и по ней видно, куда идёт трафик, без стрелок.
 */
private fun DrawScope.drawRoute(from: Offset, to: Offset, accent: Color, glow: Float, spark: Float) {
    val lift = (to - from).getDistance() * 0.42f
    val control = Offset((from.x + to.x) / 2f, min(from.y, to.y) - lift)

    fun at(t: Float): Offset {
        val u = 1f - t
        return Offset(
            u * u * from.x + 2f * u * t * control.x + t * t * to.x,
            u * u * from.y + 2f * u * t * control.y + t * t * to.y,
        )
    }

    val steps = 64
    var prev = at(0f)
    for (i in 1..steps) {
        val t = i / steps.toFloat()
        val point = at(t)
        val ramp = 0.3f + t * 0.7f
        // широкий полупрозрачный след под тонкой яркой линией даёт свечение
        drawLine(accent.copy(alpha = 0.16f * ramp * glow), prev, point, strokeWidth = 6f * ramp)
        drawLine(accent.copy(alpha = 0.95f * ramp * glow), prev, point, strokeWidth = 1.8f)
        prev = point
    }

    val head = at(spark)
    val fade = 1f - spark * spark
    drawCircle(accent.copy(alpha = 0.20f * glow * fade), 13f, head)
    drawCircle(Color.White.copy(alpha = 0.95f * glow * fade), 3f, head)
}

/** Точка страны назначения: ядро плюс ореол, который растёт вместе с glow. */
private fun DrawScope.drawEndpoint(point: Offset, accent: Color, glow: Float) {
    drawCircle(accent.copy(alpha = 0.10f + 0.16f * glow), 10f + 10f * glow, point)
    drawCircle(accent.copy(alpha = 0.45f + 0.55f * glow), 3.5f + 1.5f * glow, point)
}

/** Подпись страны рядом с точкой: флаг и название на языке телефона. */
private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    point: Offset,
    accent: Color,
    glow: Float,
    panel: Color,
    onSurface: Color,
) {
    val laid: TextLayoutResult = measurer.measure(
        text = text,
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
    val padX = 10f
    val padY = 6f
    val w = laid.size.width + padX * 2
    val h = laid.size.height + padY * 2

    // подпись над точкой, но у верхнего края — под ней: иначе таблетка уезжала
    // бы за границу карты у северных стран
    val above = point.y - h - 16f > 0f
    val top = if (above) point.y - h - 16f else point.y + 16f
    val left = (point.x - w / 2f).coerceIn(4f, max(4f, size.width - w - 4f))

    drawRoundRect(
        color = panel.copy(alpha = 0.9f * glow),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(h / 2f),
    )
    drawRoundRect(
        color = accent.copy(alpha = 0.5f * glow),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(h / 2f),
        style = Stroke(width = 1f),
    )
    drawText(
        textLayoutResult = laid,
        color = onSurface.copy(alpha = glow),
        topLeft = Offset(left + padX, top + padY),
    )
}
