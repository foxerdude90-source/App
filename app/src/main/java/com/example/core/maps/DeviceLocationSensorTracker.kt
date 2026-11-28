package com.example.core.maps

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class LiveGpsTelemetry(
    val point: GeoPoint,
    val accuracyMeters: Float,
    val speedMps: Float,
    val altitudeMeters: Double,
    val gpsBearingDegrees: Float?,
    val provider: String,
    val timestampMs: Long,
)

/**
 * Real foreground Android GPS + Hardware Compass sensor tracker.
 * Uses Google Play Services FusedLocationProviderClient for fast high-precision fixes
 * with automatic fallback to native Android LocationManager (GPS + Network).
 */
object DeviceLocationSensorTracker {

    @SuppressLint("MissingPermission")
    fun observeLocationUpdates(context: Context): Flow<LiveGpsTelemetry> = callbackFlow {
        val appCtx = context.applicationContext
        val fusedClient = runCatching { LocationServices.getFusedLocationProviderClient(appCtx) }.getOrNull()
        val locationManager = appCtx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        fun emitLocation(loc: Location) {
            trySend(
                LiveGpsTelemetry(
                    point = GeoPoint(loc.latitude, loc.longitude),
                    accuracyMeters = loc.accuracy.coerceAtLeast(1f),
                    speedMps = if (loc.hasSpeed()) loc.speed.coerceAtLeast(0f) else 0f,
                    altitudeMeters = if (loc.hasAltitude()) loc.altitude else 0.0,
                    gpsBearingDegrees = if (loc.hasBearing() && loc.speed > 1.2f) loc.bearing else null,
                    provider = loc.provider ?: "fused",
                    timestampMs = loc.time,
                ),
            )
        }

        // 1. Try instant last known location from Fused or LocationManager
        fusedClient?.lastLocation?.addOnSuccessListener { loc ->
            if (loc != null) emitLocation(loc)
        }
        runCatching {
            val bestLast = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { provider -> locationManager?.getLastKnownLocation(provider) }
                .maxByOrNull { it.time }
            if (bestLast != null) emitLocation(bestLast)
        }

        // 2. Register FusedLocationProviderClient high-frequency updates (1 Hz)
        val fusedCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { emitLocation(it) }
            }
        }
        var registeredFused = false
        if (fusedClient != null) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(0.5f)
                .build()
            registeredFused = runCatching {
                fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
                true
            }.getOrDefault(false)
        }

        // 3. Also bind native LocationManager fallback if Fused isn't available
        val legacyListener = object : LocationListener {
            override fun onLocationChanged(location: Location) = emitLocation(location)
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        var registeredLegacy = false
        if (!registeredFused && locationManager != null) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
                runCatching {
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.requestLocationUpdates(provider, 1000L, 1f, legacyListener, Looper.getMainLooper())
                        registeredLegacy = true
                    }
                }
            }
        }

        awaitClose {
            if (registeredFused && fusedClient != null) {
                runCatching { fusedClient.removeLocationUpdates(fusedCallback) }
            }
            if (registeredLegacy && locationManager != null) {
                runCatching { locationManager.removeUpdates(legacyListener) }
            }
        }
    }

    /**
     * Streams smoothed hardware compass azimuth (0..360° True/Magnetic North) from device rotation vector
     * or accelerometer + magnetometer sensors.
     */
    fun observeCompassHeading(context: Context): Flow<Float> = callbackFlow {
        val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            awaitClose { }
            return@callbackFlow
        }

        val rotSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false
        var smoothedHeading = 0f
        var initialized = false
        var lastEmitMs = 0L

        val rotMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        fun publishAzimuth(rawDeg: Float) {
            val normalized = ((rawDeg % 360f) + 360f) % 360f
            smoothedHeading = if (!initialized) {
                initialized = true
                normalized
            } else {
                MapsGeoMath.interpolateAngleDegrees(smoothedHeading, normalized, 0.22f)
            }
            val now = System.currentTimeMillis()
            if (now - lastEmitMs >= 80L) {
                lastEmitMs = now
                trySend(smoothedHeading)
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                        SensorManager.getOrientation(rotMatrix, orientation)
                        publishAzimuth(Math.toDegrees(orientation[0].toDouble()).toFloat())
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        hasGravity = true
                        if (hasGeomagnetic && SensorManager.getRotationMatrix(rotMatrix, null, gravity, geomagnetic)) {
                            SensorManager.getOrientation(rotMatrix, orientation)
                            publishAzimuth(Math.toDegrees(orientation[0].toDouble()).toFloat())
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        hasGeomagnetic = true
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotSensor != null) {
            sensorManager.registerListener(listener, rotSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            if (accelSensor != null) sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
            if (magSensor != null) sensorManager.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_UI)
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
