package com.example.flutter_application_1

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*

class GPSImpl(private val context: Context, private val onSpeedUpdate: (Float) -> Unit) {

    private var fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback

    init {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    // location.speed is in m/s
                    // Convert to km/h: speed * 3.6
                    val speedKmh = location.speed * 3.6f
                    currentSpeed = speedKmh.toDouble()
                    onSpeedUpdate(speedKmh)
                }
            }
        }
    }

    var currentSpeed = 0.0
        private set

    @SuppressLint("MissingPermission")
    fun startListening() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000) // 5 seconds
            .setMinUpdateIntervalMillis(2000)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        println("GPS: Started listening")
    }

    fun stopListening() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        println("GPS: Stopped listening")
    }
}