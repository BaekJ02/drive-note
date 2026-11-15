package com.example.drivenote

import android.content.Context
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
import androidx.compose.ui.draw.clipToBounds
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.osmdroid.util.MapTileIndex
import java.net.HttpURLConnection
import java.net.URL
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
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
private const val PROBE_ZOOM = 12
private val PROBE_TILES = listOf(3493 to 1587, 3494 to 1587) // 경로 근처(서울 동남부) z12 타일
private const val TILE_FAIL_LIMIT = 4 // 성공 없이 이만큼 실패하면 바로 일러스트로 전환 (403·네트워크 오류 등)

/** CARTO Positron 밝은 지도 (OpenStreetMap 기본 타일은 사용 정책상 403으로 막힘) */
private val CartoPositron = XYTileSource(
    "CartoPositron", 0, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/light_all/",
        "https://b.basemaps.cartocdn.com/light_all/",
        "https://c.basemaps.cartocdn.com/light_all/",
        "https://d.basemaps.cartocdn.com/light_all/",
    ),
    "© OpenStreetMap contributors © CARTO",
)

/** 앱 시작 시 한 번 호출: osmdroid User-Agent와 캐시 경로(앱 내부 저장소) 설정 */
fun initOsmdroid(context: Context) {
    try {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            val version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "1.0"
            userAgentValue = "DriveNote/$version (${context.packageName})"
            osmdroidBasePath = File(context.filesDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
    } catch (t: Throwable) {
        Log.w(TAG, "osmdroid 설정 실패", t)
    }
}
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
    var tileFails by remember { mutableStateOf(0) }
    val mapView = remember {
        if (!hasNetwork(context)) {
            Log.w(TAG, "네트워크 없음 → 지도 일러스트로 대체"); null
        } else try {
            buildMapView(context, routeColor, onTileLoaded = { tileLoaded = true }, onTileFailed = { tileFails++ })
        } catch (t: Throwable) {
            Log.w(TAG, "osmdroid 초기화 실패 → 지도 일러스트로 대체", t); null
        }
    }
    var fallback by remember { mutableStateOf(mapView == null) }
    // 타일 서버 사전 확인 결과: null = 확인 중
    var probeOk by remember { mutableStateOf<Boolean?>(if (mapView == null) false else null) }

    // 타일 서버가 403이나 "API KEY REQUIRED" 같은 안내 이미지를 200/403과 함께 보내면
    // osmdroid는 정상 타일로 그려 버리므로, 지도를 보여 주기 전에 직접 확인한다.
    if (mapView != null) {
        LaunchedEffect(Unit) {
            val ok = withContext(Dispatchers.IO) { probeTileServer(context) }
            probeOk = ok
            if (!ok) fallback = true
        }
    }

    // 성공한 타일 없이 실패가 쌓이면 바로 대체 (403, DNS 실패 등)
    if (!fallback && !tileLoaded && tileFails >= TILE_FAIL_LIMIT) {
        Log.w(TAG, "타일 로딩 실패 ${tileFails}회 → 지도 일러스트로 대체")
        fallback = true
    }

    // 일정 시간 안에 타일이 하나도 안 오면 일러스트로 대체
    if (mapView != null) {
        LaunchedEffect(probeOk) {
            if (probeOk != true) return@LaunchedEffect
            delay(TILE_TIMEOUT_MS)
            if (!tileLoaded) { Log.w(TAG, "타일 로딩 실패 → 지도 일러스트로 대체"); fallback = true }
        }
        DisposableEffect(Unit) { onDispose { runCatching { mapView.onDetach() } } }
    }

    Box(modifier.clipToBounds().background(MapBg)) {
        if (fallback || mapView == null) {
            RouteIllustration(routeColor, Modifier.fillMaxSize())
        } else if (probeOk == true) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize().clipToBounds())
            // 지도 데이터·타일 저작권 표시 (사용 조건)
            Text("© OpenStreetMap contributors © CARTO", fontSize = 14.sp, color = Color(0xFF5F6672),
                modifier = Modifier.align(Alignment.BottomEnd).background(Color.White.copy(alpha = 0.8f))
                    .padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

/**
 * 경로 지역의 서로 다른 타일 2장을 받아 본다.
 * 둘 다 HTTP 200 이미지이고 내용이 서로 달라야 정상. (차단·키 필요 안내 이미지는 모든 타일이 똑같음)
 */
private fun probeTileServer(context: Context): Boolean {
    val userAgent = Configuration.getInstance().userAgentValue ?: context.packageName
    val bodies = PROBE_TILES.map { (x, y) ->
        val url = CartoPositron.getTileURLString(MapTileIndex.getTileIndex(PROBE_ZOOM, x, y))
        val conn = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000; readTimeout = 4000
                setRequestProperty("User-Agent", userAgent)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "타일 확인 실패: $url", t); return false
        }
        try {
            val code = conn.responseCode
            val type = conn.contentType.orEmpty()
            if (code != 200 || !type.startsWith("image")) {
                Log.w(TAG, "타일 서버 응답 이상 → 지도 일러스트로 대체 (HTTP $code, $type)"); return false
            }
            conn.inputStream.use { it.readBytes() }
        } catch (t: Throwable) {
            Log.w(TAG, "타일 확인 실패 → 지도 일러스트로 대체: ${t.message}"); return false
        } finally {
            conn.disconnect()
        }
    }
    if (bodies[0].contentEquals(bodies[1])) {
        Log.w(TAG, "서로 다른 타일이 같은 이미지 (API 키 필요·차단 안내로 판단) → 지도 일러스트로 대체")
        return false
    }
    Log.i(TAG, "타일 서버 확인 완료")
    return true
}

/** 터치를 받지 않는 MapView: 부모(홈 화면 스크롤)로 터치가 그대로 넘어간다 */
private class StaticMapView(context: Context) : MapView(context) {
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean = false
}

private fun buildMapView(
    context: Context, routeColor: Color, onTileLoaded: () -> Unit, onTileFailed: () -> Unit,
): MapView {
    val map = StaticMapView(context)
    map.setTileSource(CartoPositron)
    map.setMultiTouchControls(false)
    map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
    map.isTilesScaledToDpi = true
    // 타일 로딩 성공/실패 감지
    map.tileProvider.tileRequestCompleteHandlers.add(Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            MapTileProviderBase.MAPTILE_SUCCESS_ID -> onTileLoaded()
            MapTileProviderBase.MAPTILE_FAIL_ID -> onTileFailed()
        }
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
