package com.example.flutter_application_1

import android.content.Intent
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.TextureRegistry

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.fatigue/detection"
    private val EVENT_CHANNEL = "com.example.fatigue/status"
    
    private var textureEntry: TextureRegistry.SurfaceTextureEntry? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startDetection" -> {
                    startDriverService()
                    result.success(null)
                }
                "stopDetection" -> {
                    stopDriverService()
                    result.success(null)
                }
                "createPreview" -> {
                    if (textureEntry == null) {
                        textureEntry = flutterEngine.renderer.createSurfaceTexture()
                    }
                    val surfaceTexture = textureEntry!!.surfaceTexture()
                    // 设置一个合理的预览分辨率，例如 480x640 (竖屏)
                    surfaceTexture.setDefaultBufferSize(480, 640)
                    
                    // 将纹理传递给 Service
                    DriverGuardService.setPreviewTexture(surfaceTexture)
                    
                    result.success(textureEntry!!.id())
                }
                "updateConfig" -> {
                    val earThreshold = call.argument<Double>("earThreshold") ?: 0.20
                    val durationThreshold = call.argument<Double>("durationThreshold") ?: 2.0
                    val detectionMode = call.argument<Int>("detectionMode") ?: 1
                    
                    DriverGuardService.updateConfig(earThreshold, durationThreshold, detectionMode)
                    result.success(null)
                }
                "connectWatch" -> {
                    DriverGuardService.connectWatch()
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL)
            .setStreamHandler(DetectionStreamHandler)
    }

    private fun startDriverService() {
        val intent = Intent(this, DriverGuardService::class.java)
        intent.action = DriverGuardService.ACTION_START
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopDriverService() {
        val intent = Intent(this, DriverGuardService::class.java)
        intent.action = DriverGuardService.ACTION_STOP
        startService(intent)
    }
}
