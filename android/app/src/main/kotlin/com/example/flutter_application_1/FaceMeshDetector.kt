package com.example.flutter_application_1

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.hypot

class FaceMeshDetector(val context: Context) {

    private var faceLandmarker: FaceLandmarker? = null
    private val isProcessing = AtomicBoolean(false)
    private var lastFrameTime = 0L
    
    // Configuration Parameters
    private var earThreshold = 0.20 // Increased from 0.15 to better detect semi-closed eyes
    private var durationThreshold = 1.5 // Seconds
    private var detectionMode = 1 // 1: Balanced (Default)
    
    // State Variables
    private var frameCounter = 0
    private var currentSpeed = 0f // km/h
    private var fatigueListener: ((Boolean) -> Unit)? = null
    private val eyeStateHistory = LinkedList<Boolean>() // true = closed, false = open
    private val pitchHistory = LinkedList<Double>() // For nodding detection
    private val HISTORY_SIZE = 300 // Approx 10-15 seconds buffer
    private var lastFpsTime = System.currentTimeMillis()
    private var framesInSecond = 0
    private var currentFps = 0
    private var isLowLight = false
    
    // Fusion Engine
    private val fusionEngine = FusionEngine()
    
    // TODO: Switch to false when running on real device
    private var useMockData = false 

    init {
        if (!useMockData) {
            setupLandmarker()
        }
    }

    fun setFatigueListener(listener: (Boolean) -> Unit) {
        this.fatigueListener = listener
    }

    fun updateConfig(ear: Double, duration: Double, mode: Int) {
        this.earThreshold = ear
        this.durationThreshold = duration
        this.detectionMode = mode
        println("MediaPipe: Config updated - EAR:$ear, Duration:$duration, Mode:$mode")
    }

    fun updateSpeed(speedKmh: Float) {
        this.currentSpeed = speedKmh
        fusionEngine.updateSpeed(speedKmh.toDouble())
        // Optional: Print log if speed changes significantly
        // println("MediaPipe: Speed updated to $speedKmh km/h")
    }
    
    fun updateHeartRate(bpm: Double) {
        fusionEngine.updateHeartRate(bpm)
    }
    
    fun updateStability(isStable: Boolean) {
        fusionEngine.updateStability(isStable)
    }
    
    fun reset() {
        eyeStateHistory.clear()
        pitchHistory.clear()
        frameCounter = 0
        currentSpeed = 0f
        currentFps = 0
        framesInSecond = 0
        lastFpsTime = System.currentTimeMillis()
        fusionEngine.reset()
        println("FaceMeshDetector: State Reset")
    }

    private fun setupLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(this::returnLivestreamResult)
            .setErrorListener(this::returnLivestreamError)
            .build()

        try {
            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            println("MediaPipe: FaceLandmarker initialized successfully")
        } catch (e: Exception) {
            println("MediaPipe: FATAL ERROR - Initialization failed: ${e.message}")
            e.printStackTrace()
        }
    }

    fun detect(imageProxy: ImageProxy) {
        // Mock Data Mode
        if (useMockData) {
            val mockEar = 0.15 + Math.random() * 0.20 
            val isFatigue = mockEar < earThreshold
            
            val data = mapOf(
                "ear" to mockEar,
                "fatigue" to isFatigue,
                "perclos" to 0.15, // Mock value
                "fps" to 30,
                "timestamp" to System.currentTimeMillis()
            )
            
            DetectionStreamHandler.sendData(data)
            imageProxy.close()
            return
        }

        if (faceLandmarker == null) {
            imageProxy.close()
            return
        }

        // Adaptive Scheduler: Frame Skipping
        frameCounter++
        
        // Dynamic Mode Determination based on Speed
        // If speed > 80km/h, force High Performance (Mode 2) behavior
        // Otherwise, respect user's choice (Balanced or Power Save)
        
        val effectiveMode = if (currentSpeed > 80) {
            2 // Force High Performance
        } else if (currentSpeed < 5) {
            0 // Force Power Saver (Traffic Jam / Parking)
        } else {
            // Respect user setting
            detectionMode
        }

        val skipFactor = when (effectiveMode) {
            0 -> 15 // Power Saver: Process 1 in 15 (approx 2fps) - Aggressive saving
            1 -> 2  // Balanced: Process 1 in 2 (approx 15fps)
            else -> 1 // High Performance: Process all (approx 30fps)
        }
        
        if (frameCounter % skipFactor != 0) {
            imageProxy.close()
            return
        }

        val currentTime = SystemClock.uptimeMillis()

        // 强行重置锁：如果上一帧处理超过 2000ms 还没回调，认为它卡死了，强制释放锁
        if (isProcessing.get() && (currentTime - lastFrameTime > 2000)) {
            println("MediaPipe: Timeout forced lock release")
            isProcessing.set(false)
        }

        // Optimization: Drop frame if previous one is still processing
        if (isProcessing.get()) {
            imageProxy.close()
            return
        }
        
        isProcessing.set(true)
        lastFrameTime = currentTime
        
        try {
            // ImageProxy -> Bitmap
            val bitmap = imageProxy.toBitmap()
            
            // Optimization: Scale down to reduce processing time on Emulator
            val scaleFactor = 0.5f 
            val rotation = imageProxy.imageInfo.rotationDegrees
            val matrix = Matrix().apply { 
                postRotate(rotation.toFloat()) 
                postScale(scaleFactor, scaleFactor)
            }
            
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            // Low Light Detection (Every 30 frames check)
            if (frameCounter % 30 == 0) {
                val brightness = ImageUtils.calculateAverageBrightness(rotatedBitmap)
                isLowLight = brightness < 40 // Threshold for "Dark Environment"
                if (isLowLight) {
                    println("MediaPipe: Low Light Detected (Brightness: $brightness)")
                }
            }

            val mpImage = BitmapImageBuilder(rotatedBitmap).build()

            faceLandmarker?.detectAsync(mpImage, currentTime)
        } catch (e: Exception) {
            println("MediaPipe: Pre-processing failed: ${e.message}")
            e.printStackTrace()
            isProcessing.set(false)
        } finally {
            // ImageProxy must be closed
            imageProxy.close()
        }
    }

    private fun returnLivestreamResult(result: FaceLandmarkerResult, input: MPImage) {
        isProcessing.set(false)
        calculateFps()

        if (result.faceLandmarks().isNotEmpty()) {
            val landmarks = result.faceLandmarks()[0] 

            // 1. Calculate Head Pose (Yaw & Pitch)
            // Yaw: Nose (1) x vs cheek centers (454, 234)
            val nose = landmarks[1]
            val leftCheek = landmarks[454]
            val rightCheek = landmarks[234]
            
            val distToLeft = abs(nose.x() - leftCheek.x())
            val distToRight = abs(nose.x() - rightCheek.x())
            val yawRatio = distToLeft / (distToRight + 0.001)
            
            // Normalized Yaw: -1.0 (Left) to 1.0 (Right). 0.0 is Center.
            // If yawRatio = 1 -> Center. 
            // If yawRatio > 1 -> Nose closer to RightCheek (in image) -> Turning Left? 
            // Wait, LeftCheek is 454 (User's Left, Image Right?). RightCheek is 234 (User's Right, Image Left?).
            // MediaPipe 468 map: 
            // 234 is Right side of face (Image Left). 454 is Left side of face (Image Right).
            // Nose (1) is center.
            // If looking straight: distToLeft(454) approx eq distToRight(234).
            // If turning User's Right (Image Left): Nose moves towards 234. distToRight < distToLeft. yawRatio > 1.
            // If turning User's Left (Image Right): Nose moves towards 454. distToLeft < distToRight. yawRatio < 1.
            
            // Let's simplify: 
            // SideFactor: 0 (Front) -> 1 (Side)
            val sideFactor = abs(1.0 - yawRatio).coerceAtMost(1.0)
            
            // Pitch: Nose Y vs Mean Ear Y
            // Looking Down: Nose moves down (Y increases) relative to Ears.
            // Looking Up: Nose moves up (Y decreases).
            val avgEarY = (leftCheek.y() + rightCheek.y()) / 2.0
            val pitchVal = nose.y() - avgEarY // Positive = Down, Negative = Up
            
            updatePitchHistory(pitchVal.toDouble())
            val isNodding = checkNodding()

            // 2. Smart EAR Calculation (Weighted by Yaw)
            val leftEar = calculateEAR(landmarks, 33, 160, 158, 133, 153, 144)
            val rightEar = calculateEAR(landmarks, 362, 385, 387, 263, 373, 380)

            // Anti-Glare Logic (Glasses Optimization)
            // If one eye has glare, ignore it. If both have glare, rely on PERCLOS history or just warn.
            // We need to pass the Bitmap to check pixels, but here we only have landmarks.
            // Wait, we are in the callback, we don't have the original bitmap easily unless we passed it or cached it.
            // FaceLandmarkerResult doesn't contain the image.
            // However, we can use a heuristic: if EAR suddenly drops to near zero but Pitch/Yaw is normal? 
            // Or if landmarks are jittery?
            // Actually, we can't do pixel-level glare check here without the bitmap.
            // Let's rely on the Robust EAR logic we already have (Yaw weighted).
            // Glasses reflection usually affects one eye more than the other due to angle.
            // Our "Effective EAR" logic picks the "Better" eye based on Yaw. 
            // We can improve this: Pick the eye with HIGHER EAR if we are frontal? 
            // Because glare/occlusion usually makes eye detection fail (points converge -> EAR small) OR makes it expand?
            // Usually reflection makes landmarks unreliable.
            
            // Refined Logic for Glasses/Glare:
            // If Yaw is near Center (|yawRatio - 1| < 0.2), trust the MAX(Left, Right) instead of Avg.
            // Because if one eye is occluded/glared, it often misdetects as closed (small EAR).
            // So taking the larger one is safer to avoid false fatigue.
            
            val effectiveEar = if (yawRatio > 1.5) {
                 rightEar
            } else if (yawRatio < 0.66) {
                 leftEar
            } else {
                 // Frontal-ish: Use MAX to avoid false positives from single-eye glare/hair occlusion
                 // unless both are small.
                 // But wait, if user winks? We don't want to detect winking as fatigue.
                 // Fatigue usually involves BOTH eyes closing.
                 // So MAX is actually a good proxy for "At least one eye is open".
                 Math.max(leftEar, rightEar)
            }
            
            // PERCLOS Calculation
            // Remove YawCorrection (it was reducing threshold).
            // Now we rely on "effectiveEar" being accurate for the visible eye.
            // And we increased base threshold to 0.20.
            
            var dynamicEarThreshold = if (currentSpeed > 80) earThreshold + 0.05 else earThreshold
            
            // Low Light Optimization:
            // In dark, contrast is low, landmarks might jitter inwards -> Lower EAR.
            // So we should LOWER the threshold to avoid false closed-eye detection.
            if (isLowLight) {
                dynamicEarThreshold -= 0.03
            }
            
            // Safety clamp
            if (dynamicEarThreshold < 0.12) dynamicEarThreshold = 0.12

            val dynamicDuration = if (currentSpeed > 80) durationThreshold * 0.8 else durationThreshold

            val isClosed = effectiveEar < dynamicEarThreshold
            updatePerclosHistory(isClosed)
            val perclos = calculatePerclos()

            // Fatigue Logic (Fusion Engine)
            // Add Nodding to Fusion
            fusionEngine.updateVisionData(effectiveEar, perclos, isClosed, isNodding)
            
            val isFatigue = fusionEngine.isFatigue
            
            // Notify Native Listener
            fatigueListener?.invoke(isFatigue)

            // Prepare Landmarks for Visualization (Flattened List)
            val landmarksList = ArrayList<Float>()
            // Only send key landmarks to save bandwidth? Or all? 
            // Let's send a subset: Face Oval (10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109)
            // Plus Eyes (33, 246, 161, 160, 159, 158, 157, 173, 133, 155, 154, 153, 145, 144, 163, 7)
            // Plus Lips (61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291)
            // Actually, sending all 468 points as float array is ~4KB. It should be fine.
            for (landmark in landmarks) {
                landmarksList.add(landmark.x())
                landmarksList.add(landmark.y())
            }

            val data = mapOf(
                "ear" to effectiveEar,
                "fatigue" to isFatigue,
                "perclos" to perclos,
                "fps" to currentFps,
                "speed" to currentSpeed,
                "landmarks" to landmarksList,
                "timestamp" to System.currentTimeMillis()
            )
            DetectionStreamHandler.sendData(data)
        }
    }
    
    private fun updatePerclosHistory(isClosed: Boolean) {
        eyeStateHistory.addLast(isClosed)
        if (eyeStateHistory.size > HISTORY_SIZE) {
            eyeStateHistory.removeFirst()
        }
    }
    
    private fun calculatePerclos(): Double {
        if (eyeStateHistory.isEmpty()) return 0.0
        val closedCount = eyeStateHistory.count { it }
        return closedCount.toDouble() / eyeStateHistory.size
    }
    
    private fun checkContinuousClosure(durationLimit: Double): Boolean {
        // Simple check: are the last N frames all closed?
        // Assuming 15fps effective in balanced mode, 2.0s = 30 frames
        // This is a rough estimation. For precise time, we need timestamps.
        // For now, let's use the history size proportional to frame rate.
        val framesToCheck = (durationLimit * 15).toInt().coerceAtMost(eyeStateHistory.size)
        if (framesToCheck == 0) return false
        
        // Check last N elements
        val subList = eyeStateHistory.takeLast(framesToCheck)
        return subList.all { it }
    }

    private fun calculateFps() {
        framesInSecond++
        val now = System.currentTimeMillis()
        if (now - lastFpsTime >= 1000) {
            currentFps = framesInSecond
            framesInSecond = 0
            lastFpsTime = now
        }
    }
    
    private fun updatePitchHistory(pitch: Double) {
        pitchHistory.addLast(pitch)
        if (pitchHistory.size > 150) { // 5-10 seconds buffer
            pitchHistory.removeFirst()
        }
    }
    
    private fun checkNodding(): Boolean {
        if (pitchHistory.size < 30) return false
        
        // Nodding Detection Algorithm:
        // Detect rhythmic movement in Pitch.
        // Simple variance check: if variance is high, head is moving up/down.
        // Or zero-crossing count relative to average?
        
        // 1. Calculate Average Pitch
        val avgPitch = pitchHistory.average()
        
        // 2. Count "Nods" (crossing the average with significant amplitude)
        var crossings = 0
        var isAbove = pitchHistory[0] > avgPitch
        val threshold = 0.05 // Amplitude threshold
        
        for (i in 1 until pitchHistory.size) {
            val p = pitchHistory[i]
            // Only count if it goes significantly far from average
            if (isAbove && p < avgPitch - threshold) {
                crossings++
                isAbove = false
            } else if (!isAbove && p > avgPitch + threshold) {
                crossings++
                isAbove = true
            }
        }
        
        // If > 3 crossings in last 5 seconds (150 frames), consider it nodding?
        // Actually, normal driving might have bumps.
        // We need Rhythmic check.
        
        // For now, let's stick to simple variance or significant drop (Head Bob)
        // Head Bob: Pitch increases (head down) significantly, then snaps back?
        // "Microsleep Nod": Slow down, Fast up.
        
        // Let's detect "High Variance" for now as a proxy for "Restless Head"
        val variance = pitchHistory.map { (it - avgPitch) * (it - avgPitch) }.average()
        
        // Threshold tuned experimentally. 
        // Normal variance is very low (< 0.001). Nodding might be > 0.01.
        return variance > 0.005
    }

    private fun returnLivestreamError(error: RuntimeException) {
        println("MediaPipe Error: ${error.message}")
        isProcessing.set(false)
    }

    private fun calculateEAR(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>, 
                             p1: Int, p2: Int, p3: Int, p4: Int, p5: Int, p6: Int): Double {
        val dist1 = distance(landmarks[p2], landmarks[p6])
        val dist2 = distance(landmarks[p3], landmarks[p5])
        val dist3 = distance(landmarks[p1], landmarks[p4])
        return (dist1 + dist2) / (2.0 * dist3)
    }

    private fun distance(p1: com.google.mediapipe.tasks.components.containers.NormalizedLandmark, 
                         p2: com.google.mediapipe.tasks.components.containers.NormalizedLandmark): Double {
        return hypot((p1.x() - p2.x()).toDouble(), (p1.y() - p2.y()).toDouble())
    }
    
    fun close() {
        faceLandmarker?.close()
    }
}
