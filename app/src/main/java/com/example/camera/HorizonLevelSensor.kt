package com.example.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2

class HorizonLevelSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _rollDegrees = MutableStateFlow(0f)
    val rollDegrees: StateFlow<Float> = _rollDegrees.asStateFlow()

    private var registered = false

    fun setEnabled(enabled: Boolean) {
        if (enabled && !registered && accelerometer != null) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            registered = true
        } else if (!enabled && registered) {
            sensorManager?.unregisterListener(this)
            registered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        if (values.size < 2) return
        val ax = values[0]
        val ay = values[1]
        val angleRad = atan2(ax.toDouble(), ay.toDouble())
        val deg = Math.toDegrees(angleRad).toFloat().coerceIn(-45f, 45f)
        // Low-pass filter for steady viewfinder horizon bar
        _rollDegrees.value = _rollDegrees.value * 0.72f + deg * 0.28f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun release() {
        setEnabled(false)
    }
}
