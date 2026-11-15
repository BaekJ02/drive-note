package com.example.drivenote

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
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
                            Tab.HOME -> HomeScreen(voiceOn, focus) { tab = Tab.REPORT; hasNewReport = false }
                            Tab.REPORT -> ReportScreen(trip)
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
private fun HomeScreen(voiceOn: Boolean, focus: List<Int>, openReport: () -> Unit) {
    val d = MockData.lastDrive
    val total = MockData.habits.sumOf { it.count }
    val today = remember { SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(Date()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        // 인사말: 이름만 굵게
        Column {
            Text(buildAnnotatedString {
                append("안녕하세요, ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${MockData.userName}님") }
                append(". 오늘도 안전 운전해요.")
            }, fontSize = 60.sp, color = Ink)
            Text("$today · 주차 중", fontSize = BODY, color = Sub, modifier = Modifier.padding(top = 8.dp))
        }

        // 최근 주행: 지도(60%) + 정보(40%)
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(RADIUS)).background(Surface)
                .border(1.dp, Line, RoundedCornerShape(RADIUS)).padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 지도: 고정 높이 + 둥근 모서리로 잘라서 카드·오른쪽 정보 영역 밖으로 그려지지 않게 함
            Column(Modifier.weight(0.6f)) {
                RecentDriveMap(
                    Accent,
                    Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(ITEM_RADIUS)).clipToBounds()
                )
                // 범례: 지도 아래 별도 줄 (넘치면 다음 줄로)
                FlowRow(
                    Modifier.fillMaxWidth().padding(start = 8.dp, top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MockData.lastDriveSegments.forEach { seg ->
                        val h = MockData.habits.first { it.id == seg.habitId }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(h.category.color))
                            Spacer(Modifier.width(10.dp))
                            Text(h.name, fontSize = SMALL, color = Sub)
                        }
                    }
                }
            }
            Column(Modifier.weight(0.4f).fillMaxHeight().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Label("최근 주행")
                Text("${d.date} – ${MockData.lastDriveArrive}", fontSize = SMALL, color = Sub, modifier = Modifier.padding(top = 16.dp))
                Text(d.route, fontSize = HEADLINE, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 4.dp))
                Text("${MockData.lastDriveFrom} → ${MockData.lastDriveTo}", fontSize = SMALL, color = Sub)
                Row(Modifier.fillMaxWidth().padding(top = 32.dp)) {
                    HomeStat("소요 시간", "${d.minutes}분", Modifier.weight(1f))
                    HomeStat("거리", "${d.km}km", Modifier.weight(1f))
                    HomeStat("개선 필요", "${total}건", Modifier.weight(1f))
                }
                Spacer(Modifier.weight(1f).heightIn(min = 28.dp))
                Button(onClick = openReport, modifier = Modifier.height(TOUCH).fillMaxWidth(),
                    shape = RoundedCornerShape(ITEM_RADIUS), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text("리포트 보기", fontSize = BODY, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 이번 주 요약 · 집중 개선 항목 (텍스트 중심, 나란히)
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(GAP)) {
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                Label("이번 주 요약")
                MockData.weekSummary.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                        pair.forEach { HomeStat(it.label, it.value, Modifier.weight(1f)) }
                    }
                }
            }
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                Label("집중 개선 항목")
                MockData.habits.filter { it.id in focus }.forEach { h ->
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.name, fontSize = BODY, color = Ink)
                            CategoryTag(h.category)
                        }
                        Text("이번 주 ${h.count}회", fontSize = BODY, color = Ink, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }

        // 음성 코칭 상태: 한 줄 텍스트
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (voiceOn) Positive else Sub))
            Spacer(Modifier.width(12.dp))
            Text("주행 중 음성 코칭 ${if (voiceOn) "켜짐" else "꺼짐"} · 시동을 걸면 자동으로 기록해요 · 가족 1명 열람 공유 중",
                fontSize = SMALL, color = Sub)
        }
    }
}

@Composable
private fun HomeStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = SMALL, color = Sub)
        Text(value, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = Ink)
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
private fun ReportScreen(trip: TripRecord?) {
    // 이번 주행에서 감지된 기록이 있으면 그걸, 없으면 목업 데이터를 보여 줌
    val live = trip != null && trip.events.isNotEmpty()
    val habits = remember(trip) { if (live) TripReport.habits(trip!!) else MockData.habits }
    val d = remember(trip) { if (live) TripReport.summary(trip!!) else MockData.lastDrive }
    var selected by remember(habits) { mutableStateOf(habits.first()) }
    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        ScreenHeader("주행 리포트", "${d.date} · ${d.route} · ${d.minutes}분 · ${d.km}km")
        Row(horizontalArrangement = Arrangement.spacedBy(GAP), modifier = Modifier.fillMaxSize()) {
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                Label("개선이 필요한 항목 ${habits.size}개")
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(habits) { h ->
                        val sel = h.id == selected.id
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = TOUCH).clip(RoundedCornerShape(ITEM_RADIUS))
                                .background(if (sel) Accent.copy(alpha = 0.10f) else Color.Transparent)
                                .clickable { selected = h }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryTag(h.category); Spacer(Modifier.width(16.dp))
                            Text(h.name, fontSize = BODY, color = Ink, modifier = Modifier.weight(1f),
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                            Text("${h.count}회", fontSize = BODY, color = if (sel) Accent else Sub, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
            Panel(Modifier.weight(1.2f).fillMaxHeight()) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CategoryTag(selected.category)
                    Text(selected.name, fontSize = HEADLINE, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 12.dp))
                    Text("발생 위치 · ${selected.location}", fontSize = SMALL, color = Sub, modifier = Modifier.padding(top = 4.dp))
                    // 지도 일러스트 (실제 앱: FusedLocation 좌표를 지도 위에 표시)
                    RouteMap(selected, Modifier.padding(top = 20.dp).fillMaxWidth().height(280.dp))
                    Row(Modifier.padding(top = 12.dp, bottom = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(32.dp).height(8.dp).clip(CircleShape).background(Accent))
                        Text("주행 경로", fontSize = SMALL, color = Sub, modifier = Modifier.padding(start = 10.dp, end = 32.dp))
                        Box(Modifier.size(20.dp).clip(CircleShape).background(selected.category.color))
                        Text("발생 지점 ${selected.count}곳", fontSize = SMALL, color = Sub, modifier = Modifier.padding(start = 10.dp))
                    }
                    Label("왜 고치면 좋을까요?")
                    Text(selected.why, fontSize = BODY, color = Ink, modifier = Modifier.padding(top = 8.dp, bottom = 28.dp))
                    Label("이렇게 해 보세요", Accent)
                    Text(selected.tip, fontSize = BODY, color = Ink, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

/** 간단한 지도 일러스트: 연한 도로 격자 + 주행 경로 곡선 + 습관 발생 지점 마커 */
@Composable
private fun RouteMap(habit: Habit, modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(ITEM_RADIUS)).background(Color(0xFFEDF1F6))) {
        val w = size.width
        val h = size.height
        // 공원·하천 블록
        drawRoundRect(Color(0xFFDCEFDF), Offset(w * 0.56f, h * 0.55f), Size(w * 0.22f, h * 0.3f), CornerRadius(12.dp.toPx()))
        drawRoundRect(Color(0xFFD6E6F7), Offset(w * 0.05f, h * 0.08f), Size(w * 0.18f, h * 0.26f), CornerRadius(12.dp.toPx()))
        // 도로 격자
        val road = 10.dp.toPx()
        for (k in 1..5) drawLine(Color.White, Offset(w * k / 6f, 0f), Offset(w * k / 6f, h), road)
        for (k in 1..3) drawLine(Color.White, Offset(0f, h * k / 4f), Offset(w, h * k / 4f), road)
        drawLine(Color.White, Offset(0f, h), Offset(w * 0.55f, 0f), road * 1.6f) // 대로

        // 주행 경로 (3차 베지어 곡선)
        val p0 = Offset(w * 0.07f, h * 0.84f)
        val p1 = Offset(w * 0.38f, h * 0.98f)
        val p2 = Offset(w * 0.5f, h * 0.02f)
        val p3 = Offset(w * 0.93f, h * 0.2f)
        val route = Path().apply { moveTo(p0.x, p0.y); cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y) }
        drawPath(route, Accent.copy(alpha = 0.25f), style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
        drawPath(route, Accent, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

        // 출발(빈 원)·도착(채운 원)
        drawCircle(Color.White, 11.dp.toPx(), p0)
        drawCircle(Accent, 11.dp.toPx(), p0, style = Stroke(4.dp.toPx()))
        drawCircle(Accent, 12.dp.toPx(), p3)
        drawCircle(Color.White, 5.dp.toPx(), p3)

        // 발생 지점: 경로 위에 count개를 고르게 배치 (습관별로 조금씩 위치가 다르게)
        repeat(habit.count) { i ->
            val jitter = (((habit.id * 37 + i * 53) % 7) - 3) * 0.015f
            val t = ((i + 1f) / (habit.count + 1f) + jitter).coerceIn(0.08f, 0.92f)
            val pt = bezier(p0, p1, p2, p3, t)
            drawCircle(Color.Black.copy(alpha = 0.12f), 17.dp.toPx(), pt + Offset(0f, 2.dp.toPx()))
            drawCircle(Color.White, 16.dp.toPx(), pt)
            drawCircle(habit.category.color, 11.dp.toPx(), pt)
        }
    }
}

private fun bezier(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val u = 1 - t
    return p0 * (u * u * u) + p1 * (3 * u * u * t) + p2 * (3 * u * t * t) + p3 * (t * t * t)
}

// ---------------- 개선 추이 (최근 4주 막대)
@Composable
private fun TrendScreen(focus: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        ScreenHeader("개선 추이", "최근 4주 동안 항목별 발생 횟수예요. 줄어들수록 좋아요.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(MockData.habits.sortedByDescending { it.id in focus }) { h ->
                Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CategoryTag(h.category)
                                if (h.id in focus) {
                                    Spacer(Modifier.width(12.dp))
                                    Text("집중 항목", fontSize = SMALL, color = Accent, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).border(2.dp, Accent, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp, vertical = 4.dp))
                                }
                            }
                            Text(h.name, fontSize = BODY, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 12.dp))
                            val first = h.weekly.first()
                            val last = h.weekly.last()
                            val change = when {
                                first > last -> "${first - last}회 감소"
                                first < last -> "${last - first}회 증가"
                                else -> "변화 없음"
                            }
                            Text("4주 전 ${first}회 → 이번 주 ${last}회 ($change)", fontSize = SMALL, color = Sub,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                        Spacer(Modifier.width(GAP))
                        WeeklyBars(h.weekly, h.category.color, Modifier.width(320.dp).height(110.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyBars(values: List<Int>, color: Color, modifier: Modifier) {
    val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(modifier) {
        val gap = 16.dp.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val h = size.height * v / max
            drawRoundRect(
                color = if (i == values.lastIndex) color else color.copy(alpha = 0.35f),
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h), cornerRadius = CornerRadius(8.dp.toPx())
            )
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
