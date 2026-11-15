package com.example.drivenote

import androidx.compose.ui.graphics.Color

// 분류: 색 + 글자 라벨을 함께 써서 색만으로 구분하지 않게 함
enum class Category(val label: String, val color: Color) {
    STEER("조향", Color(0xFF2F6FED)),
    ACCEL("가속", Color(0xFFE8833A)),
    BRAKE("제동", Color(0xFFD9443B)),
    SIGNAL("신호·안전", Color(0xFF2E9E5B)),
    PARKING("주차", Color(0xFF7A5AE0)),
}

data class Habit(
    val id: Int,
    val category: Category,
    val name: String,
    val count: Int,
    val location: String,
    val why: String,
    val tip: String,
    val weekly: List<Int>, // 최근 4주 발생 횟수
)

data class DriveSummary(
    val date: String,
    val route: String,
    val minutes: Int,
    val km: Double,
)

data class WeekStat(val label: String, val value: String)

/** 위도·경도 (지도 라이브러리와 무관한 목업용 좌표) */
data class GeoPt(val lat: Double, val lon: Double)

/** 경로 중 습관이 감지된 구간: route[from]..route[to], 색·이름은 habitId의 습관에서 가져옴 */
data class RouteSegment(val from: Int, val to: Int, val habitId: Int)

object MockData {
    val userName = "김현대"

    val lastDrive = DriveSummary("11월 15일 (일) 08:42", "집 → 회사", 34, 14.8)
    val lastDriveDepart = "08:42"
    val lastDriveArrive = "09:16"
    val lastDriveIssues = 7 // 최근 주행 1회의 개선 필요 건수 (홈 카드)
    val lastDriveFrom = "성남 수정구 시흥동"
    val lastDriveTo = "강남구 삼성동"

    // 최근 주행 경로: 시흥동 → 대왕판교로 → 헌릉로 → 분당수서로 → 삼성동
    // (OpenStreetMap 도로 데이터 기반 경로를 22개 점으로 단순화한 가상 경로)
    val lastDriveRoute = listOf(
        GeoPt(37.41933, 127.10171), // 0 출발 (시흥동)
        GeoPt(37.41960, 127.10233),
        GeoPt(37.42936, 127.10189),
        GeoPt(37.43275, 127.10315),
        GeoPt(37.43704, 127.10381),
        GeoPt(37.44442, 127.10366), // 5
        GeoPt(37.45187, 127.10628),
        GeoPt(37.46467, 127.10827),
        GeoPt(37.46594, 127.11729), // 8 헌릉로
        GeoPt(37.47090, 127.12682),
        GeoPt(37.47003, 127.12465), // 10
        GeoPt(37.47041, 127.12373), // 11 분당수서로 진입
        GeoPt(37.48158, 127.11536),
        GeoPt(37.48253, 127.11401),
        GeoPt(37.48394, 127.11016),
        GeoPt(37.49106, 127.10397), // 15
        GeoPt(37.49451, 127.09646),
        GeoPt(37.50017, 127.07973),
        GeoPt(37.50223, 127.07116),
        GeoPt(37.50648, 127.06802),
        GeoPt(37.51013, 127.06736), // 20
        GeoPt(37.50880, 127.06315), // 21 도착 (삼성동)
    )

    // 내장 지도 이미지(res/drawable-nodpi/route_map.png): 네이버 Static Map 요청값
    // tools/make_route_map.py가 자동으로 갱신함. Web Mercator, 256px 타일 기준 줌 = level + 1,
    // 이미지 크기 = 요청 크기(w, h) x scale, 이미지 중심 = center
    const val routeMapCenterLat = 37.447427
    const val routeMapCenterLon = 127.094985
    const val routeMapLevel = 11
    const val routeMapScale = 2
    const val routeMapWidth = 2048  // px (= 1024 x 2)
    const val routeMapHeight = 1412 // px (= 706 x 2)

    // 습관이 감지된 구간 (홈 지도에 분류 색으로 강조)
    val lastDriveSegments = listOf(
        RouteSegment(1, 3, habitId = 1),   // 급출발·급가속: 시흥동 교차로 출발 직후
        RouteSegment(12, 15, habitId = 2), // 브레이크 나눠 밟기: 분당수서로 정체 구간
        RouteSegment(16, 18, habitId = 4), // 방향지시등 늦은 점등: 분당수서로 차로 변경
    )

    // 이번 주 요약 (홈)
    val weekSummary = listOf(
        WeekStat("주행", "5회"),
        WeekStat("주행 거리", "92km"),
        WeekStat("개선 필요", "31건"),
        WeekStat("지난주 대비", "-6건"),
    )

    // 주행 중 화면: 가장 최근 음성 코칭
    val recentCoaching = "출발할 때 가속 페달을 조금 더 천천히 밟아 보면 좋아요"
    val recentCoachingCategory = Category.ACCEL

    // 리포트 공유 대상 (열람 전용)
    val sharedWith = "엄마"
    // 홈 '개선할 점' 패널: 최근 주행의 개선 필요 항목 (이름, 분류, 건수) — 합계 7건
    val homeIssues = listOf(
        Triple("브레이크 나눠 밟기", IssueType.BRAKE, 2),
        Triple("정차 직전 급제동", IssueType.BRAKE, 1),
        Triple("급출발·급가속", IssueType.ACCEL, 2),
        Triple("방향지시등 늦게 켬", IssueType.SIGNAL, 1),
        Triple("핸들 급조작", IssueType.STEER, 1),
    )

    // 홈 공유 다이얼로그: (이름, 관계)
    val shareTargets = listOf("김강사" to "운전 강사", "가족" to "엄마")

    val habits = listOf(
        Habit(1, Category.ACCEL, "급출발·급가속", 2, "수정구 시흥동 교차로 외 1곳",
            "출발할 때 가속 페달을 한 번에 깊게 밟아 차가 앞으로 튀어 나갔어요.",
            "출발할 때 가속 페달을 2초 정도에 걸쳐 천천히 밟아 보면 좋아요.", listOf(6, 5, 3, 2)),
        Habit(2, Category.BRAKE, "브레이크를 여러 번 나눠 밟기", 7, "분당수서로 정체 구간",
            "한 번 멈추는 동안 브레이크를 평균 4번 밟았다 뗐어요. 차가 울렁거려 동승자가 불편할 수 있어요.",
            "한 번에 일정한 힘으로 밟으면 차가 덜 울렁거려요.", listOf(12, 10, 9, 7)),
        Habit(3, Category.BRAKE, "정차 직전 급격한 제동", 3, "회사 앞 신호 외 2곳",
            "멈추기 직전까지 브레이크를 강하게 밟아 차가 덜컹하며 섰어요.",
            "멈추기 직전에 브레이크를 살짝 풀어주면 부드럽게 설 수 있어요.", listOf(5, 4, 4, 3)),
        Habit(4, Category.SIGNAL, "방향지시등 늦은 점등", 3, "분당수서로 차로 변경 3회",
            "핸들을 돌리기 시작한 뒤 평균 0.6초 후에 방향지시등을 켰어요.",
            "차로를 바꾸기 3초 전쯤 방향지시등을 켜면 뒤차가 미리 대비할 수 있어요.", listOf(4, 4, 3, 3)),
        Habit(5, Category.SIGNAL, "우회전 전 일시정지 미준수", 1, "시흥사거리 우회전",
            "횡단보도 앞에서 멈추지 않고 시속 14km로 우회전했어요.",
            "우회전 전에는 횡단보도 앞에서 잠시 멈추고 보행자를 확인해 주세요.", listOf(3, 2, 2, 1)),
        Habit(6, Category.STEER, "코너 주행 시 핸들 과다 조작", 2, "회사 주차장 진입로",
            "회전에 필요한 것보다 핸들을 약 90° 더 감고, 그만큼 다시 풀었어요.",
            "이 정도 교차로는 핸들을 조금 덜 돌려도 충분히 회전할 수 있어요.", listOf(4, 3, 3, 2)),
        Habit(7, Category.PARKING, "주차 시 조향·기어 변경 과다", 1, "회사 지하주차장 B2",
            "후진 주차를 하며 핸들을 6번 고쳐 잡고 기어를 4번 바꿨어요.",
            "후진 주차할 때 기준점을 먼저 잡고 들어가면 수정 횟수를 줄일 수 있어요.", listOf(2, 2, 1, 1)),
    )
}

// ---------------- 리포트 탭: 월간 캘린더 + 주행별 상세

/** 리포트 문제 분류별 색 (핀·목록 원) */
enum class IssueType(val color: Color) {
    ACCEL(Color(0xFFE8833A)),  // 급출발·급가속
    BRAKE(Color(0xFFD9443B)),  // 급제동·브레이크 나눠 밟기
    SIGNAL(Color(0xFF2E9E5B)), // 방향지시등
    STEER(Color(0xFF8E5BD9)),  // 조향 (보라)
}

/** 주행 중 감지된 문제 1건: route[from]..route[to] 구간을 색으로 표시하고, 구간 가운데에 번호 핀 */
data class DriveIssue(
    val no: Int, val type: IssueType, val title: String, val time: String, val place: String, val tip: String,
    val from: Int, val to: Int,
)

data class TripLog(
    val start: String, val from: String, val to: String, val minutes: Int, val km: Double,
    val issueCount: Int, // 이 주행의 개선 필요 건수 (목록에 표시하는 대표 문제보다 많을 수 있음)
    val route: List<GeoPt>, val issues: List<DriveIssue>,
)

object ReportData {
    val year = 2026
    val month = 11

    // 경로: 시흥동 → 삼성동(홈 경로), 반대 방향, 시흥동 → 판교
    private val toWork = MockData.lastDriveRoute
    private val toHome = MockData.lastDriveRoute.reversed()
    private val toPangyo = listOf(
        GeoPt(37.41933, 127.10171), GeoPt(37.41500, 127.10050), GeoPt(37.40900, 127.09950),
        GeoPt(37.40300, 127.10000), GeoPt(37.39800, 127.10400), GeoPt(37.39480, 127.11120),
    )

    private val fromPangyo = toPangyo.reversed()

    // 장소별 경로 구간 (출근 방향 인덱스 기준, 퇴근 방향은 뒤집어서 사용)
    private val places = mapOf(
        "시흥사거리" to (1 to 3), "대왕판교로" to (4 to 6), "헌릉로" to (7 to 9), "헌릉IC" to (9 to 11),
        "분당수서로" to (12 to 15), "수서IC" to (15 to 17), "대치동" to (16 to 18), "삼성동" to (19 to 21),
        "대치동 교차로 우회전" to (18 to 20),
    )
    private val pangyoPlaces = mapOf("시흥동 교차로" to (0 to 1), "대왕판교로" to (1 to 3), "판교IC" to (3 to 5))

    private val tips = mapOf(
        "급출발" to "가속 페달을 2초 정도에 걸쳐 천천히 밟아 보세요.",
        "급가속" to "합류할 때는 미리 속도를 조금씩 올려 두면 급하게 밟지 않아도 돼요.",
        "브레이크 나눠 밟기" to "한 번에 일정한 힘으로 밟으면 덜 울렁거려요.",
        "급제동" to "앞차와 거리를 조금 더 두면 여유 있게 멈출 수 있어요.",
        "정차 직전 급제동" to "멈추기 직전에 브레이크를 살짝 풀어 주면 부드럽게 설 수 있어요.",
        "방향지시등 늦게 켬" to "차로를 바꾸기 3초 전쯤 켜 주세요.",
        "방향지시등 미점등" to "진출로에 들어가기 전에 방향지시등을 먼저 켜 보세요.",
        "핸들 과다 조작" to "핸들을 조금 덜 돌려도 충분히 회전할 수 있어요.",
        "차로 내 흔들림" to "시선을 멀리 두면 차로 가운데를 더 쉽게 유지할 수 있어요.",
        "핸들 급조작" to "핸들을 조금 더 천천히, 여유 있게 돌려 보세요.",
    )
    private val typeOf = mapOf(
        "급출발" to IssueType.ACCEL, "급가속" to IssueType.ACCEL,
        "브레이크 나눠 밟기" to IssueType.BRAKE, "급제동" to IssueType.BRAKE, "정차 직전 급제동" to IssueType.BRAKE,
        "방향지시등 늦게 켬" to IssueType.SIGNAL, "방향지시등 미점등" to IssueType.SIGNAL,
        "핸들 과다 조작" to IssueType.STEER, "차로 내 흔들림" to IssueType.STEER, "핸들 급조작" to IssueType.STEER,
    )

    /** 문제 목록 생성: (제목, 장소) 순서대로 번호를 붙이고, 시간은 경로상 위치로 계산 (explicit이 있으면 그 시간) */
    private fun trip(
        start: String, from: String, to: String, minutes: Int, km: Double, count: Int, route: List<GeoPt>,
        vararg issues: Pair<String, String>, times: List<String>? = null,
    ): TripLog {
        val last = route.lastIndex
        val list = issues.mapIndexed { i, (title, place) ->
            // 반대 방향 경로는 구간 인덱스를 뒤집음
            fun rev(r: Pair<Int, Int>) = (last - r.second) to (last - r.first)
            val (a, b) = when (route) {
                toHome -> rev(places.getValue(place))
                toPangyo -> pangyoPlaces.getValue(place)
                fromPangyo -> rev(pangyoPlaces.getValue(place))
                else -> places.getValue(place)
            }
            val (h, m) = start.split(":").map(String::toInt)
            val at = h * 60 + m + minutes * a / last
            val time = times?.get(i) ?: "%02d:%02d".format(at / 60, at % 60)
            DriveIssue(i + 1, typeOf.getValue(title), title, time, place, tips.getValue(title), a, b)
        }
        return TripLog(start, from, to, minutes, km, count, route, list)
    }

    /** 홈 '최근 주행' = 11월 15일 08:42 주행 */
    val latestTrip get() = trips.getValue(15).first()

    /** 날짜의 주행 목록 (목업은 2026년 11월만) */
    fun tripsOn(date: java.time.LocalDate): List<TripLog> =
        if (date.year == year && date.monthValue == month) trips[date.dayOfMonth].orEmpty() else emptyList()

    /** 11월 날짜 → 그날 주행 목록 */
    val trips: Map<Int, List<TripLog>> = mapOf(
        2 to listOf(
            trip("08:35", "시흥동", "삼성동", 36, 14.8, 5, toWork, "급출발" to "시흥사거리", "브레이크 나눠 밟기" to "분당수서로", "방향지시등 늦게 켬" to "대치동"),
            trip("18:40", "삼성동", "시흥동", 44, 15.2, 3, toHome, "급제동" to "삼성동", "차로 내 흔들림" to "헌릉로"),
        ),
        3 to listOf(
            trip("08:50", "시흥동", "삼성동", 38, 14.8, 6, toWork, "급가속" to "헌릉IC", "급제동" to "수서IC", "방향지시등 미점등" to "삼성동"),
            trip("19:10", "삼성동", "시흥동", 40, 15.2, 4, toHome, "브레이크 나눠 밟기" to "분당수서로", "급출발" to "대치동", "핸들 과다 조작" to "시흥사거리"),
        ),
        4 to listOf(
            trip("08:40", "시흥동", "삼성동", 33, 14.8, 4, toWork, "급출발" to "시흥사거리", "브레이크 나눠 밟기" to "분당수서로"),
        ),
        6 to listOf(
            trip("08:45", "시흥동", "삼성동", 35, 14.8, 5, toWork, "브레이크 나눠 밟기" to "분당수서로", "방향지시등 늦게 켬" to "수서IC", "급제동" to "삼성동"),
            trip("18:25", "삼성동", "시흥동", 42, 15.2, 3, toHome, "급출발" to "삼성동", "차로 내 흔들림" to "대왕판교로"),
        ),
        9 to listOf(
            trip("10:30", "시흥동", "판교", 17, 7.9, 2, toPangyo, "급가속" to "대왕판교로", "방향지시등 늦게 켬" to "판교IC"),
            trip("15:20", "판교", "시흥동", 19, 7.9, 1, fromPangyo, "정차 직전 급제동" to "시흥동 교차로"),
        ),
        10 to listOf(
            trip("08:38", "시흥동", "삼성동", 37, 14.8, 6, toWork, "급출발" to "시흥사거리", "급가속" to "헌릉IC", "브레이크 나눠 밟기" to "분당수서로"),
            trip("18:50", "삼성동", "시흥동", 39, 15.2, 4, toHome, "방향지시등 미점등" to "수서IC", "정차 직전 급제동" to "시흥사거리"),
        ),
        11 to listOf(
            trip("08:44", "시흥동", "삼성동", 34, 14.8, 5, toWork, "브레이크 나눠 밟기" to "분당수서로", "핸들 과다 조작" to "삼성동"),
            trip("18:30", "삼성동", "시흥동", 41, 15.2, 3, toHome, "급출발" to "대치동", "급제동" to "헌릉로", "방향지시등 늦게 켬" to "시흥사거리"),
        ),
        12 to listOf(
            trip("08:41", "시흥동", "삼성동", 35, 14.8, 4, toWork, "급가속" to "대왕판교로", "방향지시등 늦게 켬" to "대치동"),
            trip("19:05", "삼성동", "시흥동", 43, 15.2, 4, toHome, "브레이크 나눠 밟기" to "분당수서로", "급제동" to "헌릉IC"),
        ),
        13 to listOf(
            trip("08:47", "시흥동", "삼성동", 36, 14.8, 5, toWork, "급출발" to "시흥사거리", "급제동" to "수서IC", "차로 내 흔들림" to "헌릉로"),
            trip("18:35", "삼성동", "시흥동", 40, 15.2, 2, toHome, "정차 직전 급제동" to "시흥사거리", "방향지시등 미점등" to "대치동"),
        ),
        15 to listOf(
            // 홈 '최근 주행'과 같은 주행 (구간도 홈 지도와 동일)
            trip("08:42", "시흥동", "삼성동", 34, 14.8, 7, toWork,
                "급출발" to "시흥사거리", "브레이크 나눠 밟기" to "분당수서로", "방향지시등 늦게 켬" to "대치동",
                "핸들 급조작" to "대치동 교차로 우회전",
                times = listOf("08:44", "09:05", "09:12", "09:14")),
            trip("18:20", "삼성동", "시흥동", 41, 15.2, 4, toHome, "급제동" to "삼성동", "브레이크 나눠 밟기" to "분당수서로", "방향지시등 늦게 켬" to "헌릉IC"),
            trip("20:05", "시흥동", "판교", 18, 7.9, 1, toPangyo, "급출발" to "시흥동 교차로"),
        ),
    )
}

// ---------------- 리포트: 내 운전 vs 권장 비교 (이벤트 전후 -2초 ~ 4초, 0.1초 간격 61포인트)

/** 문장 안의 **굵게** 표시는 화면에서 굵은 글씨로 바뀜 */
sealed interface Comparison {
    val summary: String

    /** 꺾은선 2개: 내 운전(분류 색 실선) vs 권장(회색 점선) */
    data class Chart(
        val unit: String, val mine: List<Float>, val recommended: List<Float>,
        val yMin: Float, val yMax: Float, override val summary: String,
    ) : Comparison

    /** 그래프가 어색한 항목(방향지시등): 타임라인 한 줄 */
    data class Timeline(override val summary: String) : Comparison
}

object ComparisonData {
    const val T_START = -2f
    const val T_STEP = 0.1f
    private val times = List(61) { T_START + it * T_STEP }

    /** t0부터 dur초 동안 0→1로 부드럽게 (smoothstep) */
    private fun ramp(t: Float, t0: Float, dur: Float): Float {
        val x = ((t - t0) / dur).coerceIn(0f, 1f)
        return x * x * (3 - 2 * x)
    }
    /** t0에서 시작해 rise초 동안 올라가고 hold초 유지, fall초 동안 내려오는 산 */
    private fun hill(t: Float, t0: Float, rise: Float, hold: Float, fall: Float) =
        ramp(t, t0, rise) - ramp(t, t0 + rise + hold, fall)
    private fun series(f: (Float) -> Float) = times.map(f)

    private val steering = Comparison.Chart(
        "조향각(°)",
        mine = series { 180f * hill(it, 0f, 0.8f, 0.4f, 0.8f) },
        recommended = series { 120f * hill(it, 0f, 2f, 0f, 2f) },
        yMin = 0f, yMax = 200f,
        summary = "핸들을 최대 초당 **210°**로 돌렸어요 → 초당 **90°** 이하로 여유 있게 돌려 보세요",
    )
    private val accel = Comparison.Chart(
        "가속 페달(%)",
        mine = series { 80f * ramp(it, 0f, 0.6f) },
        recommended = series { 40f * ramp(it, 0f, 2f) },
        yMin = 0f, yMax = 100f,
        summary = "**0.6초** 만에 페달을 **80%** 밟았어요 → **2초** 정도에 걸쳐 천천히 밟아 보세요",
    )
    private val brakeSplit = Comparison.Chart(
        "감속도(m/s²)",
        // 톱니 모양: 빠르게 올라갔다 떨어지기를 3번
        mine = series { 4.0f * hill(it, -0.2f, 0.35f, 0.1f, 0.45f) + 3.6f * hill(it, 0.9f, 0.35f, 0.1f, 0.45f) + 3.9f * hill(it, 2.0f, 0.35f, 0.1f, 0.5f) },
        recommended = series { 2.5f * hill(it, -0.2f, 1.2f, 1.2f, 1.3f) },
        yMin = 0f, yMax = 5f,
        summary = "브레이크를 **3번** 나눠 밟았어요 → **한 번에** 일정한 힘으로 밟아 보세요",
    )
    private val hardBrake = Comparison.Chart(
        "감속도(m/s²)",
        mine = series { 6.5f * hill(it, 0f, 0.4f, 0.8f, 0.6f) },
        recommended = series { 3.0f * hill(it, -1f, 1.5f, 1f, 1.5f) },
        yMin = 0f, yMax = 8f,
        summary = "감속도가 최대 **6.5m/s²**까지 올라갔어요 → 미리 **3m/s²** 이하로 천천히 줄여 보세요",
    )
    private val stopBrake = Comparison.Chart(
        "감속도(m/s²)",
        // 일정하게 줄이다가 멈추기 직전에 확 밟음
        mine = series { 2f * ramp(it, -2f, 0.5f) * (1 - ramp(it, 2.4f, 0.2f)) + 3.5f * hill(it, 1.6f, 0.3f, 0.3f, 0.2f) },
        recommended = series { 2f * ramp(it, -2f, 0.5f) * (1 - ramp(it, 0.5f, 2.5f)) },
        yMin = 0f, yMax = 6f,
        summary = "멈추기 직전 감속도가 **5.5m/s²**로 튀었어요 → 마지막 **1초**는 브레이크를 살짝 풀어 보세요",
    )
    private val sway = Comparison.Chart(
        "조향각(°)",
        mine = series { 12f * kotlin.math.sin(it * 4.2f) },
        recommended = series { 3f * kotlin.math.sin(it * 2.1f) },
        yMin = -16f, yMax = 16f,
        summary = "핸들이 좌우로 최대 **12°**씩 흔들렸어요 → **3°** 이내로 부드럽게 잡아 보세요",
    )

    /** 문제 항목 → 비교 그래프 (제목 기준) */
    fun forIssue(i: DriveIssue): Comparison = when (i.title) {
        "핸들 급조작", "핸들 과다 조작" -> steering
        "차로 내 흔들림" -> sway
        "급출발" -> accel
        "급가속" -> accel.copy(summary = "**1초** 만에 페달을 **80%** 밟았어요 → **2초** 이상에 걸쳐 속도를 올려 보세요")
        "브레이크 나눠 밟기" -> brakeSplit
        "급제동" -> hardBrake
        "정차 직전 급제동" -> stopBrake
        "방향지시등 미점등" -> Comparison.Timeline("방향지시등 없이 차로 변경 → 차로를 바꾸기 **3초** 전에 켜 주세요")
        else -> Comparison.Timeline("방향지시등 켬 **0.5초** 후 차로 변경 → **3초** 전에 켜 주세요")
    }
}
