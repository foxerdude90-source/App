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
import kotlin.math.abs
import kotlin.math.atan2

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
 * Real foreground Android GPS + Gimbal-Lock-Free Hardware Compass sensor tracker.
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
                    gpsBearingDegrees = if (loc.hasBearing() && loc.speed > 1.8f) loc.bearing else null,
                    provider = loc.provider ?: "fused",
                    timestampMs = loc.time,
                ),
            )
        }

        // 1. Instant last known location
        fusedClient?.lastLocation?.addOnSuccessListener { loc ->
            if (loc != null) emitLocation(loc)
        }
        runCatching {
            val bestLast = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { provider -> locationManager?.getLastKnownLocation(provider) }
                .maxByOrNull { it.time }
            if (bestLast != null) emitLocation(bestLast)
        }

        // 2. High-accuracy 1 Hz Fused updates
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

        // 3. Fallback to native LocationManager
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
     * Computes tilt-compensated, gimbal-lock-free azimuth (0..360° clockwise from Magnetic North)
     * directly from the 3x3 world-to-device rotation matrix.
     * Seamlessly blends +Y (top edge when flat) and -Z (camera forward when held upright) so
     * tilting the phone from 0° flat to 90° vertical never flips or jitters the compass arrow!
     */
    internal fun computeTiltCompensatedAzimuthDegrees(rotMatrix: FloatArray): Float {
        if (rotMatrix.size < 9) return 0f
        // World East-North-Up components of body +Y (top of phone) and -Z (out the back of phone):
        val yEast = rotMatrix[1].toDouble()
        val yNorth = rotMatrix[4].toDouble()
        val zBackEast = -rotMatrix[2].toDouble()
        val zBackNorth = -rotMatrix[5].toDouble()

        // rotMatrix[8] = cos(tilt): ~1.0 when phone is flat, ~0.0 when phone is vertical
        val flatness = abs(rotMatrix[8].toDouble()).coerceIn(0.0, 1.0)
        // Smooth blend: flat uses +Y (top of phone), upright uses -Z (forward line of sight)
        val wY = ((flatness - 0.25) / 0.45).coerceIn(0.0, 1.0)
        val wZ = 1.0 - wY

        val east = wY * yEast + wZ * zBackEast
        val north = wY * yNorth + wZ * zBackNorth
        val rawDeg = Math.toDegrees(atan2(east, north)).toFloat()
        return ((rawDeg % 360f) + 360f) % 360f
    }

    /**
     * Streams smooth, tilt-compensated hardware compass heading (0..360° North = 0, East = 90,
     * South = 180, West = 270) at ~20 Hz so the user's blue-dot arrow and top compass tape move
     * fluidly with their phone.
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

        fun publishAzimuth(rawDeg: Float) {
            val normalized = ((rawDeg % 360f) + 360f) % 360f
            smoothedHeading = if (!initialized) {
                initialized = true
                normalized
            } else {
                val delta = abs(MapsGeoMath.shortestAngleDeltaDegrees(smoothedHeading, normalized))
                // Small deadzone eliminates micro-jitter while keeping fast turns responsive
                if (delta < 0.8f) smoothedHeading
                else {
                    val alpha = if (delta > 25f) 0.35f else 0.20f
                    MapsGeoMath.interpolateAngleDegrees(smoothedHeading, normalized, alpha)
                }
            }
            val now = System.currentTimeMillis()
            if (now - lastEmitMs >= 50L) {
                lastEmitMs = now
                trySend(smoothedHeading)
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                        publishAzimuth(computeTiltCompensatedAzimuthDegrees(rotMatrix))
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        hasGravity = true
                        if (hasGeomagnetic && SensorManager.getRotationMatrix(rotMatrix, null, gravity, geomagnetic)) {
                            publishAzimuth(computeTiltCompensatedAzimuthDegrees(rotMatrix))
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
            sensorManager.registerListener(listener, rotSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            if (accelSensor != null) sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
            if (magSensor != null) sensorManager.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_UI)
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
