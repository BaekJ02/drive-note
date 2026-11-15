package com.example.drivenote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Date
import java.util.Locale

// ---- 디자인 토큰 (차량 화면: 큰 글자, 큰 터치 영역)
private val Bg = Color(0xFFF4F5F7)
private val Surface = Color.White
private val Ink = Color(0xFF111418)
private val Sub = Color(0xFF5F6672)
private val Line = Color(0xFFE2E5EA)
private val Accent = Color(0xFF1A73E8)
private val Danger = Color(0xFFD9443B)
private val Positive = Color(0xFF2E9E5B)
private val SelectedBg = Color(0xFFE8F0FE) // 선택된 목록 행 배경, 파란 배지 배경
private val DangerBg = Color(0xFFFCE8E6)   // 연한 빨강 배지 배경
private val NeutralBg = Color(0xFFF1F3F4)  // 회색 배지 배경
private val IconGray = Color(0xFF3C4149)   // 흰 원형 버튼 아이콘
private val ChartBg = Color(0xFFF6F7F9)    // 비교 그래프 배경
private val RecommendGray = Color(0xFF9AA0A6) // 비교 그래프 '권장' 선

private val TOUCH = 100.dp          // 차량 화면용 최소 터치 영역
private val SCREEN_PAD = 40.dp      // 콘텐츠 영역 바깥 여백
private val GAP = 24.dp             // 패널 사이 간격
private val PANEL_PAD = 32.dp       // 패널 안쪽 여백
private val RADIUS = 24.dp          // 패널 모서리
private val ITEM_RADIUS = 18.dp     // 버튼·목록 항목 모서리

private val DISPLAY = 64.sp
private val TITLE = 48.sp
private val HEADLINE = 40.sp
private val BODY = 32.sp
private val SMALL = 26.sp

enum class Tab(val label: String) { HOME("홈"), REPORT("리포트"), TREND("개선 추이"), SETTINGS("설정") }
@Composable
fun DriveNoteApp(vehicle: VehicleRepository, engine: CoachingEngine, speak: (String) -> Unit) {
    var gear by remember { mutableStateOf(DriveGear.P) }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var voiceOn by remember { mutableStateOf(true) }
    var hasNewReport by remember { mutableStateOf(true) }
    val focus = remember { mutableStateListOf(2, 3) } // 집중 개선 항목 id

    val sdkGear by vehicle.gear.collectAsState()
    val connected by vehicle.connected.collectAsState()
    val latest by engine.latest.collectAsState()
    val trip by engine.trip.collectAsState()
    val driving = gear != DriveGear.P

    // 기어 변경: P가 아니면 주행 시작, P로 돌아오면 주행 종료 + 리포트 탭 자동 표시
    fun changeGear(new: DriveGear) {
        val wasDriving = gear != DriveGear.P
        gear = new
        if (!wasDriving && new != DriveGear.P) engine.startTrip()
        if (wasDriving && new == DriveGear.P) {
            engine.endTrip()
            hasNewReport = true; tab = Tab.REPORT
        }
    }
    // 실제 기어(Vehicle SDK)가 들어오면 그 값을 따름. 값이 없으면 데모 P/D 버튼으로 전환
    LaunchedEffect(sdkGear) { sdkGear?.let { changeGear(it) } }
    // 새 코칭이 감지되면 음성으로 읽어 줌
    LaunchedEffect(latest?.seq) {
        val e = latest ?: return@LaunchedEffect
        if (voiceOn && driving) speak(e.type.message)
    }

    ProvideTextStyle(LocalTextStyle.current.copy(lineHeight = 1.4.em)) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            if (driving) {
                // 주행 중: 화면 조작 없음, 탭 이동 불가. 기록 상태와 최근 코칭만 표시 (주행 규제 준수)
                DrivingScreen(voiceOn, gear, latest, connected, vehicle)
            } else {
                Row(Modifier.fillMaxSize()) {
                    SideRail(tab, hasNewReport) { tab = it; if (it == Tab.REPORT) hasNewReport = false }
                    Box(Modifier.weight(1f).fillMaxHeight().padding(SCREEN_PAD)) {
                        when (tab) {
                            Tab.HOME -> HomeScreen(voiceOn, { voiceOn = it }) { tab = Tab.REPORT; hasNewReport = false }
                            Tab.REPORT -> ReportScreen()
                            Tab.TREND -> TrendScreen(focus)
                            Tab.SETTINGS -> SettingsScreen(voiceOn, { voiceOn = it }, focus)
                        }
                    }
                }
            }
            // 데모 전용: SDK 기어 값이 없을 때(일반 에뮬레이터 등) 시연용으로 P/D 전환
            DemoGearSwitch(gear, Modifier.align(Alignment.BottomStart).padding(24.dp)) { changeGear(it) }
        }
    }
}

// ---------------- 공통 컴포넌트

@Composable
private fun SideRail(current: Tab, newBadge: Boolean, onSelect: (Tab) -> Unit) {
    Column(
        Modifier.width(300.dp).fillMaxHeight().background(Surface)
            .border(1.dp, Line).padding(vertical = SCREEN_PAD, horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("드라이브 노트", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Ink,
            modifier = Modifier.padding(start = 20.dp, bottom = 28.dp))
        Tab.values().forEach { t ->
            val sel = t == current
            Row(
                Modifier.fillMaxWidth().height(TOUCH).clip(RoundedCornerShape(ITEM_RADIUS))
                    .background(if (sel) Accent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSelect(t) }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t.label, fontSize = BODY, color = if (sel) Accent else Ink,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                if (t == Tab.REPORT && newBadge) {
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Danger))
                }
            }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(RADIUS)).background(Surface)
            .border(1.dp, Line, RoundedCornerShape(RADIUS)).padding(PANEL_PAD),
        content = content
    )
}

/** 화면 제목 + 보조 설명 */
@Composable
private fun ScreenHeader(title: String, subtitle: String? = null) {
    Column {
        Text(title, fontSize = TITLE, fontWeight = FontWeight.Bold, color = Ink)
        if (subtitle != null) Text(subtitle, fontSize = BODY, color = Sub, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 패널 안의 작은 소제목 */
@Composable
private fun Label(text: String, color: Color = Sub) {
    Text(text, fontSize = SMALL, fontWeight = FontWeight.SemiBold, color = color)
}

/** 패널 안의 항목 제목 (설정 등) */
@Composable
private fun ItemTitle(text: String) {
    Text(text, fontSize = BODY, fontWeight = FontWeight.SemiBold, color = Ink)
}

/** 분류 표시: 분류 색의 작은 원 + 일반 굵기 이름 (색만으로 구분하지 않도록 이름을 함께 표시) */
@Composable
private fun CategoryTag(c: Category) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c.color))
        Spacer(Modifier.width(10.dp))
        Text(c.label, fontSize = SMALL, color = Sub)
    }
}

// ---------------- 홈
@Composable
private fun HomeScreen(voiceOn: Boolean, setVoice: (Boolean) -> Unit, openReport: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showShare by remember { mutableStateOf(false) }
    fun toast(msg: String) = scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) }
    val today = remember { SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(Date()) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        // 인사말: 이름만 굵게
        Column {
            Text(buildAnnotatedString {
                append("안녕하세요, ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${MockData.userName}님") }
                append(". 오늘도 안전 운전해요.")
            }, fontSize = 60.sp, color = Ink)
            // 날짜 | 주차 상태: 아이콘 + 텍스트 묶음 두 개, 얇은 세로 구분선
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                HomeStatusItem(R.drawable.ic_calendar, today)
                Box(Modifier.padding(horizontal = 28.dp).width(1.dp).height(28.dp).background(Line))
                HomeStatusItem(R.drawable.ic_parking, "주차 중")
            }
        }

        // 최근 주행: 지도가 카드 전체를 채우고, 정보 바가 지도 위에 떠 있음 (남은 공간을 거의 다 차지)
        val barInset = HOME_BAR_HEIGHT + HOME_BAR_MARGIN // 경로·저작권 표기를 바 위쪽에 두기 위한 높이
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(RADIUS))
        ) {
            // 1순위 네이버 지도 SDK, 실패 시 내장 이미지 (드래그·핀치 확대, '돌아가기')
            HomeRouteMap(Accent, bottomInset = barInset, modifier = Modifier.fillMaxSize())
            Text(
                "최근 주행", fontSize = SMALL, fontWeight = FontWeight.SemiBold, color = Ink,
                modifier = Modifier.align(Alignment.TopStart).padding(20.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp)).background(Color.White, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
            FloatingDriveBar(openReport, Modifier.align(Alignment.BottomCenter))
            // 원형 버튼 2개: 오른쪽 끝을 '리포트 보기' 버튼 오른쪽 끝에 맞추고, 바 위로 16dp
            Column(
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = HOME_BAR_MARGIN + HOME_BAR_END_PAD, bottom = barInset + 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoundIconButton(
                    if (voiceOn) R.drawable.ic_volume_up else R.drawable.ic_volume_off,
                    if (voiceOn) "음성 코칭 끄기" else "음성 코칭 켜기",
                    bg = if (voiceOn) Accent else Color.White, tint = if (voiceOn) Color.White else IconGray,
                ) {
                    setVoice(!voiceOn)
                    toast(if (!voiceOn) "주행 중 음성 코칭을 켰어요" else "주행 중 음성 코칭을 껐어요")
                }
                RoundIconButton(R.drawable.ic_share, "주행 리포트 공유", bg = Color.White, tint = IconGray) { showShare = true }
            }
            // 스낵바: 지도 하단, 떠 있는 바 바로 위 가운데
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = barInset + 8.dp))
        }
    }
    if (showShare) {
        ShareDialog(onDismiss = { showShare = false }) {
            showShare = false
            toast("열람 전용 링크를 복사했어요")
        }
    }
}

/** 지도 위 원형 아이콘 버튼 (지름 56dp, 옅은 그림자, 아이콘만) */
@Composable
private fun RoundIconButton(icon: Int, description: String, bg: Color, tint: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = bg, shadowElevation = 4.dp, modifier = Modifier.size(56.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = Modifier.size(26.dp))
        }
    }
}

/** 주행 리포트 공유 다이얼로그 (목업: 실제 링크는 만들지 않음) */
@Composable
private fun ShareDialog(onDismiss: () -> Unit, onCopy: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("주행 리포트 공유", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Ink) },
        text = {
            Column {
                Text("공유한 사람은 리포트를 볼 수만 있어요.", fontSize = SMALL, color = Sub)
                MockData.shareTargets.forEach { (name, role) ->
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$name · $role", fontSize = BODY, color = Ink, modifier = Modifier.weight(1f))
                        Pill("열람 중", NeutralBg, Sub, Modifier.padding(start = 24.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onCopy, modifier = Modifier.height(64.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent), contentPadding = PaddingValues(horizontal = 28.dp)) {
                Text("링크 복사", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(64.dp)) {
                Text("닫기", fontSize = 26.sp, color = Sub)
            }
        },
    )
}

private val HOME_BAR_HEIGHT = 108.dp
private val HOME_BAR_END_PAD = 20.dp // 바 안쪽 오른쪽 여백 ('리포트 보기' 버튼 오른쪽 끝 위치)
private val HOME_BAR_MARGIN = 24.dp
private val HOME_BAR_SHAPE = RoundedCornerShape(28.dp)

/**
 * 지도 위에 떠 있는 최근 주행 정보 바.
 * 반투명 흰 배경 + 얇은 흰 테두리 + 그림자.
 */
@Composable
private fun FloatingDriveBar(openReport: () -> Unit, modifier: Modifier) {
    val d = MockData.lastDrive
    Box(
        modifier.padding(start = HOME_BAR_MARGIN, end = HOME_BAR_MARGIN, bottom = HOME_BAR_MARGIN)
            .fillMaxWidth().height(HOME_BAR_HEIGHT)
            .shadow(28.dp, HOME_BAR_SHAPE, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
            .clip(HOME_BAR_SHAPE)
    ) {
        Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.92f)).border(1.dp, Color.White.copy(alpha = 0.6f), HOME_BAR_SHAPE))
        Row(Modifier.fillMaxSize().padding(start = 32.dp, end = HOME_BAR_END_PAD), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("${MockData.lastDriveDepart} → ${MockData.lastDriveArrive}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("시흥동 → 삼성동", fontSize = 24.sp, color = Sub)
            }
            BarDivider()
            BarStat("소요", "${d.minutes}분")
            BarDivider()
            BarStat("거리", "${d.km}km")
            BarDivider()
            BarStat("개선 필요", "${MockData.lastDriveIssues}건")
            Spacer(Modifier.weight(1f))
            Button(onClick = openReport, modifier = Modifier.height(60.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent), contentPadding = PaddingValues(horizontal = 28.dp)) {
                Text("리포트 보기", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BarDivider() {
    Box(Modifier.padding(horizontal = 28.dp).width(1.dp).height(52.dp).background(Line))
}

/** 정보 바 수치: 작은 보조 색 라벨 위, 크고 굵은 숫자 아래 */
@Composable
private fun BarStat(label: String, value: String) {
    Column {
        Text(label, fontSize = 22.sp, color = Sub)
        Text(value, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
    }
}

/** 홈 상단 상태 묶음: 아이콘 + 보조 텍스트 */
@Composable
private fun HomeStatusItem(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = Sub, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = BODY, color = Sub)
    }
}

// ---------------- 주행 중 (정보 최소화, 조작 요소 없음)
@Composable
private fun DrivingScreen(voiceOn: Boolean, gear: DriveGear, latest: CoachingEvent?, connected: Boolean, vehicle: VehicleRepository) {
    // 실시간 차량 값 (시연에서 바뀌는 게 보이도록 작게 표시)
    val speed by vehicle.speedKmh.collectAsState()
    val steering by vehicle.steeringDeg.collectAsState()
    val signal by vehicle.turnSignal.collectAsState()
    val brake by vehicle.brakeOn.collectAsState()
    // SDK 연결 전(데모 모드)에는 목업 문구, 연결 후에는 실제 감지 메시지
    val message = latest?.type?.message ?: if (connected) null else MockData.recentCoaching
    val category = latest?.let { MockData.habits.first { h -> h.id == it.type.templateId }.category }
        ?: MockData.recentCoachingCategory
    Column(
        Modifier.fillMaxSize().padding(SCREEN_PAD),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Danger.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Danger))
            }
            Spacer(Modifier.width(24.dp))
            Text("주행 기록 중", fontSize = DISPLAY, fontWeight = FontWeight.Bold, color = Ink)
        }
        Text(if (voiceOn) "음성 코칭이 켜져 있어요" else "음성 코칭이 꺼져 있어요. 리포트는 주차 후 확인할 수 있어요.",
            fontSize = BODY, color = Sub, modifier = Modifier.padding(top = 16.dp))

        if (voiceOn) {
            // 방금 받은 코칭: 음성으로 들은 내용을 한 번 더 보여 줄 뿐, 터치할 요소는 없음
            Row(
                Modifier.padding(top = 56.dp).widthIn(max = 1280.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(RADIUS)).background(Surface)
                    .border(1.dp, Line, RoundedCornerShape(RADIUS)).padding(horizontal = 40.dp, vertical = 36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(Accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    SpeakerIcon(Accent, Modifier.size(48.dp))
                }
                Spacer(Modifier.width(32.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("방금 받은 코칭", Accent)
                        Spacer(Modifier.width(16.dp))
                        if (message != null) CategoryTag(category)
                    }
                    Text(message ?: "아직 받은 코칭이 없어요. 지금처럼 부드럽게 운전해 주세요.",
                        fontSize = HEADLINE, fontWeight = FontWeight.SemiBold, color = if (message != null) Ink else Sub,
                        modifier = Modifier.padding(top = 12.dp))
                }
            }
        }

        // 현재 차량 상태 (조회 전용 텍스트, 조작 요소 아님)
        Row(Modifier.padding(top = 40.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            VehicleValue("차속", speed?.let { "${it.toInt()} km/h" })
            VehicleValue("기어", gear.name)
            VehicleValue("핸들", steering?.let { val r = it.roundToInt(); if (r == 0) "0°" else "%+d°".format(r) })
            VehicleValue("방향지시등", signal?.label)
            VehicleValue("브레이크", brake?.let { if (it) "밟음" else "뗌" })
        }
        if (!connected) Text("차량 신호 없음 · 데모 모드", fontSize = SMALL, color = Sub, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun VehicleValue(label: String, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = SMALL, color = Sub)
        Spacer(Modifier.width(10.dp))
        Text(value ?: "—", fontSize = SMALL, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}

/** 작은 스피커 표시 (본체 + 소리 물결 2개) */
@Composable
private fun SpeakerIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val body = Path().apply {
            moveTo(s * 0.08f, s * 0.36f); lineTo(s * 0.26f, s * 0.36f); lineTo(s * 0.5f, s * 0.14f)
            lineTo(s * 0.5f, s * 0.86f); lineTo(s * 0.26f, s * 0.64f); lineTo(s * 0.08f, s * 0.64f); close()
        }
        drawPath(body, color)
        val wave = Stroke(s * 0.08f, cap = StrokeCap.Round)
        drawArc(color, -45f, 90f, false, Offset(s * 0.38f, s * 0.3f), Size(s * 0.4f, s * 0.4f), style = wave)
        drawArc(color, -45f, 90f, false, Offset(s * 0.3f, s * 0.14f), Size(s * 0.72f, s * 0.72f), style = wave)
    }
}

// ---------------- 리포트 (목록 + 상세, 2단)
@Composable
private fun ReportScreen() {
    val firstMonth = remember { YearMonth.of(ReportData.year, ReportData.month) }
    var month by remember { mutableStateOf(firstMonth) }
    var day by remember { mutableStateOf(LocalDate.of(ReportData.year, ReportData.month, 15)) }
    val trips = ReportData.tripsOn(day)
    var tripIndex by remember(day) { mutableIntStateOf(0) }
    val trip = trips.getOrNull(tripIndex)

    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        Text("주행 리포트", fontSize = 60.sp, fontWeight = FontWeight.Bold, color = Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(GAP), modifier = Modifier.fillMaxSize()) {
            // 왼쪽: 월간 캘린더 + 그날 주행 목록
            Panel(Modifier.width(460.dp).fillMaxHeight()) {
                MonthCalendar(month, day, onMonth = { month = it }, onDay = { day = it })
                Box(Modifier.padding(vertical = 20.dp).fillMaxWidth().height(1.dp).background(Line))
                Label("${day.monthValue}월 ${day.dayOfMonth}일 · 주행 ${trips.size}회")
                if (trips.isEmpty()) {
                    Text("이 날은 주행 기록이 없어요", fontSize = SMALL, color = Sub, modifier = Modifier.padding(top = 24.dp))
                } else {
                    LazyColumn(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(trips.size) { i -> TripRow(trips[i], i == tripIndex) { tripIndex = i } }
                    }
                }
            }
            // 오른쪽: 선택한 주행 상세
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                if (trip == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("선택한 날의 주행 기록이 없어요", fontSize = BODY, color = Sub)
                    }
                } else {
                    TripDetail(trip)
                }
            }
        }
    }
}

/** 월간 캘린더: ‹ 2026년 11월 › · 요일 헤더 · 7열 날짜 (주행 있는 날 파란 점, 선택한 날 파란 배경) */
@Composable
private fun MonthCalendar(month: YearMonth, selected: LocalDate, onMonth: (YearMonth) -> Unit, onDay: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CalendarArrow("‹") { onMonth(month.minusMonths(1)) }
        Text("${month.year}년 ${month.monthValue}월", fontSize = BODY, fontWeight = FontWeight.Bold, color = Ink,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        CalendarArrow("›") { onMonth(month.plusMonths(1)) }
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        listOf("일", "월", "화", "수", "목", "금", "토").forEach {
            Text(it, fontSize = 22.sp, color = Sub, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    val lead = month.atDay(1).dayOfWeek.value % 7 // 일요일 시작
    val cells = (List(lead) { null } + (1..month.lengthOfMonth()).toList()).chunked(7)
    cells.forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i ->
                val d = week.getOrNull(i)
                Box(Modifier.weight(1f).height(56.dp), contentAlignment = Alignment.Center) {
                    if (d != null) {
                        val date = month.atDay(d)
                        val sel = date == selected
                        val has = ReportData.tripsOn(date).isNotEmpty()
                        Column(
                            Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(if (sel) Accent else Color.Transparent)
                                .clickable { onDay(date) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text("$d", fontSize = 24.sp, color = if (sel) Color.White else Ink,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, lineHeight = 26.sp)
                            Box(Modifier.padding(top = 3.dp).size(6.dp).clip(CircleShape)
                                .background(if (!has) Color.Transparent else if (sel) Color.White else Accent))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarArrow(text: String, onClick: () -> Unit) {
    Box(Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 40.sp, color = Ink)
    }
}

/** 주행 목록 한 행: 시간 | 출발 → 도착 / 34분 · 14.8km | 7건 */
@Composable
private fun TripRow(t: TripLog, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(ITEM_RADIUS))
            .background(if (selected) SelectedBg else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(t.start, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text("${t.from} → ${t.to}", fontSize = SMALL, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${t.minutes}분 · ${t.km}km", fontSize = 22.sp, color = Sub)
        }
        Text("${t.issueCount}건", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

/** 선택한 주행 상세: 요약 줄 · 지도(번호 핀) · 문제 목록 (누르면 지도가 그 핀으로 이동) */
@Composable
private fun TripDetail(t: TripLog) {
    var focus by remember(t) { mutableStateOf<MapFocus?>(null) }
    var seq by remember { mutableIntStateOf(0) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${t.start} ${t.from} → ${t.to}", fontSize = HEADLINE, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
        Text("${t.minutes}분 · ${t.km}km · 개선 ${t.issueCount}건", fontSize = SMALL, color = Sub)
    }
    HomeRouteMap(
        Accent, bottomInset = 0.dp,
        modifier = Modifier.padding(top = 20.dp).fillMaxWidth().height(380.dp).clip(RoundedCornerShape(ITEM_RADIUS)),
        overlay = tripRouteOverlay(t, Accent), focus = focus,
    )
    // 문제 목록: 누르면 아래로 '내 운전 vs 권장' 비교가 펼쳐짐 (한 번에 하나, 처음엔 첫 항목)
    var expanded by remember(t) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    // 펼침 애니메이션이 끝난 뒤 그 항목이 위로 오게 스크롤 (아래쪽 항목을 펼쳐도 그래프가 잘리지 않게)
    LaunchedEffect(expanded) {
        if (expanded > 0) { kotlinx.coroutines.delay(350); listState.animateScrollToItem(expanded) }
    }
    LazyColumn(Modifier.padding(top = 16.dp), state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(t.issues.size) { i ->
            val issue = t.issues[i]
            val open = expanded == i
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(ITEM_RADIUS))
                    .background(if (open) Color(0xFFFAFBFC) else Color.Transparent)
            ) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(ITEM_RADIUS))
                        .clickable {
                            expanded = if (open) -1 else i
                            focus = MapFocus(issuePoint(t, issue), ++seq) // 지도도 그 핀으로 이동
                        }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(issue.type.color), contentAlignment = Alignment.Center) {
                        Text("${issue.no}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(start = 20.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(issue.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text("${issue.time} · ${issue.place}", fontSize = 22.sp, color = Sub, modifier = Modifier.padding(start = 14.dp, bottom = 2.dp))
                        }
                        Text(issue.tip, fontSize = SMALL, color = Ink, modifier = Modifier.padding(top = 4.dp))
                    }
                    Text(if (open) "⌃" else "⌄", fontSize = 28.sp, color = Sub, modifier = Modifier.padding(start = 12.dp))
                }
                AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    ComparisonView(ComparisonData.forIssue(issue), issue.type.color,
                        Modifier.padding(start = 72.dp, end = 12.dp, bottom = 16.dp))
                }
            }
        }
    }
}

/** '내 운전 vs 권장' 비교: 꺾은선 그래프(또는 타임라인) + 한 줄 비교 문장 */
@Composable
private fun ComparisonView(c: Comparison, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        when (c) {
            is Comparison.Chart -> CompareChart(c, color)
            is Comparison.Timeline -> {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ChartBg).padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                    Text(boldMarkup(c.summary), fontSize = SMALL, color = Ink, modifier = Modifier.padding(start = 14.dp))
                }
            }
        }
        if (c is Comparison.Chart) {
            Text(boldMarkup(c.summary), fontSize = SMALL, color = Ink, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/** 문장의 **굵게** 부분을 굵은 글씨로 */
private fun boldMarkup(text: String) = buildAnnotatedString {
    text.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

/** 내 운전(분류 색 실선 4dp) vs 권장(회색 점선 3dp). 좌상단 단위·범례, 아래 x축(초) 라벨 */
@Composable
private fun CompareChart(c: Comparison.Chart, color: Color) {
    val measurer = rememberTextMeasurer()
    Column(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).background(ChartBg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.unit, fontSize = 18.sp, color = Sub)
            Spacer(Modifier.width(24.dp))
            LegendLine(color, dashed = false); Text("내 운전", fontSize = 18.sp, color = Ink, modifier = Modifier.padding(start = 6.dp, end = 18.dp))
            LegendLine(RecommendGray, dashed = true); Text("권장", fontSize = 18.sp, color = Ink, modifier = Modifier.padding(start = 6.dp))
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
            val labelH = 26.dp.toPx()
            val plotTop = 6.dp.toPx(); val plotBottom = size.height - labelH
            val side = 20.dp.toPx()
            val n = c.mine.size
            val tEnd = ComparisonData.T_START + (n - 1) * ComparisonData.T_STEP
            fun x(t: Float) = side + (t - ComparisonData.T_START) / (tEnd - ComparisonData.T_START) * (size.width - 2 * side)
            fun y(v: Float) = plotBottom - (v - c.yMin) / (c.yMax - c.yMin) * (plotBottom - plotTop)
            fun path(vs: List<Float>) = Path().apply {
                vs.forEachIndexed { i, v -> val px = x(ComparisonData.T_START + i * ComparisonData.T_STEP); if (i == 0) moveTo(px, y(v)) else lineTo(px, y(v)) }
            }
            // 기준선 (값 0)
            val zero = y(0f.coerceIn(c.yMin, c.yMax))
            drawLine(Line, Offset(side, zero), Offset(size.width - side, zero), strokeWidth = 1.dp.toPx())
            drawPath(path(c.recommended), RecommendGray, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))))
            drawPath(path(c.mine), color, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // x축 라벨 (초)
            listOf(-2f to "-2초", 0f to "0", 2f to "2초", 4f to "4초").forEach { (t, label) ->
                val l = measurer.measure(label, TextStyle(fontSize = 16.sp, color = Sub))
                drawText(l, topLeft = Offset((x(t) - l.size.width / 2f).coerceIn(0f, size.width - l.size.width), plotBottom + 6.dp.toPx()))
            }
        }
    }
}

/** 범례 선 샘플 (실선/점선) */
@Composable
private fun LegendLine(color: Color, dashed: Boolean) {
    Canvas(Modifier.width(28.dp).height(10.dp)) {
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
            strokeWidth = (if (dashed) 3 else 4).dp.toPx(), cap = StrokeCap.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null)
    }
}

// ---------------- 개선 추이 (최근 4주 꺾은선)
@Composable
private fun TrendScreen(focus: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        ScreenHeader("개선 추이", "최근 4주 동안 항목별 발생 횟수예요. 줄어들수록 좋아요.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(MockData.habits.sortedByDescending { it.id in focus }) { h ->
                Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrendInfo(h, h.id in focus, Modifier.weight(1f))
                        Spacer(Modifier.width(GAP))
                        WeeklyLine(h.weekly, h.category.color, Modifier.width(440.dp).height(160.dp))
                    }
                }
            }
        }
    }
}

/** 개선 추이 카드 왼쪽: 분류(+집중 항목) / 항목 이름 / 이번 주 횟수 + 4주 전 대비 배지 */
@Composable
private fun TrendInfo(h: Habit, focused: Boolean, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(h.category.color))
            Text(h.category.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = h.category.color,
                modifier = Modifier.padding(start = 10.dp))
            if (focused) {
                Pill("집중 항목", SelectedBg, Accent, Modifier.padding(start = 14.dp))
            }
        }
        Text(h.name, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 14.dp))
        val first = h.weekly.first()
        val last = h.weekly.last()
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("이번 주", fontSize = 22.sp, color = Sub)
            Text("${last}회", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(start = 12.dp))
            val (text, bg, fg) = when {
                last < first -> Triple("4주 전보다 ${first - last}회 ↓", SelectedBg, Accent)
                last > first -> Triple("4주 전보다 ${last - first}회 ↑", DangerBg, Danger)
                else -> Triple("4주 전과 같아요", NeutralBg, Sub)
            }
            Pill(text, bg, fg, Modifier.padding(start = 16.dp))
        }
    }
}

/** 테두리 없는 둥근 배지 */
@Composable
private fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = fg,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 12.dp, vertical = 4.dp))
}

/**
 * 최근 4주 꺾은선: 점 4개를 분류 색 선으로 잇고, 점 위에 값, 아래 기준선과 주 라벨.
 * 지난주들은 흰 채움 + 색 테두리의 작은 점 · 회색 숫자, 이번 주는 색으로 채운 큰 점 · 굵은 검정 숫자.
 */
@Composable
private fun WeeklyLine(values: List<Int>, color: Color, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val labels = listOf("3주 전", "2주 전", "1주 전", "이번 주")
    val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(modifier) {
        val top = 40.dp.toPx()             // 값 숫자가 잘리지 않게 위 여백
        val base = size.height - 34.dp.toPx() // 기준선 (값 0), 그 아래 주 라벨
        val sideInset = 36.dp.toPx()
        val step = (size.width - 2 * sideInset) / (values.size - 1)
        val pts = values.mapIndexed { i, v -> Offset(sideInset + i * step, base - (base - top) * v / max) }
        val lastIdx = values.lastIndex

        // 기준선
        drawLine(Line, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
        // 꺾은선
        val path = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        drawPath(path, color, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        pts.forEachIndexed { i, p ->
            val now = i == lastIdx
            // 점
            if (now) {
                drawCircle(color, 9.dp.toPx(), p)
            } else {
                drawCircle(Color.White, 6.dp.toPx(), p)
                drawCircle(color, 6.dp.toPx(), p, style = Stroke(3.dp.toPx()))
            }
            // 값 숫자 (점 위)
            val v = measurer.measure("${values[i]}", TextStyle(fontSize = if (now) 24.sp else 20.sp,
                fontWeight = if (now) FontWeight.Bold else FontWeight.Normal, color = if (now) Ink else Sub))
            drawText(v, topLeft = Offset(p.x - v.size.width / 2f, p.y - (if (now) 12 else 9).dp.toPx() - v.size.height))
            // 주 라벨 (기준선 아래)
            val l = measurer.measure(labels.getOrElse(i) { "" }, TextStyle(fontSize = 20.sp,
                fontWeight = if (now) FontWeight.Bold else FontWeight.Normal, color = if (now) Ink else Sub))
            drawText(l, topLeft = Offset(p.x - l.size.width / 2f, base + 6.dp.toPx()))
        }
    }
}

// ---------------- 설정
@Composable
private fun SettingsScreen(voiceOn: Boolean, setVoice: (Boolean) -> Unit, focus: MutableList<Int>) {
    fun toggle(id: Int) { if (id in focus) focus.remove(id) else focus.add(id) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(GAP)) {
        item { ScreenHeader("설정") }
        item {
            Panel(Modifier.fillMaxWidth()) {
                // 줄 전체를 눌러도 켜고 끌 수 있게 해서 터치 영역을 넓힘
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = TOUCH).clip(RoundedCornerShape(ITEM_RADIUS))
                        .clickable { setVoice(!voiceOn) }
                ) {
                    Column(Modifier.weight(1f)) {
                        ItemTitle("주행 중 음성 코칭")
                        Text("끄면 주행 중에는 조용히 기록만 하고, 리포트로 확인해요.", fontSize = SMALL, color = Sub,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                    Switch(checked = voiceOn, onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                        modifier = Modifier.padding(start = 24.dp, end = 16.dp).scale(1.5f))
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                ItemTitle("리포트 공유")
                Text("공유받은 사람은 스마트폰에서 리포트를 열람만 할 수 있어요.", fontSize = SMALL, color = Sub,
                    modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${MockData.sharedWith} · 열람 중", fontSize = BODY, color = Ink, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {}, modifier = Modifier.height(TOUCH), shape = RoundedCornerShape(ITEM_RADIUS),
                        contentPadding = PaddingValues(horizontal = 32.dp)) {
                        Text("공유 링크 만들기", fontSize = BODY, fontWeight = FontWeight.SemiBold, color = Accent)
                    }
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                ItemTitle("집중 개선 항목 선택")
                Text("선택한 항목은 홈과 개선 추이에서 먼저 보여 드려요.", fontSize = SMALL, color = Sub,
                    modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                MockData.habits.forEach { h ->
                    Row(
                        Modifier.fillMaxWidth().height(TOUCH).clip(RoundedCornerShape(ITEM_RADIUS))
                            .clickable { toggle(h.id) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = h.id in focus, onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = Accent),
                            modifier = Modifier.scale(1.4f))
                        Spacer(Modifier.width(24.dp))
                        CategoryTag(h.category); Spacer(Modifier.width(16.dp))
                        Text(h.name, fontSize = BODY, color = Ink)
                    }
                }
            }
        }
    }
}

// ---------------- 데모용 기어 전환 (시연 영상 촬영용)
@Composable
private fun DemoGearSwitch(gear: DriveGear, modifier: Modifier, onChange: (DriveGear) -> Unit) {
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x22000000)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(DriveGear.P, DriveGear.D).forEach { g ->
            Text("데모 ${g.name}", fontSize = 22.sp, color = if (g == gear) Color.White else Ink,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (g == gear) Ink else Color.Transparent)
                    .clickable { onChange(g) }.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}
