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
    val userName = "지민"

    val lastDrive = DriveSummary("11월 15일 (일) 08:42", "집 → 회사", 34, 14.8)
    val lastDriveArrive = "09:16"
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
        WeekStat("개선 필요", "19건"),
        WeekStat("지난주 대비", "-6건"),
    )

    // 주행 중 화면: 가장 최근 음성 코칭
    val recentCoaching = "출발할 때 가속 페달을 조금 더 천천히 밟아 보면 좋아요"
    val recentCoachingCategory = Category.ACCEL

    // 리포트 공유 대상 (열람 전용)
    val sharedWith = "엄마"

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
