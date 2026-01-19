package com.example.flutter_application_1

import android.graphics.Bitmap
import android.graphics.Color

object ImageUtils {

    /**
     * Calculates the average brightness of the image.
     * Returns a value between 0 (Black) and 255 (White).
     * For efficiency, we only sample a subset of pixels.
     */
    fun calculateAverageBrightness(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        // Sample every 10th pixel to save CPU
        val step = 10
        var totalBrightness = 0L
        var pixelCount = 0
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices step step) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            // Standard Luminance Formula: 0.299R + 0.587G + 0.114B
            val luma = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            totalBrightness += luma
            pixelCount++
        }
        
        return if (pixelCount > 0) (totalBrightness / pixelCount).toInt() else 0
    }
    
    /**
     * Detects potential glare in the eye region.
     * Returns true if a significant portion of the ROI is over-exposed (white).
     */
    fun detectGlare(bitmap: Bitmap, landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>, eyeIndices: List<Int>): Boolean {
        // 1. Get Bounding Box of the Eye
        var minX = 1.0f
        var maxX = 0.0f
        var minY = 1.0f
        var maxY = 0.0f
        
        for (idx in eyeIndices) {
            val p = landmarks[idx]
            if (p.x() < minX) minX = p.x()
            if (p.x() > maxX) maxX = p.x()
            if (p.y() < minY) minY = p.y()
            if (p.y() > maxY) maxY = p.y()
        }
        
        // Convert to pixel coordinates
        val left = (minX * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val right = (maxX * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (minY * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val bottom = (maxY * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        
        if (right <= left || bottom <= top) return false
        
        // 2. Analyze ROI for Glare
        val width = right - left
        val height = bottom - top
        val pixels = IntArray(width * height)
        
        try {
            bitmap.getPixels(pixels, 0, width, left, top, width, height)
        } catch (e: Exception) {
            return false
        }
        
        var brightPixels = 0
        val threshold = 240 // Near white
        
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luma = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            
            if (luma > threshold) {
                brightPixels++
            }
        }
        
        val ratio = brightPixels.toFloat() / pixels.size
        // If more than 10% of eye area is washed out, it's glare
        return ratio > 0.10
    }
}
