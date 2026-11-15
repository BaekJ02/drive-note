package com.example.drivenote

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.cos
import kotlin.math.min

private const val TAG = "RecentDriveMap"
private const val TILE_TIMEOUT_MS = 8000L
private val MapBg = Color(0xFFEDF1F6)
private val Casing = Color.White

/**
 * 홈 '최근 주행' 지도 미리보기.
 * osmdroid로 실제 지도 위에 경로를 그리고, 초기화 실패·네트워크 없음·타일 로딩 실패 시
 * Canvas로 그린 지도 일러스트로 대체한다. 정적인 미리보기라 터치로 움직이지 않는다.
 */
@Composable
fun RecentDriveMap(routeColor: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tileLoaded by remember { mutableStateOf(false) }
    val mapView = remember {
        if (!hasNetwork(context)) {
            Log.w(TAG, "네트워크 없음 → 지도 일러스트로 대체"); null
        } else try {
            buildMapView(context, routeColor) { tileLoaded = true }
        } catch (t: Throwable) {
            Log.w(TAG, "osmdroid 초기화 실패 → 지도 일러스트로 대체", t); null
        }
    }
    var fallback by remember { mutableStateOf(mapView == null) }

    // 일정 시간 안에 타일이 하나도 안 오면 일러스트로 대체
    if (mapView != null) {
        LaunchedEffect(Unit) {
            delay(TILE_TIMEOUT_MS)
            if (!tileLoaded) { Log.w(TAG, "타일 로딩 실패 → 지도 일러스트로 대체"); fallback = true }
        }
        DisposableEffect(Unit) { onDispose { runCatching { mapView.onDetach() } } }
    }

    Box(modifier.background(MapBg)) {
        if (fallback || mapView == null) {
            RouteIllustration(routeColor, Modifier.fillMaxSize())
        } else {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            // OpenStreetMap 저작권 표시 (타일 사용 조건)
            Text("© OpenStreetMap", fontSize = 14.sp, color = Color(0xFF5F6672),
                modifier = Modifier.align(Alignment.BottomEnd).background(Color.White.copy(alpha = 0.8f))
                    .padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

/** 터치를 받지 않는 MapView: 부모(홈 화면 스크롤)로 터치가 그대로 넘어간다 */
private class StaticMapView(context: Context) : MapView(context) {
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean = false
}

private fun buildMapView(context: Context, routeColor: Color, onTileLoaded: () -> Unit): MapView {
    // osmdroid 설정: user agent 필수, 캐시는 앱 전용 폴더 (저장소 권한 불필요)
    Configuration.getInstance().apply {
        load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        userAgentValue = context.packageName
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
    }

    val map = StaticMapView(context)
    map.setTileSource(TileSourceFactory.MAPNIK)
    map.setMultiTouchControls(false)
    map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
    map.isTilesScaledToDpi = true
    // 경로가 돋보이도록 지도 타일 채도를 낮춤 (절제된 회색 톤)
    map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.15f) }))

    // 타일 로딩 성공 감지
    map.tileProvider.tileRequestCompleteHandlers.add(Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == MapTileProviderBase.MAPTILE_SUCCESS_ID) onTileLoaded()
        false
    })

    val density = context.resources.displayMetrics.density
    val pts = MockData.lastDriveRoute.map { GeoPoint(it.lat, it.lon) }

    fun line(points: List<GeoPoint>, color: Int, widthDp: Float) = Polyline(map).apply {
        setPoints(points)
        outlinePaint.color = color
        outlinePaint.strokeWidth = widthDp * density
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.strokeJoin = Paint.Join.ROUND
        infoWindow = null
    }

    // 전체 경로: 흰 테두리 + 파란 선
    map.overlays += line(pts, Casing.toArgb(), 11f)
    map.overlays += line(pts, routeColor.toArgb(), 6f)
    // 습관 감지 구간: 분류 색으로 굵게 덮어 그림
    MockData.lastDriveSegments.forEach { s ->
        val color = MockData.habits.first { it.id == s.habitId }.category.color
        map.overlays += line(pts.subList(s.from, s.to + 1), Casing.toArgb(), 15f)
        map.overlays += line(pts.subList(s.from, s.to + 1), color.toArgb(), 10f)
    }
    // 출발(흰 원 + 파란 테두리) · 도착(파란 원)
    map.overlays += dotMarker(map, pts.first(), fill = Color.White, stroke = routeColor, density)
    map.overlays += dotMarker(map, pts.last(), fill = routeColor, stroke = Color.White, density)

    // 첫 레이아웃 후 경로 전체가 보이게 맞춤
    map.addOnFirstLayoutListener { _, _, _, _, _ ->
        runCatching { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), false, (36 * density).toInt()) }
    }
    return map
}

private fun dotMarker(map: MapView, p: GeoPoint, fill: Color, stroke: Color, density: Float) = Marker(map).apply {
    position = p
    icon = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill.toArgb())
        setStroke((4 * density).toInt(), stroke.toArgb())
        val size = (22 * density).toInt()
        setSize(size, size)
    }
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    setInfoWindow(null)
}

private fun hasNetwork(context: Context): Boolean = try {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
} catch (t: Throwable) {
    false
}

/** 대체용 지도 일러스트: 같은 경로 좌표를 화면 비율에 맞춰 투영해 그림 */
@Composable
private fun RouteIllustration(routeColor: Color, modifier: Modifier) {
    val route = MockData.lastDriveRoute
    Canvas(modifier) {
        // 연한 도로 격자
        val grid = 56.dp.toPx()
        var x = grid / 2
        while (x < size.width) { drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 6.dp.toPx()); x += grid }
        var y = grid / 2
        while (y < size.height) { drawLine(Color.White, Offset(0f, y), Offset(size.width, y), 6.dp.toPx()); y += grid }

        // 위경도 → 화면 좌표 (경도는 위도에 따라 축소)
        val k = cos(Math.toRadians(route.map { it.lat }.average()))
        val minLat = route.minOf { it.lat }; val maxLat = route.maxOf { it.lat }
        val minLon = route.minOf { it.lon }; val maxLon = route.maxOf { it.lon }
        val pad = 36.dp.toPx()
        val scale = min((size.width - 2 * pad) / ((maxLon - minLon) * k), (size.height - 2 * pad) / (maxLat - minLat))
        val offX = (size.width - (maxLon - minLon) * k * scale) / 2
        val offY = (size.height - (maxLat - minLat) * scale) / 2
        fun pt(g: GeoPt) = Offset((offX + (g.lon - minLon) * k * scale).toFloat(), (offY + (maxLat - g.lat) * scale).toFloat())
        fun path(from: Int, to: Int) = Path().apply {
            route.subList(from, to + 1).forEachIndexed { i, g -> val p = pt(g); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        fun stroke(w: Float) = Stroke(w.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        val all = path(0, route.lastIndex)
        drawPath(all, Casing, style = stroke(11f))
        drawPath(all, routeColor, style = stroke(6f))
        MockData.lastDriveSegments.forEach { s ->
            val c = MockData.habits.first { it.id == s.habitId }.category.color
            val p = path(s.from, s.to)
            drawPath(p, Casing, style = stroke(15f))
            drawPath(p, c, style = stroke(10f))
        }
        val start = pt(route.first()); val end = pt(route.last())
        drawCircle(Color.White, 11.dp.toPx(), start)
        drawCircle(routeColor, 11.dp.toPx(), start, style = Stroke(4.dp.toPx()))
        drawCircle(Color.White, 13.dp.toPx(), end)
        drawCircle(routeColor, 9.dp.toPx(), end)
    }
}
