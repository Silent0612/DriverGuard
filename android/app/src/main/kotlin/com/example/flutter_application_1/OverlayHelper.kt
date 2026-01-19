package com.example.flutter_application_1

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

class OverlayHelper(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    fun showOverlay() {
        if (overlayView != null) return // Already showing

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            1, 1, // 1x1 Pixel
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0

        // Create a dummy view (empty FrameLayout)
        // In real scenario, we might need to attach the SurfaceView here if CameraX requires it to be visible
        // For now, let's see if just having an overlay is enough to keep the context "visible"
        // or if we need to actually render the preview to this view.
        // CameraX Lifecycle binding usually requires the LifecycleOwner (Service) to be "Active".
        // Service Lifecycle is always Active if it's a Service.
        // The issue is CameraX Preview UseCase requires a SurfaceProvider.
        
        overlayView = FrameLayout(context)
        
        try {
            windowManager?.addView(overlayView, params)
            println("OverlayHelper: 1x1 Overlay added")
        } catch (e: Exception) {
            println("OverlayHelper: Failed to add overlay: ${e.message}")
        }
    }

    fun removeOverlay() {
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
                println("OverlayHelper: Overlay removed")
            } catch (e: Exception) {
                println("OverlayHelper: Error removing overlay: ${e.message}")
            }
            overlayView = null
        }
    }
}
