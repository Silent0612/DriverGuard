package com.example.flutter_application_1

class FusionEngine {
    
    // Configurable thresholds
    private val BASE_EAR_THRESHOLD = 0.15
    private val BASE_PERCLOS_THRESHOLD = 0.4
    private val HR_FATIGUE_THRESHOLD = 60.0 // BPM, if lower than this, maybe sleepy? 
    // Actually, low HR variability is a sign, but simple low HR can also mean relaxed. 
    // Let's assume if HR < 55 while driving, it's very relaxed/drowsy.
    
    // Inputs
    private var currentSpeed = 0.0
    private var currentHeartRate = 0.0
    private var currentEar = 0.0
    private var currentPerclos = 0.0
    private var isEyesClosed = false
    
    // State
    private var continuousClosedDuration = 0.0 // seconds
    private var lastUpdateTime = System.currentTimeMillis()
    private var isVehicleStable = true
    
    fun reset() {
        currentSpeed = 0.0
        currentHeartRate = 0.0
        currentEar = 0.0
        currentPerclos = 0.0
        isEyesClosed = false
        
        continuousClosedDuration = 0.0
        lastUpdateTime = System.currentTimeMillis()
        isVehicleStable = true
        isFatigue = false
        fatigueReason = ""
        println("FusionEngine: State Reset")
    }

    // Output
    var isFatigue = false
        private set
    var fatigueReason = ""
        private set
        
    fun updateVisionData(ear: Double, perclos: Double, isClosed: Boolean, isNodding: Boolean) {
        val now = System.currentTimeMillis()
        val dt = (now - lastUpdateTime) / 1000.0
        lastUpdateTime = now
        
        currentEar = ear
        currentPerclos = perclos
        
        if (isClosed) {
            continuousClosedDuration += dt
        } else {
            continuousClosedDuration = 0.0
        }
        
        evaluate(isNodding)
    }
    
    fun updateSpeed(speed: Double) {
        currentSpeed = speed
        evaluate(false)
    }
    
    fun updateHeartRate(hr: Double) {
        currentHeartRate = hr
        evaluate(false)
    }
    
    fun updateStability(isStable: Boolean) {
        isVehicleStable = isStable
        evaluate(false)
    }
    
    private fun evaluate(isNodding: Boolean) {
        // 1. Dynamic Threshold Adjustment based on Context
        
        // Stability Factor: If bumpy, increase duration threshold to avoid false positives from shaking camera
        var stabilityFactor = 1.0
        if (!isVehicleStable) {
            stabilityFactor = 1.5 // Increase required duration by 50%
        }
        
        // Speed Factor: Higher speed -> Stricter thresholds
        var speedFactor = 1.0
        if (currentSpeed > 80) speedFactor = 1.2 // Make it 20% more sensitive (threshold higher)
        if (currentSpeed < 20) speedFactor = 0.8 // Less sensitive in traffic jams
        
        // Heart Rate Factor: Low HR -> Stricter thresholds
        var hrFactor = 1.0
        if (currentHeartRate > 0 && currentHeartRate < 55) {
            hrFactor = 1.1 // Slightly more sensitive if HR is very low
        }
        
        // Calculate Effective Thresholds
        // EAR: Standard is ~0.2.
        // Stability doesn't affect EAR much, but affects Duration.
        
        val effectiveEarThreshold = BASE_EAR_THRESHOLD * speedFactor * hrFactor
        
        // Duration: Standard is 1.5s.
        // To be MORE sensitive, we need LOWER duration.
        // Factor > 1.0 (Speed/HR) -> Decrease Duration.
        // Factor > 1.0 (Stability) -> INCREASE Duration.
        val baseDuration = 1.5
        val effectiveDuration = (baseDuration / (speedFactor * hrFactor)) * stabilityFactor
        
        // PERCLOS: Standard is 0.4.
        val effectivePerclosThreshold = BASE_PERCLOS_THRESHOLD / (speedFactor * hrFactor)
        
        // 2. Logic Evaluation
        
        // Criterion A: Instant Closure (Micro-sleep)
        // Check if eyes are closed (currentEar < effective) for longer than duration
        // Note: continuousClosedDuration is updated in updateVisionData based on raw boolean.
        // We should arguably update the "isClosed" state here based on dynamic threshold first.
        // But for simplicity, let's assume the input `isClosed` was based on a reasonable raw threshold,
        // and here we refine the "Fatigue Decision".
        
        // Actually, it's better if Vision sends raw EAR, and WE decide if it's closed.
        val isCurrentlyClosed = currentEar < effectiveEarThreshold
        
        // Re-calculate duration based on this dynamic decision? 
        // It's hard because we need history. 
        // Let's stick to the Fusion Logic being a "High Level Judge".
        
        val isMicroSleep = continuousClosedDuration > effectiveDuration
        
        // Criterion B: Drowsiness (PERCLOS)
        val isDrowsy = currentPerclos > effectivePerclosThreshold
        
        // Criterion C: Physiological (Heart Rate)
        // Only trigger if combined with some vision signs. Pure low HR isn't enough.
        val isPhysiologicalFatigue = (currentHeartRate > 0 && currentHeartRate < 50) && (currentPerclos > 0.2)
        
        // Criterion D: Nodding (Head Pose)
        // If nodding is detected, it's a strong sign of drowsiness.
        // But if vehicle is unstable (bumpy), nodding might be false positive.
        val isNoddingFatigue = isNodding && isVehicleStable
        
        // Final Decision
        if (isMicroSleep) {
            isFatigue = true
            fatigueReason = "Micro-sleep detected (${String.format("%.1f", continuousClosedDuration)}s)"
        } else if (isDrowsy) {
            isFatigue = true
            fatigueReason = "Drowsiness detected (PERCLOS: ${String.format("%.2f", currentPerclos)})"
        } else if (isNoddingFatigue) {
            isFatigue = true
            fatigueReason = "Distracted/Nodding detected"
        } else if (isPhysiologicalFatigue) {
            isFatigue = true
            fatigueReason = "Physiological fatigue (HR: ${currentHeartRate.toInt()})"
        } else {
            isFatigue = false
            fatigueReason = ""
        }
    }
}
