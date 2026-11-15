package com.example.drivenote

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 앱에서 쓰는 단순화된 기어 (SDK의 GEAR_1~9 등은 D로 묶음) */
enum class DriveGear { P, R, N, D }

/** 방향지시등 상태 */
enum class Signal(val label: String) { OFF("끔"), LEFT("왼쪽"), RIGHT("오른쪽") }

/**
 * Pleos Vehicle SDK(기어·브레이크·핸들·방향지시등)와 Android Car API(차속)를 묶어
 * StateFlow로 노출한다. 값이 없거나 SDK가 실패하면 null로 남고, 앱은 데모 데이터로 동작한다.
 *
 * 값이 들어올 때마다 [engine]에도 바로 전달해서, 화면 갱신 주기와 상관없이 감지가 이뤄지게 한다.
 */
class VehicleRepository(context: Context, private val engine: CoachingEngine) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val _gear = MutableStateFlow<DriveGear?>(null)
    private val _speedKmh = MutableStateFlow<Float?>(null)
    private val _brakeOn = MutableStateFlow<Boolean?>(null)
    private val _steeringDeg = MutableStateFlow<Float?>(null)
    private val _turnSignal = MutableStateFlow<Signal?>(null)
    private val _connected = MutableStateFlow(false)

    val gear: StateFlow<DriveGear?> = _gear.asStateFlow()
    val speedKmh: StateFlow<Float?> = _speedKmh.asStateFlow()
    val brakeOn: StateFlow<Boolean?> = _brakeOn.asStateFlow()
    val steeringDeg: StateFlow<Float?> = _steeringDeg.asStateFlow()
    val turnSignal: StateFlow<Signal?> = _turnSignal.asStateFlow()
    /** Vehicle SDK에서 값을 하나라도 받았는지 (false면 데모 모드) */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var vehicle: Vehicle? = null
    private var registered = false
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null

    // ---- Vehicle SDK 리스너 (같은 인스턴스로 등록/해제)
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = onGear(gear)
        override fun onFailed(exception: Exception) = logFail("gear", exception)
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) = onBrake(status)
        override fun onFailed(exception: Exception) = logFail("brake", exception)
    }
    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) = onSteering(angle)
        override fun onFailed(exception: Exception) = logFail("steering", exception)
    }
    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = onTurnSignal(state)
        override fun onFailed(exception: Exception) = logFail("turnSignal", exception)
    }

    // ---- 차속 (Android Car API, m/s)
    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val ms = (value.value as? Float) ?: return
            val kmh = ms * 3.6f
            _speedKmh.value = kmh
            engine.onSpeed(ms, SystemClock.elapsedRealtime())
        }
        override fun onErrorEvent(propertyId: Int, zone: Int) {
            Log.w(TAG, "speed error event prop=$propertyId zone=$zone")
        }
    }

    fun start() {
        startVehicleSdk()
        startSpeed()
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        val v = vehicle
        if (v != null) {
            if (registered) {
                safe("unregister") {
                    v.getGear().unregisterGearSelection(gearListener)
                    v.getBrake().unregisterBrakePedalStatus(brakeListener)
                    v.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener)
                    v.getTurnSignal().unregisterTurnSignalState(turnSignalListener)
                }
            }
            safe("release") { v.release() }
        }
        vehicle = null
        registered = false
        @Suppress("DEPRECATION")
        safe("speed unregister") { propertyManager?.unregisterCallback(speedCallback) }
        safe("car disconnect") { car?.disconnect() }
        propertyManager = null
        car = null
    }

    // ---------------- Vehicle SDK

    private fun startVehicleSdk() {
        val v = try {
            Vehicle(appContext).also { it.initialize() }
        } catch (t: Throwable) {
            // Pleos Vehicle Service가 없는 기기 등: 앱은 데모 모드로 계속 동작
            Log.w(TAG, "Vehicle SDK 초기화 실패 → 데모 모드", t)
            return
        }
        vehicle = v
        // initialize()는 서비스 바인딩을 비동기로 진행하므로, 잠시 뒤 등록하고 실패하면 몇 번 재시도
        registerWithRetry(v, attempt = 1)
    }

    private fun registerWithRetry(v: Vehicle, attempt: Int) {
        main.postDelayed({
            if (vehicle !== v) return@postDelayed
            val ok = try {
                v.getGear().registerGearSelection(gearListener)
                v.getBrake().registerBrakePedalStatus(brakeListener)
                v.getSteeringWheel().registerSteeringWheelAngle(steeringListener)
                v.getTurnSignal().registerTurnSignalState(turnSignalListener)
                true
            } catch (t: Throwable) {
                Log.w(TAG, "Vehicle 리스너 등록 실패 (시도 $attempt)", t)
                false
            }
            if (ok) {
                registered = true
                readInitialValues(v)
                Log.i(TAG, "Vehicle 리스너 등록 완료")
            } else if (attempt < MAX_ATTEMPTS) {
                // 일부만 등록됐을 수 있으니 해제 후 재시도
                safe("unregister before retry") {
                    v.getGear().unregisterGearSelection(gearListener)
                    v.getBrake().unregisterBrakePedalStatus(brakeListener)
                    v.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener)
                    v.getTurnSignal().unregisterTurnSignalState(turnSignalListener)
                }
                registerWithRetry(v, attempt + 1)
            }
        }, RETRY_DELAY_MS * attempt)
    }

    /** 리스너는 값이 바뀔 때만 오므로, 시작 시 현재 값을 한 번 읽어 둔다 */
    private fun readInitialValues(v: Vehicle) {
        safe("initial gear") { v.getGear().getCurrentGearSelectionState({ onGear(it) }, { logFail("gear get", it) }) }
        safe("initial brake") { v.getBrake().getCurrentBrakePedalStatus({ onBrake(it) }, { logFail("brake get", it) }) }
        safe("initial steering") { v.getSteeringWheel().getCurrentSteeringWheelAngle({ onSteering(it) }, { logFail("steering get", it) }) }
        safe("initial signal") { v.getTurnSignal().getCurrentTurnSignalState({ onTurnSignal(it) }, { logFail("signal get", it) }) }
    }

    private fun onGear(g: VehicleGear?) {
        val mapped = when (g) {
            VehicleGear.GEAR_PARK -> DriveGear.P
            VehicleGear.GEAR_REVERSE -> DriveGear.R
            VehicleGear.GEAR_NEUTRAL -> DriveGear.N
            null, VehicleGear.GEAR_UNKNOWN -> null
            else -> DriveGear.D // GEAR_DRIVE, GEAR_1~9
        } ?: return
        _connected.value = true
        _gear.value = mapped
    }

    private fun onBrake(on: Boolean?) {
        on ?: return
        _connected.value = true
        _brakeOn.value = on
        engine.onBrake(on, SystemClock.elapsedRealtime())
    }

    private fun onSteering(deg: Float?) {
        deg ?: return
        _connected.value = true
        _steeringDeg.value = deg
        engine.onSteering(deg, SystemClock.elapsedRealtime())
    }

    private fun onTurnSignal(v: TurnSignalValue?) {
        val s = when (v) {
            TurnSignalValue.LEFT -> Signal.LEFT
            TurnSignalValue.RIGHT -> Signal.RIGHT
            TurnSignalValue.OFF -> Signal.OFF
            null -> return
        }
        _connected.value = true
        _turnSignal.value = s
        engine.onTurnSignal(s, SystemClock.elapsedRealtime())
    }

    // ---------------- 차속 (Android Car API)

    /** CAR_SPEED 런타임 권한이 허용된 뒤 다시 호출해도 안전하다 */
    fun startSpeed() {
        if (propertyManager != null) return
        try {
            val c = Car.createCar(appContext) ?: run { Log.w(TAG, "Car 서비스 없음"); return }
            val pm = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: run {
                Log.w(TAG, "CarPropertyManager 없음"); return
            }
            @Suppress("DEPRECATION")
            val ok = pm.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FASTEST)
            if (!ok) { Log.w(TAG, "차속 콜백 등록 실패"); c.disconnect(); return }
            car = c
            propertyManager = pm
            Log.i(TAG, "차속 콜백 등록 완료")
        } catch (t: Throwable) {
            // 권한 없음(SecurityException), 자동차가 아닌 기기(NoClassDefFoundError) 등
            Log.w(TAG, "차속 구독 실패", t)
        }
    }

    // ---------------- 공통

    private fun logFail(what: String, e: Exception) { Log.w(TAG, "Vehicle $what 실패: ${e.message}") }

    private inline fun safe(what: String, block: () -> Unit) {
        try { block() } catch (t: Throwable) { Log.w(TAG, "$what 실패", t) }
    }

    companion object {
        private const val TAG = "VehicleRepository"
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 800L
    }
}
