package com.example.drivenote

import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.drivenote.ui.theme.DriveNoteTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val engine = CoachingEngine()
    private lateinit var vehicle: VehicleRepository
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // 차속(CAR_SPEED)은 런타임 권한이라 사용자 허용 후 구독
    private val speedPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vehicle.startSpeed() else Log.w(TAG, "CAR_SPEED 권한 거부 → 차속 기반 감지 비활성")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initOsmdroid(applicationContext) // 지도 User-Agent·캐시 경로 (지도 생성 전에)
        vehicle = VehicleRepository(this, engine)
        vehicle.start()
        requestSpeedPermissionIfNeeded()
        initTts()

        setContent {
            DriveNoteTheme(darkTheme = false) { DriveNoteApp(vehicle, engine, ::speak) }
        }
    }

    override fun onDestroy() {
        vehicle.stop()
        try { tts?.shutdown() } catch (t: Throwable) { Log.w(TAG, "TTS 종료 실패", t) }
        super.onDestroy()
    }

    private fun requestSpeedPermissionIfNeeded() {
        try {
            if (ContextCompat.checkSelfPermission(this, CAR_SPEED) != PackageManager.PERMISSION_GRANTED) {
                speedPermission.launch(CAR_SPEED)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "권한 요청 실패", t)
        }
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val r = tts?.setLanguage(Locale.KOREAN)
                    ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                    if (!ttsReady) Log.w(TAG, "한국어 TTS 미지원")
                } else {
                    Log.w(TAG, "TTS 초기화 실패: $status")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "TTS 생성 실패", t)
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "coaching")
        } catch (t: Throwable) {
            Log.w(TAG, "TTS 재생 실패", t)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val CAR_SPEED = "android.car.permission.CAR_SPEED"
    }
}
