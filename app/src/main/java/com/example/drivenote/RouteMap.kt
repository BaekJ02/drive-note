package com.example.drivenote

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
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
private const val NAVER_LEVEL_OFFSET = 1 // tools/make_route_map.py의 LEVEL_OFFSET과 같아야 함
private val Casing = Color.White

/** 번호 핀 (리포트 문제 지점) */
data class MapPin(val no: Int, val at: GeoPt, val color: Color)

/** 지도에 그릴 경로: 좌표, 구간별 색(시작·끝 인덱스, 색), 번호 핀 */
data class RouteOverlay(val route: List<GeoPt>, val parts: List<Triple<Int, Int, Color>>, val pins: List<MapPin> = emptyList())

/** 지도 이동 요청 (같은 지점을 다시 눌러도 동작하도록 seq를 바꿔 보냄) */
data class MapFocus(val at: GeoPt, val seq: Int)

/** 홈 '최근 주행' 경로: 리포트의 같은 주행(11/15 08:42)과 같은 문제 구간·분류 색 (번호 핀 없음) */
fun homeRouteOverlay(routeColor: Color) = tripRouteOverlay(ReportData.latestTrip, routeColor).copy(pins = emptyList())

/** 리포트 주행 경로: 문제 구간 색 + 구간 가운데 번호 핀 */
fun tripRouteOverlay(t: TripLog, routeColor: Color) = RouteOverlay(
    t.route,
    routeParts(routeColor, t.route.lastIndex, t.issues.map { Triple(it.from, it.to, it.type.color) }),
    t.issues.map { MapPin(it.no, issuePoint(t, it), it.type.color) },
)

fun issuePoint(t: TripLog, i: DriveIssue): GeoPt = t.route[(i.from + i.to) / 2]

/**
 * 최근 주행 경로 지도.
 * - 내장 지도 이미지(route_map.png)가 있으면: 이미지 위에 Web Mercator로 변환한 경로를 그림
 * - 없으면(다운로드 실패 등): Canvas로 그린 대체 지도 위에 경로를 그림
 *
 * @param showAllSegments true면 습관 감지 구간을 모두 분류 색으로 강조 (홈)
 * @param habit 지정하면 그 습관의 구간만 강조하고 발생 지점 마커를 표시 (리포트 상세)
 * @param bottomInset 지도 아래쪽에 다른 요소(떠 있는 정보 바 등)가 겹치는 높이.
 *   경로는 그 위쪽 영역 안에 맞춰 그리고, 저작권 표기도 그 위로 올림 (기본 0 = 기존과 같음)
 * @param interactive true면 드래그·핀치 확대(최대 2.5배)와 '돌아가기' 버튼을 쓰고,
 *   경로를 구간별 같은 굵기로 이어 그려 색만 바뀌게 함. false면 기존 방식
 * @param overlay interactive일 때 그릴 경로 (null이면 홈 '최근 주행')
 * @param focus interactive일 때 이 지점으로 확대·이동
 */
@Composable
fun DriveRouteMap(
    routeColor: Color, modifier: Modifier = Modifier, showAllSegments: Boolean = true, habit: Habit? = null,
    bottomInset: Dp = 0.dp, interactive: Boolean = false, overlay: RouteOverlay? = null, focus: MapFocus? = null,
    resetTop: Dp = 16.dp, // '돌아가기' 버튼 위 여백 (홈은 오른쪽 위 '개선할 점' 버튼 아래로)
) {
    val ov = overlay ?: homeRouteOverlay(routeColor)
    val context = LocalContext.current
    val image = remember { loadRouteMapImage(context) }
    val measurer = rememberTextMeasurer()
    val view = remember { MapViewport() }
    val scope = rememberCoroutineScope()
    val gestures = interactive && image != null // Canvas 대체 지도는 확대 대상 아님
    // 경로가 바뀌면 처음 상태로
    LaunchedEffect(ov.route) { view.zoom = 1f; view.tx = 0f; view.ty = 0f }
    LaunchedEffect(focus) {
        val f = focus ?: return@LaunchedEffect
        if (!gestures) return@LaunchedEffect
        val (z0, x0, y0) = Triple(view.zoom, view.tx, view.ty)
        val (z1, x1, y1) = view.focusTarget(f.at, 2f) ?: return@LaunchedEffect
        animate(0f, 1f) { t, _ -> view.zoom = z0 + (z1 - z0) * t; view.tx = x0 + (x1 - x0) * t; view.ty = y0 + (y1 - y0) * t }
    }

    Box(modifier.clipToBounds().background(MapIllustrationBg)) {
        val canvasMod = if (gestures) Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { centroid, pan, zoom, _ -> view.transform(centroid, pan, zoom) }
        } else Modifier.fillMaxSize()
        Canvas(canvasMod) {
            val project: (GeoPt) -> Offset = if (image != null) {
                drawImageProjection(image, bottomInset.toPx(), if (gestures) view else null, if (interactive) ov.route else MockData.lastDriveRoute)
            } else {
                drawMapBase()
                fitProjection(bottomInset.toPx(), if (interactive) ov.route else MockData.lastDriveRoute)
            }
            if (interactive) drawRouteUniform(project, routeColor, ov) else drawRoute(project, routeColor, showAllSegments, habit)
            if (image == null && overlay == null) {
                // 대체 지도에는 지명이 없으므로 출발·도착 지명을 표시
                drawPlaceLabel(measurer, "시흥동", project(MockData.lastDriveRoute.first()))
                drawPlaceLabel(measurer, "삼성동", project(MockData.lastDriveRoute.last()))
            }
        }
        if (image != null) {
            // 네이버 지도 저작권 표시 (사용 조건)
            Text("© NAVER Corp.", fontSize = 14.sp, color = Color(0xFF5F6672),
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = bottomInset).background(Color.White.copy(alpha = 0.8f))
                    .padding(horizontal = 6.dp, vertical = 2.dp))
        }
        if (gestures && view.moved) {
            MapResetButton(Modifier.align(Alignment.TopEnd).padding(top = resetTop, end = 16.dp)) {
                // 처음 위치로 부드럽게 되돌림
                val (z0, x0, y0) = Triple(view.zoom, view.tx, view.ty)
                scope.launch {
                    animate(0f, 1f) { t, _ ->
                        view.zoom = z0 + (1f - z0) * t; view.tx = x0 * (1 - t); view.ty = y0 * (1 - t)
                    }
                }
            }
        }
    }
}

/** 지도 오른쪽 위 '돌아가기' 버튼 (작은 흰 배경 + 그림자). 네이버 SDK 지도와 대체 지도가 같이 씀 */
@Composable
fun MapResetButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp),
        color = Color.White, shadowElevation = 6.dp,
    ) {
        Text("돌아가기", fontSize = 22.sp, color = Color(0xFF111418),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
    }
}

/**
 * 대체 지도 확대·이동 상태. 화면 좌표 = 기본 좌표 * zoom + (tx, ty).
 * 기본 상태에서 이미지가 차지하는 영역(base*)은 그릴 때 갱신하고, 이동 범위 제한에 씀.
 */
private class MapViewport {
    var zoom by mutableFloatStateOf(1f)
    var tx by mutableFloatStateOf(0f)
    var ty by mutableFloatStateOf(0f)
    val moved get() = zoom > 1.001f || kotlin.math.abs(tx) > 0.5f || kotlin.math.abs(ty) > 0.5f
    // 기본 상태 이미지 영역(left, top, right, bottom)과 화면 크기 (상태 아님: 그리기 중 갱신)
    var baseL = 0f; var baseT = 0f; var baseR = 0f; var baseB = 0f
    var w = 0f; var h = 0f; var areaH = 0f
    var baseProject: ((GeoPt) -> Offset)? = null // 확대·이동 전 좌표 변환

    /** g를 경로 영역 가운데에 두는 (zoom, tx, ty). 범위 제한 적용 */
    fun focusTarget(g: GeoPt, z: Float): Triple<Float, Float, Float>? {
        val p = baseProject?.invoke(g) ?: return null
        val nx = (w / 2 - p.x * z).coerceIn(w - baseR * z, -baseL * z)
        val ny = (areaH / 2 - p.y * z).coerceIn(h - baseB * z, -baseT * z)
        return Triple(z, nx, ny)
    }

    fun transform(centroid: Offset, pan: Offset, zoomBy: Float) {
        val nz = (zoom * zoomBy).coerceIn(1f, MAX_ZOOM)
        val k = nz / zoom
        // 손가락 중심점이 같은 지도 위치에 머물도록 확대 + 이동
        var nx = centroid.x - (centroid.x - tx) * k + pan.x
        var ny = centroid.y - (centroid.y - ty) * k + pan.y
        // 이미지가 화면 밖으로 벗어나 빈 영역이 보이지 않게 제한
        nx = nx.coerceIn(w - baseR * nz, -baseL * nz)
        ny = ny.coerceIn(h - baseB * nz, -baseT * nz)
        zoom = nz; tx = nx; ty = ny
    }

    companion object { const val MAX_ZOOM = 2.5f }
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

/** 위경도 → Web Mercator 월드 픽셀 (256px 타일 기준 줌) */
private fun worldPixel(g: GeoPt, zoom: Int): Pair<Double, Double> {
    val n = TILE * (1 shl zoom)
    val x = (g.lon + 180.0) / 360.0 * n
    val r = Math.toRadians(g.lat)
    val y = (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * n
    return x to y
}

/**
 * 위경도 → 내장 이미지(네이버 Static Map)의 픽셀 좌표.
 * 네이버 level L = 256px 타일 줌 L+1 (실제 지도 이미지에 경로를 겹쳐 확인함).
 * 이미지 중심 = center, 1 월드 픽셀 = scale 이미지 픽셀 (scale=2면 2배 해상도)
 */
private fun imagePixel(g: GeoPt): Offset {
    val zoom = MockData.routeMapLevel + NAVER_LEVEL_OFFSET
    val (cx, cy) = worldPixel(GeoPt(MockData.routeMapCenterLat, MockData.routeMapCenterLon), zoom)
    val (x, y) = worldPixel(g, zoom)
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
private fun DrawScope.drawImageProjection(
    image: ImageBitmap, bottomInset: Float = 0f, view: MapViewport? = null, route: List<GeoPt> = MockData.lastDriveRoute,
): (GeoPt) -> Offset {
    val px = route.map(::imagePixel)
    val minX = px.minOf { it.x }; val maxX = px.maxOf { it.x }
    val minY = px.minOf { it.y }; val maxY = px.maxOf { it.y }
    val pad = 40.dp.toPx()
    val areaH = size.height - bottomInset // 경로를 둘 영역 (아래 겹치는 부분 제외)
    val fit = min((size.width - 2 * pad) / (maxX - minX), (areaH - 2 * pad) / (maxY - minY))
    val cover = max(size.width / image.width, size.height / image.height)
    val s = max(fit, cover)
    // 경로 중심을 화면 중심에 두되, 이미지가 화면을 벗어나지 않게 제한
    var ox = size.width / 2 - (minX + maxX) / 2 * s
    var oy = areaH / 2 - (minY + maxY) / 2 * s
    ox = ox.coerceIn(size.width - image.width * s, 0f)
    oy = oy.coerceIn(size.height - image.height * s, 0f)
    // 사용자 확대·이동 (없으면 그대로)
    val z = view?.zoom ?: 1f; val tx = view?.tx ?: 0f; val ty = view?.ty ?: 0f
    view?.apply {
        baseL = ox; baseT = oy; baseR = ox + image.width * s; baseB = oy + image.height * s
        w = size.width; h = size.height; this.areaH = areaH
        baseProject = { g -> val p = imagePixel(g); Offset(ox + p.x * s, oy + p.y * s) }
    }
    drawImage(
        image,
        srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset((ox * z + tx).roundToInt(), (oy * z + ty).roundToInt()),
        dstSize = IntSize((image.width * s * z).roundToInt(), (image.height * s * z).roundToInt()),
    )
    return { g -> val p = imagePixel(g); Offset((ox + p.x * s) * z + tx, (oy + p.y * s) * z + ty) }
}

/** 대체 지도: 경로 영역을 화면 비율에 맞춰 가운데에 배치 (경도는 위도에 따라 축소) */
private fun DrawScope.fitProjection(bottomInset: Float = 0f, route: List<GeoPt> = MockData.lastDriveRoute): (GeoPt) -> Offset {
    val k = cos(Math.toRadians(route.map { it.lat }.average()))
    val minLat = route.minOf { it.lat }; val maxLat = route.maxOf { it.lat }
    val minLon = route.minOf { it.lon }; val maxLon = route.maxOf { it.lon }
    val pad = 36.dp.toPx()
    val areaH = size.height - bottomInset
    val scale = min((size.width - 2 * pad) / ((maxLon - minLon) * k), (areaH - 2 * pad) / (maxLat - minLat))
    val offX = (size.width - (maxLon - minLon) * k * scale) / 2
    val offY = (areaH - (maxLat - minLat) * scale) / 2
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

/**
 * 홈 지도용 경로: 전체를 같은 굵기로, 습관 구간만 색을 바꿔 이어 그림 (덧칠 없음).
 * 흰 테두리를 경로 전체에 한 번 깔고, 그 위에 구간별 색 선을 둥근 끝·둥근 연결로 그림.
 */
private fun DrawScope.drawRouteUniform(project: (GeoPt) -> Offset, routeColor: Color, ov: RouteOverlay) {
    val route = ov.route
    val pts = route.map(project)
    fun path(from: Int, to: Int) = Path().apply {
        pts.subList(from, to + 1).forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    }
    fun stroke(w: Float) = Stroke(w.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(path(0, route.lastIndex), Casing, style = stroke(14f)) // 테두리 (선 10 + 양쪽 2)
    ov.parts.forEach { (from, to, color) -> drawPath(path(from, to), color, style = stroke(10f)) }

    val start = pts.first(); val end = pts.last()
    drawCircle(Color.White, 11.dp.toPx(), start)
    drawCircle(routeColor, 11.dp.toPx(), start, style = Stroke(4.dp.toPx()))
    drawCircle(Color.White, 13.dp.toPx(), end)
    drawCircle(routeColor, 9.dp.toPx(), end)

    // 번호 핀: 흰 테두리 원 안에 숫자
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; textSize = 15.dp.toPx(); textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    ov.pins.forEach { pin ->
        val p = project(pin.at)
        drawCircle(Color.Black.copy(alpha = 0.15f), 17.dp.toPx(), p + Offset(0f, 2.dp.toPx()))
        drawCircle(Color.White, 16.dp.toPx(), p)
        drawCircle(pin.color, 13.dp.toPx(), p)
        drawContext.canvas.nativeCanvas.drawText(pin.no.toString(), p.x, p.y - (paint.ascent() + paint.descent()) / 2, paint)
    }
}

/**
 * 경로를 습관 구간 기준으로 나눈 조각 목록 (시작 인덱스, 끝 인덱스, 색). 이웃 조각은 끝점을 공유해 끊김 없이 이어짐.
 * 네이버 SDK 지도(MultipartPathOverlay)와 대체 지도가 같이 씀.
 */
fun routeParts(routeColor: Color, last: Int, segments: List<Triple<Int, Int, Color>>): List<Triple<Int, Int, Color>> {
    val parts = mutableListOf<Triple<Int, Int, Color>>()
    var i = 0
    segments.sortedBy { it.first }.forEach { (from, to, color) ->
        val f = maxOf(from, i) // 앞 구간과 겹치면 겹친 부분은 앞 구간 색
        if (f > i) parts += Triple(i, f, routeColor)
        if (to > f) { parts += Triple(f, to, color); i = to }
    }
    if (i < last) parts += Triple(i, last, routeColor)
    return parts
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
