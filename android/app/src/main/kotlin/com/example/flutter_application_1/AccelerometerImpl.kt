package com.example.flutter_application_1

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

class AccelerometerImpl(context: Context, private val onStabilityChanged: (Boolean) -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    // Z-axis bump detection
    private val BUMP_THRESHOLD = 2.0f // m/s^2 deviation from gravity (9.8)
    private var lastStability = true
    
    // Smoothing
    private var gravity = FloatArray(3)
    private var linear_acceleration = FloatArray(3)

    fun startListening() {
        accelerometer?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            println("Accelerometer: Listening started")
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        println("Accelerometer: Listening stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Alpha is calculated as t / (t + dT)
        // with t, the low-pass filter's time-constant
        // and dT, the event delivery rate
        val alpha = 0.8f

        // Isolate the force of gravity with the low-pass filter.
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

        // Remove the gravity contribution with the high-pass filter.
        linear_acceleration[0] = event.values[0] - gravity[0]
        linear_acceleration[1] = event.values[1] - gravity[1]
        linear_acceleration[2] = event.values[2] - gravity[2]
        
        // Check Z-axis (vertical) stability
        // Strong vertical acceleration implies bumps
        val zAcc = abs(linear_acceleration[2])
        
        // Also check overall magnitude of shake
        val magnitude = sqrt(linear_acceleration[0]*linear_acceleration[0] + 
                             linear_acceleration[1]*linear_acceleration[1] + 
                             linear_acceleration[2]*linear_acceleration[2])
                             
        val isStable = magnitude < BUMP_THRESHOLD
        
        if (isStable != lastStability) {
            lastStability = isStable
            onStabilityChanged(isStable)
            // println("Accelerometer: Stability Changed -> $isStable (Mag: $magnitude)")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }
}
