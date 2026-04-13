package com.example.webrtc360player

import android.content.Context
import android.hardware.*
import android.util.Log
import kotlin.math.abs

/**
 * GyroManager — uses TYPE_ROTATION_VECTOR (sensor fusion) for smooth,
 * drift-free 360° camera rotation. Falls back to raw gyroscope if not available.
 */
class GyroManager(
    context: Context,
    private val onRotation: (deltaYaw: Float, deltaPitch: Float) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Prefer fusion sensor; fallback to raw gyro
    private val rotVecSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val gyroSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val useRotationVector: Boolean
        get() = rotVecSensor != null

    // Rotation matrix storage
    private val rotationMatrix    = FloatArray(16)
    private val orientationAngles = FloatArray(3)
    private var lastAzimuth = Float.NaN
    private var lastPitch   = Float.NaN

    // Raw gyro state
    private var lastTimestamp = 0L

    // Smoothing: exponential moving average
    private var smoothYaw   = 0f
    private var smoothPitch = 0f
    private val alpha = 0.7f   // smoothing factor (0=no update, 1=no smoothing)

    fun start() {
        if (useRotationVector) {
            sensorManager.registerListener(
                this, rotVecSensor, SensorManager.SENSOR_DELAY_GAME
            )
            Log.d("GyroManager", "Using ROTATION_VECTOR sensor (fusion)")
        } else if (gyroSensor != null) {
            sensorManager.registerListener(
                this, gyroSensor, SensorManager.SENSOR_DELAY_GAME
            )
            Log.d("GyroManager", "Using raw GYROSCOPE sensor")
        } else {
            Log.w("GyroManager", "No motion sensor available!")
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        lastAzimuth = Float.NaN
        lastPitch   = Float.NaN
        lastTimestamp = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> handleRotationVector(event)
            Sensor.TYPE_GYROSCOPE       -> handleGyroscope(event)
        }
    }

    private fun handleRotationVector(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        val pitch   = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()

        if (lastAzimuth.isNaN()) {
            lastAzimuth = azimuth
            lastPitch   = pitch
            return
        }

        var dYaw   = azimuth - lastAzimuth
        var dPitch = pitch   - lastPitch

        // Handle wraparound at ±180°
        if (dYaw > 180f)  dYaw -= 360f
        if (dYaw < -180f) dYaw += 360f

        // Deadzone: ignore micro-jitter
        if (abs(dYaw)   < 0.05f) dYaw   = 0f
        if (abs(dPitch) < 0.05f) dPitch = 0f

        // Smooth
        smoothYaw   = alpha * dYaw   + (1f - alpha) * smoothYaw
        smoothPitch = alpha * dPitch + (1f - alpha) * smoothPitch

        if (abs(smoothYaw) > 0.01f || abs(smoothPitch) > 0.01f) {
            onRotation(smoothYaw, smoothPitch)
        }

        lastAzimuth = azimuth
        lastPitch   = pitch
    }

    private fun handleGyroscope(event: SensorEvent) {
        if (lastTimestamp == 0L) {
            lastTimestamp = event.timestamp
            return
        }

        val dt = (event.timestamp - lastTimestamp) * 1e-9f  // nanoseconds → seconds
        lastTimestamp = event.timestamp

        // event.values: [rotX(pitch), rotY(yaw), rotZ(roll)] in rad/s
        val dPitch = Math.toDegrees((event.values[0] * dt).toDouble()).toFloat()
        val dYaw   = Math.toDegrees((event.values[1] * dt).toDouble()).toFloat()

        if (abs(dYaw) > 0.01f || abs(dPitch) > 0.01f) {
            onRotation(dYaw, dPitch)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("GyroManager", "Accuracy changed: $accuracy")
    }
}
