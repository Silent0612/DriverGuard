package com.example.flutter_application_1

import android.os.Handler
import android.os.Looper
import kotlin.random.Random

class SmartWatchSource : ISensorSource {
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var callback: ((SensorData) -> Unit)? = null

    private val sampleRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            // Mock Heart Rate: 60-100 bpm normal, >100 maybe fatigue/stress?
            // Actually lower heart rate might be fatigue (sleepy).
            // Let's mock a normal range 70 +/- 10
            val heartRate = 70.0 + Random.nextDouble() * 20.0 - 10.0
            
            val data = SensorData(
                type = SensorType.HEART_RATE,
                value = heartRate
            )
            
            callback?.invoke(data)
            
            // Sample every 1 second
            handler.postDelayed(this, 1000)
        }
    }

    override fun getDataType(): SensorType {
        return SensorType.HEART_RATE
    }

    override fun startSample(callback: (SensorData) -> Unit) {
        this.callback = callback
        if (!isRunning) {
            isRunning = true
            handler.post(sampleRunnable)
            println("SmartWatch: Mock connection started")
        }
    }

    override fun stopSample() {
        isRunning = false
        handler.removeCallbacks(sampleRunnable)
        println("SmartWatch: Connection stopped")
    }
}
