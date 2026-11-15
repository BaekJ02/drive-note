package com.example.drivenote

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** 실시간으로 감지하는 습관 종류. [templateId]는 MockData.habits의 id (이름·분류·설명 재사용) */
enum class Detection(val templateId: Int, val message: String) {
    HARD_ACCEL(1, "출발할 때 가속 페달을 조금 더 천천히 밟아 보면 좋아요."),
    SPLIT_BRAKE(2, "한 번에 일정한 힘으로 밟으면 차가 덜 울렁거려요."),
    HARD_BRAKE(3, "앞 상황을 미리 보고 일찍 감속하면 급하게 멈추는 일을 줄일 수 있어요."),
    NO_SIGNAL_STEER(4, "차로를 바꾸기 3초 전쯤 방향지시등을 켜면 뒤차가 미리 대비할 수 있어요."),
}

/** 감지 1건. [seq]는 같은 메시지가 다시 나와도 화면·TTS가 새 이벤트로 알아보게 하는 번호 */
data class CoachingEvent(val seq: Int, val type: Detection, val wallTimeMs: Long, val speedKmh: Float, val detail: String)

/** 끝난(또는 진행 중인) 주행 1회 기록 */
data class TripRecord(val startWallMs: Long, val endWallMs: Long, val km: Double, val events: List<CoachingEvent>)

/**
 * 간단한 규칙 기반 실시간 감지.
 * VehicleRepository가 값이 들어올 때마다 onSpeed/onBrake/onSteering/onTurnSignal을 호출한다.
 * 여러 스레드(바인더 콜백)에서 불릴 수 있어 공개 메서드는 모두 @Synchronized.
 */
class CoachingEngine {

    private val _latest = MutableStateFlow<CoachingEvent?>(null)
    private val _trip = MutableStateFlow<TripRecord?>(null)
    /** 가장 최근 코칭 (주행 중 화면 카드 + TTS) */
    val latest: StateFlow<CoachingEvent?> = _latest.asStateFlow()
    /** 이번 주행 기록 (P로 바뀌면 리포트에 표시) */
    val trip: StateFlow<TripRecord?> = _trip.asStateFlow()

    private var tripActive = false
    private var tripStartWall = 0L
    private var km = 0.0
    private val events = mutableListOf<CoachingEvent>()
    private var seq = 0
    private val lastSaid = mutableMapOf<Detection, Long>() // 메시지별 마지막 발생 시각 (elapsed ms)

    // 차속 샘플 (elapsed ms, m/s)
    private val speedSamples = ArrayDeque<Pair<Long, Float>>()
    private var speedMs: Float? = null
    private var lastSpeedT = 0L

    // 브레이크 나눠 밟기: 한 번의 감속 구간 동안 밟은 횟수
    private var brakeOn = false
    private var segmentActive = false
    private var segmentPresses = 0
    private var segmentMinMs = 0f
    private var segmentReported = false

    // 방향지시등 미점등 조향
    private val steerSamples = ArrayDeque<Pair<Long, Float>>()
    private var signal = Signal.OFF
    private var signalOffSince = 0L

    // ---------------- 주행 시작/종료

    @Synchronized
    fun startTrip() {
        if (tripActive) return
        tripActive = true
        tripStartWall = System.currentTimeMillis()
        km = 0.0
        events.clear()
        lastSaid.clear()
        speedSamples.clear()
        steerSamples.clear()
        segmentActive = false
        _latest.value = null
        Log.i(TAG, "주행 시작")
    }

    /** P로 바뀌면 호출. 기록이 있으면 리포트용으로 남긴다 */
    @Synchronized
    fun endTrip() {
        if (!tripActive) return
        tripActive = false
        _trip.value = TripRecord(tripStartWall, System.currentTimeMillis(), km, events.toList())
        Log.i(TAG, "주행 종료: ${events.size}건, ${"%.2f".format(km)}km")
    }

    // ---------------- 입력

    @Synchronized
    fun onSpeed(ms: Float, t: Long) {
        // 거리 적분 (주행 중일 때만)
        if (tripActive && lastSpeedT != 0L && speedMs != null) {
            val dt = (t - lastSpeedT) / 1000.0
            if (dt in 0.0..5.0) km += speedMs!! * dt / 1000.0
        }
        speedMs = ms
        lastSpeedT = t

        speedSamples.addLast(t to ms)
        while (speedSamples.size > 2 && t - speedSamples.first().first > WINDOW_MS) speedSamples.removeFirst()

        // 0.5초 이상 떨어진 두 샘플로 가속도 계산 (센서 잡음 완화)
        val oldest = speedSamples.first()
        val dtSec = (t - oldest.first) / 1000f
        if (dtSec >= 0.5f) {
            val a = (ms - oldest.second) / dtSec
            if (a >= ACCEL_THRESHOLD) emit(Detection.HARD_ACCEL, "가속도 ${"%.1f".format(a)} m/s²")
            if (a <= -BRAKE_THRESHOLD) emit(Detection.HARD_BRAKE, "감속도 ${"%.1f".format(-a)} m/s²")
        }

        // 감속 구간 종료 판정
        if (segmentActive) {
            if (ms < STOP_MS) {
                segmentActive = false // 정지 → 구간 끝
            } else if (ms > segmentMinMs + REACCEL_MS) {
                segmentActive = false // 다시 가속 → 감속 구간이 아님
            } else if (ms < segmentMinMs) {
                segmentMinMs = ms
            }
        }
    }

    @Synchronized
    fun onBrake(on: Boolean, @Suppress("UNUSED_PARAMETER") t: Long) {
        val pressed = on && !brakeOn
        brakeOn = on
        if (!pressed) return
        val v = speedMs ?: return
        if (v < STOP_MS) return // 정차 중에 밟는 건 제외
        if (!segmentActive) {
            segmentActive = true
            segmentPresses = 0
            segmentMinMs = v
            segmentReported = false
        }
        segmentPresses++
        if (segmentPresses >= SPLIT_BRAKE_COUNT && !segmentReported) {
            segmentReported = true
            emit(Detection.SPLIT_BRAKE, "한 번 감속하는 동안 브레이크 ${segmentPresses}번")
        }
    }

    @Synchronized
    fun onTurnSignal(s: Signal, t: Long) {
        if (s == Signal.OFF && signal != Signal.OFF) signalOffSince = t
        signal = s
    }

    @Synchronized
    fun onSteering(deg: Float, t: Long) {
        steerSamples.addLast(t to deg)
        while (steerSamples.isNotEmpty() && t - steerSamples.first().first > STEER_WINDOW_MS) steerSamples.removeFirst()

        val kmh = (speedMs ?: return) * 3.6f
        if (kmh < SIGNAL_SPEED_KMH) return
        if (signal != Signal.OFF) return
        if (signalOffSince != 0L && t - signalOffSince < SIGNAL_GRACE_MS) return // 방금 껐으면 차로 변경 직후로 봄

        val min = steerSamples.minOf { it.second }
        val max = steerSamples.maxOf { it.second }
        if (abs(max - min) >= STEER_DEG) {
            emit(Detection.NO_SIGNAL_STEER, "시속 ${kmh.toInt()}km에서 핸들 ${abs(max - min).toInt()}° 조작")
            steerSamples.clear() // 같은 조작으로 반복 감지하지 않게
        }
    }

    // ---------------- 출력

    private fun emit(type: Detection, detail: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        val last = lastSaid[type]
        if (last != null && now - last < REPEAT_MS) return // 같은 메시지 10초 안 반복 금지
        lastSaid[type] = now
        val e = CoachingEvent(++seq, type, System.currentTimeMillis(), (speedMs ?: 0f) * 3.6f, detail)
        if (tripActive) events += e
        _latest.value = e
        Log.i(TAG, "감지: ${type.name} ($detail)")
    }

    companion object {
        private const val TAG = "CoachingEngine"
        private const val WINDOW_MS = 1500L
        const val ACCEL_THRESHOLD = 3f        // m/s²
        const val BRAKE_THRESHOLD = 4f        // m/s²
        private const val STOP_MS = 1f / 3.6f   // 1km/h 미만이면 정지로 봄
        private const val REACCEL_MS = 5f / 3.6f // 구간 최저 속도보다 5km/h 이상 빨라지면 감속 구간 종료
        const val SPLIT_BRAKE_COUNT = 3
        const val SIGNAL_SPEED_KMH = 30f
        const val STEER_DEG = 30f
        private const val STEER_WINDOW_MS = 3000L
        private const val SIGNAL_GRACE_MS = 3000L
        private const val REPEAT_MS = 10_000L
    }
}

/** 이번 주행 기록을 리포트 화면용 데이터로 변환 (MockData의 이름·분류·설명 재사용) */
object TripReport {
    fun summary(trip: TripRecord): DriveSummary {
        val fmt = SimpleDateFormat("M월 d일 (E) HH:mm", Locale.KOREAN)
        val minutes = ((trip.endWallMs - trip.startWallMs) / 60_000).toInt().coerceAtLeast(1)
        return DriveSummary(fmt.format(Date(trip.startWallMs)), "이번 주행", minutes, "%.1f".format(trip.km).toDouble())
    }

    fun habits(trip: TripRecord): List<Habit> {
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREAN)
        return trip.events.groupBy { it.type }.entries
            .sortedBy { it.key.templateId }
            .map { (type, list) ->
                val tpl = MockData.habits.first { it.id == type.templateId }
                val first = list.first()
                Habit(
                    id = tpl.id,
                    category = tpl.category,
                    name = if (type == Detection.HARD_BRAKE) "급제동" else if (type == Detection.NO_SIGNAL_STEER) "방향지시등 없이 조향" else tpl.name,
                    count = list.size,
                    location = time.format(Date(first.wallTimeMs)) + (if (list.size > 1) " 외 ${list.size - 1}회" else ""),
                    why = "이번 주행에서 ${list.size}번 감지됐어요. 처음 감지: ${first.detail} (시속 ${first.speedKmh.toInt()}km).",
                    tip = type.message,
                    weekly = tpl.weekly.dropLast(1) + list.size,
                )
            }
    }
}
