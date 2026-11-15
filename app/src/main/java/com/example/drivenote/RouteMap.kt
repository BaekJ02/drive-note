package com.example.drivenote

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

private const val TAG = "RouteMap"
private const val TILE = 256.0
private val Casing = Color.White

/**
 * 최근 주행 경로 지도.
 * - 내장 지도 이미지(route_map.png)가 있으면: 이미지 위에 Web Mercator로 변환한 경로를 그림
 * - 없으면(다운로드 실패 등): Canvas로 그린 대체 지도 위에 경로를 그림
 *
 * @param showAllSegments true면 습관 감지 구간을 모두 분류 색으로 강조 (홈)
 * @param habit 지정하면 그 습관의 구간만 강조하고 발생 지점 마커를 표시 (리포트 상세)
 */
@Composable
fun DriveRouteMap(routeColor: Color, modifier: Modifier = Modifier, showAllSegments: Boolean = true, habit: Habit? = null) {
    val context = LocalContext.current
    val image = remember { loadRouteMapImage(context) }
    val measurer = rememberTextMeasurer()

    Box(modifier.clipToBounds().background(MapIllustrationBg)) {
        Canvas(Modifier.fillMaxSize()) {
            val project: (GeoPt) -> Offset = if (image != null) {
                drawImageProjection(image)
            } else {
                drawMapBase()
                fitProjection()
            }
            drawRoute(project, routeColor, showAllSegments, habit)
            if (image == null) {
                // 대체 지도에는 지명이 없으므로 출발·도착 지명을 표시
                drawPlaceLabel(measurer, "시흥동", project(MockData.lastDriveRoute.first()))
                drawPlaceLabel(measurer, "삼성동", project(MockData.lastDriveRoute.last()))
            }
        }
        if (image != null) {
            // 네이버 지도 저작권 표시 (사용 조건)
            Text("© NAVER Corp.", fontSize = 14.sp, color = Color(0xFF5F6672),
                modifier = Modifier.align(Alignment.BottomEnd).background(Color.White.copy(alpha = 0.8f))
                    .padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

/** 내장 지도 이미지. 없거나 읽기 실패하면 null (→ 대체 지도) */
@SuppressLint("DiscouragedApi") // 이미지가 없어도 빌드되도록 이름으로 찾음
private fun loadRouteMapImage(context: Context): ImageBitmap? = try {
    val id = context.resources.getIdentifier("route_map", "drawable", context.packageName)
    if (id == 0) {
        Log.i(TAG, "route_map.png 없음 → 대체 지도 사용"); null
    } else {
        BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap()
    }
} catch (t: Throwable) {
    Log.w(TAG, "지도 이미지 로드 실패 → 대체 지도 사용", t); null
}

/** 위경도 → Web Mercator 월드 픽셀 (256px 타일, 줌 = 네이버 Static Map level) */
private fun worldPixel(g: GeoPt, level: Int): Pair<Double, Double> {
    val n = TILE * (1 shl level)
    val x = (g.lon + 180.0) / 360.0 * n
    val r = Math.toRadians(g.lat)
    val y = (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * n
    return x to y
}

/**
 * 위경도 → 내장 이미지(네이버 Static Map)의 픽셀 좌표.
 * 이미지 중심 = center, 1 월드 픽셀 = scale 이미지 픽셀 (scale=2면 2배 해상도)
 */
private fun imagePixel(g: GeoPt): Offset {
    val level = MockData.routeMapLevel
    val (cx, cy) = worldPixel(GeoPt(MockData.routeMapCenterLat, MockData.routeMapCenterLon), level)
    val (x, y) = worldPixel(g, level)
    val s = MockData.routeMapScale
    return Offset(
        ((x - cx) * s + MockData.routeMapWidth / 2.0).toFloat(),
        ((y - cy) * s + MockData.routeMapHeight / 2.0).toFloat(),
    )
}

/**
 * 지도 이미지를 경로 중심으로 꽉 차게 그리고(crop/scale), 위경도 → 화면 좌표 변환 함수를 돌려준다.
 * 경로 전체가 들어가는 배율과 이미지가 화면을 꽉 채우는 배율 중 큰 쪽을 쓰고,
 * 이미지 밖(빈 영역)이 보이지 않게 위치를 조정한다.
 */
private fun DrawScope.drawImageProjection(image: ImageBitmap): (GeoPt) -> Offset {
    val px = MockData.lastDriveRoute.map(::imagePixel)
    val minX = px.minOf { it.x }; val maxX = px.maxOf { it.x }
    val minY = px.minOf { it.y }; val maxY = px.maxOf { it.y }
    val pad = 40.dp.toPx()
    val fit = min((size.width - 2 * pad) / (maxX - minX), (size.height - 2 * pad) / (maxY - minY))
    val cover = max(size.width / image.width, size.height / image.height)
    val s = max(fit, cover)
    // 경로 중심을 화면 중심에 두되, 이미지가 화면을 벗어나지 않게 제한
    var ox = size.width / 2 - (minX + maxX) / 2 * s
    var oy = size.height / 2 - (minY + maxY) / 2 * s
    ox = ox.coerceIn(size.width - image.width * s, 0f)
    oy = oy.coerceIn(size.height - image.height * s, 0f)
    drawImage(
        image,
        srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
        dstSize = IntSize((image.width * s).roundToInt(), (image.height * s).roundToInt()),
    )
    return { g -> val p = imagePixel(g); Offset(ox + p.x * s, oy + p.y * s) }
}

/** 대체 지도: 경로 영역을 화면 비율에 맞춰 가운데에 배치 (경도는 위도에 따라 축소) */
private fun DrawScope.fitProjection(): (GeoPt) -> Offset {
    val route = MockData.lastDriveRoute
    val k = cos(Math.toRadians(route.map { it.lat }.average()))
    val minLat = route.minOf { it.lat }; val maxLat = route.maxOf { it.lat }
    val minLon = route.minOf { it.lon }; val maxLon = route.maxOf { it.lon }
    val pad = 36.dp.toPx()
    val scale = min((size.width - 2 * pad) / ((maxLon - minLon) * k), (size.height - 2 * pad) / (maxLat - minLat))
    val offX = (size.width - (maxLon - minLon) * k * scale) / 2
    val offY = (size.height - (maxLat - minLat) * scale) / 2
    return { g -> Offset((offX + (g.lon - minLon) * k * scale).toFloat(), (offY + (maxLat - g.lat) * scale).toFloat()) }
}

/** 경로·습관 구간·출발/도착 마커·발생 지점 마커 */
private fun DrawScope.drawRoute(project: (GeoPt) -> Offset, routeColor: Color, showAllSegments: Boolean, habit: Habit?) {
    val route = MockData.lastDriveRoute
    val pts = route.map(project)
    fun path(from: Int, to: Int) = Path().apply {
        pts.subList(from, to + 1).forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    }
    fun stroke(w: Float) = Stroke(w.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    val all = path(0, route.lastIndex)
    drawPath(all, Casing, style = stroke(11f))
    drawPath(all, routeColor, style = stroke(6f))

    val segments = MockData.lastDriveSegments.filter { showAllSegments || it.habitId == habit?.id }
    segments.forEach { s ->
        val c = MockData.habits.first { it.id == s.habitId }.category.color
        val p = path(s.from, s.to)
        drawPath(p, Casing, style = stroke(15f))
        drawPath(p, c, style = stroke(10f))
    }

    // 출발(흰 원 + 테두리) · 도착(채운 원)
    val start = pts.first(); val end = pts.last()
    drawCircle(Color.White, 11.dp.toPx(), start)
    drawCircle(routeColor, 11.dp.toPx(), start, style = Stroke(4.dp.toPx()))
    drawCircle(Color.White, 13.dp.toPx(), end)
    drawCircle(routeColor, 9.dp.toPx(), end)

    // 리포트 상세: 선택한 습관의 발생 지점 (구간이 있으면 그 안에, 없으면 경로 전체에 고르게)
    if (habit != null) {
        val seg = MockData.lastDriveSegments.firstOrNull { it.habitId == habit.id }
        // 짧은 구간에서 마커가 겹치지 않게 구간 앞뒤로 범위를 조금 넓힘
        val (a, b) = if (seg != null) (seg.from - 2).coerceAtLeast(0) to (seg.to + 2).coerceAtMost(route.lastIndex)
            else 0 to route.lastIndex
        repeat(habit.count) { i ->
            val jitter = if (seg != null) 0f else (((habit.id * 37 + i * 53) % 7) - 3) * 0.015f
            val t = ((i + 1f) / (habit.count + 1f) + jitter).coerceIn(0.05f, 0.95f)
            val p = pointAlong(pts.subList(a, b + 1), t)
            drawCircle(Color.Black.copy(alpha = 0.12f), 15.dp.toPx(), p + Offset(0f, 2.dp.toPx()))
            drawCircle(Color.White, 14.dp.toPx(), p)
            drawCircle(habit.category.color, 9.dp.toPx(), p)
        }
    }
}

/** 꺾인 선 위에서 길이 비율 t(0..1) 지점 */
private fun pointAlong(pts: List<Offset>, t: Float): Offset {
    if (pts.size < 2) return pts.first()
    val lens = pts.zipWithNext { a, b -> (b - a).getDistance() }
    var target = lens.sum() * t
    lens.forEachIndexed { i, l ->
        if (target <= l && l > 0f) return pts[i] + (pts[i + 1] - pts[i]) * (target / l)
        target -= l
    }
    return pts.last()
}
