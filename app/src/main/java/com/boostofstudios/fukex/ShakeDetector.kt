package com.boostofstudios.fukex

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {
    private var shakeCount = 0
    private var lastShakeTime: Long = 0
    private val SHAKE_THRESHOLD_GRAVITY = 1.35F
    private val SHAKE_SLOP_TIME_MS = 150L
    private val SHAKE_COUNT_RESET_TIME_MS = 2500L

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()
            
            // Ignore shakes that are too close to each other
            if (lastShakeTime + SHAKE_SLOP_TIME_MS > now) {
                return
            }
            
            // Reset count if it's been too long since the last shake
            if (lastShakeTime + SHAKE_COUNT_RESET_TIME_MS < now) {
                shakeCount = 0
            }

            lastShakeTime = now
            shakeCount++

            if (shakeCount >= 2) {
                shakeCount = 0
                onShake()
            }
        }
    }
}
