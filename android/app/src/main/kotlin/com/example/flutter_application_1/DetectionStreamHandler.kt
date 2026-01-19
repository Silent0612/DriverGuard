package com.example.flutter_application_1

import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.EventChannel

object DetectionStreamHandler : EventChannel.StreamHandler {
    private var eventSink: EventChannel.EventSink? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        println("EventChannel: Listeners connected")
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
        println("EventChannel: Listeners disconnected")
    }

    // 供 Service 调用，发送数据给 Flutter
    fun sendData(data: Map<String, Any>) {
        uiHandler.post {
            eventSink?.success(data)
        }
    }
}
