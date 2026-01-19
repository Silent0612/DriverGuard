package com.example.flutter_application_1

enum class SensorType {
    GPS_SPEED,
    ACCELEROMETER,
    HEART_RATE,
    OBD_DATA
}

data class SensorData(
    val type: SensorType,
    val value: Double, // Generic value, can be speed or heart rate
    val timestamp: Long = System.currentTimeMillis(),
    val extra: Map<String, Any>? = null
)

interface ISensorSource {
    fun getDataType(): SensorType
    fun startSample(callback: (SensorData) -> Unit)
    fun stopSample()
}
