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
    val lastDriveIssues = 8 // 최근 주행 1회의 개선 필요 건수 (홈 카드) = homeIssues 합계
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
    // 홈 '개선할 점' 패널: 최근 주행의 개선 필요 항목 (이름, 분류, 건수) — 합계 8건 (= lastDriveIssues)
    val homeIssues = listOf(
        Triple("브레이크 나눠 밟기", IssueType.BRAKE, 2),
        Triple("정차 직전 급제동", IssueType.BRAKE, 1),
        Triple("급출발·급가속", IssueType.ACCEL, 2),
        Triple("방향지시등 늦게 켬", IssueType.SIGNAL, 1),
        Triple("우회전 전 일시정지 안 함", IssueType.SIGNAL, 1),
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

/**
 * 주행 중 감지된 문제 1건: route[from]..route[to] 구간을 색으로 표시하고, 경로 위 pin 위치에 번호 핀.
 * pin은 경로 인덱스(소수 가능: 5.5 = route[5]와 route[6] 사이 가운데) → 핀은 항상 경로 선 위
 */
data class DriveIssue(
    val no: Int, val type: IssueType, val title: String, val time: String, val place: String, val tip: String,
    val from: Int, val to: Int, val pin: Float = ((from + to) / 2).toFloat(),
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
        "고등동 교차로 우회전" to (5 to 7), "세곡동 사거리" to (8 to 10),
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
        "우회전 전 일시정지 안 함" to "횡단보도 앞에서 완전히 멈췄다가 출발해 보세요.",
    )
    private val typeOf = mapOf(
        "급출발" to IssueType.ACCEL, "급가속" to IssueType.ACCEL,
        "브레이크 나눠 밟기" to IssueType.BRAKE, "급제동" to IssueType.BRAKE, "정차 직전 급제동" to IssueType.BRAKE,
        "방향지시등 늦게 켬" to IssueType.SIGNAL, "방향지시등 미점등" to IssueType.SIGNAL,
        "핸들 과다 조작" to IssueType.STEER, "차로 내 흔들림" to IssueType.STEER, "핸들 급조작" to IssueType.STEER,
        "우회전 전 일시정지 안 함" to IssueType.SIGNAL,
    )

    /** 문제 목록 생성: (제목, 장소) 순서대로 번호를 붙이고, 시간은 경로상 위치로 계산 (explicit이 있으면 그 시간) */
    private fun trip(
        start: String, from: String, to: String, minutes: Int, km: Double, count: Int, route: List<GeoPt>,
        vararg issues: Pair<String, String>, times: List<String>? = null,
        pins: List<Float>? = null, // 핀 위치(경로 인덱스) 직접 지정. 없으면 구간 가운데
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
            val pin = pins?.get(i)?.coerceIn(a.toFloat(), b.toFloat()) ?: ((a + b) / 2).toFloat()
            DriveIssue(i + 1, typeOf.getValue(title), title, time, place, tips.getValue(title), a, b, pin)
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
            // 핀은 전체 경로가 보이는 기본 배율에서 서로 약 2.6km(≈60dp) 이상 떨어지게 배치
            trip("08:42", "시흥동", "삼성동", 34, 14.8, 8, toWork,
                "급출발" to "시흥사거리", "핸들 급조작" to "고등동 교차로 우회전", "우회전 전 일시정지 안 함" to "세곡동 사거리",
                "브레이크 나눠 밟기" to "분당수서로", "방향지시등 늦게 켬" to "대치동",
                times = listOf("08:44", "08:52", "08:57", "09:05", "09:12"),
                pins = listOf(2f, 6.1f, 9f, 14.7f, 17.1f)),
            trip("18:20", "삼성동", "시흥동", 41, 15.2, 4, toHome, "급제동" to "삼성동", "브레이크 나눠 밟기" to "분당수서로", "방향지시등 늦게 켬" to "헌릉IC"),
            trip("20:05", "시흥동", "판교", 18, 7.9, 1, toPangyo, "급출발" to "시흥동 교차로"),
        ),
    )
}

// ---------------- 리포트: 내 운전 vs 권장 비교 (이벤트 전후 -2초 ~ 4초, 0.1초 간격 61포인트)

/** 문장 안의 **굵게** 표시는 화면에서 굵은 글씨로 바뀜 */
sealed interface Comparison {
    val summary: String
    val metric: String? get() = null // 그림 아래 지표 한 줄 (없으면 생략)

    /** 꺾은선 2개: 내 운전(분류 색 실선) vs 권장(회색 점선) */
    data class Chart(
        val unit: String, val mine: List<Float>, val recommended: List<Float>,
        val yMin: Float, val yMax: Float, override val summary: String,
    ) : Comparison

    /** 그래프가 어색한 항목: 타임라인 한 줄 */
    data class Timeline(override val summary: String) : Comparison

    /** 정지선 접근: 정지선까지 남은 거리(m), 0.1초 간격. 그림은 0.5초마다 샘플링 */
    data class StopApproach(
        val mine: List<Float>, val recommended: List<Float>, override val metric: String, override val summary: String,
    ) : Comparison

    /** 차로 변경: 방향지시등을 켠 지점이 차로를 넘은 지점보다 몇 m 앞인지 */
    data class LaneChange(
        val mineSignalM: Float, val recSignalM: Float, override val metric: String, override val summary: String,
    ) : Comparison

    /** 회전 궤적: 자전거 모델로 계산한 위에서 본 궤적 2개 (내 운전, 권장) */
    data class TurnPath(
        val mine: Bicycle.Turn, val recommended: Bicycle.Turn, override val metric: String, override val summary: String,
    ) : Comparison

    /** 횡단보도 앞 속도: 횡단보도까지 남은 거리(m, 음수는 지난 뒤) → 속도(km/h) */
    data class CrossingSpeed(
        val distance: List<Float>, val mine: List<Float>, val recommended: List<Float>,
        override val metric: String, override val summary: String,
    ) : Comparison
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
    private val sway = Comparison.Chart(
        "조향각(°)",
        mine = series { 12f * kotlin.math.sin(it * 4.2f) },
        recommended = series { 3f * kotlin.math.sin(it * 2.1f) },
        yMin = -16f, yMax = 16f,
        summary = "핸들이 좌우로 최대 **12°**씩 흔들렸어요 → **3°** 이내로 부드럽게 잡아 보세요",
    )

    /** 40km/h에서 정지할 때 정지선까지 남은 거리 (0.1초 간격). decel(v) = 그 속도에서의 감속도(m/s²) */
    private fun stopRemain(decel: (Float) -> Float): List<Float> {
        var v = 40f / 3.6f; var s = 0f
        val pos = mutableListOf(0f)
        while (v > 0f) {
            val nv = maxOf(0f, v - decel(v) * 0.1f)
            s += (v + nv) / 2 * 0.1f; v = nv; pos += s
        }
        return pos.map { s - it }
    }
    private val stopApproach = Comparison.StopApproach(
        // 내 운전: 7.5m/s까지 살살(0.08g) 줄이다가 마지막에 0.45g로 급제동 → 마지막 점들이 뭉침
        mine = stopRemain { v -> if (v > 7.5f) 0.8f else 4.4f },
        // 권장: 0.2g로 일정하게 → 간격이 고르게 좁아짐
        recommended = stopRemain { 1.96f },
        metric = "멈추기 직전 감속 **0.45g** → 권장 **0.2g** 이하",
        summary = "정지선 **20m** 전부터 천천히 힘을 빼며 멈추면 덜 덜컹거려요",
    )
    private val laneChange = Comparison.LaneChange(
        12f, 30f,
        metric = "차로 변경 **12m** 전에 켬 → 권장 **30m** 전 (약 **3초** 전)",
        summary = "차로를 바꾸기 **3초** 전쯤 켜면 뒤차가 미리 알 수 있어요",
    )
    private fun smooth01(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3 - 2 * c) }
    private val crossingDist = List(77) { 30f - it * 0.5f } // 30m → -8m
    private val crossing = Comparison.CrossingSpeed(
        crossingDist,
        // 내 운전: 3m 앞에서 최저 8km/h, 그대로 다시 가속
        mine = crossingDist.map { d -> if (d >= 3f) 8f + 17f * smooth01((d - 3f) / 27f) else 8f + 7f * smooth01((3f - d) / 11f) },
        // 권장: 1m 앞에서 0km/h까지 멈췄다가 출발
        recommended = crossingDist.map { d -> if (d >= 1f) 25f * smooth01((d - 1f) / 29f) else 12f * smooth01((1f - d) / 9f) },
        metric = "최저 속도 **8km/h** → 권장 **0km/h** (완전히 정지)",
        summary = "우회전 전 횡단보도 앞에서는 **완전히** 멈췄다 출발해요",
    )

    /**
     * 20km/h 우회전 핸들각 시계열 (0.1초 간격). start초에 시작해 ramp초 동안 peak°까지 돌리고 hold초 유지, ramp초 동안 풂.
     * hold는 최종 진행 방향이 90° 우회전이 되도록 이분 탐색으로 맞춤
     */
    private fun rightTurn(start: Float, ramp: Float, peak: Float, total: Float = 6f): Bicycle.Turn {
        val n = (total / 0.1f).toInt()
        fun s01(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3 - 2 * c) }
        fun steer(hold: Float) = List(n) { i -> val t = i * 0.1f; peak * (s01((t - start) / ramp) - s01((t - start - ramp - hold) / ramp)) }
        val speed = List(n) { 20f }
        var lo = 0f; var hi = 4f
        repeat(40) {
            val mid = (lo + hi) / 2
            val turn = Bicycle.simulate(steer(mid), speed)
            if (turn.headings.last() - turn.headings.first() < (Math.PI / 2).toFloat()) lo = mid else hi = mid
        }
        return Bicycle.simulate(steer((lo + hi) / 2), speed)
    }
    private val turnPath: Comparison.TurnPath by lazy {
        val mine = rightTurn(start = 1.5f, ramp = 0.5f, peak = 420f) // 늦게, 빠르게, 많이
        val rec = rightTurn(start = 0.5f, ramp = 1.4f, peak = 250f)  // 1초 일찍, 천천히, 덜
        Comparison.TurnPath(
            mine, rec,
            // {{ }} = 분류 색 굵게(내 운전), [[ ]] = 진회색 굵게(권장)
            metric = "최대 쏠림 {{%.2fg}} → 권장 [[%.2fg]] 이하   회전 반경 {{%.1fm}} → 권장 [[%.1fm]]"
                .format(mine.maxLatG, rec.maxLatG, mine.radius, rec.radius),
            summary = "교차로 진입 전에 핸들을 조금 일찍, 천천히 돌리면 몸이 덜 쏠려요",
        )
    }

    /** 문제 항목 → 비교 그래프 (제목 기준) */
    fun forIssue(i: DriveIssue): Comparison = when (i.title) {
        "정차 직전 급제동" -> stopApproach
        "방향지시등 늦게 켬" -> laneChange
        "우회전 전 일시정지 안 함" -> crossing
        "핸들 급조작", "핸들 과다 조작" -> turnPath
        "차로 내 흔들림" -> sway
        "급출발" -> accel
        "급가속" -> accel.copy(summary = "**1초** 만에 페달을 **80%** 밟았어요 → **2초** 이상에 걸쳐 속도를 올려 보세요")
        "브레이크 나눠 밟기" -> brakeSplit
        "급제동" -> hardBrake
        "방향지시등 미점등" -> Comparison.Timeline("방향지시등 없이 차로 변경 → 차로를 바꾸기 **3초** 전에 켜 주세요")
        else -> Comparison.Timeline("방향지시등 켬 **0.5초** 후 차로 변경 → **3초** 전에 켜 주세요")
    }
}

// ---------------- 자전거 모델 (쏠림 요약 · 회전 궤적 공용)

object Bicycle {
    const val STEER_RATIO = 15f // 핸들각 : 바퀴 각
    const val WHEELBASE = 3.0f  // 축거(m)
    const val G = 9.81f

    /** 요레이트 ω(rad/s) = v·tanδ / 축거. v(m/s), δ = 바퀴 각(rad) */
    fun yawRate(v: Float, wheelRad: Float) = v * kotlin.math.tan(wheelRad) / WHEELBASE

    /** 핸들각(°) → 바퀴 각(rad) */
    fun wheelRad(steerDeg: Float) = Math.toRadians((steerDeg / STEER_RATIO).toDouble()).toFloat()

    /**
     * 위에서 본 궤적. x, y는 m (화면 방향: y 아래 +), heading은 rad (위쪽 = -π/2, 시계 방향 +: 우회전)
     * peak = |ω| 최대 지점, radius = v/|ω| (그 지점의 회전 반경), steerStart/steerEnd = 핸들 시작·다 푼 지점
     */
    data class Turn(
        val xs: List<Float>, val ys: List<Float>, val headings: List<Float>,
        val maxLatG: Float, val peak: Int, val radius: Float, val steerStart: Int, val steerEnd: Int,
    )

    fun simulate(steerDeg: List<Float>, speedKmh: List<Float>, dt: Float = 0.1f, heading0: Float = (-Math.PI / 2).toFloat()): Turn {
        var x = 0f; var y = 0f; var th = heading0
        val xs = mutableListOf(x); val ys = mutableListOf(y); val hs = mutableListOf(th)
        val omegas = mutableListOf<Float>(); val vs = mutableListOf<Float>()
        for (i in steerDeg.indices) {
            val v = speedKmh[i] / 3.6f
            val w = yawRate(v, wheelRad(steerDeg[i]))
            th += w * dt
            x += v * kotlin.math.cos(th) * dt; y += v * kotlin.math.sin(th) * dt
            xs += x; ys += y; hs += th; omegas += w; vs += v
        }
        val maxW = omegas.maxOf { kotlin.math.abs(it) }
        // |ω|가 최대인 구간(유지 구간)의 가운데 지점
        val near = omegas.indices.filter { kotlin.math.abs(omegas[it]) >= maxW * 0.999f }
        val pk = near[near.size / 2]
        return Turn(
            xs, ys, hs,
            maxLatG = omegas.indices.maxOf { vs[it] * kotlin.math.abs(omegas[it]) } / G,
            peak = pk + 1, radius = vs[pk] / kotlin.math.abs(omegas[pk]),
            // 핸들을 돌리기 시작한 지점 / 다 푼 지점 (핸들각이 거의 0이 아닌 첫·마지막 샘플)
            steerStart = steerDeg.indexOfFirst { kotlin.math.abs(it) > 1f } + 1,
            steerEnd = steerDeg.indexOfLast { kotlin.math.abs(it) > 1f } + 1,
        )
    }
}

// ---------------- 리포트: 주행 전체 쏠림 요약 (G-G 다이어그램)

/** 쏠림 샘플 1개: 좌우 g(오른쪽 +), 앞뒤 g(가속 +) */
data class GSample(val lat: Float, val lon: Float)
data class GSummary(val samples: List<GSample>, val insidePct: Int, val maxBrake: Float, val maxTurn: Float)

object GForceData {
    const val COMFORT = 0.2f // 편안한 범위 (g)
    private val cache = mutableMapOf<TripLog, GSummary>()

    fun forTrip(t: TripLog): GSummary = cache.getOrPut(t) { generate(t) }

    private fun ramp(t: Float, t0: Float, dur: Float): Float { val x = ((t - t0) / dur).coerceIn(0f, 1f); return x * x * (3 - 2 * x) }
    /** 가운데가 0초인 산 모양 (rise초 올라가고 hold초 유지, rise초 내려옴) */
    private fun hill(t: Float, rise: Float, hold: Float) = ramp(t, -hold / 2 - rise, rise) - ramp(t, hold / 2, rise)

    /**
     * 주행 하나의 샘플 300개 (0.5초 간격) 목업.
     * 평소엔 작은 흔들림만 있고, 문제 항목마다 그 종류의 쏠림 이벤트를 넣음.
     * 앞뒤 g = 차속 미분, 좌우 g = v·ω (자전거 모델: 조향각 δ → ω = v·tanδ / 축거)
     */
    private fun generate(t: TripLog): GSummary {
        val n = 300; val dt = 0.5f; val g = Bicycle.G; val wheelBase = Bicycle.WHEELBASE
        val rnd = java.util.Random(t.start.hashCode().toLong() * 31 + t.route.size)
        val lonEv = FloatArray(n); val latEv = FloatArray(n); val env = FloatArray(n)
        fun add(arr: FloatArray, center: Float, peak: Float, rise: Float, hold: Float) {
            for (i in 0 until n) {
                val v = peak * hill(i * dt - center, rise, hold)
                arr[i] += v
                env[i] = maxOf(env[i], kotlin.math.abs(v / peak))
            }
        }
        t.issues.forEachIndexed { k, issue ->
            // 이벤트 시각(초). 샘플 시각에 맞춰 최고점이 샘플에 찍히게 함
            val c = kotlin.math.round((k + 1) * n / (t.issues.size + 1f)) * dt
            when (issue.title) {
                // 완만한 산 모양: 샘플들이 한 점에 겹치지 않고 0.2g 밖에서 퍼지게
                "급출발" -> add(lonEv, c, 0.35f, 2.4f, 0.5f)
                "급가속" -> add(lonEv, c, 0.32f, 1.8f, 0f)
                "브레이크 나눠 밟기" -> { add(lonEv, c, -0.45f, 1.3f, 0f); add(lonEv, c + 3f, -0.38f, 1.3f, 0f); add(lonEv, c + 6f, -0.41f, 1.3f, 0f) }
                "급제동" -> add(lonEv, c, -0.5f, 0.5f, 0.8f)
                "정차 직전 급제동" -> add(lonEv, c, -0.45f, 0.4f, 0.4f)
                "핸들 급조작" -> add(latEv, c, 0.56f, 2.2f, 0f)
                "핸들 과다 조작" -> add(latEv, c, -0.48f, 0.8f, 0.4f)
                "차로 내 흔들림" -> { add(latEv, c, 0.24f, 0.6f, 0.2f); add(latEv, c + 2f, -0.24f, 0.6f, 0.2f); add(latEv, c + 4f, 0.22f, 0.6f, 0.2f) }
                "우회전 전 일시정지 안 함" -> { add(latEv, c, 0.3f, 2f, 1.5f); add(lonEv, c - 3f, -0.15f, 1f, 0.5f) }
                else -> { add(latEv, c, 0.15f, 1f, 0.5f); add(latEv, c + 2.5f, -0.15f, 1f, 0.5f) } // 차로 변경: 편안한 범위 안
            }
        }
        var v = 13f // m/s
        val samples = List(n) { i ->
            val calm = 1f - env[i] // 이벤트 중에는 잡음·속도 복원을 줄임
            val aG = lonEv[i] + calm * 0.045f * rnd.nextGaussian().toFloat()
            val a = aG * g - calm * 0.08f * (v - 13f)
            val nv = maxOf(1f, v + a * dt)
            val lon = (nv - v) / dt / g // 차속 미분
            val latWanted = latEv[i] + calm * 0.045f * rnd.nextGaussian().toFloat()
            val delta = kotlin.math.atan(latWanted * g * wheelBase / (v * v)) // 바퀴 각
            val omega = Bicycle.yawRate(v, delta)                              // 요레이트
            val lat = v * omega / g                                            // 좌우 g = v·ω
            v = nv
            GSample(lat, lon)
        }
        val inside = samples.count { kotlin.math.hypot(it.lat, it.lon) <= COMFORT }
        return GSummary(
            samples, (inside * 100f / n).roundToIntSafe(),
            maxBrake = samples.maxOf { -it.lon }.coerceAtLeast(0f),
            maxTurn = samples.maxOf { kotlin.math.abs(it.lat) },
        )
    }
    private fun Float.roundToIntSafe() = kotlin.math.round(this).toInt()
}
