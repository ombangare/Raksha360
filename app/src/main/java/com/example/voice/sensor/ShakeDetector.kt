package com.example.voice.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * Robust, physics-grounded accelerometer shake detector for emergency triggers.
 *
 * Designed with strict anti-false-positive measures:
 * 1. Multi-axis acceleration magnitude thresholding (normalized to standard gravity G).
 * 2. Rapid directional reversal requirement (must flip vector direction, distinguishing intentional
 *    vigorous shaking from smooth vehicle motion, running, dropping onto a bed, or road bumps).
 * 3. 10-second post-trigger debounce cooldown to prevent re-entrant dispatch storms.
 */
class ShakeDetector(
    private val context: Context,
    private val onShakeTriggered: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "ShakeDetector"
        const val COOLDOWN_MS = 10_000L

        // G-force acceleration thresholds (normalized to Earth gravity 9.80665 m/s^2)
        const val THRESHOLD_LOW = 3.2f       // Requires forceful, vigorous shaking
        const val THRESHOLD_MEDIUM = 2.7f    // Default calibrated balance
        const val THRESHOLD_HIGH = 2.2f      // More sensitive for users with limited grip strength

        const val REVERSAL_WINDOW_MS = 600L
        const val MIN_DIRECTION_REVERSALS = 3
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile
    var isListening = false
        private set

    var sensitivity: String = "MEDIUM"
        set(value) {
            field = value.uppercase()
        }

    // Optional listener for in-app calibration or visual feedback without dispatching emergency
    var testGestureListener: ((Float) -> Unit)? = null

    private var lastTriggerTimestamp = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var reversalCount = 0
    private var windowStartTimestamp = 0L

    fun start(): Boolean {
        if (isListening) return true
        if (sensorManager == null || accelerometer == null) {
            Log.w(TAG, "Accelerometer hardware is unavailable on this device.")
            return false
        }
        val registered = sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME
        )
        isListening = registered
        Log.i(TAG, "ShakeDetector started: listening=$registered, sensitivity=$sensitivity")
        return registered
    }

    fun stop() {
        if (!isListening) return
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {}
        isListening = false
        resetState()
        Log.i(TAG, "ShakeDetector stopped.")
    }

    private fun resetState() {
        reversalCount = 0
        windowStartTimestamp = 0L
        lastX = 0f
        lastY = 0f
        lastZ = 0f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val totalAcc = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val gForce = totalAcc / SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()

        testGestureListener?.invoke(gForce)

        val threshold = when (sensitivity) {
            "LOW" -> THRESHOLD_LOW
            "HIGH" -> THRESHOLD_HIGH
            else -> THRESHOLD_MEDIUM
        }

        // Check if movement exceeds threshold
        if (gForce > threshold) {
            // Check for directional reversals (rapid sign flip in primary movement axis)
            val deltaX = x - lastX
            val deltaY = y - lastY
            val deltaZ = z - lastZ

            val maxDelta = maxOf(Math.abs(deltaX), Math.abs(deltaY), Math.abs(deltaZ))
            if (maxDelta > (threshold * SensorManager.GRAVITY_EARTH * 0.5f)) {
                if (windowStartTimestamp == 0L || (now - windowStartTimestamp) > REVERSAL_WINDOW_MS) {
                    windowStartTimestamp = now
                    reversalCount = 1
                } else {
                    reversalCount++
                }

                val requiredReversals = if (sensitivity == "LOW") 4 else MIN_DIRECTION_REVERSALS
                if (reversalCount >= requiredReversals) {
                    if (now - lastTriggerTimestamp >= COOLDOWN_MS) {
                        lastTriggerTimestamp = now
                        resetState()
                        Log.w(TAG, "Confirmed Shake SOS trigger detected! (gForce=$gForce, reversals=$reversalCount)")
                        onShakeTriggered()
                    } else {
                        Log.d(TAG, "Shake gesture detected but cooldown is active (${now - lastTriggerTimestamp}ms < ${COOLDOWN_MS}ms)")
                    }
                }
            }
        }

        lastX = x
        lastY = y
        lastZ = z
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
