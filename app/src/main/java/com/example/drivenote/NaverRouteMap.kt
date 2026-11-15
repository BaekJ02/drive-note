package com.example.drivenote

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PointF
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapOptions
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.MultipartPathOverlay
import com.naver.maps.map.overlay.Overlay
import com.naver.maps.map.overlay.OverlayImage
import kotlin.math.roundToInt

private const val TAG = "HomeMap"

/** 네이버 SDK 사용 불가가 확인되면 앱 실행 중에는 다시 시도하지 않음 */
private var naverSdkFailed = false

/**
 * 최근 주행 지도 (홈, 리포트 상세).
 * 1순위: 네이버 지도 SDK(Dynamic Map). 키가 없거나, 네트워크가 없거나, 인증 실패(401/429/800 등)가
 * 감지되면 2순위: 내장 이미지 지도(드래그·핀치 확대)로 자동 대체.
 */
@Composable
fun HomeRouteMap(
    routeColor: Color, bottomInset: Dp, modifier: Modifier = Modifier,
    overlay: RouteOverlay? = null, // null이면 홈 '최근 주행' 경로
    focus: MapFocus? = null,       // 이 지점으로 카메라 이동
    resetTop: Dp = 16.dp,          // '돌아가기' 버튼 위 여백
) {
    val context = LocalContext.current
    val ov = overlay ?: homeRouteOverlay(routeColor)
    var useSdk by remember {
        val reason = when {
            naverSdkFailed -> "이전에 실패"
            !hasNaverKey(context) -> "Client ID 없음"
            !hasNetwork(context) -> "네트워크 없음"
            else -> null
        }
        Log.i(TAG, if (reason == null) "홈 지도: 네이버 지도 SDK" else "홈 지도: 내장 이미지 (SDK 미사용: $reason)")
        mutableStateOf(reason == null)
    }
    if (useSdk) {
        NaverRouteMap(routeColor, bottomInset, modifier, ov, focus, resetTop) { reason ->
            Log.w(TAG, "네이버 지도 SDK 실패 → 내장 이미지로 대체: $reason")
            naverSdkFailed = true
            useSdk = false
        }
    } else {
        DriveRouteMap(routeColor, modifier, bottomInset = bottomInset, interactive = true, overlay = overlay, focus = focus, resetTop = resetTop)
    }
}

/** 매니페스트에 Client ID가 주입됐는지 (값은 읽기만 하고 출력하지 않음) */
private fun hasNaverKey(context: Context): Boolean = try {
    val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
    !info.metaData?.getString("com.naver.maps.map.NCP_KEY_ID").isNullOrBlank()
} catch (_: Exception) { false }

private fun hasNetwork(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
private fun NaverRouteMap(
    routeColor: Color, bottomInset: Dp, modifier: Modifier, ov: RouteOverlay, focus: MapFocus?, resetTop: Dp,
    onFailed: (String) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var moved by remember { mutableStateOf(false) }
    var map by remember { mutableStateOf<NaverMap?>(null) }

    // 경로 전체가 보이는 처음 카메라: 아래 떠 있는 정보 바만큼 하단 여백
    fun fitRoute(): CameraUpdate {
        val b = LatLngBounds.Builder().apply { ov.route.forEach { include(LatLng(it.lat, it.lon)) } }.build()
        with(density) {
            val pad = 56.dp.roundToPx()
            return CameraUpdate.fitBounds(b, pad, 72.dp.roundToPx(), pad, (bottomInset + 32.dp).roundToPx())
        }
    }

    val mapView = remember {
        // TextureView: Compose의 둥근 모서리 자르기(clip)가 적용되도록
        MapView(context, NaverMapOptions().useTextureView(true)).apply { onCreate(null) }
    }

    // 인증 실패 감지 (전역 리스너라 화면을 벗어나면 해제)
    DisposableEffect(Unit) {
        val sdk = NaverMapSdk.getInstance(context)
        sdk.onAuthFailedListener = NaverMapSdk.OnAuthFailedListener { e ->
            onFailed("인증 실패 ${e.errorCode} (${e.javaClass.simpleName})")
        }
        onDispose { sdk.onAuthFailedListener = null }
    }

    // MapView 생명주기를 화면(Activity) 생명주기에 연결
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit // onCreate는 생성 시, onDestroy는 화면에서 사라질 때 호출
            }
        }
        lifecycle.addObserver(observer) // 현재 상태까지의 이벤트(START, RESUME)가 바로 전달됨
        onDispose {
            lifecycle.removeObserver(observer)
            val state = lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    Box(modifier) {
        AndroidView(factory = {
            mapView.getMapAsync { m ->
                map = m
                m.uiSettings.apply {
                    isZoomControlEnabled = false
                    isCompassEnabled = false
                    isScaleBarEnabled = false
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                    // 네이버 로고(필수 표시)가 정보 바에 가리지 않게 위로
                    with(density) { setLogoMargin(16.dp.roundToPx(), 0, 0, (bottomInset + 12.dp).roundToPx()) }
                }
                m.addOnCameraChangeListener { reason, _ -> if (reason == CameraUpdate.REASON_GESTURE) moved = true }
            }
            mapView
        }, modifier = Modifier.fillMaxSize())

        // 경로(주행)가 바뀌면 오버레이를 다시 그리고 전체가 보이게 맞춤
        LaunchedEffect(map, ov) {
            val m = map ?: return@LaunchedEffect
            val added = addRouteOverlays(m, ov, routeColor, density.density)
            mapView.post { m.moveCamera(fitRoute()) } // 뷰 크기가 정해진 뒤 맞춤
            moved = false
            try { kotlinx.coroutines.awaitCancellation() } finally { added.forEach { it.map = null } }
        }
        // 문제 항목 선택: 그 지점으로 확대·이동
        LaunchedEffect(map, focus) {
            val m = map ?: return@LaunchedEffect
            val f = focus ?: return@LaunchedEffect
            m.moveCamera(CameraUpdate.scrollAndZoomTo(LatLng(f.at.lat, f.at.lon), maxOf(m.cameraPosition.zoom, 14.0))
                .animate(CameraAnimation.Easing))
            moved = true
        }

        if (moved) {
            MapResetButton(Modifier.align(Alignment.TopEnd).padding(top = resetTop, end = 16.dp)) {
                map?.moveCamera(fitRoute().animate(CameraAnimation.Easing))
                moved = false
            }
        }
    }
}

/** 경로(구간별 색, 같은 굵기) + 출발·도착 마커 + 번호 핀. 추가한 오버레이 목록을 돌려줌 (지울 때 사용) */
private fun addRouteOverlays(map: NaverMap, ov: RouteOverlay, routeColor: Color, density: Float): List<Overlay> {
    val route = ov.route.map { LatLng(it.lat, it.lon) }
    val path = MultipartPathOverlay().apply {
        coordParts = ov.parts.map { (from, to, _) -> route.subList(from, to + 1) }
        colorParts = ov.parts.map { (_, _, c) ->
            val argb = c.toArgb()
            MultipartPathOverlay.ColorPart(argb, android.graphics.Color.WHITE, argb, android.graphics.Color.WHITE)
        }
        width = (10 * density).roundToInt()
        outlineWidth = (2 * density).roundToInt()
        this.map = map
    }
    // 출발: 흰 원 + 테두리, 도착: 흰 테두리의 채운 원
    val start = marker(route.first(), circleBitmap(11f, density, routeColor.toArgb(), filled = false), map)
    val end = marker(route.last(), circleBitmap(13f, density, routeColor.toArgb(), filled = true), map)
    val pins = ov.pins.map { p ->
        marker(LatLng(p.at.lat, p.at.lon), pinBitmap(p.no, p.color.toArgb(), density), map).apply { zIndex = 10 + p.no }
    }
    return listOf(path, start, end) + pins
}

/** 번호 핀: 흰 테두리 원 안에 흰 숫자 */
private fun pinBitmap(no: Int, color: Int, density: Float): Bitmap {
    val r = 16 * density
    val size = (r * 2).roundToInt() + 2
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    p.color = android.graphics.Color.WHITE; c.drawCircle(cx, cx, r, p)
    p.color = color; c.drawCircle(cx, cx, r - 3 * density, p)
    p.color = android.graphics.Color.WHITE; p.textSize = 15 * density; p.textAlign = Paint.Align.CENTER
    p.typeface = android.graphics.Typeface.DEFAULT_BOLD
    c.drawText(no.toString(), cx, cx - (p.ascent() + p.descent()) / 2, p)
    return bmp
}

private fun marker(at: LatLng, bmp: Bitmap, map: NaverMap) = Marker().apply {
    position = at
    icon = OverlayImage.fromBitmap(bmp)
    anchor = PointF(0.5f, 0.5f)
    this.map = map
}

private fun circleBitmap(radiusDp: Float, density: Float, color: Int, filled: Boolean): Bitmap {
    val r = radiusDp * density
    val size = (r * 2).roundToInt() + 2
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    p.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cx, r, p)
    p.color = color
    if (filled) {
        c.drawCircle(cx, cx, r - 4 * density, p)
    } else {
        p.style = Paint.Style.STROKE; p.strokeWidth = 4 * density
        c.drawCircle(cx, cx, r - 2 * density, p)
    }
    return bmp
}
