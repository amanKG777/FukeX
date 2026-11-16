package com.boostofstudios.fukex

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

// Fires after two shakes stronger than thresholdG within a couple of seconds.
// thresholdG comes from the "Shake Sensitivity" setting and can change while registered.
class ShakeDetector(
    @Volatile var thresholdG: Float,
    private val onShake: () -> Unit
) : SensorEventListener {
    private var shakeCount = 0
    private var lastShakeTime: Long = 0
    private var lastTriggerTime: Long = 0

    private companion object {
        const val SHAKE_SLOP_TIME_MS = 150L
        const val SHAKE_COUNT_RESET_TIME_MS = 2500L
        // Don't fire again straight away while the phone is still moving from the last trigger
        const val TRIGGER_COOLDOWN_MS = 2000L
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values.size < 3) return
        val gX = event.values[0] / SensorManager.GRAVITY_EARTH
        val gY = event.values[1] / SensorManager.GRAVITY_EARTH
        val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
        if (gForce <= thresholdG) return

        // Monotonic clock: wall-clock changes must not break the timing
        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerTime < TRIGGER_COOLDOWN_MS) return
        // Ignore readings from the same jolt
        if (now - lastShakeTime < SHAKE_SLOP_TIME_MS) return
        if (now - lastShakeTime > SHAKE_COUNT_RESET_TIME_MS) shakeCount = 0

        lastShakeTime = now
        shakeCount++
        if (shakeCount >= 2) {
            shakeCount = 0
            lastTriggerTime = now
            onShake()
        }
    }
}
