package com.example.flutter_application_1

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.SurfaceTexture
import android.os.Build
import android.view.Surface
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class DriverGuardService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "DriverGuardChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        
        var previewSurfaceTexture: SurfaceTexture? = null
        internal var instance: DriverGuardService? = null
        
        fun setPreviewTexture(texture: SurfaceTexture) {
            previewSurfaceTexture = texture
        }

        fun updateConfig(ear: Double, duration: Double, mode: Int) {
            instance?.detector?.updateConfig(ear, duration, mode)
        }
        
        fun connectWatch() {
            instance?.connectSmartWatch()
        }
    }
    
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var detector: FaceMeshDetector? = null
    private var gpsImpl: GPSImpl? = null
    private var watchSource: SmartWatchSource? = null
    private var tts: TTSAction? = null
    private var overlayHelper: OverlayHelper? = null
    private var dbHelper: EventDbHelper? = null
    private var accelerometerImpl: AccelerometerImpl? = null
    
    // State to prevent duplicate logging for the same event
    private var isFatigueState = false

    override fun onCreate() {
        super.onCreate()
        DriverGuardService.instance = this
        
        // Initialize Warning Actions
        tts = TTSAction(this)
        overlayHelper = OverlayHelper(this)
        dbHelper = EventDbHelper(this)
        
        // Initialize Sensors
        watchSource = SmartWatchSource()
        accelerometerImpl = AccelerometerImpl(this) { isStable ->
            detector?.updateStability(isStable)
        }
        
        detector = FaceMeshDetector(this)
        detector?.setFatigueListener { isFatigue ->
            if (isFatigue) {
                // Rising Edge Detection: Only log when state changes from False -> True
                if (!isFatigueState) {
                    logFatigueEvent()
                    isFatigueState = true
                }
                
                // Trigger Warning (Continuous)
                tts?.trigger(2) // 2 = Severe (Speech)
            } else {
                // Falling Edge
                if (isFatigueState) {
                    isFatigueState = false
                }
                
                // Cancel Warning
                tts?.cancel()
            }
        }
        
        gpsImpl = GPSImpl(this) { speed ->
            detector?.updateSpeed(speed)
            
            // Adaptive Scheduler: Auto-sleep if speed < 5km/h for long time
            // We implement a simple version: if speed < 5, reduce check frequency
            // The actual frequency reduction is handled inside FaceMeshDetector's skipFactor
            // But here we can also pause/resume heavy tasks if needed.
            // For now, FaceMeshDetector logic is enough:
            // if (currentSpeed > 80) mode = High
            // else mode = user_setting
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_STOP -> stopForegroundService()
        }
        
        return START_NOT_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        DriverGuardService.instance = null
        previewSurfaceTexture = null
        detector?.close()
        gpsImpl?.stopListening()
        accelerometerImpl?.stopListening()
        tts?.shutdown()
        cameraExecutor.shutdown()
    }

    private fun startForegroundService() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        println("DriverGuardService: Service Started")
        
        // Reset detector state to avoid false positives from previous session
        detector?.reset()
        isFatigueState = false
        
        // Show Overlay for background process survival (Android 9+)
        // This is crucial for keeping CameraX active when UI is detached
        overlayHelper?.showOverlay()
        
        // 启动相机
        startCamera()
        // 启动GPS
        gpsImpl?.startListening()
        // 启动加速度计
        accelerometerImpl?.startListening()
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        gpsImpl?.stopListening()
        accelerometerImpl?.stopListening()
        overlayHelper?.removeOverlay()
        println("DriverGuardService: Service Stopped")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                detector?.detect(imageProxy)
            }

            var preview: Preview? = null
            if (previewSurfaceTexture != null) {
                preview = Preview.Builder().build()
                preview!!.setSurfaceProvider { request ->
                    val surface = Surface(previewSurfaceTexture)
                    request.provideSurface(surface, ContextCompat.getMainExecutor(this@DriverGuardService)) { 
                        surface.release() 
                    }
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                
                if (preview != null) {
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, imageAnalysis, preview
                    )
                } else {
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, imageAnalysis
                    )
                }
                println("CameraX: Camera started successfully")

            } catch (exc: Exception) {
                println("CameraX: Use case binding failed: $exc")
            }

        }, ContextCompat.getMainExecutor(this))
    }
    
    fun connectSmartWatch() {
        watchSource?.startSample { data ->
            if (data.type == SensorType.HEART_RATE) {
                // Feed Heart Rate into Detector -> FusionEngine
                detector?.updateHeartRate(data.value)
                println("Sensor Fusion: Heart Rate received: ${data.value.toInt()} bpm")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Driver Guard Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("疲劳驾驶监测中")
            .setContentText("正在守护您的驾驶安全...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun logFatigueEvent() {
        // Log to SQLite
        // We need current speed and fatigue value.
        // Detector doesn't expose them directly in the callback boolean.
        // But gpsImpl has speed. Value (EAR) is inside detector.
        // Ideally pass data object in callback. For now, use placeholder value or expose getter.
        
        val speed = gpsImpl?.currentSpeed ?: 0.0
        // We assume type 1 (Fatigue) for now. Fusion engine might have detailed reason.
        // Ideally we should update the listener signature to pass FatigueData.
        
        // Let's execute on background thread to avoid blocking main thread
        Executors.newSingleThreadExecutor().execute {
            try {
                dbHelper?.insertEvent(1, 0.0, speed)
                println("DriverGuardService: Fatigue Event Logged to DB")
            } catch (e: Exception) {
                println("DriverGuardService: DB Log Error: ${e.message}")
            }
        }
    }
}
